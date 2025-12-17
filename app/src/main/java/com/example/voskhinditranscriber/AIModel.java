package com.example.voskhinditranscriber;

/**
 * Represents an AI model available for download and use.
 * Based on AI Edge Gallery model structure.
 */
public class AIModel {
    public final String name;              // Unique identifier
    public final String displayName;       // User-friendly name
    public final String description;       // Model description
    public final String modelId;           // HuggingFace model ID
    public final String fileName;          // Model file name
    public final long sizeInBytes;         // File size
    public final String version;           // Commit hash/version
    public final boolean supportsVision;   // Can process images
    public final boolean supportsAudio;    // Can process audio
    
    public AIModel(String name, String displayName, String description,
                   String modelId, String fileName, long sizeInBytes, 
                   String version, boolean supportsVision, boolean supportsAudio) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.modelId = modelId;
        this.fileName = fileName;
        this.sizeInBytes = sizeInBytes;
        this.version = version;
        this.supportsVision = supportsVision;
        this.supportsAudio = supportsAudio;
    }
    
    /**
     * Get human-readable file size.
     */
    public String getFormattedSize() {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AIModel other = (AIModel) obj;
        return name.equals(other.name);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public String toString() {
        return displayName + " (" + getFormattedSize() + ")";
    }
}
