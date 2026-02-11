package com.example.todolist;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fabAddTask;
    private LinearLayout navCategoriesContainer;

    private TasksFragment tasksFragment;
    private CalendarFragment calendarFragment;
    private MineFragment mineFragment;

    private DataManager dm;
    private boolean isStarred = false;
    private TaskStorageManager storageManager;
    private android.os.Handler autoSaveHandler;
    private NotificationHelper notificationHelper;

    private static final int NOTIFICATION_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);

        ToDoListApp.showCrashIfPresent(this);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);

        applySystemBars();

        dm = DataManager.getInstance(this);
        storageManager = new TaskStorageManager(this);
        storageManager.checkAndMigrateData(); // Check if migration is needed
        autoSaveHandler = new android.os.Handler();

        notificationHelper = new NotificationHelper(this);

        initViews();
        setupNavigation();
        setupBackPressHandler();
        startAutoSave();
        requestPermissions();

        // Initialize defaults in the background
        new Thread(() -> {
            if (dm != null) {
                dm.initializeDefaultData();
                // Generate today's recurring task instances
                dm.generateTodaysRecurringTasks();
            }
            // Schedule the midnight alarm for daily task generation
            MidnightTaskScheduler scheduler = new MidnightTaskScheduler(MainActivity.this);
            scheduler.scheduleMidnightAlarm();
            refreshNavigationCategories();
        }).start();

        // Load default fragment
        if (savedInstanceState == null) {
            tasksFragment = TasksFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, tasksFragment)
                    .commit();
            if (bottomNavigation != null) {
                bottomNavigation.setSelectedItemId(R.id.nav_tasks);
            }
        }

        // Stop any playing alarm when app launches
        NotificationHelper.stopAlarmSound();

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_add_task", false)) {
            String category = intent.getStringExtra("category_name");
            // Delay slightly to ensure UI is ready
            new android.os.Handler().postDelayed(() -> showAddTaskDialog(category), 300);

            // Clear the extra so it doesn't reopen on rotation
            intent.removeExtra("open_add_task");
        }
    }

    private void applySystemBars() {
        // Ensure screenshot-like system bars: white status bar with dark icons.
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(true);
        }
    }

    private void applySavedTheme() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int mode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private static final int STORAGE_PERMISSION_CODE = 101; // Added Storage Permission Code

    private void requestPermissions() {
        requestNotificationPermission();
        requestStoragePermission();
    }

    private void requestStoragePermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android 11 (API 30) and above
                if (!android.os.Environment.isExternalStorageManager()) {
                    try {
                        Intent intent = new Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.addCategory("android.intent.category.DEFAULT");
                        intent.setData(android.net.Uri
                                .parse(String.format("package:%s", getApplicationContext().getPackageName())));
                        startActivityForResult(intent, STORAGE_PERMISSION_CODE);
                    } catch (Exception e) {
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, STORAGE_PERMISSION_CODE);
                    }
                }
            } else {
                // Below Android 11
                if (checkSelfPermission(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE },
                            STORAGE_PERMISSION_CODE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestNotificationPermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
                            NOTIFICATION_PERMISSION_CODE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Note: Exact alarm permission will be requested when scheduling an alarm
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        fabAddTask = findViewById(R.id.fab_add_task);
        navCategoriesContainer = navView.findViewById(R.id.nav_categories_container);

        fabAddTask.setOnClickListener(v -> handleFabClick());

        // Setup custom nav drawer click listeners
        View navTheme = navView.findViewById(R.id.nav_theme);
        View navWidget = navView.findViewById(R.id.nav_widget);
        if (navTheme != null)
            navTheme.setOnClickListener(v -> {
                showThemeDialog();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        if (navWidget != null)
            navWidget.setOnClickListener(v -> {
                showWidgetInfo();
                drawerLayout.closeDrawer(GravityCompat.START);
            });

        // Keep category counts fresh whenever the drawer is opened.
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                refreshNavigationCategories();
            }
        });

        // Initial population
        refreshNavigationCategories();
    }

    private void handleFabClick() {
        try {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof CalendarFragment) {
                ((CalendarFragment) current).openAddTaskForSelection();
                return;
            }
            if (current instanceof TasksFragment) {
                String cat = ((TasksFragment) current).getSelectedCategory();
                if (cat != null && !cat.equalsIgnoreCase("All")) {
                    showAddTaskDialog(cat);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        showAddTaskDialog();
    }

    public void showAddTaskDialog(String preselectedCategory) {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstanceWithCategory(preselectedCategory);
        bottomSheet.setOnTaskAddedListener(() -> {
            if (tasksFragment != null && tasksFragment.isVisible()) {
                tasksFragment.loadTasks();
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
    }

    private void setupNavigation() {
        // Setup drawer navigation
        if (navView != null) {
            navView.setNavigationItemSelectedListener(this);
        }

        refreshNavigationCategories();

        // Setup bottom navigation
        if (bottomNavigation != null) {
            bottomNavigation.setOnItemSelectedListener(item -> {
                Fragment selectedFragment = null;
                int itemId = item.getItemId();

                if (itemId == R.id.nav_menu) {
                    // Open drawer instead of switching fragment
                    openDrawer();
                    return false; // Don't select this item
                } else if (itemId == R.id.nav_tasks) {
                    if (tasksFragment == null) {
                        tasksFragment = TasksFragment.newInstance();
                    }
                    selectedFragment = tasksFragment;
                } else if (itemId == R.id.nav_calendar) {
                    if (calendarFragment == null) {
                        calendarFragment = CalendarFragment.newInstance();
                    }
                    selectedFragment = calendarFragment;
                } else if (itemId == R.id.nav_mine) {
                    if (mineFragment == null) {
                        mineFragment = MineFragment.newInstance();
                    }
                    selectedFragment = mineFragment;
                }

                if (selectedFragment != null) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                }

                return true;
            });
        }
    }

    public void openTasksFromMine(int filterType) {
        if (filterType == 1) {
            // Open separate Completed Tasks Activity for better view
            Intent intent = new Intent(this, CompletedTasksActivity.class);
            startActivity(intent);
            return;
        }

        if (tasksFragment == null) {
            tasksFragment = TasksFragment.newInstance();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, tasksFragment)
                .commit();
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_tasks);
        }

        // Apply filter after fragment view is created
        getWindow().getDecorView().post(() -> {
            if (filterType == 2) {
                tasksFragment.showOverdueFromMine();
            } else if (filterType == 3) {
                tasksFragment.showRejectedFromMine();
            } else {
                tasksFragment.showPendingFromMine();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Navigation is now handled by custom views
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    public void refreshNavigationCategories() {
        // Run in background to avoid blocking UI thread
        if (dm == null || navCategoriesContainer == null)
            return;

        new Thread(() -> {
            try {
                // Fetch data in background
                List<Category> categories = dm.getAllCategories();
                int starredCount = dm.getStarredTasks().size();

                // Pre-calculate counts map to avoid multiple DB hits inside runOnUiThread
                java.util.Map<String, Integer> categoryCounts = new java.util.HashMap<>();
                int allCount = dm.getPendingTaskCount();

                if (categories != null) {
                    for (Category category : categories) {
                        int count = category.getName().equalsIgnoreCase("All")
                                ? allCount
                                : dm.getPendingCountByCategory(category.getName());
                        categoryCounts.put(category.getName(), count);
                    }
                }

                // Update UI on main thread
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed())
                        return;

                    navCategoriesContainer.removeAllViews();

                    // Add starred item first
                    addNavCategoryItem("⭐", "Starred Tasks", "#FFD700", "star", starredCount, true);

                    // Add section header
                    View headerView = LayoutInflater.from(this).inflate(R.layout.nav_section_header,
                            navCategoriesContainer,
                            false);
                    TextView tvHeader = headerView.findViewById(R.id.tv_section_header);
                    if (tvHeader != null)
                        tvHeader.setText("Categories");
                    navCategoriesContainer.addView(headerView);

                    // Get all categories
                    if (categories != null) {
                        for (Category category : categories) {
                            int count = categoryCounts.getOrDefault(category.getName(), 0);

                            String iconName = category.getIcon() != null ? category.getIcon() : "folder";
                            String color = category.getColor() != null ? category.getColor() : "#2196F3";

                            addNavCategoryItem(iconName, category.getName(), color, iconName, count, false);
                        }
                    }

                    // Add "Create New" item
                    addCreateNewCategoryItem();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void addNavCategoryItem(String iconType, String name, String color, String iconName, int count,
            boolean isStarredItem) {
        try {
            View itemView = LayoutInflater.from(this).inflate(R.layout.nav_category_item, navCategoriesContainer,
                    false);

            CardView iconContainer = itemView.findViewById(R.id.icon_container);
            ImageView ivIcon = itemView.findViewById(R.id.iv_category_icon);
            TextView tvName = itemView.findViewById(R.id.tv_category_name);
            TextView tvCount = itemView.findViewById(R.id.tv_count);

            tvName.setText(name);
            tvCount.setText(String.valueOf(count));

            // Set icon
            int iconRes = getIconResourceForName(iconName);
            ivIcon.setImageResource(iconRes);

            // Set colors - create beautiful pastel background
            try {
                int colorInt = android.graphics.Color.parseColor(color);
                // Create lighter pastel version for background
                int r = (android.graphics.Color.red(colorInt) + 255 * 2) / 3;
                int g = (android.graphics.Color.green(colorInt) + 255 * 2) / 3;
                int b = (android.graphics.Color.blue(colorInt) + 255 * 2) / 3;
                int pastelColor = android.graphics.Color.rgb(r, g, b);

                iconContainer.setCardBackgroundColor(pastelColor);
                ivIcon.setColorFilter(colorInt);
            } catch (Exception e) {
                iconContainer.setCardBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"));
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#2196F3"));
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (isStarredItem) {
                    loadStarredTasks();
                } else {
                    loadTasksByCategory(name);
                }
                drawerLayout.closeDrawer(GravityCompat.START);
            });

            navCategoriesContainer.addView(itemView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addCreateNewCategoryItem() {
        try {
            View itemView = LayoutInflater.from(this).inflate(R.layout.nav_category_item, navCategoriesContainer,
                    false);

            CardView iconContainer = itemView.findViewById(R.id.icon_container);
            ImageView ivIcon = itemView.findViewById(R.id.iv_category_icon);
            TextView tvName = itemView.findViewById(R.id.tv_category_name);
            TextView tvCount = itemView.findViewById(R.id.tv_count);

            tvName.setText("Create New");
            tvCount.setVisibility(View.GONE);

            ivIcon.setImageResource(R.drawable.ic_add_blue);
            iconContainer.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"));
            ivIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"));

            itemView.setOnClickListener(v -> {
                showCreateCategoryDialog();
                drawerLayout.closeDrawer(GravityCompat.START);
            });

            navCategoriesContainer.addView(itemView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadStarredTasks() {
        if (tasksFragment == null) {
            tasksFragment = TasksFragment.newInstance();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, tasksFragment)
                .commit();
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_tasks);
        }
        if (tasksFragment.getView() != null) {
            tasksFragment.getView().post(() -> tasksFragment.loadStarredTasks());
        }
    }

    private void loadTasksByCategory(String category) {
        if (tasksFragment == null) {
            tasksFragment = TasksFragment.newInstance();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, tasksFragment)
                .commit();
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_tasks);
        }
        if (tasksFragment.getView() != null) {
            tasksFragment.getView().post(() -> tasksFragment.loadTasksByCategory(category));
        }
    }

    private int getIconResourceForName(String iconName) {
        if (iconName == null)
            iconName = "folder";
        switch (iconName.toLowerCase()) {
            case "folder":
                return R.drawable.ic_folder;
            case "profile":
                return R.drawable.ic_profile;
            case "star":
                return R.drawable.ic_star;
            case "calendar":
                return R.drawable.ic_calendar;
            case "fire":
                return R.drawable.ic_fire;
            case "list":
                return R.drawable.ic_list;
            default:
                return R.drawable.ic_folder;
        }
    }

    private void showCreateCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Category");

        final EditText input = new EditText(this);
        input.setHint("Category name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String categoryName = input.getText().toString().trim();
            if (!categoryName.isEmpty()) {
                // Create new category
                // Assign a random pastel color or rotate through a palette
                String[] colors = { "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4",
                        "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
                        "#FF5722" };
                int randomIndex = (int) (Math.random() * colors.length);
                String color = colors[randomIndex];

                Category newCategory = new Category(categoryName, color, 0);
                dm.insertCategory(newCategory);

                refreshNavigationCategories();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    public void showAddTaskDialog() {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstance();
        bottomSheet.setOnTaskAddedListener(() -> {
            // Refresh the tasks list
            if (tasksFragment != null && tasksFragment.isVisible()) {
                tasksFragment.loadTasks();
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
    }

    public void openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    private void showThemeDialog() {
        Intent intent = new Intent(this, ThemeSelectionActivity.class);
        startActivity(intent);
    }

    private void applyBackground() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            String bg = prefs.getString("app_background", "");
            View root = findViewById(R.id.drawer_layout); // Apply to root drawer

            if (bg.isEmpty()) {
                root.setBackgroundResource(R.color.white); // Default
                return;
            }

            if (bg.startsWith("color:")) {
                root.setBackgroundColor(android.graphics.Color.parseColor(bg.substring(6)));
            } else if (bg.startsWith("gradient:")) {
                String[] colors = bg.substring(9).split(",");
                int[] colorInts = new int[colors.length];
                for (int i = 0; i < colors.length; i++)
                    colorInts[i] = android.graphics.Color.parseColor(colors[i]);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colorInts);
                root.setBackground(gd);
            } else if (bg.startsWith("uri:")) {
                try {
                    android.net.Uri uri = android.net.Uri.parse(bg.substring(4));
                    // Use input stream to avoid permission issues if persisted improperly
                    java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                    android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(
                            getResources(), bitmap);
                    drawable.setGravity(android.view.Gravity.CENTER);
                    // Scale mode? Center Crop style equivalent
                    root.setBackground(drawable);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Fallback
                    root.setBackgroundResource(R.color.white);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBackground();
        if (dm != null) {
            // Run heavy recurrence checks in background
            new Thread(() -> {
                dm.checkAndHandleMissedRecurrences();
                // Also ensure today's recurring tasks are generated
                dm.generateTodaysRecurringTasks();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    // Refresh fragments if visible
                    if (tasksFragment != null && tasksFragment.isVisible()) {
                        tasksFragment.loadTasks();
                    }
                });
            }).start();
        }
    }

    private void showWidgetInfo() {
        Intent intent = new Intent(this, WidgetInfoActivity.class);
        startActivity(intent);
    }

    private void showFAQ() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("FAQ");
        builder.setMessage("Q: How do I create a task?\nA: Tap the + button at the bottom right.\n\n" +
                "Q: How do I mark a task as important?\nA: Tap the star icon when creating or editing a task.\n\n" +
                "Q: How do I delete a task?\nA: Long press on a task to delete it.");
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showFeedbackDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send Feedback");

        final EditText input = new EditText(this);
        input.setHint("Share your thoughts or report issues...");
        input.setMinLines(3);
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String feedback = input.getText().toString().trim();
            if (!feedback.isEmpty()) {
                // Handle feedback submission
                // Could send email or save to database
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showFollowUsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow Us");
        builder.setMessage("Stay connected with us on social media for updates and tips!");

        String[] options = { "Facebook", "Twitter", "Instagram", "Website" };
        builder.setItems(options, (dialog, which) -> {
            // Handle social media links
            // Could open URLs in browser
        });

        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void startAutoSave() {
        // Auto-save every 30 minutes
        Runnable autoSaveRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> storageManager.createBackup()).start();
                autoSaveHandler.postDelayed(this, 30 * 60 * 1000); // 30 minutes
            }
        };
        autoSaveHandler.postDelayed(autoSaveRunnable, 30 * 60 * 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Silent export only if data changed
        if (dm != null && dm.isDataDirty()) {
            new Thread(() -> storageManager.exportAllTasks(false)).start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveHandler != null) {
            autoSaveHandler.removeCallbacksAndMessages(null);
        }
    }
}
