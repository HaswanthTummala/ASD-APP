package com.example.speechtoimageapp;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        initializeSharedData();
    }

    private void initializeSharedData() {
        SharedPreferences sharedPreferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Initialize colorMap
        editor.putStringSet("colors", new HashSet<>(Arrays.asList("red", "green", "blue", "yellow")));
        // Initialize adjectiveMap
        editor.putStringSet("adjectives", new HashSet<>(Arrays.asList("big", "small", "fast", "slow")));
        editor.apply();
    }
}