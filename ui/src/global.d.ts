export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export interface JsonRpcApi {
  request: (method: string, params?: any) => Promise<{ success: boolean; result?: any; error?: string }>;
  notification: (method: string, params?: any) => void;
  getStatus: () => Promise<ConnectionStatus>;
  onStatusChange: (callback: (status: ConnectionStatus) => void) => () => void;
}

declare global {
  interface Window {
    jsonRpc: JsonRpcApi;
  }
}

export {};