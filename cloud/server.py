#!/usr/bin/env python3
"""Small authenticated HTTP front end for the official Needle CLI server."""
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "8000"))
TOKEN = os.environ.get("NEEDLE_SERVER_TOKEN", "").strip()
UPSTREAM = "http://127.0.0.1:8080/complete"
MAX_BODY = 64 * 1024

if not TOKEN:
    raise SystemExit("NEEDLE_SERVER_TOKEN is required; configure it as a cloud secret.")

engine = subprocess.Popen(
    ["./needle", "--model", "needle3.cact", "--tools", "tools.json", "--serve"],
    cwd="/opt/needle",
    stdin=subprocess.DEVNULL,
    stdout=subprocess.DEVNULL,
    stderr=None,
)
started_at = time.time()


class Handler(BaseHTTPRequestHandler):
    server_version = "NeedleCloud/1.0"

    def _reply(self, status, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path != "/health":
            self._reply(404, {"error": "not_found"})
            return
        if engine.poll() is not None:
            self._reply(503, {"status": "needle_stopped"})
            return
        if time.time() - started_at < 5:
            self._reply(503, {"status": "starting"})
            return
        self._reply(200, {"status": "ok", "model": "needle3"})

    def do_POST(self):
        if self.path != "/complete":
            self._reply(404, {"error": "not_found"})
            return
        if self.headers.get("Authorization", "") != "Bearer " + TOKEN:
            self._reply(401, {"error": "unauthorized"})
            return
        if engine.poll() is not None:
            self._reply(503, {"error": "needle_engine_stopped"})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._reply(400, {"error": "invalid_content_length"})
            return
        if size < 1 or size > MAX_BODY:
            self._reply(413, {"error": "body_size_out_of_range"})
            return
        try:
            raw = self.rfile.read(size)
            request = json.loads(raw.decode("utf-8"))
            if not isinstance(request, dict) or not isinstance(request.get("input"), str):
                self._reply(400, {"error": "expected_json_with_string_input"})
                return
            payload = json.dumps({"input": request["input"]}).encode("utf-8")
            req = urllib.request.Request(
                UPSTREAM,
                data=payload,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=90) as response:
                result = response.read(MAX_BODY + 1)
                if len(result) > MAX_BODY:
                    self._reply(502, {"error": "upstream_response_too_large"})
                    return
                self.send_response(response.status)
                self.send_header("Content-Type", response.headers.get("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(result)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(result)
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._reply(400, {"error": "invalid_json"})
        except urllib.error.HTTPError as exc:
            self._reply(exc.code, {"error": "needle_request_failed"})
        except (urllib.error.URLError, TimeoutError, OSError):
            self._reply(502, {"error": "needle_upstream_unavailable"})

    def log_message(self, fmt, *args):
        # Avoid logging prompts or authorization headers.
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)


if __name__ == "__main__":
    try:
        server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
        print(f"Needle cloud proxy listening on 0.0.0.0:{PORT}", flush=True)
        server.serve_forever()
    finally:
        engine.terminate()
        try:
            engine.wait(timeout=5)
        except subprocess.TimeoutExpired:
            engine.kill()
