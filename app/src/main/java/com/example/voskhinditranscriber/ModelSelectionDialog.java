package com.example.voskhinditranscriber;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.List;

/**
 * Dialog for selecting and downloading AI models.
 * Based on AI Edge Gallery architecture.
 */
public class ModelSelectionDialog extends Dialog {

    public interface OnModelSelectedListener {
        void onModelSelected(AIModel model);
    }

    private RadioGroup radioGroup;
    private Button selectButton;
    private Button cancelButton;
    private Button downloadButton;
    private LinearLayout downloadProgressLayout;
    private ProgressBar downloadProgressBar;
    private TextView downloadStatusText;
    
    private OnModelSelectedListener listener;
    private AIModel currentModel;
    private AIModel selectedModel;
    private ModelDownloadManager downloadManager;
    private List<AIModel> availableModels;

    public ModelSelectionDialog(Context context, AIModel currentModel, OnModelSelectedListener listener) {
        super(context);
        this.listener = listener;
        this.currentModel = currentModel;
        this.selectedModel = currentModel;
        this.availableModels = ModelDownloadManager.AVAILABLE_MODELS;
        this.downloadManager = new ModelDownloadManager(context);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_model_selection);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        TextView titleText = findViewById(R.id.dialogTitle);
        radioGroup = findViewById(R.id.modelRadioGroup);
        selectButton = findViewById(R.id.selectButton);
        cancelButton = findViewById(R.id.cancelButton);
        downloadButton = findViewById(R.id.downloadButton);
        downloadProgressLayout = findViewById(R.id.downloadProgressLayout);
        downloadProgressBar = findViewById(R.id.downloadProgressBar);
        downloadStatusText = findViewById(R.id.downloadStatusText);
        
        setupModels();
        setupDownloadListener();
        setupButtons();
    }
    
    private void setupModels() {
        radioGroup.removeAllViews();
        
        for (AIModel model : availableModels) {
            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(8, 12, 8, 12);
            
            RadioButton radioButton = new RadioButton(getContext());
            radioButton.setText(model.displayName);
            radioButton.setTextSize(15);
            radioButton.setTag(model);
            
            if (currentModel != null && model.name.equals(currentModel.name)) {
                radioButton.setChecked(true);
            }
            
            radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedModel = model;
                    updateDownloadButton();
                }
            });
            
            TextView descText = new TextView(getContext());
            descText.setText(model.description);
            descText.setTextSize(11);
            descText.setAlpha(0.6f);
            descText.setPadding(40, 2, 0, 0);
            descText.setMaxLines(2);
            
            TextView sizeText = new TextView(getContext());
            boolean isDownloaded = downloadManager.isModelDownloaded(model);
            sizeText.setText(model.getFormattedSize() + (isDownloaded ? " • Downloaded ✓" : ""));
            sizeText.setTextSize(11);
            sizeText.setAlpha(0.5f);
            sizeText.setPadding(40, 2, 0, 0);
            
            itemLayout.addView(radioButton);
            itemLayout.addView(descText);
            itemLayout.addView(sizeText);
            
            radioGroup.addView(itemLayout);
        }
    }
    
    private void setupButtons() {
        selectButton.setOnClickListener(v -> {
            if (selectedModel != null) {
                if (downloadManager.isModelDownloaded(selectedModel)) {
                    if (listener != null) {
                        listener.onModelSelected(selectedModel);
                    }
                    dismiss();
                } else {
                    Toast.makeText(getContext(), "Please download the model first", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        downloadButton.setOnClickListener(v -> {
            if (selectedModel != null) {
                // Check if this is the import option
                if (selectedModel.name.equals("import-custom")) {
                    // Trigger file picker in MainActivity
                    if (getContext() instanceof MainActivity) {
                        ((MainActivity) getContext()).openFilePicker();
                        dismiss();
                    }
                } else if (downloadManager.isModelDownloaded(selectedModel)) {
                    Toast.makeText(getContext(), "Model already imported", Toast.LENGTH_SHORT).show();
                } else {
                    // Show download instructions for other models
                    String instructions = "To use this model:\n\n" +
                        "1. Download from HuggingFace:\n" + 
                        selectedModel.description.split("\\n")[0].replace("Download: ", "https://") + "\n\n" +
                        "2. Look for file:\n" + selectedModel.fileName + "\n\n" +
                        "3. Use 'Import Model from Storage' to select it";
                    
                    new android.app.AlertDialog.Builder(getContext())
                        .setTitle("Manual Download Required")
                        .setMessage(instructions)
                        .setPositiveButton("OK", null)
                        .show();
                }
            }
        });
        
        cancelButton.setOnClickListener(v -> dismiss());
        
        updateDownloadButton();
    }
    
    private void updateDownloadButton() {
        if (selectedModel != null) {
            boolean isDownloaded = downloadManager.isModelDownloaded(selectedModel);
            
            if (selectedModel.name.equals("import-custom")) {
                // Import option
                downloadButton.setText("📁 SELECT FILE");
                downloadButton.setEnabled(true);
                selectButton.setEnabled(false);
            } else if (isDownloaded) {
                // Model already imported
                downloadButton.setText("✓ IMPORTED");
                downloadButton.setEnabled(false);
                selectButton.setEnabled(true);
            } else {
                // Show instructions
                downloadButton.setText("ℹ️ HOW TO IMPORT");
                downloadButton.setEnabled(true);
                selectButton.setEnabled(false);
            }
        }
    }
    
    private void startDownload(AIModel model) {
        // Downloads are now manual - this is not used anymore
        Toast.makeText(getContext(), "Please import model manually", Toast.LENGTH_SHORT).show();
    }
    
    private void setupDownloadListener() {
        downloadManager.setDownloadListener(new ModelDownloadManager.DownloadListener() {
            @Override
            public void onDownloadStarted(AIModel model) {
                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).runOnUiThread(() -> {
                        downloadStatusText.setText("Downloading " + model.displayName + "...");
                    });
                }
            }
            
            @Override
            public void onDownloadProgress(AIModel model, int progress, long downloadedBytes, long totalBytes) {
                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).runOnUiThread(() -> {
                        downloadProgressBar.setProgress(progress);
                        downloadStatusText.setText(String.format("Downloading... %d%%", progress));
                    });
                }
            }
            
            @Override
            public void onDownloadCompleted(AIModel model, File modelFile) {
                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).runOnUiThread(() -> {
                        downloadProgressLayout.setVisibility(View.GONE);
                        Toast.makeText(getContext(), model.displayName + " downloaded!", Toast.LENGTH_LONG).show();
                        setupModels();
                        updateDownloadButton();
                    });
                }
            }
            
            @Override
            public void onDownloadFailed(AIModel model, String error) {
                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).runOnUiThread(() -> {
                        downloadProgressLayout.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Download failed: " + error, Toast.LENGTH_LONG).show();
                        downloadButton.setEnabled(true);
                    });
                }
            }
        });
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (downloadManager != null) {
            downloadManager.cleanup();
        }
    }
}
