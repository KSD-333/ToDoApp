package com.example.todolist;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data manager with persistence and sample data generation.
 */
public class DataManager {
    private static DataManager instance;

    private final ArrayList<TaskList> tasks = new ArrayList<>();
    private final ArrayList<Category> categories = new ArrayList<>();
    private final ArrayList<SubTask> subTasks = new ArrayList<>();
    private final ArrayList<TaskTemplate> templates = new ArrayList<>();

    private final AtomicInteger taskIdCounter = new AtomicInteger(1);
    private final AtomicInteger categoryIdCounter = new AtomicInteger(1);
    private final AtomicInteger subTaskIdCounter = new AtomicInteger(1);
    private final AtomicInteger templateIdCounter = new AtomicInteger(1);

    private TaskStorageManager storageManager;

    private DataManager(Context context) {
        storageManager = new TaskStorageManager(context);
        initializeData();
    }

    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context.getApplicationContext());
        }
        return instance;
    }

    private void saveChanges() {
        storageManager.saveData(tasks, categories, subTasks);
    }

    // ==================== TASK OPERATIONS ====================

    public synchronized List<TaskList> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    public synchronized List<TaskList> getTasksByStatus(int status) {
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : tasks) {
            if (t.check == status)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getTasksByStatusAndCategory(int status, String category) {
        ArrayList<TaskList> result = new ArrayList<>();
        boolean isNoCategory = "No Category".equalsIgnoreCase(category);
        for (TaskList t : tasks) {
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

    public synchronized List<TaskList> getTasksByCategory(String category) {
        ArrayList<TaskList> result = new ArrayList<>();
        boolean isNoCategory = "No Category".equalsIgnoreCase(category);
        for (TaskList t : tasks) {
            boolean match;
            if (isNoCategory) {
                match = (t.category == null || t.category.trim().isEmpty()
                        || "No Category".equalsIgnoreCase(t.category));
            } else {
                match = category.equalsIgnoreCase(t.category);
            }

            if (match)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getStarredTasks() {
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : tasks) {
            if (t.isStarred == 1)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getCompletedTasks() {
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : tasks) {
            if (t.check == 1)
                result.add(t);
        }
        return result;
    }

    public synchronized TaskList getTaskById(int id) {
        for (TaskList t : tasks) {
            if (t.id == id)
                return t;
        }
        return null;
    }

    public synchronized List<TaskList> getTasksByDueDate(long startOfDay, long endOfDay) {
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : tasks) {
            if (t.dueDate >= startOfDay && t.dueDate <= endOfDay)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getTasksByDateRange(long start, long end) {
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : tasks) {
            if (t.dueDate >= start && t.dueDate < end)
                result.add(t);
        }
        return result;
    }

    public synchronized List<TaskList> getTasksByDateRangeAndSource(long start, long end, String source) {
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : tasks) {
            if (t.dueDate >= start && t.dueDate < end && source.equals(t.createdFrom)) {
                result.add(t);
            }
        }
        return result;
    }

    public synchronized List<TaskList> getPendingTasksDueBetween(long start, long end) {
        ArrayList<TaskList> result = new ArrayList<>();
        for (TaskList t : tasks) {
            if (t.check == 0 && t.dueDate >= start && t.dueDate <= end)
                result.add(t);
        }
        return result;
    }

    public synchronized int getCompletedTaskCount() {
        int count = 0;
        for (TaskList t : tasks) {
            if (t.check == 1)
                count++;
        }
        return count;
    }

    public synchronized int getPendingTaskCount() {
        int count = 0;
        for (TaskList t : tasks) {
            if (t.check == 0)
                count++;
        }
        return count;
    }

    public synchronized int getTaskCountByCategory(String category) {
        int count = 0;
        boolean isNoCategory = "No Category".equalsIgnoreCase(category);
        for (TaskList t : tasks) {
            boolean match;
            if (isNoCategory) {
                match = (t.category == null || t.category.trim().isEmpty()
                        || "No Category".equalsIgnoreCase(t.category));
            } else {
                match = category.equalsIgnoreCase(t.category);
            }

            if (match)
                count++;
        }
        return count;
    }

    public synchronized int getPendingCountByCategory(String category) {
        int count = 0;
        boolean isNoCategory = "No Category".equalsIgnoreCase(category);
        for (TaskList t : tasks) {
            boolean match;
            if (isNoCategory) {
                match = (t.category == null || t.category.trim().isEmpty()
                        || "No Category".equalsIgnoreCase(t.category));
            } else {
                match = category.equalsIgnoreCase(t.category);
            }

            if (t.check == 0 && match)
                count++;
        }
        return count;
    }

    public synchronized long insertTask(TaskList task) {
        task.id = taskIdCounter.getAndIncrement();
        tasks.add(task);
        saveChanges();
        return task.id;
    }

    public synchronized long insertAndGetId(TaskList task) {
        return insertTask(task);
    }

    public synchronized void updateTask(TaskList task) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id == task.id) {
                tasks.set(i, task);
                saveChanges();
                break;
            }
        }
    }

    public synchronized void updateStatusAndCompletedAt(int id, int status, long completedAt) {
        for (TaskList t : tasks) {
            if (t.id == id) {
                t.check = status;
                t.completedAt = completedAt;
                saveChanges();
                break;
            }
        }
    }

    public synchronized void updateStarred(int id, int starred) {
        for (TaskList t : tasks) {
            if (t.id == id) {
                t.isStarred = starred;
                saveChanges();
                break;
            }
        }
    }

    public synchronized void deleteTask(TaskList task) {
        tasks.removeIf(t -> t.id == task.id);
        subTasks.removeIf(st -> st.parentTaskId == task.id);
        saveChanges();
    }

    public synchronized void deleteTaskById(int id) {
        tasks.removeIf(t -> t.id == id);
        subTasks.removeIf(st -> st.parentTaskId == id);
        saveChanges();
    }

    // ==================== CATEGORY OPERATIONS ====================

    public synchronized List<Category> getAllCategories() {
        return new ArrayList<>(categories);
    }

    public synchronized int getCategoryCount() {
        return categories.size();
    }

    public synchronized void insertCategory(Category category) {
        category.id = categoryIdCounter.getAndIncrement();
        categories.add(category);
        saveChanges();
    }

    public synchronized void updateCategory(Category category) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == category.id) {
                categories.set(i, category);
                saveChanges();
                break;
            }
        }
    }

    public synchronized void deleteCategory(Category category) {
        categories.removeIf(c -> c.id == category.id);
        saveChanges();
    }

    public synchronized void deleteCategory(int categoryId) {
        categories.removeIf(c -> c.id == categoryId);
        saveChanges();
    }

    // ==================== SUBTASK OPERATIONS ====================

    public synchronized List<SubTask> getSubTasksForTask(int taskId) {
        ArrayList<SubTask> result = new ArrayList<>();
        for (SubTask st : subTasks) {
            if (st.parentTaskId == taskId)
                result.add(st);
        }
        return result;
    }

    public synchronized void insertSubTask(SubTask subTask) {
        subTask.id = subTaskIdCounter.getAndIncrement();
        subTasks.add(subTask);
        saveChanges();
    }

    public synchronized void updateSubTask(SubTask subTask) {
        for (int i = 0; i < subTasks.size(); i++) {
            if (subTasks.get(i).id == subTask.id) {
                subTasks.set(i, subTask);
                saveChanges();
                break;
            }
        }
    }

    public synchronized void deleteSubTask(SubTask subTask) {
        subTasks.removeIf(st -> st.id == subTask.id);
        saveChanges();
    }

    public synchronized void deleteSubTask(int subTaskId) {
        subTasks.removeIf(st -> st.id == subTaskId);
        saveChanges();
    }

    // ==================== TEMPLATE OPERATIONS ====================

    public synchronized List<TaskTemplate> getAllTemplates() {
        return new ArrayList<>(templates);
    }

    public synchronized List<TaskTemplate> getTemplatesByCategory(String category) {
        ArrayList<TaskTemplate> result = new ArrayList<>();
        for (TaskTemplate t : templates) {
            if (category.equals(t.category))
                result.add(t);
        }
        return result;
    }

    public synchronized int getTemplateCount() {
        return templates.size();
    }

    public synchronized void insertTemplate(TaskTemplate template) {
        template.id = templateIdCounter.getAndIncrement();
        templates.add(template);
    }

    // ==================== STATISTICS ====================

    public synchronized List<CategoryCount> getPendingCountsByCategoryBetween(long start, long end) {
        ArrayList<CategoryCount> result = new ArrayList<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        for (TaskList t : tasks) {
            if (t.check == 0 && t.dueDate >= start && t.dueDate <= end) {
                String cat = (t.category != null && !t.category.trim().isEmpty()) ? t.category : "No Category";
                if ("No Category".equalsIgnoreCase(cat))
                    cat = "No Category"; // Normalize case
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

        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (TaskList t : tasks) {
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

    // ==================== INITIALIZATION ====================

    public synchronized void initializeData() {
        // Load data from storage
        List<TaskList> loadedTasks = storageManager.loadTasks();
        List<Category> loadedCategories = storageManager.loadCategories();
        List<SubTask> loadedSubTasks = storageManager.loadSubTasks();

        if (loadedTasks != null && !loadedTasks.isEmpty()) {
            tasks.clear();
            tasks.addAll(loadedTasks);

            // Update counter to max + 1
            int maxId = 0;
            for (TaskList t : loadedTasks) {
                if (t.id > maxId)
                    maxId = t.id;
            }
            taskIdCounter.set(maxId + 1);
        } else {
            // No data loaded, generate sample data
            generateSampleData();
        }

        if (loadedCategories != null && !loadedCategories.isEmpty()) {
            categories.clear();
            categories.addAll(loadedCategories);

            int maxId = 0;
            for (Category c : loadedCategories) {
                if (c.id > maxId)
                    maxId = c.id;
            }
            categoryIdCounter.set(maxId + 1);
        } else {
            // Load default categories if none found and we didn't generate them in sample
            // data
            // (Sample data generation might trigger categorization creation implicitly if
            // we wanted,
            // but here we ensure base categories exist)
            if (categories.isEmpty()) {
                insertCategory(new Category("No Category", "#9BA5B0", 1));
                insertCategory(new Category("Work", "#5B9BD5", 1));
                insertCategory(new Category("Personal", "#7CB342", 1));
                insertCategory(new Category("Wishlist", "#FF7043", 1));
                insertCategory(new Category("Birthday", "#AB47BC", 1));
                insertCategory(new Category("Health", "#EC407A", 1));
                insertCategory(new Category("Study", "#5C6BC0", 1));
            }
        }

        if (loadedSubTasks != null) {
            subTasks.clear();
            subTasks.addAll(loadedSubTasks);

            int maxId = 0;
            for (SubTask st : loadedSubTasks) {
                if (st.id > maxId)
                    maxId = st.id;
            }
            subTaskIdCounter.set(maxId + 1);
        }

        // Templates are not persisted for now, use defaults
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
    }

    private void generateSampleData() {
        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;

        // 1. Overdue Tasks (Past 1-3 days)
        insertTask(createSampleTask("Complete Project Proposal", "Work", now - (2 * dayMs), 0, true));
        insertTask(createSampleTask("Pay Electricity Bill", "Personal", now - (1 * dayMs), 0, true));
        insertTask(createSampleTask("Return Library Book", "Study", now - (3 * dayMs), 0, false));

        // 2. Completed Tasks (Past 7 days for charts)
        insertTask(createSampleTask("Weekly Team Sync", "Work", now - (1 * dayMs), 1, true)); // Yesterday
        insertTask(createSampleTask("Grocery Shopping", "Personal", now - (2 * dayMs), 1, false));
        insertTask(createSampleTask("Gym Workout", "Health", now - (3 * dayMs), 1, false));
        insertTask(createSampleTask("Read Chapter 4", "Study", now - (4 * dayMs), 1, false));
        insertTask(createSampleTask("Call Parents", "Personal", now - (2 * dayMs), 1, true));
        insertTask(createSampleTask("Submit Expense Report", "Work", now - (5 * dayMs), 1, false));

        // 3. Upcoming Tasks (Today and Future)
        insertTask(createSampleTask("Review Design Mockups", "Work", now + (2 * 60 * 60 * 1000), 0, true)); // Today
                                                                                                            // later
        insertTask(createSampleTask("Buy Birthday Gift", "Birthday", now + (1 * dayMs), 0, false));
        insertTask(createSampleTask("Dentist Appointment", "Health", now + (2 * dayMs), 0, false));
        insertTask(createSampleTask("Car Service", "Personal", now + (4 * dayMs), 0, false));
        insertTask(createSampleTask("Plan Weekend Trip", "Wishlist", now + (5 * dayMs), 0, false));
        insertTask(createSampleTask("Update Resume", "Work", now + (6 * dayMs), 0, true));
        insertTask(createSampleTask("Yoga Class", "Health", now + (dayMs / 2), 0, false));

        // Ensure changes are saved
        saveChanges();
    }

    private TaskList createSampleTask(String title, String category, long dueDate, int status, boolean starred) {
        TaskList t = new TaskList();
        t.setTask(title);
        t.setCategory(category);
        t.setDueDate(dueDate);
        t.setStatus(status);
        t.setStarred(starred ? 1 : 0);

        // Set completion time if completed, otherwise 0
        if (status == 1) {
            t.setCompletedAt(dueDate + (1000 * 60 * 60)); // Completed 1 hour after due (simulated)
        } else {
            t.setCompletedAt(0);
        }

        // Set display time string
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
        t.setTime(dueDate > 0 ? sdf.format(new java.util.Date(dueDate)) : "");

        return t;
    }

    public synchronized void initializeDefaultData() {
        initializeData();
    }

    public synchronized int getAllTasksCount() {
        return tasks.size();
    }
}
