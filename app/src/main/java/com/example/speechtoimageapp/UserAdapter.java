package com.example.speechtoimageapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private ArrayList<String> userList;
    private OnUserClickListener userClickListener;
    private boolean isDisabled = false;  // Flag to disable user clicks after one user is clicked

    public interface OnUserClickListener {
        void onUserClick(int position);
    }

    public UserAdapter(ArrayList<String> userList, OnUserClickListener userClickListener) {
        this.userList = userList;
        this.userClickListener = userClickListener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.user_item, parent, false);
        return new UserViewHolder(view, userClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        String userName = userList.get(position);
        holder.userTextView.setText(userName);

        // If isDisabled is true, we disable the click listener
        if (isDisabled) {
            holder.itemView.setEnabled(false);  // Disable the item view click
        } else {
            holder.itemView.setEnabled(true);  // Enable the item view click
        }

    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    // Method to disable all user names
    public void disableUserList() {
        isDisabled = true;
        notifyDataSetChanged();  // Notify adapter to refresh the list and disable clicks
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userTextView;

        public UserViewHolder(@NonNull View itemView, final OnUserClickListener userClickListener) {
            super(itemView);
            userTextView = itemView.findViewById(R.id.textViewUserName);

            // Set the click listener for each user item
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (userClickListener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                        userClickListener.onUserClick(getAdapterPosition());
                    }
                }
            });
        }
    }
}
