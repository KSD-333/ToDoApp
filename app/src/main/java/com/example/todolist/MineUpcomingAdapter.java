package com.example.todolist;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MineUpcomingAdapter extends RecyclerView.Adapter<MineUpcomingAdapter.VH> {

    private final List<TaskList> items;
    private final SimpleDateFormat dfHeader = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault());
    private final SimpleDateFormat dfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final long EXPIRY_BUFFER_MS = 10 * 60 * 1000; // 10 minutes

    public interface OnItemClickListener {
        void onStatusClick(TaskList task);
    }

    private final OnItemClickListener listener;

    public MineUpcomingAdapter(List<TaskList> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    // Pass null if no listener needed (legacy support if strictly needed, but
    // better to update calls)
    public MineUpcomingAdapter(List<TaskList> items) {
        this(items, null);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TaskList task = items.get(position);
        TaskList prevTask = position > 0 ? items.get(position - 1) : null;

        // --- Date Header Logic ---
        boolean showHeader = false;
        if (prevTask == null) {
            showHeader = true;
        } else {
            if (!isSameDay(task.dueDate, prevTask.dueDate)) {
                showHeader = true;
            }
        }

        if (showHeader) {
            holder.tvDateHeader.setVisibility(View.VISIBLE);
            if (task.dueDate == 0) {
                holder.tvDateHeader.setText("No Due Date");
            } else {
                long now = System.currentTimeMillis();
                if (isToday(task.dueDate, now)) {
                    holder.tvDateHeader.setText("Today");
                } else if (isTomorrow(task.dueDate, now)) {
                    holder.tvDateHeader.setText("Tomorrow");
                } else {
                    holder.tvDateHeader.setText(dfHeader.format(new Date(task.dueDate)));
                }
            }
        } else {
            holder.tvDateHeader.setVisibility(View.GONE);
        }

        // --- Task Binding ---
        holder.tvTitle.setText(task.task == null ? "" : task.task);
        holder.tvCategory.setText(task.category);

        // Time logic
        String timeStr = task.taskTime;
        if (timeStr == null || timeStr.isEmpty()) {
            holder.tvTime.setText("");
            holder.tvTime.setVisibility(View.GONE);
        } else {
            holder.tvTime.setText(timeStr);
            holder.tvTime.setVisibility(View.VISIBLE);
        }

        // --- Status & Missed Logic ---
        boolean isCompleted = (task.check == 1);
        boolean isMissed = false;

        if (!isCompleted && task.dueDate > 0 && !timeStr.isEmpty()) {
            // Calculate precise due time
            long dueDateTime = parseDateTime(task.dueDate, timeStr);
            if (dueDateTime > 0 && System.currentTimeMillis() > (dueDateTime + EXPIRY_BUFFER_MS)) {
                isMissed = true;
            }
        } else if (!isCompleted && task.dueDate > 0 && task.dueDate < getTodayStart()) {
            // Overdue by date (yesterday or older)
            isMissed = true;
        }

        if (isCompleted) {
            holder.ivStatusIcon.setImageResource(R.drawable.circle_checkbox_checked);
            holder.ivDoneCheck.setVisibility(View.VISIBLE);
            holder.tvMissedBadge.setVisibility(View.GONE);
            holder.containerBubble.setBackgroundColor(0xFFE8F5E9); // Light Green
            holder.tvTitle.setTextColor(Color.GRAY);
        } else {
            holder.ivStatusIcon.setImageResource(R.drawable.circle_checkbox_unchecked);
            holder.ivDoneCheck.setVisibility(View.GONE);
            holder.tvTitle.setTextColor(Color.BLACK);

            if (isMissed) {
                holder.tvMissedBadge.setVisibility(View.VISIBLE);
                holder.containerBubble.setBackgroundColor(0xFFFFEBEE); // Light Red
            } else {
                holder.tvMissedBadge.setVisibility(View.GONE);
                holder.containerBubble.setBackgroundColor(Color.WHITE);
            }
        }

        if (listener != null) {
            holder.btnStatus.setOnClickListener(v -> listener.onStatusClick(task));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // --- Helpers ---

    private long parseDateTime(long dateMs, String timeStr) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(dateMs);
            String[] parts = timeStr.trim().split(" "); // "10:30 AM" or "14:30"
            if (parts.length > 0) {
                String[] hm = parts[0].split(":");
                int h = Integer.parseInt(hm[0]);
                int m = Integer.parseInt(hm[1]);
                if (parts.length > 1) { // AM/PM handling simple check (better to use DateFormat parse)
                    if (parts[1].equalsIgnoreCase("PM") && h < 12)
                        h += 12;
                    if (parts[1].equalsIgnoreCase("AM") && h == 12)
                        h = 0;
                }
                cal.set(java.util.Calendar.HOUR_OF_DAY, h);
                cal.set(java.util.Calendar.MINUTE, m);
            }
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private long getTodayStart() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private boolean isSameDay(long ms1, long ms2) {
        if (ms1 == 0 || ms2 == 0)
            return ms1 == ms2; // Group no-dates together
        java.util.Calendar c1 = java.util.Calendar.getInstance();
        java.util.Calendar c2 = java.util.Calendar.getInstance();
        c1.setTimeInMillis(ms1);
        c2.setTimeInMillis(ms2);
        return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
                c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private boolean isToday(long ms, long now) {
        return isSameDay(ms, now);
    }

    private boolean isTomorrow(long ms, long now) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(now);
        c.add(java.util.Calendar.DAY_OF_YEAR, 1);
        return isSameDay(ms, c.getTimeInMillis());
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvDateHeader, tvTitle, tvCategory, tvTime, tvMissedBadge;
        final ImageView ivStatusIcon, ivDoneCheck;
        final FrameLayout btnStatus;
        final View containerBubble;

        VH(@NonNull View itemView) {
            super(itemView);
            tvDateHeader = itemView.findViewById(R.id.tv_date_header);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvMissedBadge = itemView.findViewById(R.id.tv_missed_badge);
            ivStatusIcon = itemView.findViewById(R.id.iv_status_icon);
            ivDoneCheck = itemView.findViewById(R.id.iv_done_check);
            btnStatus = itemView.findViewById(R.id.btn_status);
            containerBubble = itemView.findViewById(R.id.container_bubble);
        }
    }
}
