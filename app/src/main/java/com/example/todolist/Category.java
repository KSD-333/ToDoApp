package com.example.todolist;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Category model for Room database
 */
@Entity(tableName = "categories")
public class Category {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "color")
    public String color;

    @ColumnInfo(name = "icon")
    public String icon = "folder"; // default icon

    @ColumnInfo(name = "is_default")
    public int isDefault = 0;

    public Category() {
    }

    @Ignore
    public Category(String name, String color, int isDefault) {
        this.name = name;
        this.color = color;
        this.isDefault = isDefault;
    }

    @Ignore
    public Category(String name, String color, boolean isDefault) {
        this.name = name;
        this.color = color;
        this.isDefault = isDefault ? 1 : 0;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon != null ? icon : "folder";
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isDefault() {
        return isDefault == 1;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault ? 1 : 0;
    }
}
