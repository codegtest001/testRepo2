package com.example.springmonaco.controller;

import com.example.springmonaco.dto.ChatGenerateRequest;
import com.example.springmonaco.dto.CreateDirectoryRequest;
import com.example.springmonaco.dto.CreateFileRequest;
import com.example.springmonaco.dto.SaveFileRequest;
import com.example.springmonaco.service.FileSystemService;
import com.example.springmonaco.service.VibeCodingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ApiController {

    private final FileSystemService fileSystemService;
    private final VibeCodingService vibeCodingService;

    public ApiController(FileSystemService fileSystemService, VibeCodingService vibeCodingService) {
        this.fileSystemService = fileSystemService;
        this.vibeCodingService = vibeCodingService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("ok", true);
        body.put("repoRoot", fileSystemService.getRepoRoot().toString());
        return body;
    }

    @GetMapping("/api/tree")
    public ResponseEntity<?> tree() {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("root", fileSystemService.getRepoRoot().toString());
            body.put("tree", fileSystemService.getTree());
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/file")
    public ResponseEntity<?> file(@RequestParam("path") String path) {
        try {
            return ResponseEntity.ok(Map.of("path", path, "content", fileSystemService.readFile(path)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/api/file")
    public ResponseEntity<?> save(@RequestBody SaveFileRequest request) {
        try {
            if (request.getPath() == null || request.getContent() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
            }
            fileSystemService.writeFile(request.getPath(), request.getContent());
            return ResponseEntity.ok(Map.of("success", true, "path", request.getPath()));
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/api/file")
    public ResponseEntity<?> create(@RequestBody CreateFileRequest request) {
        try {
            if (request.getPath() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
            }
            fileSystemService.createFile(request.getPath(), request.getContent());
            return ResponseEntity.ok(Map.of("success", true, "path", request.getPath()));
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/api/file")
    public ResponseEntity<?> delete(@RequestParam("path") String path) {
        try {
            fileSystemService.deleteFile(path);
            return ResponseEntity.ok(Map.of("success", true, "path", path));
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/api/dir")
    public ResponseEntity<?> createDir(@RequestBody CreateDirectoryRequest request) {
        try {
            if (request.getPath() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
            }
            fileSystemService.createDirectory(request.getPath());
            return ResponseEntity.ok(Map.of("success", true, "path", request.getPath()));
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/api/dir")
    public ResponseEntity<?> deleteDir(@RequestParam("path") String path) {
        try {
            fileSystemService.deleteDirectory(path);
            return ResponseEntity.ok(Map.of("success", true, "path", path));
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/api/chat/generate")
    public ResponseEntity<?> chatGenerate(@RequestBody ChatGenerateRequest request) {
        try {
            return ResponseEntity.ok(vibeCodingService.generateProject(request.getPrompt()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/api/chat/resume-publish")
    public ResponseEntity<?> resumePublish() {
        try {
            return ResponseEntity.ok(vibeCodingService.resumePendingPublish());
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/api/md/export")
    public ResponseEntity<?> exportMarkdown() {
        try {
            List<String> files = fileSystemService.exportMarkdownFiles(10);
            return ResponseEntity.ok(Map.of("success", true, "files", files));
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/preview")
    public ResponseEntity<?> preview(@RequestParam(value = "path", defaultValue = "generated-project/index.html") String path) {
        try {
            String html = fileSystemService.readFile(path);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
                .body(html);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/raw")
    public ResponseEntity<?> raw(@RequestParam("path") String path) {
        try {
            String content = fileSystemService.readFile(path);
            String type = MediaType.TEXT_PLAIN_VALUE;
            if (path.endsWith(".css")) {
                type = "text/css";
            } else if (path.endsWith(".js")) {
                type = "application/javascript";
            } else if (path.endsWith(".html")) {
                type = MediaType.TEXT_HTML_VALUE;
            }

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, type + ";charset=UTF-8")
                .body(content);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
