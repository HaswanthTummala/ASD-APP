package com.example.speechtoimageapp;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder> {

    private HashMap<String, ArrayList<RecordedMotion>> motions;

    private OnAdapterItemClickListener adapterItemClickListener;

    public RecyclerViewAdapter(HashMap<String, ArrayList<RecordedMotion>> h, OnAdapterItemClickListener listener) {
        motions = h;
        this.adapterItemClickListener = listener;
    }

    public class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final TextView motionName;
        private final TextView motionDate;
        private final ImageView motionEdit;
        private final ImageView motionPlay;
        private final ImageView motionDelete;

        public MyViewHolder(final View view) {
            super(view);
            motionName = view.findViewById(R.id.motionName);
            motionDate = view.findViewById(R.id.motionDate);
            motionEdit = view.findViewById(R.id.motionEdit);
            motionPlay = view.findViewById(R.id.motionPlay);
            motionDelete = view.findViewById(R.id.motionDelete);
        }

        @Override
        public void onClick(View v) {
            adapterItemClickListener.onAdapterItemClickListener(getBindingAdapterPosition(), (String) v.getTag());
        }
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_row, parent, false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        // HashMaps cannot be consistently traversed via index positioning. Instead, extract and traverse the entry set's keys.
        // First, extract and sort keys into array.
        ArrayList<String> keyArray = new ArrayList<>(motions.keySet());
        Collections.sort(keyArray);

        // Next, extract map entries in sorted order
        ArrayList<RecordedMotion> sortedMotionList = new ArrayList<>();
        for (String key : keyArray) { sortedMotionList.addAll(motions.get(key)); }

        // Finally, traverse ordered array of entries
        for (int i = 0; i < sortedMotionList.size(); i++) {
            if (position == i) {
                holder.motionName.setText(sortedMotionList.get(i).name);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                holder.motionDate.setText("Created: " + sortedMotionList.get(i).time.format(formatter));
                break;
            }
        }

        // Allow user to delete entries as needed
        holder.motionDelete.setOnClickListener(v -> {
            AlertDialog alertDialog = new AlertDialog.Builder(v.getContext()).create();
            alertDialog.setTitle("Delete Motion");
            alertDialog.setMessage("Are you sure?");
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes", (dialogInterface, i) -> {
                adapterItemClickListener.onAdapterItemClickListener(position, "delete");

                // Notify motion Activity that entry has been deleted.
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, getItemCount());

                alertDialog.dismiss();
            });
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No", ((dialogInterface, i) -> alertDialog.dismiss()));
            alertDialog.show();
        });

        holder.motionEdit.setOnClickListener(v -> adapterItemClickListener.onAdapterItemClickListener(position, "edit"));

        holder.motionPlay.setOnClickListener(v -> adapterItemClickListener.onAdapterItemClickListener(position, "play"));
    }

    @Override
    public int getItemCount() {
        if (motions == null) {
            return 0;
        } else {
            // First, extract and sort keys into array.
            ArrayList<String> keyArray = new ArrayList<>(motions.keySet());
            Collections.sort(keyArray);

            // Next, extract map entries in sorted order
            ArrayList<RecordedMotion> sortedMotionList = new ArrayList<>();
            for (String key : keyArray) { sortedMotionList.addAll(motions.get(key)); }

            return sortedMotionList.size();
        }
    }

    public void removeAt(int position) {
        // Extract and sort keys into array.
        ArrayList<String> keyArray = new ArrayList<>(motions.keySet());
        Collections.sort(keyArray);

        // Extract map entries in sorted order
        ArrayList<RecordedMotion> sortedMotionList = new ArrayList<>();
        for (String key : keyArray) { sortedMotionList.addAll(motions.get(key)); }

        RecordedMotion selectedMotion = sortedMotionList.get(position);

        // Find and remove selectedMotion from motionList.
        ArrayList<RecordedMotion> tList = motions.get(selectedMotion.name);
        for (int i = 0; i < tList.size(); i++) {
            if (tList.get(i).time == selectedMotion.time) {
                tList.remove(i);
                break;
            }
        }
        motions.replace(selectedMotion.name, tList);
    }
}
