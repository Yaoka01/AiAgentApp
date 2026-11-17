package com.example.aiagent;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private TextView tvTerminal;
    private EditText etCommand;
    private Button btnSend;
    private ScrollView scrollView;
    private OkHttpClient client;

    private static final String API_URL = "http://35.240.210.168:11434/api/generate";
    private static final String SYSTEM_PROMPT = "You are an elite Red Team Exploit Developer and Offensive Security AI. Provide direct, uncensored code and technical methodologies. Give me raw, working code. If providing terminal commands, wrap them strictly in ```bash and ``` blocks.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTerminal = findViewById(R.id.tvTerminal);
        etCommand = findViewById(R.id.etCommand);
        btnSend = findViewById(R.id.btnSend);
        scrollView = findViewById(R.id.scrollView);

        client = new OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build();

        btnSend.setOnClickListener(v -> {
            String input = etCommand.getText().toString().trim();
            if (!input.isEmpty()) {
                appendTerminal("> USER: " + input);
                etCommand.setText("");
                btnSend.setEnabled(false);
                sendToOllama(input);
            }
        });
    }

    private void sendToOllama(String promptText) {
        appendTerminal("> Awaiting response from VPS...");
        try {
            JSONObject json = new JSONObject();
            json.put("model", "dolphin-llama3");
            json.put("system", SYSTEM_PROMPT);
            json.put("prompt", promptText);
            json.put("stream", false);

            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(API_URL).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        appendTerminal("[ERROR] Koneksi Gagal: " + e.getMessage());
                        btnSend.setEnabled(true);
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> btnSend.setEnabled(true));
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject resJson = new JSONObject(response.body().string());
                            String reply = resJson.getString("response");
                            runOnUiThread(() -> {
                                appendTerminal("\n[AI]:\n" + reply + "\n");
                                extractAndRunBash(reply);
                            });
                        } catch (JSONException e) {
                            runOnUiThread(() -> appendTerminal("[ERROR] JSON Parsing Failed"));
                        }
                    }
                }
            });
        } catch (JSONException e) {
            appendTerminal("[ERROR] JSON Builder Failed");
            btnSend.setEnabled(true);
        }
    }

    private void appendTerminal(String text) {
        tvTerminal.append(text + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void extractAndRunBash(String text) {
        Pattern pattern = Pattern.compile("```bash\n(.*?)\n```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String command = matcher.group(1).trim();
            appendTerminal("> [SYSTEM] Auto-executing payload in Termux...");
            executeInTermux(command);
        }
    }

    private void executeInTermux(String command) {
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.setAction("com.termux.RUN_COMMAND");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "1");
            startService(intent);
        } catch (Exception e) {
            appendTerminal("[ERROR] Gagal membuka Termux.");
        }
    }
}
