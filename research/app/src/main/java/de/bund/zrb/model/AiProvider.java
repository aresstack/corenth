package de.bund.zrb.model;

public enum AiProvider {
    DISABLED("Deaktiviert"),
    OLLAMA("Ollama"),
    CLOUD("Public Cloud"),
    PRIVATE_CLOUD("Private Cloud"),
    LOCAL_AI("LocalAI"),
    LLAMA_CPP_SERVER("llama.cpp Server"),
    CUSTOM("Custom"),
    ONNX_RUNTIME("ONNX Runtime"); // Lokale Inferenz via ONNX Runtime (Phi-3/Phi-4 etc.)

    private final String displayName;

    AiProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
