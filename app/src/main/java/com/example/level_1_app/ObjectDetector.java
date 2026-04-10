package com.example.level_1_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate; //
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.util.*;

public class ObjectDetector {

    public static class Detection {
        public final RectF box;
        public final String label;
        public final float score;

        public Detection(RectF box, String label, float score) {
            this.box = box;
            this.label = label;
            this.score = score;
        }
    }

    private Interpreter interpreter;
    private final List<String> labels = new ArrayList<>();
    private static final int INPUT_SIZE = 320;
    private static final int OUTPUT_ROWS = 84;
    private static final int OUTPUT_COLS = 2100;
    private static final float CONFIDENCE_THRESHOLD = 0.40f;

    private final ImageProcessor imageProcessor;

    public ObjectDetector(Context context) {
        try {
            MappedByteBuffer modelFile = FileUtil.loadMappedFile(context, "yolo11n_float16.tflite");
            Interpreter.Options options = new Interpreter.Options();

            // 🔥 GPU ACCELERATION
            try {
                options.addDelegate(new GpuDelegate()); //
                Log.d("Detector", "GPU Acceleration Enabled");
            } catch (Exception e) {
                options.setNumThreads(4);
                Log.e("Detector", "GPU Failed, falling back to CPU");
            }

            interpreter = new Interpreter(modelFile, options);
        } catch (IOException e) {
            Log.e("Detector", "Model Load Failed", e);
        }

        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 255f))
                .add(new CastOp(DataType.FLOAT32))
                .build();

        loadLabels(context);
    }

    private void loadLabels(Context context) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("labels.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line.contains(":") ? line.split(":")[1].trim() : line.trim());
            }
        } catch (IOException e) {
            Log.e("Detector", "Labels Load Failed", e);
        }
    }

    public List<Detection> detect(Bitmap bitmap) {
        if (interpreter == null) return new ArrayList<>();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);
        tensorImage = imageProcessor.process(tensorImage);

        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(
                new int[]{1, OUTPUT_ROWS, OUTPUT_COLS},
                DataType.FLOAT32
        );

        interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer().rewind());
        return parseOutput(outputBuffer.getFloatArray(), bitmap.getWidth(), bitmap.getHeight());
    }

    private List<Detection> parseOutput(float[] output, int imgWidth, int imgHeight) {
        ArrayList<Detection> detections = new ArrayList<>();
        for (int c = 0; c < OUTPUT_COLS; c++) {
            float maxScore = 0f;
            int maxClass = -1;

            for (int r = 4; r < OUTPUT_ROWS; r++) {
                float score = output[r * OUTPUT_COLS + c];
                if (score > maxScore) {
                    maxScore = score;
                    maxClass = r - 4;
                }
            }

            if (maxScore > CONFIDENCE_THRESHOLD) {
                float cx = output[c];
                float cy = output[OUTPUT_COLS + c];
                float w = output[2 * OUTPUT_COLS + c];
                float h = output[3 * OUTPUT_COLS + c];

                float left = (cx - w / 2f) * imgWidth;
                float top = (cy - h / 2f) * imgHeight;
                float right = (cx + w / 2f) * imgWidth;
                float bottom = (cy + h / 2f) * imgHeight;

                String label = (maxClass >= 0 && maxClass < labels.size()) ? labels.get(maxClass) : "Unknown";
                detections.add(new Detection(new RectF(left, top, right, bottom), label, maxScore));
            }
        }
        return applyNMS(detections);
    }

    private List<Detection> applyNMS(List<Detection> detections) {
        detections.sort((a, b) -> Float.compare(b.score, a.score));
        List<Detection> results = new ArrayList<>();
        while (!detections.isEmpty()) {
            Detection best = detections.remove(0);
            results.add(best);
            detections.removeIf(next -> calculateIoU(best.box, next.box) > 0.45f);
        }
        return results;
    }

    private float calculateIoU(RectF a, RectF b) {
        float iLeft = Math.max(a.left, b.left);
        float iTop = Math.max(a.top, b.top);
        float iRight = Math.min(a.right, b.right);
        float iBottom = Math.min(a.bottom, b.bottom);
        if (iRight < iLeft || iBottom < iTop) return 0f;
        float areaI = (iRight - iLeft) * (iBottom - iTop);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        return areaI / (areaA + areaB - areaI);
    }
}