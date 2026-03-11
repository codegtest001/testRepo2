package com.example.springmonaco.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinition;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinitionFunction;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatCompletionsToolCall;
import com.azure.ai.openai.models.ChatCompletionsToolDefinition;
import com.azure.ai.openai.models.ChatCompletionsToolSelection;
import com.azure.ai.openai.models.ChatCompletionsToolSelectionPreset;
import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestToolMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import com.example.springmonaco.dto.ChatGenerateResponse;
import com.example.springmonaco.dto.GeneratedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class VibeCodingService {

    private static final String ROOT_DIR = "generated-project";
    private static final String SESSION_GITHUB_TOKEN = "github_token";
    private static final String SESSION_PENDING_PROMPT = "pending_publish_prompt";
    private static final String SESSION_PENDING_FILES = "pending_publish_files";

    private final FileSystemService fileSystemService;
    private final GithubService githubService;
    private final HttpSession session;
    private final ObjectMapper objectMapper;

    @Value("${ai.apiKey5mini:}")
    private String apiKey5mini;

    @Value("${ai.endpoint5mini:}")
    private String endpoint5mini;

    @Value("${ai.deploymentName5mini:}")
    private String deploymentName5mini;

    public VibeCodingService(
        FileSystemService fileSystemService,
        GithubService githubService,
        HttpSession session,
        ObjectMapper objectMapper
    ) {
        this.fileSystemService = fileSystemService;
        this.githubService = githubService;
        this.session = session;
        this.objectMapper = objectMapper;
    }

    public ChatGenerateResponse generateProject(String prompt) throws IOException {
        validateAiConfig();

        String safePrompt = prompt == null || prompt.isBlank() ? "기본 랜딩 페이지를 생성해줘" : prompt.trim();
        OpenAIClient client = buildOpenAiClient();

        List<ChatRequestMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatRequestSystemMessage(buildSystemPrompt()));
        chatMessages.add(new ChatRequestUserMessage("Current date: " + LocalDate.now() + "\n요구사항: " + safePrompt));

        String rawAnswer = askLlm(client, chatMessages);
        LlmFilePayload payload = parsePayload(rawAnswer);

        List<GeneratedFile> generatedFiles = persistGeneratedFiles(payload.files);
        ChatGenerateResponse response = new ChatGenerateResponse();
        response.setReply(payload.reply == null || payload.reply.isBlank()
            ? "요구사항 기반으로 파일을 생성했습니다."
            : payload.reply);
        response.setFiles(generatedFiles);

        if (isPublishIntent(safePrompt)) {
            String githubToken = (String) session.getAttribute(SESSION_GITHUB_TOKEN);
            if (githubToken == null || githubToken.isBlank()) {
                session.setAttribute(SESSION_PENDING_PROMPT, safePrompt);
                session.setAttribute(SESSION_PENDING_FILES, objectMapper.writeValueAsString(generatedFiles));

                response.setNeedsGithubLogin(true);
                response.setGithubLoginUrl("/github/login");
                response.setReply(response.getReply() + "\nGitHub 퍼블리시 요청이 감지되었습니다. GitHub 로그인 후 자동으로 이어서 진행합니다.");
                return response;
            }

            String publishResult = publishGeneratedFiles(client, safePrompt, generatedFiles, githubToken);
            response.setPublishResult(publishResult);
            response.setReply(response.getReply() + "\n" + publishResult);
        }

        return response;
    }

    public ChatGenerateResponse resumePendingPublish() throws IOException {
        String githubToken = (String) session.getAttribute(SESSION_GITHUB_TOKEN);
        if (githubToken == null || githubToken.isBlank()) {
            throw new IllegalStateException("GitHub login is required");
        }

        String pendingPrompt = (String) session.getAttribute(SESSION_PENDING_PROMPT);
        String pendingFilesJson = (String) session.getAttribute(SESSION_PENDING_FILES);
        if (pendingPrompt == null || pendingFilesJson == null) {
            throw new IllegalStateException("No pending publish task found");
        }

        List<GeneratedFile> files = objectMapper.readerForListOf(GeneratedFile.class).readValue(pendingFilesJson);
        OpenAIClient client = buildOpenAiClient();
        String publishResult = publishGeneratedFiles(client, pendingPrompt, files, githubToken);

        session.removeAttribute(SESSION_PENDING_PROMPT);
        session.removeAttribute(SESSION_PENDING_FILES);

        ChatGenerateResponse response = new ChatGenerateResponse();
        response.setFiles(files);
        response.setPublishResult(publishResult);
        response.setReply("GitHub 로그인 후 퍼블리시를 이어서 완료했습니다.\n" + publishResult);
        return response;
    }

    private List<GeneratedFile> persistGeneratedFiles(List<LlmGeneratedFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalStateException("LLM did not return any files");
        }

        List<GeneratedFile> validatedFiles = new ArrayList<>();
        for (LlmGeneratedFile file : files) {
            String safePath = sanitizePath(file.path);
            String content = file.content == null ? "" : file.content;
            fileSystemService.upsertFile(safePath, content);
            validatedFiles.add(new GeneratedFile(safePath, content));
        }

        return validatedFiles;
    }

    private String publishGeneratedFiles(OpenAIClient client, String prompt, List<GeneratedFile> files, String githubToken) throws IOException {
        List<ChatRequestMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatRequestSystemMessage(buildPublishSystemPrompt()));
        chatMessages.add(new ChatRequestUserMessage(
            "사용자 요청: " + prompt + "\n"
                + "아래 generated files를 GitHub 저장소에 반영해줘.\n"
                + objectMapper.writeValueAsString(files)
        ));

        String finalAnswer = "";

        for (int i = 0; i < 10; i++) {
            ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages);
            options.setTools(buildGithubTools());
            options.setToolChoice(new ChatCompletionsToolSelection(ChatCompletionsToolSelectionPreset.AUTO));
            options.setMaxCompletionTokens(4096);

            ChatCompletions chatCompletions = client.getChatCompletions(deploymentName5mini, options);
            ChatChoice choice = chatCompletions.getChoices().get(0);
            ChatResponseMessage responseMessage = choice.getMessage();

            if (responseMessage.getContent() != null
                && (responseMessage.getToolCalls() == null || responseMessage.getToolCalls().isEmpty())) {
                finalAnswer = responseMessage.getContent();
                break;
            }

            if (responseMessage.getToolCalls() != null && !responseMessage.getToolCalls().isEmpty()) {
                ChatRequestAssistantMessage assistantMessage = new ChatRequestAssistantMessage(responseMessage.getContent());
                assistantMessage.setToolCalls(responseMessage.getToolCalls());
                chatMessages.add(assistantMessage);

                for (ChatCompletionsToolCall toolCall : responseMessage.getToolCalls()) {
                    if (!(toolCall instanceof ChatCompletionsFunctionToolCall)) {
                        continue;
                    }

                    ChatCompletionsFunctionToolCall functionToolCall = (ChatCompletionsFunctionToolCall) toolCall;
                    String functionName = functionToolCall.getFunction().getName();
                    String toolCallId = functionToolCall.getId();
                    JsonNode argsNode = objectMapper.readTree(functionToolCall.getFunction().getArguments());

                    String toolResult = executeGithubTool(githubToken, functionName, argsNode);
                    chatMessages.add(new ChatRequestToolMessage(toolResult, toolCallId));
                }
            }
        }

        if (finalAnswer == null || finalAnswer.isBlank()) {
            finalAnswer = "GitHub 퍼블리시가 완료되었는지 확인이 필요합니다. 저장소 변경 내역을 확인하세요.";
        }
        return finalAnswer;
    }

    private String executeGithubTool(String githubToken, String functionName, JsonNode argsNode) {
        switch (functionName) {
            case "mcp_github_create_repo":
                return githubService.githubCreateRepo(githubToken, argsNode);
            case "mcp_github_write_file":
                return githubService.githubWriteFile(githubToken, argsNode);
            case "mcp_github_list_repos":
                return githubService.githubListRepos(githubToken, argsNode);
            default:
                return "{\"status\":\"error\",\"message\":\"Unsupported tool: " + functionName + "\"}";
        }
    }

    private OpenAIClient buildOpenAiClient() {
        return new OpenAIClientBuilder()
            .credential(new AzureKeyCredential(apiKey5mini))
            .endpoint(endpoint5mini)
            .buildClient();
    }

    private String askLlm(OpenAIClient client, List<ChatRequestMessage> chatMessages) {
        try {
            ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages);
            options.setMaxCompletionTokens(4096);

            ChatCompletions completions = client.getChatCompletions(deploymentName5mini, options);
            StringBuilder answer = new StringBuilder();
            for (ChatChoice choice : completions.getChoices()) {
                ChatResponseMessage message = choice.getMessage();
                if (message != null && message.getContent() != null) {
                    answer.append(message.getContent());
                }
            }

            if (answer.length() == 0) {
                throw new IllegalStateException("Empty LLM response");
            }
            return answer.toString();
        } catch (HttpResponseException ex) {
            throw new IllegalStateException("LLM request failed: " + ex.getMessage(), ex);
        }
    }

    private LlmFilePayload parsePayload(String rawAnswer) throws IOException {
        String jsonText = trimCodeFence(rawAnswer);
        int firstBrace = jsonText.indexOf('{');
        int lastBrace = jsonText.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            throw new IllegalStateException("LLM response is not valid JSON: " + rawAnswer);
        }

        String jsonObject = jsonText.substring(firstBrace, lastBrace + 1);
        LlmFilePayload payload = objectMapper.readValue(jsonObject, LlmFilePayload.class);
        if (payload.files == null) {
            payload.files = new ArrayList<>();
        }
        return payload;
    }

    private String trimCodeFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > -1) {
                return trimmed.substring(firstNewline + 1, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Generated file path is empty");
        }

        String normalized = path.replace("\\", "/").trim();
        if (!normalized.startsWith(ROOT_DIR + "/")) {
            throw new IllegalStateException("Generated file must be under " + ROOT_DIR + ": " + normalized);
        }
        if (normalized.contains("..")) {
            throw new IllegalStateException("Invalid relative path: " + normalized);
        }
        return normalized;
    }

    private boolean isPublishIntent(String prompt) {
        String lower = prompt.toLowerCase();
        return lower.contains("commit") || lower.contains("push") || lower.contains("publish")
            || lower.contains("커밋") || lower.contains("푸시") || lower.contains("퍼블리시") || lower.contains("깃허브");
    }

    private void validateAiConfig() {
        if (isBlank(apiKey5mini) || isBlank(endpoint5mini) || isBlank(deploymentName5mini)) {
            throw new IllegalStateException("AI config is missing. Set ai.apiKey5mini, ai.endpoint5mini, ai.deploymentName5mini");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildSystemPrompt() {
        return "You are a vibe-coding assistant. Return ONLY valid JSON without markdown. "
            + "Output schema: {\"reply\":\"string\",\"files\":[{\"path\":\"generated-project/<file>\",\"content\":\"...\"}]}. "
            + "Rules: 1) Every file path must start with generated-project/. "
            + "2) Provide complete runnable files. "
            + "3) Include at least generated-project/index.html, generated-project/style.css, generated-project/app.js. "
            + "4) Reply must be in Korean.";
    }

    private String buildPublishSystemPrompt() {
        return "You are a GitHub publishing assistant. Use tools to publish provided files to GitHub. "
            + "If repository is unclear, first call mcp_github_list_repos to inspect available repos, then choose one and call mcp_github_write_file for each file. "
            + "Always include commit message in Korean. "
            + "When done, return concise Korean summary.";
    }

    private static class LlmFilePayload {
        public String reply;
        public List<LlmGeneratedFile> files;
    }

    private static class LlmGeneratedFile {
        public String path;
        public String content;
    }

    //tool github
    private List<ChatCompletionsToolDefinition> buildGithubTools() {

        List<ChatCompletionsToolDefinition> tools = new ArrayList<>();
        ChatCompletionsFunctionToolDefinitionFunction createRepoFunction =
            new ChatCompletionsFunctionToolDefinitionFunction("mcp_github_create_repo")
                .setDescription("GitHub repository 생성")
                .setParameters(BinaryData.fromObject(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "owner", Map.of("type", "string"),
                        "repo", Map.of("type", "string"),
                        "description", Map.of("type", "string")
                    )
                )));

        tools.add(new ChatCompletionsFunctionToolDefinition(createRepoFunction));

        ChatCompletionsFunctionToolDefinitionFunction fileWriteFunction =
            new ChatCompletionsFunctionToolDefinitionFunction("mcp_github_write_file")
                .setDescription("GitHub repository 파일 생성 또는 수정")
                .setParameters(BinaryData.fromObject(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "owner", Map.of("type", "string"),
                        "repo", Map.of("type", "string"),
                        "path", Map.of("type", "string"),
                        "content", Map.of("type", "string"),
                        "message", Map.of("type", "string")
                    ),
                    "required", List.of("repo", "path", "content")
                )));

        tools.add(new ChatCompletionsFunctionToolDefinition(fileWriteFunction));

        ChatCompletionsFunctionToolDefinitionFunction listReposFunction =
            new ChatCompletionsFunctionToolDefinitionFunction("mcp_github_list_repos")
                .setDescription("GitHub repository 목록 조회")
                .setParameters(BinaryData.fromObject(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "owner", Map.of("type", "string")
                    )
                )));

        tools.add(new ChatCompletionsFunctionToolDefinition(listReposFunction));
        return tools;
    }
}