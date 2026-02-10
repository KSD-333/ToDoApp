package com.example.todolist;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

/**
 * SubTask model for Room
 */
@Entity(tableName = "subtasks", foreignKeys = @ForeignKey(entity = TaskList.class, parentColumns = "id", childColumns = "parent_task_id", onDelete = CASCADE), indices = {
        @Index("parent_task_id") })
public class SubTask {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "parent_task_id")
    public int parentTaskId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "is_completed")
    public int isCompleted = 0;

    public SubTask() {
    }

    @Ignore
    public SubTask(long parentTaskId, String title) {
        this.parentTaskId = (int) parentTaskId;
        this.title = title;
        this.isCompleted = 0;
    }

    @Ignore
    public SubTask(int parentTaskId, String title) {
        this.parentTaskId = parentTaskId;
        this.title = title;
        this.isCompleted = 0;
    }
}
