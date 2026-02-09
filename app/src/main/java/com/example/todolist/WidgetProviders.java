package com.example.todolist;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class WidgetProviders {

    public static abstract class BaseListWidgetProvider extends AppWidgetProvider {
        protected abstract int getFilterType();

        protected abstract int getThemeType(); // 0=Dark, 1=Light

        protected abstract int getLayoutId();

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            for (int appWidgetId : appWidgetIds) {
                try {
                    updateAppWidget(context, appWidgetManager, appWidgetId, getFilterType(), getThemeType(),
                            getLayoutId());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public static final String ACTION_COMPLETE_TASK = "com.example.todolist.ACTION_COMPLETE_TASK";
        public static final String ACTION_OPEN_TASK = "com.example.todolist.ACTION_OPEN_TASK";

        @Override
        public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);

            if (ACTION_OPEN_TASK.equals(intent.getAction())) {
                int taskId = intent.getIntExtra("task_id", -1);
                if (taskId != -1) {
                    Intent openIntent = new Intent(context, MainActivity.class);
                    openIntent.putExtra("task_id", taskId); // Should be handled in MainActivity to open details
                    openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(openIntent);
                }
                return;
            }

            if (ACTION_COMPLETE_TASK.equals(intent.getAction())) {
                int taskId = intent.getIntExtra("task_id", -1);
                int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                if (taskId != -1) {
                    try {
                        DataManager dm = DataManager.getInstance(context);
                        // Mark task as completed
                        dm.updateStatusAndCompletedAt(taskId, 1, System.currentTimeMillis());

                        // Show feedback
                        android.widget.Toast.makeText(context, "Task completed", android.widget.Toast.LENGTH_SHORT)
                                .show();

                        // Force update all widgets to reflect change
                        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, getClass()));
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.lv_widget_tasks);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId,
                int filterType, int themeType, int layoutId) {
            try {
                RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

                // Set up title based on fitler
                String title = "Tasks";
                String categoryToOpen = null;
                switch (filterType) {
                    case 1:
                        title = "Today";
                        break;
                    case 2:
                        title = "Starred";
                        break;
                    case 3:
                        title = "Work";
                        categoryToOpen = "Work";
                        break;
                    case 4:
                        title = "Personal";
                        categoryToOpen = "Personal";
                        break;
                }
                views.setTextViewText(R.id.tv_widget_title, title);

                // Set up List Intent
                Intent intent = new Intent(context, WidgetService.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra("filter_type", filterType);
                intent.putExtra("theme", themeType);
                // Use a unique data URI effectively to differentiate this widget's adapter
                intent.setData(Uri.parse("widgets://com.example.todolist/widget/id/" + appWidgetId + "_" + filterType
                        + "_" + themeType));

                views.setRemoteAdapter(R.id.lv_widget_tasks, intent);
                views.setEmptyView(R.id.lv_widget_tasks, R.id.widget_empty_view);

                // Click pending intent for LIST ITEMS (Template)
                // We need separate templates for the container and the check button?
                // Actually, `setPendingIntentTemplate` sets one template for the whole list.
                // We distinguish actions via fillInIntent.
                // It is cleaner to use a single PendingIntent pointing to a receiver or
                // activity that handles "commands".

                // Let's use a broadcast receiver for handling item clicks specifically
                // But we want the ROW click to open Activity, and CHECK click to complete.
                // We can't easily have two different PendingIntentTemplates for the same
                // listview.
                // RemoteViews only allows one `setPendingIntentTemplate` per ID
                // (lv_widget_tasks).
                // Solution: Set the template to a generic "WidgetActionReceiver" or
                // "WidgetProviders" class
                // and distinguish actions in the fillInIntent.

                // Update: To support opening Activity cleanly, we'll point the template to our
                // WidgetProvider
                // and then inside onReceive, we'll re-dispatch to Activity if it's an "Open"
                // action.
                // OR we point to an Activity that handles both (but that opens UI).

                // Better approach for dual actions:
                // Point PendingIntentTemplate to the WidgetProvider class (BroadcastReceiver).
                // Inside onReceive:
                // If action is "OPEN_TASK", start Activity.
                // If action is "COMPLETE_TASK", update DB.

                Intent templateIntent = new Intent(context, getClass()); // Point to THIS provider class
                // We need to set action to something unique or handle default
                templateIntent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
                PendingIntent templatePendingIntent = PendingIntent.getBroadcast(context, 0, templateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                // Note: MUTABLE required for fillInIntent to work on some versions?
                // Actually, IMMUTABLE causes fillIn to fail on newer Android if not handled
                // carefully.
                // Safe bet: FLAG_UPDATE_CURRENT | FLAG_MUTABLE for templates that accept fillIn
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    templatePendingIntent = PendingIntent.getBroadcast(context, 0, templateIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                }
                views.setPendingIntentTemplate(R.id.lv_widget_tasks, templatePendingIntent);

                // Add button intent
                Intent addIntent = new Intent(context, MainActivity.class);
                addIntent.putExtra("open_add_task", true); // Handle this in MainActivity
                if (categoryToOpen != null) {
                    addIntent.putExtra("category_name", categoryToOpen);
                }
                addIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent addPendingIntent = PendingIntent.getActivity(context, appWidgetId + 1000, addIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.btn_widget_add, addPendingIntent);

                appWidgetManager.updateAppWidget(appWidgetId, views);
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_widget_tasks);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ========================================================================================
    // 15 Widget Implementation Variants
    // ========================================================================================

    // ----- ALL TASKS -----
    public static class AllDark extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 0;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_dark;
        }
    }

    public static class AllLight extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 0;
        }

        @Override
        protected int getThemeType() {
            return 1;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_light;
        }
    }

    public static class AllTrans extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 0;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_trans;
        }
    }

    // ----- TODAY -----
    public static class TodayDark extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 1;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_dark;
        }
    }

    public static class TodayLight extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 1;
        }

        @Override
        protected int getThemeType() {
            return 1;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_light;
        }
    }

    public static class TodayTrans extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 1;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_trans;
        }
    }

    // ----- STARRED -----
    public static class StarredDark extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 2;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_dark;
        }
    }

    public static class StarredLight extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 2;
        }

        @Override
        protected int getThemeType() {
            return 1;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_light;
        }
    }

    public static class StarredTrans extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 2;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_trans;
        }
    }

    // ----- WORK -----
    public static class WorkDark extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 3;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_dark;
        }
    }

    public static class WorkLight extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 3;
        }

        @Override
        protected int getThemeType() {
            return 1;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_light;
        }
    }

    public static class WorkTrans extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 3;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_trans;
        }
    }

    // ----- PERSONAL -----
    public static class PersonalDark extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 4;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_dark;
        }
    }

    public static class PersonalLight extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 4;
        }

        @Override
        protected int getThemeType() {
            return 1;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_light;
        }
    }

    public static class PersonalTrans extends BaseListWidgetProvider {
        @Override
        protected int getFilterType() {
            return 4;
        }

        @Override
        protected int getThemeType() {
            return 0;
        }

        @Override
        protected int getLayoutId() {
            return R.layout.widget_list_trans;
        }
    }
}
