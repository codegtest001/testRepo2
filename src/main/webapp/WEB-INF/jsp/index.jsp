<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Spring Monaco IDE</title>
    <link rel="stylesheet" href="/css/app.css"/>
</head>
<body>
<div class="layout">
    <aside class="sidebar">
        <h2>Files</h2>
        <div class="sidebar-actions">
            <button id="exportMdBtn">md파일 생성</button>
        </div>
        <div id="tree" class="tree-wrap"></div>
    </aside>
    <main class="center-pane">
        <header class="toolbar">
            <span id="filePath">Select a file</span>
            <div class="toolbar-right">
                <div class="tab-buttons">
                    <button id="sourceTabBtn" class="tab-btn active">소스보기</button>
                    <button id="previewTabBtn" class="tab-btn">미리보기</button>
                </div>
                <span id="dirty" class="dirty hidden">Unsaved</span>
                <button id="saveBtn" disabled>Save (Ctrl+S)</button>
            </div>
        </header>
        <section id="sourcePanel" class="view-panel active">
            <div id="editor"></div>
        </section>
        <section id="previewPanel" class="view-panel">
            <iframe id="previewFrame" title="project-preview"></iframe>
        </section>
        <div id="message" class="message hidden"></div>
    </main>
    <aside class="chat-pane">
        <h2>LLM Chat</h2>
        <div id="chatMessages" class="chat-messages"></div>
        <div class="chat-input-wrap">
            <textarea id="chatInput" placeholder="요구사항을 입력하세요. 예) 간단한 랜딩페이지 만들어줘"></textarea>
            <button id="chatSendBtn">Generate</button>
        </div>
    </aside>
</div>

<script src="https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.2/min/vs/loader.min.js"></script>
<script src="/js/app.js"></script>
</body>
</html>
