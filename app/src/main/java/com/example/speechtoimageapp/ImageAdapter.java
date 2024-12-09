package com.example.speechtoimageapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.caverock.androidsvg.SVG;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

    private Context context;
    private List<File> imageFiles;
    private MediaPlayer mediaPlayer; // MediaPlayer for audio playback
    private int selectedPosition = -1; // Track the single selected position

    public ImageAdapter(Context context, List<File> imageFiles) {
        this.context = context;
        this.imageFiles = imageFiles;
        this.mediaPlayer = new MediaPlayer(); // Initialize MediaPlayer
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflate the layout for each image item
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.image_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        File imageFile = imageFiles.get(position);

        // Load image based on the file extension (PNG or SVG)
        if (imageFile.getName().endsWith(".png")) {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            holder.imageView.setImageBitmap(bitmap);
        } else if (imageFile.getName().endsWith(".svg")) {
            try {
                FileInputStream inputStream = new FileInputStream(imageFile);
                SVG svg = SVG.getFromInputStream(inputStream);

                // Render the SVG
                int width = holder.imageView.getWidth() > 0 ? holder.imageView.getWidth() : 200;
                int height = holder.imageView.getHeight() > 0 ? holder.imageView.getHeight() : 200;

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                svg.setDocumentWidth(width);
                svg.setDocumentHeight(height);
                svg.renderToCanvas(canvas);

                holder.imageView.setImageBitmap(bitmap);
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("SVG", "Error rendering SVG: " + e.getMessage());
            }
        }

        // Get the base name for associated audio file
        String baseName = imageFile.getName().substring(0, imageFile.getName().lastIndexOf('.'));
        File audioFile = new File(imageFile.getParent(), baseName + ".mp3");

        // Fetch tags from SharedPreferences
        SharedPreferences sharedPreferences = context.getSharedPreferences("ImageTagsPrefs", Context.MODE_PRIVATE);
        String tags = sharedPreferences.getString(baseName, "No tags available");
        holder.tagsTextView.setText(tags);

        // Show play button if audio file exists
        if (audioFile.exists()) {
            holder.playButton.setVisibility(View.VISIBLE);
            holder.playButton.setOnClickListener(v -> playAudio(audioFile));
        } else {
            holder.playButton.setVisibility(View.GONE);
        }

        // Highlight the selected item
        boolean isSelected = selectedPosition == holder.getAdapterPosition();
        holder.tickMark.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.itemView.setBackgroundColor(isSelected ? 0xFFE0E0E0 : 0xFFFFFFFF); // Highlight or un-highlight

        // Handle click to toggle selection
        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition(); // Use getAdapterPosition to get the current position

            if (previousPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousPosition); // Deselect the previous item
            }
            notifyItemChanged(selectedPosition); // Highlight the current item
        });

        // Show image name on long click
        holder.itemView.setOnLongClickListener(v -> {
            Toast.makeText(context, "Image name: " + imageFile.getName(), Toast.LENGTH_SHORT).show();
            return true; // Indicate the long-click event is handled
        });
    }

    private void playAudio(File audioFile) {
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset(); // Reset the MediaPlayer to its uninitialized state

            // Check if the audio file exists
            if (!audioFile.exists()) {
                Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show();
                return;
            }

            mediaPlayer.setDataSource(audioFile.getPath());
            mediaPlayer.prepareAsync(); // Prepare asynchronously

            // Start playback once the MediaPlayer is ready
            mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Toast.makeText(context, "MediaPlayer is not in a valid state", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return imageFiles.size();
    }

    public File getSelectedFile() {
        if (selectedPosition != RecyclerView.NO_POSITION && selectedPosition < imageFiles.size()) {
            return imageFiles.get(selectedPosition);
        }
        return null; // No valid selection
    }


    // Release MediaPlayer resources when adapter is no longer needed
    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ViewHolder class to hold references to the ImageView, play button, and tick mark
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public ImageView playButton;
        public ImageView tickMark;
        public TextView tagsTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view); // Image view for displaying the image
            playButton = itemView.findViewById(R.id.play_button); // Play button for audio playback
            tickMark = itemView.findViewById(R.id.tick_mark); // Tick mark for selection
            tagsTextView = itemView.findViewById(R.id.tags_text_view); // TextView for displaying tags
        }
    }
}
