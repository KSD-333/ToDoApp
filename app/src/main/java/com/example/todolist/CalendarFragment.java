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

            // Setup "Mark as Important" FAB
            com.google.android.material.floatingactionbutton.FloatingActionButton fabMarkImportant = view
                    .findViewById(R.id.fab_mark_important);
            if (fabMarkImportant != null) {
                fabMarkImportant.setOnClickListener(v -> toggleImportantDate());
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

    /**
     * Toggle the selected date as "important" and save to SharedPreferences
     */
    private void toggleImportantDate() {
        if (selectedDate == null || getContext() == null)
            return;

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateKey = dateFormat.format(selectedDate.getTime());

        android.content.SharedPreferences prefs = getContext().getSharedPreferences("important_dates",
                android.content.Context.MODE_PRIVATE);
        Set<String> importantDates = new HashSet<>(prefs.getStringSet("dates", new HashSet<>()));

        boolean isNowImportant;
        if (importantDates.contains(dateKey)) {
            importantDates.remove(dateKey);
            isNowImportant = false;
            android.widget.Toast
                    .makeText(getContext(), "Removed from important dates", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            importantDates.add(dateKey);
            isNowImportant = true;
            android.widget.Toast.makeText(getContext(), "Marked as important date", android.widget.Toast.LENGTH_SHORT)
                    .show();
        }

        prefs.edit().putStringSet("dates", importantDates).apply();
        // Optionally update calendar UI to show important dates visually
        updateCalendar();
        updateFabState();
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

        // Update important days in adapter
        calendarAdapter.setImportantDays(getImportantDays());

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

        // Add projected recurring tasks
        daysWithTasks.addAll(getRecurringTaskDays(startMillis, endMillis));

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

        updateFabState();

        // Load ALL tasks for selected date (from both tasks tab and calendar tab)
        List<TaskList> tasks = dm.getTasksByDateRange(
                startOfDay.getTimeInMillis(),
                endOfDay.getTimeInMillis());

        // Add projected recurring tasks for this specific day
        List<TaskList> projected = getRecurringForecasts(startOfDay.getTimeInMillis());
        // Filter out if we already have a real task for this recurring series on this
        // day
        // (Simple check: if we have a real task with same title? No, title isn't
        // unique.)
        // Ideally we check createdFrom or ID, but projected tasks have ID -1.
        // For now, just show them. Duplicate detection is complex without lineage
        // tracking.
        tasks.addAll(projected);

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

    /**
     * Calculate virtual task instances for a single specific day.
     */
    private List<TaskList> getRecurringForecasts(long targetDate) {
        List<TaskList> results = new ArrayList<>();
        List<TaskList> allTasks = dm.getAllTasks();

        // Normalize target
        long normalizedTarget = normalizeToStartOfDay(targetDate);
        long targetEnd = normalizedTarget + 86400000L - 1;

        // Collect parent IDs that already have a real task on this day
        Set<Integer> existingParentIds = new HashSet<>();
        Set<Integer> existingTaskIds = new HashSet<>();
        for (TaskList t : allTasks) {
            if (t.dueDate >= normalizedTarget && t.dueDate <= targetEnd) {
                existingTaskIds.add(t.id);
                if (t.recurringParentId > 0) {
                    existingParentIds.add(t.recurringParentId);
                }
            }
        }

        for (TaskList t : allTasks) {
            // Only look at pending recurring tasks
            if (t.check == 0 && t.repeatType != null && !t.repeatType.equals("none")) {
                // If due date is 0 (unscheduled), skip
                if (t.dueDate == 0)
                    continue;

                // Skip if this task's due date is already on the target day (handled by DB
                // query)
                if (t.dueDate >= normalizedTarget && t.dueDate <= targetEnd)
                    continue;

                // Skip if a child instance already exists on target day for this parent
                int parentId = t.recurringParentId > 0 ? t.recurringParentId : t.id;
                if (existingParentIds.contains(parentId) || existingTaskIds.contains(parentId))
                    continue;

                // Check if this task recurs on 'targetDate'
                if (isRecurringMatch(t, normalizedTarget)) {
                    TaskList virtual = new TaskList();
                    // Manually copy relevant fields
                    virtual.id = -1; // Virtual ID
                    virtual.setTask(t.getTask());
                    virtual.setCategory(t.getCategory());
                    virtual.setDueDate(normalizedTarget); // Set to target date
                    virtual.setTaskTime(t.getTaskTime());
                    virtual.setTime(t.getTime());
                    virtual.setRepeatType(t.getRepeatType());
                    virtual.setRepeatInterval(t.getRepeatInterval());
                    virtual.setRepeatDays(t.getRepeatDays());
                    virtual.setUseAlarm(t.getUseAlarm());
                    virtual.setStarred(t.getIsStarred());

                    results.add(virtual);
                }
            }
        }
        return results;
    }

    /**
     * Get all dates that have recurring tasks in the given range.
     */
    private Set<Long> getRecurringTaskDays(long start, long end) {
        Set<Long> days = new HashSet<>();
        List<TaskList> allTasks = dm.getAllTasks();

        Calendar cursor = Calendar.getInstance();

        for (TaskList t : allTasks) {
            if (t.check == 0 && t.repeatType != null && !t.repeatType.equals("none") && t.dueDate > 0) {
                // Determine next occurrence after 'start'
                // Optimization: Don't loop from task creation date if it's years ago.
                // Just check days in the range?

                // Brute force check every day in range? (Max 42 days).
                // 42 * N_Tasks. If 100 recurring tasks -> 4200 checks. Fast enough.

                for (long d = start; d <= end; d += 86400000L) {
                    if (d <= t.dueDate)
                        continue; // Only project future
                    if (isRecurringMatch(t, d)) {
                        days.add(d);
                    }
                }
            }
        }
        return days;
    }

    private boolean isRecurringMatch(TaskList task, long targetDate) {
        Calendar targetCal = Calendar.getInstance();
        targetCal.setTimeInMillis(targetDate);

        Calendar dueCal = Calendar.getInstance();
        dueCal.setTimeInMillis(task.dueDate);

        // Fast fail logic - target must be strictly after the original due date
        if (targetDate <= task.dueDate)
            return false;

        // Handle custom_days first (before the switch)
        if ("custom_days".equals(task.repeatType)) {
            String repeatDays = task.getRepeatDays();
            if (repeatDays == null || repeatDays.isEmpty())
                return false;
            int targetDow = targetCal.get(Calendar.DAY_OF_WEEK);
            String[] dayParts = repeatDays.split(",");
            for (String dp : dayParts) {
                try {
                    int dayNum = Integer.parseInt(dp.trim());
                    if (dayNum == targetDow)
                        return true;
                } catch (NumberFormatException ignored) {
                }
            }
            return false;
        }

        switch (task.repeatType) {
            case "days":
                long diffDays = (targetDate - task.dueDate) / (24 * 60 * 60 * 1000);
                // Need to account for DST??
                // Safest: Use Calendar add loop if interval > 1.
                // If interval is 1, it matches every day.
                if (task.repeatInterval == 1)
                    return true;

                // For interval > 1, allow slight margin or use strict calendar loop?
                // Let's use strict calendar loop for correctness but optimize.
                // Actually (diffDays % interval == 0) is usually fine for "days".
                return diffDays % task.repeatInterval == 0;

            case "weeks":
                if (targetCal.get(Calendar.DAY_OF_WEEK) != dueCal.get(Calendar.DAY_OF_WEEK))
                    return false;
                long diffWeeks = (targetDate - task.dueDate) / (7 * 24 * 60 * 60 * 1000);
                return diffWeeks % task.repeatInterval == 0;

            case "months":
                if (targetCal.get(Calendar.DAY_OF_MONTH) != dueCal.get(Calendar.DAY_OF_MONTH))
                    return false;
                // Calculate month diff
                int diffMonths = (targetCal.get(Calendar.YEAR) - dueCal.get(Calendar.YEAR)) * 12 +
                        (targetCal.get(Calendar.MONTH) - dueCal.get(Calendar.MONTH));
                return diffMonths > 0 && diffMonths % task.repeatInterval == 0;

            case "years":
                return targetCal.get(Calendar.DAY_OF_YEAR) == dueCal.get(Calendar.DAY_OF_YEAR) &&
                        (targetCal.get(Calendar.YEAR) - dueCal.get(Calendar.YEAR)) % task.repeatInterval == 0;
        }
        return false;
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

    private Set<Long> getImportantDays() {
        Set<Long> millis = new HashSet<>();
        if (getContext() == null)
            return millis;
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("important_dates",
                android.content.Context.MODE_PRIVATE);
        Set<String> dateStrings = prefs.getStringSet("dates", new HashSet<>());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (String s : dateStrings) {
            try {
                java.util.Date d = sdf.parse(s);
                if (d != null)
                    millis.add(normalizeToStartOfDay(d.getTime()));
            } catch (Exception e) {
            }
        }
        return millis;
    }

    private void updateFabState() {
        if (selectedDate == null || getContext() == null)
            return;
        View view = getView();
        if (view == null)
            return;

        com.google.android.material.floatingactionbutton.FloatingActionButton fabMarkImportant = view
                .findViewById(R.id.fab_mark_important);
        if (fabMarkImportant == null)
            return;

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateKey = dateFormat.format(selectedDate.getTime());
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("important_dates",
                android.content.Context.MODE_PRIVATE);
        Set<String> importantDates = prefs.getStringSet("dates", new HashSet<>());

        if (importantDates.contains(dateKey)) {
            // Already important -> Show "Unmark" (Blue bg, Close icon)
            fabMarkImportant.setImageResource(R.drawable.ic_close);
            fabMarkImportant.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary_blue)));
        } else {
            // Not important -> Show "Mark" (Red bg, Add icon)
            fabMarkImportant.setImageResource(android.R.drawable.ic_input_add);
            fabMarkImportant.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#F44336")));
        }
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
