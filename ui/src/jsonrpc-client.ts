import { 
  createMessageConnection, 
  StreamMessageReader, 
  StreamMessageWriter,
  MessageConnection,
  RequestType,
  NotificationType
} from 'vscode-jsonrpc/node';
import * as net from 'net';
import * as fs from 'fs';

export class JsonRpcClient {
  private connection: MessageConnection | null = null;
  private isConnected = false;
  private socket: net.Socket | null = null;
  private readonly pipePath = '/tmp/totalrecall';
  private reconnectTimer: NodeJS.Timeout | null = null;
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 10;
  private readonly reconnectDelay = 2000; // 2 seconds
  private statusCallback: ((status: 'connected' | 'disconnected' | 'connecting') => void) | null = null;

  constructor() {}

  connect(): void {
    if (this.isConnected) {
      console.warn('JSON-RPC client already connected');
      return;
    }

    this.attemptConnection();
  }

  private attemptConnection(): void {
    this.updateStatus('connecting');
    
    // Connect to Unix domain socket
    this.socket = net.createConnection(this.pipePath);
    
    this.socket.on('connect', () => {
      console.log(`Connected to Unix socket: ${this.pipePath}`);
      this.reconnectAttempts = 0;
      
      const reader = new StreamMessageReader(this.socket!);
      const writer = new StreamMessageWriter(this.socket!);
      
      this.connection = createMessageConnection(reader, writer);
      
      this.connection.onError((error) => {
        console.error('JSON-RPC connection error:', error);
      });
      
      this.connection.onClose(() => {
        console.log('JSON-RPC connection closed');
        this.isConnected = false;
        this.updateStatus('disconnected');
        this.scheduleReconnect();
      });
      
      this.connection.listen();
      this.isConnected = true;
      this.updateStatus('connected');
      console.log('JSON-RPC client connected via Unix socket');
    });

    this.socket.on('error', (error) => {
      console.error('Socket error:', error.message);
      this.isConnected = false;
      this.updateStatus('disconnected');
      if (this.socket) {
        this.socket.destroy();
        this.socket = null;
      }
      this.scheduleReconnect();
    });

    this.socket.on('close', () => {
      console.log('Socket closed');
      this.isConnected = false;
      this.updateStatus('disconnected');
      this.scheduleReconnect();
    });
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      return; // Already scheduled
    }

    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`Failed to connect after ${this.maxReconnectAttempts} attempts`);
      return;
    }

    this.reconnectAttempts++;
    console.log(`Scheduling reconnect attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts} in ${this.reconnectDelay}ms...`);
    
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.attemptConnection();
    }, this.reconnectDelay);
  }

  disconnect(): void {
    // Clear any pending reconnect timer
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    
    if (this.connection) {
      this.connection.dispose();
      this.connection = null;
    }
    
    if (this.socket) {
      this.socket.destroy();
      this.socket = null;
    }
    
    this.isConnected = false;
    this.reconnectAttempts = 0;
    console.log('JSON-RPC client disconnected');
  }

  resetReconnectAttempts(): void {
    this.reconnectAttempts = 0;
  }

  async sendRequest<P, R>(method: string, params?: P): Promise<R> {
    if (!this.connection || !this.isConnected) {
      throw new Error('JSON-RPC client not connected');
    }
    
    const requestType = new RequestType<P, R, void>(method);
    return this.connection.sendRequest(requestType, params as P);
  }

  sendNotification<P>(method: string, params?: P): void {
    if (!this.connection || !this.isConnected) {
      throw new Error('JSON-RPC client not connected');
    }
    
    const notificationType = new NotificationType<P>(method);
    this.connection.sendNotification(notificationType, params as P);
  }

  onRequest<P, R>(method: string, handler: (params: P) => R | Promise<R>): void {
    if (!this.connection) {
      throw new Error('JSON-RPC client not initialized');
    }
    
    const requestType = new RequestType<P, R, void>(method);
    this.connection.onRequest(requestType, handler);
  }

  onNotification<P>(method: string, handler: (params: P) => void): void {
    if (!this.connection) {
      throw new Error('JSON-RPC client not initialized');
    }
    
    const notificationType = new NotificationType<P>(method);
    this.connection.onNotification(notificationType, handler);
  }

  onStatusChange(callback: (status: 'connected' | 'disconnected' | 'connecting') => void): void {
    this.statusCallback = callback;
  }

  private updateStatus(status: 'connected' | 'disconnected' | 'connecting'): void {
    if (this.statusCallback) {
      this.statusCallback(status);
    }
  }

  getConnectionStatus(): 'connected' | 'disconnected' | 'connecting' {
    if (this.isConnected) return 'connected';
    if (this.reconnectTimer || this.socket) return 'connecting';
    return 'disconnected';
  }
}

export const jsonRpcClient = new JsonRpcClient();