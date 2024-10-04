package com.example.speechtoimageapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RecordedMotion implements Serializable {

    // A 2D list containing x and y values for each coordinate, in order of recording.
    List<List<Float>> posList;
    // Used to determine how long an image is to be displayed at each coordinate.
    long increment;
    // Name of motion. Used as identifier by speech recognizer.
    String name;
    // How many degrees the image should rotate per second.
    float degreesPerIncrement;

    public RecordedMotion(List<List<Float>> posList, long increment, String name, float rotationsPerSecond, boolean stationaryFlag) {
        this.name = name;

        // RecordedMotions made with coordinates caught primarily via MotionEvent.ACTION_DOWN have far fewer entries in posList.
        // This results in rotations being choppy. Solve by making posList larger.
        if (stationaryFlag) {
            List<List<Float>> newPosList = new ArrayList<>();
            for (int i = 0; i < posList.size(); i++) {
                for (int j = 0; j < 8; j++) {
                    newPosList.add(posList.get(i));
                }
            }
            this.posList = newPosList;
            this.increment = increment / 8;
            this.degreesPerIncrement = (360 * rotationsPerSecond * increment) / 8000;
        } else {
            this.posList = posList;
            this.increment = increment;
            this.degreesPerIncrement = (360 * rotationsPerSecond * increment) / 1000;
        }
    }
}
