package com.example.speechtoimageapp;

import static java.lang.Float.isNaN;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

interface OnAdapterItemClickListener {
    void onAdapterItemClickListener(int position, String viewName);
}

public class motion extends AppCompatActivity implements OnAdapterItemClickListener {

    // RecyclerView to display all recorded motions
    RecyclerView recyclerView;
    RecyclerViewAdapter adapter;

    // Variables for handling dialog return values
    private String motionName;
    private ActivityResultLauncher<Intent> audioPickerLauncher;
    private String selectedMotionName;

    // Variables for RecordedMotion objects
    HashMap<String, ArrayList<RecordedMotion>> motionList = new HashMap<>();
    List<List<Float>> posList;
    List<Long> posIncrements;
    long startTime;
    long incrementTime;
    long endTime;
    boolean recordSentinel = false; // Used to determine whether onTouch events should be processed
    List<Float> customRotation;

    // SharedPreferences constants
    private static final String PREFS_NAME = "SpeechToImageAppSettings";
    private static final String WORD_MODE_KEY = "WordMode"; // Key for saving word mode

    // Fetch other UI elements
    Button startRecordButton;
    TextView recyclerViewLabel;
    TextView countdownTextLabel;
    CardView touchView;
    SeekBar angleSeekBar;
    ImageView previewImageView;
    TextView wordModeLabel;
    Spinner wordModeSpinner;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion);

        // Fetch current list of motions
        motionList = readMotions();

        // Set up RecyclerView
        recyclerView = findViewById(R.id.motionRecyclerView);
        setAdapter();

        // Check if there are any motions
        if (adapter.getItemCount() == 0) {
            // Load default motions
            motionList = loadDefaultMotions();
            Log.i("motionListLength", String.valueOf(motionList.size()));
            saveMotions(motionList);
            setAdapter();
        }

        // Fetch other UI elements
        startRecordButton = findViewById(R.id.recordButton);
        recyclerViewLabel = findViewById(R.id.recyclerViewLabel);
        countdownTextLabel = findViewById(R.id.countdownTextView);
        touchView = findViewById(R.id.touchView);
        angleSeekBar = findViewById(R.id.angleSeekBar);
        previewImageView = findViewById(R.id.previewImageView);
        wordModeLabel = findViewById(R.id.word_mode_label);

        // Add Spinner for Word Mode
        wordModeSpinner = findViewById(R.id.spinner_word_mode);
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

        // Initialize audio picker launcher
        audioPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (result.getData() != null) {
                            Uri audioUri = result.getData().getData();
                            saveAudioFile(audioUri, selectedMotionName);
                        }
                    }
                }
        );

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
            wordModeLabel.setVisibility(View.GONE);
            wordModeSpinner.setVisibility(View.GONE);
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
                    endTime = System.currentTimeMillis();
                    posIncrements.add(System.currentTimeMillis() - incrementTime);
                    touchView.setVisibility(View.GONE);

                    // Create dialog to allow user to name new motion and set rotation
                    AlertDialog motionDialog = motionDialog(false, null);
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
    private void saveAudioFile(Uri audioUri, String motionName) {
        if (audioUri == null || motionName == null || motionName.isEmpty()) {
            Toast.makeText(this, "Invalid motion name or audio file.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String audioFileName = motionName + ".mp3";
            FileOutputStream fos = openFileOutput(audioFileName, MODE_PRIVATE);
            InputStream inputStream = getContentResolver().openInputStream(audioUri);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            fos.close();
            inputStream.close();

            Toast.makeText(this, "Audio saved for motion: " + motionName, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteAudioFile(String motionName) {
        String audioFileName = motionName + ".mp3";
        boolean deleted = deleteFile(audioFileName);
        if (deleted) {
            Toast.makeText(this, "Audio file deleted for motion: " + motionName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No audio file found for motion: " + motionName, Toast.LENGTH_SHORT).show();
        }
    }

    // AlertDialog for customizing and saving motions
    private AlertDialog motionDialog(boolean editMode, RecordedMotion motion) {
        AlertDialog motionDialog;
        AlertDialog.Builder motionDialogBuilder = new AlertDialog.Builder(motion.this);
        motionDialogBuilder.setTitle("Customize New Motion");
        final View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_save_motion, null);
        motionDialogBuilder.setView(dialogLayout);

        EditText motionNameInput = dialogLayout.findViewById(R.id.motionNameInput);
        EditText rotationInput = dialogLayout.findViewById(R.id.rotationInput);

        // If user wants custom rotation
        motionDialogBuilder.setNeutralButton("Customize Rotation and Save", (dialogInterface, i) -> {
            String nInput = motionNameInput.getText().toString();
            final RecordedMotion[] newMotion = new RecordedMotion[1]; // Must be a final one-object array to be written to by another thread.
            customRotation = new ArrayList<>();

            // Set initial state for all UI elements
            wordModeLabel.setVisibility(View.GONE);
            wordModeSpinner.setVisibility(View.GONE);
            startRecordButton.setVisibility(View.GONE);
            recyclerViewLabel.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            countdownTextLabel.setText("Slider at bottom-left controls rotation. Rotation recording will begin in:");
            countdownTextLabel.setVisibility(View.VISIBLE);
            angleSeekBar.setVisibility(View.VISIBLE);

            HandlerThread motionThread = new HandlerThread("MotionThreadHandler");
            motionThread.start();
            Handler motionHandler = new Handler(motionThread.getLooper());
            Runnable motionRunnable;
            if (editMode) {
                motionRunnable = () -> {
                    for (int j = 0; j < motion.posList.size(); j++) {
                        if (j < motion.posIncrements.size()) {
                            // Get and remove current position duration
                            long currentIncrement = motion.posIncrements.get(j);
                            int index = j;

                            runOnUiThread(() -> {
                                previewImageView.setX(motion.posList.get(index).get(0) - previewImageView.getWidth() / 2F);
                                previewImageView.setY(motion.posList.get(index).get(1) - previewImageView.getHeight() / 2F);
                            });
                            try { Thread.sleep(currentIncrement); } catch (InterruptedException e) { throw new RuntimeException(e); }
                        }
                    }
                };
            } else {
                motionRunnable = () -> {
                    for (int j = 0; j < posList.size(); j++) {
                        if (j < posIncrements.size()) {
                            // Get and remove current position duration
                            long currentIncrement = posIncrements.get(j);
                            int index = j;

                            runOnUiThread(() -> {
                                previewImageView.setX(posList.get(index).get(0) - previewImageView.getWidth() / 2F);
                                previewImageView.setY(posList.get(index).get(1) - previewImageView.getHeight() / 2F);
                            });
                            try { Thread.sleep(currentIncrement); } catch (InterruptedException e) { throw new RuntimeException(e); }
                        }
                    }
                };
            }

            // Thread that rotates image in realtime based on user input, and saves results
            // AtomicInteger rotationCount = new AtomicInteger(0);
            long durationPerIncrement;
            if (editMode) { durationPerIncrement = motion.duration / 500; } else { durationPerIncrement = (endTime - startTime) / 500; }

            HandlerThread rotationThread = new HandlerThread("RotationHandlerThread");
            rotationThread.start();
            Handler rotationHandler = new Handler(rotationThread.getLooper());
            Runnable rotationRunnable = () -> {
                for (int j = 0; j < 500; j++) {
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
                    try { Thread.sleep(durationPerIncrement); } catch (InterruptedException e) { throw new RuntimeException(e); }
                }

                if (!editMode) {
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
                        adapter.notifyDataSetChanged();

                        wordModeLabel.setVisibility(View.VISIBLE);
                        wordModeSpinner.setVisibility(View.VISIBLE);
                        startRecordButton.setVisibility(View.VISIBLE);
                        recyclerViewLabel.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.VISIBLE);
                        recordSentinel = false;
                    });
                } else {
                    // Only the UI thread can touch UI elements
                    runOnUiThread(() -> {
                        countdownTextLabel.setVisibility(View.GONE);
                        angleSeekBar.setVisibility(View.GONE);
                        previewImageView.setVisibility(View.GONE);

                        // Save angleList to the motion being edited
                        motion.angleList = customRotation;
                        // Overwrite old motion with new.
                        ArrayList<RecordedMotion> tList = motionList.get(motion.name);

                        // If name is different, modify map accordingly.
                        String newName = motionNameInput.getText().toString();
                        if (!newName.isEmpty() && !newName.equals(motion.name)) {
                            for (int j = 0; j < tList.size(); j++) {
                                if (tList.get(j).time == motion.time) {
                                    tList.remove(j);
                                    break;
                                }
                            }
                            motion.name = newName;
                            if (!motionList.containsValue(motion.name)) {
                                ArrayList<RecordedMotion> TEMP = new ArrayList<>();
                                TEMP.add(motion);
                                // Add new entry to motionList.
                                motionList.put(motion.name, TEMP);
                            } else {
                                ArrayList<RecordedMotion> TEMP = motionList.get(motion.name);
                                TEMP.add(motion);
                                // Overwrite old motionList entry with new.
                                motionList.replace(motionName, TEMP);
                            }
                        } else {
                            for (int j = 0; j < tList.size(); j++) {
                                if (tList.get(j).time == motion.time) { tList.set(j, motion); break; }
                            }
                            // Overwrite old motionList entry with new.
                            motionList.replace(motion.name, tList);
                        }

                        // Save new list.
                        saveMotions(motionList);
                        adapter.notifyDataSetChanged();

                        wordModeLabel.setVisibility(View.VISIBLE);
                        wordModeSpinner.setVisibility(View.VISIBLE);
                        startRecordButton.setVisibility(View.VISIBLE);
                        recyclerViewLabel.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.VISIBLE);
                        recordSentinel = false;
                    });
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
        // Add "Upload Audio" button
        Button uploadAudioButton = dialogLayout.findViewById(R.id.uploadAudioButton);
        uploadAudioButton.setOnClickListener(view -> {
            selectedMotionName = motionNameInput.getText().toString().trim();
            if (selectedMotionName.isEmpty()) {
                Toast.makeText(this, "Enter a motion name before uploading audio.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent audioIntent = new Intent(Intent.ACTION_GET_CONTENT);
            audioIntent.setType("audio/*");
            audioPickerLauncher.launch(audioIntent);
        });


        motionDialogBuilder.setPositiveButton("Save", (dialogInterface, i) -> {
            if (editMode) {
                // Only change the motion's rotations per second value, and save.
                float rInput;
                try {
                    rInput = Float.parseFloat(rotationInput.getText().toString());
                } catch (Exception e) {
                    rInput = 0;
                }
                motion.rotationsPerSecond = rInput;

                // Overwrite old motion with new. If name is different, change accordingly
                ArrayList<RecordedMotion> tList = motionList.get(motion.name);
                String oldName = motion.name;
                String newName = motionNameInput.getText().toString();
                if (!newName.isEmpty() && !newName.equals(motion.name)) {
                    for (int j = 0; j < tList.size(); j++) {
                        if (tList.get(j).time == motion.time) {
                            tList.remove(j);
                            motionList.replace(oldName, tList);
                            if(motionList.get(oldName).isEmpty()) {
                                motionList.remove(oldName);
                            }
                            break;
                        }
                    }
                    motion.name = newName;
                    if (!motionList.containsValue(motion.name)) {
                        ArrayList<RecordedMotion> TEMP = new ArrayList<>();
                        TEMP.add(motion);
                        // Add new entry to motionList.
                        motionList.put(motion.name, TEMP);
                    } else {
                        ArrayList<RecordedMotion> TEMP = motionList.get(motion.name);
                        TEMP.add(motion);
                        // Overwrite old motionList entry with new.
                        motionList.replace(motionName, TEMP);
                    }
                } else {
                    for (int j = 0; j < tList.size(); j++) {
                        if (tList.get(j).time == motion.time) { tList.set(j, motion); break; }
                    }
                    // Overwrite old motionList entry with new.
                    motionList.replace(motion.name, tList);
                }

                // Save updated list.
                saveMotions(motionList);
                adapter.notifyDataSetChanged();
            } else {
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
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(motion.this, "No motion name given. Please try again.", Toast.LENGTH_SHORT).show();
                }
            }
            runOnUiThread(() -> {
                wordModeLabel.setVisibility(View.VISIBLE);
                wordModeSpinner.setVisibility(View.VISIBLE);
                startRecordButton.setVisibility(View.VISIBLE);
                recyclerViewLabel.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.VISIBLE);
                recordSentinel = false;
            });
            adapter.notifyDataSetChanged();
        });

        motionDialogBuilder.setNegativeButton("Cancel", (dialogInterface, i) -> runOnUiThread(() -> {
            wordModeLabel.setVisibility(View.VISIBLE);
            wordModeSpinner.setVisibility(View.VISIBLE);
            startRecordButton.setVisibility(View.VISIBLE);
            recyclerViewLabel.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.VISIBLE);
        }));

        motionDialog = motionDialogBuilder.create();
        return motionDialog;
    }

    private void setAdapter() {
        adapter = new RecyclerViewAdapter(motionList, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
    }

    public void onAdapterItemClickListener(int position, String viewName) {
        // HashMaps cannot be consistently traversed via index positioning. Instead, extract and traverse the entry set's keys.
        // Extract and sort keys into array.
        ArrayList<String> keyArray = new ArrayList<>(motionList.keySet());
        Collections.sort(keyArray);

        // Extract map entries in sorted order
        ArrayList<RecordedMotion> sortedMotionList = new ArrayList<>();
        for (String key : keyArray) { sortedMotionList.addAll(motionList.get(key)); }

        RecordedMotion selectedMotion = sortedMotionList.get(position);

        switch (viewName) {
            case "delete":
                ArrayList<RecordedMotion> tList = motionList.get(selectedMotion.name);
                for (int i = 0; i < tList.size(); i++) {
                    if (tList.get(i).time == selectedMotion.time) {
                        tList.remove(i);
                        break;
                    }
                }
                if (tList.isEmpty()) {
                    deleteAudioFile(selectedMotion.name); // Delete associated audio file
                    motionList.remove(selectedMotion.name);
                } else {
                    motionList.replace(selectedMotion.name, tList);
                }
                saveMotions(motionList);
                adapter.notifyDataSetChanged();
                break;

            case "edit":
                AlertDialog dialog = motionDialog(true, selectedMotion);
                dialog.show();
                break;

            case "play":
                runOnUiThread(() -> {
                    wordModeLabel.setVisibility(View.GONE);
                    wordModeSpinner.setVisibility(View.GONE);
                    startRecordButton.setVisibility(View.GONE);
                    recyclerViewLabel.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                    previewImageView.setVisibility(View.VISIBLE);


                });

                HandlerThread motionThread = new HandlerThread("MotionThreadHandler");
                motionThread.start();
                Handler motionHandler = new Handler(motionThread.getLooper());

                Runnable motionRunnable = () -> {
                    for (int j = 0; j < selectedMotion.posList.size(); j++) {
                        if (j < selectedMotion.posIncrements.size()) {
                            // Get and remove current position duration
                            long currentIncrement = selectedMotion.posIncrements.get(j);
                            int index = j;

                            runOnUiThread(() -> {
                                previewImageView.setX(selectedMotion.posList.get(index).get(0) - previewImageView.getWidth() / 2F);
                                previewImageView.setY(selectedMotion.posList.get(index).get(1) - previewImageView.getHeight() / 2F);
                            });
                            try { Thread.sleep(currentIncrement); } catch (InterruptedException e) { throw new RuntimeException(e); }
                        }
                    }
                };

                motionHandler.post(motionRunnable);

                HandlerThread rotationThread = new HandlerThread("RotationHandlerThread");
                rotationThread.start();
                Handler rotationHandler = new Handler(rotationThread.getLooper());

                Runnable rotationRunnable = () -> {
                    if (selectedMotion.angleList != null) {
                        // If there is a customized rotation
                        for (int j = 0; j < 500; j++) {
                            int finalJ = j;
                            runOnUiThread(() -> previewImageView.setRotation(selectedMotion.angleList.get(finalJ)));
                            try {
                                Thread.sleep(selectedMotion.duration / 500);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } else {
                        // Apply default rotation if no custom rotation
                        float degreesPerIncrement = ((((float) selectedMotion.duration / 1000) * selectedMotion.rotationsPerSecond) / 500) * 360;

                        for (int i = 0; i < 500; i++) {
                            final int rotationIndex = i;  // Create a final copy of the loop variable
                            runOnUiThread(() -> previewImageView.setRotation(rotationIndex * degreesPerIncrement));
                            try {
                                Thread.sleep(selectedMotion.duration / 500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }

                    runOnUiThread(() -> {
                        wordModeLabel.setVisibility(View.VISIBLE);
                        wordModeSpinner.setVisibility(View.VISIBLE);
                        startRecordButton.setVisibility(View.VISIBLE);
                        recyclerViewLabel.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.VISIBLE);
                        previewImageView.setVisibility(View.GONE);
                    });
                };

                rotationHandler.post(rotationRunnable);
                break;
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

    public HashMap<String, ArrayList<RecordedMotion>> loadDefaultMotions() {
        ObjectInputStream objectInputStream = null;
        HashMap<String, ArrayList<RecordedMotion>> motions;
        try {
            objectInputStream = new ObjectInputStream(getAssets().open("defaultMotionData.bin"));
            motions = (HashMap<String, ArrayList<RecordedMotion>>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            Log.e("DefMotionError", "Error loading default motions", e);
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