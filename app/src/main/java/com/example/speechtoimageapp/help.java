package com.example.speechtoimageapp;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class help extends AppCompatActivity {

    private File imageFolder;
    private HashMap<String, Integer> colorMap = new HashMap<>();
    private HashMap<String, String> adjectiveMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        initializeColorMap();
        initializeAdjectiveMap();
        // Initialize image folder for nouns
        imageFolder = new File(getFilesDir(), "UploadedImages");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }

        // Refresh nouns and motions data each time help is opened
        updateDataInSharedPreferences();

        // Retrieve updated nouns, verbs (motions), and adjectives
        SharedPreferences tagPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
        Map<String, ?> allTags = tagPreferences.getAll(); //
        SharedPreferences sharedPreferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);
        Set<String> adjectives = sharedPreferences.getStringSet("adjectives", new HashSet<>());
        Set<String> colours = sharedPreferences.getStringSet("colours", new HashSet<>());
        Set<String> motions = sharedPreferences.getStringSet("motions", new HashSet<>());
        Set<String> nouns = sharedPreferences.getStringSet("nouns", new HashSet<>());
        for (Object value : allTags.values()) {
            if (value instanceof String) {
                nouns.add((String) value);
            } // Cast each value to String before adding
        }
        // Display lists in TextViews
        TextView adjectiveView = findViewById(R.id.adjectiveList);
        TextView coloursView = findViewById(R.id.colorsList);
        TextView verbView = findViewById(R.id.verbList);
        TextView nounView = findViewById(R.id.nounList);

        adjectiveView.setText("Adjectives: " + String.join(", ", adjectives));
        coloursView.setText("Colours: " + String.join(", ", colours));
        verbView.setText("Verbs: " + String.join(", ", motions));
        nounView.setText("Nouns: " + String.join(", ", nouns));
        // Populate Speech Page Steps
        TextView speechPageSteps = findViewById(R.id.speechPageSteps);
        StringBuilder speechSteps = new StringBuilder();
        speechSteps.append("1) Select a user from the dropdown menu or add a new user in 'Add User'.\n");
        speechSteps.append("2) Enable the toggle to start Demo.\n");
        speechSteps.append("3) Use simple commands like 'ball' to display images.\n");
        speechSteps.append("4) Combine multiple commands like 'red jumping ball' for dynamic output.\n");
        speechSteps.append("5) Stop the session using the 'Kill Word' or the 'Stop' button.\n");
        speechSteps.append("   - *One Word Mode*: Use the motion name alone (e.g., 'dog').\n");
        speechSteps.append("   - *Two Word Mode*: Combine motion with a noun or adjective or verb(e.g., 'red dog').\n");
        speechSteps.append("   - *Three Word Mode*: Combine motion with noun, adjective and verb (e.g., 'dog red jump').\n");
        speechPageSteps.setText(speechSteps.toString());

// Upload Image Page Steps
        TextView uploadImagePageSteps = findViewById(R.id.uploadImagePageSteps);
        StringBuilder uploadSteps = new StringBuilder();
        uploadSteps.append("1) Click the 'Upload' button to select new images.\n");
        uploadSteps.append("2) Ensure parts of the image that need to be white are correctly rendered by specifying 'white' as an adjective in the Speech Page.\n");
        uploadSteps.append("3) Rename the image for easier voice recognition.\n");
        uploadSteps.append("4) Add optional tags for better search (e.g., 'puppy, cute').\n");
        uploadSteps.append("5) Upload audio to associate it with the image.\n");
        uploadSteps.append("6) Save changes to make the image available on the Speech Page.\n");
        uploadImagePageSteps.setText(uploadSteps.toString());

// Motion Setup Page Steps
        TextView motionSetupPageSteps = findViewById(R.id.motionSetupPageSteps);
        StringBuilder motionSteps = new StringBuilder();
        motionSteps.append("1) Click the 'Record' button to start creating a new motion.\n");
        motionSteps.append("2) Perform the desired motion (e.g., bounce, spin) and stop recording.\n");
        motionSteps.append("3) Name the motion for easy recognition (e.g., 'jumping').\n");
        motionSteps.append("4) Assign audio to play when the motion is triggered.\n");
        motionSteps.append("5) Save the motion to make it available for voice commands.\n");
        motionSteps.append("6) Use the motion with word modes on the Speech Page:\n");

        motionSetupPageSteps.setText(motionSteps.toString());

// Session Data Page Steps
        TextView sessionDataPageSteps = findViewById(R.id.SessionDataPageTitlePageSteps);
        StringBuilder sessionDataSteps = new StringBuilder();
        sessionDataSteps.append("1) View session history by selecting a user.\n");
        sessionDataSteps.append("2) Check spoken commands, recognized words.\n");
        sessionDataSteps.append("3) Delete specific session data if needed by long-pressing on it.\n");
        sessionDataSteps.append("4) Use the session data to analyze user interactions.\n");
        sessionDataPageSteps.setText(sessionDataSteps.toString());

// Add User Page Steps
        TextView addUserPageSteps = findViewById(R.id.AdduserPageSteps);
        StringBuilder addSteps = new StringBuilder();
        addSteps.append("1) Click the 'Add User' button to create a new user.\n");
        addSteps.append("2) Enter the user name and 'Kill Word'.\n");
        addSteps.append("3) Save the user to make them available on the Speech Page.\n");
        addSteps.append("4) Delete a user by long-pressing their name.\n");
        addUserPageSteps.setText(addSteps.toString());
    }

    // Method to update nouns and motions in SharedPreferences
    private void updateDataInSharedPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences("SpeechToImageAppSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        SharedPreferences tagPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
        Map<String, ?> allTags = tagPreferences.getAll(); //
        // Update nouns by scanning the image folder
        Set<String> nouns = loadNounsFromImageFolder();
        for (Object value : allTags.values()) {
            if (value instanceof String) {
                nouns.add((String) value);
            } else {
                Log.w("SharedPreferences", "Unexpected value type: " + value.getClass().getSimpleName());
            }
        }

      
        if(nouns.isEmpty()) {
            nouns.add("cake");
            nouns.add("ball");
            nouns.add("rooster");
            nouns.add("bird");
            nouns.add("pizza");
        }

        // Update motions with custom logic or dynamic loading
        HashMap<String, ArrayList<RecordedMotion>> motions = loadMotions();

        // Save updated nouns and motions to SharedPreferences
        if(!nouns.isEmpty())
            editor.putStringSet("nouns", nouns);
        else
            editor.putStringSet("nouns", null);
        if(!(motions == null)) {
            editor.putStringSet("motions", motions.keySet());
        }
        else{
            editor.putStringSet("motions", null);
        }
        editor.putStringSet("colours", colorMap.keySet());
        editor.putStringSet("adjectives",adjectiveMap.keySet());
        editor.apply();

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
    // Method to load or update motions (verbs)
    private HashMap<String, ArrayList<RecordedMotion>> loadMotions() {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(openFileInput("motionData.bin"))) {
            return (HashMap<String, ArrayList<RecordedMotion>>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
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
        adjectiveMap.put("striped", "pattern");
        adjectiveMap.put("dotted", "pattern");

        adjectiveMap.put("transparent", "opacity");
        adjectiveMap.put("opaque", "opacity");


        // Map to handle speed adjectives
        adjectiveMap.put("fast", "speed");
        adjectiveMap.put("slow", "speed");
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
}