package com.example.speechtoimageapp;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class User extends AppCompatActivity {

    private static final String PREFS_NAME = "UserPrefs";
    private static final String USERS_KEY = "users";
    private static final String KILL_WORD_KEY = "kill_words";

    // Save user list and kill words in SharedPreferences
    public static void saveUserList(Context context, Map<String, String> userMap) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Set<String> userSet = new HashSet<>(userMap.keySet());
        editor.putStringSet(USERS_KEY, userSet); // Save users

        for (Map.Entry<String, String> entry : userMap.entrySet()) {
            editor.putString(KILL_WORD_KEY + "_" + entry.getKey(), entry.getValue()); // Save each user's kill word
        }
        editor.apply();
    }

    // Load user list from SharedPreferences
    public static Map<String, String> loadUserMap(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> userSet = prefs.getStringSet(USERS_KEY, new HashSet<>());

        Map<String, String> userMap = new HashMap<>();
        for (String user : userSet) {
            String killWord = prefs.getString(KILL_WORD_KEY + "_" + user, "");
            userMap.put(user, killWord);
        }
        return userMap;
    }

    // Add a new user with a kill word
    public static void addUser(Context context, String userName, String killWord) {
        Map<String, String> userMap = loadUserMap(context);
        if (!userMap.containsKey(userName)) {
            userMap.put(userName, killWord);
            saveUserList(context, userMap);
        }
    }
    // Update user's name and kill word
    public static void updateUser(Context context, String oldUserName, String newUserName, String newKillWord) {
        Map<String, String> userMap = loadUserMap(context);

        if (userMap.containsKey(oldUserName)) {
            // Remove old user and add updated details
            userMap.remove(oldUserName);
            userMap.put(newUserName, newKillWord);
            saveUserList(context, userMap);
        }
    }

    // Remove a user
    public static void removeUser(Context context, String userName) {
        Map<String, String> userMap = loadUserMap(context);
        userMap.remove(userName);
        saveUserList(context, userMap);
    }

    // Retrieve a kill word for a specific user
    public static String getKillWord(Context context, String userName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KILL_WORD_KEY + "_" + userName, "");
    }
}
