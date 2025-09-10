import React, { useState, useEffect } from 'react';
import type { ConnectionStatus } from './global';

export const JsonRpcExample: React.FC = () => {
  const [response, setResponse] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');

  useEffect(() => {
    // Get initial status
    window.jsonRpc.getStatus().then(setStatus);
    
    // Subscribe to status changes
    const unsubscribe = window.jsonRpc.onStatusChange(setStatus);
    
    // Cleanup on unmount
    return unsubscribe;
  }, []);

  const sendRequest = async () => {
    setLoading(true);
    try {
      // Ping request - server expects no parameters and returns empty Pong object
      const result = await window.jsonRpc.request('ping');
      
      if (result.success) {
        setResponse('Pong received! ' + JSON.stringify(result.result, null, 2));
      } else {
        setResponse(`Error: ${result.error}`);
      }
    } catch (error) {
      setResponse(`Request failed: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const sendNotification = () => {
    // Example notification - fire and forget
    window.jsonRpc.notification('log', { level: 'info', message: 'UI notification sent' });
    setResponse('Notification sent (no response expected)');
  };

  const getStatusColor = () => {
    switch (status) {
      case 'connected': return '#4CAF50';
      case 'connecting': return '#FFC107';
      case 'disconnected': return '#F44336';
    }
  };

  const getStatusText = () => {
    switch (status) {
      case 'connected': return 'Connected';
      case 'connecting': return 'Connecting...';
      case 'disconnected': return 'Disconnected';
    }
  };

  return (
    <div style={{ padding: '20px' }}>
      <h2>JSON-RPC Communication</h2>
      
      <div style={{ marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '10px' }}>
        <div style={{ 
          width: '12px', 
          height: '12px', 
          borderRadius: '50%', 
          backgroundColor: getStatusColor() 
        }} />
        <span>Status: {getStatusText()} (pipe: /tmp/totalrecall)</span>
      </div>
      
      <div style={{ marginBottom: '10px' }}>
        <button onClick={sendRequest} disabled={loading || status !== 'connected'}>
          Send Request
        </button>
        <button onClick={sendNotification} disabled={status !== 'connected'} style={{ marginLeft: '10px' }}>
          Send Notification
        </button>
      </div>
      
      {response && (
        <div style={{ 
          marginTop: '20px', 
          padding: '10px', 
          backgroundColor: '#f0f0f0', 
          borderRadius: '4px',
          fontFamily: 'monospace',
          whiteSpace: 'pre-wrap'
        }}>
          <strong>Response:</strong><br />
          {response}
        </div>
      )}
    </div>
  );
};