package com.example.speechtoimageapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

public class settings extends AppCompatActivity {

    private static final String PREFS_NAME = "SpeechToImageAppSettings";
    private static final String WORD_MODE_KEY = "WordMode";  // Key for saving word mode

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Spinner wordModeSpinner = findViewById(R.id.spinner_word_mode);

        // Load the current mode from SharedPreferences
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentMode = preferences.getString(WORD_MODE_KEY, "One Word");

        // Set the spinner selection based on the current mode
        switch (currentMode) {
            case "Two Words":
                wordModeSpinner.setSelection(1);  // Assuming Two Words is the second item in the spinner
                break;
            case "Three Words":
                wordModeSpinner.setSelection(2);  // Assuming Three Words is the third item in the spinner
                break;
            default:
                wordModeSpinner.setSelection(0);  // Default to One Word
                break;
        }

        // Set listener to save the selection when changed
        wordModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String selectedMode = parentView.getItemAtPosition(position).toString();

                // Save the selected mode in SharedPreferences
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(WORD_MODE_KEY, selectedMode);
                editor.apply();  // Apply changes
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // No action needed
            }
        });
    }
}
