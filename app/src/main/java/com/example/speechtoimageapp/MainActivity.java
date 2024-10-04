package com.example.speechtoimageapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridLayout;  // Import GridLayout
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // Variables
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private GridLayout gridLayout;  // Declare GridLayout variable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*---------------------Hooks------------------------*/
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        gridLayout = findViewById(R.id.gridLayout);  // Initialize GridLayout

        setSupportActionBar(toolbar);
        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_home);

        // Set column count based on orientation
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            gridLayout.setColumnCount(3); // For tablets or landscape
        } else {
            gridLayout.setColumnCount(1); // For phones or portrait
        }
    }

    public void goToSpeechPage(View view) {
        Intent intent = new Intent(MainActivity.this, Speech.class);
        startActivity(intent);
    }

    public void goToUploadImagePage(View view) {
        Intent intentUpload = new Intent(MainActivity.this, UploadImage.class);
        startActivity(intentUpload);
    }

    public void goToMotionPage(View view) {
        Intent intentMotion = new Intent(MainActivity.this, motion.class);
        startActivity(intentMotion);
    }

    public void goToHistoryPage(View view) {
        Intent intentHistory = new Intent(MainActivity.this, history.class);
        startActivity(intentHistory);
    }

    public void goToDemoPage(View view) {
        Intent intentDemo = new Intent(MainActivity.this, demo.class);
        startActivity(intentDemo);
    }

    public void goToSettingsPage(View view) {
        Intent intentSetting = new Intent(MainActivity.this, settings.class);
        startActivity(intentSetting);
    }

    public void goToHelpPage(View view) {
        Intent intentHelp = new Intent(MainActivity.this, help.class);
        startActivity(intentHelp);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.nav_home:
                // Handle Home navigation
                break;

            case R.id.nav_speech:
                Intent intentSpeech = new Intent(MainActivity.this, Speech.class);
                startActivity(intentSpeech);
                break;
            case R.id.nav_upload:
                Intent intentUpload = new Intent(MainActivity.this, UploadImage.class);
                startActivity(intentUpload);
                break;
            case R.id.nav_motion:
                Intent intentMotion = new Intent(MainActivity.this, motion.class);
                startActivity(intentMotion);
                break;
            case R.id.nav_history:
                Intent intentHistory = new Intent(MainActivity.this, history.class);
                startActivity(intentHistory);
                break;
            case R.id.nav_demo:
                Intent intentDemo = new Intent(MainActivity.this, demo.class);
                startActivity(intentDemo);
                break;
            case R.id.nav_settings:
                Intent intentSetting = new Intent(MainActivity.this, settings.class);
                startActivity(intentSetting);
                break;
            case R.id.nav_help:
                Intent intentHelp = new Intent(MainActivity.this, help.class);
                startActivity(intentHelp);
                break;
            default:
                Toast.makeText(MainActivity.this, "Unknown menu item", Toast.LENGTH_SHORT).show();
                break;
        }

        // Close the navigation drawer after selecting an item
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}
