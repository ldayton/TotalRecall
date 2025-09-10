import { app, BrowserWindow, ipcMain } from 'electron';
import path from 'node:path';
import started from 'electron-squirrel-startup';
import { createApplicationMenu } from './menu';
import { jsonRpcClient } from './jsonrpc-client';

// Handle creating/removing shortcuts on Windows when installing/uninstalling.
if (started) {
  app.quit();
}

let mainWindow: BrowserWindow | null = null;

const createWindow = () => {
  // Create the browser window.
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  // Create and set the application menu
  createApplicationMenu(mainWindow);

  // and load the index.html of the app.
  if (MAIN_WINDOW_VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(MAIN_WINDOW_VITE_DEV_SERVER_URL);
  } else {
    mainWindow.loadFile(path.join(__dirname, `../renderer/${MAIN_WINDOW_VITE_NAME}/index.html`));
  }

  // Open the DevTools.
  // mainWindow.webContents.openDevTools();
};

// This method will be called when Electron has finished
// initialization and is ready to create browser windows.
// Some APIs can only be used after this event occurs.
app.on('ready', createWindow);

// Quit when all windows are closed, except on macOS. There, it's common
// for applications and their menu bar to stay active until the user quits
// explicitly with Cmd + Q.
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  // On OS X it's common to re-create a window in the app when the
  // dock icon is clicked and there are no other windows open.
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});

// In this file you can include the rest of your app's specific main process
// code. You can also put them in separate files and import them here.

// Initialize JSON-RPC client
jsonRpcClient.connect();

// Set up status change listener
jsonRpcClient.onStatusChange((status) => {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('jsonrpc:status', status);
  }
});

// Set up IPC handlers for JSON-RPC communication
ipcMain.handle('jsonrpc:request', async (event, method: string, params?: any) => {
  try {
    const result = await jsonRpcClient.sendRequest(method, params);
    return { success: true, result };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    return { success: false, error: errorMessage };
  }
});

ipcMain.on('jsonrpc:notification', (event, method: string, params?: any) => {
  try {
    jsonRpcClient.sendNotification(method, params);
  } catch (error) {
    console.error('Failed to send notification:', error);
  }
});

ipcMain.handle('jsonrpc:getStatus', () => {
  return jsonRpcClient.getConnectionStatus();
});

// Clean up on app quit
app.on('before-quit', () => {
  jsonRpcClient.disconnect();
});
