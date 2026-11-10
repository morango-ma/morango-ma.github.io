// Morango for Mac: the live site in its own window, with direct receipt printing through CUPS (raw ESC/POS)
const { app, BrowserWindow, ipcMain, shell, session, Menu } = require('electron');
const { execFileSync, execFile } = require('child_process');
const WIN = process.platform === 'win32';
const fs = require('fs'), path = require('path'), os = require('os');
const HOME = 'https://morango-ma.github.io/';
let win = null, printer = null;
const CFG = () => path.join(app.getPath('userData'), 'settings.json');
const cfgGet = () => { try { return JSON.parse(fs.readFileSync(CFG(), 'utf8')); } catch (_) { return {}; } };
const cfgSet = (k, v) => { const c = cfgGet(); c[k] = v; try { fs.writeFileSync(CFG(), JSON.stringify(c)); } catch (_) {} };

function create() {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 820, minHeight: 600, title: 'Morango', backgroundColor: '#ffffff', autoHideMenuBar: WIN,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: false }
  });
  if (cfgGet().fullscreen) win.setFullScreen(true);
  win.webContents.on('before-input-event', (ev, input) => {
    if (input.type === 'keyDown' && input.key === 'F11') { ev.preventDefault(); const on = !win.isFullScreen(); win.setFullScreen(on); cfgSet('fullscreen', on); }
  });
  win.loadURL(HOME);
  win.webContents.setWindowOpenHandler(({ url }) => { if (url.startsWith(HOME)) return { action: 'allow' }; shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith(HOME)) { e.preventDefault(); shell.openExternal(url); } });
  win.webContents.on('did-fail-load', () => setTimeout(() => { try { win.loadURL(HOME); } catch (_) {} }, 5000));
}

app.whenReady().then(() => {
  const ok = ['media', 'notifications', 'clipboard-read', 'clipboard-sanitized-write', 'fullscreen', 'wakeLock'];
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(ok.includes(perm)));
  session.defaultSession.setPermissionCheckHandler((wc, perm) => ok.includes(perm));
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { role: 'appMenu' }, { role: 'editMenu' },
    { label: 'Affichage', submenu: [{ role: 'reload' }, { role: 'forceReload' }, { type: 'separator' }, { role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' }, { type: 'separator' }, { role: 'togglefullscreen' }] },
    { role: 'windowMenu' }
  ]));
  create();
  app.on('activate', () => { if (!BrowserWindow.getAllWindows().length) create(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

function printers() {
  try {
    return execFileSync('/usr/bin/lpstat', ['-p'], { encoding: 'utf8' }).split('\n')
      .map(l => { const m = l.match(/^printer\s+(\S+)/); return m && m[1]; }).filter(Boolean);
  } catch (e) { return []; }
}

ipcMain.on('mn', (e, cmd, a) => {
  try {
    if (cmd === 'version') { e.returnValue = 'desktop-2'; return; }
    if (cmd === 'fs') { const on = String(a[0]) === '1'; win.setFullScreen(on); cfgSet('fullscreen', on); e.returnValue = 'ok'; return; }
    if (cmd === 'isfs') { e.returnValue = win && win.isFullScreen() ? '1' : '0'; return; }
    if (cmd === 'list') {
      if (!WIN) { e.returnValue = JSON.stringify(printers().map(n => ({ id: 'cups:' + n, name: n.replace(/_/g, ' ') + ' (Mac)' }))); return; }
      win.webContents.getPrintersAsync().then(L => { e.returnValue = JSON.stringify(L.map(p => ({ id: 'win:' + p.name, name: (p.displayName || p.name) + ' (Windows)' }))); }).catch(() => { e.returnValue = '[]'; });
      return;
    }
    if (cmd === 'connect') {
      const n = String(a[0] || '').replace(/^(cups|win):/, '');
      if (WIN) { win.webContents.getPrintersAsync().then(L => { if (L.some(p => p.name === n)) { printer = n; e.returnValue = 'ok'; } else e.returnValue = 'الطابعة ما كايناش فويندوز. زيدها من Paramètres → Imprimantes'; }).catch(x => { e.returnValue = 'error: ' + x.message; }); return; }
      if (!printers().includes(n)) { e.returnValue = 'الطابعة ما كايناش فالماك. زيدها من Réglages Système → Imprimantes'; return; }
      printer = n; e.returnValue = 'ok'; return;
    }
    if (cmd === 'write') {
      if (!printer) { e.returnValue = 'الطابعة ما مربوطاش'; return; }
      const f = path.join(os.tmpdir(), 'morango-' + Date.now() + '.bin');
      fs.writeFileSync(f, Buffer.from(String(a[0] || ''), 'base64'));
      if (WIN) {
        const ps = path.join(process.resourcesPath || __dirname, 'rawprint.ps1');
        const script = fs.existsSync(ps) ? ps : path.join(__dirname, 'rawprint.ps1');
        execFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script, printer, f], { windowsHide: true, timeout: 30000 }, (err, out, errOut) => {
          try { fs.unlinkSync(f); } catch (_) {}
          const r = String(out || '').trim();
          e.returnValue = r === 'ok' ? 'ok' : 'الطبع ما مشاش: ' + (r || String(errOut || (err && err.message) || '')).slice(0, 200);
        });
        return;
      }
      try { execFileSync('/usr/bin/lp', ['-d', printer, '-o', 'raw', f]); e.returnValue = 'ok'; }
      catch (x) { e.returnValue = 'الطبع ما مشاش: ' + String(x.stderr || x.message).slice(0, 200); }
      finally { try { fs.unlinkSync(f); } catch (_) {} }
      return;
    }
    if (cmd === 'save') {
      const [name, , b64] = a;
      const dir = path.join(app.getPath('downloads'), 'Morango');
      fs.mkdirSync(dir, { recursive: true });
      const f = path.join(dir, String(name || 'file').replace(/[\/\\:]/g, '_'));
      fs.writeFileSync(f, Buffer.from(String(b64 || ''), 'base64'));
      shell.showItemInFolder(f);
      e.returnValue = 'ok:' + f; return;
    }
    e.returnValue = 'unknown';
  } catch (x) { e.returnValue = 'error: ' + x.message; }
});
