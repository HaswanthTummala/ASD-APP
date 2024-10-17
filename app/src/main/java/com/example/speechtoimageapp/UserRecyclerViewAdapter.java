package com.example.speechtoimageapp;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class UserRecyclerViewAdapter extends RecyclerView.Adapter<UserRecyclerViewAdapter.UserViewHolder> {

    private ArrayList<String> userList;
    private Context context;

    // Constructor
    public UserRecyclerViewAdapter(ArrayList<String> userList, Context context) {
        this.userList = userList;
        this.context = context;
    }

    // ViewHolder class
    public static class UserViewHolder extends RecyclerView.ViewHolder {
        public TextView userNameTextView;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            userNameTextView = itemView.findViewById(R.id.userName);
        }
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recyclerview_user_row, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        String userName = userList.get(position);
        holder.userNameTextView.setText(userName);

        // Long press to delete user with confirmation dialog
        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete User")
                    .setMessage("Are you sure you want to delete " + userName + "?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        User.removeUser(context, userName);
                        userList.remove(position);
                        notifyItemRemoved(position);
                        Toast.makeText(context, "User " + userName + " deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    // Update the user list and refresh
    public void updateUserList(ArrayList<String> newUserList) {
        this.userList.clear();
        this.userList.addAll(newUserList);
        notifyDataSetChanged();
    }
}
