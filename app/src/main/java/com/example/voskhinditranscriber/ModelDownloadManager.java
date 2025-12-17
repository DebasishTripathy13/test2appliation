package com.example.voskhinditranscriber;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages downloading and tracking AI models from HuggingFace.
 * Based on AI Edge Gallery architecture.
 */
public class ModelDownloadManager {
    private static final String TAG = "ModelDownloadManager";
    
    private final Context context;
    private final DownloadManager downloadManager;
    private final Map<String, Long> downloadIds = new HashMap<>();
    private final Map<String, AIModel> downloadingModels = new HashMap<>();
    private DownloadListener downloadListener;
    
    // Available AI Models - Using public models that don't require authentication
    public static final List<AIModel> AVAILABLE_MODELS = new ArrayList<>();
    
    static {
        // Import your own model option
        AVAILABLE_MODELS.add(new AIModel(
            "import-custom",
            "📁 Import Model from Storage",
            "Select a downloaded .task model file from your device storage",
            "custom",
            "custom.task",
            0L,
            "v1.0",
            false,
            false
        ));
        
        // Recommended models to download manually:
        // Download from: https://huggingface.co/litert-community
        
        AVAILABLE_MODELS.add(new AIModel(
            "Gemma3-1B-IT-q4",
            "Gemma 3n 1B (529MB) - Import Required",
            "Download: huggingface.co/litert-community/Gemma3-1B-IT\nFile: Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            "litert-community/Gemma3-1B-IT",
            "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            554661246L,
            "20250514",
            false,
            false
        ));
        
        AVAILABLE_MODELS.add(new AIModel(
            "Gemma3n-e2b-it-q4",
            "Gemma 3n E2B (3.1GB) - Import Required",
            "Download: huggingface.co/litert-community/Gemma3n-e2b-it\nFile: Gemma3n-e2b-it_multi-prefill-seq_q4_ekv2048.task",
            "litert-community/Gemma3n-e2b-it",
            "Gemma3n-e2b-it_multi-prefill-seq_q4_ekv2048.task",
            3307974654L,
            "20250514",
            false,
            false
        ));
        
        AVAILABLE_MODELS.add(new AIModel(
            "Qwen2.5-1.5B-Instruct-q4",
            "Qwen2.5 1.5B (1.6GB) - Import Required",
            "Download: huggingface.co/litert-community/Qwen2.5-1.5B-Instruct\nFile: Qwen2.5-1.5B-Instruct_multi-prefill-seq_q4_ekv2048.task",
            "litert-community/Qwen2.5-1.5B-Instruct",
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q4_ekv2048.task",
            1681915906L,
            "20250514",
            false,
            false
        ));
    }
    
    public interface DownloadListener {
        void onDownloadStarted(AIModel model);
        void onDownloadProgress(AIModel model, int progress, long downloadedBytes, long totalBytes);
        void onDownloadCompleted(AIModel model, File modelFile);
        void onDownloadFailed(AIModel model, String error);
    }
    
    private boolean receiverRegistered = false;
    
    public ModelDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }
    
    private void registerReceiver() {
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(downloadReceiver, filter);
            }
            receiverRegistered = true;
        }
    }
    
    public void setDownloadListener(DownloadListener listener) {
        this.downloadListener = listener;
    }
    
    /**
     * Import a model file from user-selected location.
     */
    public void importModel(AIModel model, File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            if (downloadListener != null) {
                downloadListener.onDownloadFailed(model, "File not found");
            }
            return;
        }
        
        try {
            // Create destination directory
            File modelDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models/" + model.name);
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }
            
            File destinationFile = new File(modelDir, model.fileName);
            
            // Copy file
            java.io.InputStream input = new java.io.FileInputStream(sourceFile);
            java.io.OutputStream output = new java.io.FileOutputStream(destinationFile);
            
            byte[] buffer = new byte[8192];
            int length;
            long totalBytes = sourceFile.length();
            long copiedBytes = 0;
            
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
                copiedBytes += length;
                
                int progress = (int) ((copiedBytes * 100) / totalBytes);
                if (downloadListener != null && progress % 10 == 0) {
                    downloadListener.onDownloadProgress(model, progress, copiedBytes, totalBytes);
                }
            }
            
            input.close();
            output.close();
            
            Log.d(TAG, "Model imported successfully: " + destinationFile.getAbsolutePath());
            
            if (downloadListener != null) {
                downloadListener.onDownloadCompleted(model, destinationFile);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to import model", e);
            if (downloadListener != null) {
                downloadListener.onDownloadFailed(model, "Import failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Set the model file path directly (for imported models).
     */
    public void setModelPath(AIModel model, String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            importModel(model, file);
        }
    }
    
    /**
     * Check if a model is already downloaded.
     */
    public boolean isModelDownloaded(AIModel model) {
        File modelFile = getModelFile(model);
        return modelFile.exists() && modelFile.length() > 0;
    }
    
    /**
     * Cancel ongoing download.
     */
    public void cancelDownload(AIModel model) {
        Long downloadId = downloadIds.get(model.name);
        if (downloadId != null) {
            downloadManager.remove(downloadId);
            downloadIds.remove(model.name);
            downloadingModels.remove(model.name);
        }
    }
    
    /**
     * Get the file path for a downloaded model.
     */
    public File getModelFile(AIModel model) {
        File modelDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models/" + model.name);
        return new File(modelDir, model.fileName);
    }
    
    /**
     * Delete a downloaded model.
     */
    public boolean deleteModel(AIModel model) {
        File modelFile = getModelFile(model);
        if (modelFile.exists()) {
            return modelFile.delete();
        }
        return false;
    }
    
    /**
     * Get list of downloaded models.
     */
    public List<AIModel> getDownloadedModels() {
        List<AIModel> downloaded = new ArrayList<>();
        for (AIModel model : AVAILABLE_MODELS) {
            if (isModelDownloaded(model)) {
                downloaded.add(model);
            }
        }
        return downloaded;
    }
    
    /**
     * Broadcast receiver for download completion.
     */
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            
            // Find which model this download ID belongs to
            for (Map.Entry<String, Long> entry : downloadIds.entrySet()) {
                if (entry.getValue() == downloadId) {
                    String modelName = entry.getKey();
                    AIModel model = downloadingModels.get(modelName);
                    
                    if (model != null) {
                        // Check if download was successful
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(downloadId);
                        Cursor cursor = downloadManager.query(query);
                        
                        if (cursor != null && cursor.moveToFirst()) {
                            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int status = cursor.getInt(statusIndex);
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                File modelFile = getModelFile(model);
                                if (downloadListener != null) {
                                    downloadListener.onDownloadCompleted(model, modelFile);
                                }
                                Log.d(TAG, "Model downloaded successfully: " + model.name);
                            } else {
                                int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                                int reason = cursor.getInt(reasonIndex);
                                String error = "Download failed with code: " + reason;
                                if (downloadListener != null) {
                                    downloadListener.onDownloadFailed(model, error);
                                }
                                Log.e(TAG, "Model download failed: " + error);
                            }
                            cursor.close();
                        }
                        
                        downloadIds.remove(modelName);
                        downloadingModels.remove(modelName);
                    }
                    break;
                }
            }
        }
    };
    
    public void cleanup() {
        try {
            if (receiverRegistered) {
                context.unregisterReceiver(downloadReceiver);
                receiverRegistered = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
        }
    }
}
