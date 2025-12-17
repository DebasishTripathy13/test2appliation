package com.example.voskhinditranscriber;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import java.io.File;

/**
 * Helper class for running LLM inference using MediaPipe GenAI (LiteRT).
 */
public class LlmInferenceHelper {

    private static final String TAG = "LlmInferenceHelper";
    
    private LlmInference llmInference;
    private boolean isInitialized = false;

    public interface InitCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface InferenceCallback {
        void onTokenGenerated(String token);
        void onComplete(String response);
        void onError(String error);
    }

    public void initialize(Context context, AIModel aiModel, InitCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Initializing LLM from: " + aiModel.fileName);
                
                File modelFile = new File(context.getFilesDir(), aiModel.fileName);
                if (!modelFile.exists()) {
                    // Try cache dir as fallback
                    modelFile = new File(context.getCacheDir(), aiModel.fileName);
                }

                if (!modelFile.exists()) {
                    callback.onError("Model file not found: " + aiModel.fileName);
                    return;
                }

                LlmInferenceOptions options = LlmInferenceOptions.builder()
                        .setModelPath(modelFile.getAbsolutePath())
                        //.setMaxTokens(1024)
                        .setMaxTopK(40)
                        //.setTemperature(0.8f)
                        //.setRandomSeed(0)
                        .build();

                llmInference = LlmInference.createFromOptions(context, options);
                isInitialized = true;
                callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize LLM", e);
                isInitialized = false;
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void generateAnalysis(String prompt, InferenceCallback callback) {
        if (!isInitialized || llmInference == null) {
            callback.onError("LLM not initialized");
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "Generating response for prompt length: " + prompt.length());
                
                // Use synchronous generation for simplicity and to match the API
                String result = llmInference.generateResponse(prompt);
                callback.onComplete(result);

            } catch (Exception e) {
                Log.e(TAG, "Error generating response", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void cleanup() {
        if (llmInference != null) {
            try {
                if (llmInference instanceof AutoCloseable) {
                    ((AutoCloseable) llmInference).close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing LLM inference", e);
            }
            llmInference = null;
        }
        isInitialized = false;
    }
}
