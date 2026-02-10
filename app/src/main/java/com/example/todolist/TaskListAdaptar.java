package com.example.todolist;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskListAdaptar extends RecyclerView.Adapter<TaskListAdaptar.viewHolder> {

    // Progress style constants
    public static final int STYLE_FILL = 0;
    public static final int STYLE_BAR = 1;
    public static final int STYLE_BADGE = 2;

    private static final String PREF_NAME = "subtask_prefs";
    private static final String KEY_PROGRESS_STYLE = "progress_style";
    private static final String KEY_SHOW_NAMES = "show_names";
    private static final String KEY_PROGRESS_COLOR = "progress_color";

    // Color options
    public static final int COLOR_GREEN = 0;
    public static final int COLOR_BLUE = 1;
    public static final int COLOR_ORANGE = 2;
    public static final int COLOR_PURPLE = 3;
    public static final int COLOR_RED = 4;
    public static final int COLOR_TEAL = 5;

    // View types
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_TASK = 1;

    Context context;
    ArrayList<TaskList> tasklist;
    DataManager dm;
    private OnTaskCheckedListener listener;
    private OnMarkerClickListener markerClickListener;
    private SharedPreferences prefs;

    private int openSwipeTaskId = -1;
    private float openSwipeTranslationX = 0f;

    public interface OnTaskCheckedListener {
        void onCheck(TaskList task, int position);
    }

    public interface OnMarkerClickListener {
        void onMarkerClick(TaskList task, int position);
    }

    public TaskListAdaptar(Context context, ArrayList<TaskList> tasklist, OnTaskCheckedListener listener) {
        this.context = context;
        this.tasklist = tasklist;
        this.dm = DataManager.getInstance(context);
        this.listener = listener;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getProgressStyle() {
        return prefs.getInt(KEY_PROGRESS_STYLE, STYLE_FILL); // Default to FILL
    }

    public void setProgressStyle(int style) {
        prefs.edit().putInt(KEY_PROGRESS_STYLE, style).apply();
        notifyDataSetChanged();
    }

    public boolean getShowSubtaskNames() {
        return prefs.getBoolean(KEY_SHOW_NAMES, true); // Default to true
    }

    public void setShowSubtaskNames(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_NAMES, show).apply();
        notifyDataSetChanged();
    }

    public int getProgressColor() {
        return prefs.getInt(KEY_PROGRESS_COLOR, COLOR_GREEN); // Default to green
    }

    public void setProgressColor(int color) {
        prefs.edit().putInt(KEY_PROGRESS_COLOR, color).apply();
        notifyDataSetChanged();
    }

    public int getProgressColorValue() {
        switch (getProgressColor()) {
            case COLOR_BLUE:
                return Color.parseColor("#2196F3");
            case COLOR_ORANGE:
                return Color.parseColor("#FF9800");
            case COLOR_PURPLE:
                return Color.parseColor("#9C27B0");
            case COLOR_RED:
                return Color.parseColor("#F44336");
            case COLOR_TEAL:
                return Color.parseColor("#009688");
            case COLOR_GREEN:
            default:
                return Color.parseColor("#4CAF50");
        }
    }

    public void setOnMarkerClickListener(OnMarkerClickListener listener) {
        this.markerClickListener = listener;
    }

    public void setOpenSwipeTask(int taskId, float translationX) {
        this.openSwipeTaskId = taskId;
        this.openSwipeTranslationX = translationX;
    }

    @Override
    public int getItemViewType(int position) {
        TaskList item = tasklist.get(position);
        return item.isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_TASK;
    }

    @NonNull
    @Override
    public TaskListAdaptar.viewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_task_header, parent, false);
            return new viewHolder(view, true);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.activity_task_list, parent, false);
            return new viewHolder(view, false);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull TaskListAdaptar.viewHolder holder,
            @SuppressLint("RecyclerView") int position) {
        TaskList task = tasklist.get(position);

        // If it's a header, just set the title and return
        if (task.isHeader) {
            if (holder.tvHeaderTitle != null) {
                holder.tvHeaderTitle.setText(task.headerTitle);
            }
            return;
        }

        // Keep swipe-open rows stable during recycling.
        try {
            if (task != null && task.id == openSwipeTaskId) {
                holder.itemView.setTranslationX(openSwipeTranslationX);
            } else {
                holder.itemView.setTranslationX(0f);
            }

            // Overdue background logic
            if (holder.itemView instanceof androidx.cardview.widget.CardView) {
                androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) holder.itemView;
                // Get start of today
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long todayStart = cal.getTimeInMillis();

                if ((task.check == 0 && task.dueDate > 0 && task.dueDate < todayStart) || task.check == 2) {
                    card.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                } else {
                    card.setCardBackgroundColor(Color.parseColor("#E6FFFFFF"));
                }
            }
        } catch (Exception ignored) {
        }

        holder.taskname.setText(task.task);

        // Set category if available
        if (holder.taskcategory != null && task.category != null && !task.category.isEmpty()) {
            holder.taskcategory.setText(task.category);
            holder.taskcategory.setVisibility(View.VISIBLE);
        } else if (holder.taskcategory != null) {
            holder.taskcategory.setVisibility(View.GONE);
        }

        holder.taskcheck.setOnCheckedChangeListener(null);
        holder.taskcheck.setChecked(task.check == 1);

        // Strike through if completed
        if (task.check == 1) {
            holder.taskname.setPaintFlags(holder.taskname.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.taskname.setTextColor(ContextCompat.getColor(context, R.color.text_hint));
        } else if (task.check == 2) {
            // Missed task
            holder.taskname.setPaintFlags(holder.taskname.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.taskname.setTextColor(Color.RED);
            // Optional: Append (Missed) if not already clear from color
            // holder.taskname.setText(task.task + " (Missed)");
            // Better to rely on color or a badge, but let's stick to color for now.
        } else {
            holder.taskname.setPaintFlags(holder.taskname.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.taskname.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }

        // Show task details (date, time, repeat, star)
        boolean hasDetails = false;

        // Star corner badge indicator (new triangle style)
        if (holder.ivStarBadge != null && holder.ivStarIconBadge != null) {
            if (task.isStarred == 1) {
                holder.ivStarBadge.setVisibility(View.VISIBLE);
                holder.ivStarIconBadge.setVisibility(View.VISIBLE);
            } else {
                holder.ivStarBadge.setVisibility(View.GONE);
                holder.ivStarIconBadge.setVisibility(View.GONE);
            }
        }

        // Hide the old inline star icon
        if (holder.ivStar != null) {
            holder.ivStar.setVisibility(View.GONE);
        }

        // Due date
        if (holder.dueDateContainer != null && task.dueDate > 0) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            holder.tvDueDate.setText(dateFormat.format(new Date(task.dueDate)));
            holder.dueDateContainer.setVisibility(View.VISIBLE);
            hasDetails = true;
        } else if (holder.dueDateContainer != null) {
            holder.dueDateContainer.setVisibility(View.GONE);
        }

        // Task time
        if (holder.taskTimeContainer != null && task.taskTime != null && !task.taskTime.isEmpty()) {
            holder.tvTaskTime.setText(task.taskTime);
            holder.taskTimeContainer.setVisibility(View.VISIBLE);
            hasDetails = true;
        } else if (holder.taskTimeContainer != null) {
            holder.taskTimeContainer.setVisibility(View.GONE);
        }

        // Repeat
        if (holder.repeatContainer != null && task.repeatType != null && !task.repeatType.equals("none")) {
            String repeatText = getRepeatText(task.repeatType, task.repeatInterval);
            holder.tvRepeat.setText(repeatText);
            holder.repeatContainer.setVisibility(View.VISIBLE);
            hasDetails = true;
        } else if (holder.repeatContainer != null) {
            holder.repeatContainer.setVisibility(View.GONE);
        }

        // Show/hide details row
        if (holder.taskDetailsRow != null) {
            holder.taskDetailsRow.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        }

        // Subtask section with multiple progress styles
        List<SubTask> subtasks = dm.getSubTasksForTask(task.id);
        boolean hasSubtasks = subtasks != null && !subtasks.isEmpty();

        if (hasSubtasks) {
            int completed = 0;
            for (SubTask st : subtasks) {
                if (st.isCompleted == 1)
                    completed++;
            }
            int percentInt = (int) ((completed * 100.0f) / subtasks.size());
            float percent = (completed * 100.0f) / subtasks.size();

            int progressStyle = getProgressStyle();
            boolean showNames = getShowSubtaskNames();
            int progressColor = getProgressColorValue();

            // Reset all progress indicators
            if (holder.progressFillBg != null)
                holder.progressFillBg.setVisibility(View.GONE);
            if (holder.bottomProgressContainer != null)
                holder.bottomProgressContainer.setVisibility(View.GONE);
            if (holder.subtaskSection != null)
                holder.subtaskSection.setVisibility(View.GONE);
            if (holder.ivProgressBadge != null)
                holder.ivProgressBadge.setVisibility(View.GONE);
            if (holder.tvProgressPercent != null)
                holder.tvProgressPercent.setVisibility(View.GONE);
            if (holder.tvSubtaskCount != null)
                holder.tvSubtaskCount.setVisibility(View.GONE);

            final int finalCompleted = completed;
            final int totalSubtasks = subtasks.size();

            // Always show the progress badge at top right corner (like star badge)
            if (holder.ivProgressBadge != null) {
                // Set badge color using tint
                holder.ivProgressBadge.setColorFilter(progressColor, android.graphics.PorterDuff.Mode.SRC_IN);
                holder.ivProgressBadge.setVisibility(View.VISIBLE);

                // Set percentage text
                if (holder.tvProgressPercent != null) {
                    holder.tvProgressPercent.setText(percentInt + "%");
                    holder.tvProgressPercent.setVisibility(View.VISIBLE);
                }

                // Set count text
                if (holder.tvSubtaskCount != null) {
                    holder.tvSubtaskCount.setText(finalCompleted + "/" + totalSubtasks);
                    holder.tvSubtaskCount.setVisibility(View.VISIBLE);
                }
            }

            // Apply selected progress style (fill or bar)
            switch (progressStyle) {
                case STYLE_FILL:
                    // Fill background from left
                    if (holder.progressFillBg != null) {
                        // Set fill background color
                        GradientDrawable fillBg = new GradientDrawable();
                        fillBg.setShape(GradientDrawable.RECTANGLE);
                        fillBg.setColors(new int[] {
                                adjustAlpha(progressColor, 0.15f),
                                adjustAlpha(progressColor, 0.25f)
                        });
                        fillBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                        holder.progressFillBg.setBackground(fillBg);
                        holder.progressFillBg.setVisibility(View.VISIBLE);
                        holder.progressFillBg.post(() -> {
                            ViewGroup parent = (ViewGroup) holder.progressFillBg.getParent();
                            int parentWidth = parent.getWidth();
                            int newWidth = (int) (parentWidth * (percent / 100.0f));
                            ViewGroup.LayoutParams lp = holder.progressFillBg.getLayoutParams();
                            lp.width = newWidth;
                            holder.progressFillBg.setLayoutParams(lp);
                        });
                    }
                    break;

                case STYLE_BAR:
                    // Bottom progress bar
                    if (holder.bottomProgressContainer != null && holder.bottomProgressFill != null) {
                        holder.bottomProgressContainer.setVisibility(View.VISIBLE);
                        // Set bar color
                        GradientDrawable barBg = new GradientDrawable();
                        barBg.setShape(GradientDrawable.RECTANGLE);
                        barBg.setCornerRadii(new float[] { 0, 0, 0, 0, 12, 12, 12, 12 });
                        barBg.setColor(progressColor);
                        holder.bottomProgressFill.setBackground(barBg);
                        holder.bottomProgressFill.post(() -> {
                            ViewGroup parent = (ViewGroup) holder.bottomProgressFill.getParent();
                            int parentWidth = parent.getWidth();
                            int newWidth = (int) (parentWidth * (percent / 100.0f));
                            ViewGroup.LayoutParams lp = holder.bottomProgressFill.getLayoutParams();
                            lp.width = newWidth;
                            holder.bottomProgressFill.setLayoutParams(lp);
                        });
                    }
                    break;

                case STYLE_BADGE:
                    // Badge only - already shown above
                    break;
            }

            // Show subtask names if enabled
            if (showNames && holder.subtaskSection != null && holder.subtasksList != null) {
                holder.subtasksList.removeAllViews();

                // Show ALL subtasks so user can complete them from main list
                for (int i = 0; i < subtasks.size(); i++) {
                    SubTask st = subtasks.get(i);
                    View subtaskView = LayoutInflater.from(context).inflate(R.layout.item_subtask_mini,
                            holder.subtasksList, false);

                    View circle = subtaskView.findViewById(R.id.subtask_circle);
                    ImageView checkIcon = subtaskView.findViewById(R.id.subtask_check_icon);
                    TextView title = subtaskView.findViewById(R.id.tv_subtask_title);
                    View progressIndicator = subtaskView.findViewById(R.id.subtask_progress_indicator);

                    // Hide individual progress indicator (we're showing overall progress)
                    if (progressIndicator != null) {
                        progressIndicator.setVisibility(View.GONE);
                    }

                    title.setText(st.title);

                    if (st.isCompleted == 1) {
                        // Set completed circle with selected color
                        GradientDrawable checkedCircle = new GradientDrawable();
                        checkedCircle.setShape(GradientDrawable.OVAL);
                        checkedCircle.setColor(progressColor);
                        circle.setBackground(checkedCircle);
                        if (checkIcon != null)
                            checkIcon.setVisibility(View.VISIBLE);
                        title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                        title.setTextColor(ContextCompat.getColor(context, R.color.text_hint));
                    } else {
                        circle.setBackgroundResource(R.drawable.subtask_circle_small_unchecked);
                        if (checkIcon != null)
                            checkIcon.setVisibility(View.GONE);
                        title.setPaintFlags(title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                        title.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                    }

                    // Click to toggle subtask
                    final SubTask finalSt = st;
                    subtaskView.setOnClickListener(v -> {
                        finalSt.isCompleted = finalSt.isCompleted == 1 ? 0 : 1;
                        dm.updateSubTask(finalSt);
                        notifyItemChanged(position);
                    });

                    holder.subtasksList.addView(subtaskView);
                }

                holder.subtaskSection.setVisibility(View.VISIBLE);
            }

        } else {
            // No subtasks - hide all indicators
            if (holder.progressFillBg != null)
                holder.progressFillBg.setVisibility(View.GONE);
            if (holder.bottomProgressContainer != null)
                holder.bottomProgressContainer.setVisibility(View.GONE);
            if (holder.subtaskSection != null)
                holder.subtaskSection.setVisibility(View.GONE);
            if (holder.ivProgressBadge != null)
                holder.ivProgressBadge.setVisibility(View.GONE);
            if (holder.tvProgressPercent != null)
                holder.tvProgressPercent.setVisibility(View.GONE);
            if (holder.tvSubtaskCount != null)
                holder.tvSubtaskCount.setVisibility(View.GONE);
        }

        // Render marker (always visible on right)
        if (holder.markerContainer != null) {
            renderMarker(holder, task);

            holder.markerContainer.setOnClickListener(v -> {
                if (markerClickListener != null) {
                    markerClickListener.onMarkerClick(task, position);
                }
            });
        }

        holder.taskcheck.setOnClickListener(v -> {
            boolean isChecked = ((CheckBox) v).isChecked();
            int status = isChecked ? 1 : 0;
            task.check = status;
            long completedAt = status == 1 ? System.currentTimeMillis() : 0;
            task.completedAt = completedAt;
            dm.updateStatusAndCompletedAt(task.id, status, completedAt);

            if (listener != null) {
                listener.onCheck(task, position);
            }
        });

        // Tap opens Task Details screen
        holder.itemView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, TaskDetailActivity.class);
                intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String getRepeatText(String repeatType, int interval) {
        if (repeatType != null && repeatType.equals("custom_days")) {
            return "Custom";
        }
        if (interval == 1) {
            switch (repeatType) {
                case "days":
                    return "Daily";
                case "weeks":
                    return "Weekly";
                case "months":
                    return "Monthly";
                case "years":
                    return "Yearly";
                default:
                    return repeatType;
            }
        } else {
            return "Every " + interval + " " + repeatType;
        }
    }

    // Long-press menu removed (requested)

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    public int getItemCount() {
        return tasklist.size();
    }

    public static class viewHolder extends RecyclerView.ViewHolder {

        // Header view
        TextView tvHeaderTitle;

        // Task views
        TextView taskname, tasktime, taskcategory;
        TextView tvDueDate, tvTaskTime, tvRepeat;
        LinearLayout taskDetailsRow, dueDateContainer, taskTimeContainer, repeatContainer;
        LinearLayout subtaskSection;
        LinearLayout subtasksList;
        View progressFillBg;
        View bottomProgressFill;
        FrameLayout bottomProgressContainer;
        // New progress badge views (corner style like star badge)
        ImageView ivProgressBadge;
        TextView tvProgressPercent;
        TextView tvSubtaskCount;
        CheckBox taskcheck;
        FrameLayout markerContainer;
        ImageView ivMarkerIcon;
        ImageView ivStar;
        ImageView ivStarBadge;
        ImageView ivStarIconBadge;
        TextView tvMarkerText;

        // Constructor for header
        public viewHolder(@NonNull View itemView, boolean isHeader) {
            super(itemView);
            if (isHeader) {
                tvHeaderTitle = itemView.findViewById(R.id.tv_header_title);
                return;
            }
            // Otherwise initialize task views (same as regular constructor)
            initTaskViews(itemView);
        }

        // Constructor for task (backward compatibility)
        public viewHolder(@NonNull View itemView) {
            super(itemView);
            initTaskViews(itemView);
        }

        private void initTaskViews(View itemView) {

            taskcheck = itemView.findViewById(R.id.taskcheck);
            taskname = itemView.findViewById(R.id.taskname);
            tasktime = itemView.findViewById(R.id.tasktime);
            taskcategory = itemView.findViewById(R.id.taskcategory);

            markerContainer = itemView.findViewById(R.id.marker_container);
            ivMarkerIcon = itemView.findViewById(R.id.iv_marker_icon);
            tvMarkerText = itemView.findViewById(R.id.tv_marker_text);
            ivStar = itemView.findViewById(R.id.iv_star);
            ivStarBadge = itemView.findViewById(R.id.iv_star_badge);
            ivStarIconBadge = itemView.findViewById(R.id.iv_star_icon_badge);

            // New detail views
            taskDetailsRow = itemView.findViewById(R.id.task_details_row);
            dueDateContainer = itemView.findViewById(R.id.due_date_container);
            taskTimeContainer = itemView.findViewById(R.id.task_time_container);
            repeatContainer = itemView.findViewById(R.id.repeat_container);
            tvDueDate = itemView.findViewById(R.id.tv_due_date);
            tvTaskTime = itemView.findViewById(R.id.tv_task_time);
            tvRepeat = itemView.findViewById(R.id.tv_repeat);

            // Subtask views - new layout
            subtaskSection = itemView.findViewById(R.id.subtask_section);
            subtasksList = itemView.findViewById(R.id.subtasks_list);
            progressFillBg = itemView.findViewById(R.id.progress_fill_bg);
            bottomProgressContainer = itemView.findViewById(R.id.bottom_progress_container);
            bottomProgressFill = itemView.findViewById(R.id.bottom_progress_fill);

            // New progress badge (corner style)
            ivProgressBadge = itemView.findViewById(R.id.iv_progress_badge);
            tvProgressPercent = itemView.findViewById(R.id.tv_progress_percent);
            tvSubtaskCount = itemView.findViewById(R.id.tv_subtask_count);
        }
    }

    private void renderMarker(@NonNull viewHolder holder, @NonNull TaskList task) {
        try {
            String type = task.markerType == null ? "" : task.markerType;
            String value = task.markerValue == null ? "" : task.markerValue;
            int color = task.markerColor;

            if (holder.ivMarkerIcon == null || holder.tvMarkerText == null)
                return;

            if (type.isEmpty()) {
                holder.ivMarkerIcon.setVisibility(View.VISIBLE);
                holder.ivMarkerIcon.setImageResource(R.drawable.ic_flag_outline);
                holder.ivMarkerIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_hint));
                holder.tvMarkerText.setVisibility(View.GONE);
                return;
            }

            if (type.equals("flag")) {
                holder.ivMarkerIcon.setVisibility(View.VISIBLE);
                holder.ivMarkerIcon.setImageResource(R.drawable.ic_flag);
                int tint = color != 0 ? color : ContextCompat.getColor(context, R.color.primary_blue);
                holder.ivMarkerIcon.setColorFilter(tint);
                holder.tvMarkerText.setVisibility(View.GONE);
                return;
            }

            // priority, status, number, progress, mood use text bubble
            holder.ivMarkerIcon.setVisibility(View.GONE);
            holder.tvMarkerText.setVisibility(View.VISIBLE);

            if (type.equals("priority")) {
                // Show priority label with color
                holder.tvMarkerText.setText(value.substring(0, 1)); // First letter (U, H, M, L)
                int bg = color != 0 ? color : ContextCompat.getColor(context, R.color.primary_blue);
                holder.tvMarkerText.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
                holder.tvMarkerText.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else if (type.equals("status")) {
                // Show status with color badge
                String shortValue = value.length() > 2 ? value.substring(0, 2) : value;
                holder.tvMarkerText.setText(shortValue);
                int bg = color != 0 ? color : ContextCompat.getColor(context, R.color.primary_blue);
                holder.tvMarkerText.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
                holder.tvMarkerText.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else if (type.equals("number")) {
                holder.tvMarkerText.setText(value);
                int bg = color != 0 ? color : ContextCompat.getColor(context, R.color.primary_blue);
                holder.tvMarkerText.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
                holder.tvMarkerText.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else if (type.equals("progress")) {
                holder.tvMarkerText.setText(value);
                int bg = color != 0 ? color : ContextCompat.getColor(context, R.color.chip_inactive_bg);
                holder.tvMarkerText.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
                holder.tvMarkerText.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else if (type.equals("mood")) {
                holder.tvMarkerText.setText(value);
                holder.tvMarkerText.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.chip_inactive_bg)));
                holder.tvMarkerText.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            } else {
                holder.tvMarkerText.setText("");
                holder.tvMarkerText.setVisibility(View.GONE);
                holder.ivMarkerIcon.setVisibility(View.VISIBLE);
                holder.ivMarkerIcon.setImageResource(R.drawable.ic_flag_outline);
                holder.ivMarkerIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_hint));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
