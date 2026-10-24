import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

export type ConnectionState =
  'connected' | 'reconnecting' | 'disconnected' | 'preview';

export interface WSListener<T = unknown> {
  (data: T): void;
}

export class WebSocketManager {
  private client: Client | null = null;
  private connectionState: ConnectionState = 'disconnected';
  private stateListeners: Set<(state: ConnectionState) => void> = new Set();
  private latencyListeners: Set<(latencyMs: number) => void> = new Set();
  private topicSubscriptions: Map<string, StompSubscription> = new Map();
  private topicListeners: Map<string, Set<WSListener>> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectDelay = 10000;
  private baseReconnectDelay = 1000;

  constructor(private brokerUrl: string = 'ws://localhost:8080/ws') {}

  public connect(): void {
    if (this.client && this.client.active) {
      return;
    }

    this.updateState('reconnecting');

    try {
      this.client = new Client({
        brokerURL: this.brokerUrl,
        reconnectDelay: this.calculateBackoff(),
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: () => {
          // Suppress verbose debug logs in production
        },
        onConnect: () => {
          this.reconnectAttempts = 0;
          this.updateState('connected');
          this.resubscribeAll();
        },
        onDisconnect: () => {
          this.updateState('disconnected');
        },
        onStompError: () => {
          this.updateState('reconnecting');
        },
        onWebSocketClose: () => {
          this.updateState('reconnecting');
        },
      });

      this.client.activate();
    } catch {
      this.updateState('disconnected');
    }

  }

  public disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.topicSubscriptions.clear();
    this.updateState('disconnected');
  }

  public subscribe<T>(topic: string, listener: WSListener<T>): () => void {
    if (!this.topicListeners.has(topic)) {
      this.topicListeners.set(topic, new Set());
    }
    const listeners = this.topicListeners.get(topic)!;
    listeners.add(listener as WSListener);

    // If client is connected and not yet subscribed via STOMP, create subscription
    if (
      this.client &&
      this.client.connected &&
      !this.topicSubscriptions.has(topic)
    ) {
      const sub = this.client.subscribe(topic, (msg: IMessage) => {
        this.handleMessage(topic, msg);
      });
      this.topicSubscriptions.set(topic, sub);
    }

    // Return unsubscription cleanup function
    return () => {
      listeners.delete(listener as WSListener);
      if (listeners.size === 0) {
        this.topicListeners.delete(topic);
        const sub = this.topicSubscriptions.get(topic);
        if (sub) {
          sub.unsubscribe();
          this.topicSubscriptions.delete(topic);
        }
      }
    };
  }

  public onStateChange(listener: (state: ConnectionState) => void): () => void {
    this.stateListeners.add(listener);
    listener(this.connectionState);
    return () => {
      this.stateListeners.delete(listener);
    };
  }

  public onLatencyChange(listener: (latencyMs: number) => void): () => void {
    this.latencyListeners.add(listener);
    return () => {
      this.latencyListeners.delete(listener);
    };
  }

  public getState(): ConnectionState {
    return this.connectionState;
  }

  private updateState(state: ConnectionState): void {
    this.connectionState = state;
    if (state !== 'connected') this.topicSubscriptions.clear();
    this.stateListeners.forEach((fn) => fn(state));
  }

  private handleMessage(topic: string, message: IMessage): void {
    try {
      const payload = JSON.parse(message.body);
      const listeners = this.topicListeners.get(topic);
      if (listeners) {
        listeners.forEach((fn) => fn(payload));
      }
    } catch {
      // Ignored malformed JSON
    }
  }

  private resubscribeAll(): void {
    if (!this.client || !this.client.connected) return;
    this.topicSubscriptions.clear();

    this.topicListeners.forEach((_, topic) => {
      const sub = this.client!.subscribe(topic, (msg: IMessage) => {
        this.handleMessage(topic, msg);
      });
      this.topicSubscriptions.set(topic, sub);
    });
  }

  private calculateBackoff(): number {
    this.reconnectAttempts++;
    const delay = Math.min(
      this.baseReconnectDelay * Math.pow(1.5, this.reconnectAttempts),
      this.maxReconnectDelay,
    );
    return delay;
  }

}

export function getWebSocketUrl(): string {
  if (process.env.NEXT_PUBLIC_WS_URL) {
    return process.env.NEXT_PUBLIC_WS_URL;
  }
  if (typeof window !== 'undefined') {
    if (window.location.hostname === 'localhost' && window.location.port === '3000') {
      return 'ws://localhost:8080/ws';
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws`;
  }
  return 'ws://localhost:8080/ws';
}

// Global singleton instance for shared connection across hooks
export const wsManager = new WebSocketManager(getWebSocketUrl());
