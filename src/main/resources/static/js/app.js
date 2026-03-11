const languageByExt = {
	js: "javascript",
	jsx: "javascript",
	ts: "typescript",
	tsx: "typescript",
	json: "json",
	md: "markdown",
	html: "html",
	css: "css",
	java: "java",
	py: "python",
	env: "shell"
};

let editor;
let selectedPath = "";
let savedContent = "";
let treeData = [];
let activeTab = "source";
let openDirMenu = null;
const openDirs = new Set();

const treeWrap = document.getElementById("tree");
const filePathEl = document.getElementById("filePath");
const dirtyEl = document.getElementById("dirty");
const saveBtn = document.getElementById("saveBtn");
const messageEl = document.getElementById("message");
const sourceTabBtn = document.getElementById("sourceTabBtn");
const previewTabBtn = document.getElementById("previewTabBtn");
const sourcePanel = document.getElementById("sourcePanel");
const previewPanel = document.getElementById("previewPanel");
const previewFrame = document.getElementById("previewFrame");
const chatMessages = document.getElementById("chatMessages");
const chatInput = document.getElementById("chatInput");
const chatSendBtn = document.getElementById("chatSendBtn");
const exportMdBtn = document.getElementById("exportMdBtn");

function getLanguage(path) {
	const ext = path.split(".").pop();
	return languageByExt[ext] || "plaintext";
}

function setMessage(message) {
	messageEl.textContent = message;
	messageEl.classList.remove("hidden");
	setTimeout(() => messageEl.classList.add("hidden"), 1800);
}

function isDirty() {
	return editor && editor.getValue() !== savedContent;
}

function closeDirMenu() {
	if (!openDirMenu) {
		return;
	}
	openDirMenu.classList.remove("open");
	openDirMenu = null;
}

function toggleDirMenu(menuWrap) {
	if (openDirMenu && openDirMenu !== menuWrap) {
		openDirMenu.classList.remove("open");
	}

	if (menuWrap.classList.contains("open")) {
		menuWrap.classList.remove("open");
		openDirMenu = null;
		return;
	}

	menuWrap.classList.add("open");
	openDirMenu = menuWrap;
}

function syncDirtyState() {
	if (isDirty()) {
		dirtyEl.classList.remove("hidden");
	} else {
		dirtyEl.classList.add("hidden");
	}
}

function switchTab(tabName) {
	activeTab = tabName;
	const isSource = tabName === "source";

	sourceTabBtn.classList.toggle("active", isSource);
	previewTabBtn.classList.toggle("active", !isSource);
	sourcePanel.classList.toggle("active", isSource);
	previewPanel.classList.toggle("active", !isSource);

	if (!isSource) {
		refreshPreview();
	}
}

function refreshPreview() {
	let previewPath = "generated-project/index.html";
	if (selectedPath && selectedPath.endsWith(".html")) {
		previewPath = selectedPath;
	}
	previewFrame.src = `/api/preview?path=${encodeURIComponent(previewPath)}&t=${Date.now()}`;
}

