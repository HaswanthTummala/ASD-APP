package com.example.speechtoimageapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private ArrayList<String> sessionHistory;
    private OnDeleteClickListener deleteClickListener;

    // Interface to handle delete click events
    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    // Constructor to pass session history and delete click listener
    public HistoryAdapter(ArrayList<String> sessionHistory, OnDeleteClickListener deleteClickListener) {
        this.sessionHistory = sessionHistory;
        this.deleteClickListener = deleteClickListener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.history_item, parent, false);
        return new HistoryViewHolder(view, deleteClickListener);
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
        FloatingActionButton deleteButton;

        public HistoryViewHolder(@NonNull View itemView, final OnDeleteClickListener deleteClickListener) {
            super(itemView);
            sessionTextView = itemView.findViewById(R.id.textViewSession);
            deleteButton = itemView.findViewById(R.id.fabDeleteSession);

            // Handle delete button click event
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (deleteClickListener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            deleteClickListener.onDeleteClick(position);  // Trigger the delete event
                        }
                    }
                }
            });
        }
    }

    // Method to update session data
    public void updateSessionData(ArrayList<String> newSessionData) {
        this.sessionHistory = newSessionData;
        notifyDataSetChanged();
    }
}
