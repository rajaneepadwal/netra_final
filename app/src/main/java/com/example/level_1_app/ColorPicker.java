package com.example.level_1_app;

import android.util.Log;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ColorPicker {

    private static final String TAG = "ColorPicker";

    private Scalar lowerBound1, upperBound1;
    private Scalar lowerBound2, upperBound2;

    private boolean colorPicked  = false;
    private boolean isRedColor   = false;
    private boolean isBlackColor = false;
    private boolean isWhiteColor = false;

    private LightingMode lightingMode = LightingMode.GOOD_LIGHTING;

    public enum LightingMode { BAD_LIGHTING, GOOD_LIGHTING }

    public void setLightingMode(LightingMode mode) { this.lightingMode = mode; }

    public boolean pickColor(Mat hsvFrame, int x, int y) {
        if (hsvFrame == null || hsvFrame.empty()) return false;
        if (x < 0 || y < 0 || x >= hsvFrame.cols() || y >= hsvFrame.rows()) return false;

        double[] hsv = hsvFrame.get(y, x);
        if (hsv == null || hsv.length < 3) return false;

        double h = hsv[0];
        double s = hsv[1];
        double v = hsv[2];

        isRedColor   = false;
        isBlackColor = false;
        isWhiteColor = false;
        lowerBound2  = null;
        upperBound2  = null;

        // Tolerances
        int hTol, sTol, vTol;
        if (lightingMode == LightingMode.BAD_LIGHTING) {
            hTol = 10; sTol = 60; vTol = 60;
        } else {
            hTol = 8;  sTol = 50; vTol = 50;
        }

        // ── 1. BLACK: low V, any S ─────────────────────────────────────────
        // Must check BEFORE white because black has low V AND low S
        if (v < 55) {
            isBlackColor = true;
            int vUpper = (lightingMode == LightingMode.BAD_LIGHTING) ? 80 : 60;
            lowerBound1 = new Scalar(0,   0,   0);
            upperBound1 = new Scalar(179, 255, vUpper);
            colorPicked = true;
            Log.d(TAG, "BLACK selected — V=" + v);
            return true;
        }

        // ── 2. WHITE: low S + high V ───────────────────────────────────────
        // S is near 0, V is high — H is meaningless noise so ignore it
        if (s < 50 && v > 150) {
            isWhiteColor = true;
            int sUpper = (lightingMode == LightingMode.BAD_LIGHTING) ? 80 : 60;
            int vLower = (lightingMode == LightingMode.BAD_LIGHTING) ? 130 : 150;
            lowerBound1 = new Scalar(0,   0,      vLower);
            upperBound1 = new Scalar(179, sUpper, 255);
            colorPicked = true;
            Log.d(TAG, "WHITE selected — S=" + s + " V=" + v);
            return true;
        }

        // ── 3. RED / PINK: hue wraps around 0°/180° ────────────────────────
        boolean redHue  = (h <= 15) || (h >= 165);
        boolean pinkHue = redHue;                  // same hue zone, just lower S
        // Pink has lower S — accept s > 20 to catch desaturated pinks
        boolean redSV   = (s > 20 && v > 40);

        if (pinkHue && redSV) {
            isRedColor = true;

            // S/V range — floor at 15 so pale pink isn't cut off
            int sMin = Math.max((int) s - sTol, 15);
            int vMin = Math.max((int) v - vTol, 40);
            int sMax = Math.min((int) s + sTol, 255);
            int vMax = Math.min((int) v + vTol, 255);

            // Low red range [0–15]
            lowerBound1 = new Scalar(0,   sMin, vMin);
            upperBound1 = new Scalar(15,  sMax, vMax);

            // High red range [165–179]
            lowerBound2 = new Scalar(165, sMin, vMin);
            upperBound2 = new Scalar(179, sMax, vMax);

            colorPicked = true;
            Log.d(TAG, "RED/PINK selected — H=" + h + " S=" + s + " V=" + v);
            return true;
        }

        // ── 4. NORMAL COLOR ────────────────────────────────────────────────
        int hMin = (int) Math.max(h - hTol, 0);
        int hMax = (int) Math.min(h + hTol, 179);

        // For normal colors S should be decent — floor at 30 not 40
        int sMin = Math.max((int) s - sTol, 30);
        int vMin = Math.max((int) v - vTol, 30);
        int sMax = Math.min((int) s + sTol, 255);
        int vMax = Math.min((int) v + vTol, 255);

        lowerBound1 = new Scalar(hMin, sMin, vMin);
        upperBound1 = new Scalar(hMax, sMax, vMax);

        colorPicked = true;
        Log.d(TAG, "NORMAL color — H=" + h + " S=" + s + " V=" + v);
        Log.d(TAG, "Range → Lower:" + lowerBound1 + " Upper:" + upperBound1);
        return true;
    }

    public List<Scalar[]> getColorRanges() {
        List<Scalar[]> ranges = new ArrayList<>();
        if (!colorPicked) return ranges;

        ranges.add(new Scalar[]{lowerBound1, upperBound1});

        // Second range only for red (hue wrap)
        if (isRedColor && lowerBound2 != null) {
            ranges.add(new Scalar[]{lowerBound2, upperBound2});
        }
        return ranges;
    }

    public void reset() {
        lowerBound1 = upperBound1 = null;
        lowerBound2 = upperBound2 = null;
        colorPicked = isRedColor = isBlackColor = isWhiteColor = false;
        Log.d(TAG, "ColorPicker reset");
    }

    public boolean isColorPicked()  { return colorPicked;  }
    public boolean isBlackColor()   { return isBlackColor; }
    public boolean isWhiteColor()   { return isWhiteColor; }
}