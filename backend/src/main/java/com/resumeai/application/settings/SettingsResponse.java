package com.resumeai.application.settings;

public record SettingsResponse(
        AiSettings ai,
        RagSettings rag,
        StorageSettings storage,
        RedisSettings redis,
        UploadSettings upload
) {
    public record AiSettings(
            String provider,
            String springChatModel,
            String springEmbeddingModel,
            String openAiBaseUrl,
            String openAiChatBaseUrl,
            String openAiEmbeddingBaseUrl,
            String openAiChatModel,
            String openAiEmbeddingModel,
            boolean openAiApiKeyConfigured,
            boolean openAiChatApiKeyConfigured,
            boolean openAiEmbeddingApiKeyConfigured,
            String ollamaBaseUrl,
            String ollamaChatModel,
            String ollamaEmbeddingModel
    ) {
    }

    public record RagSettings(
            int embeddingDimensions,
            int topK,
            double similarityThreshold
    ) {
    }

    public record StorageSettings(
            String endpoint,
            String bucket,
            boolean accessKeyConfigured,
            boolean secretKeyConfigured
    ) {
    }

    public record RedisSettings(
            boolean enabled,
            String host,
            int port,
            boolean passwordConfigured
    ) {
    }

    public record UploadSettings(
            String maxFileSize,
            String maxRequestSize
    ) {
    }
}
