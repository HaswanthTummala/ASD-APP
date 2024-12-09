package com.example.speechtoimageapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Map;

public class UserActivity extends AppCompatActivity {

    private EditText userNameInput, killWordInput;
    private UserRecyclerViewAdapter adapter;
    private ArrayList<String> userList;
    private Map<String, String> userMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        userNameInput = findViewById(R.id.userNameInput);
        killWordInput = findViewById(R.id.killWordInput);
        Button addUserButton = findViewById(R.id.addUserButton);
        RecyclerView userRecyclerView = findViewById(R.id.userRecyclerView);

        // Load existing users from SharedPreferences
        userMap = User.loadUserMap(this);
        userList = new ArrayList<>(userMap.keySet());

        // Set up RecyclerView
        adapter = new UserRecyclerViewAdapter(userList, this);
        userRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        userRecyclerView.setAdapter(adapter);

        // Add User Button Click Listener
        addUserButton.setOnClickListener(v -> {
            String newUserName = userNameInput.getText().toString().trim();
            String newKillWord = killWordInput.getText().toString().trim();

            if (!newUserName.isEmpty() && !newKillWord.isEmpty() && !userList.contains(newUserName)) {
                User.addUser(UserActivity.this, newUserName, newKillWord);
                userList.add(newUserName);

                // Notify the adapter about the new item insertion
                adapter.notifyItemInserted(userList.size() - 1);

                // Clear the input fields
                userNameInput.setText("");
                killWordInput.setText("");

                // Scroll to the newly added item
                userRecyclerView.scrollToPosition(userList.size() - 1);
            } else {
                Toast.makeText(UserActivity.this, "Please enter a unique name and kill word.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
