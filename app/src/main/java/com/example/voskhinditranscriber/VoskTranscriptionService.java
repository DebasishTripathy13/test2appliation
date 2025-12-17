package com.example.voskhinditranscriber;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Vosk-based Hindi speech recognition service.
 * Fine-tuned for accurate Hindi transcription.
 */
public class VoskTranscriptionService implements RecognitionListener {

    private static final String TAG = "VoskTranscription";
    private static final float SAMPLE_RATE = 16000.0f;
    private static final String MODEL_PATH = "model-hi";

    private Context context;
    private Model model;
    private SpeechService speechService;
    private boolean isModelReady = false;
    private boolean isListening = false;

    private TranscriptionListener listener;
    private StringBuilder currentTranscription = new StringBuilder();

    public interface TranscriptionListener {
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String error);
        void onRecordingComplete(String transcription);
        void onModelReady();
    }

    public VoskTranscriptionService(Context context) {
        this.context = context;
        initModel();
    }

    private void initModel() {
        // Initialize Vosk model from assets
        StorageService.unpack(context, MODEL_PATH, MODEL_PATH,
            (model) -> {
                this.model = model;
                this.isModelReady = true;
                Log.d(TAG, "Vosk Hindi model loaded successfully");
                if (listener != null) {
                    listener.onModelReady();
                }
            },
            (exception) -> {
                Log.e(TAG, "Failed to load Vosk model", exception);
                // Try fallback loading from assets directly
                loadModelFromAssets();
            }
        );
    }

    /**
     * Fallback method to load model directly from assets.
     */
    private void loadModelFromAssets() {
        new Thread(() -> {
            try {
                AssetManager assetManager = context.getAssets();
                String[] assets = assetManager.list(MODEL_PATH);

                if (assets == null || assets.length == 0) {
                    String errorMsg = "Hindi model not found in assets.\n" +
                            "Please download from: https://alphacephei.com/vosk/models\n" +
                            "Look for: vosk-model-small-hi-0.22";
                    Log.e(TAG, errorMsg);
                    if (listener != null) {
                        listener.onError(errorMsg);
                    }
                    return;
                }

                // Copy model to internal storage
                File modelDir = new File(context.getFilesDir(), MODEL_PATH);
                if (!modelDir.exists()) {
                    copyAssetFolder(assetManager, MODEL_PATH, modelDir.getAbsolutePath());
                }

                // Load the model
                model = new Model(modelDir.getAbsolutePath());
                isModelReady = true;
                Log.d(TAG, "Vosk Hindi model loaded from assets");

                if (listener != null) {
                    listener.onModelReady();
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading model from assets", e);
                if (listener != null) {
                    listener.onError("Failed to load Hindi model: " + e.getMessage());
                }
            }
        }).start();
    }

    private void copyAssetFolder(AssetManager assetManager, String srcPath, String dstPath) throws IOException {
        String[] assets = assetManager.list(srcPath);

        if (assets == null || assets.length == 0) {
            // It's a file
            copyAssetFile(assetManager, srcPath, dstPath);
        } else {
            // It's a folder
            File dir = new File(dstPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            for (String asset : assets) {
                copyAssetFolder(assetManager, srcPath + "/" + asset, dstPath + "/" + asset);
            }
        }
    }

    private void copyAssetFile(AssetManager assetManager, String srcPath, String dstPath) throws IOException {
        try (InputStream in = assetManager.open(srcPath);
             OutputStream out = new FileOutputStream(dstPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    public void setTranscriptionListener(TranscriptionListener listener) {
        this.listener = listener;
    }

    public boolean isModelReady() {
        return isModelReady && model != null;
    }

    /**
     * Start live recording and transcription.
     */
    public void startRecording() {
        if (!isModelReady || model == null) {
            if (listener != null) {
                listener.onError("Model not ready. Please wait...");
            }
            return;
        }

        if (isListening) {
            return;
        }

        try {
            // Create recognizer with Hindi language settings
            Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
            
            // Configure for better Hindi recognition
            recognizer.setMaxAlternatives(3);
            recognizer.setWords(true);

            // Create speech service
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            speechService.startListening(this);
            
            isListening = true;
            currentTranscription.setLength(0);
            Log.d(TAG, "Started listening for Hindi speech");

        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            if (listener != null) {
                listener.onError("Failed to start recording: " + e.getMessage());
            }
        }
    }

    /**
     * Stop recording and get final transcription.
     */
    public void stopRecording() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        
        isListening = false;
        
        String finalText = currentTranscription.toString().trim();
        Log.d(TAG, "Stopped listening. Final transcription: " + finalText);
        
        if (listener != null) {
            listener.onRecordingComplete(finalText);
        }
    }

    /**
     * Transcribe an audio file.
     */
    public String transcribeAudioFile(Uri audioUri) throws IOException {
        if (!isModelReady || model == null) {
            throw new IOException("Model not ready");
        }

        Log.d(TAG, "Transcribing audio file: " + audioUri);
        
        StringBuilder transcription = new StringBuilder();
        Recognizer recognizer = null;

        try {
            recognizer = new Recognizer(model, SAMPLE_RATE);
            recognizer.setMaxAlternatives(3);
            recognizer.setWords(true);

            InputStream inputStream = context.getContentResolver().openInputStream(audioUri);
            if (inputStream == null) {
                throw new IOException("Cannot open audio file");
            }

            // Skip WAV header if present (44 bytes)
            byte[] header = new byte[44];
            int headerRead = inputStream.read(header);
            
            // Check if it's a WAV file
            boolean isWav = headerRead >= 4 && 
                           header[0] == 'R' && header[1] == 'I' && 
                           header[2] == 'F' && header[3] == 'F';

            if (!isWav) {
                // Not a WAV file, need to process differently or reset
                inputStream.close();
                inputStream = context.getContentResolver().openInputStream(audioUri);
            }

            // Process audio in chunks
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Convert bytes to shorts (16-bit PCM)
                short[] audioData = new short[bytesRead / 2];
                for (int i = 0; i < audioData.length && (i * 2 + 1) < bytesRead; i++) {
                    audioData[i] = (short) ((buffer[i * 2 + 1] << 8) | (buffer[i * 2] & 0xFF));
                }

                if (recognizer.acceptWaveForm(audioData, audioData.length)) {
                    String result = recognizer.getResult();
                    String text = extractText(result);
                    if (!text.isEmpty()) {
                        if (transcription.length() > 0) {
                            transcription.append(" ");
                        }
                        transcription.append(text);
                    }
                }
            }

            // Get final result
            String finalResult = recognizer.getFinalResult();
            String finalText = extractText(finalResult);
            if (!finalText.isEmpty()) {
                if (transcription.length() > 0) {
                    transcription.append(" ");
                }
                transcription.append(finalText);
            }

            inputStream.close();

        } finally {
            if (recognizer != null) {
                recognizer.close();
            }
        }

        String result = transcription.toString().trim();
        Log.d(TAG, "Audio file transcription complete: " + result);
        return result;
    }

    /**
     * Extract text from Vosk JSON result.
     */
    private String extractText(String jsonResult) {
        try {
            JSONObject obj = new JSONObject(jsonResult);
            return obj.optString("text", "");
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON result", e);
            return "";
        }
    }

    // RecognitionListener callbacks

    @Override
    public void onPartialResult(String hypothesis) {
        String text = extractText(hypothesis);
        if (!text.isEmpty() && listener != null) {
            listener.onPartialResult(text);
        }
    }

    @Override
    public void onResult(String hypothesis) {
        String text = extractText(hypothesis);
        if (!text.isEmpty()) {
            if (currentTranscription.length() > 0) {
                currentTranscription.append(" ");
            }
            currentTranscription.append(text);
            
            if (listener != null) {
                listener.onFinalResult(text);
            }
        }
    }

    @Override
    public void onFinalResult(String hypothesis) {
        String text = extractText(hypothesis);
        if (!text.isEmpty()) {
            if (currentTranscription.length() > 0) {
                currentTranscription.append(" ");
            }
            currentTranscription.append(text);
            
            if (listener != null) {
                listener.onFinalResult(text);
            }
        }
    }

    @Override
    public void onError(Exception exception) {
        Log.e(TAG, "Recognition error", exception);
        if (listener != null) {
            listener.onError("Recognition error: " + exception.getMessage());
        }
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "Recognition timeout");
        stopRecording();
    }

    /**
     * Clean up resources.
     */
    public void shutdown() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }

        if (model != null) {
            model.close();
            model = null;
        }

        isModelReady = false;
        isListening = false;
    }
}
