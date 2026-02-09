package com.example.todolist;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class WidgetInfoActivity extends AppCompatActivity {

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_info);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        getSupportActionBar().setTitle("Widget");

        RecyclerView recyclerView = findViewById(R.id.recycler_widgets);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<GroupItem> groups = new ArrayList<>();

        // Standard (All Tasks)
        GroupItem standard = new GroupItem("Standard", "4*4", WidgetProviders.AllLight.class);
        standard.addVariant(R.layout.widget_list_light, "Light"); // Light
        standard.addVariant(R.layout.widget_list_dark, "Dark"); // Dark
        groups.add(standard);

        // Lite (Today)
        GroupItem today = new GroupItem("Lite", "3*2", WidgetProviders.TodayLight.class);
        today.addVariant(R.layout.widget_list_light, "Light");
        today.addVariant(R.layout.widget_list_dark, "Dark");
        groups.add(today);

        // Starred
        GroupItem starred = new GroupItem("Starred", "4*2", WidgetProviders.StarredLight.class);
        starred.addVariant(R.layout.widget_list_light, "Light");
        starred.addVariant(R.layout.widget_list_dark, "Dark");
        groups.add(starred);

        // Work
        GroupItem work = new GroupItem("Work", "4*2", WidgetProviders.WorkLight.class);
        work.addVariant(R.layout.widget_list_light, "Light");
        work.addVariant(R.layout.widget_list_dark, "Dark");
        groups.add(work);

        // Personal
        GroupItem personal = new GroupItem("Personal", "4*2", WidgetProviders.PersonalLight.class);
        personal.addVariant(R.layout.widget_list_light, "Light");
        personal.addVariant(R.layout.widget_list_dark, "Dark");
        groups.add(personal);

        recyclerView.setAdapter(new WidgetPreviewAdapter(groups));
    }

    private static class WidgetVariant {
        int layoutId;
        String label;

        WidgetVariant(int layoutId, String label) {
            this.layoutId = layoutId;
            this.label = label;
        }
    }

    private static class GroupItem {
        String title;
        String size;
        Class<?> primaryProvider;
        List<WidgetVariant> variants = new ArrayList<>();

        GroupItem(String title, String size, Class<?> primaryProvider) {
            this.title = title;
            this.size = size;
            this.primaryProvider = primaryProvider;
        }

        void addVariant(int layoutId, String label) {
            variants.add(new WidgetVariant(layoutId, label));
        }
    }

    private class WidgetPreviewAdapter extends RecyclerView.Adapter<WidgetPreviewAdapter.VH> {
        List<GroupItem> list;

        WidgetPreviewAdapter(List<GroupItem> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_widget_preview, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            GroupItem item = list.get(position);
            holder.tvName.setText(item.title);
            holder.tvSize.setText("Size: " + item.size);

            holder.layoutPreviews.removeAllViews();

            for (WidgetVariant variant : item.variants) {
                // Inflate a container for the preview (CardView wrapper for clean look)
                androidx.cardview.widget.CardView previewCard = new androidx.cardview.widget.CardView(
                        holder.itemView.getContext());
                previewCard.setRadius(dpToPx(holder.itemView.getContext(), 8));
                previewCard.setCardElevation(dpToPx(holder.itemView.getContext(), 2));

                // Container size: 100dp x 100dp
                int containerSizeDp = 100;
                android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                        dpToPx(holder.itemView.getContext(), containerSizeDp),
                        dpToPx(holder.itemView.getContext(), containerSizeDp));
                cardParams.setMarginEnd(dpToPx(holder.itemView.getContext(), 8));
                previewCard.setLayoutParams(cardParams);

                // IMPORTANT: Scaled FrameLayout
                // We want to show a "miniature" version of the widget.
                // If we squish constraints into 100dp, it breaks.
                // Solution: Render at 200dp x 200dp, then scale to 0.5.
                float scaleFactor = 0.5f;
                int renderSizeDp = (int) (containerSizeDp / scaleFactor); // 200dp

                android.widget.FrameLayout scalerFrame = new android.widget.FrameLayout(holder.itemView.getContext());
                scalerFrame.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                        dpToPx(holder.itemView.getContext(), renderSizeDp),
                        dpToPx(holder.itemView.getContext(), renderSizeDp)));

                scalerFrame.setPivotX(0);
                scalerFrame.setPivotY(0);
                scalerFrame.setScaleX(scaleFactor);
                scalerFrame.setScaleY(scaleFactor);

                previewCard.addView(scalerFrame);

                try {
                    View widgetView = LayoutInflater.from(holder.itemView.getContext()).inflate(variant.layoutId,
                            scalerFrame, false);

                    // Ensure widget view fills the render area
                    android.widget.FrameLayout.LayoutParams widgetParams = new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    widgetView.setLayoutParams(widgetParams);

                    // Populate dummy data
                    populateDummyData(widgetView, item.title, variant.layoutId);

                    scalerFrame.addView(widgetView);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                holder.layoutPreviews.addView(previewCard);
            }

            holder.btnAdd.setOnClickListener(v -> requestPinWidget(item.primaryProvider));
        }

        private void populateDummyData(View widgetView, String titleStr, int layoutId) {
            TextView title = widgetView.findViewById(R.id.tv_widget_title);
            if (title != null) {
                if (titleStr.equals("Lite"))
                    title.setText("Today");
                else if (titleStr.equals("Standard"))
                    title.setText("My Tasks");
                else
                    title.setText(titleStr);
            }

            android.widget.ListView lv = widgetView.findViewById(R.id.lv_widget_tasks);
            if (lv != null) {
                List<String> dummy = new ArrayList<>();
                dummy.add("Morning jogging");
                dummy.add("Have lunch with Jenny");
                dummy.add("Send email to Tim");
                dummy.add("Supermarket shopping");

                int itemLayout = (layoutId == R.layout.widget_list_light) ? R.layout.item_widget_task_light
                        : R.layout.item_widget_task;

                android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                        widgetView.getContext(), itemLayout, R.id.tv_widget_task_title, dummy) {
                    @NonNull
                    @Override
                    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        TextView time = v.findViewById(R.id.tv_widget_task_time);
                        if (time != null)
                            time.setText("12:30");
                        return v;
                    }
                };
                lv.setAdapter(adapter);
            }
        }

        private int dpToPx(Context context, int dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvSize;
            com.google.android.material.button.MaterialButton btnAdd;
            android.widget.LinearLayout layoutPreviews;

            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_widget_title_header);
                tvSize = v.findViewById(R.id.tv_widget_size);
                btnAdd = v.findViewById(R.id.btn_add_widget);
                layoutPreviews = v.findViewById(R.id.layout_previews);
            }
        }
    }

    private void requestPinWidget(Class<?> providerClass) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(this, providerClass);

            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                Intent pinnedWidgetCallbackIntent = new Intent(this, MainActivity.class);
                PendingIntent successCallback = PendingIntent.getActivity(this, 0, pinnedWidgetCallbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback);
                Toast.makeText(this, "Request to pin " + providerClass.getSimpleName() + " sent", Toast.LENGTH_SHORT)
                        .show();
            } else {
                Toast.makeText(this, "Pinning not supported on this launcher", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Requires Android 8.0+", Toast.LENGTH_SHORT).show();
        }
    }
}
