package com.example.speechtoimageapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

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
    private Button speakButton;

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

    private int wordLimit = 1;  // Default to one word

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech);

        imageView = findViewById(R.id.imageView);
        speakButton = findViewById(R.id.buttonSpeak);
        handler = new Handler();

        // Initialize color and motion mappings
        initializeColorMap();
        motionMap = readMotions();

        // Request microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
        }

        // Initialize Speech Recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

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

        // Set recognition listener
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
                motionMap = readMotions();
            }

            @Override
            public void onPartialResults(Bundle bundle) {}

            @Override
            public void onError(int error) {
                if (isListening) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }

            @Override
            public void onEvent(int i, Bundle bundle) {}
        });

        // Set button click listener to start speech recognition
        speakButton.setOnClickListener(v -> {
            spokenWords.setLength(0);  // Clear previous spoken words
            unrecognizedWords.setLength(0);  // Clear previous unrecognized words
            startTime = getCurrentTimestamp();  // Capture the start time
            speechRecognizer.startListening(recognizerIntent);
            speakButton.setVisibility(View.GONE);  // Hide the speak button after tapping
            muteAudio(); // Mute speech recognizer beep sound
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speechRecognizer.cancel();
        speechRecognizer.destroy();
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void processResults(String command) {
        String[] words = command.toLowerCase().split(" ");
        Random rand = new Random();

        // Limit the number of words processed based on the word limit (One, Two, or Three Words mode)
        words = Arrays.copyOfRange(words, 0, Math.min(words.length, wordLimit));

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

            // Detect object (like "sun", "house")
            if (new File(imageFolder, word + ".png").exists() || new File(imageFolder, word + ".svg").exists()) {
                //currentObject = word;
                //recognized = true;
                List<File> matchingImages = getMatchingImagesForNoun(word);
                if (!matchingImages.isEmpty()) {
                    // Randomly select one matching image and display it
                    File randomImage = matchingImages.get(random.nextInt(matchingImages.size()));
                    currentObject = randomImage.getName().split("\\.")[0];
                    //currentObject.add(word);
                }

                // Build the phrase: color, motion, object
                StringBuilder phrase = new StringBuilder();
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

                // Reset color and motion for the next phrase
                currentColor = "";
                currentMotion = "";
                continue;
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
                unMuteAudio(); // Un-mute audio again back to previous volume
                return;
            }
        }
    }
    private List<File> getMatchingImagesForNoun(String noun) {
        // Use a FileFilter with a lambda expression to filter files
        File[] files = imageFolder.listFiles(file -> file.getName().toLowerCase().contains(noun.toLowerCase()) && (file.getName().endsWith(".png") || file.getName().endsWith(".svg")));

        // Convert the array to a list and return it
        return files != null ? Arrays.asList(files) : new ArrayList<>();
    }

    // Save session data to SharedPreferences (including unrecognized words)
    private void saveSessionData(String startTime, String endTime, String spokenWords, String unrecognizedWords) {
        SharedPreferences sharedPreferences = getSharedPreferences("SessionHistoryPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        Set<String> historySet = sharedPreferences.getStringSet("history", new HashSet<>());
        Set<String> updatedHistorySet = new HashSet<>(historySet);

        // Create session data with start time, end time, spoken words, and unrecognized words
        String sessionData = "Start: " + startTime + "\nEnd: " + endTime + "\nWords: " + spokenWords + "\nUnrecognized: " + unrecognizedWords;
        updatedHistorySet.add(sessionData);

        editor.putStringSet("history", updatedHistorySet);
        editor.apply();
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
            if (color != null && !color.isEmpty()) {
                int selectedColor = colorMap.get(color);
                bitmap = applySelectiveColorToObject(bitmap, selectedColor);  // Apply color to object only
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
            handler.postDelayed(() -> imageView.setImageResource(0), 5000);  // Clear image after 5 seconds
        }
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
        long increment = motion.increment;

        Handler motionHandler = new Handler();
        motionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!posList.isEmpty()) {
                    List<Float> pos = posList.remove(0);
                    imageView.setX(pos.get(0) - imageView.getWidth() / 2);
                    imageView.setY(pos.get(1) - imageView.getHeight() / 2);
                    motionHandler.postDelayed(this, increment);
                }
            }
        }, increment);
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

    private HashMap<String, ArrayList<RecordedMotion>> readMotions() {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(openFileInput("motionData.bin"))) {
            return (HashMap<String, ArrayList<RecordedMotion>>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
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
