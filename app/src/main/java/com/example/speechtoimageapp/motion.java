package com.example.speechtoimageapp;

import static java.lang.Float.isNaN;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

interface OnAdapterItemClickListener {
    void onAdapterItemClickListener(int position);
}

public class motion extends AppCompatActivity implements OnAdapterItemClickListener {

    // RecyclerView to display all recorded motions
    RecyclerView recyclerView;

    // Variables for handling dialog return values
    private String motionName;

    // Variables for RecordedMotion objects
    HashMap<String, ArrayList<RecordedMotion>> motionList = new HashMap<>();
    List<List<Float>> posList;
    List<Long> posIncrements;
    long startTime;
    long incrementTime;
    long endTime;
    // Flag that determines if motion is stationary
    boolean recordSentinel = false;

    // SharedPreferences constants
    private static final String PREFS_NAME = "SpeechToImageAppSettings";
    private static final String WORD_MODE_KEY = "WordMode"; // Key for saving word mode

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion);

        // Fetch current list of motions
        motionList = readMotions();
        if (motionList == null) {
            motionList = new HashMap<>();
        }

        // Set up RecyclerView
        recyclerView = findViewById(R.id.motionRecyclerView);
        setAdapter();

        // Fetch other UI elements
        Button startRecordButton = findViewById(R.id.recordButton);
        TextView recyclerViewLabel = findViewById(R.id.recyclerViewLabel);
        CardView touchView = findViewById(R.id.touchView);

        // Add Spinner for Word Mode
        Spinner wordModeSpinner = findViewById(R.id.spinner_word_mode);
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentMode = preferences.getString(WORD_MODE_KEY, "One Word");

        // Set the spinner selection based on the current mode
        switch (currentMode) {
            case "Two Words":
                wordModeSpinner.setSelection(1); // Assuming Two Words is the second item in the spinner
                break;
            case "Three Words":
                wordModeSpinner.setSelection(2); // Assuming Three Words is the third item in the spinner
                break;
            default:
                wordModeSpinner.setSelection(0); // Default to One Word
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
                editor.apply(); // Apply changes
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // No action needed
            }
        });

        // Create onClickListener on button
        startRecordButton.setOnClickListener(v -> {
            startRecordButton.setVisibility(View.GONE);
            recyclerViewLabel.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            touchView.setVisibility(View.VISIBLE);

            // Reset RecordedMotion variables
            posList = new ArrayList<>();
            posIncrements = new ArrayList<>();
            startTime = 0;
            endTime = 0;
            incrementTime = 0;
            recordSentinel = true;
        });

        View.OnTouchListener touchListener = (view, event) -> {
            if (!recordSentinel) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    posList.add(List.of(event.getRawX(), event.getRawY()));
                    startTime = System.currentTimeMillis();
                    incrementTime = startTime;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    posList.add(List.of(event.getRawX(), event.getRawY()));
                    posIncrements.add(System.currentTimeMillis() - incrementTime);
                    incrementTime = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_UP:
                    posList.add(List.of(event.getRawX(), event.getRawY()));
                    endTime = System.currentTimeMillis() + 100;
                    posIncrements.add(System.currentTimeMillis() - incrementTime);
                    posIncrements.add(100L);

                    for(long increments : posIncrements) {
                        Log.d("MOVEMENT", String.valueOf(increments));
                    }

                    // Create dialog to allow user to name new motion and set rotation
                    AlertDialog.Builder motionDialogBuilder = new AlertDialog.Builder(motion.this);
                    motionDialogBuilder.setTitle("Customize New Motion");
                    final View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_save_motion, null);
                    motionDialogBuilder.setView(dialogLayout);
                    motionDialogBuilder.setPositiveButton("Save", (dialogInterface, i) -> {
                        // Save motion with name input
                        EditText motionNameInput = dialogLayout.findViewById(R.id.motionNameInput);
                        EditText rotationInput = dialogLayout.findViewById(R.id.rotationInput);
                        String nInput = motionNameInput.getText().toString();
                        float rInput;
                        try {
                            rInput = Float.parseFloat(rotationInput.getText().toString());
                        } catch (Exception e) {
                            rInput = 0;
                        }
                        // If motion name input is not empty, process as usual. If it is, show error Toast to user.
                        if (!nInput.isEmpty()) {
                            // Create new RecordedMotion object
                            RecordedMotion newMotion;
                            // If the user specified a rotation value, create the newMotion with that value. Otherwise, default to 0.
                            if (!isNaN(rInput) && rInput != 0) {
                                newMotion = new RecordedMotion(posList, posIncrements, endTime - startTime, nInput, rInput);
                            } else {
                                newMotion = new RecordedMotion(posList, posIncrements, endTime - startTime, nInput, 0);
                            }
                            // If no RecordedMotions of this name exists, create a new blank entry.
                            if (!motionList.containsKey(nInput)) {
                                motionList.put(nInput, new ArrayList<>());
                            }
                            // Add new RecordedMotion to relevant entry.
                            ArrayList<RecordedMotion> tList = motionList.get(nInput);
                            tList.add(newMotion);
                            motionList.replace(nInput, tList);
                            saveMotions(motionList);
                        } else {
                            Toast.makeText(motion.this, "No motion name given. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    });
                    motionDialogBuilder.setNegativeButton("Cancel", (dialogInterface, i) -> { // Do nothing
                    });
                    AlertDialog motionDialog = motionDialogBuilder.create();

                    motionDialog.show();
                    startRecordButton.setVisibility(View.VISIBLE);
                    recyclerViewLabel.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.VISIBLE);
                    touchView.setVisibility(View.GONE);
                    recordSentinel = false;
                    return true;
            }
            return false;
        };

        touchView.setOnTouchListener(touchListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveMotions(motionList);
    }

    private void setAdapter() {
        RecyclerViewAdapter adapter = new RecyclerViewAdapter(motionList, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
    }

    public void onAdapterItemClickListener(int position) {
        int i = 0;
        for (Map.Entry<String, ArrayList<RecordedMotion>> entry : motionList.entrySet()) {
            if (position == i) {
                String key = entry.getKey();
                motionList.remove(key);
                break;
            } else {
                i++;
            }
        }
    }

    // Methods for saving and loading existing RecordedMotion objects from file.
    String fileName = "motionData.bin";

    public Boolean saveMotions(HashMap<String, ArrayList<RecordedMotion>> h) {
        ObjectOutputStream objectOutputStream = null;
        try {
            FileOutputStream fileOutputStream = this.openFileOutput(fileName, MODE_PRIVATE);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(h);
            objectOutputStream.close();
            fileOutputStream.close();
            return true;
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Unable to save new motion", Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, "An error has occurred", Toast.LENGTH_LONG).show();
            return false;
        } finally {
            if (objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public HashMap<String, ArrayList<RecordedMotion>> readMotions() {
        ObjectInputStream objectInputStream = null;
        HashMap<String, ArrayList<RecordedMotion>> motions;
        try {
            objectInputStream = new ObjectInputStream(openFileInput(fileName));
            motions = (HashMap<String, ArrayList<RecordedMotion>>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (objectInputStream != null) {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return motions;
    }
}
