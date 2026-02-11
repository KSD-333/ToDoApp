package com.example.todolist;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {
    @Query("SELECT * FROM tasks")
    List<TaskList> getAllTasks();

    @Query("SELECT * FROM tasks WHERE id = :id")
    TaskList getTaskById(int id);

    @Query("SELECT * FROM tasks WHERE check_status = :status")
    List<TaskList> getTasksByStatus(int status);

    @Query("SELECT * FROM tasks WHERE category = :category")
    List<TaskList> getTasksByCategory(String category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TaskList task);

    @Update
    void update(TaskList task);

    @Delete
    void delete(TaskList task);

    @Query("DELETE FROM tasks WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT COUNT(*) FROM tasks WHERE check_status = 0")
    int getPendingTaskCount();

    @Query("SELECT COUNT(*) FROM tasks WHERE check_status = 0 AND category LIKE :category")
    int getPendingTaskCountByCategory(String category);

    @Query("SELECT COUNT(*) FROM tasks WHERE check_status = 0 AND (category IS NULL OR category = '' OR category = 'No Category')")
    int getPendingTaskCountNoCategory();
}
