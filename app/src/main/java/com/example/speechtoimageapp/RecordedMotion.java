package com.example.speechtoimageapp;

import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RecordedMotion implements Serializable {

    // A 2D list containing x and y values for each coordinate, in order of recording.
    List<List<Float>> posList;
    // A list containing the amount of time each point should be shown, in order of recording.
    List<Long> posIncrements;
    // The total time the motion takes
    long duration;
    // Name of motion. Used as identifier by speech recognizer.
    String name;
    // How many rotations the image should take per second
    float rotationsPerSecond;

    public RecordedMotion(List<List<Float>> posList, List<Long> posIncrements, long duration, String name, float rotationsPerSecond) {
        this.name = name;
        this.posList = posList;
        this.posIncrements = posIncrements;
        this.duration = duration;
        this.rotationsPerSecond = rotationsPerSecond;
    }
}
