# Needle cloud server

This folder runs the **official Needle 3 Linux x86-64 engine and model only**. It does not use a second AI provider.

## Deploy with a Docker-capable host

1. Create a new service from this repository and set its Dockerfile path to `cloud/Dockerfile`.
2. Set the service's listening port to `8000` (or let the host supply `PORT`).
3. Add a strong random secret named `NEEDLE_SERVER_TOKEN`. Do not commit the token to GitHub or put it in the APK source.
4. Deploy and wait for `GET /health` to return `{"status":"ok","model":"needle3"}`.
5. In Needle Android, enter the deployed **HTTPS** service URL and the same token.

The image downloads pinned official Needle engine/model files during build. The server listens on the host-provided port and proxies only `POST /complete` to the local Needle CLI endpoint. Requests require `Authorization: Bearer <token>`; request bodies are limited to 64 KiB. The health endpoint does not require authentication.

## API

`POST /complete`

Header: `Authorization: Bearer YOUR_TOKEN`

Body:

```json
{"input":"Calculate 12 * 8"}
```

The response is Needle's structured output. Needle proposes tool calls; the server does **not** execute arbitrary tool calls or device actions.

`GET /health` reports whether the Needle process is running.

## Notes

- This Docker image targets Linux x86-64. Use a Linux x86-64 container host.
- Keep the service private or protected with its bearer token. Use HTTPS from the Android app.
- Hosting may cost money depending on provider, runtime and usage; GitHub Actions builds the APK but does not host this server.
- The Android app's local mode remains available when the cloud URL field is empty.
