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
    private RecyclerView recyclerViewSessionData;
    private HistoryAdapter historyAdapter;
    private UserAdapter userAdapter;
    private ArrayList<String> sessionHistory;
    private ArrayList<String> userList;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "SessionHistoryPrefs";
    private static final String KEY_HISTORY_PREFIX = "history_";
    private static final String USER_PREFS_NAME = "UserPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        recyclerViewSessionData = findViewById(R.id.recyclerViewHistory);

        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSessionData.setLayoutManager(new LinearLayoutManager(this));

        // Load user list from SharedPreferences
        SharedPreferences userPrefs = getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE);
        userList = new ArrayList<>(User.loadUserList(this)); // Ensure this method loads your user list correctly

        // Initialize user adapter
        userAdapter = new UserAdapter(userList, new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(String userName) {
                loadSessionDataForUser(userName); // Load session data when user is clicked
            }
        });

        recyclerViewUsers.setAdapter(userAdapter);

        // Set up RecyclerView for session data (empty initially)
        sessionHistory = new ArrayList<>();
        historyAdapter = new HistoryAdapter(sessionHistory, new HistoryAdapter.OnDeleteClickListener() {
            @Override
            public void onDeleteClick(int position) {
                // Remove the selected session from the history list
                sessionHistory.remove(position);
                saveSessionHistory();  // Save the updated history back to SharedPreferences
                historyAdapter.notifyDataSetChanged();  // Notify the adapter to update the list
            }
        });

        recyclerViewSessionData.setAdapter(historyAdapter);
    }

    // Load session data for the selected user
    // Load session data for the selected user
    private void loadSessionDataForUser(String userName) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Get the session data for the selected user
        Set<String> userHistorySet = sharedPreferences.getStringSet(KEY_HISTORY_PREFIX + userName, new HashSet<>());

        if (userHistorySet != null && !userHistorySet.isEmpty()) {
            sessionHistory.clear();
            sessionHistory.addAll(userHistorySet);
            historyAdapter.notifyDataSetChanged();
            Log.d("HistoryPage", "Loaded session data for user " + userName + ": " + sessionHistory);
        } else {
            // No session data found for this user
            Toast.makeText(this, "No session data found for user: " + userName, Toast.LENGTH_SHORT).show();
            Log.d("HistoryPage", "No session data found for user: " + userName);
            sessionHistory.clear();  // Clear existing session data if none is found
            historyAdapter.notifyDataSetChanged();
        }
    }


    // Save updated session history
    private void saveSessionHistory() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> historySet = new HashSet<>(sessionHistory);  // Convert ArrayList back to Set
        String currentUserName = getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE).getString("current_user", "default_user");
        editor.putStringSet(KEY_HISTORY_PREFIX + currentUserName, historySet);
        editor.apply();
    }
}
