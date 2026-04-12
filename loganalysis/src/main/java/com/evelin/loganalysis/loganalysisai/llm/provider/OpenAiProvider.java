package com.evelin.loganalysis.loganalysisai.llm.provider;

import com.evelin.loganalysis.loganalysisai.llm.LlmProvider;
import com.evelin.loganalysis.loganalysisai.llm.LlmRequest;
import com.evelin.loganalysis.loganalysisai.llm.LlmResponse;
import com.evelin.loganalysis.loganalysisai.llm.config.DynamicLlmConfig;
import com.evelin.loganalysis.loganalysisai.llm.config.DynamicLlmConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * OpenAI LLM 提供商实现
 *
 * @author Evelin
 */
@Slf4j
@Component
public class OpenAiProvider implements LlmProvider {
    
    private static DynamicLlmConfigService dynamicConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String OPENAI_CHAT_COMPLETION_API = "/v1/chat/completions";
    private static final String ARK_CHAT_COMPLETION_API = "/chat/completions";
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    public OpenAiProvider(DynamicLlmConfigService dynamicConfigService) {
        OpenAiProvider.dynamicConfigService = dynamicConfigService;
    }
    
    @Override
    public String getName() {
        return "openai";
    }
    
    @Override
    public LlmResponse chat(LlmRequest request) {
        DynamicLlmConfig dynamicConfig = dynamicConfigService.getActiveConfig();
        return chat(request, dynamicConfig);
    }

    private LlmResponse chat(LlmRequest request, DynamicLlmConfig dynamicConfig) {
        long startTime = System.currentTimeMillis();
        LlmRequest safeRequest = request != null ? request : new LlmRequest();

        if (dynamicConfig == null || !dynamicConfig.isValid()) {
            LlmResponse error = new LlmResponse();
            error.setStatus("error");
            error.setErrorMessage("LLM 配置无效或未配置");
            return error;
        }
        
        String apiKey = dynamicConfig.getApiKey();
        String model = safeRequest.getModel() != null ? safeRequest.getModel() : dynamicConfig.getModel();
        double temperature = safeRequest.getTemperature() > 0 ? safeRequest.getTemperature() : dynamicConfig.getTemperature();
        int maxTokens = safeRequest.getMaxTokens() > 0 ? safeRequest.getMaxTokens() : dynamicConfig.getMaxTokens();
        int timeout = dynamicConfig.getTimeout();
        String endpoint = dynamicConfig.getEndpoint();
        
        String provider = inferProviderFromEndpoint(endpoint);
        
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);
            
            // 构建消息列表
            List<Map<String, String>> messages = new ArrayList<>();
            
            // 添加系统提示
            if (safeRequest.getSystemPrompt() != null && !safeRequest.getSystemPrompt().isEmpty()) {
                messages.add(Map.of("role", "system", "content", safeRequest.getSystemPrompt()));
            } else {
                messages.add(Map.of("role", "system", "content", getDefaultSystemPrompt()));
            }
            
            // 添加历史消息
            if (safeRequest.getMessages() != null && !safeRequest.getMessages().isEmpty()) {
                for (LlmRequest.ChatMessage msg : safeRequest.getMessages()) {
                    messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                }
            }
            
            // 添加当前提示
            if (safeRequest.getPrompt() != null && !safeRequest.getPrompt().isEmpty()) {
                messages.add(Map.of("role", "user", "content", safeRequest.getPrompt()));
            }
            
            requestBody.put("messages", messages);
            
            // 发送请求
            String requestJson = objectMapper.writeValueAsString(requestBody);
            
            // 根据提供商确定基础 URL
            String baseUrl = getBaseUrl(provider, endpoint);
            String apiPath = resolveChatCompletionPath(baseUrl);
            String fullUrl = baseUrl + apiPath;
            
            log.info("调用 LLM API, 提供商: {}, 模型: {}", provider, model);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            
            HttpResponse<String> response = httpClient.send(httpRequest, 
                    HttpResponse.BodyHandlers.ofString());
            
            long responseTime = System.currentTimeMillis() - startTime;

            String body = response.body();
            
