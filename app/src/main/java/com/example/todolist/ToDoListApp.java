package com.example.todolist;

import android.app.Application;
import android.content.Intent;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ToDoListApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    pw.println("Thread: " + thread.getName());
                    pw.println("Exception: " + throwable);
                    throwable.printStackTrace(pw);
                    pw.flush();

                    try (FileOutputStream fos = openFileOutput("last_crash.txt", MODE_PRIVATE)) {
                        fos.write(sw.toString().getBytes());
                    }
                } catch (Exception ignored) {
                }

                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showCrashIfPresent(android.app.Activity activity) {
        try {
            java.io.File file = new java.io.File(activity.getFilesDir(), "last_crash.txt");
            if (!file.exists()) return;

            String text;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int read = fis.read(data);
                text = new String(data, 0, Math.max(0, read));
            }

            // Clear so it doesn't show every time.
            // If the crash is repeatable, it will be written again.
            //noinspection ResultOfMethodCallIgnored
            file.delete();

            Intent intent = new Intent(activity, FatalErrorActivity.class);
            intent.putExtra(FatalErrorActivity.EXTRA_STACKTRACE, text);
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
