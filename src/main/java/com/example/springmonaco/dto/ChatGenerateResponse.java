package com.example.springmonaco.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatGenerateResponse {

    private String reply;
    private List<GeneratedFile> files = new ArrayList<>();
    private boolean needsGithubLogin;
    private String githubLoginUrl;
    private String publishResult;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<GeneratedFile> getFiles() {
        return files;
    }

    public void setFiles(List<GeneratedFile> files) {
        this.files = files;
    }

    public boolean isNeedsGithubLogin() {
        return needsGithubLogin;
    }

    public void setNeedsGithubLogin(boolean needsGithubLogin) {
        this.needsGithubLogin = needsGithubLogin;
    }

    public String getGithubLoginUrl() {
        return githubLoginUrl;
    }

    public void setGithubLoginUrl(String githubLoginUrl) {
        this.githubLoginUrl = githubLoginUrl;
    }

    public String getPublishResult() {
        return publishResult;
    }

    public void setPublishResult(String publishResult) {
        this.publishResult = publishResult;
    }
}
