package com.example.todolist;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CalendarFragment extends Fragment {

    private RecyclerView calendarGrid;
    private RecyclerView recyclerDayTasks;
    private LinearLayout calendarEmptyState;
    private TextView tvMonthYear, tvDropdownMonthYear, tvSelectedDateHeader;
    private ImageButton btnPrevMonth, btnNextMonth;
    private LinearLayout monthYearDropdown;

    private Calendar currentCalendar;
    private Calendar selectedDate;
    private final Set<Long> selectedDates = new LinkedHashSet<>(); // normalized start-of-day millis
    private DataManager dm;
    private CalendarAdapter calendarAdapter;
    private TaskListAdaptar taskAdapter;
    private ArrayList<TaskList> dayTasks;

    private static final String[] MONTH_NAMES = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private static long normalizeToStartOfDay(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public CalendarFragment() {
        // Required empty public constructor
    }

    public static CalendarFragment newInstance() {
        return new CalendarFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            dm = DataManager.getInstance(requireContext());
            currentCalendar = Calendar.getInstance();
            selectedDate = Calendar.getInstance();
            dayTasks = new ArrayList<>();
            selectedDates.clear();
            selectedDates.add(normalizeToStartOfDay(selectedDate.getTimeInMillis()));

            tvMonthYear = view.findViewById(R.id.tv_month_year);
            btnPrevMonth = view.findViewById(R.id.btn_prev_month);
            btnNextMonth = view.findViewById(R.id.btn_next_month);
            calendarGrid = view.findViewById(R.id.calendar_grid);
            recyclerDayTasks = view.findViewById(R.id.recycler_day_tasks);
            calendarEmptyState = view.findViewById(R.id.calendar_empty_state);
            tvSelectedDateHeader = view.findViewById(R.id.tv_selected_date_header);

            // Setup calendar grid
            calendarGrid.setLayoutManager(new GridLayoutManager(requireContext(), 7));
            calendarAdapter = new CalendarAdapter(
                    requireContext(),
                    new ArrayList<>(),
                    day -> {
                        if (day == null)
                            return;
                        selectedDate.setTimeInMillis(day.getTimeInMillis());
                        selectedDates.clear();
                        selectedDates.add(normalizeToStartOfDay(day.getTimeInMillis()));
                        calendarAdapter.setSelectedDays(selectedDates);
                        loadTasksForSelectedDay();
                    },
                    day -> {
                        if (day == null)
                            return;
                        long key = normalizeToStartOfDay(day.getTimeInMillis());
                        if (selectedDates.contains(key)) {
                            // Keep at least one selected date
                            if (selectedDates.size() > 1) {
                                selectedDates.remove(key);
                            }
                        } else {
                            selectedDates.add(key);
                        }
                        selectedDate.setTimeInMillis(day.getTimeInMillis());
                        calendarAdapter.setSelectedDays(selectedDates);
                        loadTasksForSelectedDay();
                    });
            calendarGrid.setAdapter(calendarAdapter);

            // Setup swipe gestures
            final android.view.GestureDetector gestureDetector = new android.view.GestureDetector(requireContext(),
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(android.view.MotionEvent e) {
                            return false;
                        }

                        @Override
                        public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2,
                                float velocityX, float velocityY) {
                            try {
                                float diffY = e2.getY() - e1.getY();
                                float diffX = e2.getX() - e1.getX();
                                if (Math.abs(diffY) > Math.abs(diffX)) { // Vertical
                                    if (Math.abs(diffY) > 50 && Math.abs(velocityY) > 50) {
                                        if (diffY > 0) {
                                            animateMonthChange(-1); // Swipe Down -> Prev
                                        } else {
                                            animateMonthChange(1); // Swipe Up -> Next
                                        }
                                        return true;
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return false;
                        }
                    });

            calendarGrid.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                    if (gestureDetector.onTouchEvent(e)) {
                        return true;
                    }
                    return false;
                }
            });

            // Setup day tasks list
            recyclerDayTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
            taskAdapter = new TaskListAdaptar(requireContext(), dayTasks, (task, position) -> {
                loadTasksForSelectedDay();
            });
            recyclerDayTasks.setAdapter(taskAdapter);

            // Setup navigation
            if (btnPrevMonth != null) {
                btnPrevMonth.setOnClickListener(v -> {
                    currentCalendar.add(Calendar.MONTH, -1);
                    updateCalendar();
                });
            }

            if (btnNextMonth != null) {
                btnNextMonth.setOnClickListener(v -> {
                    currentCalendar.add(Calendar.MONTH, 1);
                    updateCalendar();
                });
            }

            // Setup dropdown for month/year picker
            tvDropdownMonthYear = view.findViewById(R.id.tv_dropdown_month_year);
            monthYearDropdown = view.findViewById(R.id.month_year_dropdown);
            if (monthYearDropdown != null) {
                monthYearDropdown.setOnClickListener(v -> showMonthYearPicker());
            }

            // Also make the main month/year text clickable
            if (tvMonthYear != null) {
                tvMonthYear.setOnClickListener(v -> showMonthYearPicker());
            }

            updateCalendar();
            loadTasksForSelectedDay();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by MainActivity's single FAB.
     * - Tap a date = select single date
     * - Long-press additional dates = multi-select
     * This opens Add Task with the date(s) prefilled and marked as "calendar"
     * source.
     */
    public void openAddTaskForSelection() {
        ArrayList<Long> dates = new ArrayList<>();
        if (!selectedDates.isEmpty()) {
            dates.addAll(selectedDates);
        } else {
            dates.add(normalizeToStartOfDay(System.currentTimeMillis()));
        }

        // Pass "calendar" as the source so task is only shown in calendar view
        AddTaskBottomSheet sheet = AddTaskBottomSheet.newInstanceWithDates(dates, "calendar");
        sheet.setOnTaskAddedListener(() -> {
            updateCalendar();
            loadTasksForSelectedDay();
        });
        sheet.show(getParentFragmentManager(), "AddTaskBottomSheet");
    }

    private void openAddTaskForSelectedDate() {
        Calendar cal = (Calendar) selectedDate.clone();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        AddTaskBottomSheet sheet = AddTaskBottomSheet.newInstanceWithDate(cal.getTimeInMillis());
        sheet.setOnTaskAddedListener(() -> {
            updateCalendar();
            loadTasksForSelectedDay();
        });
        sheet.show(getParentFragmentManager(), "AddTaskBottomSheet");
    }

    private void animateMonthChange(int direction) {
        if (calendarGrid == null)
            return;

        float distance = calendarGrid.getHeight() * 0.25f;
        float outY = (direction > 0) ? -distance : distance;
        float inY = (direction > 0) ? distance : -distance;

        // Animate OLD month OUT
        calendarGrid.animate()
                .translationY(outY)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(
                        android.view.animation.AnimationUtils.loadInterpolator(
                                getContext(),
                                android.R.interpolator.fast_out_linear_in))
                .withEndAction(() -> {

                    // Update data AFTER exit animation
                    currentCalendar.add(Calendar.MONTH, direction);
                    updateCalendar();

                    // Prepare NEW month position
                    calendarGrid.setTranslationY(inY);
                    calendarGrid.setAlpha(0f);

                    // Animate NEW month IN
                    calendarGrid.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(280)
                            .setInterpolator(
                                    android.view.animation.AnimationUtils.loadInterpolator(
                                            getContext(),
                                            android.R.interpolator.linear_out_slow_in))
                            .start();
                })
                .start();
    }

    private void updateCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());

        // Update dropdown text with shorter format
        SimpleDateFormat dropdownFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        if (tvDropdownMonthYear != null) {
            tvDropdownMonthYear.setText(dropdownFormat.format(currentCalendar.getTime()));
        }

        List<Calendar> days = generateDaysForMonth();
        calendarAdapter.updateDays(days);
        calendarAdapter.setSelectedDays(selectedDates);

        // Load task indicators for the visible month
        loadTaskIndicators(days);
    }

    private void loadTaskIndicators(List<Calendar> days) {
        if (days == null || days.isEmpty())
            return;

        Set<Long> daysWithTasks = new HashSet<>();

        // Get the date range for all visible days
        Calendar firstDay = days.get(0);
        Calendar lastDay = days.get(days.size() - 1);

        long startMillis = normalizeToStartOfDay(firstDay.getTimeInMillis());

        Calendar endCal = (Calendar) lastDay.clone();
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        long endMillis = endCal.getTimeInMillis();

        // Get all calendar tasks in this range
        List<TaskList> tasks = dm.getTasksByDateRange(startMillis, endMillis + 86400000);

        for (TaskList task : tasks) {
            if (task.dueDate > 0) {
                daysWithTasks.add(normalizeToStartOfDay(task.dueDate));
            }
        }

        calendarAdapter.setDaysWithTasks(daysWithTasks);
    }

    private List<Calendar> generateDaysForMonth() {
        List<Calendar> days = new ArrayList<>();

        Calendar calendar = (Calendar) currentCalendar.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;

        // Add days from previous month
        Calendar prevMonth = (Calendar) calendar.clone();
        prevMonth.add(Calendar.MONTH, -1);
        int prevMonthDays = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = firstDayOfWeek - 1; i >= 0; i--) {
            Calendar day = (Calendar) prevMonth.clone();
            day.set(Calendar.DAY_OF_MONTH, prevMonthDays - i);
            days.add(day);
        }

        // Add days of current month
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 1; i <= daysInMonth; i++) {
            Calendar day = (Calendar) calendar.clone();
            day.set(Calendar.DAY_OF_MONTH, i);
            days.add(day);
        }

        // Add days from next month to complete grid
        int remaining = 42 - days.size();
        Calendar nextMonth = (Calendar) calendar.clone();
        nextMonth.add(Calendar.MONTH, 1);

        for (int i = 1; i <= remaining; i++) {
            Calendar day = (Calendar) nextMonth.clone();
            day.set(Calendar.DAY_OF_MONTH, i);
            days.add(day);
        }

        return days;
    }

    private void loadTasksForSelectedDay() {
        dayTasks.clear();

        // Get start and end of selected day
        Calendar startOfDay = (Calendar) selectedDate.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);

        Calendar endOfDay = (Calendar) selectedDate.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        endOfDay.set(Calendar.MILLISECOND, 999);

        // Update selected date header
        if (tvSelectedDateHeader != null) {
            SimpleDateFormat headerFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
            tvSelectedDateHeader.setText(headerFormat.format(selectedDate.getTime()));
            tvSelectedDateHeader.setVisibility(View.VISIBLE);
        }

        // Load ALL tasks for selected date (from both tasks tab and calendar tab)
        List<TaskList> tasks = dm.getTasksByDateRange(
                startOfDay.getTimeInMillis(),
                endOfDay.getTimeInMillis());

        dayTasks.addAll(tasks);
        taskAdapter.notifyDataSetChanged();

        if (dayTasks.isEmpty()) {
            calendarEmptyState.setVisibility(View.VISIBLE);
            recyclerDayTasks.setVisibility(View.GONE);
        } else {
            calendarEmptyState.setVisibility(View.GONE);
            recyclerDayTasks.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCalendar();
        loadTasksForSelectedDay();
    }

    private void showMonthYearPicker() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_month_year_picker);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        final int[] selectedYear = { currentCalendar.get(Calendar.YEAR) };
        final int[] selectedMonth = { currentCalendar.get(Calendar.MONTH) };

        TextView tvYear = dialog.findViewById(R.id.tv_selected_year);
        ImageButton btnYearPrev = dialog.findViewById(R.id.btn_year_prev);
        ImageButton btnYearNext = dialog.findViewById(R.id.btn_year_next);
        RecyclerView monthGrid = dialog.findViewById(R.id.month_grid);

        tvYear.setText(String.valueOf(selectedYear[0]));

        // Year navigation
        btnYearPrev.setOnClickListener(v -> {
            selectedYear[0]--;
            tvYear.setText(String.valueOf(selectedYear[0]));
        });

        btnYearNext.setOnClickListener(v -> {
            selectedYear[0]++;
            tvYear.setText(String.valueOf(selectedYear[0]));
        });

        // Month grid
        monthGrid.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        MonthAdapter monthAdapter = new MonthAdapter(requireContext(), MONTH_NAMES, selectedMonth[0], month -> {
            selectedMonth[0] = month;
        });
        monthGrid.setAdapter(monthAdapter);

        // Cancel button
        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        // Today button
        dialog.findViewById(R.id.btn_today).setOnClickListener(v -> {
            currentCalendar = Calendar.getInstance();
            selectedDate = Calendar.getInstance();
            selectedDates.clear();
            selectedDates.add(normalizeToStartOfDay(selectedDate.getTimeInMillis()));
            updateCalendar();
            loadTasksForSelectedDay();
            dialog.dismiss();
        });

        // Done button
        dialog.findViewById(R.id.btn_done).setOnClickListener(v -> {
            currentCalendar.set(Calendar.YEAR, selectedYear[0]);
            currentCalendar.set(Calendar.MONTH, selectedMonth[0]);
            currentCalendar.set(Calendar.DAY_OF_MONTH, 1);
            updateCalendar();
            dialog.dismiss();
        });

        dialog.show();
    }

    // Simple adapter for month grid
    private static class MonthAdapter extends RecyclerView.Adapter<MonthAdapter.MonthViewHolder> {
        private final android.content.Context context;
        private final String[] months;
        private int selectedMonth;
        private final OnMonthClickListener listener;

        interface OnMonthClickListener {
            void onMonthClick(int month);
        }

        MonthAdapter(android.content.Context context, String[] months, int selectedMonth,
                OnMonthClickListener listener) {
            this.context = context;
            this.months = months;
            this.selectedMonth = selectedMonth;
            this.listener = listener;
        }

        @NonNull
        @Override
        public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_month_picker, parent, false);
            return new MonthViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
            holder.tvMonth.setText(months[position]);

            boolean isSelected = position == selectedMonth;
            holder.tvMonth.setSelected(isSelected);
            holder.tvMonth.setTextColor(isSelected ? ContextCompat.getColor(context, R.color.white)
                    : ContextCompat.getColor(context, R.color.text_primary));

            holder.itemView.setOnClickListener(v -> {
                int oldSelection = selectedMonth;
                selectedMonth = position;
                notifyItemChanged(oldSelection);
                notifyItemChanged(selectedMonth);
                if (listener != null)
                    listener.onMonthClick(position);
            });
        }

        @Override
        public int getItemCount() {
            return months.length;
        }

        static class MonthViewHolder extends RecyclerView.ViewHolder {
            TextView tvMonth;

            MonthViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMonth = itemView.findViewById(R.id.tv_month_name);
            }
        }
    }
}
