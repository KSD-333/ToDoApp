package com.example.todolist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskTemplateActivity extends AppCompatActivity {

    private RecyclerView rvTemplates;
    private DataManager dm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_task_template);

            dm = DataManager.getInstance(this);

            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setNavigationOnClickListener(v -> finish());
            }

            rvTemplates = findViewById(R.id.rv_templates);
            if (rvTemplates != null) {
                rvTemplates.setLayoutManager(new LinearLayoutManager(this));
            }

            loadTemplates();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Templates screen failed to open", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadTemplates() {
        if (dm == null || rvTemplates == null) return;
        List<TaskTemplate> templates;
        try {
            templates = dm.getAllTemplates();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to load templates", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize templates if empty
        if (templates.isEmpty()) {
            try {
                dm.initializeDefaultData();
                templates = dm.getAllTemplates();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Group templates by category
        Map<String, List<TaskTemplate>> groupedTemplates = new LinkedHashMap<>();
        for (TaskTemplate template : templates) {
            if (template == null) continue;
            String category = template.getCategory();
            if (category == null) category = "Other";
            if (!groupedTemplates.containsKey(category)) {
                groupedTemplates.put(category, new ArrayList<>());
            }
            groupedTemplates.get(category).add(template);
        }

        // Create flat list with headers
        List<Object> items = new ArrayList<>();
        for (Map.Entry<String, List<TaskTemplate>> entry : groupedTemplates.entrySet()) {
            items.add(entry.getKey()); // Header
            items.addAll(entry.getValue()); // Templates
        }

        TemplateAdapter adapter = new TemplateAdapter(items, template -> {
            // Create task from template
            TaskList newTask = new TaskList(template.getTitle());
            newTask.setCategory(template.getCategory());
            newTask.setStatus(0);
            newTask.setStarred(0);
            dm.insertTask(newTask);
            
            Toast.makeText(this, "Task created from template", Toast.LENGTH_SHORT).show();
            finish();
        });
        rvTemplates.setAdapter(adapter);
    }

    // Adapter for templates with headers
    private static class TemplateAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_TEMPLATE = 1;

        private List<Object> items;
        private OnTemplateClickListener listener;

        interface OnTemplateClickListener {
            void onTemplateClick(TaskTemplate template);
        }

        TemplateAdapter(List<Object> items, OnTemplateClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? TYPE_HEADER : TYPE_TEMPLATE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View view = inflater.inflate(R.layout.item_template_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = inflater.inflate(R.layout.item_template, parent, false);
                return new TemplateViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind((String) items.get(position));
            } else if (holder instanceof TemplateViewHolder) {
                ((TemplateViewHolder) holder).bind((TaskTemplate) items.get(position), listener);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvHeader;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvHeader = itemView.findViewById(R.id.tv_category_header);
            }

            void bind(String category) {
                tvHeader.setText(category);
            }
        }

        static class TemplateViewHolder extends RecyclerView.ViewHolder {
            TextView tvEmoji, tvTitle, tvBadgeNew, tvBadgePopular;
            com.google.android.material.button.MaterialButton btnAdd;

            TemplateViewHolder(@NonNull View itemView) {
                super(itemView);
                tvEmoji = itemView.findViewById(R.id.tv_emoji);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvBadgeNew = itemView.findViewById(R.id.tv_badge_new);
                tvBadgePopular = itemView.findViewById(R.id.tv_badge_popular);
                btnAdd = itemView.findViewById(R.id.btn_add);
            }

            void bind(TaskTemplate template, OnTemplateClickListener listener) {
                tvEmoji.setText(template.getEmoji());
                tvTitle.setText(template.getTitle());
                
                tvBadgeNew.setVisibility(template.isNew() ? View.VISIBLE : View.GONE);
                tvBadgePopular.setVisibility(template.isPopular() ? View.VISIBLE : View.GONE);

                btnAdd.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onTemplateClick(template);
                    }
                });
                
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onTemplateClick(template);
                    }
                });
            }
        }
    }
}
