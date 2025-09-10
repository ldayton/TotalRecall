// See the Electron documentation for details on how to use preload scripts:
// https://www.electronjs.org/docs/latest/tutorial/process-model#preload-scripts

import { contextBridge, ipcRenderer } from 'electron';

// Expose JSON-RPC API to the renderer process
contextBridge.exposeInMainWorld('jsonRpc', {
  request: async (method: string, params?: any) => {
    return ipcRenderer.invoke('jsonrpc:request', method, params);
  },
  
  notification: (method: string, params?: any) => {
    ipcRenderer.send('jsonrpc:notification', method, params);
  },
  
  getStatus: async () => {
    return ipcRenderer.invoke('jsonrpc:getStatus');
  },
  
  onStatusChange: (callback: (status: 'connected' | 'disconnected' | 'connecting') => void) => {
    const handler = (event: any, status: any) => callback(status);
    ipcRenderer.on('jsonrpc:status', handler);
    // Return cleanup function
    return () => ipcRenderer.removeListener('jsonrpc:status', handler);
  }
});
