package com.example.speechtoimageapp;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Looper;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.caverock.androidsvg.SVG;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Speech extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 1;
    private ImageView imageView;
    private SpeechRecognizer speechRecognizer;
    private Handler handler;
    private boolean isListening = true;
    private SoundPool soundPool;  // SoundPool for audio playback
    private HashMap<String, Integer> soundMap;  // Map to store sound IDs for objects

    // Define color and motion mappings
    private HashMap<String, Integer> colorMap = new HashMap<>();
    private HashMap<String, ArrayList<RecordedMotion>> motionMap = new HashMap<>();
    private File imageFolder;

    private StringBuilder spokenWords = new StringBuilder();
    private StringBuilder unrecognizedWords = new StringBuilder();
    private String startTime;
    private Random random = new Random();
    private String currentColor = "";
    private String currentMotion = "";
    private String currentObject = "";
    private String currentAdjective = "";

    private int wordLimit = 1;
    private HashMap<String, String> adjectiveMap = new HashMap<>();
    private ArrayList<String> userList;

    private HandlerThread motionThread = new HandlerThread("MotionHandlerThread");
    private HandlerThread rotationThread = new HandlerThread("RotationHandlerThread");
    private Handler motionHandler;
    private Handler rotationHandler;
    // Class-level variables
    private Handler autoResponseHandler = new Handler();
    private Runnable autoResponseRunnable;
    private boolean isAutoResponseEnabled = false;
    private boolean isUserSpeaking = false;
    private TextToSpeech textToSpeech;
    private TextView autoResponseTextView;  // TextView to display auto-response text
    private long lastUserInteractionTime = 0; // Track the last time user spoke
    private Handler restartHandler = new Handler();
    private boolean autoResponseActive = false;  // To track if auto-response is currently active
    private static final long AUTO_RESPONSE_DELAY = 5000;  // 5 seconds for each auto-response delay
    private static final long CHECK_SILENCE_INTERVAL = 10000;  // Check for user silence every 1.5 seconds
    private String currentKillWord = ""; // Kill word specific to the selected user
    private final List<String> validWordBuffer = new ArrayList<>();  // Buffer to store valid words in sequence
    private long userSelectedDelay = 10000L; // Default to 10 seconds
    // Class-level variable to hold the fixed interval for continuous auto-response
    private static final long CONTINUOUS_INTERVAL = 10000L; // 10 seconds for continuous auto-response
    private boolean isInitialResponse = true; // Track if this is the first response after silence
    private static final long SOUND_COOLDOWN_MS = 500;  // 500 ms cooldown period
    private boolean isSoundCooldown = false;
    private boolean sessionSaved = false;
    private File motionFolder; // Default internal files directory for motion sounds
    private static final long AUTO_RESPONSE_COOLDOWN = 3000L; // 3 seconds cooldown

    // Silence check interval
    private static final long SILENCE_CHECK_INTERVAL = 10000; // 5 second interval for checking silence
    private static final long AUTO_RESPONSE_TRIGGER_DELAY = 500; // 5 seconds of silence to trigger auto-response
    // Silence check handler
    private Handler silenceCheckHandler = new Handler();
    private Runnable silenceCheckRunnable = null;
    private TextView recognizedWordsTextView;
    StringBuilder unrecognizedWordsBuilder = new StringBuilder();
    private TextView fullRecognitionTextView; // Declare the TextView
    private final Handler soundHandler = new Handler();
    private static final long BUFFER_TIMEOUT = 30000;  // 30 seconds timeout for buffer reset
    private boolean assetsCopied = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech);
        // Initialize TextView for displaying auto-responses
        autoResponseTextView = findViewById(R.id.autoResponseTextView);
// Initialize variables properly
        isAutoResponseEnabled = false; // Ensure auto-response is disabled by default

        if (autoResponseTextView == null) {
            Log.e("InitializationError", "autoResponseTextView is null. Check the layout XML.");
        }
        lastUserInteractionTime = System.currentTimeMillis();  // Initialize last interaction time
        initializeAndSaveData();

        final Intent recognizerIntent = configureRecognizerIntent();

