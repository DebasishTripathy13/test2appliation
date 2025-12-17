package com.example.voskhinditranscriber;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for medical AI analysis and suggestions
 * Provides summarization and medical recommendations based on transcribed speech
 */
public class MedicalAIService {

    private static final String TAG = "MedicalAIService";
    
    private Context context;
    private String selectedModel = "Basic Analysis";
    private LlmInferenceHelper llmHelper;
    private AIModel currentModel;
    
    public enum ModelType {
        BASIC("Basic Analysis", "Quick medical keyword extraction"),
        ADVANCED("Advanced Analysis", "Detailed medical analysis with suggestions"),
        SYMPTOM_CHECKER("Symptom Checker", "AI-powered symptom analysis"),
        MEDICATION_ADVISOR("Medication Advisor", "Medicine and treatment suggestions");
        
        private final String name;
        private final String description;
        
        ModelType(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
    }
    
    public interface AnalysisListener {
        void onAnalysisComplete(MedicalAnalysis analysis);
        void onError(String error);
        void onProgress(int progress);
    }
    
    public static class MedicalAnalysis {
        public String diagnosis;
        public List<String> rx; // Medications
        public List<String> advice;
        public String confidence;
        
        public MedicalAnalysis() {
            rx = new ArrayList<>();
            advice = new ArrayList<>();
        }
    }
    
    public MedicalAIService(Context context) {
        this.context = context;
    }
    
    public void setModel(String modelName) {
        this.selectedModel = modelName;
        Log.d(TAG, "Model selected: " + modelName);
    }
    
    public String getSelectedModel() {
        return selectedModel;
    }
    
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        for (ModelType type : ModelType.values()) {
            models.add(type.getName());
        }
        return models;
    }
    
    /**
     * Initialize LLM with a downloaded AI model.
     */
    public void initializeModel(AIModel model, LlmInferenceHelper.InitCallback callback) {
        if (llmHelper != null) {
            llmHelper.cleanup();
        }
        
        currentModel = model;
        llmHelper = new LlmInferenceHelper();
        llmHelper.initialize(context, model, callback);
    }
    
    /**
     * Check if LLM is ready for inference.
     */
    public boolean isModelReady() {
        return llmHelper != null && llmHelper.isInitialized();
    }
    
    /**
     * Clean up LLM resources.
     */
    public void cleanup() {
        if (llmHelper != null) {
            llmHelper.cleanup();
            llmHelper = null;
        }
    }
    
    /**
     * Analyze transcribed medical speech using real AI model.
     */
    public void analyzeMedicalText(String transcription, AIModel aiModel, AnalysisListener listener) {
        // Check if we should use real LLM or fallback to rule-based
        if (llmHelper != null && llmHelper.isInitialized()) {
            analyzeWithLLM(transcription, listener);
        } else {
            analyzeWithRules(transcription, aiModel, listener);
        }
    }
    
    /**
     * Analyze using real LLM (AI Edge LiteRT).
     */
    private void analyzeWithLLM(String transcription, AnalysisListener listener) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting AI-powered analysis with LLM");
                listener.onProgress(10);
                
                MedicalAnalysis analysis = new MedicalAnalysis();
                
                // Create comprehensive medical analysis prompt
                String prompt = createMedicalAnalysisPrompt(transcription);
                
                listener.onProgress(20);
                
                // Generate analysis using LLM
                final StringBuilder[] fullResponse = {new StringBuilder()};
                final boolean[] completed = {false};
                
