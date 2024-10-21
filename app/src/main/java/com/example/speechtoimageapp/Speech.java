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
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import java.util.concurrent.atomic.AtomicInteger;

public class Speech extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 1;
    private ImageView imageView;
    private SpeechRecognizer speechRecognizer;
    private Handler handler;
    private boolean isListening = true;
    private MediaPlayer mediaPlayer;  // MediaPlayer for audio playback

    // Define color and motion mappings
    private HashMap<String, Integer> colorMap = new HashMap<>();
    private HashMap<String, ArrayList<RecordedMotion>> motionMap = new HashMap<>();
    private File imageFolder;

    private StringBuilder spokenWords = new StringBuilder();  // To collect spoken phrases
    private StringBuilder unrecognizedWords = new StringBuilder();  // To collect unrecognized words
    private String startTime;  // Track the start time
    private Random random = new Random();
    // Temporary variables to store detected color, motion, and object
    private String currentColor = "";
    private String currentMotion = "";
    private String currentObject = "";
    private String currentAdjective = "";

    private int wordLimit = 1;  // Default to one word
    private HashMap<String, String> adjectiveMap = new HashMap<>();
    // Flag that allows for motion interruption in case new input happens during the previous motion's duration
    boolean isMotionPlaying = false;
    private ArrayList<String> userList;
    // Variables for both motion threads
    private HandlerThread motionThread;
    private HandlerThread rotationThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech);

        imageView = findViewById(R.id.imageView);
        handler = new Handler();

        // Initialize color and motion mappings
        initializeColorMap();
        initializeAdjectiveMap();
        motionMap = readMotions();

        // Load uploaded images directory
        imageFolder = new File(getFilesDir(), "UploadedImages");

        // Load word mode from SharedPreferences
        SharedPreferences preferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);
        String wordMode = preferences.getString("WordMode", "One Word");  // Default to One Word
        wordLimit = wordMode.equals("Two Words") ? 2 : wordMode.equals("Three Words") ? 3 : 1;
        // Set up recognizer intent
        final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        // Load users from User class
        userList = User.loadUserList(this);

        // Show name input dialog when the page opens
        promptForUserSelection();

        // Request microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
        }

        // Initialize Speech Recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }

    // Show a dialog to prompt the user to enter their name
    private void promptForUserSelection() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Your Name");

        // Set up the dropdown (Spinner) for selecting the user
        final Spinner userSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, userList);
        userSpinner.setAdapter(adapter);
        builder.setView(userSpinner);

        builder.setPositiveButton("Start", (dialog, which) -> {
            String selectedName = userSpinner.getSelectedItem().toString().trim();

            // Save the selected user in SharedPreferences
            SharedPreferences userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor userEditor = userPrefs.edit();
            userEditor.putString("current_user", selectedName);
            userEditor.apply();

            // Mute system sounds before starting speech recognition
            muteAudio();

            // Start the speech recognition process
            startSpeechRecognition();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            finish();  // Close the activity if the user cancels
        });

        builder.show();
    }


    // Start the speech recognition process
    private void startSpeechRecognition() {
        final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        // Set recognition listener
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

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
                motionMap = readMotions();
            }

            @Override
            public void onPartialResults(Bundle bundle) {
            }

            @Override
            public void onError(int error) {
                if (isListening) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }

            @Override
            public void onEvent(int i, Bundle bundle) {
            }
        });

        // Start listening
        spokenWords.setLength(0);  // Clear previous spoken words
        unrecognizedWords.setLength(0);  // Clear previous unrecognized words
        startTime = getCurrentTimestamp();  // Capture the start time
        speechRecognizer.startListening(recognizerIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speechRecognizer.cancel();
        speechRecognizer.destroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    // Other methods for handling speech processing...


    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void processResults(String command) {
        String[] words = command.toLowerCase().split(" ");
        Random rand = new Random();

        // Limit the number of words processed based on the word limit (One, Two, or Three Words mode)
        words = Arrays.copyOfRange(words, 0, Math.min(words.length, wordLimit));

        StringBuilder phrase = new StringBuilder();

        for (String word : words) {
            word = word.trim();  // Clean up word

            boolean recognized = false;

            // Detect color
            if (colorMap.containsKey(word)) {
                currentColor = word;
                recognized = true;
            }

            // Detect motion
            if (motionMap.containsKey(word)) {
                currentMotion = word;
                recognized = true;
            }

            if (adjectiveMap.containsKey(word)) {
                currentColor = word;
                recognized = true;
            }

            // Detect object (like "sun", "house")
            if (new File(imageFolder, word + ".png").exists() || new File(imageFolder, word + ".svg").exists()) {
                List<File> matchingImages = getMatchingImagesForNoun(word);
                if (!matchingImages.isEmpty()) {
                    File randomImage = matchingImages.get(rand.nextInt(matchingImages.size()));
                    currentObject = randomImage.getName().split("\\.")[0];
                }

                recognized = true;
            }

            // If the word was not recognized as color, motion, or object, store it in unrecognizedWords
            if (!recognized) {
                if (unrecognizedWords.length() > 0) {
                    unrecognizedWords.append(", ");
                }
                unrecognizedWords.append(word);
            }
            // Handle the stop command
            if (word.equals("stop")) {
                String endTime = getCurrentTimestamp();
                saveSessionData(startTime, endTime, spokenWords.toString().trim(), unrecognizedWords.toString().trim());
                stopListeningAndReturnToMain();
                unMuteAudio();  // Un-mute audio again back to previous volume
                return;  // Stop processing further words
            }
        }

        // Handle different word modes
        switch (wordLimit) {
            case 1:  // One word mode
                // If the user spoke only one word, fill in random image, color, and motion
                if (currentObject.isEmpty()) {
                    currentObject = getRandomObject();  // Random object
                }
                if (currentColor.isEmpty()) {
                    currentColor = getRandomColor();  // Random color
                }
                if (currentMotion.isEmpty()) {
                    currentMotion = getRandomMotion();  // Random motion
                }
                break;

            case 2:  // Two word mode
                // If two words are spoken, randomly select the third item (image/motion/color)
                if (currentObject.isEmpty()) {
                    currentObject = getRandomObject();  // Random object
                }
                if (currentColor.isEmpty()) {
                    currentColor = getRandomColor();  // Random color
                }
                if (currentMotion.isEmpty()) {
                    currentMotion = getRandomMotion();  // Random motion
                }
                break;

            case 3:  // Three word mode
                // User must speak three words; if not, give an error message and return
                if (words.length < 3) {
                    Toast.makeText(this, "Please provide three words (e.g., 'red jumping ball').", Toast.LENGTH_LONG).show();
                    return;
                }
                break;
        }

        // Build the phrase: color, motion, object
        if (!currentColor.isEmpty()) phrase.append(currentColor).append(" ");
        if (!currentMotion.isEmpty()) phrase.append(currentMotion).append(" ");
        phrase.append(currentObject);

        // Add phrase to spokenWords for display
        if (spokenWords.length() > 0) {
            spokenWords.append(", ");
        }
        spokenWords.append(phrase);

        // Apply the color and motion to the image
        loadImageAndApplyProperties(currentObject, currentColor, currentMotion, rand);
        playSoundForObject(currentObject);  // Play corresponding sound for the object

        // Reset color and motion for the next phrase
        currentColor = "";
        currentMotion = "";
        currentObject = "";
    }

    // Helper function to get random object
    private String getRandomObject() {
        File[] files = imageFolder.listFiles(file -> file.getName().endsWith(".png") || file.getName().endsWith(".svg"));
        if (files != null && files.length > 0) {
            File randomImage = files[new Random().nextInt(files.length)];
            return randomImage.getName().split("\\.")[0];
        }
        return "default_object";  // Default if no image available
    }

    // Helper function to get random color
    private String getRandomColor() {
        List<String> colors = new ArrayList<>(colorMap.keySet());
        return colors.get(new Random().nextInt(colors.size()));
    }

    // Helper function to get random motion
    private String getRandomMotion() {
        List<String> motions = new ArrayList<>(motionMap.keySet());
        return motions.get(new Random().nextInt(motions.size()));

    }


    // New method to play sound for the object
    private void playSoundForObject(String objectName) {
        if (mediaPlayer != null) {
            mediaPlayer.release();  // Release any previous media player
        }

        // Extract base object name by removing numbers (e.g., "cat1" becomes "cat")
        String baseObjectName = objectName.replaceAll("\\d", "");

        // First, try to find an audio file that matches the base object name (e.g., cat.mp3)
        int soundResId = getResources().getIdentifier(baseObjectName.toLowerCase(), "raw", getPackageName());

        // If no specific sound matches, play the common/default sound
        if (soundResId == 0) {
            soundResId = R.raw.common;  // Replace with your common sound resource ID
        }

        // Create a new MediaPlayer and start playing the sound
        mediaPlayer = MediaPlayer.create(this, soundResId);
        mediaPlayer.start();
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

    // Load the image and apply color and motion properties
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
            // Apply color only to the object, skipping transparent pixels (background)
            if (color != null && !color.isEmpty() && !adjectiveMap.containsKey(color) ) {
                int selectedColor = colorMap.get(color);
                bitmap = applySelectiveColorToObject(bitmap, selectedColor);  // Apply color to object only
            }

            if(color != null && adjectiveMap.containsKey(color)) {
                bitmap = handleAdjective(bitmap, color);
            }

            imageView.setImageBitmap(bitmap);

            // Apply motion to the image
            if (motion != null && !motion.isEmpty()) {
                applyMotion(motion, rand);
            } else {
                // Re-center image
                imageView.setTranslationX(0);
                imageView.setTranslationY(0);
            }
            handler.removeCallbacksAndMessages(null); // Clear currently processing handler callbacks
            handler.postDelayed(() -> {
                imageView.setImageResource(0);  // Clear image after 5 seconds
                resetImageViewScale();  // Reset scaling after clearing the image
            }, 5000);
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
    private void applyMotion(String motion, Random rand) {
        ArrayList<RecordedMotion> selectedMotions = motionMap.get(motion);
        if (selectedMotions != null && !selectedMotions.isEmpty()) {
            RecordedMotion selectedMotion = selectedMotions.size() > 0
                    ? selectedMotions.get(rand.nextInt(selectedMotions.size()))
                    : selectedMotions.get(0);

            moveImageBasedOnMotion(selectedMotion);
        }
    }

    private void moveImageBasedOnMotion(RecordedMotion motion) {
        List<List<Float>> posList = motion.posList;
        List<Long> posIncrements = motion.posIncrements;
        imageView.setRotation(0);

        // Create a thread each for motion and rotation, so they can run concurrently
        HandlerThread motionThread = new HandlerThread("MotionHandlerThread");
        motionThread.start();
        Handler motionHandler = new Handler(motionThread.getLooper());
        motionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!posList.isEmpty()) {
                    // Get and remove next position
                    List<Float> pos = posList.remove(0);
                    // Get and remove current position duration
                    long currentIncrement = posIncrements.remove(0);
                    // Set image's position to next position
                    imageView.setX(pos.get(0) - imageView.getWidth() / 2);
                    imageView.setY(pos.get(1) - imageView.getHeight() / 2);
                    // Repeat until posList is empty
                    motionHandler.postDelayed(this, currentIncrement);
                } else {
                    motionThread.quit();
                }
            }
        }, posIncrements.get(0));

        long durationPerIncrement = motion.duration / 500;
        float degreesPerIncrement = ((((float) motion.duration / 1000) * motion.rotationsPerSecond) / 500) * 360;
        AtomicInteger rotationCount = new AtomicInteger(0);

        HandlerThread rotationThread = new HandlerThread("RotationHandlerThread");
        rotationThread.start();
        Handler rotationHandler = new Handler(rotationThread.getLooper());
        rotationHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int count = rotationCount.getAndIncrement();
                imageView.setRotation(count * degreesPerIncrement);
                if (rotationCount.get() < 500) {
                    motionHandler.postDelayed(this, durationPerIncrement);
                } else {
                    rotationThread.quit();
                }
            }
        }, durationPerIncrement);
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
        adjectiveMap.put("clear", "opacity");

        adjectiveMap.put("shiny","shiny");
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
                } else if (adjective.equals("clear")) {
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
