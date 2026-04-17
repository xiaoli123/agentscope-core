/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.formatter.dashscope.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * DashScope usage statistics DTO.
 *
 * <p>This class represents token usage statistics in a DashScope API response.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "input_tokens": 10,
 *   "output_tokens": 20,
 *   "total_tokens": 30
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashScopeUsage {

    /** Number of tokens in the input. */
    @JsonProperty("input_tokens")
    private Integer inputTokens;

    /** Number of tokens in the output. */
    @JsonProperty("output_tokens")
    private Integer outputTokens;

    /** Total tokens (input + output). */
    @JsonProperty("total_tokens")
    private Integer totalTokens;

    /** Image tokens (for multimodal). */
    @JsonProperty("image_tokens")
    private Integer imageTokens;

    /** Video tokens (for multimodal). */
    @JsonProperty("video_tokens")
    private Integer videoTokens;

    /** Audio tokens (for multimodal). */
    @JsonProperty("audio_tokens")
    private Integer audioTokens;

    /** Input tokens details (for multimodal and cache info). */
    @JsonProperty("input_tokens_details")
    private Map<String, Object> inputTokensDetails;

    /** Output tokens details (for text/reasoning breakdown). */
    @JsonProperty("output_tokens_details")
    private Map<String, Object> outputTokensDetails;

    /** Prompt tokens details (for cache info). */
    @JsonProperty("prompt_tokens_details")
    private Map<String, Object> promptTokensDetails;

    public DashScopeUsage() {}

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getImageTokens() {
        return imageTokens;
    }

    public void setImageTokens(Integer imageTokens) {
        this.imageTokens = imageTokens;
    }

    public Integer getVideoTokens() {
        return videoTokens;
    }

    public void setVideoTokens(Integer videoTokens) {
        this.videoTokens = videoTokens;
    }

    public Integer getAudioTokens() {
        return audioTokens;
    }

    public void setAudioTokens(Integer audioTokens) {
        this.audioTokens = audioTokens;
    }

    public Map<String, Object> getInputTokensDetails() {
        return inputTokensDetails;
    }

    public void setInputTokensDetails(Map<String, Object> inputTokensDetails) {
        this.inputTokensDetails = inputTokensDetails;
    }

    public Map<String, Object> getOutputTokensDetails() {
        return outputTokensDetails;
    }

    public void setOutputTokensDetails(Map<String, Object> outputTokensDetails) {
        this.outputTokensDetails = outputTokensDetails;
    }

    public Map<String, Object> getPromptTokensDetails() {
        return promptTokensDetails;
    }

    public void setPromptTokensDetails(Map<String, Object> promptTokensDetails) {
        this.promptTokensDetails = promptTokensDetails;
    }
}
