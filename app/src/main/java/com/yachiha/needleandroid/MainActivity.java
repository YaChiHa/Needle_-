package com.yachiha.needleandroid;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView output;
    private EditText input;
    private EditText cloudUrl;
    private EditText cloudToken;
    private Button send;
    private boolean closed = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        status.setText("Loading Needle model… first startup may take a little time.");
        send.setEnabled(false);
        worker.execute(() -> {
            String result;
            try (InputStream in = getAssets().open("needle3.cact")) {
                byte[] model = readAll(in);
                result = NeedleNative.initialize(model);
            } catch (Throwable t) {
                result = "ERROR: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            }
            final String finalResult = result;
            main.post(() -> {
                if (closed) return;
                boolean ok = "OK".equals(finalResult);
                status.setText(ok ? "Needle ready • runs on this device" : "Needle could not start");
                send.setEnabled(ok || cloudAvailable());
                if (!ok) output.setText(finalResult + "\n\nYou can still use cloud mode by entering your Needle server URL and token below.");
            });
        });
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32768];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(16));
        root.setBackgroundColor(0xFFF5F5F5);

        TextView title = new TextView(this);
        title.setText("Needle AI");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF202124);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Offline tool-calling model • no other AI engines");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF5F6368);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle, matchWrap());

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(0xFF155E45);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackgroundColor(0xFFE4F3EC);
        root.addView(status, matchWrap());

        TextView tip = new TextView(this);
        tip.setText("Try: “What time is it?” or “Calculate 12 * 8”. Needle returns structured tool calls, not general free-form chat.");
        tip.setTextSize(14);
        tip.setTextColor(0xFF454545);
        tip.setPadding(0, dp(16), 0, dp(12));
        root.addView(tip, matchWrap());

        cloudUrl = new EditText(this);
        cloudUrl.setSingleLine(true);
        cloudUrl.setHint("Cloud Needle URL (optional, HTTPS)");
        cloudUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(cloudUrl, matchWrap());

        cloudToken = new EditText(this);
        cloudToken.setSingleLine(true);
        cloudToken.setHint("Cloud access token (required for your server)");
        cloudToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(cloudToken, matchWrap());

        cloudUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (send != null) send.setEnabled(NeedleNative.isReady() || !s.toString().trim().isEmpty());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setText("Model output will appear here.");
        output.setTextSize(14);
        output.setTextColor(0xFF202124);
        output.setTextIsSelectable(true);
        output.setPadding(dp(14), dp(14), dp(14), dp(14));
        output.setBackgroundColor(0xFFFFFFFF);
        scroll.addView(output);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.bottomMargin = dp(14);
        root.addView(scroll, scrollLp);

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setHint("Enter a request for Needle…");
        root.addView(input, matchWrap());

        send = new Button(this);
        send.setText("Run Needle");
        send.setEnabled(false);
        LinearLayout.LayoutParams buttonLp = matchWrap();
        buttonLp.topMargin = dp(8);
        root.addView(send, buttonLp);
        send.setOnClickListener(v -> runNeedle());

        setContentView(root);
    }

    private void runNeedle() {
        final String prompt = input.getText().toString().trim();
        if (prompt.isEmpty()) {
            input.setError("Enter a request first");
            return;
        }
        final String remoteUrl = cloudUrl.getText().toString().trim();
        final String remoteToken = cloudToken.getText().toString().trim();
        final boolean useCloud = !remoteUrl.isEmpty();
        send.setEnabled(false);
        status.setText(useCloud ? "Sending to your Needle cloud server…" : "Running locally…");
        output.setText("Processing…");
        worker.execute(() -> {
            String result;
            try { result = useCloud ? completeCloud(prompt, remoteUrl, remoteToken) : NeedleNative.complete(prompt); }
            catch (Throwable t) { result = "ERROR: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()); }
            final String finalResult = result;
            main.post(() -> {
                if (closed) return;
                output.setText(finalResult);
                status.setText(useCloud ? "Needle cloud request finished" :
                    (NeedleNative.isReady() ? "Needle ready • local inference" : "Needle unavailable"));
                send.setEnabled(NeedleNative.isReady() || cloudAvailable());
            });
        });
    }

    private boolean cloudAvailable() {
        return cloudUrl != null && !cloudUrl.getText().toString().trim().isEmpty();
    }

    private String completeCloud(String prompt, String baseUrl, String token) throws Exception {
        String endpoint = baseUrl.trim();
        if (!endpoint.startsWith("https://")) {
            throw new IOException("Use an HTTPS URL for the cloud server to protect your token.");
        }
        if (!endpoint.endsWith("/complete")) {
            while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
            endpoint += "/complete";
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(90000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);

        byte[] body = new JSONObject().put("input", prompt).toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(body);
        }

        int code = connection.getResponseCode();
        InputStream responseStream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = responseStream == null ? "" : new String(readAll(responseStream), StandardCharsets.UTF_8);
        if (responseStream != null) responseStream.close();
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("Cloud server returned HTTP " + code + (response.isEmpty() ? "" : ": " + response));
        }
        return response.isEmpty() ? "Needle cloud server returned an empty response." : response;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        closed = true;
        worker.shutdownNow();
        super.onDestroy();
    }
}
