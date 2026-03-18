(function(){
  // Minimal frontend for Spring Monaco IDE
  let editor;
  let currentPath = null;
  let dirty = false;

  function init() {
    setupTabs();
    setupButtons();
    initMonaco(() => {
      createEditor();
      loadTree();
    });
    setupChat();
  }

  function setupTabs(){
    document.getElementById('sourceTabBtn').addEventListener('click', () => switchTab('source'));
    document.getElementById('previewTabBtn').addEventListener('click', () => switchTab('preview'));
  }

  function switchTab(tab){
    document.querySelectorAll('.view-panel').forEach(p=>p.classList.remove('active'));
    if(tab==='source'){
      document.getElementById('sourcePanel').classList.add('active');
    } else {
      document.getElementById('previewPanel').classList.add('active');
      if(currentPath){
        const url = '/api/preview?path=' + encodeURIComponent(currentPath);
        document.getElementById('previewFrame').src = url;
      } else {
        document.getElementById('previewFrame').src = '/api/preview';
      }
    }
  }

  function setupButtons(){
    const saveBtn = document.getElementById('saveBtn');
    saveBtn.addEventListener('click', saveFile);

    document.addEventListener('keydown', (e)=>{
      if((e.ctrlKey||e.metaKey) && e.key.toLowerCase()==='s'){
        e.preventDefault();
        if(!saveBtn.disabled) saveFile();
      }
    });
  }

  function initMonaco(cb){
    if(typeof require === 'undefined'){
      console.warn('Monaco loader not found');
      cb();
      return;
    }
    window.require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.2/min/vs' } });
    window.require(['vs/editor/editor.main'], function(){
      cb();
    });
  }

  function createEditor(){
    editor = monaco.editor.create(document.getElementById('editor'), {
      value: '// Select a file from the tree',
      language: 'text',
      automaticLayout: true,
      minimap: { enabled: false }
    });

    editor.onDidChangeModelContent(()=>{
      setDirty(true);
    });
  }

  function setDirty(val){
    dirty = val;
    const dirtyEl = document.getElementById('dirty');
    const saveBtn = document.getElementById('saveBtn');
    if(val){
      dirtyEl.classList.remove('hidden');
      saveBtn.disabled = false;
    } else {
      dirtyEl.classList.add('hidden');
      saveBtn.disabled = true;
    }
  }

  async function loadTree(){
    try{
      const res = await fetch('/api/tree');
      if(!res.ok) throw new Error('Failed to load tree');
      const body = await res.json();
      const tree = body.tree || [];
      const wrap = document.getElementById('tree');
      wrap.innerHTML = '';
      const ul = document.createElement('ul');
      buildTreeNodes(tree, ul);
      wrap.appendChild(ul);
    }catch(err){
      showMessage('error: ' + err.message);
    }
  }

  function buildTreeNodes(nodes, parentEl){
    nodes.forEach(node=>{
      const li = document.createElement('li');
      li.className = node.type;
      const btn = document.createElement('button');
      btn.textContent = node.name;
      btn.title = node.path;
      btn.addEventListener('click', (e)=>{
        e.stopPropagation();
        if(node.type==='file'){
          selectFile(node.path);
        } else {
          // toggle children
          const next = li.querySelector('ul');
          if(next) next.style.display = next.style.display==='none' ? 'block' : 'none';
        }
      });
      li.appendChild(btn);
      if(node.children && node.children.length){
        const childUl = document.createElement('ul');
        buildTreeNodes(node.children, childUl);
        li.appendChild(childUl);
      }
      parentEl.appendChild(li);
    });
  }

  async function selectFile(path){
    try{
      const res = await fetch('/api/file?path=' + encodeURIComponent(path));
      if(!res.ok) throw new Error('Failed to load file');
      const body = await res.json();
      const content = body.content || '';
      currentPath = path;
      document.getElementById('filePath').textContent = path;
      editor.setValue(content);
      setEditorLanguageFromPath(path);
      setDirty(false);
      switchTab('source');
    }catch(err){
      showMessage('error: ' + err.message);
    }
  }

  function setEditorLanguageFromPath(path){
    const ext = path.split('.').pop().toLowerCase();
    let lang = 'plaintext';
    if(['js'].includes(ext)) lang='javascript';
    if(['html','htm'].includes(ext)) lang='html';
    if(['css'].includes(ext)) lang='css';
    if(['java'].includes(ext)) lang='java';
    const model = editor.getModel();
    monaco.editor.setModelLanguage(model, lang);
  }

  async function saveFile(){
    if(!currentPath) return showMessage('No file selected');
    try{
      const content = editor.getValue();
      const res = await fetch('/api/file',{
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: currentPath, content })
      });
      const body = await res.json();
      if(!res.ok) throw new Error(body.error || 'Save failed');
      setDirty(false);
      showMessage('Saved: ' + currentPath);
    }catch(err){
      showMessage('error: ' + err.message);
    }
  }

  function showMessage(text, timeout=4000){
    const el = document.getElementById('message');
    el.textContent = text;
    el.classList.remove('hidden');
    setTimeout(()=>el.classList.add('hidden'), timeout);
  }

  function setupChat(){
    document.getElementById('chatSendBtn').addEventListener('click', async ()=>{
      const prompt = document.getElementById('chatInput').value.trim();
      if(!prompt) return;
      appendChat('user', prompt);
      try{
        const res = await fetch('/api/chat/generate',{
          method:'POST', headers: { 'Content-Type':'application/json' }, body: JSON.stringify({ prompt })
        });
        const body = await res.json();
        appendChat('assistant', body.reply || JSON.stringify(body));
        if(body.files && body.files.length){
          // refresh tree and auto-open first file
          await loadTree();
          selectFile(body.files[0].path);
        }
      }catch(err){
        appendChat('assistant','error: '+err.message);
      }
    });
  }

  function appendChat(role, text){
    const wrap = document.getElementById('chatMessages');
    const div = document.createElement('div');
    div.className = 'chat-'+role;
    div.textContent = text;
    wrap.appendChild(div);
    wrap.scrollTop = wrap.scrollHeight;
  }

  // start
  document.addEventListener('DOMContentLoaded', init);
})();
