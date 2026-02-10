package com.example.todolist;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.recyclerview.widget.GridLayoutManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddTaskBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PRESELECTED_DUE_DATE = "preselected_due_date";
    private static final String ARG_PRESELECTED_DUE_DATES = "preselected_due_dates";
    private static final String ARG_PRESELECTED_CATEGORY = "preselected_category";
    private static final String ARG_CREATED_FROM = "created_from";

    private EditText etTaskTitle, etTaskDetails;
    private Chip chipCategory;
    private View btnDate;
    private TextView tvDateBadge;
    private ImageView icDate;
    private LinearLayout subtasksContainer;
    private ScrollView subtasksScroll;
    private DataManager dm;

    private android.widget.ImageButton btnSubtask;
    private android.widget.ImageButton btnTemplate;
    private android.widget.ImageButton btnSubmit;

    private String selectedCategory = "No Category";
    private long selectedDueDate = 0;
    private ArrayList<Long> preselectedDueDates = null;
    private String selectedTime = "";
    private String selectedRepeatType = "none";
    private int selectedRepeatInterval = 1;
    private boolean[] selectedWeekdays = new boolean[7]; // Sun, Mon, Tue, Wed, Thu, Fri, Sat
    private ArrayList<String> selectedReminders = new ArrayList<>();
    private String selectedReminderMinutes = "5"; // Default 5 minutes
    private String selectedReminderType = "notification"; // notification or alarm
    private boolean useAlarm = false;
    private boolean selectedScreenLock = false;
    private OnTaskAddedListener listener;
    private String createdFrom = "tasks"; // Source: "tasks" or "calendar"

    // For editing existing task
    private TaskList editingTask = null;
    private boolean isEditMode = false;

    public interface OnTaskAddedListener {
        void onTaskAdded();
    }

    public static AddTaskBottomSheet newInstance() {
        return new AddTaskBottomSheet();
    }

    public static AddTaskBottomSheet newInstanceWithDate(long dueDateMillis) {
        AddTaskBottomSheet sheet = new AddTaskBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_PRESELECTED_DUE_DATE, dueDateMillis);
        sheet.setArguments(args);
        return sheet;
    }

    public static AddTaskBottomSheet newInstanceWithDates(ArrayList<Long> dueDatesMillis) {
        return newInstanceWithDates(dueDatesMillis, "tasks");
    }

    public static AddTaskBottomSheet newInstanceWithDates(ArrayList<Long> dueDatesMillis, String source) {
        AddTaskBottomSheet sheet = new AddTaskBottomSheet();
        Bundle args = new Bundle();
        if (dueDatesMillis != null) {
            args.putLongArray(ARG_PRESELECTED_DUE_DATES, toLongArray(dueDatesMillis));
        }
        args.putString(ARG_CREATED_FROM, source);
        sheet.setArguments(args);
        return sheet;
    }

    public static AddTaskBottomSheet newInstanceWithCategory(String category) {
        AddTaskBottomSheet sheet = new AddTaskBottomSheet();
        Bundle args = new Bundle();
        if (category != null) {
            args.putString(ARG_PRESELECTED_CATEGORY, category);
        }
        sheet.setArguments(args);
        return sheet;
    }

    private static long[] toLongArray(ArrayList<Long> list) {
        if (list == null)
            return new long[0];
        long[] arr = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Long v = list.get(i);
            arr[i] = v == null ? 0 : v;
        }
        return arr;
    }

    public static AddTaskBottomSheet newInstanceForEdit(TaskList task) {
        AddTaskBottomSheet sheet = new AddTaskBottomSheet();
        sheet.editingTask = task;
        sheet.isEditMode = true;
        return sheet;
    }

    private static long normalizeToStartOfDay(long millis) {
        if (millis <= 0)
            return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public void setOnTaskAddedListener(OnTaskAddedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_add_task_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dm = DataManager.getInstance(requireContext());

        etTaskTitle = view.findViewById(R.id.et_task_title);
        etTaskDetails = view.findViewById(R.id.et_task_details);
        chipCategory = view.findViewById(R.id.chip_category);
        subtasksScroll = view.findViewById(R.id.subtasks_scroll);
        subtasksContainer = view.findViewById(R.id.subtasks_container);

        // New bottom bar elements
        btnDate = view.findViewById(R.id.btn_date);
        tvDateBadge = view.findViewById(R.id.tv_date_badge);
        icDate = view.findViewById(R.id.ic_date);
        btnSubtask = view.findViewById(R.id.btn_subtask);
        btnTemplate = view.findViewById(R.id.btn_template);
        btnSubmit = view.findViewById(R.id.btn_submit);

        chipCategory.setOnClickListener(v -> showCategoryPicker());

        if (btnDate != null) {
            btnDate.setOnClickListener(v -> showDatePicker());
        }

        if (btnSubtask != null) {
            btnSubtask.setOnClickListener(v -> addSubtaskInput());
        }

        if (btnTemplate != null) {
            btnTemplate.setOnClickListener(v -> {
                try {
                    android.content.Intent intent = new android.content.Intent(requireContext(),
                            TaskTemplateActivity.class);
                    startActivity(intent);
                    dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Calendar can pre-fill a date (single or multiple).
        if (!isEditMode) {
            Bundle args = getArguments();
            if (args != null) {
                if (args.containsKey(ARG_PRESELECTED_DUE_DATES)) {
                    long[] raw = args.getLongArray(ARG_PRESELECTED_DUE_DATES);
                    if (raw != null && raw.length > 0) {
                        preselectedDueDates = new ArrayList<>();
                        for (long v : raw) {
                            if (v > 0)
                                preselectedDueDates.add(normalizeToStartOfDay(v));
                        }
                        if (!preselectedDueDates.isEmpty()) {
                            selectedDueDate = preselectedDueDates.get(0);
                            updateDateChipDisplay();
                        }
                    }
                } else if (args.containsKey(ARG_PRESELECTED_DUE_DATE)) {
                    long preselected = args.getLong(ARG_PRESELECTED_DUE_DATE, 0);
                    if (preselected > 0) {
                        selectedDueDate = normalizeToStartOfDay(preselected);
                        updateDateChipDisplay();
                    }
                }
            }
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> saveTask());
        }

        // If editing, populate the fields
        if (isEditMode && editingTask != null) {
            populateForEdit();
        }

        // Prefill category if coming from Tasks filter
        if (!isEditMode) {
            try {
                Bundle args = getArguments();
                if (args != null && args.containsKey(ARG_PRESELECTED_CATEGORY)) {
                    String cat = args.getString(ARG_PRESELECTED_CATEGORY, "");
                    if (cat != null && !cat.trim().isEmpty() && !cat.equalsIgnoreCase("All")) {
                        selectedCategory = cat.trim();
                        chipCategory.setText(selectedCategory);
                    }
                }
                // Get the source (tasks or calendar)
                if (args != null && args.containsKey(ARG_CREATED_FROM)) {
                    createdFrom = args.getString(ARG_CREATED_FROM, "tasks");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Focus on title input
        etTaskTitle.requestFocus();
    }

    private void populateForEdit() {
        etTaskTitle.setText(editingTask.task);
        if (etTaskDetails != null) {
            etTaskDetails.setText(editingTask.time);
        }

        selectedCategory = editingTask.category != null ? editingTask.category : "No Category";
        chipCategory.setText(selectedCategory.isEmpty() ? "Category" : selectedCategory);

        selectedDueDate = editingTask.dueDate;
        selectedTime = editingTask.taskTime != null ? editingTask.taskTime : "";
        selectedRepeatType = editingTask.repeatType != null ? editingTask.repeatType : "none";
        selectedRepeatInterval = editingTask.repeatInterval;

        // Restore weekday selection from repeatDays
        if ("custom_days".equals(selectedRepeatType) && editingTask.repeatDays != null
                && !editingTask.repeatDays.isEmpty()) {
            java.util.Arrays.fill(selectedWeekdays, false);
            String[] dayParts = editingTask.repeatDays.split(",");
            for (String dp : dayParts) {
                try {
                    int dayNum = Integer.parseInt(dp.trim());
                    if (dayNum >= 1 && dayNum <= 7) {
                        selectedWeekdays[dayNum - 1] = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Parse reminders
        if (editingTask.reminderMinutes != null && !editingTask.reminderMinutes.isEmpty()) {
            String[] parts = editingTask.reminderMinutes.split(",");
            selectedReminders.clear();
            for (String part : parts) {
                selectedReminders.add(part.trim());
            }
        }

        useAlarm = editingTask.useAlarm == 1;
        selectedScreenLock = editingTask.screenLock == 1;

        // Update date chip display
        updateDateChipDisplay();
    }

    private void updateDateChipDisplay() {
        try {
            // Update date button with badge showing selected date
            if (tvDateBadge == null || icDate == null)
                return;

            if (selectedDueDate <= 0) {
                tvDateBadge.setVisibility(View.GONE);
                icDate.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                return;
            }

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selectedDueDate);
            int day = cal.get(Calendar.DAY_OF_MONTH);

            // Format: "Jan 30" or just day number
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
            String dateText = sdf.format(cal.getTime());

            if (preselectedDueDates != null && preselectedDueDates.size() > 1) {
                dateText = dateText + "+";
            }

            tvDateBadge.setText(dateText);
            tvDateBadge.setVisibility(View.VISIBLE);
            icDate.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_blue));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyQuickChipStyle(Chip chip, boolean selected) {
        if (chip == null)
            return;
        if (selected) {
            chip.setChipBackgroundColorResource(R.color.primary_blue);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        } else {
            chip.setChipBackgroundColorResource(R.color.chip_inactive_bg);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                // Compact by default (like screenshot). Users can drag it up if needed.
                behavior.setSkipCollapsed(false);
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        }
    }

    private void showCategoryPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_category_picker, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView rvCategories = dialogView.findViewById(R.id.rv_categories);
        LinearLayout btnCreateCategory = dialogView.findViewById(R.id.btn_create_category);

        List<Category> categories = dm.getAllCategories();

        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        CategoryAdapter categoryAdapter = new CategoryAdapter(categories, selectedCategory, category -> {
            selectedCategory = category.getName();
            chipCategory.setText(selectedCategory);
            dialog.dismiss();
        });
        rvCategories.setAdapter(categoryAdapter);

        btnCreateCategory.setOnClickListener(v -> {
            dialog.dismiss();
            showCreateCategoryDialog();
        });

        dialog.show();
    }

    private void showCreateCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_category, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etCategoryName = dialogView.findViewById(R.id.et_category_name);
        View btnSave = dialogView.findViewById(R.id.btn_save);

        btnSave.setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim();
            if (!name.isEmpty()) {
                Category newCategory = new Category(name, "#4A90D9", false);
                dm.insertCategory(newCategory);
                selectedCategory = name;
                chipCategory.setText(selectedCategory);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showDatePicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_date_picker, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView calendarGrid = dialogView.findViewById(R.id.calendar_grid);
        TextView tvMonthYear = dialogView.findViewById(R.id.tv_month_year);
        View btnPrevMonth = dialogView.findViewById(R.id.btn_prev_month);
        View btnNextMonth = dialogView.findViewById(R.id.btn_next_month);

        Chip chipNoDate = dialogView.findViewById(R.id.chip_no_date);
        Chip chipToday = dialogView.findViewById(R.id.chip_today);
        Chip chipTomorrow = dialogView.findViewById(R.id.chip_tomorrow);
        Chip chip3Days = dialogView.findViewById(R.id.chip_3_days);
        Chip chipThisSunday = dialogView.findViewById(R.id.chip_this_sunday);

        // Make sure chips are actually checkable and not "visually selected" by default
        chipNoDate.setCheckable(true);
        chipToday.setCheckable(true);
        chipTomorrow.setCheckable(true);
        chip3Days.setCheckable(true);
        chipThisSunday.setCheckable(true);

        View repeatOption = dialogView.findViewById(R.id.repeat_option);
        TextView tvRepeatValue = dialogView.findViewById(R.id.tv_repeat_value);

        View timeOption = dialogView.findViewById(R.id.time_option);
        TextView tvTimeValue = dialogView.findViewById(R.id.tv_time_value);

        View reminderOption = dialogView.findViewById(R.id.reminder_option);
        TextView tvReminderValue = dialogView.findViewById(R.id.tv_reminder_value);
        ImageView icReminder = dialogView.findViewById(R.id.ic_reminder);
        TextView tvReminderLabel = dialogView.findViewById(R.id.tv_reminder_label);

        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        final long[] tempDueDate = { selectedDueDate };

        // Open the picker on the selected date's month (if a date is already chosen)
        Calendar calendar = Calendar.getInstance();
        if (tempDueDate[0] > 0) {
            calendar.setTimeInMillis(tempDueDate[0]);
        }
        final Calendar currentCalendar = (Calendar) calendar.clone();

        // Initialize calendar adapter
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());

        calendarGrid.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7));
        java.util.List<Calendar> days = generateDaysForMonth(currentCalendar);

        // Set initial selected date if exists
        Calendar initialSelected = Calendar.getInstance();
        if (selectedDueDate > 0) {
            initialSelected.setTimeInMillis(selectedDueDate);
        }

        // Use array to make it effectively final for lambdas
        final CalendarAdapter[] adapterHolder = new CalendarAdapter[1];
        adapterHolder[0] = new CalendarAdapter(requireContext(), days, day -> {
            if (day != null) {
                tempDueDate[0] = normalizeToStartOfDay(day.getTimeInMillis());
                adapterHolder[0].setSelectedDate(day);

                // Clear quick chips when user picks from calendar grid
                chipNoDate.setChecked(false);
                chipToday.setChecked(false);
                chipTomorrow.setChecked(false);
                chip3Days.setChecked(false);
                chipThisSunday.setChecked(false);

                applyQuickChipStyle(chipNoDate, false);
                applyQuickChipStyle(chipToday, false);
                applyQuickChipStyle(chipTomorrow, false);
                applyQuickChipStyle(chip3Days, false);
                applyQuickChipStyle(chipThisSunday, false);
            }
        });

        if (selectedDueDate > 0) {
            adapterHolder[0].setSelectedDate(initialSelected);
        }

        calendarGrid.setAdapter(adapterHolder[0]);

        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
            adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
        });

        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
            adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
        });

        // Update repeat value display
        if (!selectedRepeatType.equals("none")) {
            String repeatText = selectedRepeatInterval > 1
                    ? "Every " + selectedRepeatInterval + " " + selectedRepeatType
                    : "Every " + selectedRepeatType.substring(0, selectedRepeatType.length() - 2);
            tvRepeatValue.setText(repeatText);
        } else {
            tvRepeatValue.setText("No");
        }

        // Update time display
        if (!selectedTime.isEmpty()) {
            tvTimeValue.setText(selectedTime);
        } else {
            tvTimeValue.setText("No");
        }

        // Helper to update reminder option enabled state
        Runnable updateReminderOptionState = () -> {
            boolean timeSet = !selectedTime.isEmpty();
            float alpha = timeSet ? 1.0f : 0.4f;
            reminderOption.setAlpha(alpha);
            reminderOption.setClickable(timeSet);
            reminderOption.setEnabled(timeSet);

            if (icReminder != null) {
                icReminder.setColorFilter(timeSet ? ContextCompat.getColor(requireContext(), R.color.primary_blue)
                        : ContextCompat.getColor(requireContext(), R.color.text_hint));
            }
            if (tvReminderLabel != null) {
                tvReminderLabel.setTextColor(timeSet ? ContextCompat.getColor(requireContext(), R.color.text_primary)
                        : ContextCompat.getColor(requireContext(), R.color.text_hint));
            }

            if (!timeSet) {
                tvReminderValue.setText("Set time first");
            } else if (!selectedReminders.isEmpty()) {
                // Show reminder details
                String displayText = "";
                String[] reminderOptions = { "5 minutes before", "10 minutes before", "15 minutes before",
                        "30 minutes before", "1 hour before" };
                String[] reminderValues = { "5", "10", "15", "30", "60" };
                for (int i = 0; i < reminderValues.length; i++) {
                    if (reminderValues[i].equals(selectedReminderMinutes)) {
                        displayText = reminderOptions[i].replace(" before", "");
                        break;
                    }
                }
                tvReminderValue.setText(displayText.isEmpty() ? "On" : displayText);
            } else {
                tvReminderValue.setText("Off");
            }
        };

        // Initial reminder state
        updateReminderOptionState.run();

        timeOption.setOnClickListener(v -> {
            showTimePickerOverlay(tvTimeValue, tvReminderValue, reminderOption, updateReminderOptionState);
        });

        reminderOption.setOnClickListener(v -> {
            // Only allow reminder if time is set
            if (selectedTime.isEmpty()) {
                Toast.makeText(requireContext(), "Please set a time first", Toast.LENGTH_SHORT).show();
                return;
            }
            showReminderOverlay(tvReminderValue, updateReminderOptionState);
        });

        repeatOption.setOnClickListener(v -> {
            showRepeatOverlay(tvRepeatValue);
        });

        // Initial quick-chip state based on current selection
        if (tempDueDate[0] <= 0) {
            chipNoDate.setChecked(false);
            chipToday.setChecked(false);
            chipTomorrow.setChecked(false);
            chip3Days.setChecked(false);
            chipThisSunday.setChecked(false);
        } else {
            Calendar selectedCal = Calendar.getInstance();
            selectedCal.setTimeInMillis(tempDueDate[0]);

            Calendar today = Calendar.getInstance();
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            Calendar threeDays = Calendar.getInstance();
            threeDays.add(Calendar.DAY_OF_YEAR, 3);
            Calendar sunday = Calendar.getInstance();
            int dayOfWeek = sunday.get(Calendar.DAY_OF_WEEK);
            int daysUntilSunday = (Calendar.SUNDAY - dayOfWeek + 7) % 7;
            if (daysUntilSunday == 0)
                daysUntilSunday = 7;
            sunday.add(Calendar.DAY_OF_YEAR, daysUntilSunday);

            boolean isToday = selectedCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && selectedCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            boolean isTomorrow = selectedCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR)
                    && selectedCal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR);
            boolean isThreeDays = selectedCal.get(Calendar.YEAR) == threeDays.get(Calendar.YEAR)
                    && selectedCal.get(Calendar.DAY_OF_YEAR) == threeDays.get(Calendar.DAY_OF_YEAR);
            boolean isSunday = selectedCal.get(Calendar.YEAR) == sunday.get(Calendar.YEAR)
                    && selectedCal.get(Calendar.DAY_OF_YEAR) == sunday.get(Calendar.DAY_OF_YEAR);

            chipToday.setChecked(isToday);
            chipTomorrow.setChecked(isTomorrow);
            chip3Days.setChecked(isThreeDays);
            chipThisSunday.setChecked(isSunday);
            chipNoDate.setChecked(false);
        }

        applyQuickChipStyle(chipNoDate, chipNoDate.isChecked());
        applyQuickChipStyle(chipToday, chipToday.isChecked());
        applyQuickChipStyle(chipTomorrow, chipTomorrow.isChecked());
        applyQuickChipStyle(chip3Days, chip3Days.isChecked());
        applyQuickChipStyle(chipThisSunday, chipThisSunday.isChecked());

        chipNoDate.setOnClickListener(v -> {
            tempDueDate[0] = 0;
            adapterHolder[0].setSelectedDate(null);
            // Uncheck all quick select chips
            chipToday.setChecked(false);
            chipTomorrow.setChecked(false);
            chip3Days.setChecked(false);
            chipThisSunday.setChecked(false);

            chipNoDate.setChecked(true);
            applyQuickChipStyle(chipNoDate, true);
            applyQuickChipStyle(chipToday, false);
            applyQuickChipStyle(chipTomorrow, false);
            applyQuickChipStyle(chip3Days, false);
            applyQuickChipStyle(chipThisSunday, false);
        });

        chipToday.setOnClickListener(v -> {
            // Toggle - if already today, unselect
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            Calendar selectedCal = Calendar.getInstance();
            if (tempDueDate[0] > 0) {
                selectedCal.setTimeInMillis(tempDueDate[0]);
            }

            boolean isToday = tempDueDate[0] > 0 &&
                    selectedCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    selectedCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

            if (isToday) {
                // Unselect
                tempDueDate[0] = 0;
                adapterHolder[0].setSelectedDate(null);
                chipToday.setChecked(false);

                applyQuickChipStyle(chipToday, false);
            } else {
                tempDueDate[0] = today.getTimeInMillis();
                adapterHolder[0].setSelectedDate(today);
                chipToday.setChecked(true);
                chipTomorrow.setChecked(false);
                chip3Days.setChecked(false);
                chipThisSunday.setChecked(false);

                chipNoDate.setChecked(false);
                applyQuickChipStyle(chipNoDate, false);
                applyQuickChipStyle(chipToday, true);
                applyQuickChipStyle(chipTomorrow, false);
                applyQuickChipStyle(chip3Days, false);
                applyQuickChipStyle(chipThisSunday, false);
                // Navigate to current month if not already there
                if (currentCalendar.get(Calendar.MONTH) != today.get(Calendar.MONTH) ||
                        currentCalendar.get(Calendar.YEAR) != today.get(Calendar.YEAR)) {
                    currentCalendar.set(Calendar.YEAR, today.get(Calendar.YEAR));
                    currentCalendar.set(Calendar.MONTH, today.get(Calendar.MONTH));
                    tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
                    adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
                }
            }
        });

        chipTomorrow.setOnClickListener(v -> {
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            tomorrow.set(Calendar.HOUR_OF_DAY, 0);
            tomorrow.set(Calendar.MINUTE, 0);
            tomorrow.set(Calendar.SECOND, 0);
            tomorrow.set(Calendar.MILLISECOND, 0);
            Calendar selectedCal = Calendar.getInstance();
            if (tempDueDate[0] > 0) {
                selectedCal.setTimeInMillis(tempDueDate[0]);
            }

            boolean isTomorrow = tempDueDate[0] > 0 &&
                    selectedCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                    selectedCal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR);

            if (isTomorrow) {
                tempDueDate[0] = 0;
                adapterHolder[0].setSelectedDate(null);
                chipTomorrow.setChecked(false);

                applyQuickChipStyle(chipTomorrow, false);
            } else {
                tempDueDate[0] = tomorrow.getTimeInMillis();
                adapterHolder[0].setSelectedDate(tomorrow);
                chipToday.setChecked(false);
                chipTomorrow.setChecked(true);
                chip3Days.setChecked(false);
                chipThisSunday.setChecked(false);

                chipNoDate.setChecked(false);
                applyQuickChipStyle(chipNoDate, false);
                applyQuickChipStyle(chipToday, false);
                applyQuickChipStyle(chipTomorrow, true);
                applyQuickChipStyle(chip3Days, false);
                applyQuickChipStyle(chipThisSunday, false);
                // Navigate to month if needed
                if (currentCalendar.get(Calendar.MONTH) != tomorrow.get(Calendar.MONTH) ||
                        currentCalendar.get(Calendar.YEAR) != tomorrow.get(Calendar.YEAR)) {
                    currentCalendar.set(Calendar.YEAR, tomorrow.get(Calendar.YEAR));
                    currentCalendar.set(Calendar.MONTH, tomorrow.get(Calendar.MONTH));
                    tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
                    adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
                }
            }
        });

        chip3Days.setOnClickListener(v -> {
            Calendar threeDays = Calendar.getInstance();
            threeDays.add(Calendar.DAY_OF_YEAR, 3);
            threeDays.set(Calendar.HOUR_OF_DAY, 0);
            threeDays.set(Calendar.MINUTE, 0);
            threeDays.set(Calendar.SECOND, 0);
            threeDays.set(Calendar.MILLISECOND, 0);
            Calendar selectedCal = Calendar.getInstance();
            if (tempDueDate[0] > 0) {
                selectedCal.setTimeInMillis(tempDueDate[0]);
            }

            boolean isThreeDays = tempDueDate[0] > 0 &&
                    selectedCal.get(Calendar.YEAR) == threeDays.get(Calendar.YEAR) &&
                    selectedCal.get(Calendar.DAY_OF_YEAR) == threeDays.get(Calendar.DAY_OF_YEAR);

            if (isThreeDays) {
                tempDueDate[0] = 0;
                adapterHolder[0].setSelectedDate(null);
                chip3Days.setChecked(false);

                applyQuickChipStyle(chip3Days, false);
            } else {
                tempDueDate[0] = threeDays.getTimeInMillis();
                adapterHolder[0].setSelectedDate(threeDays);
                chipToday.setChecked(false);
                chipTomorrow.setChecked(false);
                chip3Days.setChecked(true);
                chipThisSunday.setChecked(false);

                chipNoDate.setChecked(false);
                applyQuickChipStyle(chipNoDate, false);
                applyQuickChipStyle(chipToday, false);
                applyQuickChipStyle(chipTomorrow, false);
                applyQuickChipStyle(chip3Days, true);
                applyQuickChipStyle(chipThisSunday, false);
                // Navigate to month if needed
                if (currentCalendar.get(Calendar.MONTH) != threeDays.get(Calendar.MONTH) ||
                        currentCalendar.get(Calendar.YEAR) != threeDays.get(Calendar.YEAR)) {
                    currentCalendar.set(Calendar.YEAR, threeDays.get(Calendar.YEAR));
                    currentCalendar.set(Calendar.MONTH, threeDays.get(Calendar.MONTH));
                    tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
                    adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
                }
            }
        });

        chipThisSunday.setOnClickListener(v -> {
            Calendar sunday = Calendar.getInstance();
            int dayOfWeek = sunday.get(Calendar.DAY_OF_WEEK);
            int daysUntilSunday = (Calendar.SUNDAY - dayOfWeek + 7) % 7;
            if (daysUntilSunday == 0)
                daysUntilSunday = 7;
            sunday.add(Calendar.DAY_OF_YEAR, daysUntilSunday);
            sunday.set(Calendar.HOUR_OF_DAY, 0);
            sunday.set(Calendar.MINUTE, 0);
            sunday.set(Calendar.SECOND, 0);
            sunday.set(Calendar.MILLISECOND, 0);

            Calendar selectedCal = Calendar.getInstance();
            if (tempDueDate[0] > 0) {
                selectedCal.setTimeInMillis(tempDueDate[0]);
            }

            boolean isSunday = tempDueDate[0] > 0 &&
                    selectedCal.get(Calendar.YEAR) == sunday.get(Calendar.YEAR) &&
                    selectedCal.get(Calendar.DAY_OF_YEAR) == sunday.get(Calendar.DAY_OF_YEAR);

            if (isSunday) {
                tempDueDate[0] = 0;
                adapterHolder[0].setSelectedDate(null);
                chipThisSunday.setChecked(false);

                applyQuickChipStyle(chipThisSunday, false);
            } else {
                tempDueDate[0] = sunday.getTimeInMillis();
                adapterHolder[0].setSelectedDate(sunday);
                chipToday.setChecked(false);
                chipTomorrow.setChecked(false);
                chip3Days.setChecked(false);
                chipThisSunday.setChecked(true);

                chipNoDate.setChecked(false);
                applyQuickChipStyle(chipNoDate, false);
                applyQuickChipStyle(chipToday, false);
                applyQuickChipStyle(chipTomorrow, false);
                applyQuickChipStyle(chip3Days, false);
                applyQuickChipStyle(chipThisSunday, true);
                // Navigate to month if needed
                if (currentCalendar.get(Calendar.MONTH) != sunday.get(Calendar.MONTH) ||
                        currentCalendar.get(Calendar.YEAR) != sunday.get(Calendar.YEAR)) {
                    currentCalendar.set(Calendar.YEAR, sunday.get(Calendar.YEAR));
                    currentCalendar.set(Calendar.MONTH, sunday.get(Calendar.MONTH));
                    tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
                    adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
                }
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            selectedDueDate = normalizeToStartOfDay(tempDueDate[0]);
            // If user manually picks a date here, treat it as single-date mode.
            preselectedDueDates = null;
            updateDateChipDisplay();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addSubtaskInput() {
        if (subtasksScroll != null)
            subtasksScroll.setVisibility(View.VISIBLE);

        // Limit max subtasks to prevent UI overflow (max 5 visible, scrollable)
        limitSubtasksScrollHeight();

        View subtaskView = LayoutInflater.from(requireContext()).inflate(R.layout.item_subtask_input, subtasksContainer,
                false);
        final EditText etSubtask = subtaskView.findViewById(R.id.et_subtask);
        ImageButton btnRemove = subtaskView.findViewById(R.id.btn_remove_subtask);

        btnRemove.setOnClickListener(v -> {
            subtasksContainer.removeView(subtaskView);
            if (subtasksContainer.getChildCount() == 0) {
                if (subtasksScroll != null)
                    subtasksScroll.setVisibility(View.GONE);
            }
            limitSubtasksScrollHeight();
        });

        // Auto-add next row when user presses Enter/Next after typing
        etSubtask.setOnEditorActionListener((v, actionId, event) -> {
            String text = etSubtask.getText().toString().trim();
            if (!text.isEmpty()) {
                // Add a new empty input for next subtask
                addSubtaskInput();
            }
            return true;
        });

        subtasksContainer.addView(subtaskView);

        // Clear focus from all other subtask inputs first
        for (int i = 0; i < subtasksContainer.getChildCount() - 1; i++) {
            View child = subtasksContainer.getChildAt(i);
            EditText et = child.findViewById(R.id.et_subtask);
            if (et != null) {
                et.clearFocus();
            }
        }

        // Scroll to bottom first, then focus on new subtask
        if (subtasksScroll != null) {
            subtasksScroll.post(() -> {
                subtasksScroll.scrollTo(0, subtasksContainer.getHeight());

                // Now focus on the new EditText after scroll
                etSubtask.postDelayed(() -> {
                    etSubtask.requestFocus();
                    // Move cursor to end
                    etSubtask.setSelection(etSubtask.getText().length());
                    showKeyboard(etSubtask);
                }, 100);
            });
        } else {
            etSubtask.post(() -> {
                etSubtask.requestFocus();
                showKeyboard(etSubtask);
            });
        }
    }

    private void limitSubtasksScrollHeight() {
        if (subtasksScroll != null) {
            // Limit scroll height to show max 4 subtasks (about 160dp)
            int maxHeightPx = (int) (160 * getResources().getDisplayMetrics().density);
            ViewGroup.LayoutParams params = subtasksScroll.getLayoutParams();

            // Measure current content height
            subtasksContainer.measure(
                    View.MeasureSpec.makeMeasureSpec(subtasksScroll.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int contentHeight = subtasksContainer.getMeasuredHeight();

            if (contentHeight > maxHeightPx) {
                params.height = maxHeightPx;
            } else {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            subtasksScroll.setLayoutParams(params);
        }
    }

    private void showKeyboard(EditText editText) {
        try {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext()
                    .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveTask() {
        String taskTitle = etTaskTitle.getText().toString().trim();
        if (taskTitle.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a task", Toast.LENGTH_SHORT).show();
            return;
        }

        // Set reminder minutes (default 5 if none selected and time is set)
        String reminderMinutesStr = "";
        if (!selectedReminders.isEmpty()) {
            reminderMinutesStr = android.text.TextUtils.join(",", selectedReminders);
        } else if (!selectedTime.isEmpty()) {
            reminderMinutesStr = "5"; // Default 5 min reminder
        }

        // Set time/details if provided
        String details = etTaskDetails.getText().toString().trim();

        long taskId;

        if (isEditMode && editingTask != null) {
            // Update existing task
            editingTask.setTask(taskTitle);
            editingTask.setCategory(selectedCategory.equals("No Category") ? "" : selectedCategory);

            // Default to today's date if no date selected
            long dueDate = selectedDueDate;
            if (dueDate <= 0) {
                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);
                dueDate = today.getTimeInMillis();
            }
            editingTask.setDueDate(dueDate);

            editingTask.setRepeatType(selectedRepeatType);
            editingTask.setRepeatInterval(selectedRepeatInterval);
            // Save custom weekday selections as repeatDays
            editingTask.setRepeatDays(buildRepeatDaysString());
            editingTask.setTaskTime(selectedTime);
            editingTask.setReminderMinutes(reminderMinutesStr);
            editingTask.setUseAlarm(useAlarm ? 1 : 0);
            editingTask.setScreenLock(selectedScreenLock ? 1 : 0);
            if (!details.isEmpty()) {
                editingTask.setTime(details);
            }

            dm.updateTask(editingTask);
            taskId = editingTask.getId();

            // Cancel old reminders and schedule new ones
            NotificationHelper notificationHelper = new NotificationHelper(requireContext());
            notificationHelper.cancelReminders((int) taskId);

            if (dueDate > 0 && !selectedTime.isEmpty() && !reminderMinutesStr.isEmpty()) {
                notificationHelper.scheduleReminders(
                        (int) taskId,
                        taskTitle,
                        dueDate,
                        selectedTime,
                        reminderMinutesStr,
                        useAlarm,
                        selectedScreenLock);
            }

            Toast.makeText(requireContext(), "Task updated", Toast.LENGTH_SHORT).show();
        } else {
            // Create new task(s): if Calendar multi-selected dates were provided, create
            // one per date.
            List<Long> dueDates;
            if (preselectedDueDates != null && preselectedDueDates.size() > 1) {
                dueDates = new ArrayList<>(preselectedDueDates);
            } else if (selectedDueDate > 0) {
                dueDates = Collections.singletonList(selectedDueDate);
            } else {
                // Default to today's date if no date selected
                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);
                dueDates = Collections.singletonList(today.getTimeInMillis());
            }

            // Read subtasks once; reuse across multi-date tasks.
            ArrayList<String> subtaskTitles = new ArrayList<>();
            for (int i = 0; i < subtasksContainer.getChildCount(); i++) {
                View subtaskView = subtasksContainer.getChildAt(i);
                EditText etSubtask = subtaskView.findViewById(R.id.et_subtask);
                String subtaskTitle = etSubtask.getText().toString().trim();
                if (!subtaskTitle.isEmpty()) {
                    subtaskTitles.add(subtaskTitle);
                }
            }

            NotificationHelper notificationHelper = null;
            if (!selectedTime.isEmpty() && !reminderMinutesStr.isEmpty()) {
                notificationHelper = new NotificationHelper(requireContext());
            }

            long lastInsertedId = 0;
            int created = 0;
            for (Long due : dueDates) {
                long dueMs = due == null ? 0 : due;
                TaskList newTask = new TaskList(taskTitle);
                newTask.setCategory(selectedCategory.equals("No Category") ? "" : selectedCategory);
                newTask.setDueDate(dueMs);
                newTask.setRepeatType(selectedRepeatType);
                newTask.setRepeatInterval(selectedRepeatInterval);
                // Save custom weekday selections as repeatDays
                newTask.setRepeatDays(buildRepeatDaysString());
                newTask.setStarred(0);
                newTask.setStatus(0);
                newTask.setTaskTime(selectedTime);
                newTask.setReminderMinutes(reminderMinutesStr);
                newTask.setUseAlarm(useAlarm ? 1 : 0);
                newTask.setScreenLock(selectedScreenLock ? 1 : 0);
                newTask.setCreatedFrom(createdFrom); // Set the source (tasks or calendar)
                if (!details.isEmpty()) {
                    newTask.setTime(details);
                }

                long insertedId = dm.insertAndGetId(newTask);
                lastInsertedId = insertedId;
                created++;

                // Schedule reminders per task id/date
                if (notificationHelper != null && dueMs > 0) {
                    notificationHelper.scheduleReminders(
                            (int) insertedId,
                            taskTitle,
                            dueMs,
                            selectedTime,
                            reminderMinutesStr,
                            useAlarm,
                            selectedScreenLock);
                }

                // Save subtasks per created task
                for (String st : subtaskTitles) {
                    SubTask subTask = new SubTask((int) insertedId, st);
                    dm.insertSubTask(subTask);
                }
            }

            taskId = lastInsertedId;
            Toast.makeText(requireContext(), created > 1 ? ("Added " + created + " tasks") : "Task added",
                    Toast.LENGTH_SHORT).show();
        }

        if (listener != null) {
            listener.onTaskAdded();
        }

        dismiss();
    }

    private List<Calendar> generateDaysForMonth(Calendar currentCalendar) {
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

    // Old showRepeatDialog removed - now using showRepeatOverlay

    private void showTimePicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_picker, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvHour = dialogView.findViewById(R.id.tv_hour);
        TextView tvMinute = dialogView.findViewById(R.id.tv_minute);
        TextView tvAm = dialogView.findViewById(R.id.tv_am);
        TextView tvPm = dialogView.findViewById(R.id.tv_pm);
        TimePicker timePicker = dialogView.findViewById(R.id.time_picker);

        Chip chipNoTime = dialogView.findViewById(R.id.chip_no_time);
        Chip chip7am = dialogView.findViewById(R.id.chip_7am);
        Chip chip9am = dialogView.findViewById(R.id.chip_9am);
        Chip chip10am = dialogView.findViewById(R.id.chip_10am);
        Chip chip12pm = dialogView.findViewById(R.id.chip_12pm);
        Chip chip2pm = dialogView.findViewById(R.id.chip_2pm);
        Chip chip4pm = dialogView.findViewById(R.id.chip_4pm);
        Chip chip6pm = dialogView.findViewById(R.id.chip_6pm);

        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        // Set 12-hour format
        timePicker.setIs24HourView(false);

        // Hide the TimePicker's internal header and keyboard toggle
        hideTimePickerHeader(timePicker);

        final int[] tempHour = { 7 };
        final int[] tempMinute = { 0 };
        final boolean[] tempIsAm = { true };
        final boolean[] noTimeSelected = { selectedTime.isEmpty() };

        // Parse existing time if set
        if (!selectedTime.isEmpty()) {
            try {
                String[] parts = selectedTime.replace(" AM", "").replace(" PM", "").split(":");
                tempHour[0] = Integer.parseInt(parts[0]);
                tempMinute[0] = Integer.parseInt(parts[1].split(" ")[0]);
                tempIsAm[0] = selectedTime.contains("AM");
                noTimeSelected[0] = false;
            } catch (Exception e) {
                // Use default time
            }
        }

        // Update display
        Runnable updateDisplay = () -> {
            if (noTimeSelected[0]) {
                tvHour.setText("--");
                tvMinute.setText("--");
                tvHour.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                tvMinute.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            } else {
                tvHour.setText(String.valueOf(tempHour[0]));
                tvMinute.setText(String.format(java.util.Locale.getDefault(), "%02d", tempMinute[0]));
                tvHour.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                tvMinute.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }

            if (tempIsAm[0]) {
                tvAm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                tvAm.setTypeface(null, android.graphics.Typeface.BOLD);
                tvPm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                tvPm.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else {
                tvPm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                tvPm.setTypeface(null, android.graphics.Typeface.BOLD);
                tvAm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                tvAm.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            // Update chip styles
            applyQuickChipStyle(chipNoTime, noTimeSelected[0]);
            applyQuickChipStyle(chip7am, !noTimeSelected[0] && tempHour[0] == 7 && tempMinute[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip9am, !noTimeSelected[0] && tempHour[0] == 9 && tempMinute[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip10am, !noTimeSelected[0] && tempHour[0] == 10 && tempMinute[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip12pm,
                    !noTimeSelected[0] && tempHour[0] == 12 && tempMinute[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip2pm, !noTimeSelected[0] && tempHour[0] == 2 && tempMinute[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip4pm, !noTimeSelected[0] && tempHour[0] == 4 && tempMinute[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip6pm, !noTimeSelected[0] && tempHour[0] == 6 && tempMinute[0] == 0 && !tempIsAm[0]);
        };

        // Initialize display
        updateDisplay.run();

        // Set TimePicker initial value
        int initial24Hour = tempHour[0];
        if (!tempIsAm[0] && tempHour[0] != 12) {
            initial24Hour += 12;
        } else if (tempIsAm[0] && tempHour[0] == 12) {
            initial24Hour = 0;
        }
        timePicker.setHour(initial24Hour);
        timePicker.setMinute(tempMinute[0]);

        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            noTimeSelected[0] = false;
            tempMinute[0] = minute;
            tempIsAm[0] = hourOfDay < 12;
            tempHour[0] = hourOfDay % 12;
            if (tempHour[0] == 0)
                tempHour[0] = 12;
            updateDisplay.run();
        });

        tvAm.setOnClickListener(v -> {
            tempIsAm[0] = true;
            updateDisplay.run();
        });

        tvPm.setOnClickListener(v -> {
            tempIsAm[0] = false;
            updateDisplay.run();
        });

        // Quick time chip handlers
        chipNoTime.setOnClickListener(v -> {
            noTimeSelected[0] = true;
            updateDisplay.run();
        });

        chip7am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 7;
            tempMinute[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(7);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        chip9am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 9;
            tempMinute[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(9);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        chip10am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 10;
            tempMinute[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(10);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        chip12pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 12;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(12);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        chip2pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 2;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(14);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        chip4pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 4;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(16);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        chip6pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 6;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(18);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            if (noTimeSelected[0]) {
                selectedTime = "";
            } else {
                String amPm = tempIsAm[0] ? "AM" : "PM";
                selectedTime = String.format(java.util.Locale.getDefault(), "%d:%02d %s", tempHour[0], tempMinute[0],
                        amPm);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showReminderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reminder_new, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvReminderTitle = dialogView.findViewById(R.id.tv_reminder_title);
        SwitchMaterial switchReminder = dialogView.findViewById(R.id.switch_reminder);
        LinearLayout reminderAtOption = dialogView.findViewById(R.id.reminder_at_option);
        TextView tvReminderAtValue = dialogView.findViewById(R.id.tv_reminder_at_value);
        LinearLayout reminderTypeOption = dialogView.findViewById(R.id.reminder_type_option);
        TextView tvReminderTypeValue = dialogView.findViewById(R.id.tv_reminder_type_value);
        LinearLayout screenlockOption = dialogView.findViewById(R.id.screenlock_option);
        TextView tvScreenlockValue = dialogView.findViewById(R.id.tv_screenlock_value);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        // Temp state
        final boolean[] tempReminderOn = { !selectedReminders.isEmpty() || useAlarm };
        final String[] tempReminderMinutes = { selectedReminderMinutes };
        final String[] tempReminderType = { useAlarm ? "alarm" : "notification" };

        // Reminder minute options
        final String[] reminderOptions = { "5 minutes before", "10 minutes before", "15 minutes before",
                "30 minutes before", "1 hour before" };
        final String[] reminderValues = { "5", "10", "15", "30", "60" };

        // Update UI state
        Runnable updateUI = () -> {
            switchReminder.setChecked(tempReminderOn[0]);
            tvReminderTitle.setText(tempReminderOn[0] ? "Reminder is on" : "Reminder is off");

            // Find display text for reminder minutes
            String reminderDisplay = "5 minutes before";
            for (int i = 0; i < reminderValues.length; i++) {
                if (reminderValues[i].equals(tempReminderMinutes[0])) {
                    reminderDisplay = reminderOptions[i];
                    break;
                }
            }
            tvReminderAtValue.setText(reminderDisplay);

            // Reminder type
            tvReminderTypeValue.setText(tempReminderType[0].equals("alarm") ? "Alarm" : "Notification");

            // Enable/disable options based on switch
            float alpha = tempReminderOn[0] ? 1.0f : 0.5f;
            reminderAtOption.setAlpha(alpha);
            reminderTypeOption.setAlpha(alpha);
            screenlockOption.setAlpha(alpha);
            reminderAtOption.setClickable(tempReminderOn[0]);
            reminderTypeOption.setClickable(tempReminderOn[0]);
            screenlockOption.setClickable(tempReminderOn[0]);
        };

        updateUI.run();

        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tempReminderOn[0] = isChecked;
            updateUI.run();
        });

        reminderAtOption.setOnClickListener(v -> {
            if (!tempReminderOn[0])
                return;

            PopupMenu popup = new PopupMenu(requireContext(), tvReminderAtValue);
            for (String option : reminderOptions) {
                popup.getMenu().add(option);
            }
            popup.setOnMenuItemClickListener(item -> {
                String selected = item.getTitle().toString();
                for (int i = 0; i < reminderOptions.length; i++) {
                    if (reminderOptions[i].equals(selected)) {
                        tempReminderMinutes[0] = reminderValues[i];
                        break;
                    }
                }
                updateUI.run();
                return true;
            });
            popup.show();
        });

        reminderTypeOption.setOnClickListener(v -> {
            if (!tempReminderOn[0])
                return;

            PopupMenu popup = new PopupMenu(requireContext(), tvReminderTypeValue);
            popup.getMenu().add("Notification");
            popup.getMenu().add("Alarm");
            popup.setOnMenuItemClickListener(item -> {
                tempReminderType[0] = item.getTitle().toString().toLowerCase();
                updateUI.run();
                return true;
            });
            popup.show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            selectedReminders.clear();
            if (tempReminderOn[0]) {
                selectedReminders.add(tempReminderMinutes[0]);
                selectedReminderMinutes = tempReminderMinutes[0];
            }
            useAlarm = tempReminderType[0].equals("alarm");
            selectedReminderType = tempReminderType[0];

            dialog.dismiss();
            showDatePicker(); // Return to date picker to show updated reminder count
        });

        dialog.show();
    }

    // ========== OVERLAY DIALOG METHODS (Don't close parent date picker) ==========

    private void showTimePickerOverlay(TextView tvTimeValue, TextView tvReminderValue, View reminderOption,
            Runnable onTimeChanged) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_picker, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Hide keyboard when dialog opens
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }

        TextView tvHour = dialogView.findViewById(R.id.tv_hour);
        TextView tvMinute = dialogView.findViewById(R.id.tv_minute);
        TextView tvAm = dialogView.findViewById(R.id.tv_am);
        TextView tvPm = dialogView.findViewById(R.id.tv_pm);
        TimePicker timePicker = dialogView.findViewById(R.id.time_picker);

        // Hide the TimePicker's internal header and keyboard toggle
        hideTimePickerHeader(timePicker);

        Chip chipNoTime = dialogView.findViewById(R.id.chip_no_time);
        Chip chip7am = dialogView.findViewById(R.id.chip_7am);
        Chip chip9am = dialogView.findViewById(R.id.chip_9am);
        Chip chip10am = dialogView.findViewById(R.id.chip_10am);
        Chip chip12pm = dialogView.findViewById(R.id.chip_12pm);
        Chip chip2pm = dialogView.findViewById(R.id.chip_2pm);
        Chip chip4pm = dialogView.findViewById(R.id.chip_4pm);
        Chip chip6pm = dialogView.findViewById(R.id.chip_6pm);

        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        timePicker.setIs24HourView(false);

        final int[] tempHour = { 7 };
        final int[] tempMinute = { 0 };
        final boolean[] tempIsAm = { true };
        final boolean[] noTimeSelected = { selectedTime.isEmpty() };

        if (!selectedTime.isEmpty()) {
            try {
                String[] parts = selectedTime.replace(" AM", "").replace(" PM", "").split(":");
                tempHour[0] = Integer.parseInt(parts[0]);
                tempMinute[0] = Integer.parseInt(parts[1].split(" ")[0]);
                tempIsAm[0] = selectedTime.contains("AM");
                noTimeSelected[0] = false;
            } catch (Exception e) {
            }
        }

        Runnable updateDisplay = () -> {
            if (noTimeSelected[0]) {
                tvHour.setText("--");
                tvMinute.setText("--");
                tvHour.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                tvMinute.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            } else {
                tvHour.setText(String.valueOf(tempHour[0]));
                tvMinute.setText(String.format(java.util.Locale.getDefault(), "%02d", tempMinute[0]));
                tvHour.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                tvMinute.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }

            if (tempIsAm[0]) {
                tvAm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                tvAm.setTypeface(null, android.graphics.Typeface.BOLD);
                tvPm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                tvPm.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else {
                tvPm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                tvPm.setTypeface(null, android.graphics.Typeface.BOLD);
                tvAm.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                tvAm.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            applyQuickChipStyle(chipNoTime, noTimeSelected[0]);
            applyQuickChipStyle(chip7am, !noTimeSelected[0] && tempHour[0] == 7 && tempMinute[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip9am, !noTimeSelected[0] && tempHour[0] == 9 && tempMinute[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip10am, !noTimeSelected[0] && tempHour[0] == 10 && tempMinute[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip12pm,
                    !noTimeSelected[0] && tempHour[0] == 12 && tempMinute[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip2pm, !noTimeSelected[0] && tempHour[0] == 2 && tempMinute[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip4pm, !noTimeSelected[0] && tempHour[0] == 4 && tempMinute[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip6pm, !noTimeSelected[0] && tempHour[0] == 6 && tempMinute[0] == 0 && !tempIsAm[0]);
        };

        updateDisplay.run();

        int initial24Hour = tempHour[0];
        if (!tempIsAm[0] && tempHour[0] != 12)
            initial24Hour += 12;
        else if (tempIsAm[0] && tempHour[0] == 12)
            initial24Hour = 0;
        timePicker.setHour(initial24Hour);
        timePicker.setMinute(tempMinute[0]);

        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            noTimeSelected[0] = false;
            tempMinute[0] = minute;
            tempIsAm[0] = hourOfDay < 12;
            tempHour[0] = hourOfDay % 12;
            if (tempHour[0] == 0)
                tempHour[0] = 12;
            updateDisplay.run();
        });

        tvAm.setOnClickListener(v -> {
            tempIsAm[0] = true;
            updateDisplay.run();
        });
        tvPm.setOnClickListener(v -> {
            tempIsAm[0] = false;
            updateDisplay.run();
        });

        chipNoTime.setOnClickListener(v -> {
            noTimeSelected[0] = true;
            updateDisplay.run();
        });
        chip7am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 7;
            tempMinute[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(7);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip9am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 9;
            tempMinute[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(9);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip10am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 10;
            tempMinute[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(10);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip12pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 12;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(12);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip2pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 2;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(14);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip4pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 4;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(16);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip6pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 6;
            tempMinute[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(18);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            if (noTimeSelected[0]) {
                selectedTime = "";
                tvTimeValue.setText("No");
                // Clear reminder if time is cleared
                selectedReminders.clear();
                tvReminderValue.setText("Set time first");
            } else {
                String amPm = tempIsAm[0] ? "AM" : "PM";
                selectedTime = String.format(java.util.Locale.getDefault(), "%d:%02d %s", tempHour[0], tempMinute[0],
                        amPm);
                tvTimeValue.setText(selectedTime);
            }
            // Update reminder option state
            if (onTimeChanged != null) {
                onTimeChanged.run();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showReminderOverlay(TextView tvReminderValue, Runnable onReminderChanged) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reminder_new, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvReminderTitle = dialogView.findViewById(R.id.tv_reminder_title);
        SwitchMaterial switchReminder = dialogView.findViewById(R.id.switch_reminder);
        LinearLayout reminderAtOption = dialogView.findViewById(R.id.reminder_at_option);
        TextView tvReminderAtValue = dialogView.findViewById(R.id.tv_reminder_at_value);
        LinearLayout reminderTypeOption = dialogView.findViewById(R.id.reminder_type_option);
        TextView tvReminderTypeValue = dialogView.findViewById(R.id.tv_reminder_type_value);
        LinearLayout screenlockOption = dialogView.findViewById(R.id.screenlock_option);
        TextView tvScreenlockValue = dialogView.findViewById(R.id.tv_screenlock_value);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        final boolean[] tempReminderOn = { !selectedReminders.isEmpty() || useAlarm };
        final String[] tempReminderMinutes = { selectedReminderMinutes };
        final String[] tempReminderType = { useAlarm ? "alarm" : "notification" };
        final boolean[] tempScreenlock = { selectedScreenLock }; // Track screenlock state

        final String[] reminderOptions = { "5 minutes before", "10 minutes before", "15 minutes before",
                "30 minutes before", "1 hour before" };
        final String[] reminderValues = { "5", "10", "15", "30", "60" };

        Runnable updateUI = () -> {
            switchReminder.setChecked(tempReminderOn[0]);
            tvReminderTitle.setText(tempReminderOn[0] ? "Reminder is on" : "Reminder is off");

            String reminderDisplay = "5 minutes before";
            for (int i = 0; i < reminderValues.length; i++) {
                if (reminderValues[i].equals(tempReminderMinutes[0])) {
                    reminderDisplay = reminderOptions[i];
                    break;
                }
            }
            tvReminderAtValue.setText(reminderDisplay);
            tvReminderTypeValue.setText(tempReminderType[0].equals("alarm") ? "Alarm" : "Notification");
            tvScreenlockValue.setText(tempScreenlock[0] ? "On" : "Off");

            float alpha = tempReminderOn[0] ? 1.0f : 0.5f;
            reminderAtOption.setAlpha(alpha);
            reminderTypeOption.setAlpha(alpha);
            screenlockOption.setAlpha(alpha);
            reminderAtOption.setClickable(tempReminderOn[0]);
            reminderTypeOption.setClickable(tempReminderOn[0]);
            screenlockOption.setClickable(tempReminderOn[0]);
        };

        updateUI.run();

        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tempReminderOn[0] = isChecked;
            updateUI.run();
        });

        reminderAtOption.setOnClickListener(v -> {
            if (!tempReminderOn[0])
                return;
            showDropdownDialog(reminderOptions, tempReminderMinutes[0].equals("5") ? 0
                    : tempReminderMinutes[0].equals("10") ? 1
                            : tempReminderMinutes[0].equals("15") ? 2 : tempReminderMinutes[0].equals("30") ? 3 : 4,
                    index -> {
                        tempReminderMinutes[0] = reminderValues[index];
                        updateUI.run();
                    });
        });

        reminderTypeOption.setOnClickListener(v -> {
            if (!tempReminderOn[0])
                return;
            String[] typeOptions = { "Notification", "Alarm" };
            int currentIndex = tempReminderType[0].equals("alarm") ? 1 : 0;
            showDropdownDialog(typeOptions, currentIndex, index -> {
                tempReminderType[0] = typeOptions[index].toLowerCase();
                updateUI.run();
            });
        });

        screenlockOption.setOnClickListener(v -> {
            if (!tempReminderOn[0])
                return;
            String[] screenlockOptions = { "Off", "On" };
            int currentIndex = tempScreenlock[0] ? 1 : 0;
            showDropdownDialog(screenlockOptions, currentIndex, index -> {
                tempScreenlock[0] = index == 1;
                updateUI.run();
            });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            selectedReminders.clear();
            if (tempReminderOn[0]) {
                selectedReminders.add(tempReminderMinutes[0]);
                selectedReminderMinutes = tempReminderMinutes[0];

                // Build display text
                String displayText = "";
                for (int i = 0; i < reminderValues.length; i++) {
                    if (reminderValues[i].equals(tempReminderMinutes[0])) {
                        displayText = reminderOptions[i].replace(" before", "");
                        break;
                    }
                }
                tvReminderValue.setText(displayText);
            } else {
                tvReminderValue.setText("Off");
            }
            useAlarm = tempReminderType[0].equals("alarm");
            selectedReminderType = tempReminderType[0];
            selectedScreenLock = tempScreenlock[0];

            // Update parent dialog UI
            if (onReminderChanged != null) {
                onReminderChanged.run();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showRepeatOverlay(TextView tvRepeatValue) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_repeat_new, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Pattern options (No Repeat, Weekly, Monthly, Yearly)
        View optionNoRepeat = dialogView.findViewById(R.id.option_no_repeat);
        View optionWeekly = dialogView.findViewById(R.id.option_weekly);
        View optionMonthly = dialogView.findViewById(R.id.option_monthly);
        View optionYearly = dialogView.findViewById(R.id.option_yearly);

        ImageView checkNoRepeat = dialogView.findViewById(R.id.check_no_repeat);
        ImageView checkWeekly = dialogView.findViewById(R.id.check_weekly);
        ImageView checkMonthly = dialogView.findViewById(R.id.check_monthly);
        ImageView checkYearly = dialogView.findViewById(R.id.check_yearly);

        // Weekday TextViews (not Chips)
        TextView tvSun = dialogView.findViewById(R.id.chip_sun);
        TextView tvMon = dialogView.findViewById(R.id.chip_mon);
        TextView tvTue = dialogView.findViewById(R.id.chip_tue);
        TextView tvWed = dialogView.findViewById(R.id.chip_wed);
        TextView tvThu = dialogView.findViewById(R.id.chip_thu);
        TextView tvFri = dialogView.findViewById(R.id.chip_fri);
        TextView tvSat = dialogView.findViewById(R.id.chip_sat);

        // Preset chips
        Chip chipWorkWeek = dialogView.findViewById(R.id.chip_work_week);
        Chip chipWeekend = dialogView.findViewById(R.id.chip_weekend);
        Chip chipEveryday = dialogView.findViewById(R.id.chip_everyday);

        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        final String[] tempRepeatType = { selectedRepeatType };
        final int[] tempRepeatInterval = { selectedRepeatInterval };
        final boolean[] tempWeekdays = selectedWeekdays.clone();

        TextView[] dayViews = { tvSun, tvMon, tvTue, tvWed, tvThu, tvFri, tvSat };
        String[] dayNames = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };

        // Update weekday button visual states
        Runnable updateWeekdayButtons = () -> {
            for (int i = 0; i < 7; i++) {
                if (tempWeekdays[i]) {
                    dayViews[i].setSelected(true);
                    dayViews[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                    dayViews[i].setBackgroundResource(R.drawable.day_circle_selected_bg);
                } else {
                    dayViews[i].setSelected(false);
                    dayViews[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                    dayViews[i].setBackgroundResource(R.drawable.day_circle_bg);
                }
            }

            // Highlight preset chips if they match current selection
            boolean isWorkWeek = tempWeekdays[1] && tempWeekdays[2] && tempWeekdays[3] &&
                    tempWeekdays[4] && tempWeekdays[5] && !tempWeekdays[0] && !tempWeekdays[6];
            boolean isWeekend = tempWeekdays[0] && tempWeekdays[6] &&
                    !tempWeekdays[1] && !tempWeekdays[2] && !tempWeekdays[3] &&
                    !tempWeekdays[4] && !tempWeekdays[5];
            boolean isEveryday = tempWeekdays[0] && tempWeekdays[1] && tempWeekdays[2] &&
                    tempWeekdays[3] && tempWeekdays[4] && tempWeekdays[5] && tempWeekdays[6];

            applyQuickChipStyle(chipWorkWeek, isWorkWeek);
            applyQuickChipStyle(chipWeekend, isWeekend);
            applyQuickChipStyle(chipEveryday, isEveryday);
        };

        // Update check marks based on selected pattern
        Runnable updateChecks = () -> {
            checkNoRepeat.setVisibility(tempRepeatType[0].equals("none") ? View.VISIBLE : View.GONE);
            checkWeekly.setVisibility(tempRepeatType[0].equals("weeks") ? View.VISIBLE : View.GONE);
            checkMonthly.setVisibility(tempRepeatType[0].equals("months") ? View.VISIBLE : View.GONE);
            checkYearly.setVisibility(tempRepeatType[0].equals("years") ? View.VISIBLE : View.GONE);

            updateWeekdayButtons.run();
        };

        updateChecks.run();

        // Pattern option click handlers
        optionNoRepeat.setOnClickListener(v -> {
            tempRepeatType[0] = "none";
            java.util.Arrays.fill(tempWeekdays, false);
            updateChecks.run();
        });

        optionWeekly.setOnClickListener(v -> {
            tempRepeatType[0] = "weeks";
            tempRepeatInterval[0] = 1;
            java.util.Arrays.fill(tempWeekdays, false);
            updateChecks.run();
        });

        optionMonthly.setOnClickListener(v -> {
            tempRepeatType[0] = "months";
            tempRepeatInterval[0] = 1;
            java.util.Arrays.fill(tempWeekdays, false);
            updateChecks.run();
        });

        optionYearly.setOnClickListener(v -> {
            tempRepeatType[0] = "years";
            tempRepeatInterval[0] = 1;
            java.util.Arrays.fill(tempWeekdays, false);
            updateChecks.run();
        });

        // Individual weekday button click handlers
        for (int i = 0; i < 7; i++) {
            final int dayIndex = i;
            dayViews[i].setOnClickListener(v -> {
                tempWeekdays[dayIndex] = !tempWeekdays[dayIndex];
                tempRepeatType[0] = "custom_days";
                updateChecks.run();
            });
        }

        // Preset chip click handlers
        chipWorkWeek.setOnClickListener(v -> {
            java.util.Arrays.fill(tempWeekdays, false);
            tempWeekdays[1] = true;
            tempWeekdays[2] = true;
            tempWeekdays[3] = true;
            tempWeekdays[4] = true;
            tempWeekdays[5] = true;
            tempRepeatType[0] = "custom_days";
            updateChecks.run();
        });

        chipWeekend.setOnClickListener(v -> {
            java.util.Arrays.fill(tempWeekdays, false);
            tempWeekdays[0] = true;
            tempWeekdays[6] = true;
            tempRepeatType[0] = "custom_days";
            updateChecks.run();
        });

        chipEveryday.setOnClickListener(v -> {
            java.util.Arrays.fill(tempWeekdays, true);
            tempRepeatType[0] = "days";
            updateChecks.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            selectedRepeatType = tempRepeatType[0];
            selectedRepeatInterval = tempRepeatInterval[0];
            System.arraycopy(tempWeekdays, 0, selectedWeekdays, 0, 7);

            // Update display text
            if (tempRepeatType[0].equals("none")) {
                tvRepeatValue.setText("No");
            } else if (tempRepeatType[0].equals("custom_days")) {
                // Build days string
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 7; i++) {
                    if (tempWeekdays[i]) {
                        if (sb.length() > 0)
                            sb.append(", ");
                        sb.append(dayNames[i]);
                    }
                }
                tvRepeatValue.setText(sb.length() > 0 ? sb.toString() : "No");
            } else if (tempRepeatType[0].equals("days")) {
                tvRepeatValue.setText("Daily");
            } else if (tempRepeatType[0].equals("weeks")) {
                tvRepeatValue.setText("Weekly");
            } else if (tempRepeatType[0].equals("months")) {
                tvRepeatValue.setText("Monthly");
            } else if (tempRepeatType[0].equals("years")) {
                tvRepeatValue.setText("Yearly");
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * Hide the TimePicker's internal header (hour:minute display) and keyboard
     * toggle icon.
     * This allows us to use our own custom display.
     */
    private void hideTimePickerHeader(TimePicker timePicker) {
        try {
            // Hide the header area (contains hour:minute display and AM/PM)
            int headerId = getResources().getIdentifier("time_header", "id", "android");
            if (headerId != 0) {
                View header = timePicker.findViewById(headerId);
                if (header != null) {
                    header.setVisibility(View.GONE);
                }
            }

            // Hide the keyboard toggle icon
            int toggleId = getResources().getIdentifier("toggle_mode", "id", "android");
            if (toggleId != 0) {
                View toggle = timePicker.findViewById(toggleId);
                if (toggle != null) {
                    toggle.setVisibility(View.GONE);
                }
            }

            // Alternative approach - try to find ImageButton (keyboard toggle)
            hideViewByType(timePicker, android.widget.ImageButton.class);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Recursively hide views of a specific type within a parent view.
     */
    private void hideViewByType(View parent, Class<?> targetType) {
        if (parent == null)
            return;

        if (targetType.isInstance(parent)) {
            parent.setVisibility(View.GONE);
            return;
        }

        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                hideViewByType(group.getChildAt(i), targetType);
            }
        }
    }

    // White dialog style dropdown selector
    private interface OnDropdownItemSelected {
        void onSelected(int index);
    }

    private void showDropdownDialog(String[] options, int selectedIndex, OnDropdownItemSelected listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dropdown_selector, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        LinearLayout optionsContainer = dialogView.findViewById(R.id.options_container);

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            View optionView = LayoutInflater.from(requireContext()).inflate(R.layout.item_dropdown_option,
                    optionsContainer, false);

            TextView tvOptionText = optionView.findViewById(R.id.tv_option_text);
            ImageView ivOptionCheck = optionView.findViewById(R.id.iv_option_check);

            tvOptionText.setText(options[i]);
            ivOptionCheck.setVisibility(i == selectedIndex ? View.VISIBLE : View.GONE);

            if (i == selectedIndex) {
                tvOptionText.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
            }

            optionView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSelected(index);
                }
                dialog.dismiss();
            });

            optionsContainer.addView(optionView);
        }

        dialog.show();
    }

    /**
     * Builds a comma-separated string of Calendar day-of-week constants
     * (1=Sunday..7=Saturday)
     * from the selectedWeekdays boolean array.
     * Returns empty string if repeatType is not "custom_days" or no days selected.
     */
    private String buildRepeatDaysString() {
        if (!"custom_days".equals(selectedRepeatType)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (selectedWeekdays[i]) {
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(i + 1); // Calendar.SUNDAY=1 .. Calendar.SATURDAY=7
            }
        }
        return sb.toString();
    }
}
