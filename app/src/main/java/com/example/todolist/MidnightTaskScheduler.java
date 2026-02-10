package com.example.todolist;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * Schedules a daily alarm at midnight (12:00 AM) to generate recurring task
 * instances.
 * This ensures that daily/weekly/custom recurring tasks create actual task
 * entries
 * each day at midnight, so users can see them in their calendar and mark them
 * as
 * completed/missed independently.
 */
public class MidnightTaskScheduler {

    private static final String TAG = "MidnightTaskScheduler";
    public static final String ACTION_MIDNIGHT_TASK_GENERATION = "com.example.todolist.ACTION_MIDNIGHT_TASK_GENERATION";
    private static final int MIDNIGHT_ALARM_REQUEST_CODE = 999999;

    private final Context context;

    public MidnightTaskScheduler(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Schedule the daily midnight alarm. Should be called on app startup and boot.
     */
    public void scheduleMidnightAlarm() {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null");
            return;
        }

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_MIDNIGHT_TASK_GENERATION);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Calculate next midnight
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 5); // 5 seconds after midnight for safety
        calendar.set(Calendar.MILLISECOND, 0);

        long triggerTime = calendar.getTimeInMillis();

        // If we're past midnight but it's very early (< 1 minute), trigger now
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) == 0 && now.get(Calendar.MINUTE) == 0) {
            triggerTime = System.currentTimeMillis() + 5000; // 5 seconds from now
        }

        try {
            // Cancel any existing alarm first
            alarmManager.cancel(pendingIntent);

            // Schedule repeating daily alarm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For exact alarms on newer Android versions
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent);
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent);
            }

            Log.d(TAG, "Midnight alarm scheduled for: " + new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(
                            new java.util.Date(triggerTime)));

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException scheduling midnight alarm: " + e.getMessage());
            // Fallback to inexact alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    /**
     * Cancel the midnight alarm.
     */
    public void cancelMidnightAlarm() {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null)
            return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_MIDNIGHT_TASK_GENERATION);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Midnight alarm cancelled");
    }
}
