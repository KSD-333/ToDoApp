package com.example.todolist;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletedTasksActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private DataManager dm;
    private CompletedTaskAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_completed_tasks);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // System bars
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));

        recyclerView = findViewById(R.id.recycler_completed_tasks);
        emptyView = findViewById(R.id.tv_empty_completed);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Use single thread for DB access
        executor.execute(() -> {
            dm = DataManager.getInstance(this);
            loadData();
        });
    }

    private void loadData() {
        if (dm == null)
            return;

        List<TaskList> completedTasks = dm.getCompletedTasks();
        List<Category> categories = dm.getAllCategories();

        // Build color map
        Map<String, String> colorMap = new HashMap<>();
        for (Category c : categories) {
            colorMap.put(c.getName(), c.getColor());
        }

        // Sort by completed time descending (newest first)
        Collections.sort(completedTasks, (t1, t2) -> Long.compare(t2.getCompletedAt(), t1.getCompletedAt()));

        new Handler(Looper.getMainLooper()).post(() -> {
            if (completedTasks.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);

                adapter = new CompletedTaskAdapter(CompletedTasksActivity.this, completedTasks, colorMap);
                recyclerView.setAdapter(adapter);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
