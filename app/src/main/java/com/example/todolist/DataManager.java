package com.example.todolist;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Data manager with Room persistence and sample data generation.
 */
public class DataManager {
    private static DataManager instance;

    // In-memory cache for synchronous access if needed, but we should rely on DB
    // where possible.
    // However, keeping these lists updated ensures minimal changes to existing
    // adapters that might reference these list objects (if any).
    // Actually, most adapters call getAllTasks(), so they get a fresh list.
    // We will keep maintaining these lists to avoid breakage in complex UI flows
    // that might expect object reference equality in the short term.
    private final ArrayList<TaskList> tasks = new ArrayList<>();
    private final ArrayList<Category> categories = new ArrayList<>();
    private final ArrayList<SubTask> subTasks = new ArrayList<>();
    private final ArrayList<TaskTemplate> templates = new ArrayList<>();

    private AppDatabase db;
    private Context context;
    private TaskStorageManager storageManager; // Helper for migration only

    private DataManager(Context context) {
        this.context = context;
        db = AppDatabase.getDatabase(context);
        storageManager = new TaskStorageManager(context);
        initializeData();
    }

    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context.getApplicationContext());
        }
        return instance;
    }

    private boolean isDirty = false;

    // ==================== INITIALIZATION ====================

    public synchronized void initializeData() {
        // 1. Load Categories
        categories.clear();
        categories.addAll(db.categoryDao().getAllCategories());

        if (categories.isEmpty()) {
            // Attempt migration from JSON
            List<Category> jsonCats = storageManager.loadCategories();
            if (!jsonCats.isEmpty()) {
                for (Category c : jsonCats) {
                    // Reset ID to let AutoGenerate handle it, or keep if we want to preserve IDs?
                    // Preserving IDs is risky if they conflict, but since table is empty, it's
                    // fine.
                    // However, we should let DB handle IDs to be safe.
                    // But if we reset IDs, we break relationships?
                    // Categories don't have relationships pointing to them by ID (Tasks use
                    // category NAME string).
                    // So we can let DB generate IDs.
                    c.id = 0;
                    db.categoryDao().insert(c);
                }
                categories.addAll(db.categoryDao().getAllCategories());
            } else {
                // Defaults
                insertCategory(new Category("No Category", "#9BA5B0", 1));
                insertCategory(new Category("Work", "#5B9BD5", 1));
                insertCategory(new Category("Personal", "#7CB342", 1));
                insertCategory(new Category("Wishlist", "#FF7043", 1));
                insertCategory(new Category("Birthday", "#AB47BC", 1));
                insertCategory(new Category("Health", "#EC407A", 1));
                insertCategory(new Category("Study", "#5C6BC0", 1));
            }
        }

        // 2. Load Tasks
        tasks.clear();
        tasks.addAll(db.taskDao().getAllTasks());

        if (tasks.isEmpty()) {
            // Attempt migration
            List<TaskList> jsonTasks = storageManager.loadTasks();
            if (!jsonTasks.isEmpty()) {
                // We must preserve IDs if possible because SubTasks point to them.
                // Or we migrate SubTasks intelligently.
                // Let's try to insert assuming IDs are safe (DB is empty).
                // Actually, jsonTasks IDs are integers.

                // Map old ID to new ID if we wanted to be perfectly safe, but since DB is
                // empty,
                // we can insert with specific IDs if we remove 'autoGenerate' or simple insert.
                // Room allows inserting object with ID if it doesn't conflict.

                for (TaskList t : jsonTasks) {
                    // Check if SubTasks exist for this task
                    // We need to handle this carefully.
                    long newId = db.taskDao().insert(t); // if t.id is set and not 0, Room tries to use it.
                }

                // Reload to get properly saved tasks
                tasks.addAll(db.taskDao().getAllTasks());

                // Migrate SubTasks
                List<SubTask> jsonSub = storageManager.loadSubTasks();
                for (SubTask st : jsonSub) {
                    db.subTaskDao().insert(st);
                }
            } else {
                // Only generate sample data on FIRST RUN ever
                android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs",
                        Context.MODE_PRIVATE);
                boolean isFirstRun = prefs.getBoolean("is_first_run", true);
                if (isFirstRun) {
                    generateSampleData();
                    prefs.edit().putBoolean("is_first_run", false).apply();
                }
            }
        } else {
            // Data exists, so this is definitely not the first run.
            // Mark it as such to prevent future sample generation if user clears all tasks.
            android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("is_first_run", true)) {
                prefs.edit().putBoolean("is_first_run", false).apply();
            }
        }

        // 3. Load SubTasks
        subTasks.clear();
        // Since we don't have a specific getAllSubTasks() because it's usually by
        // Parent,
        // we can iterate tasks or just leave this empty and query on demand.
        // But to keep 'subTasks' list populated if needed:
        // We really should query these on demand.
        // But for compatibility with existing code that might access 'subTasks'
        // directly?
        // Let's assuming getSubTasksForTask is the main access point.

        // Templates (In-memory only usually, or we can persist)
        if (templates.isEmpty()) {
            insertTemplate(new TaskTemplate("Drink water, keep healthy", "🥤", "Health", 0, 1));
            insertTemplate(new TaskTemplate("Brush teeth", "🦷", "Health", 1, 0));
            insertTemplate(new TaskTemplate("Take a shower", "🚿", "Health", 1, 0));
            insertTemplate(new TaskTemplate("Go to bed early", "🌙", "Health", 0, 1));
            insertTemplate(new TaskTemplate("Get up early", "🌅", "Health", 0, 0));
            insertTemplate(new TaskTemplate("Shopping", "🛒", "Life", 0, 1));
            insertTemplate(new TaskTemplate("Pay bills", "💳", "Life", 0, 0));
            insertTemplate(new TaskTemplate("Team meeting", "👥", "Work", 0, 1));
            insertTemplate(new TaskTemplate("Read a book", "📚", "Study", 0, 0));
        }

        checkAndHandleMissedRecurrences();
    }

    private void generateSampleData() {
        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;

        // 1. Overdue Tasks (Past 1-3 days)
        if (db.taskDao().getAllTasks().isEmpty()) {
            insertTask(createSampleTask("Complete Project Proposal", "Work", now - (2 * dayMs), 0, true));
            insertTask(createSampleTask("Pay Electricity Bill", "Personal", now - (1 * dayMs), 0, true));
            insertTask(createSampleTask("Return Library Book", "Study", now - (3 * dayMs), 0, false));

            // 2. Completed Tasks
            insertTask(createSampleTask("Weekly Team Sync", "Work", now - (1 * dayMs), 1, true));
            insertTask(createSampleTask("Grocery Shopping", "Personal", now - (2 * dayMs), 1, false));
            insertTask(createSampleTask("Gym Workout", "Health", now - (3 * dayMs), 1, false));
            insertTask(createSampleTask("Read Chapter 4", "Study", now - (4 * dayMs), 1, false));
            insertTask(createSampleTask("Call Parents", "Personal", now - (2 * dayMs), 1, true));
            insertTask(createSampleTask("Submit Expense Report", "Work", now - (5 * dayMs), 1, false));

            // 3. Upcoming Tasks
            insertTask(createSampleTask("Review Design Mockups", "Work", now + (2 * 60 * 60 * 1000), 0, true));
            insertTask(createSampleTask("Buy Birthday Gift", "Birthday", now + (1 * dayMs), 0, false));
            insertTask(createSampleTask("Dentist Appointment", "Health", now + (2 * dayMs), 0, false));
            insertTask(createSampleTask("Car Service", "Personal", now + (4 * dayMs), 0, false));
            insertTask(createSampleTask("Plan Weekend Trip", "Wishlist", now + (5 * dayMs), 0, false));
            insertTask(createSampleTask("Update Resume", "Work", now + (6 * dayMs), 0, true));
            insertTask(createSampleTask("Yoga Class", "Health", now + (dayMs / 2), 0, false));
        }
    }

    private TaskList createSampleTask(String title, String category, long dueDate, int status, boolean starred) {
        TaskList t = new TaskList();
        t.setTask(title);
        t.setCategory(category);
        t.setDueDate(dueDate);
        t.setStatus(status);
        t.setStarred(starred ? 1 : 0);

        if (status == 1) {
            t.setCompletedAt(dueDate + (1000 * 60 * 60));
        } else {
            t.setCompletedAt(0);
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
        t.setTime(dueDate > 0 ? sdf.format(new java.util.Date(dueDate)) : "");

        return t;
    }

    // ==================== TASK OPERATIONS ====================

    public synchronized List<TaskList> getAllTasks() {
        tasks.clear();
        tasks.addAll(db.taskDao().getAllTasks());
        return new ArrayList<>(tasks);
    }

    public synchronized List<TaskList> getTasksByStatus(int status) {
        return db.taskDao().getTasksByStatus(status);
    }

    public synchronized List<TaskList> getTasksByCategory(String category) {
        return db.taskDao().getTasksByCategory(category);
    }

    public synchronized List<TaskList> getTasksByStatusAndCategory(int status, String category) {
        // Complex query, implementing via filtering for now, or could add DAO method.
        // Keeping Java stream logic for simplicity if complex conditions
        List<TaskList> all = getAllTasks();
        ArrayList<TaskList> result = new ArrayList<>();
        boolean isNoCategory = "No Category".equalsIgnoreCase(category);
        for (TaskList t : all) {
            boolean match;
            if (isNoCategory) {
                match = (t.category == null || t.category.trim().isEmpty()
                        || "No Category".equalsIgnoreCase(t.category));
            } else {
                match = category.equalsIgnoreCase(t.category);
            }
            if (t.check == status && match)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getStarredTasks() {
        List<TaskList> all = getAllTasks();
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : all) {
            if (t.isStarred == 1)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getCompletedTasks() {
        return getTasksByStatus(1);
    }

    public synchronized TaskList getTaskById(int id) {
        return db.taskDao().getTaskById(id);
    }

    // Keeping memory-based filtering for date ranges to avoid complex SQL queries
    // in DAO for now,
    // unless performance is critical.
    public synchronized List<TaskList> getTasksByDueDate(long startOfDay, long endOfDay) {
        List<TaskList> all = getAllTasks();
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : all) {
            if (t.dueDate >= startOfDay && t.dueDate <= endOfDay)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getTasksByDateRange(long start, long end) {
        List<TaskList> all = getAllTasks();
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : all) {
            if (t.dueDate >= start && t.dueDate < end)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getTasksByDateRangeAndSource(long start, long end, String source) {
        List<TaskList> all = getAllTasks();
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : all) {
            if (t.dueDate >= start && t.dueDate < end && source.equals(t.createdFrom)) {
                result.add(t);
            }
        }
        return result;
    }

    public synchronized List<TaskList> getPendingTasksDueBetween(long start, long end) {
        List<TaskList> all = getAllTasks();
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : all) {
            if (t.check == 0 && t.dueDate >= start && t.dueDate <= end)
                result.add(t);
        }
        return result;
    }

    public synchronized int getCompletedTaskCount() {
        return getTasksByStatus(1).size();
    }

    public synchronized int getPendingTaskCount() {
        return db.taskDao().getPendingTaskCount();
    }

    public synchronized int getTaskCountByCategory(String category) {
        return getTasksByCategory(category).size();
    }

    public synchronized int getPendingCountByCategory(String category) {
        if ("No Category".equalsIgnoreCase(category)) {
            return db.taskDao().getPendingTaskCountNoCategory();
        }
        return db.taskDao().getPendingTaskCountByCategory(category);
    }

    public synchronized long insertTask(TaskList task) {
        long id = db.taskDao().insert(task); // Returns rowId (which is id)
        task.id = (int) id;
        tasks.add(task); // Update cache
        isDirty = true;
        sendWidgetUpdate();
        return id;
    }

    public synchronized long insertAndGetId(TaskList task) {
        return insertTask(task);
    }

    public synchronized void updateTask(TaskList task) {
        db.taskDao().update(task);
        // Update cache
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id == task.id) {
                tasks.set(i, task);
                break;
            }
        }
        isDirty = true;
        sendWidgetUpdate();
    }

    public synchronized void updateStatusAndCompletedAt(int id, int status, long completedAt) {
        TaskList task = getTaskById(id);
        if (task != null) {
            if (status == 1 && task.check == 0) {
                handleRecurrence(task);
            }
            task.check = status;
            task.completedAt = completedAt;
            updateTask(task);

            if (status == 1) {
                NotificationHelper helper = new NotificationHelper(context);
                helper.cancelReminders(task.id);
            }
            sendWidgetUpdate();
        }
    }

    private void handleRecurrence(TaskList task) {
        if (task.repeatType == null || "none".equals(task.repeatType))
            return;

        long nextDueDate = calculateSingleNextDueDate(task, task.dueDate);
        if (nextDueDate <= 0)
            return;

        TaskList newTask = new TaskList();
        newTask.setTask(task.getTask());
        newTask.setCategory(task.getCategory());
        newTask.setTime(task.getTime());
        newTask.setDueDate(nextDueDate);
        newTask.setTaskTime(task.getTaskTime());
        newTask.setRepeatType(task.getRepeatType());
        newTask.setRepeatInterval(task.getRepeatInterval());
        newTask.setRepeatDays(task.getRepeatDays());
        newTask.setReminderMinutes(task.getReminderMinutes());
        newTask.setUseAlarm(task.getUseAlarm());
        newTask.setScreenLock(task.getScreenLock());
        newTask.setStarred(task.getIsStarred());
        newTask.markerType = task.markerType;
        newTask.markerValue = task.markerValue;
        newTask.markerColor = task.markerColor;
        newTask.setAttachments(task.getAttachments());
        newTask.setCreatedFrom(task.getCreatedFrom());
        newTask.setStatus(0);
        newTask.setCompletedAt(0);

        // Link to the parent recurring task
        if (task.recurringParentId > 0) {
            newTask.recurringParentId = task.recurringParentId;
        } else {
            newTask.recurringParentId = task.id;
        }

        long newId = insertTask(newTask);

        if ((newTask.getUseAlarm() == 1
                || (newTask.getReminderMinutes() != null && !newTask.getReminderMinutes().isEmpty()))
                && newTask.getDueDate() > System.currentTimeMillis()) {
            NotificationHelper helper = new NotificationHelper(context);
            helper.scheduleReminders(
                    (int) newId,
                    newTask.getTask(),
                    newTask.getDueDate(),
                    newTask.getTaskTime(),
                    newTask.getReminderMinutes(),
                    newTask.getUseAlarm() == 1,
                    newTask.getScreenLock() == 1);
        }
    }

    private long calculateNextDueDate(TaskList task) {
        // Keeps compatibility for other calls if any, but forwards to single step from
        // current due date
        return calculateSingleNextDueDate(task, task.dueDate);
    }

    private long calculateSingleNextDueDate(TaskList task, long fromDate) {
        if (fromDate == 0)
            return 0;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(fromDate);

        switch (task.repeatType) {
            case "days":
                cal.add(java.util.Calendar.DAY_OF_YEAR, task.repeatInterval);
                break;
            case "weeks":
                cal.add(java.util.Calendar.WEEK_OF_YEAR, task.repeatInterval);
                break;
            case "months":
                cal.add(java.util.Calendar.MONTH, task.repeatInterval);
                break;
            case "years":
                cal.add(java.util.Calendar.YEAR, task.repeatInterval);
                break;
            case "custom_days":
                // Advance day-by-day until we hit a matching day-of-week
                if (task.repeatDays != null && !task.repeatDays.isEmpty()) {
                    java.util.Set<Integer> allowedDays = new java.util.HashSet<>();
                    for (String d : task.repeatDays.split(",")) {
                        try {
                            allowedDays.add(Integer.parseInt(d.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (allowedDays.isEmpty())
                        return 0;
                    // Advance at least 1 day, then find next matching day
                    for (int attempt = 0; attempt < 8; attempt++) {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                        if (allowedDays.contains(cal.get(java.util.Calendar.DAY_OF_WEEK))) {
                            return cal.getTimeInMillis();
                        }
                    }
                    return 0; // No matching day found
                }
                return 0;
            default:
                return 0;
        }
        return cal.getTimeInMillis();
    }

    // RESTORED METHODS START
    public synchronized void updateStarred(int id, int starred) {
        TaskList t = getTaskById(id);
        if (t != null) {
            t.isStarred = starred;
            updateTask(t);
        }
    }

    public synchronized void deleteTask(TaskList task) {
        db.taskDao().delete(task);
        tasks.removeIf(t -> t.id == task.id);
        isDirty = true;
        sendWidgetUpdate();
    }

    public synchronized void deleteTaskById(int id) {
        db.taskDao().deleteById(id);
        tasks.removeIf(t -> t.id == id);
        isDirty = true;
    }

    // ==================== CATEGORY OPERATIONS ====================

    public synchronized List<Category> getAllCategories() {
        categories.clear();
        categories.addAll(db.categoryDao().getAllCategories());
        return new ArrayList<>(categories);
    }

    public synchronized void insertCategory(Category category) {
        long id = db.categoryDao().insert(category);
        category.id = (int) id;
        categories.add(category);
        isDirty = true;
    }

    public synchronized void updateCategory(Category category) {
        db.categoryDao().update(category);
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == category.id) {
                categories.set(i, category);
                break;
            }
        }
        isDirty = true;
    }

    public synchronized void deleteCategory(Category category) {
        db.categoryDao().delete(category);
        categories.removeIf(c -> c.id == category.id);
        isDirty = true;
    }

    public synchronized void deleteCategory(int categoryId) {
        db.categoryDao().deleteById(categoryId);
        categories.removeIf(c -> c.id == categoryId);
        isDirty = true;
    }

    // ==================== SUBTASK OPERATIONS ====================

    public synchronized List<SubTask> getSubTasksForTask(int taskId) {
        return db.subTaskDao().getSubTasksForTask(taskId);
    }

    public synchronized void insertSubTask(SubTask subTask) {
        long id = db.subTaskDao().insert(subTask);
        subTask.id = (int) id;
        subTasks.add(subTask); // Optional cache update
        isDirty = true;
    }

    public synchronized void updateSubTask(SubTask subTask) {
        db.subTaskDao().update(subTask);
        isDirty = true;
    }

    public synchronized void deleteSubTask(SubTask subTask) {
        db.subTaskDao().delete(subTask);
        isDirty = true;
    }

    public synchronized void deleteSubTask(int subTaskId) {
        db.subTaskDao().deleteById(subTaskId);
        isDirty = true;
    }

    // ==================== TEMPLATES ====================
    // Keep internal for now unless persisted
    public synchronized List<TaskTemplate> getAllTemplates() {
        return new ArrayList<>(templates);
    }

    public synchronized void insertTemplate(TaskTemplate template) {
        templates.add(template);
    }

    public synchronized int getTemplateCount() {
        return templates.size();
    }

    public synchronized List<TaskTemplate> getTemplatesByCategory(String category) {
        ArrayList<TaskTemplate> result = new ArrayList<>();
        for (TaskTemplate t : templates) {
            if (category.equals(t.category))
                result.add(t);
        }
        return result;
    }

    // ==================== STATISTICS ====================
    // Keep Java logic using getTasksBy... methods

    public synchronized List<CategoryCount> getPendingCountsByCategoryBetween(long start, long end) {
        ArrayList<CategoryCount> result = new ArrayList<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        List<TaskList> all = getAllTasks();

        for (TaskList t : all) {
            if (t.check == 0 && t.dueDate >= start && t.dueDate <= end) {
                String cat = (t.category != null && !t.category.trim().isEmpty()) ? t.category : "No Category";
                if ("No Category".equalsIgnoreCase(cat))
                    cat = "No Category";
                counts.put(cat, counts.getOrDefault(cat, 0) + 1);
            }
        }
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            result.add(new CategoryCount(e.getKey(), e.getValue()));
        }
        return result;
    }

    public synchronized List<DayCount> getCompletedCountsByDayOfWeek(long weekStart, long weekEnd) {
        ArrayList<DayCount> result = new ArrayList<>();
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        List<TaskList> all = getAllTasks(); // Refresh

        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (TaskList t : all) {
            if (t.check == 1 && t.completedAt >= weekStart && t.completedAt <= weekEnd) {
                cal.setTimeInMillis(t.completedAt);
                int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
                counts.put(dayOfWeek, counts.getOrDefault(dayOfWeek, 0) + 1);
            }
        }
        for (java.util.Map.Entry<Integer, Integer> e : counts.entrySet()) {
            result.add(new DayCount(e.getKey(), e.getValue()));
        }
        return result;
    }

    public synchronized void initializeDefaultData() {
        initializeData();
    }

    public synchronized int getAllTasksCount() {
        return db.taskDao().getAllTasks().size();
        // or return tasks.size();
    }

    public synchronized int getCategoryCount() {
        return categories.size();
    }
    // RESTORED METHODS END

    public void checkAndHandleMissedRecurrences() {
        // Helper to check overdue recurring tasks and mark them missed, creating
        // subsequent tasks
        // NOT synchronized to avoid blocking UI thread for long duration
        List<TaskList> all = getAllTasks(); // Internal call, synchronized

        long todayStart = 0;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        todayStart = cal.getTimeInMillis();

        List<TaskList> newTasks = new ArrayList<>();
        boolean changed = false;

        for (TaskList task : all) {
            boolean isRecurring = task.repeatType != null && !task.repeatType.equals("none");

            // Look for pending tasks that are overdue
            if (task.check == 0 && isRecurring) {
                if (task.dueDate > 0 && task.dueDate < todayStart) {

                    // Atomic check and update to prevent race conditions (e.g. user completes task
                    // while we process)
                    boolean proceed = false;
                    synchronized (this) {
                        TaskList current = getTaskById(task.id);
                        if (current != null && current.check == 0) {
                            current.check = 2; // Missed
                            updateTask(current);
                            proceed = true;
                            changed = true;
                        }
                    }

                    if (proceed) {
                        // Now fill the gap between that task's due date and today
                        long currentProcessingDate = task.dueDate;

                        // Safety break to prevent infinite loops (e.g. max 2000 instances)
                        int iterations = 0;

                        while (iterations < 2000) {
                            long nextDate = calculateSingleNextDueDate(task, currentProcessingDate);

                            if (nextDate <= 0)
                                break; // Error or invalid
                            if (nextDate >= todayStart) {
                                // This instance falls on (or after) today. Create it as PENDING.
                                TaskList nextTask = createNextTaskInstance(task, nextDate, 0);
                                newTasks.add(nextTask);
                                break; // Done filling gaps
                            } else {
                                // This instance is ALSO in the past. Create it as MISSED.
                                TaskList missedTask = createNextTaskInstance(task, nextDate, 2);
                                // We insert directly or add to a list to insert?
                                // Better to add to list to batch, but we need IDs?
                                // Actually, insertTask is synchronized. Safe.
                                // However, we are iterating 'all' which is a copy, so safe to modify DB.
                                // But we are in a loop for ONE original task.
                                // Let's add to 'newTasks' list, but wait..
                                // If we add to 'newTasks', the loop below will insert them.
                                // But for "missed" ones, we might want to insert them now or just add to list.
                                // The problem is createNextTaskInstance returns an object.
                                newTasks.add(missedTask);
                            }

                            currentProcessingDate = nextDate;
                            iterations++;
                        }
                    }
                }
            }
        }

        if (!newTasks.isEmpty()) {
            for (TaskList t : newTasks) {
                long newId = insertTask(t); // Synchronized

                // Schedule reminders ONLY for Pending tasks (Status 0) that are in future/today
                if (t.check == 0
                        && (t.getUseAlarm() == 1
                                || (t.getReminderMinutes() != null && !t.getReminderMinutes().isEmpty()))
                        && t.getDueDate() > System.currentTimeMillis()) {
                    NotificationHelper helper = new NotificationHelper(context);
                    helper.scheduleReminders(
                            (int) newId,
                            t.getTask(),
                            t.getDueDate(),
                            t.getTaskTime(),
                            t.getReminderMinutes(),
                            t.getUseAlarm() == 1,
                            t.getScreenLock() == 1);
                }
            }
        }
    }

    /**
     * Generate today's recurring task instances proactively.
     * This is called at midnight (via MidnightTaskScheduler) and on app startup.
     * It creates actual task entries for today based on recurring task patterns.
     * 
     * Logic:
     * 1. Find all "parent" recurring tasks (recurringParentId == 0 and has
     * recurrence)
     * 2. For each parent, check if today should have a task instance
     * 3. If no instance exists for today (by checking recurringParentId and date),
     * create one
     */
    public void generateTodaysRecurringTasks() {
        // NOT synchronized to avoid blocking UI
        List<TaskList> all = getAllTasks(); // Synchronized

        // Calculate today's date range
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();

        cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        long tomorrowStart = cal.getTimeInMillis();

        List<TaskList> tasksToCreate = new ArrayList<>();

        // Build a set of parent IDs that already have a task for today
        java.util.Set<Integer> parentsWithTodayTasks = new java.util.HashSet<>();

        // Also collect parent recurring tasks
        java.util.List<TaskList> parentRecurringTasks = new ArrayList<>();

        for (TaskList task : all) {
            boolean isRecurring = task.repeatType != null && !task.repeatType.equals("none");

            // If this task is due today, mark its parent as having a task for today
            if (task.dueDate >= todayStart && task.dueDate < tomorrowStart) {
                if (task.recurringParentId > 0) {
                    parentsWithTodayTasks.add(task.recurringParentId);
                } else if (isRecurring) {
                    // This is a parent task that's due today
                    parentsWithTodayTasks.add(task.id);
                }
            }

            // Collect parent recurring tasks (recurringParentId == 0 and has recurrence)
            if (isRecurring && task.recurringParentId == 0) {
                parentRecurringTasks.add(task);
            }
        }

        // For each parent recurring task, check if we need to create today's instance
        for (TaskList parentTask : parentRecurringTasks) {
            // Skip if there's already a task for today from this parent
            if (parentsWithTodayTasks.contains(parentTask.id)) {
                continue;
            }

            // Check if this recurring task should have an instance today
            if (shouldTaskOccurOnDate(parentTask, todayStart)) {
                // Create a new task instance for today
                long todayDueDate = calculateTodayDueDate(parentTask, todayStart);
                TaskList newTask = createNextTaskInstance(parentTask, todayDueDate, 0);
                newTask.recurringParentId = parentTask.id; // Link to parent
                tasksToCreate.add(newTask);

                android.util.Log.d("DataManager", "Creating recurring instance for: " + parentTask.task + " on today");
            }
        }

        // Insert all new task instances
        for (TaskList t : tasksToCreate) {
            long newId = insertTask(t); // Synchronized

            // Schedule reminders for tasks with time in the future
            if ((t.getUseAlarm() == 1 || (t.getReminderMinutes() != null && !t.getReminderMinutes().isEmpty()))
                    && t.getDueDate() > System.currentTimeMillis()) {
                NotificationHelper helper = new NotificationHelper(context);
                helper.scheduleReminders(
                        (int) newId,
                        t.getTask(),
                        t.getDueDate(),
                        t.getTaskTime(),
                        t.getReminderMinutes(),
                        t.getUseAlarm() == 1,
                        t.getScreenLock() == 1);
            }
        }

        if (!tasksToCreate.isEmpty()) {
            android.util.Log.d("DataManager",
                    "Generated " + tasksToCreate.size() + " recurring task instances for today");
        }
    }

    public synchronized boolean isDataDirty() {
        return isDirty;
    }

    public synchronized void clearDataDirty() {
        isDirty = false;
    }

    /**
     * Check if a recurring task should occur on the given date
     */
    private boolean shouldTaskOccurOnDate(TaskList task, long targetDate) {
        if (task.dueDate == 0)
            return false;
        if (task.repeatType == null || "none".equals(task.repeatType))
            return false;

        java.util.Calendar targetCal = java.util.Calendar.getInstance();
        targetCal.setTimeInMillis(targetDate);
        int targetDayOfWeek = targetCal.get(java.util.Calendar.DAY_OF_WEEK);

        java.util.Calendar taskCal = java.util.Calendar.getInstance();
        taskCal.setTimeInMillis(task.dueDate);

        // For custom weekly recurrence with specific days
        if ("days".equals(task.repeatType) && task.repeatInterval == 1) {
            // Daily recurrence - always occurs
            // But check if it started (task.dueDate <= targetDate)
            return task.dueDate <= targetDate + (24 * 60 * 60 * 1000);
        }

        // Check repeatDays for custom weekly patterns
        String repeatDays = task.getRepeatDays();
        if (repeatDays != null && !repeatDays.isEmpty()) {
            // repeatDays format: "1,2,3,4,5,6,7" where 1=Sunday, 7=Saturday
            String[] days = repeatDays.split(",");
            for (String day : days) {
                try {
                    int dayNum = Integer.parseInt(day.trim());
                    if (dayNum == targetDayOfWeek) {
                        // Check if this is on or after the original start date
                        return task.dueDate <= targetDate + (24 * 60 * 60 * 1000);
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid day numbers
                }
            }
            return false;
        }

        // For simple recurrence patterns (days, weeks, months, years)
        // Check if target date is a valid recurrence of the original date
        switch (task.repeatType) {
            case "days":
                // Check if (targetDate - originalDate) is a multiple of (interval * day)
                long daysDiff = (targetDate - normalizeToStartOfDay(task.dueDate)) / (24 * 60 * 60 * 1000);
                return daysDiff >= 0 && daysDiff % task.repeatInterval == 0;

            case "weeks":
                // Must be same day of week and correct week interval
                if (taskCal.get(java.util.Calendar.DAY_OF_WEEK) != targetDayOfWeek)
                    return false;
                long weeksDiff = (targetDate - normalizeToStartOfDay(task.dueDate)) / (7 * 24 * 60 * 60 * 1000);
                return weeksDiff >= 0 && weeksDiff % task.repeatInterval == 0;

            case "months":
                // Same day of month and correct month interval
                if (taskCal.get(java.util.Calendar.DAY_OF_MONTH) != targetCal.get(java.util.Calendar.DAY_OF_MONTH))
                    return false;
                int monthsDiff = (targetCal.get(java.util.Calendar.YEAR) - taskCal.get(java.util.Calendar.YEAR)) * 12
                        + (targetCal.get(java.util.Calendar.MONTH) - taskCal.get(java.util.Calendar.MONTH));
                return monthsDiff >= 0 && monthsDiff % task.repeatInterval == 0;

            case "years":
                // Same month and day, correct year interval
                if (taskCal.get(java.util.Calendar.DAY_OF_MONTH) != targetCal.get(java.util.Calendar.DAY_OF_MONTH))
                    return false;
                if (taskCal.get(java.util.Calendar.MONTH) != targetCal.get(java.util.Calendar.MONTH))
                    return false;
                int yearsDiff = targetCal.get(java.util.Calendar.YEAR) - taskCal.get(java.util.Calendar.YEAR);
                return yearsDiff >= 0 && yearsDiff % task.repeatInterval == 0;

            case "custom_days":
                // Already handled by the repeatDays check above; if we reach here,
                // the task has custom_days type but no matching repeatDays → doesn't occur
                return false;
        }

        return false;
    }

    /**
     * Calculate the due date for today preserving the time from the original task
     */
    private long calculateTodayDueDate(TaskList task, long todayStart) {
        if (task.taskTime != null && !task.taskTime.isEmpty()) {
            // Parse taskTime (format: "HH:mm" or "h:mm AM/PM")
            try {
                String cleanTime = task.taskTime.toUpperCase()
                        .replace(" AM", "")
                        .replace(" PM", "")
                        .replace("AM", "")
                        .replace("PM", "")
                        .trim();

                boolean isPM = task.taskTime.toUpperCase().contains("PM");
                boolean isAM = task.taskTime.toUpperCase().contains("AM");

                String[] parts = cleanTime.split(":");
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());

                // Convert to 24-hour format
                if (isPM && hour != 12) {
                    hour += 12;
                } else if (isAM && hour == 12) {
                    hour = 0;
                }

                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(todayStart);
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
                cal.set(java.util.Calendar.MINUTE, minute);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
            } catch (Exception e) {
                // Fall through to default
                android.util.Log.e("DataManager", "Error parsing time in calculateTodayDueDate: " + e.getMessage());
            }
        }

        // If original task had a specific time, preserve it
        if (task.dueDate > 0) {
            java.util.Calendar originalCal = java.util.Calendar.getInstance();
            originalCal.setTimeInMillis(task.dueDate);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(todayStart);
            cal.set(java.util.Calendar.HOUR_OF_DAY, originalCal.get(java.util.Calendar.HOUR_OF_DAY));
            cal.set(java.util.Calendar.MINUTE, originalCal.get(java.util.Calendar.MINUTE));
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }

        // Default to start of today
        return todayStart;
    }

    /**
     * Normalize a timestamp to the start of its day (midnight)
     */
    private long normalizeToStartOfDay(long timestamp) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private TaskList createNextTaskInstance(TaskList original, long dueDate, int status) {
        TaskList nextTask = new TaskList();
        nextTask.setTask(original.getTask());
        nextTask.setCategory(original.getCategory());
        nextTask.setTime("");
        nextTask.setStatus(status);
        nextTask.setRepeatType(original.getRepeatType());
        nextTask.setRepeatInterval(original.getRepeatInterval());
        nextTask.setRepeatDays(original.getRepeatDays());
        nextTask.setReminderMinutes(original.getReminderMinutes());
        nextTask.setUseAlarm(original.getUseAlarm());
        nextTask.setScreenLock(original.getScreenLock());
        nextTask.setStarred(original.getIsStarred());
        nextTask.setTaskTime(original.getTaskTime());
        nextTask.markerType = original.markerType;
        nextTask.markerValue = original.markerValue;
        nextTask.markerColor = original.markerColor;
        nextTask.setAttachments(original.getAttachments());
        nextTask.setCreatedFrom(original.getCreatedFrom());
        nextTask.dueDate = dueDate;
        // Link to the parent recurring task
        // If original is already a child, inherit its parent. Otherwise, original is
        // the parent.
        if (original.recurringParentId > 0) {
            nextTask.recurringParentId = original.recurringParentId;
        } else {
            nextTask.recurringParentId = original.id;
        }
        return nextTask;
    }

    private void sendWidgetUpdate() {
        try {
            android.appwidget.AppWidgetManager appWidgetManager = android.appwidget.AppWidgetManager
                    .getInstance(context);
            Class<?>[] providers = {
                    WidgetProviders.AllDark.class, WidgetProviders.AllLight.class, WidgetProviders.AllTrans.class,
                    WidgetProviders.TodayDark.class, WidgetProviders.TodayLight.class, WidgetProviders.TodayTrans.class,
                    WidgetProviders.StarredDark.class, WidgetProviders.StarredLight.class,
                    WidgetProviders.StarredTrans.class,
                    WidgetProviders.WorkDark.class, WidgetProviders.WorkLight.class, WidgetProviders.WorkTrans.class,
                    WidgetProviders.PersonalDark.class, WidgetProviders.PersonalLight.class,
                    WidgetProviders.PersonalTrans.class
            };

            for (Class<?> c : providers) {
                int[] ids = appWidgetManager.getAppWidgetIds(new android.content.ComponentName(context, c));
                if (ids != null && ids.length > 0) {
                    appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lv_widget_tasks);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