function createTreeNode(node, level) {
	const wrapper = document.createElement("div");
	const row = document.createElement("div");
	row.className = `tree-row ${node.type === "directory" ? "dir" : "file"}`;
	row.style.paddingLeft = `${level * 14 + 8}px`;
	row.setAttribute("role", "button");
	row.tabIndex = 0;

	if (node.type === "directory") {
		let open = openDirs.has(node.path);
		if (!open && level < 1) {
			open = true;
			openDirs.add(node.path);
		}
		const label = document.createElement("span");
		label.className = "tree-label";
		label.textContent = `${open ? "- " : "+ "} ${node.name}`;

		const menuWrap = document.createElement("div");
		menuWrap.className = "dir-menu-wrap";

		const menuBtn = document.createElement("button");
		menuBtn.className = "dir-menu-btn";
		menuBtn.textContent = "...";

		const menu = document.createElement("div");
		menu.className = "dir-menu";

		const newFolderBtn = document.createElement("button");
		newFolderBtn.className = "dir-menu-item";
		newFolderBtn.textContent = "New Folder";
		newFolderBtn.addEventListener("click", (event) => {
			event.stopPropagation();
			closeDirMenu();
			createDirectoryInDirectory(node.path);
		});

		const newFileBtn = document.createElement("button");
		newFileBtn.className = "dir-menu-item";
		newFileBtn.textContent = "New File";
		newFileBtn.addEventListener("click", (event) => {
			event.stopPropagation();
			closeDirMenu();
			createFileInDirectory(node.path);
		});

		const deleteDirBtn = document.createElement("button");
		deleteDirBtn.className = "dir-menu-item danger";
		deleteDirBtn.textContent = "Delete";
		deleteDirBtn.addEventListener("click", (event) => {
			event.stopPropagation();
			closeDirMenu();
			deleteDirectory(node.path);
		});

		menu.appendChild(newFolderBtn);
		menu.appendChild(newFileBtn);
		menu.appendChild(deleteDirBtn);
		menuWrap.appendChild(menuBtn);
		menuWrap.appendChild(menu);

		menuWrap.addEventListener("click", (event) => {
			event.stopPropagation();
		});

		menuBtn.addEventListener("click", (event) => {
			event.stopPropagation();
			toggleDirMenu(menuWrap);
		});

		row.appendChild(label);
		row.appendChild(menuWrap);

		const childrenWrap = document.createElement("div");
		childrenWrap.style.display = open ? "block" : "none";

		row.addEventListener("click", () => {
			open = !open;
			if (open) {
				openDirs.add(node.path);
			} else {
				openDirs.delete(node.path);
			}
			label.textContent = `${open ? "- " : "+ "} ${node.name}`;
			childrenWrap.style.display = open ? "block" : "none";
		});
		row.addEventListener("keydown", (event) => {
			if (event.key === "Enter" || event.key === " ") {
				event.preventDefault();
				open = !open;
				if (open) {
					openDirs.add(node.path);
				} else {
					openDirs.delete(node.path);
				}
				label.textContent = `${open ? "- " : "+ "} ${node.name}`;
				childrenWrap.style.display = open ? "block" : "none";
			}
		});

		(node.children || []).forEach((child) => {
			childrenWrap.appendChild(createTreeNode(child, level + 1));
		});

		wrapper.appendChild(row);
		wrapper.appendChild(childrenWrap);
		return wrapper;
	}

	const label = document.createElement("span");
	label.className = "tree-label";
	label.textContent = node.name;

	const menuWrap = document.createElement("div");
	menuWrap.className = "dir-menu-wrap";

	const menuBtn = document.createElement("button");
	menuBtn.className = "dir-menu-btn";
	menuBtn.textContent = "...";

	const menu = document.createElement("div");
	menu.className = "dir-menu";

	const deleteFileBtn = document.createElement("button");
	deleteFileBtn.className = "dir-menu-item danger";
	deleteFileBtn.textContent = "Delete";
	deleteFileBtn.addEventListener("click", (event) => {
		event.stopPropagation();
		closeDirMenu();
		deleteFileAtPath(node.path);
	});

	menu.appendChild(deleteFileBtn);
	menuWrap.appendChild(menuBtn);
	menuWrap.appendChild(menu);

	menuWrap.addEventListener("click", (event) => {
		event.stopPropagation();
	});

	menuBtn.addEventListener("click", (event) => {
		event.stopPropagation();
		toggleDirMenu(menuWrap);
	});

	row.appendChild(label);
	row.appendChild(menuWrap);

	row.dataset.path = node.path;
	if (node.path === selectedPath) {
		row.classList.add("active");
	}

	row.addEventListener("click", () => openFile(node.path));
	row.addEventListener("keydown", (event) => {
		if (event.key === "Enter" || event.key === " ") {
			event.preventDefault();
			openFile(node.path);
		}
	});

	wrapper.appendChild(row);
	return wrapper;
}

function renderTree() {
	treeWrap.innerHTML = "";
	treeData.forEach((node) => {
		treeWrap.appendChild(createTreeNode(node, 0));
	});
}

async function loadTree() {
	const res = await fetch("/api/tree");
	const data = await res.json();
	if (!res.ok) {
		setMessage(data.error || "Failed to load tree");
		return;
	}

	treeData = data.tree || [];
	renderTree();
}

async function openFile(path) {
	const res = await fetch(`/api/file?path=${encodeURIComponent(path)}`);
	const data = await res.json();

	if (!res.ok) {
		setMessage(data.error || "Failed to open file");
		return;
	}

	selectedPath = path;
	savedContent = data.content;
	editor.setValue(data.content || "");
	monaco.editor.setModelLanguage(editor.getModel(), getLanguage(path));

	filePathEl.textContent = path;
	saveBtn.disabled = false;
	syncDirtyState();
	renderTree();

	if (activeTab === "preview") {
		refreshPreview();
	}
}

