package com.resumeai.application.settings;

import com.resumeai.infrastructure.config.ResumeAiProperties;
import org.springframework.core.env.Environment;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final ResumeAiProperties properties;
    private final Environment environment;
    private final MultipartProperties multipartProperties;

    public SettingsService(
            ResumeAiProperties properties,
            Environment environment,
            MultipartProperties multipartProperties
    ) {
        this.properties = properties;
        this.environment = environment;
        this.multipartProperties = multipartProperties;
    }

    public SettingsResponse getSettings() {
        return new SettingsResponse(
                aiSettings(),
                new SettingsResponse.RagSettings(
                        properties.rag().embeddingDimensions(),
                        properties.rag().topK(),
                        properties.rag().similarityThreshold()
                ),
                new SettingsResponse.StorageSettings(
                        properties.storage().endpoint(),
                        properties.storage().bucket(),
                        hasText(properties.storage().accessKey()),
                        hasText(properties.storage().secretKey())
                ),
                new SettingsResponse.RedisSettings(
                        properties.redis().enabled(),
                        properties.redis().host(),
                        properties.redis().port(),
                        hasText(environment.getProperty("spring.data.redis.password"))
                ),
                new SettingsResponse.UploadSettings(
                        multipartProperties.getMaxFileSize().toString(),
                        multipartProperties.getMaxRequestSize().toString()
                )
        );
    }

    private SettingsResponse.AiSettings aiSettings() {
        return new SettingsResponse.AiSettings(
                properties.llm().provider(),
                environment.getProperty("spring.ai.model.chat", "unknown"),
                environment.getProperty("spring.ai.model.embedding", "unknown"),
                environment.getProperty("spring.ai.openai.base-url", ""),
                environment.getProperty("spring.ai.openai.chat.base-url", ""),
                environment.getProperty("spring.ai.openai.embedding.base-url", ""),
                environment.getProperty("spring.ai.openai.chat.options.model", ""),
                environment.getProperty("spring.ai.openai.embedding.options.model", ""),
                hasText(environment.getProperty("spring.ai.openai.api-key")),
                hasText(environment.getProperty("spring.ai.openai.chat.api-key")),
                hasText(environment.getProperty("spring.ai.openai.embedding.api-key")),
                environment.getProperty("spring.ai.ollama.base-url", ""),
                environment.getProperty("spring.ai.ollama.chat.model", ""),
                environment.getProperty("spring.ai.ollama.embedding.model", "")
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
