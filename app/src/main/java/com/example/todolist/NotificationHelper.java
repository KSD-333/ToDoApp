package com.example.todolist;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Calendar;

public class NotificationHelper {

    private static final String CHANNEL_ID = "todolist_reminders";
    private static final String CHANNEL_NAME = "Task Reminders";
    private static final String CHANNEL_DESC = "Notifications for task reminders";

    private static final String ALARM_CHANNEL_ID = "todolist_alarms";
    private static final String ALARM_CHANNEL_NAME = "Task Alarms";
    private static final String ALARM_CHANNEL_DESC = "Alarm notifications for important tasks";

    private Context context;
    private AlarmManager alarmManager;
    private NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Regular notification channel
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);

            // Alarm channel with alarm sound
            NotificationChannel alarmChannel = new NotificationChannel(
                    ALARM_CHANNEL_ID,
                    ALARM_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            alarmChannel.setDescription(ALARM_CHANNEL_DESC);
            alarmChannel.enableVibration(true);
            alarmChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null);

            notificationManager.createNotificationChannel(channel);
            notificationManager.createNotificationChannel(alarmChannel);
        }
    }

    /**
     * Schedule reminders for a task
     * 
     * @param taskId          The task ID
     * @param taskName        The task name
     * @param dueDate         The due date timestamp
     * @param taskTime        The time in HH:mm format
     * @param reminderMinutes Comma-separated list of reminder minutes (e.g.,
     *                        "5,10,15")
     * @param useAlarm        Whether to use alarm sound
     * @param useScreenLock   Whether to show on lock screen
     */
    public void scheduleReminders(int taskId, String taskName, long dueDate, String taskTime,
            String reminderMinutes, boolean useAlarm, boolean useScreenLock) {

        // Handle "No Date" (0) by assuming Today if Time is set
        long effectiveDueDate = dueDate;
        boolean isNoDate = (dueDate == 0);

        if (isNoDate) {
            if (taskTime != null && !taskTime.isEmpty()) {
                Calendar nowCal = Calendar.getInstance();
                nowCal.set(Calendar.HOUR_OF_DAY, 0);
                nowCal.set(Calendar.MINUTE, 0);
                nowCal.set(Calendar.SECOND, 0);
                nowCal.set(Calendar.MILLISECOND, 0);
                effectiveDueDate = nowCal.getTimeInMillis();
            } else {
                Log.d("NotificationHelper", "No date or time set, skipping reminders");
                return;
            }
        } else if (taskTime == null || taskTime.isEmpty()) {
            // Date is set but no time -> skip
            Log.d("NotificationHelper", "No time set, skipping reminders");
            return;
        }

        if (reminderMinutes == null || reminderMinutes.isEmpty()) {
            Log.d("NotificationHelper", "No reminders set");
            return;
        }

        // Parse the task time (handles both "7:00" and "7:00 AM" formats)
        try {
            int hour;
            int minute;

            // Check if it's AM/PM format
            boolean isPM = taskTime.toUpperCase().contains("PM");
            boolean isAM = taskTime.toUpperCase().contains("AM");

            // Remove AM/PM suffix for parsing
            String cleanTime = taskTime.toUpperCase().replace(" AM", "").replace(" PM", "").replace("AM", "")
                    .replace("PM", "").trim();

            String[] timeParts = cleanTime.split(":");
            if (timeParts.length != 2) {
                Log.e("NotificationHelper", "Invalid time format: " + taskTime);
                return;
            }

            hour = Integer.parseInt(timeParts[0].trim());
            minute = Integer.parseInt(timeParts[1].trim());

            // Convert to 24-hour format if AM/PM was specified
            if (isPM && hour != 12) {
                hour += 12;
            } else if (isAM && hour == 12) {
                hour = 0;
            }

            // Create the full due datetime
            Calendar dueDateTime = Calendar.getInstance();
            dueDateTime.setTimeInMillis(effectiveDueDate);
            dueDateTime.set(Calendar.HOUR_OF_DAY, hour);
            dueDateTime.set(Calendar.MINUTE, minute);
            dueDateTime.set(Calendar.SECOND, 0);
            dueDateTime.set(Calendar.MILLISECOND, 0);

            // Parse interval if present
            String[] configParts = reminderMinutes.split("\\|interval:");
            String standardReminders = configParts[0];
            int intervalMinutes = 0;
            if (configParts.length > 1) {
                try {
                    intervalMinutes = Integer.parseInt(configParts[1].trim());
                } catch (NumberFormatException e) {
                    Log.e("NotificationHelper", "Invalid interval format");
                }
            }

            // Schedule each standard reminder
            if (!standardReminders.isEmpty()) {
                String[] reminderArray = standardReminders.split(",");
                for (int i = 0; i < reminderArray.length; i++) {
                    try {
                        int mins = Integer.parseInt(reminderArray[i].trim());
                        long reminderTime = dueDateTime.getTimeInMillis() - (mins * 60 * 1000L);
                        long now = System.currentTimeMillis();

                        // Only schedule if in the future
                        if (reminderTime > now) {
                            scheduleAlarm(taskId * 100 + i, taskName, reminderTime, mins, useAlarm, useScreenLock);
                            Log.d("NotificationHelper",
                                    "Scheduled reminder for " + mins + " mins before at " + reminderTime);
                        } else {
                            // Catch-up logic: If reminder missed recently (within 30 mins)
                            // AND the task itself is not completely ancient (e.g. within 2 hours of due
                            // time)
                            long timeSinceReminder = now - reminderTime;
                            boolean isRecentMiss = timeSinceReminder > 0 && timeSinceReminder < 30 * 60 * 1000L;

                            if (isRecentMiss) {
                                Log.d("NotificationHelper",
                                        "Reminder caught up: firing immediately for " + mins + " min reminder");
                                showNotification(context, taskId * 100 + i, taskName, mins, useAlarm, useScreenLock);
                            } else {
                                Log.d("NotificationHelper",
                                        "Reminder time " + reminderTime + " passed too long ago or invalid");
                            }
                        }
                    } catch (NumberFormatException e) {
                        Log.e("NotificationHelper", "Invalid reminder minutes: " + reminderArray[i]);
                    }
                }
            }

            // Schedule interval reminders
            if (intervalMinutes > 0) {
                Calendar endOfDay = Calendar.getInstance();
                endOfDay.setTimeInMillis(dueDate);
                endOfDay.set(Calendar.HOUR_OF_DAY, 23);
                endOfDay.set(Calendar.MINUTE, 59);
                endOfDay.set(Calendar.SECOND, 59);

                long nextTime = dueDateTime.getTimeInMillis() + (intervalMinutes * 60 * 1000L);
                int count = 0;
                // Schedule up to 40 intervals or until end of day
                while (nextTime <= endOfDay.getTimeInMillis() && count < 40) {
                    if (nextTime > System.currentTimeMillis()) {
                        // Use offset 50+ for interval alarms
                        scheduleAlarm(taskId * 100 + 50 + count, taskName, nextTime, 0, useAlarm, useScreenLock);
                        Log.d("NotificationHelper", "Scheduled interval reminder " + count + " at " + nextTime);
                    }
                    nextTime += (intervalMinutes * 60 * 1000L);
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e("NotificationHelper", "Error parsing time: " + taskTime + " - " + e.getMessage());
        }
    }

    private void scheduleAlarm(int requestCode, String taskName, long triggerTime, int minutesBefore, boolean useAlarm,
            boolean useScreenLock) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("task_name", taskName);
        intent.putExtra("minutes_before", minutesBefore);
        intent.putExtra("use_alarm", useAlarm);
        intent.putExtra("use_screen_lock", useScreenLock);
        intent.putExtra("notification_id", requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            // Use setAlarmClock for maximum reliability and to show in status
            // bar/lockscreen
            // This is the strongest API for "reminders" that must fire exactly.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(triggerTime,
                        pendingIntent);
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (SecurityException e) {
            Log.e("NotificationHelper", "Cannot schedule exact alarm: " + e.getMessage());
            // Fall back to inexact alarm if permission denied
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } catch (Exception e) {
            Log.e("NotificationHelper", "Error scheduling alarm: " + e.getMessage());
        }
    }

    /**
     * Cancel all reminders for a task
     */
    public void cancelReminders(int taskId, int maxReminders) {
        for (int i = 0; i < maxReminders; i++) {
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId * 100 + i,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * Cancel reminders for a task using a safe default range.
     */
    public void cancelReminders(int taskId) {
        cancelReminders(taskId, 100);
    }

    /**
     * Show a notification immediately (used by AlarmReceiver)
     */
    public static void showNotification(Context context, int notificationId, String taskName,
            int minutesBefore, boolean useAlarm, boolean useScreenLock) {
        String channelId = useAlarm ? ALARM_CHANNEL_ID : CHANNEL_ID;

        String message = minutesBefore == 0
                ? "Task is due now!"
                : "Task is due in " + minutesBefore + " minutes";

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // STOP action for alarm notifications
        Intent stopIntent = new Intent(context, AlarmReceiver.class);
        stopIntent.setAction("com.example.todolist.ACTION_STOP_ALARM");
        stopIntent.putExtra("notification_id", notificationId);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                context, notificationId + 100000, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("📋 " + taskName)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(useAlarm ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Enable screen lock features: show over lock screen, turn screen on
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            builder.setFullScreenIntent(pendingIntent, useAlarm || useScreenLock);
        }

        if (useAlarm) {
            // Add STOP action to notification
            builder.addAction(R.drawable.ic_close, "Stop", stopPendingIntent);
            // Don't set sound on notification - we'll play it manually for 30 seconds
            builder.setSound(null);
            builder.setVibrate(new long[] { 0, 500, 200, 500, 200, 500 });
            // Play alarm sound for 30 seconds
            playAlarmSound(context, 30000);
        } else {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            builder.setVibrate(new long[] { 0, 250, 100, 250 });
        }

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, builder.build());
    }

    private static MediaPlayer alarmMediaPlayer;
    private static android.os.PowerManager.WakeLock wakeLock;

    /**
     * Play alarm sound for specified duration
     */
    private static void playAlarmSound(Context context, long durationMs) {
        try {
            // Stop any existing alarm
            stopAlarmSound();

            // Acquire WakeLock to keep CPU running while alarm plays
            android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ToDoList:AlarmWakeLock");
            wakeLock.acquire(durationMs + 5000); // Acquire with timeout slightly longer than duration

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            alarmMediaPlayer = new MediaPlayer();
            alarmMediaPlayer.setDataSource(context, alarmUri);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            alarmMediaPlayer.setAudioAttributes(audioAttributes);

            alarmMediaPlayer.setLooping(true);
            alarmMediaPlayer.prepare();
            alarmMediaPlayer.start();

            // Stop after duration (30 seconds)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                stopAlarmSound();
            }, durationMs);

            Log.d("NotificationHelper", "Alarm sound started for " + durationMs + "ms");
        } catch (Exception e) {
            Log.e("NotificationHelper", "Error playing alarm sound: " + e.getMessage());
            // Ensure wakelock is released if error occurs
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
        }
    }

    /**
     * Stop the alarm sound
     */
    public static void stopAlarmSound() {
        if (alarmMediaPlayer != null) {
            try {
                if (alarmMediaPlayer.isPlaying()) {
                    alarmMediaPlayer.stop();
                }
                alarmMediaPlayer.release();
                alarmMediaPlayer = null;
                Log.d("NotificationHelper", "Alarm sound stopped");
            } catch (Exception e) {
                Log.e("NotificationHelper", "Error stopping alarm: " + e.getMessage());
            }
        }

        // Release WakeLock
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            } catch (Exception e) {
                Log.e("NotificationHelper", "Error releasing wakelock: " + e.getMessage());
            }
            wakeLock = null;
        }
    }

    /**
     * Reschedule all reminders from the database.
     * Use this on boot or app startup to ensure alarms are synced.
     */
    public void rescheduleAllReminders() {
        new Thread(() -> {
            try {
                DataManager dm = DataManager.getInstance(context);
                java.util.List<TaskList> tasks = dm.getAllTasks();
                long now = System.currentTimeMillis();
                int count = 0;

                for (TaskList task : tasks) {
                    // Check if task is pending and due in the future (or recently past for
                    // catch-up)
                    // We check if due date is within the last 24 hours or future
                    if (task.check == 0) {
                        boolean isFutureDate = task.dueDate > (now - 86400000);
                        boolean isNoDateWithTime = (task.dueDate == 0 && task.taskTime != null
                                && !task.taskTime.isEmpty());

                        if (isFutureDate || isNoDateWithTime) {
                            boolean hasReminders = task.reminderMinutes != null && !task.reminderMinutes.isEmpty();
                            boolean useAlarm = task.useAlarm == 1;

                            if (hasReminders || useAlarm) {
                                scheduleReminders(
                                        task.id,
                                        task.task,
                                        task.dueDate,
                                        task.taskTime,
                                        task.reminderMinutes,
                                        useAlarm,
                                        task.screenLock == 1);
                                count++;
                            }
                        }
                    }
                }
                Log.d("NotificationHelper", "Rescheduled " + count + " alarms from DB");
            } catch (Exception e) {
                Log.e("NotificationHelper", "Error rescheduling alarms: " + e.getMessage());
            }
        }).start();
    }
}
