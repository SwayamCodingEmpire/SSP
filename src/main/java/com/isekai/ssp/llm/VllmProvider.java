package com.isekai.ssp.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * vLLM provider for local inference.
 * Uses Spring AI's OpenAI client pointed at vLLM's OpenAI-compatible endpoint.
 * Activated when ssp.ai.vllm.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "ssp.ai.vllm.enabled", havingValue = "true")
public class VllmProvider implements LlmProvider {

    private final ChatClient chatClient;

    public VllmProvider(@Qualifier("vllmChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getName() {
        return "vllm";
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }
}
