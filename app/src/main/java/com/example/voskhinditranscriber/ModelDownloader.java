package com.example.voskhinditranscriber;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Custom downloader for HuggingFace models with proper error handling and progress tracking.
 */
public class ModelDownloader {
    private static final String TAG = "ModelDownloader";
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 30000;
    
    private volatile boolean isCancelled = false;
    
    public interface DownloadProgressListener {
        void onProgress(long downloadedBytes, long totalBytes, int percentage);
        void onComplete(File file);
        void onError(String error);
    }
    
    /**
     * Download a model from HuggingFace with proper error handling.
     */
    public void downloadModel(Context context, AIModel model, DownloadProgressListener listener) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream input = null;
            FileOutputStream output = null;
            
            try {
                // Construct URL
                String urlString = String.format(
                    "https://huggingface.co/%s/resolve/%s/%s?download=true",
                    model.modelId,
                    model.version,
                    model.fileName
                );
                
                Log.d(TAG, "Downloading from: " + urlString);
                
                // Create destination directory
                File modelDir = new File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), 
                    "models/" + model.name
                );
                if (!modelDir.exists()) {
                    modelDir.mkdirs();
                }
                
                File destinationFile = new File(modelDir, model.fileName);
                
                // Open connection
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
                connection.setRequestProperty("Accept", "*/*");
                connection.setInstanceFollowRedirects(true);
                
                // Get response code
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    // Handle redirect
                    String newUrl = connection.getHeaderField("Location");
                    Log.d(TAG, "Redirected to: " + newUrl);
                    connection.disconnect();
                    
                    // Follow redirect
                    connection = (HttpURLConnection) new URL(newUrl).openConnection();
                    connection.setConnectTimeout(CONNECT_TIMEOUT);
                    connection.setReadTimeout(READ_TIMEOUT);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
                    responseCode = connection.getResponseCode();
                }
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String errorMsg = "HTTP error code: " + responseCode;
                    if (responseCode == 401 || responseCode == 403) {
                        errorMsg += "\nThis model requires authentication. Please use a public model or implement OAuth.";
                    }
                    listener.onError(errorMsg);
                    return;
                }
                
                // Get file size
                long fileLength = connection.getContentLengthLong();
                Log.d(TAG, "File size: " + fileLength + " bytes");
                
                // Download file
                input = connection.getInputStream();
                output = new FileOutputStream(destinationFile);
                
                byte[] buffer = new byte[BUFFER_SIZE];
                long total = 0;
                int count;
                int lastProgress = 0;
                
                while ((count = input.read(buffer)) != -1) {
                    if (isCancelled) {
                        Log.d(TAG, "Download cancelled");
                        destinationFile.delete();
                        return;
                    }
                    
                    total += count;
                    output.write(buffer, 0, count);
                    
                    // Update progress
                    if (fileLength > 0) {
                        int progress = (int) ((total * 100) / fileLength);
                        if (progress != lastProgress && progress % 5 == 0) {
                            listener.onProgress(total, fileLength, progress);
                            lastProgress = progress;
                        }
                    }
                }
                
                output.flush();
                Log.d(TAG, "Download completed: " + destinationFile.getAbsolutePath());
                listener.onComplete(destinationFile);
                
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                listener.onError("Download failed: " + e.getMessage());
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing streams", e);
                }
            }
        }).start();
    }
    
    public void cancel() {
        isCancelled = true;
    }
}
