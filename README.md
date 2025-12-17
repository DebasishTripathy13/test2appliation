# Medical AI Assistant

An advanced Android application that combines Hindi/English speech-to-text transcription with on-device AI for medical analysis and suggestions. Powered by Vosk speech recognition and inspired by Google AI Edge Gallery's architecture.

## ✨ Features

### 🎤 Voice Transcription
- **Real-time Recording** - Record medical consultations and conversations
- **Audio File Upload** - Upload and transcribe existing audio files (WAV, MP3, etc.)
- **Complete Transcription** - Full transcription text with high accuracy
- **Offline Processing** - Works completely offline after model installation

### 🤖 AI-Powered Medical Analysis
- **Multiple AI Models** - Choose from 4 specialized medical AI models:
  - 🔹 **Basic Analysis** - Quick medical keyword extraction
  - 🔹 **Advanced Analysis** - Detailed medical analysis with suggestions
  - 🔹 **Symptom Checker** - AI-powered symptom analysis
  - � **Medication Advisor** - Medicine and treatment suggestions
- **Smart Summarization** - AI generates concise medical summaries
- **Symptom Detection** - Automatically identifies medical symptoms
- **Personalized Suggestions** - Context-aware medical recommendations
- **Safety Warnings** - Important disclaimers and safety information

### 🎨 Modern UI/UX (Inspired by AI Edge Gallery)
- **Material Design 3** - Clean, professional interface
- **Model Selection Dialog** - Easy AI model switching
- **Real-time Progress** - Visual feedback during analysis
- **Organized Card Layout** - Separate sections for transcription and analysis
- **Selectable Text** - Copy transcriptions and analysis results
- **Responsive Design** - Smooth animations and interactions

## Prerequisites

- Android Studio (Arctic Fox or later)
- Android SDK (API 24 or higher)
- Gradle 7.0+
- Hindi Vosk model

## Setup Instructions

### 1. Clone/Download the Project

Download or clone this project to your local machine.

### 2. Download Vosk Hindi Model

1. Download the Hindi Vosk model from: https://alphacephei.com/vosk/models
   - Recommended: `vosk-model-small-hi-0.22` (lighter, ~42MB)
   - Or: `vosk-model-hi-0.22` (larger, better accuracy, ~1.5GB)

2. Extract the downloaded model

3. Rename the extracted folder to `model-hi`

4. Place the `model-hi` folder inside:
   ```
   VoskHindiTranscriber/app/src/main/assets/model-hi/
   ```

   The directory structure should look like:
   ```
   app/src/main/assets/model-hi/
   ├── am/
   ├── conf/
   ├── graph/
   ├── ivector/
   └── README
   ```

### 3. Open Project in Android Studio

1. Launch Android Studio
2. Select "Open an Existing Project"
3. Navigate to the `VoskHindiTranscriber` folder
4. Click "OK" and wait for Gradle sync to complete

### 4. Build and Run

1. Connect an Android device with USB debugging enabled, or create an emulator
2. Click the "Run" button in Android Studio
3. Grant microphone permissions when prompted
4. Wait for the model to initialize
5. Click "Start Recording" to begin transcription

## Project Structure

