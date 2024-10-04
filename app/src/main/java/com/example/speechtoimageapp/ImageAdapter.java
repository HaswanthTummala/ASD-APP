package com.example.speechtoimageapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import com.caverock.androidsvg.SVG;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

    private Context context;
    private List<File> imageFiles;
    private Set<File> selectedFiles = new HashSet<>();  // Track selected files

    public ImageAdapter(Context context, List<File> imageFiles) {
        this.context = context;
        this.imageFiles = imageFiles;
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
            // Load PNG image
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            holder.imageView.setImageBitmap(bitmap);
        } else if (imageFile.getName().endsWith(".svg")) {
            // Load and render SVG image
            try {
                FileInputStream inputStream = new FileInputStream(imageFile);
                SVG svg = SVG.getFromInputStream(inputStream);

                // Get or set dimensions of ImageView for rendering the SVG
                int width = holder.imageView.getWidth();
                int height = holder.imageView.getHeight();

                if (width == 0 || height == 0) {
                    width = 200;  // Default size if dimensions are not set yet
                    height = 200;
                }

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

        // Handle the selection logic for displaying/hiding the tick mark
        if (selectedFiles.contains(imageFile)) {
            holder.tickMark.setVisibility(View.VISIBLE);  // Show tick mark for selected items
        } else {
            holder.tickMark.setVisibility(View.GONE);  // Hide tick mark for unselected items
        }

        // Handle item click for selecting/deselecting images
        holder.itemView.setOnClickListener(v -> {
            if (selectedFiles.contains(imageFile)) {
                selectedFiles.remove(imageFile);  // Deselect the image
                holder.tickMark.setVisibility(View.GONE);  // Hide tick mark
            } else {
                selectedFiles.add(imageFile);  // Select the image
                holder.tickMark.setVisibility(View.VISIBLE);  // Show tick mark
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageFiles.size();
    }

    // Return the selected files for any action like deletion
    public List<File> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }

    // ViewHolder class to hold references to the ImageView and tick mark
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public ImageView tickMark;

        public ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);  // Image view for displaying the image
            tickMark = itemView.findViewById(R.id.tick_mark);  // Tick mark for selection
        }
    }
}
