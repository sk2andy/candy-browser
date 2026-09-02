import type { CommittedTabDelta } from "../core/models.js";
import { CandySyncApiClient, parseCommittedTabDelta } from "../protocol/api-client.js";

export interface RealtimeChangeFrame {
  type: "change";
  cursor: string;
  change: CommittedTabDelta;
}

export function reconnectDelayMs(attempt: number, random = Math.random()): number {
  const boundedAttempt = Math.min(Math.max(0, attempt), 6);
  const base = Math.min(60_000, 1_000 * 2 ** boundedAttempt);
  const jitter = Math.floor(base * 0.25 * Math.min(1, Math.max(0, random)));
  return base + jitter;
}

export function parseRealtimeFrame(data: unknown): RealtimeChangeFrame | null {
  if (typeof data !== "string" || data.length > 600_000) throw new Error("Invalid realtime frame");
  const raw = JSON.parse(data) as unknown;
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new Error("Invalid realtime frame");
  const value = raw as Record<string, unknown>;
  if (value.type === "pong") return null;
  if (value.type !== "change" || typeof value.cursor !== "string" || value.cursor.length > 4_096) {
    throw new Error("Unsupported realtime frame");
  }
  return { type: "change", cursor: value.cursor, change: parseCommittedTabDelta(value.change) };
}

type SocketFactory = (url: string) => WebSocket;

export class CandySyncRealtimeClient {
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private keepaliveTimer: ReturnType<typeof setInterval> | null = null;
  private attempt = 0;
  private running = false;
  private eventTail: Promise<void> = Promise.resolve();

  constructor(
    private readonly api: CandySyncApiClient,
    private readonly token: string,
    private readonly onChange: (frame: RealtimeChangeFrame) => Promise<void>,
    private readonly socketFactory: SocketFactory = (url) => new WebSocket(url),
    private readonly random: () => number = Math.random,
  ) {}

  start(): void {
    if (this.running) return;
    this.running = true;
    void this.connect();
  }

  stop(): void {
    this.running = false;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.keepaliveTimer) clearInterval(this.keepaliveTimer);
    this.reconnectTimer = null;
    this.keepaliveTimer = null;
    const socket = this.socket;
    this.socket = null;
    if (socket && socket.readyState < 2) socket.close(1000, "client stopped");
  }

  private async connect(): Promise<void> {
    if (!this.running) return;
    try {
      const ticket = await this.api.createRealtimeTicket(this.token);
      if (!this.running) return;
      const socket = this.socketFactory(this.api.realtimeUrl(ticket.ticket));
      this.socket = socket;
      socket.addEventListener("open", () => {
        if (socket !== this.socket) return;
        this.attempt = 0;
        this.keepaliveTimer = setInterval(() => {
          if (socket.readyState === 1) socket.send(JSON.stringify({ type: "ping" }));
        }, 20_000);
      });
      socket.addEventListener("message", (event) => {
        if (socket !== this.socket) return;
        this.eventTail = this.eventTail.then(async () => {
          const frame = parseRealtimeFrame(event.data);
          if (frame) await this.onChange(frame);
        }).catch(() => { socket.close(1011, "invalid event"); });
      });
      socket.addEventListener("close", () => {
        if (socket !== this.socket) return;
        this.socket = null;
        if (this.keepaliveTimer) clearInterval(this.keepaliveTimer);
        this.keepaliveTimer = null;
        this.scheduleReconnect();
      });
      socket.addEventListener("error", () => { socket.close(); });
    } catch {
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (!this.running || this.reconnectTimer) return;
    const delay = reconnectDelayMs(this.attempt++, this.random());
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      void this.connect();
    }, delay);
  }
}