                llmHelper.generateAnalysis(prompt, new LlmInferenceHelper.InferenceCallback() {
                    @Override
                    public void onTokenGenerated(String token) {
                        fullResponse[0].append(token);
                        // Update progress during generation
                        int progress = Math.min(90, 20 + (fullResponse[0].length() / 10));
                        listener.onProgress(progress);
                    }
                    
                    @Override
                    public void onComplete(String response) {
                        completed[0] = true;
                        parseAIResponse(response, analysis);
                        analysis.confidence = "AI-Powered (High)";
                        listener.onProgress(100);
                        listener.onAnalysisComplete(analysis);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "LLM error: " + error);
                        // Fallback to rule-based analysis
                        analyzeWithRules(transcription, currentModel, listener);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error during AI analysis", e);
                // Fallback to rule-based
                analyzeWithRules(transcription, currentModel, listener);
            }
        }).start();
    }
    
    /**
     * Fallback rule-based analysis.
     */
    private void analyzeWithRules(String transcription, AIModel aiModel, AnalysisListener listener) {
        new Thread(() -> {
            try {
                String modelName = aiModel != null ? aiModel.displayName : "Basic Analysis";
                Log.d(TAG, "Starting rule-based analysis with model: " + modelName);
                listener.onProgress(20);
                
                MedicalAnalysis analysis = new MedicalAnalysis();
                
                // Simulate analysis steps
                listener.onProgress(40);
                
                // Extract symptoms to guess diagnosis
                List<String> symptoms = extractSymptoms(transcription);
                analysis.diagnosis = guessDiagnosis(symptoms);
                
                listener.onProgress(60);
                
                analysis.rx = generateRx(analysis.diagnosis);
                
                listener.onProgress(80);
                
                analysis.advice = generateAdvice(analysis.diagnosis);
                analysis.confidence = "Rule-Based (Medium)";
                
                listener.onProgress(100);
                listener.onAnalysisComplete(analysis);
                
            } catch (Exception e) {
                Log.e(TAG, "Error during analysis", e);
                listener.onError("Analysis failed: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Create a comprehensive medical analysis prompt for the LLM.
     */
    private String createMedicalAnalysisPrompt(String transcription) {
        return "You are a helpful medical assistant. Create a medical prescription based on the following patient transcription.\n" +
               "Format the output EXACTLY as follows:\n\n" +
               "DIAGNOSIS: [Probable condition]\n" +
               "RX: [Medication 1] | [Medication 2] | ...\n" +
               "ADVICE: [Advice 1] | [Advice 2] | ...\n\n" +
               "Patient Transcription:\n" + transcription + "\n\n" +
               "Prescription:";
    }
    
    /**
     * Parse the AI-generated response into structured analysis.
     */
    private void parseAIResponse(String response, MedicalAnalysis analysis) {
        try {
            // Parse DIAGNOSIS
            int diagStart = response.indexOf("DIAGNOSIS:");
            int rxStart = response.indexOf("RX:");
            
            if (diagStart != -1 && rxStart != -1) {
                analysis.diagnosis = response.substring(diagStart + 10, rxStart).trim();
            } else if (diagStart != -1) {
                analysis.diagnosis = response.substring(diagStart + 10).trim();
            } else {
                analysis.diagnosis = "General Consultation";
            }
            
            // Parse RX
            int adviceStart = response.indexOf("ADVICE:");
            if (rxStart != -1 && adviceStart != -1) {
                String rxText = response.substring(rxStart + 3, adviceStart).trim();
                String[] rxArray = rxText.split("\\|");
                for (String rx : rxArray) {
                    String cleaned = rx.trim();
                    if (!cleaned.isEmpty()) {
                        analysis.rx.add(cleaned);
                    }
                }
            }
            
            // Parse ADVICE
            if (adviceStart != -1) {
                String adviceText = response.substring(adviceStart + 7).trim();
                String[] adviceArray = adviceText.split("\\|");
                for (String advice : adviceArray) {
                    String cleaned = advice.trim();
                    if (!cleaned.isEmpty()) {
                        analysis.advice.add(cleaned);
                    }
                }
            }
            
            // Defaults
            if (analysis.rx.isEmpty()) analysis.rx.add("No specific medication prescribed.");
            if (analysis.advice.isEmpty()) analysis.advice.add("Consult a doctor for confirmation.");
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing AI response", e);
            analysis.diagnosis = "Analysis Error";
            analysis.rx.add("Error parsing response");
        }
    }
    
    // --- Rule-based Helpers ---
    
    private List<String> extractSymptoms(String text) {
        List<String> symptoms = new ArrayList<>();
        String lowerText = text.toLowerCase();
        
        String[] symptomKeywords = {
            "fever", "bukhar", "pain", "dard", "cough", "khansi", 
            "cold", "sardi", "headache", "sir dard", "vomit", "ulti"
        };
        
        for (String k : symptomKeywords) {
            if (lowerText.contains(k)) symptoms.add(k);
        }
        return symptoms;
    }
    
    private String guessDiagnosis(List<String> symptoms) {
        if (symptoms.contains("fever") || symptoms.contains("bukhar")) return "Viral Fever";
        if (symptoms.contains("cough") || symptoms.contains("khansi")) return "Upper Respiratory Infection";
        if (symptoms.contains("pain") || symptoms.contains("dard")) return "General Body Pain / Myalgia";
        return "General Checkup";
    }
    
    private List<String> generateRx(String diagnosis) {
        List<String> rx = new ArrayList<>();
        if (diagnosis.contains("Fever")) {
            rx.add("Tab. Paracetamol 500mg (SOS)");
            rx.add("Tab. Vitamin C");
        } else if (diagnosis.contains("Cough") || diagnosis.contains("Respiratory")) {
            rx.add("Syp. Cough Formula 10ml TDS");
            rx.add("Tab. Cetirizine 10mg OD");
        } else if (diagnosis.contains("Pain")) {
            rx.add("Tab. Ibuprofen 400mg BD");
            rx.add("Gel. Pain Relief (Local Application)");
        } else {
            rx.add("Multivitamins OD");
        }
        return rx;
    }
    
    private List<String> generateAdvice(String diagnosis) {
        List<String> advice = new ArrayList<>();
        advice.add("Drink plenty of water");
        advice.add("Take proper rest");
        if (diagnosis.contains("Fever")) advice.add("Monitor temperature every 4 hours");
        advice.add("Follow up after 3 days if symptoms persist");
        return advice;
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}

