package com.example.todolist;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TasksFragment extends Fragment {

    private RecyclerView recyclerTasks;
    private LinearLayout emptyState;
    private TaskListAdaptar adapter;
    private ArrayList<TaskList> taskList;
    private ArrayList<TaskList> allTasksForSearch;
    private DataManager dm;

    private Chip chipAll;
    private LinearLayout dynamicChipsContainer;
    private ImageButton btnMoreOptions;
    private String currentCategory = "All";
    private String currentFilter = "all";

    // Search components
    private CardView searchContainer;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private boolean isSearchActive = false;

    private List<Category> allCategories = new ArrayList<>();
    private List<Chip> categoryChips = new ArrayList<>();

    public TasksFragment() {
        // Required empty public constructor
    }

    public static TasksFragment newInstance() {
        return new TasksFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            dm = DataManager.getInstance(requireContext());
            taskList = new ArrayList<>();

            recyclerTasks = view.findViewById(R.id.recycler_tasks);
            emptyState = view.findViewById(R.id.empty_state);

            chipAll = view.findViewById(R.id.chip_all);
            dynamicChipsContainer = view.findViewById(R.id.dynamic_chips_container);
            // removed: chip_more_categories (we show all categories in horizontal scroll)
            btnMoreOptions = view.findViewById(R.id.btn_more_options);

            // Search components
            searchContainer = view.findViewById(R.id.search_container);
            etSearch = view.findViewById(R.id.et_search);
            btnClearSearch = view.findViewById(R.id.btn_clear_search);
            allTasksForSearch = new ArrayList<>();
            setupSearch();

            recyclerTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new TaskListAdaptar(requireContext(), taskList, (task, position) -> {
                // If task was marked complete, show undo option
                if (task.check == 1) {
                    // Remove from list immediately for visual feedback
                    if (position >= 0 && position < taskList.size()) {
                        taskList.remove(position);
                        adapter.notifyItemRemoved(position);
                    }

                    Snackbar.make(recyclerTasks, "Task completed", Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> {
                                // Restore task to incomplete
                                dm.updateStatusAndCompletedAt(task.id, 0, 0);
                                task.check = 0;
                                task.completedAt = 0;
                                int insertPos = Math.min(position, taskList.size());
                                taskList.add(insertPos, task);
                                adapter.notifyItemInserted(insertPos);
                                recyclerTasks.scrollToPosition(insertPos);
                            })
                            .addCallback(new Snackbar.Callback() {
                                @Override
                                public void onDismissed(Snackbar transientBottomBar, int event) {
                                    if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                                        loadTasks();
                                    }
                                }
                            })
                            .show();
                } else {
                    // Task was unchecked, just reload
                    loadTasks();
                }
            });

            adapter.setOnMarkerClickListener((task, position) -> showMarkSymbolSheet(task));

            // Long-press edit/delete removed (requested)

            recyclerTasks.setAdapter(adapter);

            attachSwipeGestures();

            setupCategoryChips();
            setupMoreOptionsMenu();
            loadTasks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void attachSwipeGestures() {
        final float buttonWidth = dpToPx(72);
        final int buttonCount = 3;
        final float maxLeftSwipe = -buttonWidth * buttonCount;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(spToPx(11));
        textPaint.setTextAlign(Paint.Align.CENTER);

        final int colorStarBg = Color.parseColor("#FFF3E0");
        final int colorDateBg = Color.parseColor("#E3F2FD");
        final int colorDeleteBg = Color.parseColor("#FFEBEE");

        final RectF[] actionRects = new RectF[buttonCount];
        for (int i = 0; i < buttonCount; i++)
            actionRects[i] = new RectF();

        final int[] openPosition = { -1 };
        final float[] lastDx = { 0f };
        final boolean[] isSwipingLeft = { false };

        // Handle tapping on the revealed action buttons.
        recyclerTasks.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (openPosition[0] < 0)
                    return false;
                if (e.getAction() != MotionEvent.ACTION_UP)
                    return false;

                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(openPosition[0]);
                if (vh == null) {
                    openPosition[0] = -1;
                    return false;
                }

                float x = e.getX();
                float y = e.getY();

                // If user taps inside one of the action rects, perform that action.
                for (int i = 0; i < actionRects.length; i++) {
                    RectF r = actionRects[i];
                    if (r != null && !r.isEmpty() && r.contains(x, y)) {
                        handleSwipeAction(i, openPosition[0]);
                        openPosition[0] = -1;
                        adapter.notifyItemChanged(vh.getAdapterPosition());
                        return true;
                    }
                }

                // If translation is zero (visually closed), reset state and allow click
                // propagation
                if (Math.abs(vh.itemView.getTranslationX()) < 1f) {
                    openPosition[0] = -1;
                    adapter.setOpenSwipeTask(-1, 0f);
                    return false; // Propagate event to child views (e.g. Flag)
                }

                // Tap outside closes the menu and consumes the event (don't trigger item click)
                int prev = openPosition[0];
                openPosition[0] = -1;
                if (prev >= 0 && prev < taskList.size()) {
                    adapter.setOpenSwipeTask(-1, 0f);
                    adapter.notifyItemChanged(prev);
                }
                return true; // Consume the touch event to prevent item click
            }
        });

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position < 0 || position >= taskList.size()) {
                    adapter.notifyDataSetChanged();
                    return;
                }

                if (direction == ItemTouchHelper.LEFT) {
                    // Keep the row open - don't trigger onSwiped completion
                    // This is handled in clearView
                    openPosition[0] = position;
                    if (position >= 0 && position < taskList.size()) {
                        adapter.setOpenSwipeTask(taskList.get(position).id, maxLeftSwipe);
                    }
                    // Reset the view position manually to stay open
                    viewHolder.itemView.setTranslationX(maxLeftSwipe);
                    return;
                }

                TaskList task = taskList.get(position);

                // Mark complete
                long completedAt = System.currentTimeMillis();
                dm.updateStatusAndCompletedAt(task.id, 1, completedAt);
                task.check = 1;
                task.completedAt = completedAt;
                taskList.remove(position);
                adapter.notifyItemRemoved(position);

                Snackbar.make(recyclerTasks, "Task completed", Snackbar.LENGTH_LONG)
                        .setAction("UNDO", v -> {
                            dm.updateStatusAndCompletedAt(task.id, 0, 0);
                            task.check = 0;
                            task.completedAt = 0;
                            int insertPos = Math.min(position, taskList.size());
                            taskList.add(insertPos, task);
                            adapter.notifyItemInserted(insertPos);
                            recyclerTasks.scrollToPosition(insertPos);
                        })
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                // If not undone, refresh list to keep filters accurate
                                if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                                    loadTasks();
                                }
                            }
                        })
                        .show();
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                // For LEFT swipe we want to reveal actions - lower threshold to make it easier
                if (isSwipingLeft[0])
                    return 0.3f;
                return 0.5f;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return defaultValue * 5f; // Require faster swipe to auto-trigger
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                // Don't call super - we handle the view translation ourselves
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) {
                    super.clearView(recyclerView, viewHolder);
                    return;
                }

                // If this is the open position, keep it open
                if (pos == openPosition[0]) {
                    viewHolder.itemView.setTranslationX(maxLeftSwipe);
                    return;
                }

                // Snap behavior for LEFT swipe: keep open if dragged past threshold
                if (lastDx[0] < 0 && isSwipingLeft[0]) {
                    // If swiped more than 1/4 of the way, keep it open
                    if (Math.abs(lastDx[0]) >= Math.abs(maxLeftSwipe) * 0.25f) {
                        openPosition[0] = pos;
                        if (pos >= 0 && pos < taskList.size()) {
                            adapter.setOpenSwipeTask(taskList.get(pos).id, maxLeftSwipe);
                        }
                        viewHolder.itemView.setTranslationX(maxLeftSwipe);
                        return;
                    }
                }

                // Close it
                if (openPosition[0] == pos)
                    openPosition[0] = -1;
                adapter.setOpenSwipeTask(-1, 0f);
                viewHolder.itemView.setTranslationX(0f);
                super.clearView(recyclerView, viewHolder);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    float dX,
                    float dY,
                    int actionState,
                    boolean isCurrentlyActive) {

                // Clear action rects first to prevent stale clicks when closed
                for (RectF r : actionRects)
                    r.setEmpty();

                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    return;
                }

                View itemView = viewHolder.itemView;
                float itemTop = itemView.getTop();
                float itemBottom = itemView.getBottom();
                float itemRight = itemView.getRight();
                float itemLeft = itemView.getLeft();

                // Close other open rows when starting a new swipe.
                if (isCurrentlyActive && openPosition[0] != -1 && openPosition[0] != pos) {
                    int prev = openPosition[0];
                    openPosition[0] = -1;
                    adapter.setOpenSwipeTask(-1, 0f);
                    adapter.notifyItemChanged(prev);
                }

                // If swiping right on an open row, close it
                if (isCurrentlyActive && pos == openPosition[0] && dX > 0) {
                    openPosition[0] = -1;
                    adapter.setOpenSwipeTask(-1, 0f);
                    viewHolder.itemView.setTranslationX(0f);
                    return;
                }

                isSwipingLeft[0] = dX < 0;
                lastDx[0] = dX;

                float clampedDx = dX;

                if (pos == openPosition[0] && !isCurrentlyActive) {
                    // Keep opened row pinned.
                    clampedDx = maxLeftSwipe;
                } else if (pos == openPosition[0] && dX > 0) {
                    // User is swiping right to close - interpolate
                    clampedDx = maxLeftSwipe + dX;
                    if (clampedDx > 0)
                        clampedDx = 0;
                } else if (dX < 0) {
                    clampedDx = Math.max(dX, maxLeftSwipe);
                }

                // Draw LEFT swipe action buttons.
                if (clampedDx < 0) {
                    float left = itemRight + clampedDx;
                    float right = itemRight;
                    float height = itemBottom - itemTop;
                    float top = itemTop;

                    // segment widths - 3 buttons now
                    float seg = buttonWidth;
                    float x0 = right - seg * 3; // Star
                    float x1 = right - seg * 2; // Date
                    float x2 = right - seg; // Delete
                    float x3 = right;

                    // backgrounds with rounded corners
                    float r = dpToPx(12);

                    // Star background (leftmost, rounded left corners)
                    paint.setColor(colorStarBg);
                    RectF starRect = new RectF(x0, top, x1, itemBottom);
                    c.drawRect(x0 + r, top, x1, itemBottom, paint);
                    c.drawRoundRect(new RectF(x0, top, x0 + r * 2, itemBottom), r, r, paint);

                    // Date background (middle)
                    paint.setColor(colorDateBg);
                    c.drawRect(x1, top, x2, itemBottom, paint);

                    // Delete background (rightmost, rounded right corners)
                    paint.setColor(colorDeleteBg);
                    RectF delRect = new RectF(x2, top, x3, itemBottom);
                    c.drawRect(x2, top, x3 - r, itemBottom, paint);
                    c.drawRoundRect(new RectF(x3 - r * 2, top, x3, itemBottom), r, r, paint);

                    // record clickable rects in RecyclerView coords
                    actionRects[0].set(x0, top, x1, itemBottom);
                    actionRects[1].set(x1, top, x2, itemBottom);
                    actionRects[2].set(x2, top, x3, itemBottom);

                    // icons + labels - Star (filled if starred), Date, Delete
                    int taskPos = viewHolder.getAdapterPosition();
                    boolean isStarred = taskPos >= 0 && taskPos < taskList.size()
                            && taskList.get(taskPos).isStarred == 1;
                    int starIcon = isStarred ? R.drawable.ic_star : R.drawable.ic_star_outline;
                    drawSwipeIconAndText(c, starIcon, x0, top, seg, height, Color.parseColor("#FF9800"),
                            isStarred ? "Unstar" : "Star");
                    drawSwipeIconAndText(c, R.drawable.ic_calendar_date, x1, top, seg, height,
                            Color.parseColor("#2196F3"), "Date");
                    drawSwipeIconAndText(c, R.drawable.ic_delete, x2, top, seg, height, Color.parseColor("#F44336"),
                            "Delete");
                }

                super.onChildDraw(c, recyclerView, viewHolder, clampedDx, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerTasks);
    }

    private void handleSwipeAction(int actionIndex, int position) {
        if (position < 0 || position >= taskList.size())
            return;
        TaskList task = taskList.get(position);
        if (task == null)
            return;

        // Reset swipe state
        adapter.setOpenSwipeTask(-1, 0f);

        switch (actionIndex) {
            case 0: // Star
                int newStarred = task.isStarred == 1 ? 0 : 1;
                task.isStarred = newStarred;
                dm.updateStarred(task.id, newStarred);
                adapter.notifyItemChanged(position);
                break;
            case 1: // Date - Open AddTaskBottomSheet for editing date/time/repeat
                openTaskEditDialog(task);
                break;
            case 2: // Delete
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Task")
                        .setMessage("Are you sure you want to delete \"" + task.task + "\"?")
                        .setPositiveButton("Delete", (d, w) -> {
                            dm.deleteTask(task);
                            loadTasks();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                break;
        }
    }

    private void showMarkSymbolSheet(TaskList task) {
        try {
            if (task == null)
                return;

            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_mark_symbol, null);
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
                    loadTasks();
                });
            }

            // Cancel button
            View btnCancel = v.findViewById(R.id.btn_cancel);
            if (btnCancel != null) {
                btnCancel.setOnClickListener(x -> dialog.dismiss());
            }

            // Priority buttons
            setMarkerClick(v.findViewById(R.id.priority_urgent), task, dialog, "priority", "Urgent", 0xFFE53935);
            setMarkerClick(v.findViewById(R.id.priority_high), task, dialog, "priority", "High", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.priority_medium), task, dialog, "priority", "Medium", 0xFF2196F3);
            setMarkerClick(v.findViewById(R.id.priority_low), task, dialog, "priority", "Low", 0xFF4CAF50);

            // Flags (6 colors)
            setMarkerClick(v.findViewById(R.id.flag_red), task, dialog, "flag", "", 0xFFF06292);
            setMarkerClick(v.findViewById(R.id.flag_orange), task, dialog, "flag", "", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.flag_yellow), task, dialog, "flag", "", 0xFFFBC02D);
            setMarkerClick(v.findViewById(R.id.flag_purple), task, dialog, "flag", "", 0xFFAB47BC);
            setMarkerClick(v.findViewById(R.id.flag_blue), task, dialog, "flag", "", 0xFF42A5F5);
            setMarkerClick(v.findViewById(R.id.flag_green), task, dialog, "flag", "", 0xFF66BB6A);

            // Numbers
            setMarkerClick(v.findViewById(R.id.num_1), task, dialog, "number", "1", 0xFFF06292);
            setMarkerClick(v.findViewById(R.id.num_2), task, dialog, "number", "2", 0xFFFBC02D);
            setMarkerClick(v.findViewById(R.id.num_3), task, dialog, "number", "3", 0xFFAB47BC);
            setMarkerClick(v.findViewById(R.id.num_4), task, dialog, "number", "4", 0xFF42A5F5);
            setMarkerClick(v.findViewById(R.id.num_5), task, dialog, "number", "5", 0xFF66BB6A);

            // Progress (percentages)
            setMarkerClick(v.findViewById(R.id.prog_1), task, dialog, "progress", "0%", 0xFFBDBDBD);
            setMarkerClick(v.findViewById(R.id.prog_2), task, dialog, "progress", "25%", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.prog_3), task, dialog, "progress", "50%", 0xFF2196F3);
            setMarkerClick(v.findViewById(R.id.prog_4), task, dialog, "progress", "75%", 0xFF9C27B0);
            setMarkerClick(v.findViewById(R.id.prog_5), task, dialog, "progress", "100%", 0xFF4CAF50);

            // Mood
            setMarkerClick(v.findViewById(R.id.mood_1), task, dialog, "mood", "😀", 0);
            setMarkerClick(v.findViewById(R.id.mood_2), task, dialog, "mood", "🙂", 0);
            setMarkerClick(v.findViewById(R.id.mood_3), task, dialog, "mood", "😐", 0);
            setMarkerClick(v.findViewById(R.id.mood_4), task, dialog, "mood", "😔", 0);
            setMarkerClick(v.findViewById(R.id.mood_5), task, dialog, "mood", "😤", 0);

            // Quick Status chips
            setMarkerClick(v.findViewById(R.id.status_blocked), task, dialog, "status", "Blocked", 0xFFE53935);
            setMarkerClick(v.findViewById(R.id.status_waiting), task, dialog, "status", "Waiting", 0xFFFF9800);
            setMarkerClick(v.findViewById(R.id.status_review), task, dialog, "status", "Review", 0xFF9C27B0);
            setMarkerClick(v.findViewById(R.id.status_inprogress), task, dialog, "status", "In Progress", 0xFF2196F3);
            setMarkerClick(v.findViewById(R.id.status_done), task, dialog, "status", "Done", 0xFF4CAF50);
            setMarkerClick(v.findViewById(R.id.status_idea), task, dialog, "status", "Idea", 0xFF00BCD4);

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setMarkerClick(View view, TaskList task,
            AlertDialog dialog,
            String type, String value, int color) {
        if (view == null || task == null)
            return;
        view.setOnClickListener(v -> {
            task.markerType = type;
            task.markerValue = value;
            task.markerColor = color;
            dm.updateTask(task);
            dialog.dismiss();
            loadTasks();
        });
    }

    private void showInlineDatePicker(TaskList task) {
        try {
            Calendar cal = Calendar.getInstance();
            if (task.dueDate > 0) {
                cal.setTimeInMillis(task.dueDate);
            }
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);

            new android.app.DatePickerDialog(requireContext(), (view, y, m, d) -> {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.YEAR, y);
                c.set(Calendar.MONTH, m);
                c.set(Calendar.DAY_OF_MONTH, d);
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                task.dueDate = c.getTimeInMillis();
                dm.updateTask(task);
                loadTasks();
            }, year, month, day).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openTaskEditDialog(TaskList task) {
        try {
            if (task == null || getActivity() == null)
                return;
            AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstanceForEdit(task);
            bottomSheet.setOnTaskAddedListener(() -> {
                loadTasks();
            });
            bottomSheet.show(getParentFragmentManager(), "EditTaskBottomSheet");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawSwipeIconAndText(Canvas c,
            int drawableRes,
            float left,
            float top,
            float width,
            float height,
            int tintColor,
            String label) {
        try {
            android.graphics.drawable.Drawable d = ContextCompat.getDrawable(requireContext(), drawableRes);
            if (d == null)
                return;

            d = d.mutate();
            d.setTint(tintColor);

            int iconSize = (int) dpToPx(22);
            int cx = (int) (left + width / 2f);
            int iconTop = (int) (top + height / 2f - dpToPx(label.isEmpty() ? 11 : 16));
            int iconLeft = cx - iconSize / 2;
            int iconRight = cx + iconSize / 2;
            int iconBottom = iconTop + iconSize;
            d.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            d.draw(c);

            if (label != null && !label.isEmpty()) {
                Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
                tp.setColor(tintColor == Color.WHITE ? Color.WHITE : Color.parseColor("#6B7C93"));
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(spToPx(11));
                c.drawText(label, left + width / 2f, iconBottom + dpToPx(14), tp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private float dpToPx(float dp) {
        return dp * requireContext().getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * requireContext().getResources().getDisplayMetrics().scaledDensity;
    }

    private void setupCategoryChips() {
        if (chipAll != null) {
            chipAll.setOnClickListener(v -> selectCategory("All"));
        }

        // Load categories from data manager
        try {
            List<Category> dbCategories = dm.getAllCategories();
            if (dbCategories != null) {
                allCategories = new ArrayList<>(dbCategories);
                // Remove "No Category" from visible chips
                allCategories
                        .removeIf(cat -> cat != null && cat.getName() != null && cat.getName().equals("No Category"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            allCategories = new ArrayList<>();
        }

        if (dynamicChipsContainer != null) {
            dynamicChipsContainer.removeAllViews();
        }
        categoryChips.clear();

        // Show all categories as chips (HorizontalScrollView)
        for (int i = 0; i < allCategories.size(); i++) {
            Category category = allCategories.get(i);
            if (category != null && category.getName() != null) {
                Chip chip = createCategoryChip(category.getName());
                if (dynamicChipsContainer != null) {
                    dynamicChipsContainer.addView(chip);
                }
                categoryChips.add(chip);
            }
        }
    }

    private Chip createCategoryChip(String categoryName) {
        Chip chip = new Chip(requireContext());
        chip.setText(categoryName);
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.chip_inactive_text));
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.chip_outline_bg)));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.chip_outline_stroke)));
        chip.setChipStrokeWidth(
                android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP,
                        1,
                        getResources().getDisplayMetrics()));
        float radiusPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                20,
                getResources().getDisplayMetrics());
        chip.setChipCornerRadius(radiusPx);
        chip.setCheckedIconVisible(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginEndPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                8,
                getResources().getDisplayMetrics());
        params.setMarginEnd(marginEndPx);
        chip.setLayoutParams(params);

        chip.setOnClickListener(v -> selectCategory(categoryName));
        return chip;
    }

    private void setupMoreOptionsMenu() {
        btnMoreOptions.setOnClickListener(v -> {
            showCustomPopupMenu(v);
        });
    }

    private void showCustomPopupMenu(View anchor) {
        final List<String> options = new ArrayList<>();
        final List<Integer> icons = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        // Add Menu Items
        options.add("Manage Categories");
        icons.add(R.drawable.ic_folder); // Assuming generic folder icon exists or null
        actions.add(this::showManageCategoriesDialog);

        options.add("Search");
        icons.add(android.R.drawable.ic_menu_search);
        actions.add(this::showSearchDialog);

        // Sorting
        options.add("Sort by Date");
        icons.add(R.drawable.ic_calendar);
        actions.add(this::sortByDate);

        options.add("Sort by Priority");
        icons.add(R.drawable.ic_star);
        actions.add(this::sortByPriority);

        options.add("Sort by Name");
        icons.add(R.drawable.ic_list);
        actions.add(this::sortByName);

        // Filters
        options.add("Filter: Today");
        icons.add(0);
        actions.add(this::filterByToday);

        options.add("Filter: Previous");
        icons.add(0);
        actions.add(this::filterByPrevious);

        options.add("Filter: Future");
        icons.add(0);
        actions.add(this::filterByFuture);

        options.add("Filter: Completed");
        icons.add(0);
        actions.add(this::filterByCompleted);

        // Progress Style
        options.add("Progress Style");
        icons.add(0);
        actions.add(this::showProgressStyleDialog);

        // Printing
        options.add("Print as PDF");
        icons.add(0);
        actions.add(this::printTasksAsPDF);

        // Create ListPopupWindow
        final android.widget.ListPopupWindow popup = new android.widget.ListPopupWindow(requireContext());
        popup.setAnchorView(anchor);
        popup.setWidth((int) dpToPx(200));
        popup.setModal(true);
        popup.setVerticalOffset((int) dpToPx(4));

        // Use custom adapter for white background and styling
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_1, options) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.BLACK);
                view.setBackgroundColor(Color.WHITE);
                view.setTextSize(14);
                view.setPadding((int) dpToPx(16), (int) dpToPx(12), (int) dpToPx(16), (int) dpToPx(12));

                // Add icons if available
                if (position < icons.size() && icons.get(position) != 0) {
                    // Using setCompoundDrawablesWithIntrinsicBounds would require drawables
                    // Simplicity: just text for now to match user request "white background"
                }
                return view;
            }
        };

        popup.setAdapter(adapter);

        // thorough white background
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dpToPx(8));
        background.setStroke(1, Color.parseColor("#EEEEEE"));
        popup.setBackgroundDrawable(background);

        popup.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < actions.size()) {
                actions.get(position).run();
            }
            popup.dismiss();
        });

        popup.show();
    }

    private void showProgressStyleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress_style, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Get current settings
        int currentStyle = adapter.getProgressStyle();
        boolean showNames = adapter.getShowSubtaskNames();

        // Views
        LinearLayout optionFill = dialogView.findViewById(R.id.option_fill);
        LinearLayout optionBar = dialogView.findViewById(R.id.option_bar);
        LinearLayout optionBadge = dialogView.findViewById(R.id.option_badge);
        android.widget.RadioButton radioFill = dialogView.findViewById(R.id.radio_fill);
        android.widget.RadioButton radioBar = dialogView.findViewById(R.id.radio_bar);
        android.widget.RadioButton radioBadge = dialogView.findViewById(R.id.radio_badge);
        androidx.appcompat.widget.SwitchCompat switchShowNames = dialogView.findViewById(R.id.switch_show_names);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnApply = dialogView.findViewById(R.id.btn_apply);

        // Set initial states
        radioFill.setChecked(currentStyle == TaskListAdaptar.STYLE_FILL);
        radioBar.setChecked(currentStyle == TaskListAdaptar.STYLE_BAR);
        radioBadge.setChecked(currentStyle == TaskListAdaptar.STYLE_BADGE);
        switchShowNames.setChecked(showNames);

        // Update backgrounds
        updateOptionBackground(optionFill, currentStyle == TaskListAdaptar.STYLE_FILL);
        updateOptionBackground(optionBar, currentStyle == TaskListAdaptar.STYLE_BAR);
        updateOptionBackground(optionBadge, currentStyle == TaskListAdaptar.STYLE_BADGE);

        // Color selection views
        View colorGreen = dialogView.findViewById(R.id.color_green);
        View colorBlue = dialogView.findViewById(R.id.color_blue);
        View colorOrange = dialogView.findViewById(R.id.color_orange);
        View colorPurple = dialogView.findViewById(R.id.color_purple);
        View colorRed = dialogView.findViewById(R.id.color_red);
        View colorTeal = dialogView.findViewById(R.id.color_teal);

        ImageView checkGreen = dialogView.findViewById(R.id.check_green);
        ImageView checkBlue = dialogView.findViewById(R.id.check_blue);
        ImageView checkOrange = dialogView.findViewById(R.id.check_orange);
        ImageView checkPurple = dialogView.findViewById(R.id.check_purple);
        ImageView checkRed = dialogView.findViewById(R.id.check_red);
        ImageView checkTeal = dialogView.findViewById(R.id.check_teal);

        ImageView[] colorChecks = { checkGreen, checkBlue, checkOrange, checkPurple, checkRed, checkTeal };

        // Set current color selection
        int currentColor = adapter.getProgressColor();
        for (int i = 0; i < colorChecks.length; i++) {
            colorChecks[i].setVisibility(i == currentColor ? View.VISIBLE : View.GONE);
        }

        // Color click listeners
        final int[] selectedColor = { currentColor };
        View.OnClickListener colorClickListener = v -> {
            int id = v.getId();
            int colorIndex = 0;
            if (id == R.id.color_green)
                colorIndex = TaskListAdaptar.COLOR_GREEN;
            else if (id == R.id.color_blue)
                colorIndex = TaskListAdaptar.COLOR_BLUE;
            else if (id == R.id.color_orange)
                colorIndex = TaskListAdaptar.COLOR_ORANGE;
            else if (id == R.id.color_purple)
                colorIndex = TaskListAdaptar.COLOR_PURPLE;
            else if (id == R.id.color_red)
                colorIndex = TaskListAdaptar.COLOR_RED;
            else if (id == R.id.color_teal)
                colorIndex = TaskListAdaptar.COLOR_TEAL;

            selectedColor[0] = colorIndex;
            for (int i = 0; i < colorChecks.length; i++) {
                colorChecks[i].setVisibility(i == colorIndex ? View.VISIBLE : View.GONE);
            }
        };

        colorGreen.setOnClickListener(colorClickListener);
        colorBlue.setOnClickListener(colorClickListener);
        colorOrange.setOnClickListener(colorClickListener);
        colorPurple.setOnClickListener(colorClickListener);
        colorRed.setOnClickListener(colorClickListener);
        colorTeal.setOnClickListener(colorClickListener);

        // Click listeners for options
        View.OnClickListener optionClickListener = v -> {
            int id = v.getId();
            radioFill.setChecked(id == R.id.option_fill);
            radioBar.setChecked(id == R.id.option_bar);
            radioBadge.setChecked(id == R.id.option_badge);

            updateOptionBackground(optionFill, id == R.id.option_fill);
            updateOptionBackground(optionBar, id == R.id.option_bar);
            updateOptionBackground(optionBadge, id == R.id.option_badge);
        };

        optionFill.setOnClickListener(optionClickListener);
        optionBar.setOnClickListener(optionClickListener);
        optionBadge.setOnClickListener(optionClickListener);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnApply.setOnClickListener(v -> {
            int selectedStyle = TaskListAdaptar.STYLE_FILL;
            if (radioBar.isChecked())
                selectedStyle = TaskListAdaptar.STYLE_BAR;
            else if (radioBadge.isChecked())
                selectedStyle = TaskListAdaptar.STYLE_BADGE;

            adapter.setProgressStyle(selectedStyle);
            adapter.setShowSubtaskNames(switchShowNames.isChecked());
            adapter.setProgressColor(selectedColor[0]);

            Toast.makeText(requireContext(), "Progress style updated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateOptionBackground(LinearLayout option, boolean selected) {
        if (option != null) {
            option.setBackgroundResource(selected ? R.drawable.option_selected_bg : R.drawable.option_unselected_bg);
        }
    }

    private void selectCategory(String category) {
        currentCategory = category;
        // Reset filter to default (pending) when switching categories
        currentFilter = null;

        // Update "All" chip state
        if (chipAll != null) {
            updateChipState(chipAll, category.equals("All"));
        }

        // Update dynamic chip states
        for (Chip chip : categoryChips) {
            if (chip != null) {
                updateChipState(chip, chip.getText().toString().equals(category));
            }
        }

        loadTasks();
    }

    private void updateChipState(Chip chip, boolean selected) {
        if (chip == null)
            return;
        if (selected) {
            chip.setChipBackgroundColor(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.chip_all_bg)));
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.chip_all_text));
            chip.setChipStrokeWidth(0);
        } else {
            chip.setChipBackgroundColor(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.chip_outline_bg)));
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.chip_inactive_text));
            chip.setChipStrokeColor(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.chip_outline_stroke)));
            chip.setChipStrokeWidth(
                    android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_DIP,
                            1,
                            getResources().getDisplayMetrics()));
        }
    }

    public void loadTasks() {
        try {
            if (taskList == null)
                taskList = new ArrayList<>();
            taskList.clear();

            // Reset swipe state when reloading tasks
            if (adapter != null) {
                adapter.setOpenSwipeTask(-1, 0f);
            }

            List<TaskList> tasks = new ArrayList<>();

            // Check current filter to determine what to load
            if ("completed".equals(currentFilter)) {
                if (currentCategory == null || currentCategory.equals("All")) {
                    tasks = dm.getTasksByStatus(1);
                } else {
                    tasks = dm.getTasksByStatusAndCategory(1, currentCategory);
                }
            } else if ("previous".equals(currentFilter)) { // Overdue
                long todayStart = getTodayStartTimestamp();
                List<TaskList> allPending;
                if (currentCategory == null || currentCategory.equals("All")) {
                    allPending = dm.getTasksByStatus(0);
                } else {
                    allPending = dm.getTasksByStatusAndCategory(0, currentCategory);
                }
                // Filter for overdue
                for (TaskList t : allPending) {
                    if (t.dueDate > 0 && t.dueDate < todayStart) {
                        tasks.add(t);
                    }
                }
            } else if ("today".equals(currentFilter)) {
                long todayStart = getTodayStartTimestamp();
                long todayEnd = getTodayEndTimestamp();
                List<TaskList> allPending;
                if (currentCategory == null || currentCategory.equals("All")) {
                    allPending = dm.getTasksByStatus(0);
                } else {
                    allPending = dm.getTasksByStatusAndCategory(0, currentCategory);
                }
                for (TaskList t : allPending) {
                    if (t.dueDate >= todayStart && t.dueDate <= todayEnd) {
                        tasks.add(t);
                    }
                }
            } else if ("future".equals(currentFilter)) {
                long todayEnd = getTodayEndTimestamp();
                List<TaskList> allPending;
                if (currentCategory == null || currentCategory.equals("All")) {
                    allPending = dm.getTasksByStatus(0);
                } else {
                    allPending = dm.getTasksByStatusAndCategory(0, currentCategory);
                }
                for (TaskList t : allPending) {
                    if (t.dueDate > todayEnd) {
                        tasks.add(t);
                    }
                }
            } else if ("starred".equals(currentFilter)) {
                tasks = dm.getStarredTasks();
            } else { // Default: Pending (and optionally active/today/etc if other filters existed)
                // For now, default is ALL Pending
                if (currentCategory == null || currentCategory.equals("All")) {
                    tasks = dm.getTasksByStatus(0);
                } else {
                    tasks = dm.getTasksByStatusAndCategory(0, currentCategory);
                }
            }

            if (tasks != null) {
                taskList.addAll(tasks);
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            // Show/hide empty state
            if (taskList.isEmpty()) {
                if (emptyState != null)
                    emptyState.setVisibility(View.VISIBLE);
                if (recyclerTasks != null)
                    recyclerTasks.setVisibility(View.GONE);
            } else {
                if (emptyState != null)
                    emptyState.setVisibility(View.GONE);
                if (recyclerTasks != null)
                    recyclerTasks.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadStarredTasks() {
        // Set filter to starred so UI updates correctly (All chip deselected)
        currentFilter = "starred";
        currentCategory = "All"; // Reset to All scope effectively
        updateCategoryChipsUI();
        loadTasks();
    }

    public void loadTasksByCategory(String category) {
        currentCategory = category;
        currentFilter = null; // Reset filter when picking category explicitly
        // Update chip UI to reflect selected category
        updateCategoryChipsUI();
        loadTasks();
    }

    private void updateCategoryChipsUI() {
        // Update "All" chip state
        // Only select "All" if we are in "All" category AND no specific filter is
        // active (default view)
        if (chipAll != null) {
            updateChipState(chipAll, "All".equals(currentCategory) && currentFilter == null);
        }

        // Update dynamic chip states
        for (Chip chip : categoryChips) {
            if (chip != null) {
                updateChipState(chip, chip.getText().toString().equals(currentCategory));
            }
        }
    }

    public String getSelectedCategory() {
        return currentCategory;
    }

    public void showCompletedFromMine() {
        // Default to All categories for Mine navigation
        currentCategory = "All";
        currentFilter = "completed";
        updateCategoryChipsUI();
        loadTasks();
    }

    public void showPendingFromMine() {
        currentCategory = "All";
        currentFilter = null; // Pending
        updateCategoryChipsUI();
        loadTasks();
    }

    public void showOverdueFromMine() {
        currentCategory = "All";
        currentFilter = "previous";
        updateCategoryChipsUI();
        loadTasks();
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            loadTasks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showManageCategoriesDialog() {
        // Show dialog with list of categories to manage
        List<Category> categories = dm.getAllCategories();
        if (categories == null || categories.isEmpty()) {
            Toast.makeText(requireContext(), "No categories to manage", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_category, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView rvCategories = dialogView.findViewById(R.id.rv_categories);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel);

        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Simple adapter for categories
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_select_category, parent, false);
                return new RecyclerView.ViewHolder(view) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Category category = categories.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tv_category_name);
                ImageView ivIcon = holder.itemView.findViewById(R.id.iv_category_icon);

                tvName.setText(category.getName());

                // Set category color tint if available
                if (category.getColor() != null && !category.getColor().isEmpty()) {
                    try {
                        ivIcon.setColorFilter(android.graphics.Color.parseColor(category.getColor()));
                    } catch (Exception e) {
                        ivIcon.setColorFilter(getResources().getColor(R.color.primary_blue));
                    }
                }

                holder.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    showEditCategoryDialog(category);
                });
            }

            @Override
            public int getItemCount() {
                return categories.size();
            }
        };

        rvCategories.setAdapter(adapter);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showEditCategoryDialog(Category category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manage_category, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etName = dialogView.findViewById(R.id.et_category_name);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnSave = dialogView.findViewById(R.id.btn_save);
        View btnDelete = dialogView.findViewById(R.id.btn_delete_category);

        // Color views
        View colorBlue = dialogView.findViewById(R.id.color_blue);
        View colorGreen = dialogView.findViewById(R.id.color_green);
        View colorOrange = dialogView.findViewById(R.id.color_orange);
        View colorPurple = dialogView.findViewById(R.id.color_purple);
        View colorRed = dialogView.findViewById(R.id.color_red);
        View colorTeal = dialogView.findViewById(R.id.color_teal);

        // Icon views
        View iconWork = dialogView.findViewById(R.id.icon_work);
        View iconPersonal = dialogView.findViewById(R.id.icon_personal);
        View iconStar = dialogView.findViewById(R.id.icon_star);
        View iconCalendar = dialogView.findViewById(R.id.icon_calendar);
        View iconFire = dialogView.findViewById(R.id.icon_fire);
        View iconList = dialogView.findViewById(R.id.icon_list);

        etName.setText(category.getName());

        final String[] selectedColor = { category.getColor() != null ? category.getColor() : "#2196F3" };
        final String[] selectedIcon = { category.getIcon() != null ? category.getIcon() : "folder" };

        View[] colorViews = { colorBlue, colorGreen, colorOrange, colorPurple, colorRed, colorTeal };
        String[] colorValues = { "#2196F3", "#4CAF50", "#FF9800", "#9C27B0", "#F44336", "#009688" };

        // Pre-select current color
        for (int i = 0; i < colorValues.length; i++) {
            if (colorValues[i].equalsIgnoreCase(selectedColor[0])) {
                colorViews[i].setScaleX(1.3f);
                colorViews[i].setScaleY(1.3f);
            }
        }

        View.OnClickListener colorClickListener = v -> {
            for (int i = 0; i < colorViews.length; i++) {
                if (v == colorViews[i]) {
                    selectedColor[0] = colorValues[i];
                    colorViews[i].setScaleX(1.3f);
                    colorViews[i].setScaleY(1.3f);
                } else {
                    colorViews[i].setScaleX(1.0f);
                    colorViews[i].setScaleY(1.0f);
                }
            }
        };

        for (View cv : colorViews) {
            cv.setOnClickListener(colorClickListener);
        }

        View[] iconViews = { iconWork, iconPersonal, iconStar, iconCalendar, iconFire, iconList };
        String[] iconNames = { "folder", "profile", "star", "calendar", "fire", "list" };

        // Pre-select current icon
        for (int i = 0; i < iconNames.length; i++) {
            if (iconNames[i].equalsIgnoreCase(selectedIcon[0])) {
                iconViews[i].setBackgroundResource(R.drawable.circle_blue_bg);
            } else {
                iconViews[i].setBackgroundResource(R.drawable.circle_gray_bg);
            }
        }

        View.OnClickListener iconClickListener = v -> {
            for (int i = 0; i < iconViews.length; i++) {
                if (v == iconViews[i]) {
                    selectedIcon[0] = iconNames[i];
                    iconViews[i].setBackgroundResource(R.drawable.circle_blue_bg);
                } else {
                    iconViews[i].setBackgroundResource(R.drawable.circle_gray_bg);
                }
            }
        };

        for (View iv : iconViews) {
            iv.setOnClickListener(iconClickListener);
        }

        // Hide delete for default categories
        if (category.isDefault()) {
            btnDelete.setVisibility(View.GONE);
        }

        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Category")
                    .setMessage("Are you sure you want to delete \"" + category.getName()
                            + "\"? Tasks in this category will be moved to 'All'.")
                    .setPositiveButton("Delete", (d, w) -> {
                        dm.deleteCategory(category.getId());
                        setupCategoryChips();
                        loadTasks();
                        dialog.dismiss();
                        Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            String oldName = category.getName();
            category.setName(newName);
            category.setColor(selectedColor[0]);
            category.setIcon(selectedIcon[0]);
            dm.updateCategory(category);

            // Update tasks with old category name to new name
            if (!oldName.equals(newName)) {
                List<TaskList> tasksInCategory = dm.getTasksByCategory(oldName);
                for (TaskList task : tasksInCategory) {
                    task.setCategory(newName);
                    dm.updateTask(task);
                }
            }

            setupCategoryChips();
            loadTasks();

            // Refresh navigation drawer in MainActivity
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshNavigationCategories();
            }

            dialog.dismiss();
            Toast.makeText(requireContext(), "Category updated", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showSearchDialog() {
        // Toggle search visibility
        if (searchContainer != null) {
            if (searchContainer.getVisibility() == View.VISIBLE) {
                hideSearch();
            } else {
                showSearch();
            }
        }
    }

    private void setupSearch() {
        if (etSearch == null || btnClearSearch == null)
            return;

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            hideSearch();
        });
    }

    private void showSearch() {
        isSearchActive = true;
        if (searchContainer != null) {
            searchContainer.setVisibility(View.VISIBLE);
        }
        if (etSearch != null) {
            etSearch.requestFocus();
            // Show keyboard
            try {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext()
                        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {
            }
        }

        // Store all current tasks for filtering
        allTasksForSearch.clear();
        allTasksForSearch.addAll(taskList);
    }

    private void hideSearch() {
        isSearchActive = false;
        if (searchContainer != null) {
            searchContainer.setVisibility(View.GONE);
        }
        if (etSearch != null) {
            etSearch.setText("");
            // Hide keyboard
            try {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext()
                        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            } catch (Exception ignored) {
            }
        }

        // Reload original tasks
        loadTasks();
    }

    private void performSearch(String query) {
        if (!isSearchActive)
            return;

        taskList.clear();

        if (query.isEmpty()) {
            taskList.addAll(allTasksForSearch);
        } else {
            String lowerQuery = query.toLowerCase();
            for (TaskList task : allTasksForSearch) {
                if (task.getTask() != null && task.getTask().toLowerCase().contains(lowerQuery)) {
                    taskList.add(task);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // Update empty state
        if (taskList.isEmpty()) {
            if (emptyState != null)
                emptyState.setVisibility(View.VISIBLE);
            if (recyclerTasks != null)
                recyclerTasks.setVisibility(View.GONE);
        } else {
            if (emptyState != null)
                emptyState.setVisibility(View.GONE);
            if (recyclerTasks != null)
                recyclerTasks.setVisibility(View.VISIBLE);
        }
    }

    private void sortByDate() {
        // Reset filter and load pending tasks
        currentFilter = null;
        updateCategoryChipsUI();
        loadTasks();

        // Sort tasks by date
        taskList.sort((t1, t2) -> {
            // Handle tasks with no due date (0) to appear at the end
            if (t1.getDueDate() == 0 && t2.getDueDate() == 0)
                return 0;
            if (t1.getDueDate() == 0)
                return 1;
            if (t2.getDueDate() == 0)
                return -1;
            return Long.compare(t1.getDueDate(), t2.getDueDate());
        });
        adapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Sorted by Date", Toast.LENGTH_SHORT).show();
    }

    private void sortByPriority() {
        // Reset filter and load pending tasks
        currentFilter = null;
        updateCategoryChipsUI();
        loadTasks();

        // Sort tasks by Priority: Urgent > High > Medium > Low > None
        taskList.sort((t1, t2) -> {
            // First compare starred status
            if (t1.getIsStarred() != t2.getIsStarred()) {
                return Integer.compare(t2.getIsStarred(), t1.getIsStarred());
            }

            // Then compare priority markers
            int p1 = getPriorityValue(t1);
            int p2 = getPriorityValue(t2);
            return Integer.compare(p2, p1); // Descending
        });
        adapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Sorted by Priority", Toast.LENGTH_SHORT).show();
    }

    private int getPriorityValue(TaskList t) {
        if ("priority".equals(t.markerType) && t.markerValue != null) {
            switch (t.markerValue) {
                case "Urgent":
                    return 4;
                case "High":
                    return 3;
                case "Medium":
                    return 2;
                case "Low":
                    return 1;
            }
        }
        return 0;
    }

    private void sortByName() {
        // Reset filter and load pending tasks
        currentFilter = null;
        updateCategoryChipsUI();
        loadTasks();

        // Sort tasks alphabetically
        taskList.sort((t1, t2) -> {
            String s1 = t1.getTask() != null ? t1.getTask() : "";
            String s2 = t2.getTask() != null ? t2.getTask() : "";
            return s1.compareToIgnoreCase(s2);
        });
        adapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Sorted by Name", Toast.LENGTH_SHORT).show();
    }

    private void filterByToday() {
        currentFilter = "today";
        updateCategoryChipsUI();
        loadTasks();
        Toast.makeText(requireContext(), "Showing Today's Tasks", Toast.LENGTH_SHORT).show();
    }

    private void filterByPrevious() {
        currentFilter = "previous";
        updateCategoryChipsUI();
        loadTasks();
        Toast.makeText(requireContext(), "Showing Previous Tasks", Toast.LENGTH_SHORT).show();
    }

    private void filterByFuture() {
        currentFilter = "future";
        updateCategoryChipsUI();
        loadTasks();
        Toast.makeText(requireContext(), "Showing Future Tasks", Toast.LENGTH_SHORT).show();
    }

    private void filterByCompleted() {
        currentFilter = "completed";
        updateCategoryChipsUI();
        loadTasks();
        Toast.makeText(requireContext(), "Showing Completed Tasks", Toast.LENGTH_SHORT).show();
    }

    private void printTasks() {
        // Option removed as requested
    }

    private void printTasksAsPDF() {
        if (taskList.isEmpty()) {
            Toast.makeText(requireContext(), "No tasks to print", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show dialog to choose whether to include completed items
        new AlertDialog.Builder(requireContext())
                .setTitle("Print Options")
                .setMessage("Include completed tasks in the PDF?")
                .setPositiveButton("Yes, Include All", (dialog, which) -> {
                    generatePDF(true);
                })
                .setNegativeButton("No, Pending Only", (dialog, which) -> {
                    generatePDF(false);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void generatePDF(boolean includeCompleted) {
        try {
            // Filter list based on selection
            List<TaskList> tasksToPrint = new ArrayList<>();
            for (TaskList t : taskList) {
                if (includeCompleted || t.getCheck() == 0) {
                    tasksToPrint.add(t);
                }
            }

            if (tasksToPrint.isEmpty()) {
                Toast.makeText(requireContext(), "No matching tasks to print", Toast.LENGTH_SHORT).show();
                return;
            }

            // Generate HTML for the selected list
            StringBuilder html = new StringBuilder();
            html.append("<html><head><style>");
            html.append("body { font-family: Helvetica, Arial, sans-serif; padding: 20px; }");
            html.append("h1 { color: #333; text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px; }");
            html.append(".summary { margin-bottom: 20px; color: #666; font-size: 0.9em; text-align: right; }");
            html.append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
            html.append(
                    "th { background-color: #f2f2f2; color: #333; padding: 12px 10px; text-align: left; border-bottom: 2px solid #ddd; }");
            html.append("td { padding: 12px 10px; border-bottom: 1px solid #eee; vertical-align: top; }");
            html.append(".status-done { color: #2E7D32; font-weight: bold; }");
            html.append(".status-pending { color: #D32F2F; font-weight: bold; }");
            html.append(".task-title { font-size: 1.1em; font-weight: bold; color: #000; }");
            html.append(".task-meta { color: #555; font-size: 0.85em; margin-top: 4px; }");
            html.append(
                    ".priority-badge { font-weight: bold; font-size: 0.75em; padding: 2px 6px; border-radius: 4px; color: white; display: inline-block; margin-left: 6px; vertical-align: middle; }");
            html.append("</style></head><body>");

            html.append("<h1>").append(currentCategory).append(" Tasks</h1>");

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy",
                    java.util.Locale.getDefault());
            String dateStr = sdf.format(new java.util.Date());
            html.append("<div class='summary'>Generated on: ").append(dateStr).append("<br>Total Items: ")
                    .append(tasksToPrint.size()).append("</div>");

            html.append("<table>");
            html.append(
                    "<tr><th style='width: 60%'>Task Details</th><th style='width: 25%'>Due Date</th><th style='width: 15%'>Status</th></tr>");

            for (TaskList task : tasksToPrint) {
                html.append("<tr>");

                // Task Name and Priority
                html.append("<td><div class='task-title'>").append(android.text.TextUtils.htmlEncode(task.getTask()));

                if ("priority".equals(task.markerType) && task.markerValue != null) {
                    String color = "#78909C"; // default gray
                    if (task.markerColor != 0) {
                        color = String.format("#%06X", (0xFFFFFF & task.markerColor));
                    }
                    html.append(" <span class='priority-badge' style='background-color:").append(color).append(";'>")
                            .append(task.markerValue).append("</span>");
                }
                html.append("</div>");

                // Description/Time
                if (task.taskTime != null && !task.taskTime.isEmpty()) {
                    html.append("<div class='task-meta'>Time: ").append(task.taskTime).append("</div>");
                }
                html.append("</td>");

                // Due Date
                html.append("<td>");
                if (task.getDueDate() > 0) {
                    html.append(sdf.format(new java.util.Date(task.getDueDate())));
                } else {
                    html.append("<span style='color: #999'>--</span>");
                }
                html.append("</td>");

                // Status
                if (task.getCheck() == 1) {
                    html.append("<td class='status-done'>Completed</td>");
                } else {
                    html.append("<td class='status-pending'>Pending</td>");
                }

                html.append("</tr>");
            }
            html.append("</table>");
            html.append("</body></html>");

            // Create a WebView to handle the printing
            android.webkit.WebView webView = new android.webkit.WebView(requireContext());
            webView.setWebViewClient(new android.webkit.WebViewClient() {
                @Override
                public void onPageFinished(android.webkit.WebView view, String url) {
                    createWebPrintJob(view);
                }
            });

            webView.loadDataWithBaseURL(null, html.toString(), "text/HTML", "UTF-8", null);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error creating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void createWebPrintJob(android.webkit.WebView webView) {
        try {
            android.print.PrintManager printManager = (android.print.PrintManager) requireContext()
                    .getSystemService(android.content.Context.PRINT_SERVICE);
            String jobName = "ToDoList_Export_" + System.currentTimeMillis();
            android.print.PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
            printManager.print(jobName, printAdapter, new android.print.PrintAttributes.Builder().build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long getTodayStartTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getTodayEndTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private void updateEmptyState() {
        if (taskList.isEmpty()) {
            if (emptyState != null)
                emptyState.setVisibility(View.VISIBLE);
            if (recyclerTasks != null)
                recyclerTasks.setVisibility(View.GONE);
        } else {
            if (emptyState != null)
                emptyState.setVisibility(View.GONE);
            if (recyclerTasks != null)
                recyclerTasks.setVisibility(View.VISIBLE);
        }
    }
}