            // 解析响应
            if (response.statusCode() == 200) {
                return parseSuccessResponse(response.body(), model, responseTime);
            } else {
                LlmResponse error = parseErrorResponse(response.body(), responseTime);
                // 补充 statusCode，避免日志里只看到 "HTTP "
                if (error.getErrorMessage() == null || error.getErrorMessage().isBlank()) {
                    error.setErrorMessage("HTTP " + response.statusCode());
                } else if (!error.getErrorMessage().startsWith("HTTP")) {
                    error.setErrorMessage("HTTP " + response.statusCode() + " - " + error.getErrorMessage());
                }
                return error;
            }
            
        } catch (Exception e) {
            log.error("OpenAI API 调用失败: {}", e.getMessage(), e);
            LlmResponse errorResponse = new LlmResponse();
            errorResponse.setStatus("error");
            errorResponse.setErrorMessage(e.getMessage());
            errorResponse.setResponseTimeMs(System.currentTimeMillis() - startTime);
            return errorResponse;
        }
    }

    private String resolveChatCompletionPath(String baseUrl) {
        if (baseUrl == null) {
            return OPENAI_CHAT_COMPLETION_API;
        }
        String lower = baseUrl.toLowerCase();
        if (lower.endsWith("/v1") || lower.contains("/compatible-mode/v1")) {
            return "/chat/completions";
        }
        // Ark(豆包) baseUrl 通常包含 /api/v3
        if (lower.contains("volces.com") && lower.contains("/api/v3")) {
            return ARK_CHAT_COMPLETION_API;
        }
        return OPENAI_CHAT_COMPLETION_API;
    }
    
    /**
     * 根据提供商获取基础 URL
     */
    private String getBaseUrl(String provider, String customEndpoint) {
        // 如果有自定义端点，优先使用
        if (customEndpoint != null && !customEndpoint.isEmpty()) {
            String normalized = customEndpoint.trim();
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://" + normalized;
            }
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
        
        // 根据提供商返回默认端点
        return switch (provider.toLowerCase()) {
            case "deepseek" -> DEEPSEEK_BASE_URL;
            case "doubao", "豆包", "ark" -> DOUBAO_BASE_URL;
            default -> DEFAULT_BASE_URL;
        };
    }
    
    /**
     * 根据端点推断提供商
     */
    private String inferProviderFromEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "openai";
        }
        String lower = endpoint.toLowerCase();
        if (lower.contains("deepseek")) {
            return "deepseek";
        } else if (lower.contains("volces.com") || lower.contains("ark.cn-beijing")) {
            return "doubao";
        } else if (lower.contains("azure")) {
            return "azure";
        }
        return "openai";
    }
    
    @Override
    public boolean validateApiKey() {
        return validateApiKey(dynamicConfigService.getActiveConfig());
    }

    public boolean validateApiKey(DynamicLlmConfig dynamicConfig) {
        if (dynamicConfig == null || !dynamicConfig.isValid()) {
            return false;
        }

        try {
            LlmRequest request = new LlmRequest();
            request.setModel(dynamicConfig.getModel());
            request.setPrompt("Hello");
            request.setTemperature(0);
            request.setMaxTokens(1);

            LlmResponse response = chat(request, dynamicConfig);
            return response.isSuccess();
        } catch (Exception e) {
            log.warn("API 密钥验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析成功响应
     */
    private LlmResponse parseSuccessResponse(String responseBody, String model, long responseTime) 
            throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode choicesNode = rootNode.get("choices");
        
        LlmResponse response = new LlmResponse();
        response.setStatus("success");
        response.setResponseTimeMs(responseTime);
        
        if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
            JsonNode firstChoice = choicesNode.get(0);
            JsonNode messageNode = firstChoice.get("message");
            if (messageNode != null) {
                response.setContent(messageNode.get("content").asText());
            }
        }
        
        // 解析 token 使用量
        JsonNode usageNode = rootNode.get("usage");
        if (usageNode != null) {
            response.setPromptTokens(usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0);
            response.setCompletionTokens(usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0);
            response.setTokensUsed(response.getPromptTokens() + response.getCompletionTokens());
        }
        
        response.setModel(model != null ? model : "gpt-3.5-turbo");
        
        return response;
    }
    
    /**
     * 解析错误响应
     */
    private LlmResponse parseErrorResponse(String responseBody, long responseTime) {
        LlmResponse response = new LlmResponse();
        response.setStatus("error");
        response.setResponseTimeMs(responseTime);
        
        try {
            if (responseBody == null || responseBody.isBlank()) {
                response.setErrorMessage("Empty response body");
                return response;
            }
            JsonNode errorNode = objectMapper.readTree(responseBody);
            if (errorNode.has("error")) {
                JsonNode error = errorNode.get("error");
                response.setErrorMessage(error.has("message") ? error.get("message").asText() : "Unknown error");
            } else {
                response.setErrorMessage("HTTP " + responseBody);
            }
        } catch (Exception e) {
            response.setErrorMessage("HTTP Error: " + responseBody);
        }
        
        return response;
    }

    /**
     * 获取默认系统提示
     */
    private String getDefaultSystemPrompt() {
        return "你是一个专业的系统运维专家，擅长分析日志、诊断问题并提供解决方案。" +
               "请用中文回复，分析问题时要条理清晰，给出的建议要具体可操作。";
    }
}
