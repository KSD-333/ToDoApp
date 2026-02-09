package com.example.todolist;

/**
 * Simple TaskTemplate model (Room annotations removed)
 */
public class TaskTemplate {

    public int id;
    public String name;
    public String emoji;
    public String category;
    public int isBuiltIn = 0;
    public int usageCount = 0;

    public TaskTemplate() {
    }

    public TaskTemplate(String name, String emoji, String category, int isBuiltIn, int usageCount) {
        this.name = name;
        this.emoji = emoji;
        this.category = category;
        this.isBuiltIn = isBuiltIn;
        this.usageCount = usageCount;
    }
    
    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return name; }
    public void setTitle(String title) { this.name = title; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isNew() { return isBuiltIn == 1; }
    public boolean isPopular() { return usageCount > 0; }
}
