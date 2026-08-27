import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

export interface CandidateRealtimeEvent {
  eventId: string;
  type: 'NOTIFICATION_UPDATED' | 'APPLICATION_UPDATED' | 'REALTIME_RECONNECTED' | string;
  entityId?: string | null;
  occurredAt: string;
  version: number;
}

type Listener = (event: CandidateRealtimeEvent) => void;

function websocketUrl() {
  const apiBase = import.meta.env.VITE_API_URL || '/api';
  if (/^https?:\/\//i.test(apiBase)) {
    return `${apiBase.replace(/^http/i, 'ws').replace(/\/$/, '')}/ws`;
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const normalized = apiBase.startsWith('/') ? apiBase : `/${apiBase}`;
  return `${protocol}//${window.location.host}${normalized.replace(/\/$/, '')}/ws`;
}

class CandidateRealtimeClient {
  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private listeners = new Set<Listener>();
  private seenEventIds = new Set<string>();
  private connectedOnce = false;

  subscribe(listener: Listener) {
    this.listeners.add(listener);
    this.ensureConnected();
    return () => {
      this.listeners.delete(listener);
      if (this.listeners.size === 0) this.disconnect();
    };
  }

  private ensureConnected() {
    if (this.client?.active || localStorage.getItem('role') !== 'CANDIDATE') return;
    const token = localStorage.getItem('token');
    if (!token) return;

    this.client = new Client({
      brokerURL: websocketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
    });
    this.client.onConnect = () => {
      this.subscription?.unsubscribe();
      this.subscription = this.client?.subscribe('/user/queue/candidate-events', (message) => {
        this.handleMessage(message);
      }) || null;
      if (this.connectedOnce) {
        this.emit({
          eventId: `reconnect-${Date.now()}`,
          type: 'REALTIME_RECONNECTED',
          occurredAt: new Date().toISOString(),
          version: Date.now(),
        });
      }
      this.connectedOnce = true;
    };
    this.client.activate();
  }

  private handleMessage(message: IMessage) {
    try {
      const event = JSON.parse(message.body) as CandidateRealtimeEvent;
      if (!event.eventId || this.seenEventIds.has(event.eventId)) return;
      this.seenEventIds.add(event.eventId);
      if (this.seenEventIds.size > 200) {
        const oldest = this.seenEventIds.values().next().value as string | undefined;
        if (oldest) this.seenEventIds.delete(oldest);
      }
      this.emit(event);
    } catch {
      // REST remains the source of truth when a malformed event is received.
    }
  }

  private emit(event: CandidateRealtimeEvent) {
    this.listeners.forEach((listener) => listener(event));
  }

  private disconnect() {
    this.subscription?.unsubscribe();
    this.subscription = null;
    const client = this.client;
    this.client = null;
    if (client?.active) void client.deactivate();
  }
}

export const candidateRealtime = new CandidateRealtimeClient();