// Initialize the SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        } else {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
            finish();  // Close the activity if speech recognition is not available
            return;
        }


        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());
            }
        });

        // Enable StrictMode for debugging potential issues
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());

        imageView = findViewById(R.id.imageView);
        handler = new Handler();
      //  setVolumeToMax();
        setVolumeToMin();
        initializeColorMap();
        initializeAdjectiveMap();
        handleFirstRun(); // Ensure files are copied on first run
        motionMap = readMotions();
        validateSharedData(); // Ensure shared data is ready
        initializeComponents(); // Initialize resources
        imageFolder = new File(getFilesDir(), "UploadedImages");
        motionFolder = getFilesDir(); // Default internal storage for motion sounds
        // Ensure image folder exists
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }

        SharedPreferences preferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);
        String wordMode = preferences.getString("WordMode", "One Word");
        wordLimit = wordMode.equals("Two Words") ? 2 : wordMode.equals("Three Words") ? 3 : 1;

        // Initialize maps
        if (colorMap == null) {
            initializeColorMap();
        }
        if (motionMap == null) {
            motionMap = new HashMap<>(); // Initialize empty if data not available
        }
        if (adjectiveMap == null) {
            initializeAdjectiveMap();
        }


        Map<String, String> userMap = User.loadUserMap(this);
        userList = new ArrayList<>(userMap.keySet()); // Extract user names from the map
        promptForUserSelection();


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
        }


        loadDefaultImages();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        // Initialize AudioAttributes for SoundPool
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        // Initialize SoundPool with the defined AudioAttributes
        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)  // Define the maximum number of simultaneous sounds
                .setAudioAttributes(audioAttributes)
                .build();

        // Set OnLoadCompleteListener to ensure sounds are ready before playing
        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) {
                Log.d("SoundDebug", "Sound loaded successfully, sampleId: " + sampleId);
            } else {
                Log.e("SoundDebug", "Error loading sound, sampleId: " + sampleId);
            }
        });

        // Set the device volume to 50% by default
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // Get the maximum volume for STREAM_MUSIC
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            // Calculate 50% of the maximum volume
            int volumeToSet = maxVolume;

            // Set the volume to 50%
            audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    volumeToSet,
                    AudioManager.FLAG_SHOW_UI // Optional: Show volume slider
            );

            Log.d("VolumeControl", "Default volume set to 50%.");
        } else {
            Log.e("VolumeControl", "AudioManager is null. Cannot set volume.");
        }



        // Initialize the sound map or any other setup, like loading motion sounds
        soundMap = new HashMap<>();
        
        startSilenceCheck();  // Any other setup you may have in onCreate



        motionThread.start();
        rotationThread.start();
        motionHandler = new Handler(motionThread.getLooper());
        rotationHandler = new Handler(rotationThread.getLooper());
    }
    private void enableFullScreenMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void startSilenceCheck() {
        if (!isAutoResponseEnabled) return; // Only start if auto-response is enabled

        silenceCheckRunnable = new Runnable() {
            @Override
            public void run() {
                long timeSinceLastInteraction = System.currentTimeMillis() - lastUserInteractionTime;

                if (timeSinceLastInteraction >= userSelectedDelay && validWordBuffer.isEmpty()) {
                    // Trigger auto-response if no true words are detected and the delay has elapsed
                    if (!autoResponseActive) {
                        Log.d("SilenceCheck", "No true words detected. Triggering auto-response.");
                        triggerAutoResponse();
                    }
                } else {
                    // True words detected or within delay
                    Log.d("SilenceCheck", "True words detected or within delay. Resetting timer.");
                }

                // Reschedule the silence check
                silenceCheckHandler.postDelayed(this, 1000); // Check every second
            }
        };

        // Start the silence check loop
        silenceCheckHandler.postDelayed(silenceCheckRunnable, 1000);
    }

    private void initializeSpeechRecognition() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            startSpeechRecognition(); // Start recognition after initialization
        } else {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
            finish(); // Exit if the device does not support speech recognition
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Call the superclass implementation

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeSpeechRecognition(); // Initialize speech recognition after permission is granted
            } else {
                Toast.makeText(this, "Permission denied. Speech recognition won't work.", Toast.LENGTH_SHORT).show();
            }
        }
    }





    private void handleFirstRun() {
        SharedPreferences preferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isFirstRun = preferences.getBoolean("isFirstRun", true);

        if (isFirstRun) {
            copyAssetsToInternalStorage();

            // Mark the first run as complete
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("isFirstRun", false);
            editor.apply();
        }

        // Validate assets are present after the first run
        File imageFolder = new File(getFilesDir(), "UploadedImages");
        File soundFolder = new File(getFilesDir(), "DefaultSounds");

        if (!imageFolder.exists() || !soundFolder.exists() || imageFolder.listFiles() == null || imageFolder.listFiles().length == 0) {
            Log.e("AssetValidation", "Images or sounds are missing!");
            Toast.makeText(this, "Initializing resources. Please wait.", Toast.LENGTH_SHORT).show();
            copyAssetsToInternalStorage();
        }

        // Delay initialization until assets are validated
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            assetsCopied = true;
        }, 3000);
    }


    private boolean areResourcesInitialized() {
        if (!assetsCopied) return false;
        if (motionMap == null || motionMap.isEmpty()) return false;
        if (imageFolder == null || !imageFolder.exists() || imageFolder.listFiles().length == 0) return false;
        return true;
    }

    private boolean loadDefaultImages() {
        File imageFolder = new File(getFilesDir(), "UploadedImages");
        if (!imageFolder.exists() || imageFolder.listFiles() == null) {
            Log.e("ImageLoadError", "Default images folder is missing or empty.");
            return false; // Indicate failure
        }

        File[] imageFiles = imageFolder.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".svg"));
        if (imageFiles == null || imageFiles.length == 0) {
            Log.e("ImageLoadError", "No images found in default folder.");
            return false; // Indicate failure
        }

        // Logic to load images into your app
        Log.d("ImageLoad", "Loaded " + imageFiles.length + " default images.");
        return true; // Indicate success
    }

    private void copyAssetsToInternalStorage() {
        File imageFolder = new File(getFilesDir(), "UploadedImages");
        File soundFolder = new File(getFilesDir(), "DefaultSounds");

        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
        if (!soundFolder.exists()) {
            soundFolder.mkdirs();
        }

        try {
            // Copy images
            String[] imageFiles = getAssets().list("default_images");
            if (imageFiles != null) {
                for (String fileName : imageFiles) {
                    InputStream is = getAssets().open("default_images/" + fileName);
                    File outFile = new File(imageFolder, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                    is.close();
                }
            }

            // Copy sounds
            String[] soundFiles = getAssets().list("default_sounds");
            if (soundFiles != null) {
                for (String fileName : soundFiles) {
                    InputStream is = getAssets().open("default_sounds/" + fileName);
                    File outFile = new File(soundFolder, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                    is.close();
                }
            }

            Log.d("AssetCopy", "Assets copied successfully.");
        } catch (IOException e) {
            Log.e("AssetCopyError", "Error copying assets: " + e.getMessage());
        }
    }


    private void validateSharedData() {
        SharedPreferences sharedPreferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);

        // Validate colors
        Set<String> colors = sharedPreferences.getStringSet("colors", new HashSet<>());
        if (colors.isEmpty()) {
            Log.e("InitializationError", "Colors not initialized properly!");
            initializeColorMap(); // Fallback initialization
        }

        // Validate adjectives
        Set<String> adjectives = sharedPreferences.getStringSet("adjectives", new HashSet<>());
        if (adjectives.isEmpty()) {
            Log.e("InitializationError", "Adjectives not initialized properly!");
            initializeAdjectiveMap(); // Fallback initialization
        }
    }
    private void initializeComponents() {
        // Initialize SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        // Initialize SoundPool
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build();
    }

    private void triggerAutoResponse() {
        if (isSoundCooldown || !validWordBuffer.isEmpty()) {
            Log.d("AutoResponse", "Skipping auto-response. True words detected or sound is on cooldown.");
            return;
        }

        autoResponseActive = true;
        isSoundCooldown = true;

        // Request audio focus
        AudioFocusRequest audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        textToSpeech.stop(); // Stop TTS if audio focus is lost
                    }
                })
                .build();

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int reducedVolume = Math.max(1, originalVolume); // Reduce volume to 50%, ensure not zero
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, reducedVolume, 0);

        int focusResult = audioManager.requestAudioFocus(audioFocusRequest);
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.e("AutoResponse", "Failed to gain audio focus");
            return;
        }

        // Set cooldown to prevent overlapping auto-responses
        autoResponseHandler.postDelayed(() -> isSoundCooldown = false, AUTO_RESPONSE_COOLDOWN);

        // Generate random components for auto-response
        String randomObject = getRandomNoun();
        String randomColor = getRandomColor();
        String randomMotion = getRandomMotion();

        // Construct the response text
        String responseText = randomColor + " " + randomMotion + " " + randomObject;
        Log.d("AutoResponse", "Generated response: " + responseText);

        // Display response text in the UI (demo mode only)
        if (isAutoResponseEnabled && autoResponseTextView != null) {
            runOnUiThread(() -> autoResponseTextView.setText(responseText));
        }

        // Speak the response text with Text-to-Speech
        if (textToSpeech != null) {
            textToSpeech.speak(responseText, TextToSpeech.QUEUE_FLUSH, null, "autoResponse");
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    pauseSpeechRecognition(); // Pause recognition during TTS
                }

                @Override
                public void onDone(String utteranceId) {
                    restoreVolumeAndResumeRecognition(audioManager, originalVolume);
                }

                @Override
                public void onError(String utteranceId) {
                    restoreVolumeAndResumeRecognition(audioManager, originalVolume);
                }
            });
        }

        // Display the image and play motion if applicable
        loadImageAndApplyProperties(randomObject, randomColor, randomMotion, new Random());

        // Clear the TextView and reset state after a delay
        clearTextViewsAfterDelay(3000);
        autoResponseHandler.postDelayed(() -> autoResponseActive = false, userSelectedDelay);
    }

    // Helper method to restore volume and resume recognition
    private void restoreVolumeAndResumeRecognition(AudioManager audioManager, int originalVolume) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        resumeSpeechRecognition(); // Resume recognition after TTS
        runOnUiThread(() -> {
            if (autoResponseTextView != null) autoResponseTextView.setText(""); // Clear response text
        });
        autoResponseActive = false; // Allow the next auto-response
    }



    private void pauseSpeechRecognition() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel(); // Temporarily stop recognition
        }
    }

    private void resumeSpeechRecognition() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.startListening(configureRecognizerIntent());
        }
    }


    private Intent configureRecognizerIntent() {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US"); // Use U.S. English

        // Increase silence length to give the user more time to pause without cutting off
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L); // 5 seconds of silence

        // Minimum length for speech input (in milliseconds) before it starts processing
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L); // Increased to 3 seconds

        // Configure to capture multiple results and partial results
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        return recognizerIntent;
    }

    private void promptForUserSelection() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Your Name and Settings");

        // Create a LinearLayout to hold the spinner, kill word TextView, word mode spinner, auto-response switch, and delay selection
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Spinner for selecting user
        final Spinner userSpinner = new Spinner(this);
        userSpinner.setId(View.generateViewId()); // Assign a unique ID to the Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, userList);
        adapter.insert("Select User", 0); // Add "Select User" as the first item
        userSpinner.setAdapter(adapter);
        layout.addView(userSpinner);

        // Word mode selection spinner
        final Spinner wordModeSpinner = new Spinner(this);
        ArrayAdapter<String> wordModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"One Word", "Two Words", "Three Words"});
        wordModeSpinner.setAdapter(wordModeAdapter);
        layout.addView(wordModeSpinner);

        // TextView to display the kill word
        final TextView killWordTextView = new TextView(this);
        killWordTextView.setText(""); // Initially empty
        layout.addView(killWordTextView);

        // Switch for enabling auto-response
        final Switch autoResponseSwitch = new Switch(this);
        autoResponseSwitch.setText("Enable Demo");
        autoResponseSwitch.setChecked(false); // Default to off
        layout.addView(autoResponseSwitch);

        // Text field for delay input
        final EditText delayInput = new EditText(this);
        delayInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(delayInput);

        // Load the user's last selected delay or set default to 10 seconds
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        delayInput.setHint("Enter delay (5-60 seconds)");
        int lastDelay = prefs.getInt("last_auto_response_delay", 10); // Default to 10 seconds
        delayInput.setText(String.valueOf(lastDelay)); // Set the default value

        // Initially hide delay input until demo toggle is enabled
        delayInput.setVisibility(View.GONE);

        builder.setView(layout);

        // Initialize the dialog and disable the "Start" button initially
        builder.setPositiveButton("Start", null); // Set it to null so we can control the button later
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            finish();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Disable the "Start" button initially
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        // Listener for user selection
        userSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) { // Ignore the "Select User" prompt at position 0
                    String selectedUser = userSpinner.getSelectedItem().toString().trim();
                    String killWord = User.getKillWord(getApplicationContext(), selectedUser);
                    killWordTextView.setText("Your stop Word: " + killWord);

                    // Load saved word mode for the selected user
                    String savedWordMode = prefs.getString("WordMode_" + selectedUser, "One Word");
                    switch (savedWordMode) {
                        case "Two Words":
                            wordModeSpinner.setSelection(1);
                            break;
                        case "Three Words":
                            wordModeSpinner.setSelection(2);
                            break;
                        default:
                            wordModeSpinner.setSelection(0);
                            break;
                    }

                    // Enable or disable the "Start" button based on toggle and input validation
                    validateInputs(autoResponseSwitch.isChecked(), delayInput, dialog, userSpinner);
                } else {
                    killWordTextView.setText("");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Listener for demo toggle
        autoResponseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            delayInput.setVisibility(isChecked ? View.VISIBLE : View.GONE); // Show/hide delay input based on toggle
            validateInputs(isChecked, delayInput, dialog, userSpinner); // Revalidate inputs when toggle changes
        });

        // Listener for delay input validation
        delayInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateInputs(autoResponseSwitch.isChecked(), delayInput, dialog, userSpinner);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
