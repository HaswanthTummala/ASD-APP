package com.example.speechtoimageapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class history extends AppCompatActivity {

    private RecyclerView recyclerViewUsers;
    private RecyclerView recyclerViewSessions;
    private HistoryAdapter historyAdapter;
    private UserAdapter userAdapter;
    private ArrayList<String> sessionHistory;
    private ArrayList<String> userList;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "SessionHistoryPrefs";
    private static final String KEY_HISTORY = "history";
    private static final String USERS_PREFS = "UserPrefs";  // To store the users
    private static final String KEY_USERS = "users";

    private String currentUserName;  // Store the current user name to filter sessions

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        recyclerViewSessions = findViewById(R.id.recyclerViewHistory);

        // Set layout manager for both RecyclerViews
        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSessions.setLayoutManager(new LinearLayoutManager(this));

        // Load user list and session history
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userList = new ArrayList<>(loadUserList());

        // Show only user names by default
        sessionHistory = new ArrayList<>();

        // Set up the UserAdapter for users RecyclerView
        userAdapter = new UserAdapter(userList, new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(int position) {
                currentUserName = userList.get(position);  // Get the clicked user name
                Log.d("HistoryPage", "Selected user: " + currentUserName);  // Log to confirm the user click

                // Load session data for the selected user
                sessionHistory = new ArrayList<>(loadSessionHistoryForUser(currentUserName));

                if (sessionHistory.isEmpty()) {
                    Log.d("HistoryPage", "No session data found for user: " + currentUserName);
                    Toast.makeText(history.this, "No session data found for " + currentUserName, Toast.LENGTH_SHORT).show();
                } else {
                    Log.d("HistoryPage", "Session data loaded for user: " + sessionHistory.toString());
                    historyAdapter.updateSessionData(sessionHistory);  // Update the RecyclerView with the new session data
                    historyAdapter.notifyDataSetChanged();  // Ensure the RecyclerView updates
                }

                // Disable other user names after a click
               // userAdapter.disableUserList();  // Call this method to disable further clicks
            }
        });
        recyclerViewUsers.setAdapter(userAdapter);

        // Set up the HistoryAdapter for session data RecyclerView
        historyAdapter = new HistoryAdapter(sessionHistory, new HistoryAdapter.OnDeleteClickListener() {
            @Override
            public void onDeleteClick(int position) {
                // Remove the selected session from the history list
                sessionHistory.remove(position);
                saveSessionHistoryForUser(currentUserName);  // Save the updated history back to SharedPreferences
                historyAdapter.notifyDataSetChanged();  // Notify the adapter to update the list
            }
        });
        recyclerViewSessions.setAdapter(historyAdapter);
    }

    // Load saved session history for a specific user
    private Set<String> loadSessionHistoryForUser(String userName) {
        // The key now includes the user's name
        Set<String> sessionHistorySet = sharedPreferences.getStringSet("history_" + userName, new HashSet<>());
        Log.d("HistoryPage", "Loaded session data for user " + userName + ": " + sessionHistorySet);
        return sessionHistorySet;
    }

    // Save updated session history for the specific user
    private void saveSessionHistoryForUser(String userName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> historySet = new HashSet<>(sessionHistory);  // Convert ArrayList back to Set
        editor.putStringSet(KEY_HISTORY + "_" + userName, historySet);
        editor.apply();
    }

    // Load user list from SharedPreferences
    private Set<String> loadUserList() {
        SharedPreferences userPrefs = getSharedPreferences(USERS_PREFS, MODE_PRIVATE);
        Set<String> userSet = userPrefs.getStringSet(KEY_USERS, new HashSet<>());
        Log.d("HistoryPage", "Loaded users: " + userSet);
        return userSet;
    }

    // Disable user names after a click
    private void disableUserNames() {
        for (int i = 0; i < recyclerViewUsers.getChildCount(); i++) {
            recyclerViewUsers.getChildAt(i).setEnabled(false);  // Disable each user item
        }
    }
}
