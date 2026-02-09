package com.example.todolist;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class FatalErrorActivity extends AppCompatActivity {

    public static final String EXTRA_STACKTRACE = "stacktrace";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fatal_error);

        TextView tv = findViewById(R.id.tv_error);
        Button btnCopy = findViewById(R.id.btn_copy);
        Button btnClose = findViewById(R.id.btn_close);

        String trace = getIntent().getStringExtra(EXTRA_STACKTRACE);
        if (trace == null || trace.trim().isEmpty()) {
            trace = "Unknown crash. Please share Logcat.";
        }
        tv.setText(trace);

        String finalTrace = trace;
        btnCopy.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Crash", finalTrace));
                }
            } catch (Exception ignored) {
            }
        });

        btnClose.setOnClickListener(v -> finish());
    }
}
