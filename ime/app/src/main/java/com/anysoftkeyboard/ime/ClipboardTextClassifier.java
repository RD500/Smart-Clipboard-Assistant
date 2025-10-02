package com.anysoftkeyboard.ime;



import android.content.Context;

import android.os.Handler;

import android.os.Looper;

import android.util.Log;

import android.util.Pair;



// Import TensorFlow Lite Task Library classes

import org.tensorflow.lite.task.text.nlclassifier.BertNLClassifier;

import org.tensorflow.lite.support.label.Category; // Note: This is from TFLite, not MediaPipe



import java.io.IOException; // For BertNLClassifier.createFromFile

import java.util.ArrayList;

import java.util.List;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;



public class ClipboardTextClassifier {

    private static final String TAG = "ASK_TextClassifier_TFLite"; // Changed TAG for clarity

    private final String modelName;

    private final Context context;

    private final ClassificationListener listener;



    // Change this to the TFLite Task Library's BertNLClassifier

    private BertNLClassifier bertClassifier;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();



    // Listener interface remains the same

    public interface ClassificationListener {

        void onClassificationResult(List<Pair<String, Float>> results);

    }



    public ClipboardTextClassifier(Context context, String modelName, ClassificationListener listener) {

        this.context = context;

        this.modelName = modelName; // This should be the name of your .tflite file in assets

        this.listener = listener;

        setupClassifier();

    }



    private void setupClassifier() {

        try {

            // Close existing classifier before creating a new one, if any

            if (bertClassifier != null) {

                bertClassifier.close();

            }

            // Create BertNLClassifier from the model file in assets

            bertClassifier = BertNLClassifier.createFromFile(context, modelName);

            Log.i(TAG, "BertNLClassifier setup successfully.");

        } catch (IOException e) { // createFromFile throws IOException

            Log.e(TAG, "Error setting up BertNLClassifier: " + e.getMessage(), e);

            bertClassifier = null;

        } catch (Exception e) { // Catch broader exceptions just in case

            Log.e(TAG, "Unexpected error setting up BertNLClassifier: " + e.getMessage(), e);

            bertClassifier = null;

        }

    }


    // In ClipboardTextClassifier.java
    public void classify(String text) {
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            Log.w(TAG, "Background executor is not running. Cannot classify.");
            postEmptyResults();
            return;
        }

        backgroundExecutor.execute(() -> {
            // Add a log to show the background task has started
            Log.d(TAG, "Starting classification in background for text: '" + text + "'");

            if (bertClassifier == null) {
                Log.e(TAG, "BertNLClassifier is null. Cannot classify.");
                postEmptyResults();
                return;
            }

            List<Pair<String, Float>> classificationResultsList = new ArrayList<>();
            try {
                // Perform classification using BertNLClassifier
                List<Category> tfliteResults = bertClassifier.classify(text);

                // --- CRITICAL LOGGING ---
                if (tfliteResults != null && !tfliteResults.isEmpty()) {
                    Log.i(TAG, "Classification successful! Got " + tfliteResults.size() + " results.");
                    StringBuilder resultsLog = new StringBuilder("Model results:\n");

                    for (Category category : tfliteResults) {
                        String name = category.getLabel();
                        float scr = category.getScore();
                        // Log each result individually to see the scores
                        resultsLog.append(String.format("  - Label: %s, Score: %.4f\n", name, scr));
                        classificationResultsList.add(new Pair<>(name, scr));
                    }
                    Log.d(TAG, resultsLog.toString());
                } else {
                    // This is likely what's happening
                    Log.w(TAG, "Classification returned null or empty results from the model.");
                }
                // --- END OF CRITICAL LOGGING ---

            } catch (Exception e) {
                Log.e(TAG, "Error during text classification: " + e.getMessage(), e);
                // classificationResultsList will be empty, which is correct for an error state
            }

            // Post the results back to the main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                Log.d(TAG, "Posting " + classificationResultsList.size() + " results back to the listener.");
                if (listener != null) {
                    listener.onClassificationResult(classificationResultsList);
                }
            });
        });
    }




    private void postEmptyResults() {

        new Handler(Looper.getMainLooper()).post(() -> {

            if (listener != null) {

                listener.onClassificationResult(new ArrayList<>());

            }

        });

    }



    public void close() {

        Log.d(TAG, "Closing ClipboardTextClassifier.");

        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {

            backgroundExecutor.shutdown();

            // Optional: Await termination if necessary, but shutdown() is often enough.

        }

        if (bertClassifier != null) {

            try {

                bertClassifier.close(); // Release TFLite resources

                Log.i(TAG, "BertNLClassifier closed successfully.");

            } catch (Exception e) { // BertNLClassifier.close() doesn't declare throwing checked exceptions

                Log.e(TAG, "Error closing BertNLClassifier: " + e.getMessage(), e);

            }

            bertClassifier = null;

        }

    }

}