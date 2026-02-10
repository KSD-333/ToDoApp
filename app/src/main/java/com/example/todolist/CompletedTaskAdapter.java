package com.example.todolist;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompletedTaskAdapter extends RecyclerView.Adapter<CompletedTaskAdapter.CompletedTaskViewHolder> {

    private final List<TaskList> tasks;
    private final Map<String, String> categoryColors;
    private final Context context;
    private final SimpleDateFormat completedFormat = new SimpleDateFormat("EEE, dd MMM yyyy, HH:mm",
            Locale.getDefault());
    private final SimpleDateFormat dueDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    public CompletedTaskAdapter(Context context, List<TaskList> tasks, Map<String, String> categoryColors) {
        this.context = context;
        this.tasks = tasks;
        this.categoryColors = categoryColors;
    }

    @NonNull
    @Override
    public CompletedTaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_completed_task, parent, false);
        return new CompletedTaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CompletedTaskViewHolder holder, int position) {
        TaskList task = tasks.get(position);

        holder.tvTaskName.setText(task.getTask());

        // Category Chip
        String category = task.getCategory();
        if (category == null || category.isEmpty()) {
            category = "No Category";
        }
        holder.chipCategory.setText(category);

        // Set chip color
        String colorHex = categoryColors != null ? categoryColors.get(category) : "#2196F3";
        if (colorHex == null)
            colorHex = "#2196F3";
        try {
            int color = Color.parseColor(colorHex);
            holder.chipCategory.setChipBackgroundColor(ColorStateList.valueOf(color));
        } catch (IllegalArgumentException e) {
            holder.chipCategory.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#2196F3")));
        }

        // Completed Date
        if (task.getCompletedAt() > 0) {
            holder.tvCompletedDate.setText(completedFormat.format(task.getCompletedAt()));
            holder.tvCompletedDate.setVisibility(View.VISIBLE);
        } else {
            holder.tvCompletedDate.setText("Unknown");
        }

        // Original Due Date
        if (task.getDueDate() > 0) {
            holder.tvDueDate.setText(dueDateFormat.format(task.getDueDate()));
            holder.tvDueDate.setVisibility(View.VISIBLE);
        } else {
            holder.tvDueDate.setText("No Due Date");
        }

        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(context, TaskDetailActivity.class);
            intent.putExtra("task_id", task.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class CompletedTaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTaskName, tvCompletedDate, tvDueDate;
        Chip chipCategory;

        public CompletedTaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTaskName = itemView.findViewById(R.id.tv_task_name);
            chipCategory = itemView.findViewById(R.id.chip_category);
            tvCompletedDate = itemView.findViewById(R.id.tv_completed_date);
            tvDueDate = itemView.findViewById(R.id.tv_due_date);
        }
    }
}
