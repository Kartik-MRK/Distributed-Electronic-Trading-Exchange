'use client';

import { useEffect, useState, useCallback } from 'react';
import { ConnectionState, wsManager, WSListener } from '../lib/ws';

export function useWebSocket() {
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [latency, setLatency] = useState<number>(24);

  useEffect(() => {
    wsManager.connect();

    const unsubscribeState = wsManager.onStateChange((state) => {
      setConnectionState(state);
    });

    const unsubscribeLatency = wsManager.onLatencyChange((lat) => {
      setLatency(lat);
    });

    return () => {
      unsubscribeState();
      unsubscribeLatency();
    };
  }, []);

  const subscribe = useCallback(<T>(topic: string, listener: WSListener<T>) => {
    return wsManager.subscribe<T>(topic, listener);
  }, []);

  return {
    connectionState,
    latency,
    subscribe,
  };
}
