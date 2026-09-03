package api

import (
	"context"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

const (
	realtimeTicketTTL = 45 * time.Second
	realtimeQueueSize = 32
)

type realtimeTicket struct {
	auth      store.AuthContext
	expiresAt time.Time
}

type ticketStore struct {
	mu      sync.Mutex
	now     func() time.Time
	tickets map[string]realtimeTicket
}

func newTicketStore(now func() time.Time) *ticketStore {
	return &ticketStore{now: now, tickets: make(map[string]realtimeTicket)}
}

func (s *ticketStore) create(auth store.AuthContext) (string, time.Time, error) {
	value, err := randomIdentifier("rt2_", 32)
	if err != nil {
		return "", time.Time{}, err
	}
	expiresAt := s.now().Add(realtimeTicketTTL).UTC()
	s.mu.Lock()
	defer s.mu.Unlock()
	for key, ticket := range s.tickets {
		if !s.now().Before(ticket.expiresAt) || ticket.auth.DeviceID == auth.DeviceID {
			delete(s.tickets, key)
		}
	}
	s.tickets[value] = realtimeTicket{auth: auth, expiresAt: expiresAt}
	return value, expiresAt, nil
}

func (s *ticketStore) revokeDevice(deviceID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for value, ticket := range s.tickets {
		if ticket.auth.DeviceID == deviceID {
			delete(s.tickets, value)
		}
	}
}

func (s *ticketStore) consume(value string) (store.AuthContext, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	ticket, ok := s.tickets[value]
	delete(s.tickets, value)
	if !ok || !s.now().Before(ticket.expiresAt) {
		return store.AuthContext{}, false
	}
	return ticket.auth, true
}

type realtimeFrame struct {
	Type   string      `json:"type"`
	Cursor string      `json:"cursor"`
	Change v2ChangeDTO `json:"change"`
}

type realtimeClient struct {
	auth   store.AuthContext
	queue  chan realtimeFrame
	cancel context.CancelFunc
}

type realtimeHub struct {
	mu      sync.Mutex
	clients map[*realtimeClient]struct{}
}

func newRealtimeHub() *realtimeHub {
	return &realtimeHub{clients: make(map[*realtimeClient]struct{})}
}

func (h *realtimeHub) register(auth store.AuthContext, cancel context.CancelFunc) *realtimeClient {
	client := &realtimeClient{auth: auth, queue: make(chan realtimeFrame, realtimeQueueSize), cancel: cancel}
	h.mu.Lock()
	for existing := range h.clients {
		if existing.auth.DeviceID == auth.DeviceID {
			delete(h.clients, existing)
			existing.cancel()
		}
	}
	h.clients[client] = struct{}{}
	h.mu.Unlock()
	return client
}

func (h *realtimeHub) remove(client *realtimeClient) {
	h.mu.Lock()
	delete(h.clients, client)
	h.mu.Unlock()
}

func (h *realtimeHub) publish(workspaceID string, frame realtimeFrame) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for client := range h.clients {
		if client.auth.WorkspaceID != workspaceID {
			continue
		}
		select {
		case client.queue <- frame:
		default:
			delete(h.clients, client)
			client.cancel()
		}
	}
}

func (h *realtimeHub) disconnectDevice(deviceID string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for client := range h.clients {
		if client.auth.DeviceID == deviceID {
			delete(h.clients, client)
			client.cancel()
		}
	}
}

func (s *Server) createRealtimeTicket(w http.ResponseWriter, r *http.Request, authenticated store.AuthContext) {
	ticket, expiresAt, err := s.tickets.create(authenticated)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	writeJSON(w, http.StatusCreated, map[string]string{
		"ticket":    ticket,
		"expiresAt": expiresAt.Format(time.RFC3339Nano),
	})
}

func (s *Server) realtime(w http.ResponseWriter, r *http.Request) {
	ticket := r.URL.Query().Get("ticket")
	if validateIdentifier(ticket, 128) != nil || len(r.URL.Query()) != 1 || len(r.URL.Query()["ticket"]) != 1 {
		writeProblem(w, r, http.StatusUnauthorized, "invalid_realtime_ticket", "valid single-use realtime ticket required")
		return
	}
	authenticated, ok := s.tickets.consume(ticket)
	if !ok {
		writeProblem(w, r, http.StatusUnauthorized, "invalid_realtime_ticket", "valid single-use realtime ticket required")
		return
	}
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		// Authentication uses a one-time ticket, never ambient browser cookies.
		// WebExtension origins therefore do not create a cross-site request risk.
		InsecureSkipVerify: true,
		CompressionMode:    websocket.CompressionDisabled,
	})
	if err != nil {
		return
	}
	defer conn.CloseNow()
	conn.SetReadLimit(1024)

	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()
	client := s.hub.register(authenticated, cancel)
	defer s.hub.remove(client)

	readDone := make(chan struct{})
	go func() {
		defer close(readDone)
		for {
			_, _, err := conn.Read(ctx)
			if err != nil {
				cancel()
				return
			}
		}
	}()

	ping := time.NewTicker(20 * time.Second)
	defer ping.Stop()
	for {
		select {
		case <-ctx.Done():
			_ = conn.Close(websocket.StatusNormalClosure, "connection closed")
			return
		case <-readDone:
			return
		case frame := <-client.queue:
			writeCtx, writeCancel := context.WithTimeout(ctx, 5*time.Second)
			err := wsjson.Write(writeCtx, conn, frame)
			writeCancel()
			if err != nil {
				return
			}
		case <-ping.C:
			pingCtx, pingCancel := context.WithTimeout(ctx, 5*time.Second)
			err := conn.Ping(pingCtx)
			pingCancel()
			if err != nil {
				return
			}
		}
	}
}
