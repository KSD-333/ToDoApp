package com.example.todolist;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private List<Category> categories;
    private String selectedCategory;
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public CategoryAdapter(List<Category> categories, String selectedCategory, OnCategoryClickListener listener) {
        this.categories = categories;
        this.selectedCategory = selectedCategory;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private View categoryColor;
        private TextView tvCategoryName;
        private ImageView ivCheck;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryColor = itemView.findViewById(R.id.category_color);
            tvCategoryName = itemView.findViewById(R.id.tv_category_name);
            ivCheck = itemView.findViewById(R.id.iv_check);
        }

        void bind(Category category) {
            tvCategoryName.setText(category.getName());
            
            // Set color
            try {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(Color.parseColor(category.getColor()));
                categoryColor.setBackground(drawable);
            } catch (Exception e) {
                // Default color if parsing fails
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(Color.parseColor("#9BA5B0"));
                categoryColor.setBackground(drawable);
            }
            
            // Show check mark if selected
            if (category.getName().equals(selectedCategory)) {
                ivCheck.setVisibility(View.VISIBLE);
            } else {
                ivCheck.setVisibility(View.GONE);
            }
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}
