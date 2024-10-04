package com.example.speechtoimageapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import java.util.Arrays;
import java.util.List;

public class UploadImage extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private RecyclerView recyclerView;
    private ImageAdapter imageAdapter;
    private Button deleteButton;
    private File imageFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_image);

        // Request external storage permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);

        // Initialize views
        Button uploadButton = findViewById(R.id.button_upload);
        deleteButton = findViewById(R.id.button_delete);
        recyclerView = findViewById(R.id.recyclerView_images);

        // Set the RecyclerView with GridLayoutManager to display images side by side
        recyclerView.setLayoutManager(new GridLayoutManager(this, 5));  // 5 columns for tablet view

        // Create image folder in internal storage
        imageFolder = new File(getFilesDir(), "UploadedImages");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }

        // Load existing images from internal storage
        loadImagesFromInternalStorage();

        // Set up upload button click listener
        // Set up upload button click listener
        uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");  // Allow any file type
            String[] mimeTypes = {"image/png", "image/svg+xml", "image/jpeg", "image/jpg", "application/msword"};  // Allow PNG, SVG, JPEG, and document types like PDF, DOC
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });


        // Set up delete button click listener
        deleteButton.setOnClickListener(v -> {
            List<File> selectedFiles = imageAdapter.getSelectedFiles();
            for (File file : selectedFiles) {
                if (file.delete()) {
                    Toast.makeText(UploadImage.this, "Image deleted: " + file.getName(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(UploadImage.this, "Failed to delete: " + file.getName(), Toast.LENGTH_SHORT).show();
                }
            }
            loadImagesFromInternalStorage();  // Refresh the list
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri selectedImageUri = data.getData();
            String imageType = getContentResolver().getType(selectedImageUri);

            // Disallow JPEG/JPG files
            if ("image/jpeg".equals(imageType) || "image/jpg".equals(imageType)) {
                Toast.makeText(this, "JPEG/JPG uploads are not supported!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show a pop-up to rename the image
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Rename Image");

            final EditText input = new EditText(this);
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                String newName = input.getText().toString();
                if (!newName.isEmpty()) {
                    saveImageToInternalStorage(selectedImageUri, newName, imageType);
                } else {
                    Toast.makeText(UploadImage.this, "Invalid name!", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        }
    }

    private void saveImageToInternalStorage(Uri imageUri, String newName, String imageType) {
        try {
            if ("image/svg+xml".equals(imageType)) {
                saveSvgToInternalStorage(imageUri, newName);
            } else if ("image/png".equals(imageType)) {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                // Remove background from PNG
                Bitmap transparentBitmap = removePngBackground(bitmap);

                File imageFile = new File(imageFolder, newName + ".png");
                if (imageFile.exists()) {
                    Toast.makeText(this, "File with this name already exists.", Toast.LENGTH_SHORT).show();
                    return;
                }

                FileOutputStream fos = new FileOutputStream(imageFile);
                transparentBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
            }

            loadImagesFromInternalStorage();  // Refresh the image list

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving image!", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap removePngBackground(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);

        // Go through the image and set the background (white) to transparent
        for (int x = 0; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                int pixel = original.getPixel(x, y);
                if (pixel == Color.WHITE) {
                    result.setPixel(x, y, Color.TRANSPARENT);
                } else {
                    result.setPixel(x, y, pixel);
                }
            }
        }
        return result;
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

    private void loadImagesFromInternalStorage() {
        File[] imageFiles = imageFolder.listFiles();
        if (imageFiles != null) {
            List<File> fileList = Arrays.asList(imageFiles);

            imageAdapter = new ImageAdapter(this, fileList);
            recyclerView.setAdapter(imageAdapter);

            deleteButton.setEnabled(!fileList.isEmpty());
        }
    }
}
