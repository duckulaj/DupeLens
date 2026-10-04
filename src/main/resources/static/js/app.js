function openFolderBrowser(path){const modal=document.getElementById('folderModal');modal.classList.add('open');loadFolders(path||'');}
function closeFolderBrowser(){document.getElementById('folderModal')?.classList.remove('open');}
async function loadFolders(path){const box=document.getElementById('folderBrowserContent');box.innerHTML='<div class="empty">Loading folders…</div>';try{const r=await fetch('/folders?path='+encodeURIComponent(path));box.innerHTML=await r.text();}catch(e){box.innerHTML='<div class="warning">Could not load folders: '+e+'</div>';}}
function selectCurrentFolder(path){const ta=document.getElementById('roots');const lines=ta.value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);if(!lines.includes(path)) lines.push(path);ta.value=lines.join('\n');closeFolderBrowser();}
function openPreview(id,type){const m=document.getElementById('previewModal');const c=document.getElementById('previewContent');c.innerHTML=type==='VIDEO'?'<video src="/preview/'+id+'" controls autoplay></video>':'<img src="/preview/'+id+'" alt="Preview">';m.classList.add('open');}
function closePreview(){const m=document.getElementById('previewModal');if(m){m.classList.remove('open');document.getElementById('previewContent').innerHTML='';}}
function selectSuggested(){document.querySelectorAll('input[data-suggested="true"]').forEach(x=>x.checked=true);}
function clearSelection(){document.querySelectorAll('input[name="ids"]').forEach(x=>x.checked=false);}
function selectAllVisible(){document.querySelectorAll('input[name="ids"]').forEach(x=>x.checked=true);}
function selectedCount(){return document.querySelectorAll('input[name="ids"]:checked').length;}
function confirmTrash(){const n=selectedCount();if(!n){alert('Select at least one file first.');return false;}return confirm('Move '+n+' selected file(s) to the Linux Trash? Nothing will be permanently deleted.');}
function validateMove(){const n=selectedCount();if(!n){alert('Select at least one file first.');return false;}const d=document.getElementById('moveDestination').value.trim();if(!d){alert('Enter a destination folder.');return false;}return true;}
window.addEventListener('click',e=>{if(e.target.classList.contains('modal'))e.target.classList.remove('open');});
// Offline fallback for the scan form if htmx is unavailable.
async function fallbackControl(e,path){if(window.htmx)return true;e.preventDefault();await fetch(path,{method:'POST'});const r=await fetch('/scan/progress');document.getElementById('scan-panel').outerHTML=await r.text();fallbackPoll();return false;}
window.addEventListener('DOMContentLoaded',()=>{const f=document.getElementById('scanForm');if(f&&!window.htmx){f.addEventListener('submit',async e=>{e.preventDefault();const r=await fetch(f.action,{method:'POST',body:new FormData(f)});document.getElementById('scan-panel').outerHTML=await r.text();fallbackPoll();});}fallbackPoll();});
async function fallbackPoll(){if(window.htmx)return;let p=document.querySelector('#scan-panel [data-running="true"]');if(!p)return;setTimeout(async()=>{const r=await fetch('/scan/progress');document.getElementById('scan-panel').outerHTML=await r.text();fallbackPoll();},1000);}
