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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView output;
    private EditText input;
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
                send.setEnabled(ok);
                if (!ok) output.setText(finalResult + "\n\nIf this persists, check the GitHub Actions build log.");
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
        send.setEnabled(false);
        status.setText("Running locally…");
        output.setText("Processing…");
        worker.execute(() -> {
            String result;
            try { result = NeedleNative.complete(prompt); }
            catch (Throwable t) { result = "ERROR: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()); }
            final String finalResult = result;
            main.post(() -> {
                if (closed) return;
                output.setText(finalResult);
                status.setText(NeedleNative.isReady() ? "Needle ready • local inference" : "Needle unavailable");
                send.setEnabled(NeedleNative.isReady());
            });
        });
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
