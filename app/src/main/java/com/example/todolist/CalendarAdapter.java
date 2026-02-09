package com.example.todolist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.DayViewHolder> {

    private Context context;
    private List<Calendar> days;
    private Calendar selectedDate;
    private final Set<Long> selectedDays = new HashSet<>(); // normalized start-of-day millis
    private final Set<Long> daysWithTasks = new HashSet<>(); // days that have tasks
    private Calendar today;
    private Calendar currentMonth;
    private OnDayClickListener listener;
    private OnDayLongClickListener longClickListener;

    public interface OnDayClickListener {
        void onDayClick(Calendar day);
    }

    public interface OnDayLongClickListener {
        void onDayLongClick(Calendar day);
    }

    public CalendarAdapter(Context context, List<Calendar> days, OnDayClickListener listener) {
        this.context = context;
        this.days = days;
        this.listener = listener;
        this.today = Calendar.getInstance();
        this.selectedDate = null;
        this.currentMonth = Calendar.getInstance();
    }

    public CalendarAdapter(Context context, List<Calendar> days, OnDayClickListener listener, OnDayLongClickListener longClickListener) {
        this(context, days, listener);
        this.longClickListener = longClickListener;
    }

    public void updateDays(List<Calendar> days) {
        this.days = days;
        if (!days.isEmpty()) {
            // Find the first day of the current month in the list
            for (Calendar day : days) {
                if (day.get(Calendar.DAY_OF_MONTH) == 1) {
                    currentMonth = (Calendar) day.clone();
                    break;
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedDate(Calendar date) {
        if (date == null) {
            this.selectedDate = null;
        } else {
            this.selectedDate = (Calendar) date.clone();
        }
        selectedDays.clear();
        if (this.selectedDate != null) {
            selectedDays.add(normalizeToStartOfDay(this.selectedDate.getTimeInMillis()));
        }
        notifyDataSetChanged();
    }

    public void setSelectedDays(Set<Long> daysStartOfDay) {
        selectedDays.clear();
        if (daysStartOfDay != null) selectedDays.addAll(daysStartOfDay);
        notifyDataSetChanged();
    }
    
    public void setDaysWithTasks(Set<Long> taskDays) {
        daysWithTasks.clear();
        if (taskDays != null) daysWithTasks.addAll(taskDays);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        Calendar day = days.get(position);
        
        holder.tvDay.setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));
        
        boolean isCurrentMonth = day.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH);
        boolean isToday = isSameDay(day, today);
        boolean isSelected = selectedDays.contains(normalizeToStartOfDay(day.getTimeInMillis()));
        
        // Style based on state
        if (isSelected) {
            holder.tvDay.setBackgroundResource(R.drawable.calendar_selected_bg);
            holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.white));
        } else if (isToday) {
            holder.tvDay.setBackgroundResource(R.drawable.calendar_today_faint_bg);
            holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.primary_blue));
        } else {
            holder.tvDay.setBackground(null);
            if (isCurrentMonth) {
                holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            } else {
                holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.text_hint));
            }
        }
        
        // Show task indicator dot if this day has tasks
        long dayMillis = normalizeToStartOfDay(day.getTimeInMillis());
        boolean hasTasks = daysWithTasks.contains(dayMillis);
        if (holder.taskIndicator != null) {
            holder.taskIndicator.setVisibility(hasTasks ? View.VISIBLE : View.GONE);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDayClick(day);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onDayLongClick(day);
                return true;
            }
            return false;
        });
    }

    private boolean isSameDay(Calendar day1, Calendar day2) {
        return day1.get(Calendar.YEAR) == day2.get(Calendar.YEAR) &&
               day1.get(Calendar.MONTH) == day2.get(Calendar.MONTH) &&
               day1.get(Calendar.DAY_OF_MONTH) == day2.get(Calendar.DAY_OF_MONTH);
    }

    private long normalizeToStartOfDay(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay;
        View taskIndicator;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tv_day);
            taskIndicator = itemView.findViewById(R.id.task_indicator);
        }
    }
}
