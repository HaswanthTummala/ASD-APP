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
    private void showEditDialog(String currentUserName, int position) {
        // Inflate the dialog layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_user, null);
        TextView userNameEditText = dialogView.findViewById(R.id.editUserNameInput);
        TextView killWordEditText = dialogView.findViewById(R.id.editKillWordInput);

        // Pre-fill current user details
        userNameEditText.setText(currentUserName);
        String currentKillWord = User.getKillWord(context, currentUserName);
        killWordEditText.setText(currentKillWord);

        new AlertDialog.Builder(context)
                .setTitle("Edit User")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newUserName = userNameEditText.getText().toString().trim();
                    String newKillWord = killWordEditText.getText().toString().trim();

                    // Validate inputs
                    if (userList.contains(newUserName) && !newUserName.equals(currentUserName)) {
                        Toast.makeText(context, "Username already exists.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newUserName.matches("^[a-zA-Z0-9_]+$") || newUserName.length() < 3) {
                        Toast.makeText(context, "Invalid username. Use only letters, numbers, or underscores (min 3 characters).", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newKillWord.isEmpty()) {
                        Toast.makeText(context, "Kill word cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Update user data
                    User.updateUser(context, currentUserName, newUserName, newKillWord);

                    // Update the list and adapter
                    userList.set(position, newUserName);
                    notifyItemChanged(position);

                    Toast.makeText(context, "User updated successfully!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        String userName = userList.get(position);
        holder.userNameTextView.setText(userName);

        // Long press to show Edit/Delete options
        holder.itemView.setOnLongClickListener(v -> {
            String selectedUserName = userList.get(position);

            new AlertDialog.Builder(context)
                    .setTitle("Select Action")
                    .setItems(new String[]{"Edit", "Delete"}, (dialog, which) -> {
                        if (which == 0) {
                            // Edit option selected
                            showEditDialog(selectedUserName, position);
                        } else if (which == 1) {
                            // Delete option selected
                            new AlertDialog.Builder(context)
                                    .setTitle("Delete User")
                                    .setMessage("Are you sure you want to delete " + selectedUserName + "?")
                                    .setPositiveButton("Delete", (d, w) -> {
                                        User.removeUser(context, selectedUserName); // Remove from storage
                                        userList.remove(position); // Remove from list
                                        notifyItemRemoved(position); // Update adapter
                                        Toast.makeText(context, "User " + selectedUserName + " deleted", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    })
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
