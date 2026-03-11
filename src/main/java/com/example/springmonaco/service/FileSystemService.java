package com.example.springmonaco.service;

import com.example.springmonaco.config.AppProperties;
import com.example.springmonaco.dto.TreeNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FileSystemService {

    private static final Set<String> IGNORE_DIRS = Set.of(".git", "node_modules", "dist", "build", "target");

    private final Path repoRoot;

    public FileSystemService(AppProperties appProperties) {
        String configuredRoot = appProperties.getRepoRoot();
        if (configuredRoot == null || configuredRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("app.repo-root must be configured");
        }
        this.repoRoot = Paths.get(configuredRoot).toAbsolutePath().normalize();
    }

    public Path getRepoRoot() {
        return repoRoot;
    }

    public List<TreeNode> getTree() throws IOException {
        return buildTree(repoRoot, "");
    }

    public String readFile(String relativePath) throws IOException {
        Path safePath = resolveSafePath(relativePath);
        if (!Files.isRegularFile(safePath)) {
            throw new IllegalArgumentException("Not a file: " + relativePath);
        }
        return Files.readString(safePath, StandardCharsets.UTF_8);
    }

    public void writeFile(String relativePath, String content) throws IOException {
        Path safePath = resolveSafePath(relativePath);
        if (!Files.exists(safePath) || !Files.isRegularFile(safePath)) {
            throw new IllegalArgumentException("Not a file: " + relativePath);
        }
        Files.writeString(safePath, content, StandardCharsets.UTF_8);
        updateMarkdownForPath(relativePath, content);
    }

    public void upsertFile(String relativePath, String content) throws IOException {
        Path safePath = resolveSafePath(relativePath);
        Path parent = safePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(safePath, content, StandardCharsets.UTF_8);
    }

    public void createFile(String relativePath, String content) throws IOException {
        Path safePath = resolveSafePath(relativePath);
        if (Files.exists(safePath)) {
            throw new IllegalArgumentException("File already exists: " + relativePath);
        }
        Path parent = safePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(safePath, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public void deleteFile(String relativePath) throws IOException {
        Path safePath = resolveSafePath(relativePath);
        if (!Files.exists(safePath) || !Files.isRegularFile(safePath)) {
            throw new IllegalArgumentException("Not a file: " + relativePath);
        }
        Files.delete(safePath);
        updateMarkdownForPath(relativePath, null);
    }

    public void createDirectory(String relativePath) throws IOException {
        Path safePath = resolveSafePath(relativePath);
        if (Files.exists(safePath)) {
            throw new IllegalArgumentException("Directory already exists: " + relativePath);
        }
        Files.createDirectories(safePath);
    }

    public void deleteDirectory(String relativePath) throws IOException {
        Path safePath = resolveSafePath(relativePath);
        if (!Files.exists(safePath) || !Files.isDirectory(safePath)) {
            throw new IllegalArgumentException("Not a directory: " + relativePath);
        }
        try (var walk = Files.walk(safePath)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ex) {
                        throw new RuntimeException("Failed to delete: " + path, ex);
                    }
                });
        }
    }

    public List<String> exportMarkdownFiles(int maxPerFile) throws IOException {
        int limit = Math.max(1, maxPerFile);
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(repoRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(this::shouldIncludeForExport)
                .sorted(Comparator.comparing(path -> repoRoot.relativize(path).toString().toLowerCase()))
                .forEach(files::add);
        }

        Path mdDir = repoRoot.resolve("md");
        Files.createDirectories(mdDir);

        List<String> created = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int fileIndex = 1;
        int entryCount = 0;

        for (int i = 0; i < files.size(); i++) {
            Path filePath = files.get(i);
            String relativePath = toRelativePath(filePath);
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            if (entryCount > 0) {
                builder.append("----------\n");
            }
            builder.append("filepath: ").append(relativePath).append('\n');
            if (content != null) {
                builder.append(content);
                if (!content.endsWith("\n")) {
                    builder.append('\n');
                }
            }

            entryCount++;
            if (entryCount >= limit) {
                created.add(writeExportFile(mdDir, builder, fileIndex));
                fileIndex++;
                entryCount = 0;
                builder.setLength(0);
            }
        }

        if (entryCount > 0) {
            created.add(writeExportFile(mdDir, builder, fileIndex));
        }

        return created;
    }

    private Path resolveSafePath(String relativePath) {
        Path requested = repoRoot.resolve(relativePath).normalize();
        if (!requested.startsWith(repoRoot)) {
            throw new IllegalArgumentException("Invalid path");
        }
        return requested;
    }

    private List<TreeNode> buildTree(Path current, String relativeBase) throws IOException {
        try (var stream = Files.list(current)) {
            return stream
                .filter(path -> includePath(path.getFileName().toString(), Files.isDirectory(path)))
                .sorted(Comparator
                    .comparing((Path path) -> !Files.isDirectory(path))
                    .thenComparing(path -> path.getFileName().toString().toLowerCase()))
                .map(path -> toNode(path, relativeBase))
                .collect(Collectors.toList());
        }
    }

    private TreeNode toNode(Path path, String relativeBase) {
        String name = path.getFileName().toString();
        String relativePath = relativeBase.isEmpty() ? name : relativeBase + "/" + name;

        if (Files.isDirectory(path)) {
            TreeNode dirNode = new TreeNode(name, relativePath, "directory");
            try {
                dirNode.setChildren(buildTree(path, relativePath));
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read directory: " + relativePath, ex);
            }
            return dirNode;
        }

        return new TreeNode(name, relativePath, "file");
    }

    private boolean includePath(String name, boolean isDirectory) {
        if (name.startsWith(".") && !".env".equals(name)) {
            return false;
        }
        return !isDirectory || !IGNORE_DIRS.contains(name);
    }

    private void updateMarkdownForPath(String relativePath, String newContent) throws IOException {
        if (relativePath == null) {
            return;
        }
        String normalized = relativePath.replace("\\", "/");
        if (normalized.startsWith("md/")) {
            return;
        }
        Path mdDir = repoRoot.resolve("md");
        if (!Files.exists(mdDir) || !Files.isDirectory(mdDir)) {
            return;
        }

        try (var stream = Files.list(mdDir)) {
            for (Path mdFile : stream.filter(Files::isRegularFile).collect(Collectors.toList())) {
                updateMarkdownFile(mdFile, normalized, newContent);
            }
        }
    }

    private void updateMarkdownFile(Path mdFile, String relativePath, String newContent) throws IOException {
        String raw = Files.readString(mdFile, StandardCharsets.UTF_8);
        if (raw == null || raw.isEmpty()) {
            return;
        }

        String delimiter = "----------\n";
        List<String> blocks = new ArrayList<>(Arrays.asList(raw.split("\\Q" + delimiter + "\\E", -1)));
        while (!blocks.isEmpty() && blocks.get(blocks.size() - 1).trim().isEmpty()) {
            blocks.remove(blocks.size() - 1);
        }

        String header = "filepath: " + relativePath;
        List<String> updated = new ArrayList<>();
        boolean changed = false;

        for (String block : blocks) {
            String trimmed = block.stripLeading();
            if (trimmed.startsWith(header)) {
                changed = true;
                if (newContent == null) {
                    continue;
                }
                StringBuilder builder = new StringBuilder();
                builder.append(header).append('\n');
                if (newContent != null && !newContent.isEmpty()) {
                    builder.append(newContent);
                    if (!newContent.endsWith("\n")) {
                        builder.append('\n');
                    }
                }
                updated.add(builder.toString());
            } else {
                updated.add(block);
            }
        }

        if (!changed) {
            return;
        }

        if (updated.isEmpty()) {
            Files.delete(mdFile);
            return;
        }

        String joined = String.join(delimiter, updated);
        Files.writeString(mdFile, joined, StandardCharsets.UTF_8);
    }

    private boolean shouldIncludeForExport(Path path) {
        Path relative = repoRoot.relativize(path);
        String relativeString = relative.toString();
        if (relativeString.isEmpty()) {
            return false;
        }
        String normalized = relativeString.replace("\\", "/");
        if (normalized.startsWith("md/")) {
            return false;
        }

        for (Path part : relative) {
            String name = part.toString();
            if (name.startsWith(".") && !".env".equals(name)) {
                return false;
            }
            if (IGNORE_DIRS.contains(name)) {
                return false;
            }
        }

        return true;
    }

    private String toRelativePath(Path path) {
        return repoRoot.relativize(path).toString().replace("\\", "/");
    }

    private String writeExportFile(Path mdDir, StringBuilder builder, int fileIndex) throws IOException {
        String fileName = "export-" + fileIndex + ".md";
        Path outPath = mdDir.resolve(fileName);
        Files.writeString(outPath, builder.toString(), StandardCharsets.UTF_8);
        return "md/" + fileName;
    }
}
