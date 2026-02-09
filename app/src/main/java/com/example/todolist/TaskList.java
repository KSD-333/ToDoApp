package com.example.todolist;

/**
 * Simple Task model (Room annotations removed)
 */
public class TaskList {

    public int id;
    public int check;
    public String task;
    public String time;
    public String category = "All";
    public int isStarred = 0;
    public long dueDate = 0;
    public String repeatType = "none";
    public int repeatInterval = 1;
    public String reminderMinutes = "";
    public int useAlarm = 0;
    public int screenLock = 0; // 0 = off, 1 = on
    public String taskTime = "";
    public long completedAt = 0;
    public String markerType;
    public String markerValue;
    public int markerColor = 0;
    public String attachments = ""; // Comma-separated file paths
    public String repeatDays = ""; // Comma-separated day indices (0-6, Sun-Sat)
    public String createdFrom = "tasks"; // Source: "tasks" or "calendar"

    public TaskList(int id, int check, String task, String time) {
        this.id = id;
        this.check = check;
        this.task = task;
        this.time = time;
    }

    public TaskList(int check, String task, String time) {
        this.check = check;
        this.task = task;
        this.time = time;
    }

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

    public TaskList(String task) {
        this.task = task;
        this.check = 0;
        this.time = "";
    }

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
}
