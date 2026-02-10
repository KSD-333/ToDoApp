package com.example.todolist;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SubTaskDao {
    @Query("SELECT * FROM subtasks WHERE parent_task_id = :taskId")
    List<SubTask> getSubTasksForTask(int taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SubTask subTask);

    @Update
    void update(SubTask subTask);

    @Delete
    void delete(SubTask subTask);

    @Query("DELETE FROM subtasks WHERE id = :id")
    void deleteById(int id);
}