// Set the "Start" button click listener
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            enableFullScreenMode(); // Enable full-screen when session starts
            if (userSpinner.getSelectedItemPosition() > 0) { // Ensure a user is selected
                String selectedName = userSpinner.getSelectedItem().toString().trim();
                String selectedWordMode = wordModeSpinner.getSelectedItem().toString();
                currentKillWord = User.getKillWord(getApplicationContext(), selectedName); // Set user's kill word

                // Save the selected user name, word mode, and auto-response setting
                SharedPreferences.Editor userEditor = prefs.edit();
                userEditor.putString("current_user", selectedName);
                userEditor.putString("WordMode_" + selectedName, selectedWordMode); // Save word mode per user
                userEditor.putBoolean("auto_response_enabled", autoResponseSwitch.isChecked());

                // Parse and save the selected delay if demo is enabled
                if (autoResponseSwitch.isChecked()) {
                    int delay = Integer.parseInt(delayInput.getText().toString());
                    userSelectedDelay = delay * 1000L; // Convert seconds to milliseconds
                    userEditor.putInt("last_auto_response_delay", delay); // Save last delay
                }
                userEditor.apply();

                // Apply the selected word mode
                wordLimit = selectedWordMode.equals("Two Words") ? 2 : selectedWordMode.equals("Three Words") ? 3 : 1;

                // Set auto-response based on the toggle switch
                isAutoResponseEnabled = autoResponseSwitch.isChecked();
                if (isAutoResponseEnabled) {
                    enableAutoResponse(); // Enable auto-response without starting the microphone
                } else {
                    disableAutoResponse(); // Disable auto-response
                    startSpeechRecognition(); // Start microphone only if demo mode is not enabled
                }

                muteAudio(); // Mute system audio if needed
                dialog.dismiss(); // Close the dialog after settings are applied
            }
        });
    }
    // Helper method to validate inputs
    private void validateInputs(boolean isDemoEnabled, EditText delayInput, AlertDialog dialog, Spinner userSpinner) {
        String input = delayInput.getText().toString();
        boolean isDelayValid = true;

        // Check delay input only if demo mode is enabled
        if (isDemoEnabled) {
            isDelayValid = !input.isEmpty() && Integer.parseInt(input) >= 5 && Integer.parseInt(input) <= 60;
        }

        // Enable "Start" button only if user is selected and inputs are valid
        boolean isUserSelected = userSpinner.getSelectedItemPosition() > 0;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isUserSelected && isDelayValid);
    }

    private void clearTextViewsAfterDelay(long delay) {
        handler.postDelayed(() -> {
            runOnUiThread(() -> {
                if (autoResponseTextView != null) {
                    autoResponseTextView.setText("");
                }
                if (recognizedWordsTextView != null) {
                    recognizedWordsTextView.setText("");
                }
            });
        }, delay);
    }

    private void enableAutoResponse() {
        Log.d("AutoResponse", "enableAutoResponse called");

        // Exit early if auto-response is not enabled or already active
        if (!isAutoResponseEnabled || autoResponseActive) {
            Log.d("AutoResponse", "Auto-response not enabled or already active.");
            return;
        }

        autoResponseActive = true; // Mark auto-response as active

        // Remove any existing callbacks to prevent overlapping executions
        if (autoResponseRunnable != null) {
            autoResponseHandler.removeCallbacks(autoResponseRunnable);
        }

        // Define the auto-response logic
        autoResponseRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAutoResponseEnabled) {
                    Log.d("AutoResponse", "Auto-response disabled. Stopping execution.");
                    autoResponseActive = false;
                    return; // Stop execution if auto-response is disabled
                }

                // Calculate time since the last user interaction
                long timeSinceLastInteraction = System.currentTimeMillis() - lastUserInteractionTime;

                if (timeSinceLastInteraction >= userSelectedDelay && validWordBuffer.isEmpty()) {
                    Log.d("AutoResponse", "No true words detected. Triggering auto-response.");
                    triggerAutoResponse();
                }

                // Schedule the next execution
                autoResponseHandler.postDelayed(this, userSelectedDelay);
            }
        };

        // Start the auto-response loop with the initial delay
        autoResponseHandler.postDelayed(autoResponseRunnable, userSelectedDelay);
    }


    private void disableAutoResponse() {
        autoResponseHandler.removeCallbacks(autoResponseRunnable);
        silenceCheckHandler.removeCallbacksAndMessages(null);
        autoResponseRunnable = null;
        isInitialResponse = true; // Reset to apply `userSelectedDelay` again when restarted
        autoResponseActive = false;
    }



    private void clearImageAndStopSounds() {
        // Clear image display
        runOnUiThread(() -> imageView.setImageResource(0));  // Set image to empty

        // Stop any currently playing sounds
        if (soundPool != null) {
            soundPool.autoPause();  // Pauses all active sounds
        }
    }
    private void generateAndDisplayAutoResponse() {
        // Check if an auto-response is already active or if we are in a cooldown period
        if (autoResponseActive || isSoundCooldown) return;
        autoResponseActive = true;
        isSoundCooldown = true;  // Start cooldown to prevent rapid auto-response triggers

        // Clear any previous visuals and sounds
        clearImageAndStopSounds();

        // Generate random components for the auto-response
        String randomObject = getRandomNoun();
        String randomColor = getRandomColor();
        String randomVerb = getRandomVerb();

        // Construct and display the auto-response text
        String responseText = randomColor + " " + randomVerb + " " + randomObject;
        autoResponseTextView.setText(responseText);
        speakWithFocus(responseText);  // Use TTS with audio focus control to prevent overlap

        // Bundle for TextToSpeech with a unique utterance ID to track this specific response
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "autoResponse");

        // Set the TTS listener to trigger the image and sound actions only after TTS completes
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                pauseSpeechRecognition(); // Pause SpeechRecognizer here

                // Log when TTS starts
                Log.d("AutoResponse", "TTS started for auto-response");
            }

            @Override
            public void onDone(String utteranceId) {
                resumeSpeechRecognition();
                if ("autoResponse".equals(utteranceId)) {
                    runOnUiThread(() -> {
                        // Only load image and play sound after TTS completes
                        loadImageAndApplyProperties(randomObject, randomColor, randomVerb, new Random());

                        // Reset auto-response active flag after full delay to allow for the next response
                        autoResponseHandler.postDelayed(() -> autoResponseActive = false, 100); // Adjust delay as needed
                        lastUserInteractionTime = System.currentTimeMillis();  // Update last interaction time
                    });
                }
            }

            @Override
            public void onError(String utteranceId) {
                resumeSpeechRecognition(); // Resume SpeechRecognizer even if there’s an error

                // Reset the active flag on error to allow future auto-responses
                autoResponseActive = false;
                Log.e("AutoResponse", "Error in TTS for auto-response");
            }
        });

        // Start speaking the auto-response text, with the audio focus handler
        textToSpeech.speak(responseText, TextToSpeech.QUEUE_FLUSH, params, "autoResponse");

        // Calculate and delay the next auto-response trigger to avoid overlap
        int wordCount = responseText.split("\\s+").length;
        long estimatedDuration = wordCount * 500 + 8000;  // Approximate duration per word + buffer time
        autoResponseHandler.postDelayed(() -> {
            // Only enable auto-response if it's still active and the user hasn't interacted
            if (isAutoResponseEnabled && !autoResponseActive) {
                enableAutoResponse();
            }
        }, estimatedDuration);

        // Set a short cooldown period after speaking to prevent overlapping responses
        autoResponseHandler.postDelayed(() -> isSoundCooldown = false, SOUND_COOLDOWN_MS);
    }

    private void setVolumeToMin() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // Force volume to minimum (0)
            audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    0, // Minimum level
                    AudioManager.FLAG_SHOW_UI // Optional: Show volume slider UI
            );
            Log.d("VolumeControl", "Default volume set to minimum on app launch");
        } else {
            Log.e("VolumeControl", "AudioManager is null. Cannot set volume.");
        }
    }


    // Utility methods to mute and unmute the microphone temporarily
    private void muteMicrophoneTemporarily() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        }
    }

    private void unmuteMicrophone() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        }
    }
    private boolean isDebouncing = false;

    private void processUserInput(String command) {
        if (isDebouncing) return;

        isDebouncing = true;
        handler.postDelayed(() -> isDebouncing = false, 500); // 500ms debounce delay

        lastUserInteractionTime = System.currentTimeMillis();

        disableAutoResponse(); // Stop auto-response when user is speaking
        isUserSpeaking = true;
        runOnUiThread(() -> autoResponseTextView.setText(""));
        String[] words = command.split("\\s+");
        Set<String> uniqueWords = new HashSet<>(); // Set to track unique valid words per input

        for (String word : words) {
            if (validWordBuffer.contains(word)) continue;

            if (uniqueWords.contains(word)) {
                continue; // Skip duplicate words
            }

            if (colorMap.containsKey(word) || motionMap.containsKey(word) || imageExists(word)) {
                uniqueWords.add(word); // Add word to set for valid, unique words

                if (colorMap.containsKey(word)) {
                    currentColor = word;
                } else if (motionMap.containsKey(word)) {
                    currentMotion = word;
                } else {
                    currentObject = word;
                }

                validWordBuffer.add(word);
            } else {
                // Append unrecognized words once for session data logging
                if (!unrecognizedWords.toString().contains(word)) {
                    unrecognizedWords.append(word).append(" ");
                }
            }
            if (validWordBuffer.size() == wordLimit) {
                triggerOutput(); // Handle recognized words
                validWordBuffer.clear();
                enableAutoResponse(); // Restart auto-response mechanism
                return;
            }
            // Trigger output if required number of valid words is reached
            if (validWordBuffer.size() >= wordLimit) {
                triggerOutput();
                return;
            }
        }
    }

    private void triggerOutput() {
        if (sessionSaved) {
            return;  // Prevent multiple saves for the same trigger
        }
        if (!currentObject.isEmpty() && !currentColor.isEmpty() && !currentMotion.isEmpty()) {
            String phrase = currentObject + " " + currentColor + " " + currentMotion;

            // Display image and play sound
            loadImageAndApplyProperties(currentObject, currentColor, currentMotion, new Random());
            playSoundForObject(currentObject);

            // Save only the triggered phrase in spokenWords
            if (!spokenWords.toString().contains(phrase)) {
                spokenWords.append(phrase).append("; ");
            }

            // Save session data for each trigger
            //    saveSessionData(startTime, getCurrentTimestamp(), spokenWords.toString().trim(), unrecognizedWords.toString().trim());
            sessionSaved = true;
            // Ensure the microphone continues listening while output is processed
            if (isListening && speechRecognizer != null) {
                handler.post(() -> speechRecognizer.startListening(configureRecognizerIntent()));
            }

            // Clear properties to avoid saving duplicates for same phrase
            resetCurrentProperties();
        }
    }





    private void speakWithFocus(String responseText) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        Log.d("AudioFocus", "Audio focus gained.");
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        Log.d("AudioFocus", "Audio focus lost. Stopping TTS.");
                        textToSpeech.stop();
                    }
                })
                .build();
        audioManager.requestAudioFocus(focusRequest);

        if (speechRecognizer != null && isListening) {
            Log.d("Speech", "Pausing speech recognition during TTS.");
            speechRecognizer.stopListening(); // Pause recognition during TTS
        }

        textToSpeech.speak(responseText, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID");

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d("TTS", "TTS started: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d("TTS", "TTS completed: " + utteranceId);

                // Resume speech recognition after TTS completes
                if (speechRecognizer != null) {
                    Log.d("Speech", "Resuming speech recognition after TTS.");
                    speechRecognizer.startListening(configureRecognizerIntent());
                }

                // Restart auto-response after TTS
                if (isAutoResponseEnabled) {
                    enableAutoResponse();
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e("TTS", "TTS encountered an error.");
                if (speechRecognizer != null) {
                    speechRecognizer.startListening(configureRecognizerIntent());
                }
            }
        });
    }




    private void startSpeechRecognition() {
        // Reset session flags and data for a new session
        sessionSaved = false;
        spokenWords.setLength(0); // Clear spoken words buffer
        unrecognizedWords.setLength(0); // Clear unrecognized words buffer
        startTime = getCurrentTimestamp(); // Log session start time

        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recognizer not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        muteMicrophoneBeep(); // Prevent system beep on recognition start
        final Intent recognizerIntent = configureRecognizerIntent();

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechRecognition", "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                isUserSpeaking = true; // Mark user as speaking
                lastUserInteractionTime = System.currentTimeMillis(); // Update interaction timestamp
                Log.d("SpeechRecognition", "Speech started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Optional: Use rmsdB to provide real-time feedback on speech volume
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Optional: Handle raw speech data
            }

            @Override
            public void onEndOfSpeech() {
                Log.d("SpeechRecognition", "Speech ended");
                sessionSaved = false; // Reset flag to allow data saving for next speech input

                // Restart recognition if still active
                if (isListening && speechRecognizer != null) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }
            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partialMatches != null && !partialMatches.isEmpty()) {
                    Log.d("SpeechDebug", "Partial result: " + partialMatches.get(0));

                    String partialResult = partialMatches.get(0).toLowerCase().trim();
                    String[] words = partialResult.split("\\s+");
                    boolean trueWordDetected = false;

                    Log.d("SpeechDebug", "Partial Result: " + partialResult);

                    for (String word : words) {
                        if (word.equals(currentKillWord) || word.equals("bored")) {
                            endSession();
                            return;
                        }

                        // Check and add unique recognized true words
                        if (colorMap.containsKey(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);
                        } else if (motionMap.containsKey(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);
                        } else if (adjectiveMap.containsKey(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);
                        } else if (imageExists(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);

                        } else if (!unrecognizedWords.toString().contains(word)) {
                            unrecognizedWords.append(word).append(" ");
                        }

                        // Trigger response if enough valid words are detected
                        if (validWordBuffer.size() == wordLimit) {
                            Log.d("SpeechDebug", "Buffer full, triggering response: " + validWordBuffer);
                            processResults(String.join(" ", validWordBuffer));
                            validWordBuffer.clear();
                            return;
                        }
                    }

                    // If valid words are detected, reset the interaction timer
                    if (trueWordDetected) {
                        lastUserInteractionTime = System.currentTimeMillis();
                    } else {
                        Log.d("SpeechDebug", "No true words detected. Keeping timer running.");
                    }
                }
            }

            private void handleSpeechGaps() {
                final long gapThreshold = 10000L; // 3 seconds for speech gap handling

                // Schedule a task to process the buffer after the gap threshold
                handler.postDelayed(() -> {
                    if (System.currentTimeMillis() - lastUserInteractionTime >= gapThreshold && !validWordBuffer.isEmpty()) {
                        Log.d("SpeechDebug", "Gap detected, processing buffer: " + validWordBuffer);
                        processResults(String.join(" ", validWordBuffer));
                        validWordBuffer.clear();
                    }
                }, gapThreshold);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    Log.d("SpeechDebug", "Final result: " + matches.get(0));

                    String result = matches.get(0).toLowerCase().trim();
                    String[] words = result.split("\\s+");
                    processResults(result); // Pass final result for processing

                    boolean trueWordDetected = false;

                    for (String word : words) {
                        if (word.equals(currentKillWord) || word.equals("bored")) {
                            endSession();
                            return;
                        }

                        // Check and add unique recognized true words
                        if (colorMap.containsKey(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);
                        } else if (motionMap.containsKey(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);
                        } else if (adjectiveMap.containsKey(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);
                        } else if (imageExists(word) && !validWordBuffer.contains(word)) {
                            trueWordDetected = true;
                            validWordBuffer.add(word);
                        } else {
                            unrecognizedWords.append(word).append(" ");
                        }

                        // Trigger response if enough valid words are detected
                        if (validWordBuffer.size() == wordLimit) {
                            processResults(String.join(" ", validWordBuffer));
                            validWordBuffer.clear();
                            return;
                        }
                    }

                    // Reset interaction timer if true words are detected
                    if (trueWordDetected) {
                        lastUserInteractionTime = System.currentTimeMillis();

                    } else {
                        // Trigger auto-response if no true words are detected
                        autoResponseHandler.postDelayed(() -> {
                            if (validWordBuffer.isEmpty() && !isUserSpeaking) {
                                enableAutoResponse();
                            }
                        }, userSelectedDelay);
                    }
                }
            }

            @Override
            public void onError(int error) {
                Log.d("SpeechRecognizerError", "Error Code: " + error);

                if (isListening) {
                    handler.postDelayed(() -> speechRecognizer.startListening(recognizerIntent), 100);
                }

                // Trigger auto-response if in auto mode
                if (isAutoResponseEnabled) {
                    autoResponseHandler.postDelayed(() -> {
                        if (validWordBuffer.isEmpty()) {
                            enableAutoResponse();
                        }
                    }, userSelectedDelay);
                }
            }


            @Override
            public void onEvent(int i, Bundle bundle) {}
        });


        spokenWords.setLength(0);
        unrecognizedWords.setLength(0);
        startTime = getCurrentTimestamp();
        speechRecognizer.startListening(recognizerIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        motionHandler.removeCallbacksAndMessages(null);
        rotationHandler.removeCallbacksAndMessages(null);
    }
    private void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        long maxHeapSizeInMB = runtime.maxMemory() / 1048576L;
        Log.d("MemoryUsage", "Used Memory: " + usedMemInMB + " MB, Max Heap Size: " + maxHeapSizeInMB + " MB");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoResponseHandler != null) {
            autoResponseHandler.removeCallbacksAndMessages(null);
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        autoResponseHandler.removeCallbacks(autoResponseRunnable);  // Clean up handler callbacks
    }

    private void releaseResources() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (motionHandler != null) {
            motionHandler.removeCallbacksAndMessages(null);
        }
        if (rotationHandler != null) {
            rotationHandler.removeCallbacksAndMessages(null);
        }
        if (motionThread != null) {
            motionThread.quitSafely();
        }
        if (rotationThread != null) {
            rotationThread.quitSafely();
        }
    }
    // Method to handle starting the motion or animation
    private void startMotion(String validPhrase) {
        // Your logic to start motion based on recognized true words
        // Example: if using an animation library, initiate animation here
        // Ensure the animation or motion effect does not interfere with ongoing speech recognition

        // Example of motion logic (replace with actual motion code)
        imageView.animate().translationXBy(100).setDuration(500).withEndAction(() -> {
            // Reset position after motion, or perform further actions if needed
            imageView.setTranslationX(0);
        }).start();
    }
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
    private void processResults(String command) {
        runOnUiThread(()-> imageView.setImageResource(0));
        String[] words = command.toLowerCase().split(" ");
        StringBuilder phrase = new StringBuilder();

        // Track recognized types
        boolean nounRecognized = false;
        boolean adjectiveOrColorRecognized = false;
        boolean motionRecognized = false;

        // Process each word in the command
        for (String word : words) {
            boolean isRecognized = false;

            // Recognize color or adjective
            if (colorMap.containsKey(word) || adjectiveMap.containsKey(word)) {
                currentAdjective = word;
                adjectiveOrColorRecognized = true;
                isRecognized = true;
            }
            // Recognize motion
            else if (motionMap.containsKey(word)) {
                currentMotion = word;
                motionRecognized = true;
                isRecognized = true;
            }
            // Recognize noun (image) or tag
            else if (imageExists(word)) {
                List<File> matchingImages = getMatchingImagesForNoun(word);
                if (!matchingImages.isEmpty()) {
                    File randomImage = matchingImages.get(new Random().nextInt(matchingImages.size()));
                    currentObject = randomImage.getName().split("\\.")[0];
                    nounRecognized = true;
                    isRecognized = true;
                }
            } else {
                SharedPreferences sharedPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
                Map<String, ?> allTags = sharedPreferences.getAll(); // Retrieve all saved tags

                // Collect all images associated with the tag
                List<String> matchingImagesForTag = new ArrayList<>();

                for (Map.Entry<String, ?> entry : allTags.entrySet()) {
                    Object value = entry.getValue(); // Retrieve the value
                    if (value instanceof String) { // Check if the value is a String
                        String tags = (String) value;
                        if (tags != null) {
                            List<String> tagList = Arrays.asList(tags.split(",")); // Split tags into a list
                            if (tagList.contains(word.toLowerCase())) { // Check if the spoken word matches a tag
                                matchingImagesForTag.add(entry.getKey()); // Add matching image names to the list
                            }
                        }
                    } else {
                        Log.w("Speech", "Unexpected value type in SharedPreferences: " + value.getClass().getName());
                    }
                }

                // If multiple images match the tag, pick one randomly
                if (!matchingImagesForTag.isEmpty()) {
                    currentObject = matchingImagesForTag.get(new Random().nextInt(matchingImagesForTag.size()));
                    nounRecognized = true;
                    isRecognized = true;

                    // Debug log to confirm the tag match
                    Log.d("TagMatch", "Word: " + word + " matched tag for images: " + matchingImagesForTag);
                }
            }

            // Add unrecognized words to the buffer
            if (!isRecognized) {
                if (unrecognizedWordsBuilder.length() > 0) {
                    unrecognizedWordsBuilder.append(", ");
                }
                unrecognizedWordsBuilder.append(word);
            }
        }

        // Adjust logic based on word limit (mode)
        switch (wordLimit) {
            case 1: // One-word mode
                if (nounRecognized || adjectiveOrColorRecognized || motionRecognized) {
                    if (!nounRecognized) currentObject = getRandomNoun();
                    if (!adjectiveOrColorRecognized) currentAdjective = getRandomAdjectiveOrColor();
                    if (!motionRecognized) currentMotion = getRandomMotion();
                }
                break;
            case 2: // Two-word mode
                // Ensure at least two valid words are recognized (noun + adjective/motion or adjective + motion)
                if (nounRecognized && (adjectiveOrColorRecognized || motionRecognized)) {
                    if (!adjectiveOrColorRecognized) currentAdjective = getRandomAdjectiveOrColor();
                    if (!motionRecognized) currentMotion = getRandomMotion();
                } else if (adjectiveOrColorRecognized && motionRecognized) {
                    if (!nounRecognized) currentObject = getRandomNoun();
                } else {
                    return; // Wait until exactly two valid words are recognized
                }
                break;

            case 3: // Three-word mode
                if (!nounRecognized || !adjectiveOrColorRecognized || !motionRecognized) {
                    return; // Wait until all three components are provided
                }
                break;
        }
        validWordBuffer.add(command);
        if (wordLimit == 1 && validWordBuffer.size() >= 1) {
            triggerOutput();
        }
// Construct the phrase
        if (nounRecognized || adjectiveOrColorRecognized || motionRecognized) {
            if (!nounRecognized) currentObject = getRandomNoun();
            if (!adjectiveOrColorRecognized) currentAdjective = getRandomAdjectiveOrColor();
            if (!motionRecognized) currentMotion = getRandomMotion();

            phrase.append(currentAdjective).append(" ").append(currentMotion).append(" ").append(currentObject);

            // Display recognized true words in demo mode
            if (isAutoResponseEnabled && autoResponseTextView != null) {
                runOnUiThread(() -> autoResponseTextView.setText(phrase.toString().trim()));
            }
        }
        // Trigger output if valid input is complete
        if (!currentObject.isEmpty() || !currentAdjective.isEmpty() || !currentMotion.isEmpty()) {
            loadImageAndApplyProperties(currentObject, currentAdjective, currentMotion, new Random());
            playSoundForObject(currentObject);

            // Save the triggered phrase
            phrase.append(currentObject);
            if (!currentAdjective.isEmpty()) phrase.append(" ").append(currentAdjective);
            if (!currentMotion.isEmpty()) phrase.append(" ").append(currentMotion);

            // Append the phrase to spoken words
            spokenWords.append(phrase.toString().trim()).append("; ");
        }

        // Reset temporary state for the next command
        resetCurrentProperties();
        clearTextViewsAfterDelay(3000);

    }

    // Helper methods for random selection of missing words
    private String getRandomAdjectiveOrColor() {
        return new Random().nextBoolean() ? getRandomColor() : getRandomAdjective();
    }

    private String getRandomMotion() {
        List<String> motions = new ArrayList<>(motionMap.keySet());
        return motions.get(new Random().nextInt(motions.size()));
    }



    // Reset properties to ensure clean processing of next command
    private void resetCurrentProperties() {
        currentAdjective = "";
        currentMotion = "";
        currentObject = "";
    }


    private void playSoundForObject(String objectName) {
        // If a sound is currently on cooldown, skip playing sound
        if (isSoundCooldown) {
            Log.d("SoundDebug", "Sound is on cooldown, skipping play request.");
            return;
        }

        // Set cooldown flag to prevent rapid sound replays
        isSoundCooldown = true;
        soundHandler.postDelayed(() -> isSoundCooldown = false, SOUND_COOLDOWN_MS);

        // Retrieve sounds for image and motion dynamically
        Integer imageSoundId = soundMap.get(objectName.toLowerCase());
        if (imageSoundId == null && objectName != null) {
            imageSoundId = loadSoundFromInternalStorage(objectName, imageFolder); // Check in image folder
        }

        Integer motionSoundId = soundMap.get(currentMotion.toLowerCase());
        if (motionSoundId == null && currentMotion != null) {
            motionSoundId = loadSoundFromInternalStorage(currentMotion, motionFolder); // Check in motion folder
        }

        Log.d("SoundDebug", "imageSoundId: " + imageSoundId + ", motionSoundId: " + motionSoundId);

        // Randomly select one sound if both are available
        if (imageSoundId != null && motionSoundId != null) {
            int selectedSound = random.nextBoolean() ? imageSoundId : motionSoundId;
            Log.d("SoundDebug", "Playing randomly selected sound ID: " + selectedSound);
            soundPool.play(selectedSound, 1.0f, 1.0f, 1, 0, 1.0f);
        } else if (motionSoundId != null) {
            // Play only the motion sound if image sound is not available
            Log.d("SoundDebug", "Playing motion sound ID: " + motionSoundId);
            soundPool.play(motionSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else if (imageSoundId != null) {
            // Play only the image sound if motion sound is not available
            Log.d("SoundDebug", "Playing image sound ID: " + imageSoundId);
            soundPool.play(imageSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else {
            // No sound available; log that only motion will be applied
            Log.d("SoundDebug", "No sound available; only motion will apply.");
        }
    }
    private void saveSoundToFile(Uri soundUri, File folder, String fileName) {
        try {
            File soundFile = new File(folder, fileName + ".mp3");
            try (InputStream inputStream = getContentResolver().openInputStream(soundUri);
                 FileOutputStream outputStream = new FileOutputStream(soundFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            Log.d("SoundDebug", "Sound saved to: " + soundFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("SoundDebug", "Error saving sound file", e);
        }
    }

    // Helper method to check if an image exists for the object
    private boolean imageExists(String objectName) {
        File imageFilePNG = new File(imageFolder, objectName + ".png");
        File imageFileSVG = new File(imageFolder, objectName + ".svg");
        return imageFilePNG.exists() || imageFileSVG.exists();
    }

    // Updated loadSoundFromInternalStorage method
    private Integer loadSoundFromInternalStorage(String objectName, File folder) {
        if (soundPool == null) {
            Log.e("SoundDebug", "SoundPool is not initialized.");
            return null;
        }

        File audioFile = new File(folder, objectName + ".mp3");
        if (audioFile.exists()) {
            try {
                AssetFileDescriptor afd = new AssetFileDescriptor(
                        ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY),
                        0, AssetFileDescriptor.UNKNOWN_LENGTH);
                int soundId = soundPool.load(afd, 1);
                soundMap.put(objectName.toLowerCase(), soundId);
                Log.d("SoundDebug", "Loaded sound for object: " + objectName + " from folder: " + folder.getAbsolutePath());
                return soundId;
            } catch (IOException e) {
                Log.e("SoundDebug", "Error loading audio file: " + audioFile.getAbsolutePath(), e);
            }
        } else {
            Log.d("SoundDebug", "No audio file found for object: " + objectName + " in folder: " + folder.getAbsolutePath());
        }
        return null;
    }



    private void unmuteSystemStreams() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        }
    }


    // Add other methods and helper functions
    public void muteMicrophoneBeep() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Muting the system sound effects (this usually affects the mic beep sound)
        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
    }

    public void unmuteMicrophoneBeep() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Unmuting the system sound effects
        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
    }


    // Helper function to get random object
    private String getRandomNoun() {
        File[] nounFiles = imageFolder.listFiles(file -> file.getName().endsWith(".png") || file.getName().endsWith(".svg"));
        if (nounFiles != null && nounFiles.length > 0) {
            File randomNounFile = nounFiles[random.nextInt(nounFiles.length)];
            return randomNounFile.getName().split("\\.")[0];  // Extract noun name from filename
        }
        return null;
    }


    // Helper function to get random color
    private String getRandomAdjective() {
        List<String> adjectives = new ArrayList<>(adjectiveMap.keySet());
        List<String> colors = new ArrayList<>(colorMap.keySet());
        adjectives.addAll(colors);
        return adjectives.isEmpty() ? "big" : adjectives.get(random.nextInt(adjectives.size()));
    }

    private String getRandomColor() {
        List<String> colors = new ArrayList<>(colorMap.keySet());
        return colors.get(new Random().nextInt(colors.size()));
    }
    // Helper function to get random motion
    private String getRandomVerb() {
        List<String> motions = new ArrayList<>(motionMap.keySet());
        return motions.get(new Random().nextInt(motions.size()));

    }




    private List<File> getMatchingImagesForNoun(String noun) {
        File[] files = imageFolder.listFiles(file ->
                file.getName().toLowerCase().startsWith(noun.toLowerCase()) &&
                        (file.getName().endsWith(".png") || file.getName().endsWith(".svg"))
        );
        return files != null ? Arrays.asList(files) : new ArrayList<>();
    }
    private void stopListeningAndReturnToMain() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;

            Intent intent = new Intent(Speech.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }


    private void saveSessionData(String startTime, String endTime, String recognizedWords, String unrecognizedWords) {
        if (sessionSaved) return;  // Prevent multiple saves per session

        SharedPreferences sharedPreferences = getSharedPreferences("SessionHistoryPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Retrieve the current user name
        SharedPreferences userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String currentUser = userPrefs.getString("current_user", "default");

        // Fetch and update the user's session history
        Set<String> historySet = sharedPreferences.getStringSet("history_" + currentUser, new HashSet<>());
        Set<String> updatedHistorySet = new HashSet<>(historySet);
        // Format the session data for this session
        String sessionData = "Start: " + startTime + "\nEnd: " + endTime
                + "\nOutput: " + recognizedWords; // Only save recognized (good/output) words
        updatedHistorySet.add(sessionData);

        // Save the updated history set to SharedPreferences
        editor.putStringSet("history_" + currentUser, updatedHistorySet);
        editor.apply();
        sessionSaved = true;  // Set flag to prevent multiple saves

    }

    private void endSession() {
        // Ensure we save data one last time if it hasn't been saved
        if (!sessionSaved) {
            String endTime = getCurrentTimestamp();
           saveSessionData(startTime, endTime, spokenWords.toString().trim(), unrecognizedWords.toString().trim());
        }

        // Display confirmation and reset variables
        Toast.makeText(this, "Session ended. Data saved.", Toast.LENGTH_SHORT).show();
        spokenWords.setLength(0);
        unrecognizedWords.setLength(0);
        sessionSaved = false;

        // Stop the speech recognizer
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }

        finish();
    }
    private void initializeAndSaveData() {
        initializeColorMap();
        initializeAdjectiveMap();
        motionMap = readMotions();
        SharedPreferences sharedPreferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet("colours", colorMap.keySet());
        editor.putStringSet("adjectives", adjectiveMap.keySet());
        if(!(motionMap == null)) {
            editor.putStringSet("motions", motionMap.keySet());
        }

        // Load nouns from image folder
        Set<String> nouns = loadNounsFromImageFolder();
        editor.putStringSet("nouns", nouns);
        editor.apply();

    }

    private Set<String> loadNounsFromImageFolder() {
        Set<String> nouns = new HashSet<>();
        if (imageFolder != null && imageFolder.exists() && imageFolder.isDirectory()) {
            for (File file : imageFolder.listFiles()) {
                if (file.getName().endsWith(".png") || file.getName().endsWith(".svg")) {
                    nouns.add(file.getName().replaceFirst("[.][^.]+$", "")); // Remove extension
                }
            }
        }
        return nouns;
    }

    private void loadImageAndApplyProperties(String image, String color, String motion, Random rand) {
        File imageFilePNG = new File(imageFolder, image + ".png");
        File imageFileSVG = new File(imageFolder, image + ".svg");
        Bitmap bitmap = null;

        if (imageFilePNG.exists()) {
            bitmap = BitmapFactory.decodeFile(imageFilePNG.getAbsolutePath());
        } else if (imageFileSVG.exists()) {
            bitmap = loadSVGAsBitmap(imageFileSVG);
        }

        if (bitmap != null) {
            if (color != null && !color.isEmpty() && !adjectiveMap.containsKey(color)) {
                int selectedColor = colorMap.get(color);
                bitmap = applySelectiveColorToObject(bitmap, selectedColor);
            }

            if (color != null && adjectiveMap.containsKey(color)) {
                bitmap = handleAdjective(bitmap, color);
            }

            Bitmap finalBitmap = bitmap;
            handler.postDelayed(() -> imageView.setImageBitmap(finalBitmap), 100);

            if (motion != null && !motion.isEmpty()) {
                if (currentColor.equals("fast")) {
                    applyMotion(motion, rand, 0.5);
                } else if (currentColor.equals("slow")) {
                    applyMotion(motion, rand, 2);
                } else {
                    applyMotion(motion, rand, 1);
                }
            } else {
                imageView.setTranslationX(0);
                imageView.setTranslationY(0);

                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> {
                    imageView.setImageResource(0);
                    resetImageViewScale();
                }, 5000);
            }
        }
    }

    private void resetImageViewScale() {
        imageView.setScaleX(1.0f);  // Reset horizontal scale to default
        imageView.setScaleY(1.0f);  // Reset vertical scale to default
    }

    // Apply color to any shade of white or near-white, including transparent white, ignoring fully transparent pixels (background)
    private Bitmap applySelectiveColorToObject(Bitmap originalBitmap, int color) {
        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        for (int x = 0; x < mutableBitmap.getWidth(); x++) {
            for (int y = 0; y < mutableBitmap.getHeight(); y++) {
                int pixel = mutableBitmap.getPixel(x, y);

                // Apply color to any shade of white or near-white with some transparency allowance
                if (Color.alpha(pixel) > 150 && isAnyShadeOfWhite(pixel)) { // Allow partially transparent white
                    mutableBitmap.setPixel(x, y, color);
                }
            }
        }
        return mutableBitmap;
    }
    // Apply motion to the image based on the detected motion word
    private void applyMotion(String motion, Random rand, double speed) {
        //interrupt any currently running motions
        motionThread.interrupt();motionThread = new HandlerThread("MotionHandlerThread");
        motionThread.start();
        motionHandler = new Handler(motionThread.getLooper());
        rotationThread.interrupt();
        rotationThread = new HandlerThread("RotationHandlerThread");
        rotationThread.start(); rotationHandler =new Handler(rotationThread.getLooper());
        ArrayList<RecordedMotion> selectedMotions = motionMap.get(motion);
        if (selectedMotions != null && !selectedMotions.isEmpty()) {
            RecordedMotion selectedMotion = (selectedMotions.size() > 0)
                    ? selectedMotions.get(rand.nextInt(selectedMotions.size()))
                    : selectedMotions.get(0);

            moveImageBasedOnMotion(selectedMotion, speed);
        }
    }

    private void moveImageBasedOnMotion(RecordedMotion motion, double speed) {
        // Get screen dimensions
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Reset image's rotation to default
        imageView.setRotation(0);

        // Ensure motion data exists and is valid
        if (motion.posList == null || motion.posList.isEmpty() || motion.posIncrements == null || motion.posIncrements.isEmpty()) {
            return;
        }

        // Remove previous callbacks and reset threads
        motionHandler.removeCallbacksAndMessages(null);
        rotationHandler.removeCallbacksAndMessages(null);

        // Send runnables to motion handlers
        motionHandler.post(() -> {
            for (int i = 0; i < motion.posList.size(); i++) {
                if (i >= motion.posIncrements.size()) return;  // Safety check

                // Create final variables to be used inside the lambda expression
                final List<Float> pos = motion.posList.get(i);
                final long currentIncrement = motion.posIncrements.get(i);

                // Calculate the next position
                float targetX = pos.get(0) - imageView.getWidth() / 2;
                float targetY = pos.get(1) - imageView.getHeight() / 2;

                // Check if the target position exceeds the screen width or height
                if (targetX < 0) targetX = 0;
                if (targetY < 0) targetY = 0;
                if (targetX + imageView.getWidth() > screenWidth) targetX = screenWidth - imageView.getWidth();
                if (targetY + imageView.getHeight() > screenHeight) targetY = screenHeight - imageView.getHeight();

                // Move the image to the calculated position on the UI thread
                float finalTargetX = targetX;
                float finalTargetY = targetY;
                runOnUiThread(() -> {
                    imageView.setX(finalTargetX);
                    imageView.setY(finalTargetY);
                });

                // Sleep for the current increment
                try {
                    Thread.sleep(currentIncrement);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // Clear the image after motion completes
            runOnUiThread(() -> {
                imageView.setImageResource(0);
                resetImageViewScale();
            });
        });

        long durationPerIncrement = (long) ((motion.duration * speed) / 500);

        // Handle rotation if motion has a custom rotation flag
        if (motion.rotFlag) {
            rotationHandler.post(() -> {
                for (float angle : motion.angleList) {
                    // Use final variable for rotation angle
                    final float finalAngle = angle;
                    runOnUiThread(() -> imageView.setRotation(finalAngle));
                    try {
                        Thread.sleep(durationPerIncrement);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                // Ensure image is cleared only after both motion and rotation are done
                runOnUiThread(() -> {
                    imageView.setImageResource(0);
                    resetImageViewScale();
                });
            });
        } else {
            // Apply default rotation if no custom rotation
            float degreesPerIncrement = ((((float) motion.duration / 1000) * motion.rotationsPerSecond) / 500) * 360;

            rotationHandler.post(() -> {
                for (int i = 0; i < 500; i++) {
                    final int rotationIndex = i;  // Create a final copy of the loop variable
                    runOnUiThread(() -> imageView.setRotation(rotationIndex * degreesPerIncrement));
                    try {
                        Thread.sleep(durationPerIncrement);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                // Ensure image is cleared only after both motion and rotation are done
                runOnUiThread(() -> {
                    imageView.setImageResource(0);
                    resetImageViewScale();
                });
            });
        }
    }

    private Bitmap loadSVGAsBitmap(File svgFile) {
        try {
            FileInputStream fileInputStream = new FileInputStream(svgFile);
            SVG svg = SVG.getFromInputStream(fileInputStream);
            Bitmap bitmap = Bitmap.createBitmap((int) svg.getDocumentWidth(), (int) svg.getDocumentHeight(), Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new android.graphics.Canvas(bitmap));
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(Speech.this, "Error loading SVG!", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    // Helper method to check if a pixel is any shade of white
    private boolean isAnyShadeOfWhite(int pixel) {
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);

        // Define a tolerance to capture near-white shades
        int lowTolerance = 180; // Lower tolerance to include even darker whites or shades of gray close to white
        return red >= lowTolerance && green >= lowTolerance && blue >= lowTolerance;
    }

    private void initializeColorMap() {
        colorMap.put("red", Color.RED);
        colorMap.put("green", Color.GREEN);
        colorMap.put("blue", Color.BLUE);
        colorMap.put("yellow", Color.YELLOW);
        colorMap.put("black", Color.BLACK);
        colorMap.put("white", Color.WHITE);
        colorMap.put("gray", Color.GRAY);
        colorMap.put("orange", Color.rgb(255, 165, 0));
        colorMap.put("purple", Color.rgb(128, 0, 128));
        colorMap.put("pink", Color.rgb(255, 192, 203));
        colorMap.put("lime", Color.rgb(50,205,50 ));
        colorMap.put("indigo", Color.rgb(75,0,130));
        colorMap.put("magenta", Color.rgb(255,0,255));
    }

    private void initializeAdjectiveMap() {
        // Map to handle size adjectives
        adjectiveMap.put("big", "size");
        adjectiveMap.put("small", "size");

        adjectiveMap.put("wide", "size");
        adjectiveMap.put("narrow", "size");

        adjectiveMap.put("bright", "light");
        adjectiveMap.put("dark", "light");

        // Map to handle pattern adjectives
        adjectiveMap.put("dotted", "pattern");

        adjectiveMap.put("transparent", "opacity");
        adjectiveMap.put("opaque", "opacity");
        //map to handle texture adjective
        adjectiveMap.put("furry","texture");
        adjectiveMap.put("smooth","texture");
        adjectiveMap.put("shiny","shiny");
        // Map to handle speed adjectives
        adjectiveMap.put("fast", "speed");
        adjectiveMap.put("slow", "speed");
    }


    private HashMap<String, ArrayList<RecordedMotion>> readMotions() {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(openFileInput("motionData.bin"))) {
            HashMap<String, ArrayList<RecordedMotion>> map = (HashMap<String, ArrayList<RecordedMotion>>) objectInputStream.readObject();
            if (map.isEmpty()) {
                ObjectInputStream newObjectInputStream = new ObjectInputStream(getAssets().open("defaultMotionData.bin"));
                map = (HashMap<String, ArrayList<RecordedMotion>>) newObjectInputStream.readObject();
            }
            return map;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap handleAdjective(Bitmap bitmap, String adjective) {
        String type = adjectiveMap.get(adjective);


        switch (type) {
            case "size":
                if (adjective.equals("big")) {
                    imageView.setScaleX(2.0f);
                    imageView.setScaleY(2.0f);
                } else if (adjective.equals("small")) {
                    imageView.setScaleX(0.25f);
                    imageView.setScaleY(0.25f);
                }
                else if(adjective.equals("wide")) {
                    // Increase the horizontal scale of the ImageView to make it wider
                    imageView.setScaleX(2.0f);  // Make the image 1.5 times wider
                    imageView.setScaleY(0.5f);  // Keep the height the same
                }

                else if(adjective.equals("narrow")) {
                    // Decrease the horizontal scale of the ImageView to make it narrower
                    imageView.setScaleX(0.5f);  // Make the image half as wide
                    imageView.setScaleY(2.0f);  // Keep the height the same
                }
                break;


            case "texture":
                if(adjective.equals("furry")) {
                    return addTextureEffect("furry", bitmap);
                }else if(adjective.equals("smooth")){
                    return addTextureEffect("smooth",bitmap);
                }
                break;
            case "light":
                if(adjective.equals("dark")) {
                    return applyBrightnessEffect(bitmap, 0.25f);
                }
                else if(adjective.equals("bright")) {
                    return applyBrightnessEffect(bitmap, 2.0f);
                }
                break;

            case "opacity":  // Handle opacity changes
                if (adjective.equals("transparent")) {
                    return changeImageOpacity(bitmap, 0.1f);  // 30% visible
                } else if (adjective.equals("opaque")) {
                    return changeImageOpacity(bitmap, 1.0f);  // Fully visible
                }
                break;

            case "wide":
                // Increase the horizontal scale of the ImageView to make it wider
                imageView.setScaleX(1.5f);  // Make the image 1.5 times wider
                imageView.setScaleY(1.0f);  // Keep the height the same
                break;

            case "narrow":
                // Decrease the horizontal scale of the ImageView to make it narrower
                imageView.setScaleX(0.5f);  // Make the image half as wide
                imageView.setScaleY(1.0f);  // Keep the height the same
                break;

            case "speed":
                // Do nothing
                break;
        }
        return bitmap;
    }

    private Bitmap applyBrightnessEffect(Bitmap bitmap, float brightnessFactor) {
        if (bitmap != null) {
            // Create a mutable copy of the original bitmap to apply changes
            Bitmap brightnessAdjustedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            for (int x = 0; x < brightnessAdjustedBitmap.getWidth(); x++) {
                for (int y = 0; y < brightnessAdjustedBitmap.getHeight(); y++) {
                    int pixel = brightnessAdjustedBitmap.getPixel(x, y);

                    // Get the current RGB values
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);
                    int alpha = Color.alpha(pixel);  // Preserve alpha channel

                    // Apply brightness factor (for "bright", brightnessFactor > 1; for "dark", brightnessFactor < 1)
                    r = Math.min(255, (int) (r * brightnessFactor));
                    g = Math.min(255, (int) (g * brightnessFactor));
                    b = Math.min(255, (int) (b * brightnessFactor));

                    // Set the new pixel color with the modified brightness values
                    brightnessAdjustedBitmap.setPixel(x, y, Color.argb(alpha, r, g, b));
                }
            }

            return brightnessAdjustedBitmap;  // Return the modified bitmap with brightness effect applied
        }
        return null;  // Return null if the bitmap is not valid
    }


    private Bitmap addTextureEffect(String texture, Bitmap bitmap) {

        if (bitmap != null) {
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            Paint paint = new Paint();

            switch (texture) {
                case "furry":
                    // Draw furry texture effect using random lines
                    paint.setColor(Color.GRAY);
                    paint.setStrokeWidth(2);
                    for (int i = 0; i < 1000; i++) {
                        int startX = random.nextInt(mutableBitmap.getWidth());
                        int startY = random.nextInt(mutableBitmap.getHeight());
                        int endX = startX + random.nextInt(20) - 10;
                        int endY = startY + random.nextInt(20) - 10;
                        canvas.drawLine(startX, startY, endX, endY, paint);
                    }
                    break;

                case "smooth":
                    // Draw smooth texture using a blur effect
                    break;
            }

            return mutableBitmap;  // Return the modified bitmap
        }
        return null;

    }


    private Bitmap overlayPatternOnImage(String pattern, Bitmap bitmap) {
        //Bitmap bitmap = loadSVGAsBitmap(imageFileSVG);
        if (bitmap != null) {
            // Create a mutable copy of the original bitmap to apply changes
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);  // Color for the pattern
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);  // Adjust stroke width for patterns

            // Determine the pattern to draw
            switch (pattern) {

                case "dotted":
                    // Draw dotted pattern on the object only (skip transparent pixels)
                    for (int x = 0; x < mutableBitmap.getWidth(); x += 30) {
                        for (int y = 0; y < mutableBitmap.getHeight(); y += 30) {
                            // Check if the pixel is not transparent
                            if (Color.alpha(mutableBitmap.getPixel(x, y)) != 0) {
                                canvas.drawCircle(x, y, 5, paint);  // Draw a small dot
                            }
                        }
                    }
                    break;
            }

            return mutableBitmap;  // Return the modified bitmap
        }
        return null;  //
    }

    private Bitmap changeImageOpacity(Bitmap bitmap, float opacity) {
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        // Create a temporary bitmap with the same dimensions and apply opacity using a separate Canvas
        Bitmap tempBitmap = Bitmap.createBitmap(mutableBitmap.getWidth(), mutableBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tempBitmap);
        Paint paint = new Paint();

        // Set the opacity value (0.0 to 1.0) and apply it to the paint object
        paint.setAlpha((int) (opacity * 255));  // Alpha value (0-255)

        // Draw the bitmap onto the new canvas with the specified opacity
        canvas.drawBitmap(mutableBitmap, 0, 0, paint);

        return tempBitmap;
    }


    public void muteAudio() {
        AudioManager audMan = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audMan != null) {
            audMan.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
            audMan.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0);
            // DO NOT mute STREAM_MUSIC here
        }
    }

    public void unMuteAudio() {
        AudioManager audMan = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audMan.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0);
    }
}