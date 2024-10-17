package com.example.speechtoimageapp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class User {

    private static final String PREFS_NAME = "UserPrefs";
    private static final String USERS_KEY = "users";

    // Save user list in SharedPreferences
    public static void saveUserList(Context context, ArrayList<String> userList) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> userSet = new HashSet<>(userList); // Convert ArrayList to Set
        editor.putStringSet(USERS_KEY, userSet);
        editor.apply(); // Save changes
    }

    // Load user list from SharedPreferences
    public static ArrayList<String> loadUserList(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> userSet = prefs.getStringSet(USERS_KEY, new HashSet<>());
        return new ArrayList<>(userSet); // Convert Set to ArrayList
    }

    // Add a new user
    public static void addUser(Context context, String userName) {
        ArrayList<String> userList = loadUserList(context);
        if (!userList.contains(userName)) {
            userList.add(userName);
            saveUserList(context, userList);
        }
    }

    // Remove a user
    public static void removeUser(Context context, String userName) {
        ArrayList<String> userList = loadUserList(context);
        userList.remove(userName);
        saveUserList(context, userList);
    }
}
