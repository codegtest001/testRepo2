package com.example.springmonaco.controller;

import com.example.springmonaco.service.GithubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@RequestMapping("/github")
public class GitHubController {

    private static final String SESSION_GITHUB_TOKEN = "github_token";
    private static final String SESSION_GITHUB_STATE = "github_oauth_state";

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.rediract-url:}")
    private String redirectBaseUrl;

    private final HttpSession session;
    private final GithubService githubService;

    public GitHubController(HttpSession session, GithubService githubService) {
        this.session = session;
        this.githubService = githubService;
    }

    @GetMapping("/login")
    public String githubLogin() {
        if (clientId == null || clientId.isBlank() || redirectBaseUrl == null || redirectBaseUrl.isBlank()) {
            throw new IllegalStateException("GitHub OAuth config is missing");
        }

        String state = UUID.randomUUID().toString();
        session.setAttribute(SESSION_GITHUB_STATE, state);

        String callbackUrl = redirectBaseUrl + "github/callback";
        String url = "https://github.com/login/oauth/authorize"
            + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&scope=repo"
            + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
            + "&redirect_uri=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);

        return "redirect:" + url;
    }

    @GetMapping("/callback")
    public ResponseEntity<String> githubCallback(@RequestParam String code, @RequestParam(required = false) String state) {
        String expectedState = (String) session.getAttribute(SESSION_GITHUB_STATE);
        if (expectedState == null || state == null || !expectedState.equals(state)) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_HTML)
                .body("<html><body><script>alert('GitHub 로그인 state 검증에 실패했습니다.');window.close();</script></body></html>");
        }

        String accessToken = githubService.getGithubAccessToken(code);
        session.setAttribute(SESSION_GITHUB_TOKEN, accessToken);
        session.removeAttribute(SESSION_GITHUB_STATE);

        String html = "<html><body><script>"
            + "if(window.opener){window.opener.postMessage({type:'github-login-success'}, window.location.origin);}"
            + "window.close();"
            + "</script><p>GitHub login success. You can close this window.</p></body></html>";

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        String token = (String) session.getAttribute(SESSION_GITHUB_TOKEN);
        return ResponseEntity.ok().body(java.util.Map.of("loggedIn", token != null && !token.isBlank()));
    }
}