package com.isekai.ssp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures ChatClient beans for both OpenAI and Anthropic.
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
}
