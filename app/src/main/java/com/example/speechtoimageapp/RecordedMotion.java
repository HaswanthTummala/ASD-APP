package com.example.speechtoimageapp;

import android.util.Log;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RecordedMotion implements Serializable {

    // A 2D list containing x and y values for each coordinate, in order of recording.
    List<List<Float>> posList;
    // A list containing the amount of time each point should be shown, in order of recording.
    List<Long> posIncrements;
    // The total time the motion takes.
    long duration;
    // The date and time at which the motion was recorded.
    LocalDateTime time;
    // Name of motion. Used as identifier by speech recognizer.
    String name;
    // How many rotations the image should take per second.
    float rotationsPerSecond;
    // If rotation was customized, store array of angles instead of rotationsPerSecond float.
    List<Float> angleList;
    // Flag to determine if rotation was customized
    boolean rotFlag;

    public RecordedMotion(List<List<Float>> posList, List<Long> posIncrements, long duration, String name, float rotationsPerSecond) {
        this.name = name;
        this.posList = posList;
        this.posIncrements = posIncrements;
        this.duration = duration;
        this.time = LocalDateTime.now();
        this.rotationsPerSecond = rotationsPerSecond;
        this.angleList = null;
        this.rotFlag = false;
    }

    public RecordedMotion(List<List<Float>> posList, List<Long> posIncrements, long duration, String name, List<Float> angleList) {
        this.name = name;
        this.posList = posList;
        this.posIncrements = posIncrements;
        this.duration = duration;
        this.time = LocalDateTime.now();
        this.rotationsPerSecond = 0;
        this.angleList = angleList;
        this.rotFlag = true;
    }

    public void printMotion() {
        Log.i("MotionDisplay", this.posList.toString());
        Log.i("MotionDisplay", this.posIncrements.toString());
        Log.i("MotionDisplay", String.valueOf(this.duration));
        Log.i("MotionDisplay", this.time.toString());
        Log.i("MotionDisplay", this.name);
        Log.i("MotionDisplay", String.valueOf(this.rotationsPerSecond));
        if (angleList != null) { Log.i("MotionDisplay", this.angleList.toString()); }
        Log.i("MotionDisplay", String.valueOf(this.rotFlag));
    }
}