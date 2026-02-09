package com.example.todolist;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskStorageManager {

    private static final String APP_FOLDER = "ToDoList";
    private static final String TASKS_FOLDER = "Tasks";
    private static final String CATEGORIES_FOLDER = "Categories";
    private static final String BACKUPS_FOLDER = "Backups";

    private final Context context;
    private DataManager dm;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TaskStorageManager(Context context) {
        this.context = context.getApplicationContext();
    }

    private DataManager getDataManager() {
        if (dm == null) {
            dm = DataManager.getInstance(context);
        }
        return dm;
    }

    private void showToast(String message, int duration) {
        if (message == null)
            return;
        mainHandler.post(() -> Toast.makeText(context, message, duration).show());
    }

    /**
     * Creates the main app folder structure in external storage
     */
    private File getAppDirectory() {
        File baseExternal = context.getExternalFilesDir(null);
        File baseDir = (baseExternal != null) ? baseExternal : context.getFilesDir();
        File appDir = new File(baseDir, APP_FOLDER);

        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        // Create subfolders
        new File(appDir, TASKS_FOLDER).mkdirs();
        new File(appDir, CATEGORIES_FOLDER).mkdirs();
        new File(appDir, BACKUPS_FOLDER).mkdirs();

        return appDir;
    }

    /**
     * Exports all tasks to a JSON file organized by categories
     */
    public boolean exportAllTasks() {
        return exportAllTasks(true);
    }

    /**
     * Exports all tasks. Set showToasts=false for silent/background exports.
     */
    public boolean exportAllTasks(boolean showToasts) {
        try {
            File appDir = getAppDirectory();
            File tasksDir = new File(appDir, TASKS_FOLDER);

            // Create timestamp for file
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String timestamp = sdf.format(new Date());

            // Export by categories
            List<Category> categories = getDataManager().getAllCategories();

            for (Category category : categories) {
                List<TaskList> tasks = getDataManager().getTasksByCategory(category.getName());

                if (!tasks.isEmpty()) {
                    JSONObject categoryJson = new JSONObject();
                    categoryJson.put("category", category.getName());
                    categoryJson.put("color", category.getColor());
                    categoryJson.put("exportDate", timestamp);

                    JSONArray tasksArray = new JSONArray();
                    for (TaskList task : tasks) {
                        JSONObject taskJson = new JSONObject();
                        taskJson.put("id", task.getId());
                        taskJson.put("title", task.getTask());
                        taskJson.put("details", task.getTime());
                        taskJson.put("category", task.getCategory());
                        taskJson.put("dueDate", task.getDueDate());
                        taskJson.put("isStarred", task.getIsStarred());
                        taskJson.put("isCompleted", task.getCheck());
                        taskJson.put("repeatType", task.getRepeatType());
                        taskJson.put("repeatInterval", task.getRepeatInterval());

                        // Add subtasks
                        List<SubTask> subtasks = getDataManager().getSubTasksForTask(task.getId());
                        if (!subtasks.isEmpty()) {
                            JSONArray subtasksArray = new JSONArray();
                            for (SubTask subtask : subtasks) {
                                JSONObject subtaskJson = new JSONObject();
                                subtaskJson.put("title", subtask.title);
                                subtaskJson.put("isCompleted", subtask.isCompleted);
                                subtasksArray.put(subtaskJson);
                            }
                            taskJson.put("subtasks", subtasksArray);
                        }

                        tasksArray.put(taskJson);
                    }
                    categoryJson.put("tasks", tasksArray);
                    categoryJson.put("taskCount", tasks.size());

                    // Save to file
                    String fileName = category.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_" + timestamp + ".json";
                    File categoryFile = new File(tasksDir, fileName);

                    try (FileWriter writer = new FileWriter(categoryFile)) {
                        writer.write(categoryJson.toString(4)); // Pretty print with indent 4
                    }
                }
            }

            // Create master summary file
            createSummaryFile(timestamp);

            if (showToasts) {
                showToast("Tasks exported successfully", Toast.LENGTH_LONG);
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            if (showToasts) {
                showToast("Export failed: " + e.getMessage(), Toast.LENGTH_LONG);
            }
            return false;
        }
    }

    /**
     * Creates a summary file with overview of all tasks
     */
    private void createSummaryFile(String timestamp) throws JSONException, IOException {
        File appDir = getAppDirectory();

        JSONObject summary = new JSONObject();
        summary.put("exportDate", timestamp);
        summary.put("appVersion", "1.0");
        summary.put("totalTasks", getDataManager().getAllTasks().size());
        summary.put("completedTasks", getDataManager().getCompletedTaskCount());
        summary.put("pendingTasks", getDataManager().getPendingTaskCount());
        summary.put("starredTasks", getDataManager().getStarredTasks().size());

        // Category breakdown
        JSONArray categoriesArray = new JSONArray();
        List<Category> categories = getDataManager().getAllCategories();
        for (Category category : categories) {
            JSONObject catObj = new JSONObject();
            catObj.put("name", category.getName());
            catObj.put("taskCount", getDataManager().getTasksByCategory(category.getName()).size());
            categoriesArray.put(catObj);
        }
        summary.put("categories", categoriesArray);

        File summaryFile = new File(appDir, "summary_" + timestamp + ".json");
        try (FileWriter writer = new FileWriter(summaryFile)) {
            writer.write(summary.toString(4));
        }
    }

    /**
     * Saves current state of all data to permanent storage (files)
     */
    public void saveData(List<TaskList> tasks, List<Category> categories, List<SubTask> subTasks) {
        try {
            File appDir = getAppDirectory();

            // Save Tasks
            JSONArray tasksArray = new JSONArray();
            for (TaskList task : tasks) {
                JSONObject taskJson = new JSONObject();
                taskJson.put("id", task.getId());
                taskJson.put("title", task.getTask());
                taskJson.put("details", task.getTime());
                taskJson.put("category", task.getCategory());
                taskJson.put("dueDate", task.getDueDate());
                taskJson.put("isStarred", task.getIsStarred());
                taskJson.put("isCompleted", task.getCheck());
                taskJson.put("repeatType", task.getRepeatType());
                taskJson.put("repeatInterval", task.getRepeatInterval());
                taskJson.put("reminderMinutes", task.getReminderMinutes());
                taskJson.put("useAlarm", task.getUseAlarm());
                taskJson.put("screenLock", task.getScreenLock());
                taskJson.put("taskTime", task.getTaskTime());
                taskJson.put("completedAt", task.getCompletedAt());
                taskJson.put("attachments", task.getAttachments());
                taskJson.put("repeatDays", task.getRepeatDays());
                taskJson.put("createdFrom", task.getCreatedFrom());
                tasksArray.put(taskJson);
            }
            writeFile(new File(appDir, "tasks.json"), tasksArray.toString(4));

            // Save Categories
            JSONArray categoriesArray = new JSONArray();
            for (Category category : categories) {
                JSONObject catJson = new JSONObject();
                catJson.put("id", category.getId());
                catJson.put("name", category.getName());
                catJson.put("color", category.getColor());
                catJson.put("isDefault", category.isDefault() ? 1 : 0);
                categoriesArray.put(catJson);
            }
            writeFile(new File(appDir, "categories.json"), categoriesArray.toString(4));

            // Save SubTasks
            JSONArray subTasksArray = new JSONArray();
            for (SubTask subTask : subTasks) {
                JSONObject stJson = new JSONObject();
                stJson.put("id", subTask.id);
                stJson.put("parentTaskId", subTask.parentTaskId);
                stJson.put("title", subTask.title);
                stJson.put("isCompleted", subTask.isCompleted);
                subTasksArray.put(stJson);
            }
            writeFile(new File(appDir, "subtasks.json"), subTasksArray.toString(4));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public java.util.ArrayList<TaskList> loadTasks() {
        java.util.ArrayList<TaskList> list = new java.util.ArrayList<>();
        try {
            File file = new File(getAppDirectory(), "tasks.json");
            if (!file.exists())
                return list;

            String jsonStr = readFile(file);
            JSONArray array = new JSONArray(jsonStr);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                TaskList t = new TaskList();
                t.id = obj.optInt("id");
                t.setTask(obj.optString("title"));
                t.setTime(obj.optString("details"));
                t.setCategory(obj.optString("category", "All"));
                t.setDueDate(obj.optLong("dueDate"));
                t.setStarred(obj.optInt("isStarred"));
                t.setStatus(obj.optInt("isCompleted"));
                t.setRepeatType(obj.optString("repeatType", "none"));
                t.setRepeatInterval(obj.optInt("repeatInterval", 1));
                t.setReminderMinutes(obj.optString("reminderMinutes"));
                t.setUseAlarm(obj.optInt("useAlarm"));
                t.setScreenLock(obj.optInt("screenLock"));
                t.setTaskTime(obj.optString("taskTime"));
                t.setCompletedAt(obj.optLong("completedAt"));
                t.setAttachments(obj.optString("attachments"));
                t.setRepeatDays(obj.optString("repeatDays"));
                t.setCreatedFrom(obj.optString("createdFrom", "tasks"));
                list.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public java.util.ArrayList<Category> loadCategories() {
        java.util.ArrayList<Category> list = new java.util.ArrayList<>();
        try {
            File file = new File(getAppDirectory(), "categories.json");
            if (!file.exists())
                return list;

            String jsonStr = readFile(file);
            JSONArray array = new JSONArray(jsonStr);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Category c = new Category(
                        obj.optString("name"),
                        obj.optString("color"),
                        obj.optInt("isDefault"));
                c.id = obj.optInt("id");
                list.add(c);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public java.util.ArrayList<SubTask> loadSubTasks() {
        java.util.ArrayList<SubTask> list = new java.util.ArrayList<>();
        try {
            File file = new File(getAppDirectory(), "subtasks.json");
            if (!file.exists())
                return list;

            String jsonStr = readFile(file);
            JSONArray array = new JSONArray(jsonStr);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                SubTask st = new SubTask(
                        obj.optInt("parentTaskId"),
                        obj.optString("title"));
                st.id = obj.optInt("id");
                st.isCompleted = obj.optInt("isCompleted");
                list.add(st);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Creates a full backup of all data
     */
    public boolean createBackup() {
        try {
            File appDir = getAppDirectory();
            File backupsDir = new File(appDir, BACKUPS_FOLDER);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String timestamp = sdf.format(new Date());

            JSONObject backup = new JSONObject();
            backup.put("backupDate", timestamp);
            backup.put("version", "1.0");

            // Export all tasks
            JSONArray allTasks = new JSONArray();
            List<TaskList> tasks = getDataManager().getAllTasks();
            for (TaskList task : tasks) {
                JSONObject taskJson = new JSONObject();
                taskJson.put("id", task.getId());
                taskJson.put("title", task.getTask());
                taskJson.put("details", task.getTime());
                taskJson.put("category", task.getCategory());
                taskJson.put("dueDate", task.getDueDate());
                taskJson.put("isStarred", task.getIsStarred());
                taskJson.put("isCompleted", task.getCheck());
                taskJson.put("repeatType", task.getRepeatType());
                taskJson.put("repeatInterval", task.getRepeatInterval());
                allTasks.put(taskJson);
            }
            backup.put("tasks", allTasks);

            // Export categories
            JSONArray categoriesArray = new JSONArray();
            List<Category> categories = getDataManager().getAllCategories();
            for (Category category : categories) {
                JSONObject catJson = new JSONObject();
                catJson.put("name", category.getName());
                catJson.put("color", category.getColor());
                catJson.put("isDefault", category.isDefault());
                categoriesArray.put(catJson);
            }
            backup.put("categories", categoriesArray);

            File backupFile = new File(backupsDir, "backup_" + timestamp + ".json");

            try (FileWriter writer = new FileWriter(backupFile)) {
                writer.write(backup.toString(4));
            }

            showToast("Backup created successfully", Toast.LENGTH_SHORT);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            showToast("Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT);
            return false;
        }
    }

    /**
     * Gets the app storage directory path for display
     */
    public String getStoragePath() {
        return new File(Environment.getExternalStorageDirectory(), APP_FOLDER).getAbsolutePath();
    }
}
