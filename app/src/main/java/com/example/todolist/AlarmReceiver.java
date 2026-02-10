package com.example.todolist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;
import android.util.Log;
import java.util.List;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Boot completed, rescheduling alarms...");
            rescheduleAlarms(context);
            // Also schedule midnight alarm and generate today's tasks
            MidnightTaskScheduler scheduler = new MidnightTaskScheduler(context);
            scheduler.scheduleMidnightAlarm();
            DataManager.getInstance(context).generateTodaysRecurringTasks();
            return;
        }

        // Handle midnight task generation alarm
        if (MidnightTaskScheduler.ACTION_MIDNIGHT_TASK_GENERATION.equals(action)) {
            Log.d(TAG, "Midnight alarm triggered - generating today's recurring tasks");
            try {
                DataManager dm = DataManager.getInstance(context);
                dm.generateTodaysRecurringTasks();

                // Reschedule for next midnight
                MidnightTaskScheduler scheduler = new MidnightTaskScheduler(context);
                scheduler.scheduleMidnightAlarm();

                Log.d(TAG, "Today's recurring tasks generated successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error generating today's recurring tasks: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        Log.d(TAG, "Alarm received!");

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

    private void rescheduleAlarms(Context context) {
        try {
            DataManager dm = DataManager.getInstance(context);
            List<TaskList> tasks = dm.getAllTasks();
            NotificationHelper helper = new NotificationHelper(context);

            long now = System.currentTimeMillis();
            int count = 0;

            for (TaskList task : tasks) {
                // Check if task is pending and due in the future (or today)
                if (task.check == 0 && task.dueDate > (now - 86400000)) {
                    boolean hasReminders = task.reminderMinutes != null && !task.reminderMinutes.isEmpty();
                    boolean useAlarm = task.useAlarm == 1;

                    if (hasReminders || useAlarm) {
                        try {
                            helper.scheduleReminders(
                                    task.id,
                                    task.task,
                                    task.dueDate,
                                    task.taskTime,
                                    task.reminderMinutes,
                                    useAlarm,
                                    task.screenLock == 1);
                            count++;
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to schedule for task " + task.id + ": " + e.getMessage());
                        }
                    }
                }
            }
            Log.d(TAG, "Rescheduled " + count + " alarms");
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling alarms: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
