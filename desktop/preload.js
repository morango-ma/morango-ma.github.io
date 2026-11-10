const { contextBridge, ipcRenderer } = require('electron');
const call = (c, ...a) => ipcRenderer.sendSync('mn', c, a);
contextBridge.exposeInMainWorld('MorangoNative', {
  desktop: true,
  version: () => call('version'),
  listPrinters: () => call('list'),
  connect: (id) => call('connect', id),
  write: (b64) => call('write', b64),
  saveFile: (name, mime, b64) => call('save', name, mime, b64)
});
