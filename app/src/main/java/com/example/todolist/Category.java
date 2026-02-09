package com.example.todolist;

/**
 * Simple Category model (Room annotations removed)
 */
public class Category {

    public int id;
    public String name;
    public String color;
    public String icon = "folder"; // default icon
    public int isDefault = 0;

    public Category() {
    }

    public Category(String name, String color, int isDefault) {
        this.name = name;
        this.color = color;
        this.isDefault = isDefault;
    }
    
    public Category(String name, String color, boolean isDefault) {
        this.name = name;
        this.color = color;
        this.isDefault = isDefault ? 1 : 0;
    }
    
    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getIcon() { return icon != null ? icon : "folder"; }
    public void setIcon(String icon) { this.icon = icon; }
    public boolean isDefault() { return isDefault == 1; }

    
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault ? 1 : 0;
    }
}
