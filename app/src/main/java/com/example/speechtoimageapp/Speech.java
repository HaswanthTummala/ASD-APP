package com.example.speechtoimageapp;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StrictMode;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.caverock.androidsvg.SVG;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech);

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

        initializeColorMap();
        initializeAdjectiveMap();
        motionMap = readMotions();

        imageFolder = new File(getFilesDir(), "UploadedImages");

        SharedPreferences preferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);
        String wordMode = preferences.getString("WordMode", "One Word");
        wordLimit = wordMode.equals("Two Words") ? 2 : wordMode.equals("Three Words") ? 3 : 1;

        final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        userList = User.loadUserList(this);
        promptForUserSelection();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build();

        soundMap = new HashMap<>();
        loadSounds();

        motionThread.start();
        rotationThread.start();
        motionHandler = new Handler(motionThread.getLooper());
        rotationHandler = new Handler(rotationThread.getLooper());
    }

    private void promptForUserSelection() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Your Name");

        final Spinner userSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, userList);
        userSpinner.setAdapter(adapter);
        builder.setView(userSpinner);

        builder.setPositiveButton("Start", (dialog, which) -> {
            String selectedName = userSpinner.getSelectedItem().toString().trim();

            SharedPreferences userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor userEditor = userPrefs.edit();
            userEditor.putString("current_user", selectedName);
            userEditor.apply();
            muteAudio();
            startSpeechRecognition();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            finish();
        });

        builder.show();
    }

    private void startSpeechRecognition() {
        muteMicrophoneBeep();
        final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                if (isListening) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    processResults(matches.get(0));
                }
                if (isListening) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {}

            @Override
            public void onError(int error) {
                // Add a delay before restarting
                handler.postDelayed(() -> {
                    if (isListening) {
                        speechRecognizer.startListening(recognizerIntent);
                    }
                }, 2000); // 2 seconds delay to reduce load
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
        releaseResources();
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
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void processResults(String command) {
        String[] words = command.toLowerCase().split(" ");

        Random rand = new Random();
        StringBuilder phrase = new StringBuilder();
        boolean nounRecognized = false;
        boolean adjectiveRecognized = false;
        boolean verbRecognized = false;
        // Limit the number of words processed based on the word limit (One, Two, or Three Words mode)
        words = Arrays.copyOfRange(words, 0, Math.min(words.length, wordLimit));

        for (int i = words.length - 1; i >= 0; i--) {
            String word = words[i].trim();
            boolean recognized = false;

            if (colorMap.containsKey(word)) {
                currentColor = word;
                recognized = true;
                adjectiveRecognized = true;
                if (!currentColor.isEmpty()) phrase.append(currentColor).append(" ");
            }

            if (motionMap.containsKey(word)) {
                currentMotion = word;
                recognized = true;
                verbRecognized = true;
                if (!currentMotion.isEmpty()) phrase.append(currentMotion).append(" ");
            }

            if (adjectiveMap.containsKey(word)) {
                currentColor = word;
                recognized = true;
                adjectiveRecognized = true;
                if (!currentColor.isEmpty()) phrase.append(currentColor).append(" ");
            }

            if (new File(imageFolder, word + ".png").exists() || new File(imageFolder, word + ".svg").exists()) {
                List<File> matchingImages = getMatchingImagesForNoun(word);
                if (!matchingImages.isEmpty()) {
                    File randomImage = matchingImages.get(random.nextInt(matchingImages.size()));
                    currentObject = randomImage.getName().split("\\.")[0];
                    nounRecognized = true;
                    recognized = true;
                    if (!currentObject.isEmpty()) phrase.append(word).append(" ");
                    // Play sound for recognized object immediately
                    playSoundForObject(currentObject);
                }
            }

            if (!recognized) {
                if (unrecognizedWords.length() > 0) {
                    unrecognizedWords.append(", ");
                }
                unrecognizedWords.append(word);
            }

            if (word.equals("stop")) {
                String endTime = getCurrentTimestamp();
                saveSessionData(startTime, endTime, spokenWords.toString().trim(), unrecognizedWords.toString().trim());
                stopListeningAndReturnToMain();
                unMuteAudio();
                return;
            }
        }

        if (spokenWords.length() > 0) {
            spokenWords.append(", ");
        }
        spokenWords.append(phrase);

        switch (wordLimit) {
            case 1:
                if (nounRecognized && currentColor.isEmpty()) {
                    currentColor = getRandomColor();
                    currentAdjective = getRandomAdjective();
                    currentMotion = getRandomVerb();
                } else if (adjectiveRecognized && currentObject.isEmpty()) {
                    currentObject = getRandomNoun();
                    currentObject = getRandomNoun();
                    currentMotion = getRandomVerb();
                } else if (verbRecognized) {
                    if (currentObject.isEmpty()) {
                        currentObject = getRandomNoun();
                    currentAdjective = getRandomAdjective();
                }}
                break;
            case 2:
                // Two-word mode
                if (currentObject.isEmpty()) {
                    currentObject = getRandomNoun();
                }
                if (currentColor.isEmpty()) {
                    currentColor = getRandomColor();
                }
                if (currentMotion.isEmpty()) {
                    currentMotion = getRandomVerb();
                }
                break;
            case 3:
                if (words.length < 3) {
                    Toast.makeText(this, "Please provide three words (e.g., 'red jumping ball').", Toast.LENGTH_LONG).show();
                    return;
                }
                break;
        }

        // Updated processResults method call
        loadImageAndApplyProperties(currentObject, currentColor, currentMotion, random);

        playSoundForObject(currentObject);

        currentColor = "";
        currentMotion = "";
        currentAdjective = "";
        currentObject = "";
    }
    private String getRandomColor() {
        List<String> colors = new ArrayList<>(colorMap.keySet());
        return colors.get(new Random().nextInt(colors.size()));
    }
    private void playSoundForObject(String objectName) {
        Integer soundId = soundMap.get(objectName.toLowerCase());
        if (soundId != null) {
            // Play specific sound for the recognized object
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else if (imageExists(objectName)) {
            // Play default sound only if an image exists but has no specific sound
            Integer defaultSoundId = soundMap.get("default");
            if (defaultSoundId != null) {
                soundPool.play(defaultSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
            }
        }
    }

    // Helper method to check if an image exists for the object
    private boolean imageExists(String objectName) {
        File imageFilePNG = new File(imageFolder, objectName + ".png");
        File imageFileSVG = new File(imageFolder, objectName + ".svg");
        return imageFilePNG.exists() || imageFileSVG.exists();
    }

    private void loadSounds() {
        soundMap.put("cat", soundPool.load(this, R.raw.cat, 1));
        soundMap.put("ball", soundPool.load(this, R.raw.ball, 1));
        soundMap.put("cow", soundPool.load(this, R.raw.cow, 1));
        soundMap.put("rooster", soundPool.load(this, R.raw.rooster, 1));
        soundMap.put("dog", soundPool.load(this, R.raw.dog, 1));
        soundMap.put("default", soundPool.load(this, R.raw.common, 1));  // Default sound
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
        return adjectives.isEmpty() ? "big" : adjectives.get(random.nextInt(adjectives.size()));
    }

    // Helper function to get random motion
    private String getRandomVerb() {
        List<String> motions = new ArrayList<>(motionMap.keySet());
        return motions.get(new Random().nextInt(motions.size()));

    }




    private List<File> getMatchingImagesForNoun(String noun) {
        // Use a FileFilter with a lambda expression to filter files
        File[] files = imageFolder.listFiles(file -> file.getName().toLowerCase().startsWith(noun.toLowerCase()) && (file.getName().endsWith(".png") || file.getName().endsWith(".svg")));

        // Convert the array to a list and return it
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


    // Save session data to SharedPreferences (including unrecognized words)
    private void saveSessionData(String startTime, String endTime, String spokenWords, String unrecognizedWords) {
        SharedPreferences sharedPreferences = getSharedPreferences("SessionHistoryPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Retrieve the current user name (the one who is speaking)
        SharedPreferences userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String currentUser = userPrefs.getString("current_user", "default");

        Set<String> historySet = sharedPreferences.getStringSet("history_" + currentUser, new HashSet<>());
        Set<String> updatedHistorySet = new HashSet<>(historySet);

        // Create session data with start time, end time, spoken words, and unrecognized words
        String sessionData = "Start: " + startTime + "\nEnd: " + endTime + "\nWords: " + spokenWords + "\nUnrecognized: " + unrecognizedWords;
        updatedHistorySet.add(sessionData);

        editor.putStringSet("history_" + currentUser, updatedHistorySet);
        editor.apply();
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

            imageView.setImageBitmap(bitmap);

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

    // Apply color only to the object, ignoring transparent pixels (background)
    private Bitmap applySelectiveColorToObject(Bitmap originalBitmap, int color) {
        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        for (int x = 0; x < mutableBitmap.getWidth(); x++) {
            for (int y = 0; y < mutableBitmap.getHeight(); y++) {
                int pixel = mutableBitmap.getPixel(x, y);

                // Skip fully transparent pixels (background)
                if (Color.alpha(pixel) != 0 && isBright(pixel)) {
                    mutableBitmap.setPixel(x, y, blendColors(pixel, color));
                }
            }
        }
        return mutableBitmap;
    }

    // Apply motion to the image based on the detected motion word
    private void applyMotion(String motion, Random rand, double speed) {
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

    private boolean isBright(int pixel) {
        int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
        return brightness > 150;
    }

    private int blendColors(int originalColor, int blendColor) {
        int red = (Color.red(originalColor) + Color.red(blendColor)) / 2;
        int green = (Color.green(originalColor) + Color.green(blendColor)) / 2;
        int blue = (Color.blue(originalColor) + Color.blue(blendColor)) / 2;
        return Color.argb(Color.alpha(originalColor), red, green, blue);
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
        adjectiveMap.put("stripped", "pattern");
        adjectiveMap.put("dotted", "pattern");

        // Map to handle texture adjectives
        adjectiveMap.put("furry", "texture");
        adjectiveMap.put("smooth", "texture");

        adjectiveMap.put("transparent", "opacity");
        adjectiveMap.put("opaque", "opacity");

        adjectiveMap.put("shiny","shiny");

        // Map to handle speed adjectives
        adjectiveMap.put("fast", "speed");
        adjectiveMap.put("slow", "speed");
    }


    private HashMap<String, ArrayList<RecordedMotion>> readMotions() {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(openFileInput("motionData.bin"))) {
            return (HashMap<String, ArrayList<RecordedMotion>>) objectInputStream.readObject();
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

            case "pattern":
                // Apply pattern overlay transformations here
                // e.g., striped or dotted patterns
                if (adjective.equals("stripped")) {
                    // Implement logic to overlay striped pattern on the image
                     return overlayPatternOnImage("stripped", bitmap);
                } else if (adjective.equals("dotted")) {
                    // Implement logic to overlay dotted pattern on the image
                     return overlayPatternOnImage("dotted", bitmap);
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

            case "texture":
                // Apply texture transformations here
                if (adjective.equals("furry")) {
                   return addTextureEffect("furry", bitmap);
                } else if (adjective.equals("smooth")) {
                    return addTextureEffect("smooth", bitmap);
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
                case "stripped":
                    // Draw horizontal stripes on the object only (skip transparent pixels)
                    for (int y = 0; y < mutableBitmap.getHeight(); y += 30) {  // Change `30` to adjust stripe spacing
                        // Check for the object boundaries on this row
                        int startX = -1;
                        int endX = -1;
                        for (int x = 0; x < mutableBitmap.getWidth(); x++) {
                            int pixel = mutableBitmap.getPixel(x, y);
                            if (Color.alpha(pixel) != 0) {
                                if (startX == -1) startX = x;  // Mark the start of the object
                                endX = x;  // Continuously update endX to mark the end of the object
                            }
                        }

                        // Draw the stripe line only within the object boundaries
                        if (startX != -1 && endX != -1) {
                            canvas.drawLine(startX, y, endX, y, paint);
                        }
                    }
                    break;

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
        audMan.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);
        audMan.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
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
