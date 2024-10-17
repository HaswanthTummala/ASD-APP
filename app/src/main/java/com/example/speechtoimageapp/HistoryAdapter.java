package com.example.speechtoimageapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private ArrayList<String> sessionHistory;
    private OnDeleteClickListener deleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    public HistoryAdapter(ArrayList<String> sessionHistory, OnDeleteClickListener deleteClickListener) {
        this.sessionHistory = sessionHistory;
        this.deleteClickListener = deleteClickListener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.history_item, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        String sessionData = sessionHistory.get(position);
        holder.sessionTextView.setText(sessionData);
    }

    @Override
    public int getItemCount() {
        return sessionHistory.size();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView sessionTextView;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            sessionTextView = itemView.findViewById(R.id.textViewSession);
        }
    }
}