async function createFileAtPath(path) {
	const res = await fetch("/api/file", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ path, content: "" })
	});

	const data = await res.json();
	if (!res.ok) {
		setMessage(data.error || "Create failed");
		return;
	}

	await loadTree();
	await openFile(path);
	setMessage(`Created: ${path}`);
}

async function createFile() {
	const baseDir = selectedPath && selectedPath.includes("/")
		? selectedPath.slice(0, selectedPath.lastIndexOf("/"))
		: "";
	const defaultName = baseDir ? `${baseDir}/new-file.txt` : "new-file.txt";
	const path = window.prompt("New file path", defaultName);
	if (!path) {
		return;
	}
	await createFileAtPath(path);
}

async function createFileInDirectory(directoryPath) {
	const fileName = window.prompt("New file name", "new-file.txt");
	if (!fileName) {
		return;
	}
	const path = directoryPath ? `${directoryPath}/${fileName}` : fileName;
	await createFileAtPath(path);
}

async function createDirectoryInDirectory(directoryPath) {
	const dirName = window.prompt("New folder name", "new-folder");
	if (!dirName) {
		return;
	}
	const path = directoryPath ? `${directoryPath}/${dirName}` : dirName;

	const res = await fetch("/api/dir", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ path })
	});

	const data = await res.json();
	if (!res.ok) {
		setMessage(data.error || "Create failed");
		return;
	}

	await loadTree();
	setMessage(`Created: ${path}`);
}

async function deleteDirectory(path) {
	const ok = window.confirm(`Delete folder: ${path}?`);
	if (!ok) {
		return;
	}

	const res = await fetch(`/api/dir?path=${encodeURIComponent(path)}`, {
		method: "DELETE"
	});
	const data = await res.json();
	if (!res.ok) {
		setMessage(data.error || "Delete failed");
		return;
	}

	if (selectedPath && (selectedPath === path || selectedPath.startsWith(`${path}/`))) {
		selectedPath = "";
		savedContent = "";
		editor.setValue("");
		filePathEl.textContent = "Select a file";
		saveBtn.disabled = true;
		syncDirtyState();
	}

	await loadTree();
	setMessage("Deleted");
}

async function deleteFile() {
	if (!selectedPath) {
		return;
	}
	await deleteFileAtPath(selectedPath);
}

async function deleteFileAtPath(path) {
	if (!path) {
		return;
	}
	const ok = window.confirm(`Delete file: ${path}?`);
	if (!ok) {
		return;
	}

	const res = await fetch(`/api/file?path=${encodeURIComponent(path)}`, {
		method: "DELETE"
	});
	const data = await res.json();
	if (!res.ok) {
		setMessage(data.error || "Delete failed");
		return;
	}

	if (selectedPath === path) {
		selectedPath = "";
		savedContent = "";
		editor.setValue("");
		filePathEl.textContent = "Select a file";
		saveBtn.disabled = true;
		syncDirtyState();
	}
	await loadTree();
	refreshPreview();
	setMessage("Deleted");
}

async function exportMarkdown() {
	const res = await fetch("/api/md/export", { method: "POST" });
	const data = await res.json();
	if (!res.ok) {
		setMessage(data.error || "Export failed");
		return;
	}

	await loadTree();
	const count = Array.isArray(data.files) ? data.files.length : 0;
	setMessage(`MD files created: ${count}`);
}

async function saveFile() {
	if (!selectedPath || !editor) {
		return;
	}

	const res = await fetch("/api/file", {
		method: "PUT",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ path: selectedPath, content: editor.getValue() })
	});

	const data = await res.json();
	if (!res.ok) {
		setMessage(data.error || "Save failed");
		return;
	}

	savedContent = editor.getValue();
	syncDirtyState();
	setMessage(`Saved: ${selectedPath}`);

	if (activeTab === "preview") {
		refreshPreview();
	}
}

