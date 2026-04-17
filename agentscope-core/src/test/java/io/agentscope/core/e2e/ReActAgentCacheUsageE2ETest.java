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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.test.TestUtils;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.EndpointType;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E test for verifying cache token usage reporting from DashScope via ReActAgent.
 *
 * <p>This test:
 * <ul>
 *   <li>Uses the real DashScope API with model {@code qwen3.6-plus}</li>
 *   <li>Enables prompt caching via {@code GenerateOptions#cacheControl(true)}</li>
 *   <li>Runs a simple ReActAgent task</li>
 *   <li>Prints cache-related usage details (cached_tokens, cache_creation, etc.)</li>
 * </ul>
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>{@code ENABLE_E2E_TESTS=true}</li>
 *   <li>{@code DASHSCOPE_API_KEY} environment variable set</li>
 * </ul>
 */
@Tag("e2e")
@Tag("dashscope")
// @ExtendWith(E2ETestCondition.class)
@DisplayName("ReActAgent DashScope Cache Usage E2E Test")
class ReActAgentCacheUsageE2ETest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(120);

    @Test
    @DisplayName("Should print cache token details after ReActAgent execution")
    void testCacheTokenUsageWithReActAgent() {
        String apiKey = "sk-27fa7fe7be6248bf99f26aff8b6ed505";
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠ Skipping test - DASHSCOPE_API_KEY is not set.");
            return;
        }

        System.out.println("\n=== ReActAgent Cache Usage E2E Test (qwen3.5-plus) ===");

        // Build model with cacheControl enabled
        DashScopeChatModel model =
                DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("qwen3.5-plus")
                        .endpointType(EndpointType.MULTIMODAL)
                        .stream(false)
                        .defaultOptions(GenerateOptions.builder().cacheControl(true).build())
                        .build();

        ReActAgent agent =
                ReActAgent.builder()
                        .name("CacheTestAgent")
                        .sysPrompt(DashScopeExplicitCacheE2ETest.generateLongText(20))
                        .model(model)
                        .toolkit(new Toolkit())
                        .memory(new InMemoryMemory())
                        .maxIters(5)
                        .build();

        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                List.of(
                                        TextBlock.builder()
                                                .text("请用中文简要介绍量子计算的基本原理，并列举三个实际应用场景。")
                                                .build()))
                        .build();

        System.out.println("Sending request to DashScope qwen3.5-plus with cacheControl=true...");

        Msg response = agent.call(userMsg).block(TEST_TIMEOUT);

        assertNotNull(response, "Response should not be null");
        String responseText = TestUtils.extractTextContent(response);
        assertTrue(
                responseText != null && !responseText.isBlank(),
                "Response should have meaningful content");

        System.out.println("\n--- Agent Response ---");
        System.out.println(responseText);

        // Extract and print usage details
        ChatUsage usage = response.getChatUsage();
        if (usage != null) {
            System.out.println("\n--- Usage Summary ---");
            System.out.println("Input tokens : " + usage.getInputTokens());
            System.out.println("Output tokens: " + usage.getOutputTokens());
            System.out.println("Total tokens : " + usage.getTotalTokens());

            Map<String, Object> details = usage.getDetails();
            if (details != null && !details.isEmpty()) {
                System.out.println("\n--- Usage Details ---");
                printDetailsMap(details, "");

                // Specifically print cache info if present
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
                    System.out.println("\nNo prompt_tokens_details returned by the model.");
                }
            } else {
                System.out.println("\nNo usage details returned by the model.");
            }
        } else {
            System.out.println("\nNo usage information available in the response metadata.");
        }

        System.out.println("\n✓ Cache usage E2E test completed.");
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
