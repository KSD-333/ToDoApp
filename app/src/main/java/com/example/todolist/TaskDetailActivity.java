package com.example.todolist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TaskDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "task_id";

    private DataManager dm;
    private TaskList task;

    private TextView tvCategory;
    private EditText etTitle;

    private View scheduleDetails;
    private TextView tvScheduleDate;
    private TextView tvScheduleTime;
    private TextView tvScheduleRepeat;
    private TextView tvNotesAction;
    private CardView notesPreviewCard;
    private TextView tvNotesPreview;
    private TextView tvAttachmentAction;
    private LinearLayout attachmentsContainer;

    // Subtasks views
    private CardView subtasksCard;
    private LinearLayout subtasksContainer;
    private ProgressBar subtaskProgressCircle;
    private TextView tvSubtaskProgress;

    private boolean titleDirty = false;

    private final SimpleDateFormat dueDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_detail);

        dm = DataManager.getInstance(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnMore = findViewById(R.id.btn_more);
        tvCategory = findViewById(R.id.tv_category_pill);
        etTitle = findViewById(R.id.et_title);

        View rowAddSubtask = findViewById(R.id.row_add_subtask);
        View rowSchedule = findViewById(R.id.row_schedule);
        View rowNotes = findViewById(R.id.row_notes);
        View rowAttachment = findViewById(R.id.row_attachment);

        scheduleDetails = findViewById(R.id.schedule_details);
        tvScheduleDate = findViewById(R.id.tv_schedule_date);
        tvScheduleTime = findViewById(R.id.tv_schedule_time);
        tvScheduleRepeat = findViewById(R.id.tv_schedule_repeat);

        tvNotesAction = findViewById(R.id.tv_notes_action);
        notesPreviewCard = findViewById(R.id.notes_preview_card);
        tvNotesPreview = findViewById(R.id.tv_notes_preview);

        tvAttachmentAction = findViewById(R.id.tv_attachment_action);
        attachmentsContainer = findViewById(R.id.attachments_container);

        // Subtasks views
        subtasksCard = findViewById(R.id.subtasks_card);
        subtasksContainer = findViewById(R.id.subtasks_container);
        subtaskProgressCircle = findViewById(R.id.subtask_progress_circle);
        tvSubtaskProgress = findViewById(R.id.tv_subtask_progress);

        btnBack.setOnClickListener(v -> finish());
        btnMore.setOnClickListener(v -> showMoreMenu());

        int taskId = getIntent().getIntExtra(EXTRA_TASK_ID, -1);
        if (taskId <= 0) {
            finish();
            return;
        }

        task = dm.getTaskById(taskId);
        if (task == null) {
            finish();
            return;
        }

        bindTaskToUi();

        tvCategory.setOnClickListener(v -> showCategoryPicker());
        rowSchedule.setOnClickListener(v -> openScheduleEditor());
        rowNotes.setOnClickListener(v -> showNotesEditor());
        rowAttachment.setOnClickListener(v -> showAttachmentPicker());
        rowAddSubtask.setOnClickListener(v -> showAddSubtaskDialog());

        etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                titleDirty = true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (task == null)
            return;
        if (!titleDirty)
            return;

        try {
            task.task = etTitle.getText().toString().trim();
            dm.updateTask(task);
            titleDirty = false;
            setResult(RESULT_OK);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (task != null) {
            TaskList updated = dm.getTaskById(task.id);
            if (updated != null) {
                task = updated;
                bindTaskToUi();
            }
        }
    }

    private void bindTaskToUi() {
        etTitle.setText(task.task == null ? "" : task.task);

        String cat = task.category == null || task.category.trim().isEmpty() ? "No Category" : task.category;
        tvCategory.setText(cat);

        updateScheduleDisplay();
        updateNotesDisplay();
        updateAttachmentsDisplay();
        updateSubtasksDisplay();
    }

    private void updateScheduleDisplay() {
        boolean hasSchedule = false;

        if (task.dueDate > 0) {
            tvScheduleDate.setText(dueDateFormat.format(task.dueDate));
            tvScheduleDate.setVisibility(View.VISIBLE);
            hasSchedule = true;
        } else {
            tvScheduleDate.setVisibility(View.GONE);
        }

        if (task.taskTime != null && !task.taskTime.trim().isEmpty()) {
            tvScheduleTime.setText(task.taskTime);
            tvScheduleTime.setVisibility(View.VISIBLE);
            hasSchedule = true;
        } else {
            tvScheduleTime.setVisibility(View.GONE);
        }

        if (task.repeatType != null && !task.repeatType.equals("none")) {
            tvScheduleRepeat.setText(prettyRepeat(task.repeatType, task.repeatInterval));
            tvScheduleRepeat.setVisibility(View.VISIBLE);
            hasSchedule = true;
        } else {
            tvScheduleRepeat.setVisibility(View.GONE);
        }

        scheduleDetails.setVisibility(hasSchedule ? View.VISIBLE : View.GONE);
    }

    private void updateNotesDisplay() {
        if (task.time != null && !task.time.trim().isEmpty()) {
            tvNotesAction.setText("EDIT");
            notesPreviewCard.setVisibility(View.VISIBLE);
            tvNotesPreview.setText(task.time);
        } else {
            tvNotesAction.setText("ADD");
            notesPreviewCard.setVisibility(View.GONE);
        }
    }

    private void updateAttachmentsDisplay() {
        attachmentsContainer.removeAllViews();

        if (task.attachments == null || task.attachments.trim().isEmpty()) {
            tvAttachmentAction.setText("ADD");
            attachmentsContainer.setVisibility(View.GONE);
            return;
        }

        String[] files = task.attachments.split(",");
        if (files.length == 0) {
            tvAttachmentAction.setText("ADD");
            attachmentsContainer.setVisibility(View.GONE);
            return;
        }

        tvAttachmentAction.setText(files.length + " FILES");
        attachmentsContainer.setVisibility(View.VISIBLE);

        for (String filePath : files) {
            if (filePath.trim().isEmpty())
                continue;
            addAttachmentView(filePath.trim());
        }
    }

    private void addAttachmentView(String filePath) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_attachment, attachmentsContainer, false);

        TextView tvName = itemView.findViewById(R.id.tv_attachment_name);
        ImageButton btnRemove = itemView.findViewById(R.id.btn_remove);

        String fileName = getFileNameFromUri(filePath);
        tvName.setText(fileName);

        itemView.setOnClickListener(v -> openFile(filePath));
        btnRemove.setOnClickListener(v -> removeAttachment(filePath));

        attachmentsContainer.addView(itemView);
    }

    private String getFileNameFromUri(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            String result = null;
            if (uriString.startsWith("content://")) {
                try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0)
                            result = cursor.getString(idx);
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                if (result != null && result.contains("/")) {
                    result = result.substring(result.lastIndexOf("/") + 1);
                }
            }
            return result != null ? result : "Attachment";
        } catch (Exception e) {
            return "Attachment";
        }
    }

    private void openScheduleEditor() {
        showScheduleDialog();
    }

    private void showScheduleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_picker, null);
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

        // Initialize with task's current values
        final long[] tempDueDate = { task.dueDate };
        final String[] tempTime = { task.taskTime != null ? task.taskTime : "" };
        final String[] tempRepeatType = { task.repeatType != null ? task.repeatType : "none" };
        final int[] tempRepeatInterval = { task.repeatInterval > 0 ? task.repeatInterval : 1 };
        final String[] tempReminder = { task.reminderMinutes != null ? task.reminderMinutes : "" };
        final int[] tempUseAlarm = { task.useAlarm };
        final boolean[] tempScreenLock = { task.screenLock == 1 };
        final boolean[] tempWeekdays = new boolean[7]; // Sun, Mon, Tue, Wed, Thu, Fri, Sat

        // Restore previously selected weekdays
        if (task.repeatDays != null && !task.repeatDays.isEmpty()) {
            String[] dayIndices = task.repeatDays.split(",");
            for (String idx : dayIndices) {
                try {
                    int dayIdx = Integer.parseInt(idx.trim());
                    if (dayIdx >= 0 && dayIdx < 7) {
                        tempWeekdays[dayIdx] = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Open the picker on the selected date's month (if a date is already chosen)
        Calendar calendar = Calendar.getInstance();
        if (tempDueDate[0] > 0) {
            calendar.setTimeInMillis(tempDueDate[0]);
        }
        final Calendar currentCalendar = (Calendar) calendar.clone();

        // Initialize calendar adapter
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());

        calendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
        List<Calendar> days = generateDaysForMonth(currentCalendar);

        // Set initial selected date if exists
        Calendar initialSelected = Calendar.getInstance();
        if (tempDueDate[0] > 0) {
            initialSelected.setTimeInMillis(tempDueDate[0]);
        }

        final CalendarAdapter[] adapterHolder = new CalendarAdapter[1];
        adapterHolder[0] = new CalendarAdapter(this, days, day -> {
            if (day != null) {
                tempDueDate[0] = normalizeToStartOfDay(day.getTimeInMillis());
                adapterHolder[0].setSelectedDate(day);

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

        if (tempDueDate[0] > 0) {
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

        // Update displays with current values
        if (!tempTime[0].isEmpty()) {
            tvTimeValue.setText(tempTime[0]);
        }
        if (!tempRepeatType[0].equals("none")) {
            String repeatText = tempRepeatInterval[0] > 1 ? "Every " + tempRepeatInterval[0] + " " + tempRepeatType[0]
                    : "Every " + tempRepeatType[0].substring(0, 1).toUpperCase() + tempRepeatType[0].substring(1);
            tvRepeatValue.setText(repeatText);
        }
        if (!tempReminder[0].isEmpty()) {
            tvReminderValue.setText(tempReminder[0] + " min");
        }

        // Pre-select quick chip if date matches
        if (tempDueDate[0] > 0) {
            Calendar selectedCal = Calendar.getInstance();
            selectedCal.setTimeInMillis(tempDueDate[0]);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);

            Calendar threeDays = (Calendar) today.clone();
            threeDays.add(Calendar.DAY_OF_YEAR, 3);

            Calendar sunday = (Calendar) today.clone();
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

            if (isToday) {
                chipToday.setChecked(true);
                applyQuickChipStyle(chipToday, true);
            } else if (isTomorrow) {
                chipTomorrow.setChecked(true);
                applyQuickChipStyle(chipTomorrow, true);
            } else if (isThreeDays) {
                chip3Days.setChecked(true);
                applyQuickChipStyle(chip3Days, true);
            } else if (isSunday) {
                chipThisSunday.setChecked(true);
                applyQuickChipStyle(chipThisSunday, true);
            }
        } else {
            chipNoDate.setChecked(true);
            applyQuickChipStyle(chipNoDate, true);
        }

        // Quick date chip click handlers
        chipNoDate.setOnClickListener(v -> {
            tempDueDate[0] = 0;
            adapterHolder[0].setSelectedDate(null);
            chipNoDate.setChecked(true);
            chipToday.setChecked(false);
            chipTomorrow.setChecked(false);
            chip3Days.setChecked(false);
            chipThisSunday.setChecked(false);
            applyQuickChipStyle(chipNoDate, true);
            applyQuickChipStyle(chipToday, false);
            applyQuickChipStyle(chipTomorrow, false);
            applyQuickChipStyle(chip3Days, false);
            applyQuickChipStyle(chipThisSunday, false);
        });

        chipToday.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            tempDueDate[0] = today.getTimeInMillis();
            adapterHolder[0].setSelectedDate(today);
            chipNoDate.setChecked(false);
            chipToday.setChecked(true);
            chipTomorrow.setChecked(false);
            chip3Days.setChecked(false);
            chipThisSunday.setChecked(false);
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
        });

        chipTomorrow.setOnClickListener(v -> {
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            tomorrow.set(Calendar.HOUR_OF_DAY, 0);
            tomorrow.set(Calendar.MINUTE, 0);
            tomorrow.set(Calendar.SECOND, 0);
            tomorrow.set(Calendar.MILLISECOND, 0);
            tempDueDate[0] = tomorrow.getTimeInMillis();
            adapterHolder[0].setSelectedDate(tomorrow);
            chipNoDate.setChecked(false);
            chipToday.setChecked(false);
            chipTomorrow.setChecked(true);
            chip3Days.setChecked(false);
            chipThisSunday.setChecked(false);
            applyQuickChipStyle(chipNoDate, false);
            applyQuickChipStyle(chipToday, false);
            applyQuickChipStyle(chipTomorrow, true);
            applyQuickChipStyle(chip3Days, false);
            applyQuickChipStyle(chipThisSunday, false);
            if (currentCalendar.get(Calendar.MONTH) != tomorrow.get(Calendar.MONTH) ||
                    currentCalendar.get(Calendar.YEAR) != tomorrow.get(Calendar.YEAR)) {
                currentCalendar.set(Calendar.YEAR, tomorrow.get(Calendar.YEAR));
                currentCalendar.set(Calendar.MONTH, tomorrow.get(Calendar.MONTH));
                tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
                adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
            }
        });

        chip3Days.setOnClickListener(v -> {
            Calendar threeDays = Calendar.getInstance();
            threeDays.add(Calendar.DAY_OF_YEAR, 3);
            threeDays.set(Calendar.HOUR_OF_DAY, 0);
            threeDays.set(Calendar.MINUTE, 0);
            threeDays.set(Calendar.SECOND, 0);
            threeDays.set(Calendar.MILLISECOND, 0);
            tempDueDate[0] = threeDays.getTimeInMillis();
            adapterHolder[0].setSelectedDate(threeDays);
            chipNoDate.setChecked(false);
            chipToday.setChecked(false);
            chipTomorrow.setChecked(false);
            chip3Days.setChecked(true);
            chipThisSunday.setChecked(false);
            applyQuickChipStyle(chipNoDate, false);
            applyQuickChipStyle(chipToday, false);
            applyQuickChipStyle(chipTomorrow, false);
            applyQuickChipStyle(chip3Days, true);
            applyQuickChipStyle(chipThisSunday, false);
            if (currentCalendar.get(Calendar.MONTH) != threeDays.get(Calendar.MONTH) ||
                    currentCalendar.get(Calendar.YEAR) != threeDays.get(Calendar.YEAR)) {
                currentCalendar.set(Calendar.YEAR, threeDays.get(Calendar.YEAR));
                currentCalendar.set(Calendar.MONTH, threeDays.get(Calendar.MONTH));
                tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
                adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
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
            tempDueDate[0] = sunday.getTimeInMillis();
            adapterHolder[0].setSelectedDate(sunday);
            chipNoDate.setChecked(false);
            chipToday.setChecked(false);
            chipTomorrow.setChecked(false);
            chip3Days.setChecked(false);
            chipThisSunday.setChecked(true);
            applyQuickChipStyle(chipNoDate, false);
            applyQuickChipStyle(chipToday, false);
            applyQuickChipStyle(chipTomorrow, false);
            applyQuickChipStyle(chip3Days, false);
            applyQuickChipStyle(chipThisSunday, true);
            if (currentCalendar.get(Calendar.MONTH) != sunday.get(Calendar.MONTH) ||
                    currentCalendar.get(Calendar.YEAR) != sunday.get(Calendar.YEAR)) {
                currentCalendar.set(Calendar.YEAR, sunday.get(Calendar.YEAR));
                currentCalendar.set(Calendar.MONTH, sunday.get(Calendar.MONTH));
                tvMonthYear.setText(sdf.format(currentCalendar.getTime()).toUpperCase());
                adapterHolder[0].updateDays(generateDaysForMonth(currentCalendar));
            }
        });

        // Helper to update reminder option enabled state based on time
        final String[] reminderOptions = { "5 minutes before", "10 minutes before", "15 minutes before",
                "30 minutes before", "1 hour before" };
        final String[] reminderValues = { "5", "10", "15", "30", "60" };

        Runnable updateReminderOptionState = () -> {
            boolean timeSet = !tempTime[0].isEmpty();
            float alpha = timeSet ? 1.0f : 0.4f;
            reminderOption.setAlpha(alpha);
            reminderOption.setClickable(timeSet);
            reminderOption.setEnabled(timeSet);

            if (icReminder != null) {
                icReminder.setColorFilter(timeSet ? ContextCompat.getColor(this, R.color.primary_blue)
                        : ContextCompat.getColor(this, R.color.text_hint));
            }
            if (tvReminderLabel != null) {
                tvReminderLabel.setTextColor(timeSet ? ContextCompat.getColor(this, R.color.text_primary)
                        : ContextCompat.getColor(this, R.color.text_hint));
            }

            if (!timeSet) {
                tvReminderValue.setText("Set time first");
            } else if (!tempReminder[0].isEmpty()) {
                // Show reminder details
                String displayText = "";
                for (int i = 0; i < reminderValues.length; i++) {
                    if (reminderValues[i].equals(tempReminder[0])) {
                        displayText = reminderOptions[i].replace(" minutes", " min").replace(" hour", " hr")
                                .replace(" before", "");
                        break;
                    }
                }
                tvReminderValue.setText(displayText.isEmpty() ? "On" : displayText);
            } else {
                tvReminderValue.setText("Off");
            }
        };

        // Initialize reminder option state
        updateReminderOptionState.run();

        // Time option click
        timeOption.setOnClickListener(v -> {
            showTimePickerDialog(tvTimeValue, tempTime, tvReminderValue, tempReminder, reminderOption,
                    updateReminderOptionState);
        });

        // Repeat option click
        repeatOption.setOnClickListener(v -> {
            showRepeatDialog(tvRepeatValue, tempRepeatType, tempRepeatInterval, tempWeekdays);
        });

        // Reminder option click
        reminderOption.setOnClickListener(v -> {
            // Only allow reminder if time is set
            if (tempTime[0].isEmpty()) {
                Toast.makeText(this, "Please set a time first", Toast.LENGTH_SHORT).show();
                return;
            }
            showReminderPickerDialog(tvReminderValue, tempReminder, tempUseAlarm, tempScreenLock,
                    updateReminderOptionState);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            // Save the schedule to the task
            task.dueDate = normalizeToStartOfDay(tempDueDate[0]);
            task.taskTime = tempTime[0];
            task.repeatType = tempRepeatType[0];
            task.repeatInterval = tempRepeatInterval[0];
            task.reminderMinutes = tempReminder[0];
            task.useAlarm = tempUseAlarm[0];
            task.screenLock = tempScreenLock[0] ? 1 : 0;

            // Save selected weekdays
            StringBuilder daysBuilder = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (tempWeekdays[i]) {
                    if (daysBuilder.length() > 0)
                        daysBuilder.append(",");
                    daysBuilder.append(i);
                }
            }
            task.repeatDays = daysBuilder.toString();

            dm.updateTask(task);
            // Cancel old reminders and schedule new ones
            NotificationHelper notificationHelper = new NotificationHelper(this);
            notificationHelper.cancelReminders(task.id);
            if (task.dueDate > 0 && task.taskTime != null && !task.taskTime.isEmpty() && task.reminderMinutes != null
                    && !task.reminderMinutes.isEmpty()) {
                notificationHelper.scheduleReminders(
                        task.id,
                        task.task,
                        task.dueDate,
                        task.taskTime,
                        task.reminderMinutes,
                        task.useAlarm == 1,
                        task.screenLock == 1);
            }
            updateScheduleDisplay();
            setResult(RESULT_OK);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showTimePickerDialog(TextView tvTimeValue, String[] tempTime, TextView tvReminderValue,
            String[] tempReminder, View reminderOption, Runnable onTimeChanged) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_picker, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }

        TextView tvHour = dialogView.findViewById(R.id.tv_hour);
        TextView tvMinute = dialogView.findViewById(R.id.tv_minute);
        TextView tvAm = dialogView.findViewById(R.id.tv_am);
        TextView tvPm = dialogView.findViewById(R.id.tv_pm);
        TimePicker timePicker = dialogView.findViewById(R.id.time_picker);

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
        final int[] tempMinuteVal = { 0 };
        final boolean[] tempIsAm = { true };
        final boolean[] noTimeSelected = { tempTime[0].isEmpty() };

        if (!tempTime[0].isEmpty()) {
            try {
                String[] parts = tempTime[0].replace(" AM", "").replace(" PM", "").split(":");
                tempHour[0] = Integer.parseInt(parts[0]);
                tempMinuteVal[0] = Integer.parseInt(parts[1].split(" ")[0]);
                tempIsAm[0] = tempTime[0].contains("AM");
                noTimeSelected[0] = false;
            } catch (Exception e) {
            }
        }

        Runnable updateDisplay = () -> {
            if (noTimeSelected[0]) {
                tvHour.setText("--");
                tvMinute.setText("--");
                tvHour.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvMinute.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            } else {
                tvHour.setText(String.valueOf(tempHour[0]));
                tvMinute.setText(String.format(Locale.getDefault(), "%02d", tempMinuteVal[0]));
                tvHour.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvMinute.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }

            if (tempIsAm[0]) {
                tvAm.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvAm.setTypeface(null, Typeface.BOLD);
                tvPm.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvPm.setTypeface(null, Typeface.NORMAL);
            } else {
                tvPm.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvPm.setTypeface(null, Typeface.BOLD);
                tvAm.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvAm.setTypeface(null, Typeface.NORMAL);
            }

            applyQuickChipStyle(chipNoTime, noTimeSelected[0]);
            applyQuickChipStyle(chip7am,
                    !noTimeSelected[0] && tempHour[0] == 7 && tempMinuteVal[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip9am,
                    !noTimeSelected[0] && tempHour[0] == 9 && tempMinuteVal[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip10am,
                    !noTimeSelected[0] && tempHour[0] == 10 && tempMinuteVal[0] == 0 && tempIsAm[0]);
            applyQuickChipStyle(chip12pm,
                    !noTimeSelected[0] && tempHour[0] == 12 && tempMinuteVal[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip2pm,
                    !noTimeSelected[0] && tempHour[0] == 2 && tempMinuteVal[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip4pm,
                    !noTimeSelected[0] && tempHour[0] == 4 && tempMinuteVal[0] == 0 && !tempIsAm[0]);
            applyQuickChipStyle(chip6pm,
                    !noTimeSelected[0] && tempHour[0] == 6 && tempMinuteVal[0] == 0 && !tempIsAm[0]);
        };

        updateDisplay.run();

        int initial24Hour = tempHour[0];
        if (!tempIsAm[0] && tempHour[0] != 12)
            initial24Hour += 12;
        else if (tempIsAm[0] && tempHour[0] == 12)
            initial24Hour = 0;
        timePicker.setHour(initial24Hour);
        timePicker.setMinute(tempMinuteVal[0]);

        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            noTimeSelected[0] = false;
            tempMinuteVal[0] = minute;
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
            tempMinuteVal[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(7);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip9am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 9;
            tempMinuteVal[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(9);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip10am.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 10;
            tempMinuteVal[0] = 0;
            tempIsAm[0] = true;
            timePicker.setHour(10);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip12pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 12;
            tempMinuteVal[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(12);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip2pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 2;
            tempMinuteVal[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(14);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip4pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 4;
            tempMinuteVal[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(16);
            timePicker.setMinute(0);
            updateDisplay.run();
        });
        chip6pm.setOnClickListener(v -> {
            noTimeSelected[0] = false;
            tempHour[0] = 6;
            tempMinuteVal[0] = 0;
            tempIsAm[0] = false;
            timePicker.setHour(18);
            timePicker.setMinute(0);
            updateDisplay.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            if (noTimeSelected[0]) {
                tempTime[0] = "";
                tvTimeValue.setText("No");
                tempReminder[0] = "";
                tvReminderValue.setText("Set time first");
            } else {
                String amPm = tempIsAm[0] ? "AM" : "PM";
                tempTime[0] = String.format(Locale.getDefault(), "%d:%02d %s", tempHour[0], tempMinuteVal[0], amPm);
                tvTimeValue.setText(tempTime[0]);
            }
            // Update reminder option state after time change
            if (onTimeChanged != null) {
                onTimeChanged.run();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void hideTimePickerHeader(TimePicker timePicker) {
        try {
            int headerId = timePicker.getResources().getIdentifier("time_header", "id", "android");
            if (headerId != 0) {
                View header = timePicker.findViewById(headerId);
                if (header != null)
                    header.setVisibility(View.GONE);
            }
            int toggleId = timePicker.getResources().getIdentifier("toggle_mode", "id", "android");
            if (toggleId != 0) {
                View toggle = timePicker.findViewById(toggleId);
                if (toggle != null)
                    toggle.setVisibility(View.GONE);
            }
        } catch (Exception e) {
        }
    }

    private void showRepeatDialog(TextView tvRepeatValue, String[] tempRepeatType, int[] tempRepeatInterval,
            boolean[] tempWeekdays) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_repeat_new, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View optionNoRepeat = dialogView.findViewById(R.id.option_no_repeat);
        View optionWeekly = dialogView.findViewById(R.id.option_weekly);
        View optionMonthly = dialogView.findViewById(R.id.option_monthly);
        View optionYearly = dialogView.findViewById(R.id.option_yearly);

        ImageView checkNoRepeat = dialogView.findViewById(R.id.check_no_repeat);
        ImageView checkWeekly = dialogView.findViewById(R.id.check_weekly);
        ImageView checkMonthly = dialogView.findViewById(R.id.check_monthly);
        ImageView checkYearly = dialogView.findViewById(R.id.check_yearly);

        TextView tvSun = dialogView.findViewById(R.id.chip_sun);
        TextView tvMon = dialogView.findViewById(R.id.chip_mon);
        TextView tvTue = dialogView.findViewById(R.id.chip_tue);
        TextView tvWed = dialogView.findViewById(R.id.chip_wed);
        TextView tvThu = dialogView.findViewById(R.id.chip_thu);
        TextView tvFri = dialogView.findViewById(R.id.chip_fri);
        TextView tvSat = dialogView.findViewById(R.id.chip_sat);

        Chip chipWorkWeek = dialogView.findViewById(R.id.chip_work_week);
        Chip chipWeekend = dialogView.findViewById(R.id.chip_weekend);
        Chip chipEveryday = dialogView.findViewById(R.id.chip_everyday);

        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        final String[] localRepeatType = { tempRepeatType[0] };
        final int[] localRepeatInterval = { tempRepeatInterval[0] };
        final boolean[] localWeekdays = tempWeekdays.clone();

        TextView[] dayViews = { tvSun, tvMon, tvTue, tvWed, tvThu, tvFri, tvSat };

        Runnable updateWeekdayButtons = () -> {
            for (int i = 0; i < 7; i++) {
                if (localWeekdays[i]) {
                    dayViews[i].setSelected(true);
                    dayViews[i].setTextColor(ContextCompat.getColor(this, R.color.white));
                    dayViews[i].setBackgroundResource(R.drawable.day_circle_selected_bg);
                } else {
                    dayViews[i].setSelected(false);
                    dayViews[i].setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    dayViews[i].setBackgroundResource(R.drawable.day_circle_bg);
                }
            }

            boolean isWorkWeek = localWeekdays[1] && localWeekdays[2] && localWeekdays[3] &&
                    localWeekdays[4] && localWeekdays[5] && !localWeekdays[0] && !localWeekdays[6];
            boolean isWeekend = localWeekdays[0] && localWeekdays[6] &&
                    !localWeekdays[1] && !localWeekdays[2] && !localWeekdays[3] &&
                    !localWeekdays[4] && !localWeekdays[5];
            boolean isEveryday = localWeekdays[0] && localWeekdays[1] && localWeekdays[2] &&
                    localWeekdays[3] && localWeekdays[4] && localWeekdays[5] && localWeekdays[6];

            applyQuickChipStyle(chipWorkWeek, isWorkWeek);
            applyQuickChipStyle(chipWeekend, isWeekend);
            applyQuickChipStyle(chipEveryday, isEveryday);
        };

        Runnable updateChecks = () -> {
            checkNoRepeat.setVisibility(localRepeatType[0].equals("none") ? View.VISIBLE : View.GONE);
            checkWeekly.setVisibility(localRepeatType[0].equals("weeks") ? View.VISIBLE : View.GONE);
            checkMonthly.setVisibility(localRepeatType[0].equals("months") ? View.VISIBLE : View.GONE);
            checkYearly.setVisibility(localRepeatType[0].equals("years") ? View.VISIBLE : View.GONE);
            updateWeekdayButtons.run();
        };

        updateChecks.run();

        optionNoRepeat.setOnClickListener(v -> {
            localRepeatType[0] = "none";
            Arrays.fill(localWeekdays, false);
            updateChecks.run();
        });

        optionWeekly.setOnClickListener(v -> {
            localRepeatType[0] = "weeks";
            localRepeatInterval[0] = 1;
            Arrays.fill(localWeekdays, false);
            updateChecks.run();
        });

        optionMonthly.setOnClickListener(v -> {
            localRepeatType[0] = "months";
            localRepeatInterval[0] = 1;
            Arrays.fill(localWeekdays, false);
            updateChecks.run();
        });

        optionYearly.setOnClickListener(v -> {
            localRepeatType[0] = "years";
            localRepeatInterval[0] = 1;
            Arrays.fill(localWeekdays, false);
            updateChecks.run();
        });

        for (int i = 0; i < 7; i++) {
            final int dayIndex = i;
            dayViews[i].setOnClickListener(v -> {
                localWeekdays[dayIndex] = !localWeekdays[dayIndex];
                // Check if all days are selected - then use "days" (Daily)
                boolean allSelected = true;
                for (int j = 0; j < 7; j++) {
                    if (!localWeekdays[j]) {
                        allSelected = false;
                        break;
                    }
                }
                localRepeatType[0] = allSelected ? "days" : "custom_days";
                updateChecks.run();
            });
        }

        chipWorkWeek.setOnClickListener(v -> {
            Arrays.fill(localWeekdays, false);
            localWeekdays[1] = true;
            localWeekdays[2] = true;
            localWeekdays[3] = true;
            localWeekdays[4] = true;
            localWeekdays[5] = true;
            localRepeatType[0] = "custom_days";
            updateChecks.run();
        });

        chipWeekend.setOnClickListener(v -> {
            Arrays.fill(localWeekdays, false);
            localWeekdays[0] = true;
            localWeekdays[6] = true;
            localRepeatType[0] = "custom_days";
            updateChecks.run();
        });

        chipEveryday.setOnClickListener(v -> {
            Arrays.fill(localWeekdays, true);
            localRepeatType[0] = "days";
            updateChecks.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            // Check if all 7 days are selected - convert to "days" (Daily)
            boolean allDays = true;
            for (int i = 0; i < 7; i++) {
                if (!localWeekdays[i]) {
                    allDays = false;
                    break;
                }
            }
            if (allDays && localRepeatType[0].equals("custom_days")) {
                localRepeatType[0] = "days";
            }

            tempRepeatType[0] = localRepeatType[0];
            tempRepeatInterval[0] = localRepeatInterval[0];
            System.arraycopy(localWeekdays, 0, tempWeekdays, 0, 7);

            if (localRepeatType[0].equals("none")) {
                tvRepeatValue.setText("No");
            } else if (localRepeatType[0].equals("custom_days")) {
                // Show "Custom" for any custom day selection
                tvRepeatValue.setText("Custom");
            } else if (localRepeatType[0].equals("days")) {
                tvRepeatValue.setText("Daily");
            } else {
                String repeatText = localRepeatInterval[0] > 1
                        ? "Every " + localRepeatInterval[0] + " " + localRepeatType[0]
                        : "Every " + localRepeatType[0].substring(0, localRepeatType[0].length() - 1);
                tvRepeatValue.setText(repeatText);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showReminderPickerDialog(TextView tvReminderValue, String[] tempReminder, int[] tempUseAlarm,
            boolean[] tempScreenLock, Runnable onReminderChanged) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reminder_new, null);
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

        final boolean[] localReminderOn = { !tempReminder[0].isEmpty() || tempUseAlarm[0] > 0 };
        final String[] localReminderMinutes = { tempReminder[0].isEmpty() ? "5" : tempReminder[0] };
        final String[] localReminderType = { tempUseAlarm[0] > 0 ? "alarm" : "notification" };
        final boolean[] localScreenLock = { tempScreenLock[0] };

        final String[] reminderOptions = { "5 minutes before", "10 minutes before", "15 minutes before",
                "30 minutes before", "1 hour before" };
        final String[] reminderValues = { "5", "10", "15", "30", "60" };

        Runnable updateUI = () -> {
            switchReminder.setChecked(localReminderOn[0]);
            tvReminderTitle.setText(localReminderOn[0] ? "Reminder is on" : "Reminder is off");

            String reminderDisplay = "5 minutes before";
            for (int i = 0; i < reminderValues.length; i++) {
                if (reminderValues[i].equals(localReminderMinutes[0])) {
                    reminderDisplay = reminderOptions[i];
                    break;
                }
            }
            tvReminderAtValue.setText(reminderDisplay);
            tvReminderTypeValue.setText(localReminderType[0].equals("alarm") ? "Alarm" : "Notification");
            tvScreenlockValue.setText(localScreenLock[0] ? "On" : "Off");

            float alpha = localReminderOn[0] ? 1.0f : 0.5f;
            reminderAtOption.setAlpha(alpha);
            reminderTypeOption.setAlpha(alpha);
            screenlockOption.setAlpha(alpha);
            reminderAtOption.setClickable(localReminderOn[0]);
            reminderTypeOption.setClickable(localReminderOn[0]);
            screenlockOption.setClickable(localReminderOn[0]);
        };

        updateUI.run();

        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            localReminderOn[0] = isChecked;
            updateUI.run();
        });

        reminderAtOption.setOnClickListener(v -> {
            if (!localReminderOn[0])
                return;

            int currentIndex = 0;
            for (int i = 0; i < reminderValues.length; i++) {
                if (reminderValues[i].equals(localReminderMinutes[0])) {
                    currentIndex = i;
                    break;
                }
            }
            showDropdownDialog(reminderOptions, currentIndex, index -> {
                localReminderMinutes[0] = reminderValues[index];
                updateUI.run();
            });
        });

        reminderTypeOption.setOnClickListener(v -> {
            if (!localReminderOn[0])
                return;

            String[] typeOptions = { "Notification", "Alarm" };
            int currentIndex = localReminderType[0].equals("alarm") ? 1 : 0;
            showDropdownDialog(typeOptions, currentIndex, index -> {
                localReminderType[0] = typeOptions[index].toLowerCase();
                updateUI.run();
            });
        });

        screenlockOption.setOnClickListener(v -> {
            if (!localReminderOn[0])
                return;

            String[] screenlockOptions = { "Off", "On" };
            int currentIndex = localScreenLock[0] ? 1 : 0;
            showDropdownDialog(screenlockOptions, currentIndex, index -> {
                localScreenLock[0] = index == 1;
                updateUI.run();
            });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDone.setOnClickListener(v -> {
            if (localReminderOn[0]) {
                tempReminder[0] = localReminderMinutes[0];
                tempUseAlarm[0] = localReminderType[0].equals("alarm") ? 1 : 0;
                tempScreenLock[0] = localScreenLock[0];

                String reminderDisplay = "5 min before";
                for (int i = 0; i < reminderValues.length; i++) {
                    if (reminderValues[i].equals(localReminderMinutes[0])) {
                        reminderDisplay = reminderOptions[i].replace(" minutes", " min").replace(" hour", " hr");
                        break;
                    }
                }
                tvReminderValue.setText(reminderDisplay);
            } else {
                tempReminder[0] = "";
                tempUseAlarm[0] = 0;
                tvReminderValue.setText("Off");
            }
            // Update reminder option state
            if (onReminderChanged != null) {
                onReminderChanged.run();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void applyQuickChipStyle(Chip chip, boolean selected) {
        if (chip == null)
            return;
        if (selected) {
            chip.setChipBackgroundColorResource(R.color.primary_blue);
            chip.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            chip.setChipBackgroundColorResource(R.color.chip_inactive_bg);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
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

    private void showCategoryPicker() {
        try {
            List<Category> cats = dm.getAllCategories();
            ArrayList<String> items = new ArrayList<>();
            if (cats != null) {
                for (Category c : cats) {
                    if (c != null && c.getName() != null) {
                        items.add(c.getName());
                    }
                }
            }
            items.add("+ Create New");

            String[] arr = items.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Select category")
                    .setItems(arr, (d, which) -> {
                        String sel = arr[which];
                        if (sel.startsWith("+")) {
                            showCreateCategoryDialog();
                            return;
                        }
                        task.category = sel;
                        dm.updateTask(task);
                        tvCategory.setText(sel);
                        setResult(RESULT_OK);
                    })
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showCreateCategoryDialog() {
        try {
            final EditText input = new EditText(this);
            input.setHint("Category name");
            new AlertDialog.Builder(this)
                    .setTitle("Create New")
                    .setView(input)
                    .setPositiveButton("Create", (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty())
                            return;
                        try {
                            dm.insertCategory(new Category(name, "#5B9BD5", 1));
                        } catch (Exception ignored) {
                        }
                        task.category = name;
                        dm.updateTask(task);
                        tvCategory.setText(name);
                        setResult(RESULT_OK);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNotesEditor() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notes_editor, null);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            EditText etNotes = dialogView.findViewById(R.id.et_notes);
            TextView tvCharCount = dialogView.findViewById(R.id.tv_char_count);
            ImageButton btnClear = dialogView.findViewById(R.id.btn_clear_notes);
            View btnCancel = dialogView.findViewById(R.id.btn_cancel);
            View btnSave = dialogView.findViewById(R.id.btn_save);

            etNotes.setText(task.time == null ? "" : task.time);
            updateCharCount(tvCharCount, etNotes.getText().length());

            etNotes.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateCharCount(tvCharCount, s.length());
                }
            });

            btnClear.setOnClickListener(v -> etNotes.setText(""));
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnSave.setOnClickListener(v -> {
                task.time = etNotes.getText().toString();
                dm.updateTask(task);
                updateNotesDisplay();
                setResult(RESULT_OK);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCharCount(TextView tv, int count) {
        tv.setText(count + " character" + (count == 1 ? "" : "s"));
    }

    private void showAttachmentPicker() {
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(this);
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_attachment_picker, null);
            dialog.setContentView(view);

            view.findViewById(R.id.option_image).setOnClickListener(v -> {
                pickFile("image/*");
                dialog.dismiss();
            });

            view.findViewById(R.id.option_video).setOnClickListener(v -> {
                pickFile("video/*");
                dialog.dismiss();
            });

            view.findViewById(R.id.option_audio).setOnClickListener(v -> {
                pickFile("audio/*");
                dialog.dismiss();
            });

            view.findViewById(R.id.option_document).setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain"
                });
                filePickerLauncher.launch(intent);
                dialog.dismiss();
            });

            view.findViewById(R.id.option_file).setOnClickListener(v -> {
                pickFile("*/*");
                dialog.dismiss();
            });

            view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pickFile(String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mimeType);
            filePickerLauncher.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to open file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedFile(Uri uri) {
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, takeFlags);

            String uriString = uri.toString();

            String current = task.attachments == null ? "" : task.attachments;
            if (current.isEmpty()) {
                task.attachments = uriString;
            } else {
                task.attachments = current + "," + uriString;
            }

            dm.updateTask(task);
            updateAttachmentsDisplay();
            setResult(RESULT_OK);

            Toast.makeText(this, "File attached", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to attach file", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeAttachment(String filePath) {
        try {
            if (task.attachments == null)
                return;

            String[] files = task.attachments.split(",");
            StringBuilder newAttachments = new StringBuilder();

            for (String file : files) {
                if (!file.trim().equals(filePath)) {
                    if (newAttachments.length() > 0)
                        newAttachments.append(",");
                    newAttachments.append(file.trim());
                }
            }

            task.attachments = newAttachments.toString();
            dm.updateTask(task);
            updateAttachmentsDisplay();
            setResult(RESULT_OK);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openFile(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getContentResolver().getType(uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to open file", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddSubtaskDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_subtask, null);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                // Resize dialog when keyboard appears
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }

            EditText etSubtaskTitle = dialogView.findViewById(R.id.et_subtask_title);
            View btnAddSubtask = dialogView.findViewById(R.id.btn_add_subtask);
            View btnAddNew = dialogView.findViewById(R.id.btn_add_new);
            LinearLayout subtasksListContainer = dialogView.findViewById(R.id.subtasks_list_container);
            ScrollView subtasksScroll = dialogView.findViewById(R.id.subtasks_scroll);
            View progressSection = dialogView.findViewById(R.id.progress_section);
            ProgressBar progressCircle = dialogView.findViewById(R.id.progress_circle);
            TextView tvProgressPercent = dialogView.findViewById(R.id.tv_progress_percent);
            TextView tvProgressStatus = dialogView.findViewById(R.id.tv_progress_status);
            View btnCancel = dialogView.findViewById(R.id.btn_cancel);
            View btnDone = dialogView.findViewById(R.id.btn_done);

            // Set max height for ScrollView (180dp - smaller to avoid keyboard overlap)
            int maxHeightPx = (int) (180 * getResources().getDisplayMetrics().density);

            // Load existing subtasks
            List<SubTask> subtasks = dm.getSubTasksForTask(task.id);

            Runnable refreshSubtasksList = new Runnable() {
                @Override
                public void run() {
                    subtasksListContainer.removeAllViews();
                    List<SubTask> currentSubtasks = dm.getSubTasksForTask(task.id);

                    if (currentSubtasks.isEmpty()) {
                        progressSection.setVisibility(View.GONE);
                        subtasksScroll.setVisibility(View.GONE);
                    } else {
                        progressSection.setVisibility(View.VISIBLE);
                        subtasksScroll.setVisibility(View.VISIBLE);

                        int completed = 0;
                        for (SubTask st : currentSubtasks) {
                            if (st.isCompleted == 1)
                                completed++;
                        }

                        int percent = (int) ((completed * 100.0f) / currentSubtasks.size());
                        progressCircle.setProgress(percent);
                        tvProgressPercent.setText(percent + "%");
                        tvProgressStatus.setText(completed + " of " + currentSubtasks.size() + " completed");
                    }

                    for (SubTask st : currentSubtasks) {
                        View itemView = LayoutInflater.from(TaskDetailActivity.this)
                                .inflate(R.layout.item_subtask, subtasksListContainer, false);

                        View circle = itemView.findViewById(R.id.subtask_circle);
                        ImageView checkIcon = itemView.findViewById(R.id.subtask_check_icon);
                        TextView tvTitle = itemView.findViewById(R.id.tv_subtask_title);
                        View btnDelete = itemView.findViewById(R.id.btn_delete_subtask);

                        tvTitle.setText(st.title);

                        // Set completion state
                        if (st.isCompleted == 1) {
                            circle.setBackgroundResource(R.drawable.subtask_circle_checked);
                            checkIcon.setVisibility(View.VISIBLE);
                            tvTitle.setPaintFlags(
                                    tvTitle.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                            tvTitle.setTextColor(getResources().getColor(R.color.text_hint));
                        } else {
                            circle.setBackgroundResource(R.drawable.subtask_circle_unchecked);
                            checkIcon.setVisibility(View.GONE);
                            tvTitle.setPaintFlags(
                                    tvTitle.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                            tvTitle.setTextColor(getResources().getColor(R.color.text_primary));
                        }

                        // Toggle completion on circle click
                        View checkContainer = itemView.findViewById(R.id.subtask_check_container);
                        checkContainer.setOnClickListener(v -> {
                            st.isCompleted = st.isCompleted == 1 ? 0 : 1;
                            dm.updateSubTask(st);
                            this.run();
                        });

                        // Delete subtask
                        btnDelete.setOnClickListener(v -> {
                            dm.deleteSubTask(st.id);
                            this.run();
                        });

                        subtasksListContainer.addView(itemView);
                    }

                    // Limit ScrollView height after adding items
                    subtasksScroll.post(() -> {
                        if (subtasksListContainer.getHeight() > maxHeightPx) {
                            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) subtasksScroll
                                    .getLayoutParams();
                            params.height = maxHeightPx;
                            subtasksScroll.setLayoutParams(params);
                        } else {
                            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) subtasksScroll
                                    .getLayoutParams();
                            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                            subtasksScroll.setLayoutParams(params);
                        }
                    });
                }
            };

            refreshSubtasksList.run();

            // Add new subtask from + button in input field
            View.OnClickListener addSubtaskAction = v -> {
                String title = etSubtaskTitle.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "Enter a subtask title", Toast.LENGTH_SHORT).show();
                    return;
                }
                dm.insertSubTask(new SubTask(task.id, title));
                etSubtaskTitle.setText("");
                refreshSubtasksList.run();

                // Scroll to bottom to show new item
                subtasksScroll.post(() -> subtasksScroll.fullScroll(View.FOCUS_DOWN));
            };

            btnAddSubtask.setOnClickListener(addSubtaskAction);

            // Add button near Done
            if (btnAddNew != null) {
                btnAddNew.setOnClickListener(addSubtaskAction);
            }

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnDone.setOnClickListener(v -> {
                updateSubtasksDisplay();
                setResult(RESULT_OK);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSubtasksDisplay() {
        try {
            List<SubTask> subtasks = dm.getSubTasksForTask(task.id);

            if (subtasks == null || subtasks.isEmpty()) {
                subtasksCard.setVisibility(View.GONE);
                return;
            }

            subtasksCard.setVisibility(View.VISIBLE);
            subtasksContainer.removeAllViews();

            int completed = 0;
            for (SubTask st : subtasks) {
                if (st.isCompleted == 1)
                    completed++;
            }

            // Update progress
            int percent = (int) ((completed * 100.0f) / subtasks.size());
            subtaskProgressCircle.setProgress(percent);
            tvSubtaskProgress.setText(completed + "/" + subtasks.size());

            // Add subtask items
            for (SubTask st : subtasks) {
                View itemView = LayoutInflater.from(this).inflate(R.layout.item_subtask, subtasksContainer, false);

                View circle = itemView.findViewById(R.id.subtask_circle);
                ImageView checkIcon = itemView.findViewById(R.id.subtask_check_icon);
                TextView tvTitle = itemView.findViewById(R.id.tv_subtask_title);
                View btnDelete = itemView.findViewById(R.id.btn_delete_subtask);

                btnDelete.setVisibility(View.GONE); // Hide delete in main view

                tvTitle.setText(st.title);

                // Set completion state
                if (st.isCompleted == 1) {
                    circle.setBackgroundResource(R.drawable.subtask_circle_checked);
                    checkIcon.setVisibility(View.VISIBLE);
                    tvTitle.setPaintFlags(tvTitle.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    tvTitle.setTextColor(getResources().getColor(R.color.text_hint));
                } else {
                    circle.setBackgroundResource(R.drawable.subtask_circle_unchecked);
                    checkIcon.setVisibility(View.GONE);
                    tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    tvTitle.setTextColor(getResources().getColor(R.color.text_primary));
                }

                // Toggle completion on click
                View checkContainer = itemView.findViewById(R.id.subtask_check_container);
                checkContainer.setOnClickListener(v -> {
                    st.isCompleted = st.isCompleted == 1 ? 0 : 1;
                    dm.updateSubTask(st);
                    updateSubtasksDisplay();
                    setResult(RESULT_OK);
                });

                subtasksContainer.addView(itemView);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showMoreMenu() {
        View btnMore = findViewById(R.id.btn_more);
        View popupView = LayoutInflater.from(this).inflate(R.layout.dialog_task_options, null);

        PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        popupWindow.setElevation(8f);
        popupWindow.setOutsideTouchable(true);

        // Star option
        View optionStar = popupView.findViewById(R.id.option_star);
        ImageView ivStarIcon = popupView.findViewById(R.id.iv_star_icon);
        TextView tvStarText = popupView.findViewById(R.id.tv_star_text);

        // Update star UI based on current state
        if (task.isStarred == 1) {
            ivStarIcon.setImageResource(R.drawable.ic_star_filled);
            ivStarIcon.setColorFilter(ContextCompat.getColor(this, R.color.star_yellow));
            tvStarText.setText("Unstar");
        } else {
            ivStarIcon.setImageResource(R.drawable.ic_star_outline);
            ivStarIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
            tvStarText.setText("Star");
        }

        optionStar.setOnClickListener(v -> {
            task.isStarred = task.isStarred == 1 ? 0 : 1;
            dm.updateTask(task);
            setResult(RESULT_OK);
            popupWindow.dismiss();
            Toast.makeText(this, task.isStarred == 1 ? "Task starred" : "Star removed", Toast.LENGTH_SHORT).show();
        });

        // Set Flag option
        View optionFlag = popupView.findViewById(R.id.option_flag);
        optionFlag.setOnClickListener(v -> {
            popupWindow.dismiss();
            showMarkSymbolSheet();
        });

        // Delete option
        View optionDelete = popupView.findViewById(R.id.option_delete);
        optionDelete.setOnClickListener(v -> {
            popupWindow.dismiss();
            new AlertDialog.Builder(this)
                    .setTitle("Delete Task")
                    .setMessage("Are you sure you want to delete this task?")
                    .setPositiveButton("Delete", (d, w) -> {
                        dm.deleteTask(task);
                        setResult(RESULT_OK);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Show popup anchored to the more button, aligned to the right
        popupWindow.showAsDropDown(btnMore, 0, 0, Gravity.END);
    }

    private void showMarkSymbolSheet() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View v = LayoutInflater.from(this).inflate(R.layout.dialog_mark_symbol, null);
            builder.setView(v);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            // Clear button
            View btnClear = v.findViewById(R.id.btn_clear);
            if (btnClear != null) {
                btnClear.setOnClickListener(x -> {
                    task.markerType = "";
                    task.markerValue = "";
                    task.markerColor = 0;
                    dm.updateTask(task);
                    dialog.dismiss();
                    setResult(RESULT_OK);
                    Toast.makeText(this, "Flag cleared", Toast.LENGTH_SHORT).show();
                });
            }

            // Cancel button
            View btnCancel = v.findViewById(R.id.btn_cancel);
            if (btnCancel != null) {
                btnCancel.setOnClickListener(x -> dialog.dismiss());
            }

            // Priority buttons
            setMarkerClick(v.findViewById(R.id.priority_urgent), dialog, "priority", "Urgent", 0xFFE53935);
            setMarkerClick(v.findViewById(R.id.priority_high), dialog, "priority", "High", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.priority_medium), dialog, "priority", "Medium", 0xFF2196F3);
            setMarkerClick(v.findViewById(R.id.priority_low), dialog, "priority", "Low", 0xFF4CAF50);

            // Flags (6 colors)
            setMarkerClick(v.findViewById(R.id.flag_red), dialog, "flag", "", 0xFFF06292);
            setMarkerClick(v.findViewById(R.id.flag_orange), dialog, "flag", "", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.flag_yellow), dialog, "flag", "", 0xFFFBC02D);
            setMarkerClick(v.findViewById(R.id.flag_purple), dialog, "flag", "", 0xFFAB47BC);
            setMarkerClick(v.findViewById(R.id.flag_blue), dialog, "flag", "", 0xFF42A5F5);
            setMarkerClick(v.findViewById(R.id.flag_green), dialog, "flag", "", 0xFF66BB6A);

            // Numbers
            setMarkerClick(v.findViewById(R.id.num_1), dialog, "number", "1", 0xFFF06292);
            setMarkerClick(v.findViewById(R.id.num_2), dialog, "number", "2", 0xFFFBC02D);
            setMarkerClick(v.findViewById(R.id.num_3), dialog, "number", "3", 0xFFAB47BC);
            setMarkerClick(v.findViewById(R.id.num_4), dialog, "number", "4", 0xFF42A5F5);
            setMarkerClick(v.findViewById(R.id.num_5), dialog, "number", "5", 0xFF66BB6A);

            // Progress (percentages)
            setMarkerClick(v.findViewById(R.id.prog_1), dialog, "progress", "0%", 0xFFBDBDBD);
            setMarkerClick(v.findViewById(R.id.prog_2), dialog, "progress", "25%", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.prog_3), dialog, "progress", "50%", 0xFF2196F3);
            setMarkerClick(v.findViewById(R.id.prog_4), dialog, "progress", "75%", 0xFF9C27B0);
            setMarkerClick(v.findViewById(R.id.prog_5), dialog, "progress", "100%", 0xFF4CAF50);

            // Mood
            setMarkerClick(v.findViewById(R.id.mood_1), dialog, "mood", "😀", 0);
            setMarkerClick(v.findViewById(R.id.mood_2), dialog, "mood", "🙂", 0);
            setMarkerClick(v.findViewById(R.id.mood_3), dialog, "mood", "😐", 0);
            setMarkerClick(v.findViewById(R.id.mood_4), dialog, "mood", "😔", 0);
            setMarkerClick(v.findViewById(R.id.mood_5), dialog, "mood", "😤", 0);

            // Quick Status chips
            setMarkerClick(v.findViewById(R.id.status_blocked), dialog, "status", "Blocked", 0xFFE53935);
            setMarkerClick(v.findViewById(R.id.status_waiting), dialog, "status", "Waiting", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.status_review), dialog, "status", "Review", 0xFF9C27B0);
            setMarkerClick(v.findViewById(R.id.status_inprogress), dialog, "status", "In Progress", 0xFF2196F3);
            setMarkerClick(v.findViewById(R.id.status_done), dialog, "status", "Done", 0xFF4CAF50);
            setMarkerClick(v.findViewById(R.id.status_idea), dialog, "status", "Idea", 0xFF00BCD4);

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setMarkerClick(View view, AlertDialog dialog, String type, String value, int color) {
        if (view == null)
            return;
        view.setOnClickListener(v -> {
            task.markerType = type;
            task.markerValue = value;
            task.markerColor = color;
            dm.updateTask(task);
            setResult(RESULT_OK);
            dialog.dismiss();
            Toast.makeText(this, "Flag set", Toast.LENGTH_SHORT).show();
        });
    }

    private String prettyRepeat(String repeatType, int interval) {
        if (repeatType != null && repeatType.equals("custom_days")) {
            return "Custom";
        }
        if (interval <= 1) {
            switch (repeatType) {
                case "days":
                    return "Daily";
                case "weeks":
                    return "Weekly";
                case "months":
                    return "Monthly";
                case "years":
                    return "Yearly";
            }
        }
        return "Every " + Math.max(1, interval) + " " + repeatType;
    }

    // White dialog style dropdown selector
    private interface OnDropdownItemSelected {
        void onSelected(int index);
    }

    private void showDropdownDialog(String[] options, int selectedIndex, OnDropdownItemSelected listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dropdown_selector, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        LinearLayout optionsContainer = dialogView.findViewById(R.id.options_container);

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            View optionView = LayoutInflater.from(this).inflate(R.layout.item_dropdown_option, optionsContainer, false);

            TextView tvOptionText = optionView.findViewById(R.id.tv_option_text);
            ImageView ivOptionCheck = optionView.findViewById(R.id.iv_option_check);

            tvOptionText.setText(options[i]);
            ivOptionCheck.setVisibility(i == selectedIndex ? View.VISIBLE : View.GONE);

            if (i == selectedIndex) {
                tvOptionText.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
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
}
