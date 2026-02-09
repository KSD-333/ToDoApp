package com.example.todolist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received!");
        String action = intent.getAction();
        if ("com.example.todolist.ACTION_STOP_ALARM".equals(action)) {
            int notificationId = intent.getIntExtra("notification_id", 0);
            NotificationManager notificationManager = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
            NotificationHelper.stopAlarmSound();
            Log.d(TAG, "Alarm stopped by user");
            return;
        }

        String taskName = intent.getStringExtra("task_name");
        int minutesBefore = intent.getIntExtra("minutes_before", 0);
        boolean useAlarm = intent.getBooleanExtra("use_alarm", false);
        boolean useScreenLock = intent.getBooleanExtra("use_screen_lock", false);
        int notificationId = intent.getIntExtra("notification_id", 0);

        if (taskName == null) {
            taskName = "Task Reminder";
        }

        // Show the notification
        NotificationHelper.showNotification(context, notificationId, taskName, minutesBefore, useAlarm, useScreenLock);
    }
}
