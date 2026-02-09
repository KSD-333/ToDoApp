package com.example.todolist;

/**
 * Simple SubTask model (Room annotations removed)
 */
public class SubTask {

    public int id;
    public int parentTaskId;
    public String title;
    public int isCompleted = 0;

    public SubTask() {
    }

    public SubTask(long parentTaskId, String title) {
        this.parentTaskId = (int) parentTaskId;
        this.title = title;
        this.isCompleted = 0;
    }
    
    public SubTask(int parentTaskId, String title) {
        this.parentTaskId = parentTaskId;
        this.title = title;
        this.isCompleted = 0;
    }
}
