package com.example.speechtoimageapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UploadImage extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PICK_AUDIO_REQUEST = 2;
    private RecyclerView recyclerView;
    private ImageAdapter imageAdapter;
    private Button deleteButton;
    private File imageFolder;
    private Uri selectedImageUri;
    private Uri selectedAudioUri;  // Store the selected audio URI
    private Button editButton;
    private static final List<String> DEFAULT_IMAGE_NAMES = Arrays.asList("dog", "bird", "cake", "rooster", "ball");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_image);

        // Request external storage permission
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        Button uploadButton = findViewById(R.id.button_upload);
        deleteButton = findViewById(R.id.button_delete);
        recyclerView = findViewById(R.id.recyclerView_images);
        editButton = findViewById(R.id.button_edit);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 5));  // 5 columns for tablet view

        imageFolder = new File(getFilesDir(), "UploadedImages");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }

        saveDefaultImagesAndAudiosToInternalStorage();
        loadImagesFromInternalStorage();

        initializeDefaultTags(); // Add default tags for bundled images
        uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            String[] mimeTypes = {"image/png", "image/svg+xml", "image/jpeg", "image/jpg", "application/msword"};  // Allow PNG, SVG, JPEG, and document types like PDF, DOC
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });
        deleteButton.setOnClickListener(v -> {
            File selectedFile = imageAdapter.getSelectedFile();
            if (selectedFile != null) {
                String fileName = selectedFile.getName().replaceFirst("[.][^.]+$", ""); // Get name without extension

                if (selectedFile.delete()) {
                    // Remove the associated tags, including default ones
                    removeTagsForImage(fileName);

                    Toast.makeText(this, "Image deleted: " + selectedFile.getName(), Toast.LENGTH_SHORT).show();
                    loadImagesFromInternalStorage(); // Refresh the list
                } else {
                    Toast.makeText(this, "Failed to delete image.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please select an image to delete.", Toast.LENGTH_SHORT).show();
            }
        });


// Handle edit action
        editButton.setOnClickListener(v -> {
            File selectedFile = imageAdapter.getSelectedFile();
            if (selectedFile != null) {
                showEditDialog(selectedFile); // Pass the selected file to the edit dialog
            } else {
                Toast.makeText(this, "Please select an image to edit.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditDialog(File selectedFile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Image");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rename_image, null);
        EditText nameInput = dialogView.findViewById(R.id.editTextImageName);
        EditText tagsInput = dialogView.findViewById(R.id.editTextImageTags);
        Button uploadAudioButton = dialogView.findViewById(R.id.buttonUploadAudio);

        // Populate current values
        String fileExtension = selectedFile.getName().substring(selectedFile.getName().lastIndexOf("."));
        nameInput.setText(selectedFile.getName().replaceFirst("[.][^.]+$", "")); // Current name
        tagsInput.setText(getTagsForImage(selectedFile.getName())); // Current tags

        uploadAudioButton.setOnClickListener(v -> {
            Intent audioIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            audioIntent.setType("audio/*");
            startActivityForResult(audioIntent, PICK_AUDIO_REQUEST);
        });

        builder.setView(dialogView);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            String newTags = tagsInput.getText().toString().trim();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Rename file with appropriate extension
            File newFile = new File(imageFolder, newName + fileExtension);
            if (!newFile.exists() || newFile.equals(selectedFile)) {
                if (selectedFile.renameTo(newFile)) {
                    // Save tags
                    saveTagsToInternalStorage(newName, newTags);

                    // Save audio if selected
                    if (selectedAudioUri != null) {
                        saveAudioToInternalStorage(newName);
                        selectedAudioUri = null; // Clear after saving
                    }

                    // Refresh RecyclerView
                    loadImagesFromInternalStorage();
                } else {
                    Toast.makeText(this, "Failed to rename file.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "File with this name already exists.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private String getTagsForImage(String imageName) {
        SharedPreferences sharedPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
        // Fetch tags using the image name (without extension) as the key
        return sharedPreferences.getString(imageName.replaceFirst("[.][^.]+$", ""), "");
    }


    private void saveDefaultImagesAndAudiosToInternalStorage() {
        // Check if the folder is empty
        File[] files = imageFolder.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".svg"));
        if (files != null && files.length > 0) {
            // If the folder is not empty, skip adding default images
            return;
        }

        // Proceed to save default images and audios only if the folder is empty
        String[] defaultImages = {"dog", "bird", "cake", "rooster", "ball"};
        for (String name : defaultImages) {
            saveDrawableToInternalStorage(name, "png");
            saveDrawableToInternalStorage(name, "mp3");
        }
    }


    private void initializeDefaultTags() {
        // Map of default image names to their tags
        Map<String, String> defaultTags = new HashMap<>();
        defaultTags.put("dog", "puppy,dog,animal");
        defaultTags.put("bird", "sparrow");
        defaultTags.put("cake", "dessert,cake");
        defaultTags.put("rooster", "chicken,rooster");
        defaultTags.put("ball", "sphere,ball");

        SharedPreferences sharedPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Check if tags have already been initialized
        boolean isTagsInitialized = sharedPreferences.getBoolean("DefaultTagsInitialized", false);

        if (!isTagsInitialized) {
            // Initialize tags for the first time
            for (Map.Entry<String, String> entry : defaultTags.entrySet()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
            editor.putBoolean("DefaultTagsInitialized", true); // Mark tags as initialized
            editor.apply();
        } else {
            // Restore tags only for existing default images
            File[] imageFiles = imageFolder.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".svg"));
            if (imageFiles != null) {
                for (File file : imageFiles) {
                    String imageName = file.getName().replaceFirst("[.][^.]+$", ""); // Remove file extension
                    if (defaultTags.containsKey(imageName) && !sharedPreferences.contains(imageName)) {
                        editor.putString(imageName, defaultTags.get(imageName));
                    }
                }
                editor.apply();
            }
        }
    }

    private void saveDrawableToInternalStorage(String name, String extension) {
        int resourceId = getResources().getIdentifier(name, extension.equals("png") ? "drawable" : "raw", getPackageName());
        if (resourceId == 0) {
            return;
        }

        File file = new File(imageFolder, name + "." + extension);
        if (file.exists()) return;

        try (InputStream inputStream = getResources().openRawResource(resourceId);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving default files", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();

            // Show rename dialog with option to upload audio
            showRenameAndAudioUploadDialog();
        }

        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedAudioUri = data.getData();
            Toast.makeText(this, "Audio file selected.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameAndAudioUploadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Image and Add Audio");

        // Create a layout for the dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rename_image, null);
        EditText input = dialogView.findViewById(R.id.editTextImageName);
        Button uploadAudioButton = dialogView.findViewById(R.id.buttonUploadAudio);

        builder.setView(dialogView);

        // Set up audio upload button
        uploadAudioButton.setOnClickListener(v -> {
            Intent audioIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            audioIntent.setType("audio/*");
            startActivityForResult(audioIntent, PICK_AUDIO_REQUEST);
        });

        builder.setPositiveButton("Add", (dialog, which) -> {
            String newName = input.getText().toString();

            // Fetch the tags entered in the EditText for tags
            EditText tagsEditText = dialogView.findViewById(R.id.editTextImageTags);
            String tagsInput = tagsEditText.getText().toString().trim();
            if (!newName.isEmpty() && selectedImageUri != null) {
                // Save image to internal storage
                saveImageToInternalStorage(selectedImageUri, newName);
                // Save tags in SharedPreferences
                saveTagsToInternalStorage(newName, tagsInput);
            } else {
                Toast.makeText(this, "Please enter a valid name and select an image.", Toast.LENGTH_SHORT).show();
            }
            SharedPreferences sharedPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(newName, tagsInput); // Save tags against the image name
            editor.apply();

// Debug log to confirm tag storage
            Log.d("TagSave", "Image: " + newName + " Tags: " + tagsInput);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            selectedAudioUri = null; // Clear audio URI if the user cancels
            dialog.cancel();
        });

        builder.show();
    }

    private void saveTagsToInternalStorage(String imageName, String tagsInput) {
        if (tagsInput == null || tagsInput.isEmpty()) {
            Toast.makeText(this, "Tags cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Normalize and deduplicate tags
        List<String> tags = Arrays.asList(tagsInput.split(","));
        tags = tags.stream().map(String::trim).map(String::toLowerCase).distinct().collect(Collectors.toList());

        // Check for conflicts with existing file names
        File[] imageFiles = imageFolder.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".svg"));
        if (imageFiles != null) {
            for (File file : imageFiles) {
                String fileNameWithoutExtension = file.getName().replaceFirst("[.][^.]+$", "").toLowerCase();
                for (String tag : tags) {
                    if (fileNameWithoutExtension.equals(tag)) {
                        Toast.makeText(this, "Tag '" + tag + "' conflicts with existing file: " + fileNameWithoutExtension, Toast.LENGTH_LONG).show();
                        return; // Stop saving tags if a conflict is found
                    }
                }
            }
        }

        // Overwrite confirmation
        SharedPreferences sharedPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
        String existingTags = sharedPreferences.getString(imageName, null);
        if (existingTags != null) {
            Toast.makeText(this, "Overwriting existing tags: " + existingTags, Toast.LENGTH_SHORT).show();
        }

        // Save tags to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(imageName, String.join(",", tags));
        editor.apply();

        Toast.makeText(this, "Tags saved successfully!", Toast.LENGTH_SHORT).show();
    }


    private void saveSvgToInternalStorage(Uri svgUri, String newName) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(svgUri);
            if (inputStream == null) {
                Toast.makeText(this, "Error opening SVG file!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Read the SVG content
            String svgContent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                svgContent = new String(inputStream.readAllBytes());
            }

            // Remove background rect element if it exists (usually white background)
            svgContent = svgContent.replaceAll("<rect.*?fill=['\"]#FFFFFF['\"].*?/>", "");

            // Save the modified SVG content
            File svgFile = new File(imageFolder, newName + ".svg");
            if (svgFile.exists()) {
                Toast.makeText(this, "File with this name already exists.", Toast.LENGTH_SHORT).show();
                return;
            }

            FileOutputStream fos = new FileOutputStream(svgFile);
            fos.write(svgContent.getBytes());
            fos.close();
            inputStream.close();

            Toast.makeText(this, "SVG saved successfully!", Toast.LENGTH_SHORT).show();

            loadImagesFromInternalStorage();  // Refresh the image list

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving SVG!", Toast.LENGTH_SHORT).show();
        }
    }


    private void saveImageToInternalStorage(Uri imageUri, String newName) {
        try {
            // Handle SVG or Raster images
            String mimeType = getContentResolver().getType(imageUri);
            if (mimeType != null && mimeType.equals("image/svg+xml")) {
                saveSvgToInternalStorage(imageUri, newName);
            } else {
                Bitmap bitmap = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), imageUri));
                } else {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                }

                if (bitmap == null) {
                    Toast.makeText(this, "Error decoding image. Please select a valid image file.", Toast.LENGTH_SHORT).show();
                    return;
                }

                File imageFile = new File(imageFolder, newName + ".png");
                if (imageFile.exists()) {
                    Toast.makeText(this, "File with this name already exists.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
            }

            // Save audio only if the user has selected it
            if (selectedAudioUri != null) {
                saveAudioToInternalStorage(newName);
                selectedAudioUri = null; // Clear after saving audio
            }

            loadImagesFromInternalStorage(); // Refresh the image list

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving image!", Toast.LENGTH_SHORT).show();
        }
    }


    private void saveAudioToInternalStorage(String newName) {
        if (selectedAudioUri == null) return;

        File audioFile = new File(imageFolder, newName + ".mp3");
        try (InputStream inputStream = getContentResolver().openInputStream(selectedAudioUri);
             FileOutputStream outputStream = new FileOutputStream(audioFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Toast.makeText(this, "Audio saved successfully!", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving audio file!", Toast.LENGTH_SHORT).show();
        }
    }
    private void removeTagsForImage(String imageName) {
        SharedPreferences sharedPreferences = getSharedPreferences("ImageTagsPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Remove tags for the specified image
        if (sharedPreferences.contains(imageName)) {
            editor.remove(imageName);
            editor.apply();
            Toast.makeText(this, "Tags removed for: " + imageName, Toast.LENGTH_SHORT).show();
        }
    }


    private Bitmap removePngBackground(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);

        int threshold = 245;
        for (int x = 0; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                int pixel = original.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);

                if (red >= threshold && green >= threshold && blue >= threshold) {
                    result.setPixel(x, y, Color.TRANSPARENT);
                } else {
                    result.setPixel(x, y, pixel);
                }
            }
        }
        return result;
    }

    private void loadImagesFromInternalStorage() {
        File[] imageFiles = imageFolder.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".svg"));

        // Check if the folder is empty
        if (imageFiles == null || imageFiles.length == 0) {
            // Folder is empty, repopulate with default images
            saveDefaultImagesAndAudiosToInternalStorage();
           initializeDefaultTags();

            imageFiles = imageFolder.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".svg"));
        }

        List<File> fileList = imageFiles != null ? Arrays.asList(imageFiles) : new ArrayList<>();

        // Initialize ImageAdapter without the boolean parameter
        imageAdapter = new ImageAdapter(this, fileList);

        // Set the adapter to the RecyclerView
        recyclerView.setAdapter(imageAdapter);

        // Enable or disable buttons based on the presence of images
        deleteButton.setEnabled(!fileList.isEmpty());
        editButton.setEnabled(!fileList.isEmpty());
    }
}

