package com.example.todolist;

/**
 * Simple model for day-of-week task counts (Room annotations removed)
 */
public class DayCount {
    public int dayOfWeek;
    public int count;

    public DayCount() {
    }

    public DayCount(int dayOfWeek, int count) {
        this.dayOfWeek = dayOfWeek;
        this.count = count;
    }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
