package com.example.todolist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MineFragment extends Fragment {

    private TextView tvCompletedCount, tvPendingCount, tvOverdueCount;
    private TextView tvCompletionRate, tvRateMessage, tvInsightTotal;
    private TextView tvBestDay, tvTopCategory; // New insights
    private TextView tvDateRange, tvNoData;
    private WeeklyBarChartView weeklyChart;
    private ImageButton btnPrevWeek, btnNextWeek;
    private RecyclerView recyclerUpcoming, recyclerCategories;
    private DataManager dm;

    // Executor for background tasks
    private ExecutorService io;
    private int weekOffset = 0;

    private final ArrayList<TaskList> upcomingItems = new ArrayList<>();
    private final ArrayList<CategoryCount> categoryItems = new ArrayList<>();
    private MineUpcomingAdapter upcomingAdapter;
    private MineCategorySummaryAdapter categoryAdapter;

    public MineFragment() {
        // Required empty public constructor
    }

    public static MineFragment newInstance() {
        return new MineFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize or restart executor
        if (io == null || io.isShutdown()) {
            io = Executors.newSingleThreadExecutor();
        }

        try {
            dm = DataManager.getInstance(requireContext());

            tvCompletedCount = view.findViewById(R.id.tv_completed_count);
            tvPendingCount = view.findViewById(R.id.tv_pending_count);
            tvOverdueCount = view.findViewById(R.id.tv_overdue_count);
            tvCompletionRate = view.findViewById(R.id.tv_completion_rate);
            tvRateMessage = view.findViewById(R.id.tv_rate_message);

            tvDateRange = view.findViewById(R.id.tv_date_range);
            tvNoData = view.findViewById(R.id.tv_no_data);

            tvBestDay = view.findViewById(R.id.tv_best_day);
            tvTopCategory = view.findViewById(R.id.tv_top_category);
            tvInsightTotal = view.findViewById(R.id.tv_insight_total);

            weeklyChart = view.findViewById(R.id.chart_weekly);
            // Default to Bar chart
            if (weeklyChart != null)
                weeklyChart.setChartType(0);

            // Chart Type Toggle Logic
            com.google.android.material.button.MaterialButtonToggleGroup toggleGroup = view
                    .findViewById(R.id.toggle_chart_type);
            if (toggleGroup != null) {
                toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (isChecked && weeklyChart != null) {
                        if (checkedId == R.id.btn_type_bar)
                            weeklyChart.setChartType(0);
                        else if (checkedId == R.id.btn_type_line)
                            weeklyChart.setChartType(1);
                        else if (checkedId == R.id.btn_type_pie)
                            weeklyChart.setChartType(2);
                    }
                });
                // Ensure Bar is checked initially
                toggleGroup.check(R.id.btn_type_bar);
            }

            btnPrevWeek = view.findViewById(R.id.btn_prev_week);
            btnNextWeek = view.findViewById(R.id.btn_next_week);

            recyclerUpcoming = view.findViewById(R.id.recycler_upcoming);
            recyclerCategories = view.findViewById(R.id.recycler_categories);

            if (recyclerUpcoming != null) {
                recyclerUpcoming.setNestedScrollingEnabled(false);
                recyclerUpcoming.setLayoutManager(new LinearLayoutManager(requireContext()));
            }
            if (recyclerCategories != null) {
                recyclerCategories.setNestedScrollingEnabled(false);
                recyclerCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
            }

            // Setup Click Listener for "Total Stored"
            View cardTotalStored = view.findViewById(R.id.card_total_stored);
            if (cardTotalStored != null) {
                cardTotalStored.setOnClickListener(v -> {
                    // Toggle or Show details
                });
            }

            upcomingAdapter = new MineUpcomingAdapter(upcomingItems, task -> {
                int newStatus = task.check == 0 ? 1 : 0;
                task.check = newStatus;
                task.completedAt = newStatus == 1 ? System.currentTimeMillis() : 0;
                dm.updateStatusAndCompletedAt(task.id, newStatus, task.completedAt);
                refreshAll();
            });
            categoryAdapter = new MineCategorySummaryAdapter(categoryItems);

            if (recyclerUpcoming != null)
                recyclerUpcoming.setAdapter(upcomingAdapter);
            if (recyclerCategories != null)
                recyclerCategories.setAdapter(categoryAdapter);

            View cardCompleted = view.findViewById(R.id.card_completed);
            View cardPending = view.findViewById(R.id.card_pending);
            View cardOverdue = view.findViewById(R.id.card_overdue);

            if (cardCompleted != null) {
                cardCompleted.setOnClickListener(v -> openTasksFiltered(1));
            }
            if (cardPending != null) {
                cardPending.setOnClickListener(v -> openTasksFiltered(0));
            }
            if (cardOverdue != null) {
                cardOverdue.setOnClickListener(v -> openTasksFiltered(2));
            }

            if (btnPrevWeek != null) {
                btnPrevWeek.setOnClickListener(v -> {
                    weekOffset -= 1;
                    refreshAll();
                });
            }
            if (btnNextWeek != null) {
                btnNextWeek.setOnClickListener(v -> {
                    weekOffset += 1;
                    refreshAll();
                });
            }

            refreshAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshAll() {
        updateDateRange();
        try {
            if (io != null && !io.isShutdown()) {
                io.execute(() -> {
                    // Fetch ALL tasks once
                    List<TaskList> allTasks = dm.getAllTasks();

                    int completed = 0;
                    int pending = 0;
                    int overdue = 0;
                    long now = System.currentTimeMillis();
                    long todayStart = getTodayStart();

                    // For best day calc
                    Map<Integer, Integer> dayCompletedCounts = new HashMap<>();
                    // For top category calc (completed tasks)
                    Map<String, Integer> catCompletedCounts = new HashMap<>();

                    // For categories breakdown (All tasks)
                    Map<String, Integer> catAllCounts = new HashMap<>();

                    for (TaskList t : allTasks) {
                        if (t.check == 1) {
                            completed++;

                            // Best Day
                            if (t.completedAt > 0) {
                                Calendar c = Calendar.getInstance();
                                c.setTimeInMillis(t.completedAt);
                                int day = c.get(Calendar.DAY_OF_WEEK);
                                dayCompletedCounts.put(day, dayCompletedCounts.getOrDefault(day, 0) + 1);
                            }

                            // Top Category (based on completed tasks)
                            String cat = t.category;
                            if (cat == null || cat.trim().isEmpty()) {
                                cat = "Unknown";
                            }
                            catCompletedCounts.put(cat, catCompletedCounts.getOrDefault(cat, 0) + 1);

                        } else {
                            pending++;
                            if (t.dueDate > 0 && t.dueDate < todayStart) {
                                overdue++;
                            }
                        }

                        // Task Distribution (All tasks)
                        String cat = t.category;
                        if (cat == null || cat.trim().isEmpty()) {
                            cat = "Unknown";
                        }
                        catAllCounts.put(cat, catAllCounts.getOrDefault(cat, 0) + 1);
                    }

                    int total = completed + pending;
                    int rate = total > 0 ? (int) ((completed / (float) total) * 100) : 0;

                    // Rate Message
                    String rateMsg = "Keep going!";
                    if (rate >= 80)
                        rateMsg = "Excellent work!";
                    else if (rate >= 50)
                        rateMsg = "Good progress!";
                    else if (rate > 0)
                        rateMsg = "Keep it up!";
                    else
                        rateMsg = "Let's start!";

                    // Best Day
                    String bestDayStr = "None";
                    int maxDayVal = -1;
                    int bestDayIdx = -1;
                    for (Map.Entry<Integer, Integer> entry : dayCompletedCounts.entrySet()) {
                        if (entry.getValue() > maxDayVal) {
                            maxDayVal = entry.getValue();
                            bestDayIdx = entry.getKey();
                        }
                    }
                    if (bestDayIdx != -1) {
                        String[] days = new String[] { "", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday",
                                "Friday", "Saturday" };
                        if (bestDayIdx < days.length)
                            bestDayStr = days[bestDayIdx];
                    }

                    // Top Category (from Completed)
                    String topCatStr = "None";
                    int maxCatVal = -1;
                    for (Map.Entry<String, Integer> entry : catCompletedCounts.entrySet()) {
                        if (entry.getValue() > maxCatVal) {
                            maxCatVal = entry.getValue();
                            topCatStr = entry.getKey();
                        }
                    }
                    // Fallback: If no completed, show top category by volume
                    if (topCatStr.equals("None") && !catAllCounts.isEmpty()) {
                        for (Map.Entry<String, Integer> entry : catAllCounts.entrySet()) {
                            if (entry.getValue() > maxCatVal) {
                                maxCatVal = entry.getValue();
                                topCatStr = entry.getKey();
                            }
                        }
                    }

                    // Recent/Upcoming List
                    List<TaskList> recentList = new ArrayList<>();
                    List<TaskList> pendingTasks = new ArrayList<>();
                    for (TaskList t : allTasks) {
                        if (t.check == 0)
                            pendingTasks.add(t);
                    }

                    Collections.sort(pendingTasks, (t1, t2) -> {
                        long d1 = t1.dueDate;
                        long d2 = t2.dueDate;
                        if (d1 == 0 && d2 == 0)
                            return 0;
                        if (d1 == 0)
                            return 1;
                        if (d2 == 0)
                            return -1;
                        return Long.compare(d1, d2);
                    });

                    // Take top 10
                    int limit = Math.min(pendingTasks.size(), 10);
                    for (int i = 0; i < limit; i++)
                        recentList.add(pendingTasks.get(i));

                    // Weekly Chart Data
                    long weekStart = getWeekStart(weekOffset);
                    long weekEnd = getWeekEnd(weekStart);
                    List<DayCount> dayCounts = dm.getCompletedCountsByDayOfWeek(weekStart, weekEnd);
                    int[] weekly = toWeeklyArray(dayCounts);

                    // Category List (Distribution)
                    List<CategoryCount> catList = new ArrayList<>();
                    // Sort by count descending
                    List<Map.Entry<String, Integer>> sortedCats = new ArrayList<>(catAllCounts.entrySet());
                    Collections.sort(sortedCats, (e1, e2) -> e2.getValue().compareTo(e1.getValue()));

                    for (Map.Entry<String, Integer> entry : sortedCats) {
                        catList.add(new CategoryCount(entry.getKey(), entry.getValue()));
                    }

                    // UI Updates
                    final android.app.Activity activity = getActivity();
                    if (activity == null || !isAdded())
                        return;

                    final int finalRate = rate;
                    final int finalCompleted = completed;
                    final int finalPending = pending;
                    final int finalOverdue = overdue;
                    final String finalRateMsg = rateMsg;
                    final String finalBestDay = bestDayStr;
                    final String finalTopCat = topCatStr;
                    final int finalTotal = total;

                    activity.runOnUiThread(() -> {
                        if (!isAdded())
                            return;

                        if (tvCompletedCount != null)
                            tvCompletedCount.setText(String.valueOf(finalCompleted));
                        if (tvPendingCount != null)
                            tvPendingCount.setText(String.valueOf(finalPending));
                        if (tvOverdueCount != null)
                            tvOverdueCount.setText(String.valueOf(finalOverdue));

                        if (tvCompletionRate != null)
                            tvCompletionRate.setText(finalRate + "%");
                        if (tvRateMessage != null)
                            tvRateMessage.setText(finalRateMsg);

                        if (tvBestDay != null)
                            tvBestDay.setText(finalBestDay);
                        if (tvTopCategory != null)
                            tvTopCategory.setText(finalTopCat);
                        if (tvInsightTotal != null)
                            tvInsightTotal.setText(finalTotal + " Tasks");

                        upcomingItems.clear();
                        upcomingItems.addAll(recentList);
                        upcomingAdapter.notifyDataSetChanged();

                        categoryItems.clear();
                        categoryItems.addAll(catList);
                        categoryAdapter.notifyDataSetChanged();

                        boolean hasWeeklyData = false;
                        for (int v : weekly) {
                            if (v > 0) {
                                hasWeeklyData = true;
                                break;
                            }
                        }

                        if (weeklyChart != null)
                            weeklyChart.setValues(weekly);
                        if (tvNoData != null)
                            tvNoData.setVisibility(hasWeeklyData ? View.GONE : View.VISIBLE);
                    });
                });
            }
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void updateDateRange() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset);
        calendar.setFirstDayOfWeek(Calendar.SUNDAY);

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startMs = calendar.getTimeInMillis();

        calendar.add(Calendar.DAY_OF_WEEK, 6);
        long endMs = calendar.getTimeInMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
        tvDateRange.setText(sdf.format(startMs) + " - " + sdf.format(endMs));
    }

    private int[] toWeeklyArray(List<DayCount> counts) {
        int[] arr = new int[] { 0, 0, 0, 0, 0, 0, 0 }; // 0=Sun .. 6=Sat
        if (counts == null)
            return arr;
        for (DayCount dc : counts) {
            if (dc == null)
                continue;
            try {
                int idx = dc.dayOfWeek - 1; // Convert to 0-based index
                if (idx >= 0 && idx < 7)
                    arr[idx] = dc.count;
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private long getWeekStart(int offsetWeeks) {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.SUNDAY);
        calendar.add(Calendar.WEEK_OF_YEAR, offsetWeeks);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getWeekEnd(long weekStart) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(weekStart);
        calendar.add(Calendar.DAY_OF_YEAR, 6);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private long getTodayStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDay(long dayStartMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dayStartMs);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    // Kept from original
    private long addDays(long baseMs, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(baseMs);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTimeInMillis();
    }

    private void openTasksFiltered(int filterType) {
        if (!(requireActivity() instanceof MainActivity))
            return;
        MainActivity activity = (MainActivity) requireActivity();
        activity.openTasksFromMine(filterType);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (io != null) {
            io.shutdownNow();
        }
    }
}
