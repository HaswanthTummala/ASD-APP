package com.example.speechtoimageapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class UserActivity extends AppCompatActivity {

    private EditText userNameInput;
    private UserRecyclerViewAdapter adapter;
    private ArrayList<String> userList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        userNameInput = findViewById(R.id.userNameInput);
        Button addUserButton = findViewById(R.id.addUserButton);
        RecyclerView userRecyclerView = findViewById(R.id.userRecyclerView);

        // Load existing users from SharedPreferences
        userList = User.loadUserList(this);

        // Set up RecyclerView
        adapter = new UserRecyclerViewAdapter(userList, this);
        userRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        userRecyclerView.setAdapter(adapter);

        // Add User Button Click Listener
        addUserButton.setOnClickListener(v -> {
            String newUserName = userNameInput.getText().toString().trim();
            if (!newUserName.isEmpty() && !userList.contains(newUserName)) {
                // Add the new user to the list and save it in SharedPreferences
                User.addUser(UserActivity.this, newUserName);
                userList.add(newUserName);

                // Notify the adapter about the new item insertion
                adapter.notifyItemInserted(userList.size() - 1);

                // Clear the input field
                userNameInput.setText("");

                // Scroll to the newly added item
                userRecyclerView.scrollToPosition(userList.size() - 1);
            } else {
                Toast.makeText(UserActivity.this, "Please enter a unique name.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
