package com.example.todolist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MineCategorySummaryAdapter extends RecyclerView.Adapter<MineCategorySummaryAdapter.VH> {

    private final List<CategoryCount> items;

    public MineCategorySummaryAdapter(List<CategoryCount> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mine_category_summary, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CategoryCount cc = items.get(position);
        holder.category.setText(cc.category == null ? "" : cc.category);
        holder.count.setText(String.valueOf(cc.count));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView category;
        final TextView count;

        VH(@NonNull View itemView) {
            super(itemView);
            category = itemView.findViewById(R.id.tv_category);
            count = itemView.findViewById(R.id.tv_count);
        }
    }
}
