package com.example.todolist;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class WidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new WidgetRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class WidgetRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private List<TaskList> taskList = new ArrayList<>();
    private DataManager dm;
    private int filterType = 0; // 0=All, 1=Today, 2=Starred, 3=Work, 4=Personal
    private int theme = 0; // 0=Dark, 1=Light

    public WidgetRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        if (intent.hasExtra("filter_type")) {
            filterType = intent.getIntExtra("filter_type", 0);
        }
        if (intent.hasExtra("theme")) {
            theme = intent.getIntExtra("theme", 0);
        }
    }

    @Override
    public void onCreate() {
        try {
            dm = DataManager.getInstance(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDataSetChanged() {
        // Data might have changed in the app process.
        // If this service is running in a different process, or if we need to reload
        // from disk.
        // But DataManager is a singleton.
        // IMPORTANT: Ensure we have permissions to read files if needed.
        long identityToken = android.os.Binder.clearCallingIdentity();
        try {
            if (dm == null)
                dm = DataManager.getInstance(context);

            // Force reload from disk to ensure we have the latest data from the app
            // Note: In a real production app with a shared process, this might not be
            // needed,
            // but if the widget process is separate or if the app was killed, we need to
            // load.
            // Since DataManager constructor loads data, and we just got the instance, it
            // should be loaded.
            // However, if the instance was ALREADY alive in memory but stale (unlikely for
            // singleton in same process),
            // we might want to re-initialize if the file changed.
            // For now, let's assume standard loading is fine, but we wrap in try/catch
            // aggressively.

            // To be absolutely sure, we can trigger a reload in DataManager if we add a
            // method for it.
            // For now, we trust initializeData() was called.

            long todayStart = 0;
            long todayEnd = 0;

            // Simple calculation for today
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            todayStart = cal.getTimeInMillis();
            todayEnd = todayStart + (24 * 60 * 60 * 1000) - 1;

            List<TaskList> all = new ArrayList<>();
            if (dm != null) {
                all = dm.getAllTasks();
            }

            // Fallback: Read manually from JSON if empty (Double check)
            if (all == null || all.isEmpty()) {
                try {
                    java.io.File file = new java.io.File(context.getExternalFilesDir(null),
                            "ToDoList/Tasks/tasks.json");
                    if (!file.exists())
                        file = new java.io.File(context.getFilesDir(), "ToDoList/Tasks/tasks.json");

                    if (file.exists()) {
                        StringBuilder sb = new StringBuilder();
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null)
                                sb.append(line);
                        }
                        org.json.JSONArray array = new org.json.JSONArray(sb.toString());
                        all = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            org.json.JSONObject obj = array.getJSONObject(i);
                            TaskList t = new TaskList();
                            t.id = obj.optInt("id");
                            t.setTask(obj.optString("title"));
                            t.setTime(obj.optString("details"));
                            t.setCategory(obj.optString("category", "All"));
                            t.setDueDate(obj.optLong("dueDate"));
                            t.setStarred(obj.optInt("isStarred"));
                            t.setStatus(obj.optInt("isCompleted"));
                            t.setTaskTime(obj.optString("taskTime", ""));
                            // Basic fields needed for widget
                            all.add(t);
                        }
                    }
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }

            // DEBUG: If still empty, add a dummy task to verify WidgetService is connected
            if (all == null || all.isEmpty()) {
                if (all == null)
                    all = new ArrayList<>();
                TaskList dummy = new TaskList();
                dummy.id = -999;
                dummy.setTask("Widget Connected - No Data");
                dummy.setTime("Tap + to add tasks");
                dummy.check = 0;
                all.add(dummy);
            }

            taskList.clear();

            switch (filterType) {
                case 0: // All Pending
                    for (TaskList t : all) {
                        if (t.check == 0)
                            taskList.add(t);
                    }
                    break;
                case 1: // Today
                    for (TaskList t : all) {
                        if (t.check == 0 && t.dueDate >= todayStart && t.dueDate <= todayEnd)
                            taskList.add(t);
                    }
                    break;
                case 2: // Starred
                    for (TaskList t : all) {
                        if (t.check == 0 && t.isStarred == 1)
                            taskList.add(t);
                    }
                    break;
                case 3: // Work
                    for (TaskList t : all) {
                        if (t.check == 0 && "Work".equalsIgnoreCase(t.category))
                            taskList.add(t);
                    }
                    break;
                case 4: // Personal
                    for (TaskList t : all) {
                        if (t.check == 0 && "Personal".equalsIgnoreCase(t.category))
                            taskList.add(t);
                    }
                    break;
                default: // Fallback all
                    for (TaskList t : all) {
                        if (t.check == 0)
                            taskList.add(t);
                    }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            android.os.Binder.restoreCallingIdentity(identityToken);
        }
    }

    @Override
    public void onDestroy() {
        taskList.clear();
    }

    @Override
    public int getCount() {
        return taskList.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position >= taskList.size())
            return null;

        int layoutId = (theme == 1) ? R.layout.item_widget_task_light : R.layout.item_widget_task;
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        TaskList task = taskList.get(position);

        views.setTextViewText(R.id.tv_widget_task_title, task.task);
        if (task.time != null && !task.time.isEmpty()) {
            views.setTextViewText(R.id.tv_widget_task_time, task.time);
            views.setViewVisibility(R.id.tv_widget_task_time, android.view.View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.tv_widget_task_time, android.view.View.GONE);
        }

        // 1. OPEN TASK (Container Click)
        Intent openFillIn = new Intent();
        openFillIn.setAction(WidgetProviders.BaseListWidgetProvider.ACTION_OPEN_TASK);
        openFillIn.putExtra("task_id", task.id);
        views.setOnClickFillInIntent(R.id.widget_item_container, openFillIn);

        // 2. COMPLETE TASK (Check Button Click)
        Intent completeFillIn = new Intent();
        completeFillIn.setAction(WidgetProviders.BaseListWidgetProvider.ACTION_COMPLETE_TASK);
        completeFillIn.putExtra("task_id", task.id);
        views.setOnClickFillInIntent(R.id.btn_widget_check, completeFillIn);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 2; // Dark and Light
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
