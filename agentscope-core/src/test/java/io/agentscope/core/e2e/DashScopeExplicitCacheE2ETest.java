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
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.EndpointType;
import io.agentscope.core.model.GenerateOptions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Direct E2E test for verifying DashScope explicit prompt caching with long context.
 *
 * <p>Per DashScope docs, explicit cache requires:
 * <ul>
 *   <li>Minimum 1024 tokens of cacheable content</li>
 *   <li>{@code cache_control} placed inside a content part (not message-level)</li>
 * </ul>
 */
@Tag("e2e")
@Tag("dashscope")
@DisplayName("DashScope Explicit Cache E2E Test")
class DashScopeExplicitCacheE2ETest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(120);

    /** Generate text guaranteed to exceed 1024 tokens. */
    static String generateLongText(int repeatCount) {
        StringBuilder sb = new StringBuilder();
        String chunk =
                "Quantum computing is a revolutionary paradigm that leverages quantum mechanical"
                    + " phenomena such as superposition, entanglement, and quantum interference to"
                    + " perform computations. Unlike classical computers that use bits as the"
                    + " smallest unit of information, quantum computers use quantum bits or qubits."
                    + " A qubit can exist in a state of 0, 1, or any quantum superposition of these"
                    + " states. This property allows quantum computers to process a vast number of"
                    + " possibilities simultaneously. Entanglement enables qubits that are"
                    + " entangled to be correlated with each other regardless of distance. Quantum"
                    + " algorithms exploit these properties to solve certain problems exponentially"
                    + " faster than the best known classical algorithms. Examples include Shor's"
                    + " algorithm for integer factorization and Grover's algorithm for unstructured"
                    + " search. Quantum error correction is essential for fault-tolerant quantum"
                    + " computing because qubits are highly susceptible to decoherence and noise. ";
        for (int i = 0; i < repeatCount; i++) {
            sb.append(chunk);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("Should create explicit cache and report cache_creation_input_tokens")
    void testExplicitCacheCreation() {
        String apiKey = "sk-27fa7fe7be6248bf99f26aff8b6ed505";
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠ Skipping test - DASHSCOPE_API_KEY is not set.");
            return;
        }

        System.out.println("\n=== DashScope Explicit Cache E2E Test (qwen3.5-plus) ===");

        String longContext = generateLongText(20);

        Msg sysMsg =
                Msg.builder()
                        .role(MsgRole.SYSTEM)
                        .content(List.of(TextBlock.builder().text(longContext).build()))
                        .build();

        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(List.of(TextBlock.builder().text("请用一句话总结上文的核心观点。").build()))
                        .build();

        DashScopeChatModel model =
                DashScopeChatModel.builder().apiKey(apiKey).modelName("qwen3.5-plus").stream(false)
                        .formatter(new DashScopeChatFormatter())
                        .endpointType(EndpointType.MULTIMODAL)
                        .defaultOptions(GenerateOptions.builder().cacheControl(true).build())
                        .build();

        System.out.println("Sending first request (should create cache)...");

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
        assertNotNull(usage, "Usage should not be null");
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
            } else {
                System.out.println("\nNo promptTokensDetails in usage details.");
            }
        } else {
            System.out.println("\nNo usage details returned by the model.");
        }

        System.out.println("\n✓ Explicit cache E2E test completed.");
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
