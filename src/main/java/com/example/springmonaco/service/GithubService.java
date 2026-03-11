package com.example.springmonaco.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GithubService {

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.client-secret:}")
    private String clientSecret;

    private final ObjectMapper objectMapper;

    public GithubService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getGithubAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("https://github.com/login/oauth/access_token", request, Map.class);

        Object token = response.getBody() == null ? null : response.getBody().get("access_token");
        if (token == null) {
            throw new IllegalStateException("GitHub access token is missing in callback response");
        }
        return token.toString();
    }

    public String getAuthenticatedOwner(String token) {
        try {
            URL url = new URL("https://api.github.com/user");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            String json = readBody(conn);
            JsonNode node = objectMapper.readTree(json);
            return node.get("login").asText();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve GitHub owner: " + ex.getMessage(), ex);
        }
    }

    public String githubListRepos(String token, JsonNode argsNode) {
        try {
            String owner = argsNode.path("owner").asText();
            String urlStr = owner.isEmpty()
                ? "https://api.github.com/user/repos?per_page=100"
                : "https://api.github.com/users/" + owner + "/repos?per_page=100";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            return readBody(conn);
        } catch (Exception ex) {
            return "{\"status\":\"error\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}";
        }
    }

    public String githubWriteFile(String token, JsonNode argsNode) {
        try {
            String repo = argsNode.get("repo").asText();
            String path = argsNode.get("path").asText();
            String content = argsNode.get("content").asText();
            String message = argsNode.path("message").asText("chore: update files from vibe coding");

            String owner = argsNode.path("owner").asText();
            if (owner == null || owner.isBlank()) {
                owner = getAuthenticatedOwner(token);
            }

            String sha = getFileShaIfExists(token, owner, repo, path);
            String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

            String urlStr = String.format("https://api.github.com/repos/%s/%s/contents/%s", owner, repo, path);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("message", message);
            body.put("content", encodedContent);
            if (sha != null) {
                body.put("sha", sha);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            return readBody(conn);
        } catch (Exception ex) {
            return "{\"status\":\"error\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}";
        }
    }

    public String githubCreateRepo(String token, JsonNode argsNode) {
        try {
            String repoName = argsNode.path("repo").asText();
            String description = argsNode.path("description").asText("");

            URL url = new URL("https://api.github.com/user/repos");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", repoName);
            body.put("description", description);
            body.put("auto_init", true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            return readBody(conn);
        } catch (Exception ex) {
            return "{\"status\":\"error\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}";
        }
    }

    private String getFileShaIfExists(String token, String owner, String repo, String path) {
        try {
            String urlStr = String.format("https://api.github.com/repos/%s/%s/contents/%s", owner, repo, path);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            int status = conn.getResponseCode();
            if (status == 200) {
                String json = readBody(conn);
                JsonNode node = objectMapper.readTree(json);
                return node.get("sha").asText();
            }
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    private String readBody(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}