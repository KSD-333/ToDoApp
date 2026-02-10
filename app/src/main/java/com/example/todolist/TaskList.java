package com.example.todolist;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Task model for Room
 */
@Entity(tableName = "tasks")
public class TaskList {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "check_status")
    public int check;

    @ColumnInfo(name = "task")
    public String task;

    @ColumnInfo(name = "time")
    public String time;

    @ColumnInfo(name = "category")
    public String category = "All";

    @ColumnInfo(name = "is_starred")
    public int isStarred = 0;

    @ColumnInfo(name = "due_date")
    public long dueDate = 0;

    @ColumnInfo(name = "repeat_type")
    public String repeatType = "none";

    @ColumnInfo(name = "repeat_interval")
    public int repeatInterval = 1;

    @ColumnInfo(name = "reminder_minutes")
    public String reminderMinutes = "";

    @ColumnInfo(name = "use_alarm")
    public int useAlarm = 0;

    @ColumnInfo(name = "screen_lock")
    public int screenLock = 0; // 0 = off, 1 = on

    @ColumnInfo(name = "task_time")
    public String taskTime = "";
    @ColumnInfo(name = "completed_at")
    public long completedAt = 0;

    @ColumnInfo(name = "marker_type")
    public String markerType;

    @ColumnInfo(name = "marker_value")
    public String markerValue;

    @ColumnInfo(name = "marker_color")
    public int markerColor = 0;

    @ColumnInfo(name = "attachments")
    public String attachments = "";

    @ColumnInfo(name = "rejection_reason")
    public String rejectionReason = "";

    @ColumnInfo(name = "repeat_days")
    public String repeatDays = "";

    @ColumnInfo(name = "created_from")
    public String createdFrom = "tasks";

    // ID of the parent recurring task (0 if this is the original or non-recurring)
    @ColumnInfo(name = "recurring_parent_id")
    public int recurringParentId = 0;

    @Ignore
    public TaskList(int id, int check, String task, String time) {
        this.id = id;
        this.check = check;
        this.task = task;
        this.time = time;
    }

    @Ignore
    public TaskList(int check, String task, String time) {
        this.check = check;
        this.task = task;
        this.time = time;
    }

    @Ignore
    public TaskList(int check, String task, String time, String category, int isStarred, long dueDate) {
        this.check = check;
        this.task = task;
        this.time = time;
        this.category = category;
        this.isStarred = isStarred;
        this.dueDate = dueDate;
    }

    public TaskList() {
    }

    @Ignore
    public TaskList(String task) {
        this.task = task;
        this.check = 0;
        this.time = "";
    }

    // UI-only fields for date headers (not stored in DB)
    @Ignore
    public boolean isHeader = false;

    @Ignore
    public String headerTitle = "";

    // Getters and setters
    public int getId() {
        return id;
    }

    public int getCheck() {
        return check;
    }

    public void setCheck(int check) {
        this.check = check;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getIsStarred() {
        return isStarred;
    }

    public void setStarred(int isStarred) {
        this.isStarred = isStarred;
    }

    public long getDueDate() {
        return dueDate;
    }

    public void setDueDate(long dueDate) {
        this.dueDate = dueDate;
    }

    public void setStatus(int status) {
        this.check = status;
    }

    public int getStatus() {
        return check;
    }

    public String getRepeatType() {
        return repeatType;
    }

    public void setRepeatType(String repeatType) {
        this.repeatType = repeatType;
    }

    public int getRepeatInterval() {
        return repeatInterval;
    }

    public void setRepeatInterval(int repeatInterval) {
        this.repeatInterval = repeatInterval;
    }

    public String getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(String reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    public int getUseAlarm() {
        return useAlarm;
    }

    public void setUseAlarm(int useAlarm) {
        this.useAlarm = useAlarm;
    }

    public int getScreenLock() {
        return screenLock;
    }

    public void setScreenLock(int screenLock) {
        this.screenLock = screenLock;
    }

    public String getTaskTime() {
        return taskTime;
    }

    public void setTaskTime(String taskTime) {
        this.taskTime = taskTime;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }

    public String getRepeatDays() {
        return repeatDays;
    }

    public void setRepeatDays(String repeatDays) {
        this.repeatDays = repeatDays;
    }

    public String getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(String createdFrom) {
        this.createdFrom = createdFrom;
    }

    public int getRecurringParentId() {
        return recurringParentId;
    }

    public void setRecurringParentId(int recurringParentId) {
        this.recurringParentId = recurringParentId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
