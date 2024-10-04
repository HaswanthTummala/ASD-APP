package com.example.speechtoimageapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder> {

    private HashMap<String, ArrayList<RecordedMotion>> motions;
    private int clickedPosition = -1;

    private OnAdapterItemClickListener adapterItemClickListener;

    public RecyclerViewAdapter(HashMap<String, ArrayList<RecordedMotion>> h, OnAdapterItemClickListener listener) {
        motions = h;
        this.adapterItemClickListener = listener;
    }

    public class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView motionName;

        public MyViewHolder(final View view) {
            super(view);
            motionName = view.findViewById(R.id.motionName);
        }

        @Override
        public void onClick(View v) {
            adapterItemClickListener.onAdapterItemClickListener(getBindingAdapterPosition());
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
        int i = 0;
        for (Map.Entry<String, ArrayList<RecordedMotion>> entry : motions.entrySet()) {
            if (position == i) {
                String key = entry.getKey();
                holder.motionName.setText(key);
                break;
            } else { i++; }
        }
        // Allow user to delete entries as needed
        holder.motionName.setOnClickListener(v -> {
            clickedPosition = holder.getBindingAdapterPosition();
            removeAt(clickedPosition);
        });
    }

    @Override
    public int getItemCount() {
        return motions.size();
    }

    public void removeAt(int position) {
        int i = 0;
        for (Map.Entry<String, ArrayList<RecordedMotion>> entry : motions.entrySet()) {
            if (position == i) {
                String key = entry.getKey();
                motions.remove(key);
                break;
            } else { i++; }
        }
        // Notify motion Activity that entry has been deleted.
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, motions.size());
    }

}