function addChatBubble(role, text, files) {
	const bubble = document.createElement("div");
	bubble.className = `chat-bubble ${role}`;
	bubble.textContent = text;

	if (Array.isArray(files) && files.length > 0) {
		const filesWrap = document.createElement("div");
		filesWrap.className = "generated-files";

		files.forEach((file) => {
			const btn = document.createElement("button");
			btn.className = "generated-file-btn";
			btn.textContent = file.path;
			btn.addEventListener("click", () => {
				openFile(file.path);
				switchTab("source");
			});
			filesWrap.appendChild(btn);
		});

		bubble.appendChild(filesWrap);
	}

	chatMessages.appendChild(bubble);
	chatMessages.scrollTop = chatMessages.scrollHeight;
}

async function openGithubLogin(loginUrl) {
	return new Promise((resolve, reject) => {
		const popup = window.open(loginUrl || "/github/login", "githubLoginPopup", "width=640,height=760,resizable=yes,scrollbars=yes");
		if (!popup) {
			reject(new Error("Popup blocked"));
			return;
		}

		const timeoutId = setTimeout(() => {
			window.removeEventListener("message", onMessage);
			reject(new Error("GitHub login timeout"));
		}, 180000);

		function onMessage(event) {
			if (event.origin !== window.location.origin) {
				return;
			}
			if (event.data && event.data.type === "github-login-success") {
				clearTimeout(timeoutId);
				window.removeEventListener("message", onMessage);
				resolve();
			}
		}

		window.addEventListener("message", onMessage);
	});
}

async function resumePublishFlow() {
	const res = await fetch("/api/chat/resume-publish", { method: "POST" });
	const data = await res.json();
	if (!res.ok) {
		addChatBubble("assistant", data.error || "Publish resume failed");
		return;
	}

	addChatBubble("assistant", data.reply || "Publish completed", data.files || []);
	await loadTree();
	if (data.files && data.files.length > 0) {
		await openFile(data.files[0].path);
	}
	refreshPreview();
}

async function generateFromChat() {
	const prompt = chatInput.value.trim();
	if (!prompt) {
		return;
	}

	addChatBubble("user", prompt);
	chatInput.value = "";
	chatSendBtn.disabled = true;

	try {
		const res = await fetch("/api/chat/generate", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ prompt })
		});

		const data = await res.json();
		if (!res.ok) {
			addChatBubble("assistant", data.error || "Generation failed");
			return;
		}

		addChatBubble("assistant", data.reply || "Generated", data.files || []);
		await loadTree();
		if (data.files && data.files.length > 0) {
			await openFile(data.files[0].path);
		}
		refreshPreview();

		if (data.needsGithubLogin) {
			addChatBubble("assistant", "GitHub 로그인 후 퍼블리시를 이어서 진행합니다.");
			try {
				await openGithubLogin(data.githubLoginUrl);
				await resumePublishFlow();
			} catch (err) {
				addChatBubble("assistant", `GitHub 로그인 또는 재개 실패: ${err.message}`);
			}
		}

		setMessage("Generated files ready");
	} catch (_err) {
		addChatBubble("assistant", "Network error during generation");
	} finally {
		chatSendBtn.disabled = false;
	}
}

saveBtn.addEventListener("click", saveFile);
sourceTabBtn.addEventListener("click", () => switchTab("source"));
previewTabBtn.addEventListener("click", () => switchTab("preview"));
chatSendBtn.addEventListener("click", generateFromChat);
exportMdBtn.addEventListener("click", exportMarkdown);

chatInput.addEventListener("keydown", (e) => {
	if (e.key === "Enter" && !e.shiftKey) {
		e.preventDefault();
		generateFromChat();
	}
});

window.addEventListener("keydown", (e) => {
	if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") {
		e.preventDefault();
		saveFile();
	}
});

window.addEventListener("click", () => {
	closeDirMenu();
});

require.config({
	paths: {
		vs: "https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.2/min/vs"
	}
});

require(["vs/editor/editor.main"], () => {
	editor = monaco.editor.create(document.getElementById("editor"), {
		value: "",
		language: "plaintext",
		theme: "vs-dark",
		automaticLayout: true,
		fontSize: 14,
		minimap: { enabled: false },
		wordWrap: "on"
	});

	editor.onDidChangeModelContent(() => syncDirtyState());
	addChatBubble("assistant", "요구사항을 입력하면 generated-project 아래 파일을 생성합니다. 커밋/푸시 요청을 쓰면 GitHub 퍼블리시 흐름을 시작합니다.");
	loadTree();
	refreshPreview();
});