```
VoskHindiTranscriber/
├── app/
│   ├── build.gradle                 # App-level Gradle configuration
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml  # App manifest with permissions
│   │       ├── assets/              # Place Vosk model here
│   │       │   └── model-hi/        # Hindi model folder
│   │       ├── java/com/example/voskhinditranscriber/
│   │       │   ├── MainActivity.java              # Main UI activity
│   │       │   └── VoskTranscriptionService.java  # Vosk service
│   │       └── res/
│   │           ├── layout/
│   │           │   └── activity_main.xml          # Main UI layout
│   │           └── values/
│   │               ├── strings.xml                # String resources
### Step 1: Record or Upload Medical Audio

1. Launch the app
2. Wait for "✅ Ready to record or upload audio" status
3. **To Record:**
   - Tap the **blue microphone FAB** button at the bottom
   - Speak about medical symptoms, concerns, or consultation notes
   - Tap the button again to stop recording
   - Wait for transcription to complete
4. **To Upload:**
   - Tap the **📂 Upload Audio** button
   - Select an audio file from your device (WAV format recommended)
   - Wait for processing
   - View complete transcription

### Step 2: Select AI Model

1. Review the current AI model in the **🤖 AI Model** card
2. Tap **"Change Model"** to select a different AI model
3. Choose from:
   - **Basic Analysis** - Fast keyword extraction
   - **Advanced Analysis** - Comprehensive medical insights
   - **Symptom Checker** - Symptom-focused analysis
   - **Medication Advisor** - Treatment recommendations
4. Tap **"Select Model"** to confirm

### Step 3: Analyze with AI

1. After transcription is complete, review the text in the **📝 Transcription** card
2. Tap the **🔍 Analyze with AI** button in the **🏥 Medical Analysis** card
3. Wait for AI processing (progress shown in status)
4. View comprehensive analysis including:
   - 📋 Medical Summary
   - 🔹 Detected Symptoms
   - 💡 AI Suggestions
   - ⚠️ Important Warnings
   - 📊 Confidence Level

### Managing Data

- **Clear All**: Tap the "Clear" button to remove transcriptions and analysis
- **Copy Text**: Long-press any text to select and copy
- **Review Models**: Switch models anytime to compare different analyses
5. Watch real-time transcription appear in the card
6. Tap the button again to stop recording
7. View complete transcription

### Uploading Audio Files

1. Tap the **📂 Upload Audio** button
2. Select an audio file from your device (WAV format recommended)
3. Wait for processing
4. View complete transcription

### Managing Transcriptions

- **Clear**: Tap the "Clear" button to remove all transcribed text
- **Copy**: Long-press the transcription text to select and copy
- **Continuous Mode**: Keep recording to append more text to existing transcription

## Permissions

The app requires the following permissions:
- `RECORD_AUDIO`: To capture audio for transcription
- `INTERNET`: For potential model downloads (optional)

## Troubleshooting

### Model Not Loading
- Ensure the model folder is named exactly `model-hi`
- Verify the model is in `app/src/main/assets/model-hi/`
- Check that all model files are present (am, conf, graph, ivector folders)

### No Audio Recording
- Grant microphone permission in app settings
- Test microphone with another app
- Ensure device audio input is working

### Build Errors
- Clean and rebuild: Build → Clean Project, then Build → Rebuild Project
- Sync Gradle files
- Check internet connection for dependency downloads

### Poor Recognition Quality
- Speak clearly and at moderate pace
- Use the larger Hindi model for better accuracy
- Ensure minimal background noise

## Dependencies

- AndroidX AppCompat: 1.6.1
- Material Components: 1.9.0
- ConstraintLayout: 2.1.4
- Vosk Android: 0.3.32

## Technical Details

- **Sample Rate**: 16000 Hz
- **Audio Format**: PCM 16-bit
- **Channel**: Mono
- **Recognition**: Real-time with partial and final results

## Model Information

Vosk models are offline speech recognition models. For Hindi:
- Small model (~42MB): Good for mobile devices, decent accuracy
- Full model (~1.5GB): Better accuracy, requires more storage

Download from: https://alphacephei.com/vosk/models

## License

This project uses the Vosk library which is licensed under the Apache License 2.0.

## Credits

- Vosk Speech Recognition: https://alphacephei.com/vosk/
- Alpha Cephei Inc.

## Support

For issues with:
- Vosk library: https://github.com/alphacep/vosk-api
- Hindi models: https://alphacephei.com/vosk/models

## Future Enhancements

- Save transcriptions to file
- Share transcription text
- Support for multiple languages
- Custom vocabulary
- Audio playback
- Transcription history
