package moa.classifiers.trees.plastic_util;

import moa.classifiers.core.driftdetection.ADWINChangeDetector;

public class CustomADWINChangeDetector extends ADWINChangeDetector {
    private Double previousEstimation;

    @Override
    public void input(double inputValue) {
        previousEstimation = getEstimation();
        super.input(inputValue);
    }

    public int getWidth() {
        return adwin.getWidth();
    }
}