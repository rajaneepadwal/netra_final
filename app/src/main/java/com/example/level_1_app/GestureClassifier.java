package com.example.level_1_app;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class GestureClassifier {

    // Landmark indices
    private static final int WRIST = 0;

    // Fingers: [MCP, PIP, DIP, TIP]
    private static final int[][] FINGERS = {
            {5, 6, 7, 8},    // Index
            {9, 10, 11, 12},  // Middle
            {13, 14, 15, 16}, // Ring
            {17, 18, 19, 20}  // Pinky
    };

    /**
     * Classifies the hand gesture based on finger states.
     */
    public static String classify(List<NormalizedLandmark> lm) {
        if (lm == null || lm.size() < 21) return "S*";

        boolean thumbExtended = isThumbExtended(lm);
        boolean indexExtended = isFingerExtended(lm, FINGERS[0]);
        boolean middleExtended = isFingerExtended(lm, FINGERS[1]);
        boolean ringExtended = isFingerExtended(lm, FINGERS[2]);
        boolean pinkyExtended = isFingerExtended(lm, FINGERS[3]);

        // --- GESTURE MAPPING ---

        // ☝️ FORWARD: Only index is extended
        if (indexExtended && !middleExtended && !ringExtended && !pinkyExtended) {
            return "F*";
        }

        // ✌️ BACK: Index and Middle are extended
        if (indexExtended && middleExtended && !ringExtended && !pinkyExtended) {
            return "B*";
        }

        // 🖐️ LEFT: All four fingers extended (ignoring thumb to prevent false triggers)
        if (indexExtended && middleExtended && ringExtended && pinkyExtended) {
            return "L*";
        }

        // 🤙 RIGHT: Only pinky is extended
        if (pinkyExtended && !indexExtended && !middleExtended && !ringExtended) {
            return "R*";
        }

        // ✊ STOP: Default if no patterns match or specifically closed fist
        return "S*";
    }

    /**
     * Checks if a regular finger (index to pinky) is extended.
     * Uses the angle between MCP-PIP-DIP and PIP-DIP-TIP.
     */
    private static boolean isFingerExtended(List<NormalizedLandmark> lm, int[] indices) {
        float angle1 = calculateAngle(lm.get(indices[0]), lm.get(indices[1]), lm.get(indices[2]));
        float angle2 = calculateAngle(lm.get(indices[1]), lm.get(indices[2]), lm.get(indices[3]));
        float averageAngle = (angle1 + angle2) / 2f;

        // Extended fingers usually have an angle > 160 degrees.
        // Curled fingers have angles < 100 degrees.
        return averageAngle > 145f;
    }

    /**
     * Thumb classification is slightly different as it doesn't curl the same way.
     * We check the angle at the MCP joint.
     */
    private static boolean isThumbExtended(List<NormalizedLandmark> lm) {
        float angle = calculateAngle(lm.get(0), lm.get(2), lm.get(4));
        return angle > 150f;
    }

    /**
     * Calculates the angle ABC in degrees using 3D coordinates.
     */
    private static float calculateAngle(NormalizedLandmark a, NormalizedLandmark b, NormalizedLandmark c) {
        float[] vectorAB = {a.x() - b.x(), a.y() - b.y(), a.z() - b.z()};
        float[] vectorCB = {c.x() - b.x(), c.y() - b.y(), c.z() - b.z()};

        float dotProduct = vectorAB[0] * vectorCB[0] + vectorAB[1] * vectorCB[1] + vectorAB[2] * vectorCB[2];
        float magnitudeAB = (float) Math.sqrt(vectorAB[0] * vectorAB[0] + vectorAB[1] * vectorAB[1] + vectorAB[2] * vectorAB[2]);
        float magnitudeCB = (float) Math.sqrt(vectorCB[0] * vectorCB[0] + vectorCB[1] * vectorCB[1] + vectorCB[2] * vectorCB[2]);

        if (magnitudeAB == 0 || magnitudeCB == 0) return 0f;

        float cosAngle = dotProduct / (magnitudeAB * magnitudeCB);
        // Clamp for numerical stability
        cosAngle = Math.max(-1f, Math.min(1f, cosAngle));

        return (float) Math.toDegrees(Math.acos(cosAngle));
    }
}