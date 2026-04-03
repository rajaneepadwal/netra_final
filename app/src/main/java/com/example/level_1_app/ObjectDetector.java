package com.example.level_1_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final Context context;

    private static final int INPUT_SIZE = 320;
    private static final int OUTPUT_ROWS = 84;
    private static final int OUTPUT_COLS = 2100;
    private static final float CONFIDENCE_THRESHOLD = 0.25f;

    public ObjectDetector(Context context) {
        this.context = context;
        setupInterpreter();
        loadLabels();
    }

    private void setupInterpreter() {
        try {
            MappedByteBuffer modelFile = FileUtil.loadMappedFile(context, "yolo11n_float16.tflite");
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(modelFile, options);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLabels() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("labels.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":")) {
                    labels.add(line.split(":")[1].trim());
                } else {
                    labels.add(line.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Detection> detect(Bitmap bitmap) {
        if (interpreter == null) return new ArrayList<>();

        // Image processor scales the bitmap to 320x320 for the model
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 255f))
                .add(new CastOp(DataType.FLOAT32))
                .build();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);
        tensorImage = imageProcessor.process(tensorImage);

        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, OUTPUT_ROWS, OUTPUT_COLS}, DataType.FLOAT32);
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
                float cx = output[0 * OUTPUT_COLS + c];
                float cy = output[1 * OUTPUT_COLS + c];
                float w = output[2 * OUTPUT_COLS + c];
                float h = output[3 * OUTPUT_COLS + c];

                // Scale normalized coordinates to the actual image dimensions
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
        Collections.sort(detections, (a, b) -> Float.compare(b.score, a.score));
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