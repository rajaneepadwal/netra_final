package com.example.level_1_app;

import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ColorPicker {

    private static final String TAG = "ColorPicker";

    // HSV bounds
    private Scalar lowerBound1, upperBound1;
    private Scalar lowerBound2, upperBound2;

    private boolean colorPicked = false;

    private boolean isRedColor = false;
    private boolean isBlackColor = false;

    private LightingMode lightingMode = LightingMode.GOOD_LIGHTING;

    public enum LightingMode {
        BAD_LIGHTING,
        GOOD_LIGHTING
    }

    public void setLightingMode(LightingMode mode) {
        this.lightingMode = mode;
    }

    public boolean pickColor(Mat hsvFrame, int x, int y) {
        if (hsvFrame == null || hsvFrame.empty()) return false;
        if (x < 0 || y < 0 || x >= hsvFrame.cols() || y >= hsvFrame.rows()) return false;

        double[] hsv = hsvFrame.get(y, x);
        if (hsv == null || hsv.length < 3) return false;

        double h = hsv[0];
        double s = hsv[1];
        double v = hsv[2];

        isRedColor = false;
        isBlackColor = false;

        // Tolerance based on lighting
        int hTol, sTol, vTol;
        if (lightingMode == LightingMode.BAD_LIGHTING) {
            hTol = 4;
            sTol = 45;
            vTol = 45;
        } else {
            hTol = 6;
            sTol = 60;
            vTol = 60;
        }

        // 1. Check if it's red
        // Use fixed red ranges: [0, lowRedMax] and [highRedMin, 179]
        int lowRedMax = 10;
        int highRedMin = 170;

        boolean redHue = (h <= lowRedMax) || (h >= highRedMin);
        boolean redSatVal = (s > 40 && v > 40);  // avoid very dark/gray as red

        if (redHue && redSatVal) {
            isRedColor = true;

            // Clamp S and V to avoid including too much gray/black
            int sMin = Math.max((int) s - sTol, 40);
            int vMin = Math.max((int) v - vTol, 40);
            int sMax = Math.min((int) s + sTol, 255);
            int vMax = Math.min((int) v + vTol, 255);

            // Low red range: [0, lowRedMax] + adjusted S,V
            lowerBound1 = new Scalar(0, sMin, vMin);
            upperBound1 = new Scalar(lowRedMax, sMax, vMax);

            // High red range: [highRedMin, 179] + same S,V
            lowerBound2 = new Scalar(highRedMin, sMin, vMin);
            upperBound2 = new Scalar(179, sMax, vMax);

            colorPicked = true;
            Log.d(TAG, "Red color selected");
            return true;
        }

        // 2. Check if it's black (low V and low S)
        // Black: very dark and desaturated
        if (v < 60 && s < 80) {
            isBlackColor = true;

            // Black mask: full hue, any saturation, low value
            lowerBound1 = new Scalar(0, 0, 0);
            upperBound1 = new Scalar(179, 255, 60);  // adjust V upper as needed

            lowerBound2 = null;
            upperBound2 = null;

            colorPicked = true;
            Log.d(TAG, "Black color selected");
            return true;
        }

        // 3. Normal color (not red, not black)
        // Use centered range around picked hue
        int hMin = (int) Math.max(h - hTol, 0);
        int hMax = (int) Math.min(h + hTol, 179);

        // Avoid very low S/V to prevent including gray/black
        int sMin = Math.max((int) s - sTol, 40);
        int vMin = Math.max((int) v - vTol, 40);
        int sMax = Math.min((int) s + sTol, 255);
        int vMax = Math.min((int) v + vTol, 255);

        lowerBound1 = new Scalar(hMin, sMin, vMin);
        upperBound1 = new Scalar(hMax, sMax, vMax);

        lowerBound2 = null;
        upperBound2 = null;

        colorPicked = true;

        Log.d(TAG, "Picked HSV: " + Arrays.toString(hsv));
        Log.d(TAG, "Red: " + isRedColor + " | Black: " + isBlackColor);

        return true;
    }

    public List<Scalar[]> getColorRanges() {
        List<Scalar[]> ranges = new ArrayList<>();

        if (!colorPicked) return ranges;

        ranges.add(new Scalar[]{lowerBound1, upperBound1});
        if (isRedColor && lowerBound2 != null && upperBound2 != null) {
            ranges.add(new Scalar[]{lowerBound2, upperBound2});
        }

        return ranges;
    }

    public void reset() {
        lowerBound1 = null;
        upperBound1 = null;
        lowerBound2 = null;
        upperBound2 = null;
        colorPicked = false;
        isRedColor = false;
        isBlackColor = false;

        Log.d(TAG, "ColorPicker reset");
    }

    public boolean isColorPicked() {
        return colorPicked;
    }

    public boolean isBlackColor() {
        return isBlackColor;
    }

}