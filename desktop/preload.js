const { contextBridge, ipcRenderer } = require('electron');
const call = (c, ...a) => ipcRenderer.sendSync('mn', c, a);
contextBridge.exposeInMainWorld('MorangoNative', {
  desktop: true,
  platform: process.platform,
  setFullscreen: (on) => call('fs', on),
  isFullscreen: () => call('isfs'),
  version: () => call('version'),
  listPrinters: () => call('list'),
  connect: (id) => call('connect', id),
  write: (b64) => call('write', b64),
  saveFile: (name, mime, b64) => call('save', name, mime, b64),
  printImage: (dataUrl, wmm, hmm) => call('printimg', dataUrl, wmm, hmm)
});
