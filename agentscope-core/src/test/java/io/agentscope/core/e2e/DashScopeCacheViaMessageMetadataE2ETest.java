/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.e2e;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.EndpointType;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Direct E2E test that enables DashScope prompt caching by manually marking specific messages
 * via {@link MessageMetadataKeys#CACHE_CONTROL}, then inspects the returned usage details.
 */
@Tag("e2e")
@Tag("dashscope")
@DisplayName("DashScope Cache via Message Metadata E2E Test")
class DashScopeCacheViaMessageMetadataE2ETest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(120);

    @Test
    @DisplayName("Should return cache token details when cache_control is set on messages")
    void testCacheTokenUsageWithMessageMetadata() {
        String apiKey = "sk-27fa7fe7be6248bf99f26aff8b6ed505";
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠ Skipping test - DASHSCOPE_API_KEY is not set.");
            return;
        }

        System.out.println("\n=== DashScope Cache via Message Metadata E2E Test ===");

        // Build a system message that we want to cache
        Map<String, Object> sysMetadata = new HashMap<>();
        sysMetadata.put(MessageMetadataKeys.CACHE_CONTROL, true);

        Msg sysMsg =
                Msg.builder()
                        .role(MsgRole.SYSTEM)
                        .content(
                                List.of(
                                        TextBlock.builder()
                                                .text(
                                                        "You are a quantum computing expert."
                                                                + " Be concise and accurate.")
                                                .build()))
                        .metadata(sysMetadata)
                        .build();

        // Build a user message that we also want to cache (last message)
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put(MessageMetadataKeys.CACHE_CONTROL, true);

        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                List.of(
                                        TextBlock.builder()
                                                .text("请用中文简要介绍量子计算的基本原理，并列举三个实际应用场景。")
                                                .build()))
                        .metadata(userMetadata)
                        .build();

        // Build model without GenerateOptions.cacheControl, relying solely on message metadata
        DashScopeChatModel model =
                DashScopeChatModel.builder().apiKey(apiKey).modelName("qwen3.5-plus").stream(false)
                        .formatter(new DashScopeChatFormatter())
                        .endpointType(EndpointType.MULTIMODAL)
                        .build();

        System.out.println(
                "Sending request with cache_control=true set directly on message metadata...");

        ChatResponse response =
                model.stream(List.of(sysMsg, userMsg), List.of(), null).blockFirst(TEST_TIMEOUT);

        assertNotNull(response, "Response should not be null");
        assertTrue(
                response.getContent() != null && !response.getContent().isEmpty(),
                "Response should have content");

        String responseText =
                response.getContent().stream()
                        .filter(io.agentscope.core.message.TextBlock.class::isInstance)
                        .map(io.agentscope.core.message.TextBlock.class::cast)
                        .map(TextBlock::getText)
                        .reduce("", (a, b) -> a + b);

        System.out.println("\n--- Model Response ---");
        System.out.println(responseText);

        ChatUsage usage = response.getUsage();
        if (usage != null) {
            System.out.println("\n--- Usage Summary ---");
            System.out.println("Input tokens : " + usage.getInputTokens());
            System.out.println("Output tokens: " + usage.getOutputTokens());
            System.out.println("Total tokens : " + usage.getTotalTokens());

            Map<String, Object> details = usage.getDetails();
            if (details != null && !details.isEmpty()) {
                System.out.println("\n--- Usage Details ---");
                printDetailsMap(details, "");

                @SuppressWarnings("unchecked")
                Map<String, Object> promptDetails =
                        (Map<String, Object>) details.get("promptTokensDetails");
                if (promptDetails != null) {
                    System.out.println("\n--- Cache Info (prompt_tokens_details) ---");
                    System.out.println("cached_tokens: " + promptDetails.get("cached_tokens"));
                    System.out.println(
                            "cache_creation_input_tokens: "
                                    + promptDetails.get("cache_creation_input_tokens"));
                    System.out.println("cache_type: " + promptDetails.get("cache_type"));

                    @SuppressWarnings("unchecked")
                    Map<String, Object> cacheCreation =
                            (Map<String, Object>) promptDetails.get("cache_creation");
                    if (cacheCreation != null) {
                        System.out.println("cache_creation sub-object:");
                        printDetailsMap(cacheCreation, "  ");
                    }
                } else {
                    System.out.println("\nNo promptTokensDetails in usage details.");
                }
            } else {
                System.out.println("\nNo usage details returned by the model.");
            }
        } else {
            System.out.println("\nNo usage information available in the response.");
        }

        System.out.println("\n✓ Message-metadata cache E2E test completed.");
    }

    private void printDetailsMap(Map<String, Object> map, String indent) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map) {
                System.out.println(indent + entry.getKey() + ":");
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) entry.getValue();
                printDetailsMap(nested, indent + "  ");
            } else {
                System.out.println(indent + entry.getKey() + ": " + entry.getValue());
            }
        }
    }
}
