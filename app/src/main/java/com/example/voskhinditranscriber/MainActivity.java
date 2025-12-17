package com.example.voskhinditranscriber;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    // UI Components
    private ExtendedFloatingActionButton recordButton;
    private Button uploadButton;
    private Button clearButton;
    private Button analyzeButton;
    private Button changeModelButton;
    private TextView statusTextView;
    private TextView transcriptionTextView;
    private TextView selectedModelText;
    private TextView diagnosisText;
    private TextView rxText;
    private TextView adviceText;
    private TextView confidenceText;
    private ProgressBar progressBar;
    private MaterialCardView transcriptionCard;
    private MaterialCardView analysisCard;

    // Services
    private VoskTranscriptionService transcriptionService;
    private MedicalAIService medicalAIService;

    // State
    private boolean isRecording = false;
    private AIModel currentAIModel;
    private StringBuilder completeTranscription = new StringBuilder();

    private ActivityResultLauncher<String> audioPickerLauncher;
    private ActivityResultLauncher<String> modelPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            // Initialize UI components
            recordButton = findViewById(R.id.recordButton);
            uploadButton = findViewById(R.id.uploadButton);
            clearButton = findViewById(R.id.clearButton);
            analyzeButton = findViewById(R.id.analyzeButton);
            changeModelButton = findViewById(R.id.changeModelButton);
            statusTextView = findViewById(R.id.statusTextView);
            transcriptionTextView = findViewById(R.id.transcriptionTextView);
            selectedModelText = findViewById(R.id.selectedModelText);
            diagnosisText = findViewById(R.id.diagnosisText);
            rxText = findViewById(R.id.rxText);
            adviceText = findViewById(R.id.adviceText);
            confidenceText = findViewById(R.id.confidenceText);
            progressBar = findViewById(R.id.progressBar);
            transcriptionCard = findViewById(R.id.transcriptionCard);
            analysisCard = findViewById(R.id.analysisCard);

            // Initialize medical AI service
            medicalAIService = new MedicalAIService(this);

            // Set initial AI model
            currentAIModel = getDefaultAIModel();
            updateSelectedModelUI();

            // Request audio permission
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            }

            // Setup audio picker
            audioPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        transcribeAudioFile(uri);
                    }
                }
            );

            // Setup model file picker
            modelPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Log.d(TAG, "File selected: " + uri.toString());
                        importModelFile(uri);
                    } else {
                        Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                    }
                }
            );

            // Setup button listeners
            if (recordButton != null) recordButton.setOnClickListener(v -> toggleRecording());
            if (uploadButton != null) uploadButton.setOnClickListener(v -> selectAudioFile());
            if (clearButton != null) clearButton.setOnClickListener(v -> clearTranscription());
            if (analyzeButton != null) analyzeButton.setOnClickListener(v -> analyzeMedicalText());
            if (changeModelButton != null) changeModelButton.setOnClickListener(v -> showModelSelectionDialog());

            // Disable buttons initially
            if (recordButton != null) recordButton.setEnabled(false);
            if (uploadButton != null) uploadButton.setEnabled(false);

            // Initialize transcription service
            initializeTranscriptionService();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeTranscriptionService() {
        if (statusTextView != null) statusTextView.setText("Initializing Vosk model...");
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                transcriptionService = new VoskTranscriptionService(this);
                transcriptionService.setTranscriptionListener(new VoskTranscriptionService.TranscriptionListener() {
                    @Override
                    public void onPartialResult(String text) {
                        runOnUiThread(() -> {
                            if (transcriptionTextView != null && !text.isEmpty()) {
                                transcriptionTextView.setText(completeTranscription.toString() + text);
                            }
                        });
                    }

                    @Override
                    public void onFinalResult(String text) {
                        runOnUiThread(() -> {
                            if (transcriptionTextView != null && !text.isEmpty()) {
                                completeTranscription.append(text).append(" ");
                                transcriptionTextView.setText(completeTranscription.toString());
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if (statusTextView != null) statusTextView.setText("❌ Error: " + error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onRecordingComplete(String transcription) {
                        runOnUiThread(() -> {
                            if (transcriptionTextView != null && !transcription.isEmpty()) {
                                completeTranscription.append(transcription);
                                transcriptionTextView.setText(completeTranscription.toString());
                            }
                            if (statusTextView != null) statusTextView.setText("✅ Recording complete");
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            if (uploadButton != null) uploadButton.setEnabled(true);
                        });
                    }

                    @Override
                    public void onModelReady() {
                        runOnUiThread(() -> {
                            if (statusTextView != null) statusTextView.setText("✅ Ready to record or upload audio");
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            if (recordButton != null) recordButton.setEnabled(true);
                            if (uploadButton != null) uploadButton.setEnabled(true);
                            Toast.makeText(MainActivity.this, "Model loaded successfully!", Toast.LENGTH_SHORT).show();
                        });
                    }
                });

                runOnUiThread(() -> {
                    if (statusTextView != null) statusTextView.setText("✅ Ready to record or upload audio");
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (recordButton != null) recordButton.setEnabled(true);
                    if (uploadButton != null) uploadButton.setEnabled(true);
                });
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Native library not found", e);
                runOnUiThread(() -> {
                    if (statusTextView != null) statusTextView.setText("❌ Vosk library not available");
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    // Enable buttons anyway so user can at least use AI analysis
                    if (recordButton != null) recordButton.setEnabled(false);
                    if (uploadButton != null) uploadButton.setEnabled(false);
                    Toast.makeText(MainActivity.this, "Vosk speech recognition not available on this device", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Vosk", e);
                runOnUiThread(() -> {
                    String errorMsg = "Failed to initialize Vosk: " + e.getMessage();
                    if (statusTextView != null) statusTextView.setText("❌ Failed to initialize");
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void selectAudioFile() {
        audioPickerLauncher.launch("audio/*");
    }

    private void transcribeAudioFile(Uri audioUri) {
        if (transcriptionService == null || !transcriptionService.isModelReady()) {
            Toast.makeText(this, "Please wait for model to load", Toast.LENGTH_SHORT).show();
            return;
        }

        statusTextView.setText("📂 Processing audio file...");
        progressBar.setVisibility(View.VISIBLE);
        uploadButton.setEnabled(false);
        recordButton.setEnabled(false);

        new Thread(() -> {
            try {
                String result = transcriptionService.transcribeAudioFile(audioUri);
                runOnUiThread(() -> {
                    if (!result.isEmpty()) {
                        completeTranscription.append(result);
                        transcriptionTextView.setText(completeTranscription.toString());
                        transcriptionCard.setVisibility(View.VISIBLE);
                        statusTextView.setText("✅ Transcription complete");
                        Toast.makeText(MainActivity.this, "Transcription complete!", Toast.LENGTH_SHORT).show();
                    } else {
                        statusTextView.setText("⚠️ No speech detected");
                        Toast.makeText(MainActivity.this, "No speech detected in audio", Toast.LENGTH_SHORT).show();
                    }
                    progressBar.setVisibility(View.GONE);
                    uploadButton.setEnabled(true);
                    recordButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusTextView.setText("❌ Failed to transcribe audio");
                    progressBar.setVisibility(View.GONE);
                    uploadButton.setEnabled(true);
                    recordButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void clearTranscription() {
        completeTranscription.setLength(0);
        transcriptionTextView.setText("");
        if (diagnosisText != null) {
            diagnosisText.setText("");
            rxText.setText("");
            adviceText.setText("");
        }
        transcriptionCard.setVisibility(View.GONE);
        analysisCard.setVisibility(View.GONE);
        statusTextView.setText("✅ Ready to record or upload audio");
        Toast.makeText(this, "Transcription cleared", Toast.LENGTH_SHORT).show();
    }

    private void showModelSelectionDialog() {
        ModelSelectionDialog dialog = new ModelSelectionDialog(
            this,
            currentAIModel,
            model -> {
                currentAIModel = model;
                updateSelectedModelUI();

                // Initialize LLM if model is downloaded
                ModelDownloadManager downloadManager = new ModelDownloadManager(this);
                if (downloadManager.isModelDownloaded(model)) {
                    initializeAIModel(model);
                }
                downloadManager.cleanup();

                Toast.makeText(this, "Model changed to: " + model.displayName, Toast.LENGTH_SHORT).show();
            }
        );
        dialog.show();
    }

    private void initializeAIModel(AIModel model) {
        Toast.makeText(this, "Loading AI model...", Toast.LENGTH_SHORT).show();

        medicalAIService.initializeModel(model, new LlmInferenceHelper.InitCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        "✅ " + model.displayName + " loaded successfully!",
                        Toast.LENGTH_LONG).show();
                    updateSelectedModelUI();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        "⚠️ Failed to load model: " + error + "\nUsing rule-based analysis.",
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void analyzeMedicalText() {
        String transcription = completeTranscription.toString().trim();

        if (transcription.isEmpty()) {
            Toast.makeText(this, "No transcription to analyze", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if current model is downloaded
        ModelDownloadManager downloadManager = new ModelDownloadManager(this);
        boolean isDownloaded = downloadManager.isModelDownloaded(currentAIModel);
        downloadManager.cleanup();

        if (!isDownloaded) {
            Toast.makeText(this, "Please download " + currentAIModel.displayName + " first", Toast.LENGTH_LONG).show();
            showModelSelectionDialog();
            return;
        }

        statusTextView.setText("🔄 Analyzing with " + currentAIModel.displayName + "...");
        progressBar.setVisibility(View.VISIBLE);
        analyzeButton.setEnabled(false);
        analysisCard.setVisibility(View.VISIBLE);

        medicalAIService.analyzeMedicalText(transcription, currentAIModel, new MedicalAIService.AnalysisListener() {
            @Override
            public void onAnalysisComplete(MedicalAIService.MedicalAnalysis analysis) {
                runOnUiThread(() -> {
                    displayAnalysisResults(analysis);
                    statusTextView.setText("✅ Analysis complete");
                    progressBar.setVisibility(View.GONE);
                    analyzeButton.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    statusTextView.setText("❌ Analysis failed");
                    progressBar.setVisibility(View.GONE);
                    analyzeButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> {
                    statusTextView.setText("🔄 Analyzing... " + progress + "%");
                });
            }
        });
    }

    private void displayAnalysisResults(MedicalAIService.MedicalAnalysis analysis) {
        // Populate Prescription UI
        diagnosisText.setText(analysis.diagnosis);

        StringBuilder rxBuilder = new StringBuilder();
        for (String rx : analysis.rx) {
            rxBuilder.append("• ").append(rx).append("\n");
        }
        rxText.setText(rxBuilder.toString().trim());

        StringBuilder adviceBuilder = new StringBuilder();
        for (String advice : analysis.advice) {
            adviceBuilder.append("• ").append(advice).append("\n");
        }
        adviceText.setText(adviceBuilder.toString().trim());

        confidenceText.setText("Confidence: " + analysis.confidence + "\nModel: " + (currentAIModel != null ? currentAIModel.displayName : "Unknown"));
    }

    private void updateRecordButton(boolean recording) {
        if (recording) {
            recordButton.setIcon(getDrawable(android.R.drawable.ic_media_pause));
            recordButton.setText("Stop");
            recordButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_light, null));
        } else {
            recordButton.setIcon(getDrawable(android.R.drawable.ic_btn_speak_now));
            recordButton.setText("Record Audio");
            recordButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_blue_dark, null));
        }
    }

    private void toggleRecording() {
        if (transcriptionService == null) {
            Toast.makeText(this, "Transcription service not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }

        transcriptionService.startRecording();
        isRecording = true;
        updateRecordButton(true);
        uploadButton.setEnabled(false);
        statusTextView.setText("🎤 Recording...");
        transcriptionCard.setVisibility(View.VISIBLE);
    }

    private void stopRecording() {
        transcriptionService.stopRecording();
        isRecording = false;
        updateRecordButton(false);
        uploadButton.setEnabled(false);
        statusTextView.setText("🔄 Processing recording...");
        progressBar.setVisibility(View.VISIBLE);
        transcriptionCard.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Audio permission is required for transcription", Toast.LENGTH_LONG).show();
            }
        }
    }

    private AIModel getDefaultAIModel() {
        try {
            // Try to find first downloaded model
            ModelDownloadManager downloadManager = new ModelDownloadManager(this);
            List<AIModel> downloaded = downloadManager.getDownloadedModels();
            downloadManager.cleanup();

            if (downloaded != null && !downloaded.isEmpty()) {
                return downloaded.get(0);
            }

            // Return first available model (not downloaded yet)
            if (ModelDownloadManager.AVAILABLE_MODELS != null && !ModelDownloadManager.AVAILABLE_MODELS.isEmpty()) {
                return ModelDownloadManager.AVAILABLE_MODELS.get(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting default AI model", e);
        }
        
        // Return a fallback model
        return new AIModel(
            "basic-analysis",
            "Basic Analysis",
            "Rule-based medical analysis",
            "basic",
            "none",
            0L,
            "v1.0",
            false,
            false
        );
    }

    private void updateSelectedModelUI() {
        if (selectedModelText != null && currentAIModel != null) {
            selectedModelText.setText("Model: " + currentAIModel.displayName);
        } else if (selectedModelText != null) {
            selectedModelText.setText("Model: Basic Analysis");
        }
    }

    /**
     * Open file picker to select a .task model file
     */
    public void openFilePicker() {
        // Check for storage permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, 2);
                Toast.makeText(this, "Please grant storage permission to import models", Toast.LENGTH_LONG).show();
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            Toast.makeText(this, "Please grant storage permission to import models", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Select a .task model file from your storage", Toast.LENGTH_LONG).show();

        // Try to launch with application/octet-stream for .task files
        try {
            modelPickerLauncher.launch("application/octet-stream");
        } catch (Exception e) {
            // Fallback to any file type
            modelPickerLauncher.launch("*/*");
        }
    }

    /**
     * Import a model file from storage
     */
    private void importModelFile(Uri uri) {
        try {
            // Get file path from URI
            String fileName = getFileNameFromUri(uri);

            if (fileName == null || !fileName.endsWith(".task")) {
                Toast.makeText(this, "Please select a .task model file", Toast.LENGTH_LONG).show();
                return;
            }

            Toast.makeText(this, "Importing model: " + fileName, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.VISIBLE);

            // Copy file to app storage in background
            new Thread(() -> {
                try {
                    // Create temporary file
                    File tempFile = new File(getCacheDir(), fileName);
                    java.io.InputStream input = getContentResolver().openInputStream(uri);
                    java.io.OutputStream output = new java.io.FileOutputStream(tempFile);

                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }

                    input.close();
                    output.close();

                    // Create imported model
                    AIModel importedModel = new AIModel(
                        "imported-" + System.currentTimeMillis(),
                        fileName.replace(".task", ""),
                        "Imported from storage",
                        "custom",
                        fileName,
                        tempFile.length(),
                        "v1.0",
                        false,
                        false
                    );

                    // Import model using ModelDownloadManager
                    ModelDownloadManager downloadManager = new ModelDownloadManager(this);
                    downloadManager.setDownloadListener(new ModelDownloadManager.DownloadListener() {
                        @Override
                        public void onDownloadStarted(AIModel model) {}

                        @Override
                        public void onDownloadProgress(AIModel model, int progress, long downloadedBytes, long totalBytes) {}

                        @Override
                        public void onDownloadCompleted(AIModel model, File modelFile) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(MainActivity.this,
                                    "✅ Model imported successfully!",
                                    Toast.LENGTH_LONG).show();

                                // Set as current model and initialize
                                currentAIModel = importedModel;
                                updateSelectedModelUI();
                                initializeAIModel(importedModel);

                                // Delete temp file
                                tempFile.delete();
                            });
                        }

                        @Override
                        public void onDownloadFailed(AIModel model, String error) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(MainActivity.this,
                                    "❌ Import failed: " + error,
                                    Toast.LENGTH_LONG).show();
                                tempFile.delete();
                            });
                        }
                    });

                    downloadManager.importModel(importedModel, tempFile);

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, "Error importing model: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    });
                }
            }).start();

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Get filename from URI
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
                fileName = cursor.getString(nameIndex);
            }
            cursor.close();
        }
        return fileName;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transcriptionService != null) {
            transcriptionService.shutdown();
        }
        if (medicalAIService != null) {
            medicalAIService.cleanup();
        }
    }
}
