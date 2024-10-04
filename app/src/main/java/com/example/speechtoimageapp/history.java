package com.example.speechtoimageapp;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class history extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter historyAdapter;
    private ArrayList<String> sessionHistory;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "SessionHistoryPrefs";
    private static final String KEY_HISTORY = "history";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load session history from SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sessionHistory = new ArrayList<>(loadSessionHistory());

        // Set up the RecyclerView adapter with delete functionality
        historyAdapter = new HistoryAdapter(sessionHistory, new HistoryAdapter.OnDeleteClickListener() {
            @Override
            public void onDeleteClick(int position) {
                // Remove the selected session from the history list
                sessionHistory.remove(position);
                saveSessionHistory();  // Save the updated history back to SharedPreferences
                historyAdapter.notifyDataSetChanged();  // Notify the adapter to update the list
            }
        });

        recyclerView.setAdapter(historyAdapter);
    }

    // Load saved session history
    private Set<String> loadSessionHistory() {
        return sharedPreferences.getStringSet(KEY_HISTORY, new HashSet<>());
    }

    // Save updated session history
    private void saveSessionHistory() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> historySet = new HashSet<>(sessionHistory);  // Convert ArrayList back to Set
        editor.putStringSet(KEY_HISTORY, historySet);
        editor.apply();
    }
}
