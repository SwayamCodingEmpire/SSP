package com.isekai.ssp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures ChatClient beans for OpenAI, Anthropic, and optionally vLLM.
 * The active provider is selected via ssp.ai.active-provider property.
 */
@Configuration
public class AiConfig {

    @Value("${ssp.ai.active-provider:openai}")
    private String activeProvider;

    @Bean
    @Qualifier("openaiChatClient")
    public ChatClient openaiChatClient(
            @Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    @Bean
    @Qualifier("anthropicChatClient")
    public ChatClient anthropicChatClient(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel).build();
    }

    @Bean
    @Primary
    public ChatClient primaryChatClient(
            @Qualifier("openaiChatClient") ChatClient openaiClient,
            @Qualifier("anthropicChatClient") ChatClient anthropicClient) {
        return "anthropic".equalsIgnoreCase(activeProvider)
                ? anthropicClient
                : openaiClient;
    }

    /**
     * vLLM ChatClient bean — uses Spring AI's OpenAI client pointed at vLLM's
     * OpenAI-compatible endpoint. Only created when ssp.ai.vllm.enabled=true.
     */
    @Bean
    @Qualifier("vllmChatClient")
    @ConditionalOnProperty(name = "ssp.ai.vllm.enabled", havingValue = "true")
    public ChatClient vllmChatClient(
            @Value("${ssp.ai.vllm.base-url:http://localhost:8000}") String baseUrl,
            @Value("${ssp.ai.vllm.model:mistral-7b-instruct}") String model,
            @Value("${ssp.ai.vllm.temperature:0.3}") double temperature) {
        OpenAiApi vllmApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey("not-needed")
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(vllmApi)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
