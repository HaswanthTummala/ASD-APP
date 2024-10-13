package com.example.speechtoimageapp;

import static java.lang.Float.isNaN;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.motion.widget.Debug;
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
import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    // Flag that handles rotation customization
    boolean rotationFlag = false;

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
        TextView countdownTextLabel = findViewById(R.id.countdownTextView);
        CardView touchView = findViewById(R.id.touchView);
        SeekBar angleSeekBar = findViewById(R.id.angleSeekBar);
        ImageView previewImageView = findViewById(R.id.previewImageView);

        // Add Spinner for Word Mode
        Spinner wordModeSpinner = findViewById(R.id.spinner_word_mode);
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentMode = preferences.getString(WORD_MODE_KEY, "One Word");

        // Set angleSeekBar's minimum and maximum values
        angleSeekBar.setMin(-10);
        angleSeekBar.setMax(10);

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
            countdownTextLabel.setText("Tap and start dragging anywhere to start recording new motion");
            countdownTextLabel.setVisibility(View.VISIBLE);

            // Reset RecordedMotion variables
            posList = new ArrayList<>();
            posIncrements = new ArrayList<>();
            startTime = 0;
            endTime = 0;
            incrementTime = 0;
            angleSeekBar.setProgress(0);
            recordSentinel = true;
        });

        // Set OnTouchListener for recording motions
        View.OnTouchListener touchListener = (view, event) -> {
            // Only listen when user wants to record new motion
            if (!recordSentinel) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    countdownTextLabel.setVisibility(View.GONE);
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
                    touchView.setVisibility(View.GONE);

                    // Create dialog to allow user to name new motion and set rotation
                    AlertDialog.Builder motionDialogBuilder = new AlertDialog.Builder(motion.this);
                    AlertDialog motionDialog;
                    motionDialogBuilder.setTitle("Customize New Motion");
                    final View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_save_motion, null);
                    motionDialogBuilder.setView(dialogLayout);

                    EditText motionNameInput = dialogLayout.findViewById(R.id.motionNameInput);
                    EditText rotationInput = dialogLayout.findViewById(R.id.rotationInput);

                    // If user wants custom rotation
                    motionDialogBuilder.setNeutralButton("Customize Rotation and Save", (dialogInterface, i) -> {
                        String nInput = motionNameInput.getText().toString();
                        final RecordedMotion[] newMotion = new RecordedMotion[1]; // Must be a final one-object array to be written to by another thread.
                        List<Float> customRotation = new ArrayList<>();

                        // Set initial state for all UI elements
                        countdownTextLabel.setText("Slider at bottom-left controls rotation. Rotation recording will begin in:");
                        countdownTextLabel.setVisibility(View.VISIBLE);
                        angleSeekBar.setVisibility(View.VISIBLE);

                        // Thread that moves image according to specified motion
                        List<List<Float>> tempPosList = new ArrayList<>(posList);
                        List<Long> tempPosIncrements = new ArrayList<>(posIncrements);

                        HandlerThread motionThread = new HandlerThread("MotionThreadHandler");
                        motionThread.start();
                        Handler motionHandler = new Handler(motionThread.getLooper());
                        Runnable motionRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (!tempPosList.isEmpty()) {
                                    // Get and remove next position
                                    List<Float> pos = tempPosList.remove(0);
                                    // Get and remove current position duration
                                    long currentIncrement = tempPosIncrements.remove(0);
                                    // Set image's position to next position
                                    runOnUiThread(() -> {
                                        previewImageView.setX(pos.get(0) - previewImageView.getWidth() / 2F);
                                        previewImageView.setY(pos.get(1) - previewImageView.getHeight() / 2F);
                                    });
                                    // Repeat until posList is empty
                                    motionHandler.postDelayed(this, currentIncrement);
                                }
                            }
                        };

                        // Thread that rotates image in realtime based on user input, and saves results
                        AtomicInteger rotationCount = new AtomicInteger(0);
                        long durationPerIncrement = (endTime - startTime) / 500;

                        HandlerThread rotationThread = new HandlerThread("RotationHandlerThread");
                        rotationThread.start();
                        Handler rotationHandler = new Handler(rotationThread.getLooper());
                        Runnable rotationRunnable = new Runnable() {
                            @Override
                            public void run() {
                                rotationCount.getAndIncrement();
                                // Get image's current rotation angle
                                float currentAngle = previewImageView.getRotation();
                                // Add image's current angle to the returned angle list
                                customRotation.add(currentAngle);
                                // Set image's new angle to its current angle + the user-specified increment/decrement.
                                float angle = currentAngle + angleSeekBar.getProgress();
                                // Ensure the new angle is between 0 and 360
                                if (angle >= 360) { angle = angle - 360; } else if (angle < 0) { angle = angle + 360; }
                                float finalAngle = angle; // Must be final to be accessed by another thread.
                                runOnUiThread(() -> previewImageView.setRotation(finalAngle));
                                // Repeat until finished
                                if (rotationCount.get() < 500) {
                                    rotationHandler.postDelayed(this, durationPerIncrement);
                                } else {
                                    // Once recording custom rotation is finished, save the new motion.
                                    newMotion[0] = new RecordedMotion(posList, posIncrements, endTime - startTime, nInput, customRotation);

                                    // Only the UI thread can touch UI elements
                                    runOnUiThread(() -> {
                                        countdownTextLabel.setVisibility(View.GONE);
                                        angleSeekBar.setVisibility(View.GONE);
                                        previewImageView.setVisibility(View.GONE);

                                        // If no RecordedMotions of this name exists, create a new blank entry.
                                        if (!motionList.containsKey(nInput)) {
                                            motionList.put(nInput, new ArrayList<>());
                                        }
                                        // Add new RecordedMotion to relevant entry.
                                        ArrayList<RecordedMotion> tList = motionList.get(nInput);
                                        tList.add(newMotion[0]);
                                        motionList.replace(nInput, tList);
                                        saveMotions(motionList);

                                        startRecordButton.setVisibility(View.VISIBLE);
                                        recyclerViewLabel.setVisibility(View.VISIBLE);
                                        recyclerView.setVisibility(View.VISIBLE);
                                        recordSentinel = false;
                                    });
                                }
                            }
                        };

                        // Thread that handles the initial countdown. Posts Runnables to the other two threads once finished.
                        HandlerThread countdownThread = new HandlerThread("CountdownHandlerThread");
                        countdownThread.start();

                        AtomicInteger countdown = new AtomicInteger(3);
                        Handler countdownHandler = new Handler(countdownThread.getLooper());
                        countdownHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                runOnUiThread(() -> {
                                    countdownTextLabel.setText("" + countdown.getAndDecrement());
                                });
                                if (countdown.get() > 0) {
                                    countdownHandler.postDelayed(this, 1000);
                                } else {
                                    runOnUiThread(() -> previewImageView.setVisibility(View.VISIBLE));
                                    motionHandler.post(motionRunnable);
                                    rotationHandler.post(rotationRunnable);
                                }
                            }
                        });
                    });

                    motionDialogBuilder.setPositiveButton("Save", (dialogInterface, i) -> {
                        // Save motion with name input
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
                    motionDialog = motionDialogBuilder.create();

                    motionDialog.show();
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
            e.printStackTrace();
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
