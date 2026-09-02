package api

import (
	"context"
	"crypto/elliptic"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

func TestV2DeltaCommitsBeforeWorkspaceFanoutAndPullRecovers(t *testing.T) {
	server := newTestServer(t, 1<<20)
	target := enrollV2Device(t, server.URL, true)
	writer := enrollV2Device(t, server.URL, false)
	targetSocket := dialRealtime(t, server.URL, target.Token)
	writerSocket := dialRealtime(t, server.URL, writer.Token)

	change := v2Envelope(writer.WorkspaceID, writer.DeviceID, target.DeviceID, "change_delta_a", "mutation_delta_a", "0")
	response := bearerJSON(t, http.MethodPost, server.URL+"/v2/sync/push", writer.Token, "change_delta_a", map[string]any{"changes": []any{change}})
	if response.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(response.Body)
		response.Body.Close()
		t.Fatalf("push status=%d body=%s", response.StatusCode, body)
	}
	var pushed struct {
		Cursor  string `json:"cursor"`
		Results []struct {
			ChangeID string `json:"changeId"`
			Revision string `json:"revision"`
		} `json:"results"`
	}
	decodeResponse(t, response, &pushed)
	if pushed.Cursor == "" || len(pushed.Results) != 1 || pushed.Results[0].Revision != "1" {
		t.Fatalf("push result = %+v", pushed)
	}

	for name, conn := range map[string]*websocket.Conn{"sender": writerSocket, "target": targetSocket} {
		t.Run(name, func(t *testing.T) {
			ctx, cancel := context.WithTimeout(t.Context(), 2*time.Second)
			defer cancel()
			var frame realtimeFrame
			if err := wsjson.Read(ctx, conn, &frame); err != nil {
				t.Fatal(err)
			}
			if frame.Type != "change" || frame.Cursor != pushed.Cursor || frame.Change.ChangeID != "change_delta_a" || frame.Change.MutationID != "mutation_delta_a" || frame.Change.Revision != "1" {
				t.Fatalf("frame = %+v", frame)
			}
		})
	}

	pull := get(t, server.URL+"/v2/sync/pull?after="+target.Cursor, target.Token)
	var pulled struct {
		Changes    []v2ChangeDTO `json:"changes"`
		NextCursor string        `json:"nextCursor"`
		HasMore    bool          `json:"hasMore"`
	}
	decodeResponse(t, pull, &pulled)
	if len(pulled.Changes) != 1 || pulled.Changes[0].ChangeID != "change_delta_a" || pulled.NextCursor != pushed.Cursor || pulled.HasMore {
		t.Fatalf("pull after realtime = %+v", pulled)
	}
	legacy := bearerJSON(t, http.MethodPut, server.URL+"/v1/devices/"+target.DeviceID+"/tabs", writer.Token, "legacy_after_v2", map[string]any{
		"changeId": "legacy_after_v2", "expectedRevision": "1", "revision": "2",
		"schemaVersion": 1, "cryptoVersion": 1, "keyVersion": 1,
		"nonce": testNonce, "ciphertext": testCiphertext,
	})
	if legacy.StatusCode != http.StatusConflict {
		body, _ := io.ReadAll(legacy.Body)
		legacy.Body.Close()
		t.Fatalf("legacy write status=%d body=%s", legacy.StatusCode, body)
	}
	var legacyProblem problem
	decodeResponse(t, legacy, &legacyProblem)
	if legacyProblem.Code != "protocol_upgrade_required" {
		t.Fatalf("legacy write problem=%+v", legacyProblem)
	}
}

func TestV2RejectsUntrustedTenantAndWriterMetadata(t *testing.T) {
	server := newTestServer(t, 1<<20)
	device := enrollV2Device(t, server.URL, true)
	for _, mutation := range []func(map[string]any){
		func(value map[string]any) { value["workspaceId"] = "workspace_other" },
		func(value map[string]any) { value["deviceId"] = "device_other" },
		func(value map[string]any) { value["keyVersion"] = 2 },
	} {
		change := v2Envelope(device.WorkspaceID, device.DeviceID, device.DeviceID, "change_rejected", "mutation_rejected", "0")
		mutation(change)
		response := bearerJSON(t, http.MethodPost, server.URL+"/v2/sync/push", device.Token, "change_rejected", map[string]any{"changes": []any{change}})
		response.Body.Close()
		if response.StatusCode != http.StatusUnprocessableEntity {
			t.Fatalf("metadata mismatch status=%d", response.StatusCode)
		}
	}
}

func TestRealtimeTicketIsSingleUse(t *testing.T) {
	server := newTestServer(t, 1<<20)
	device := enrollV2Device(t, server.URL, true)
	ticket := createTicket(t, server.URL, device.Token)
	conn := dialTicket(t, server.URL, ticket)
	conn.CloseNow()

	ctx, cancel := context.WithTimeout(t.Context(), 2*time.Second)
	defer cancel()
	_, response, err := websocket.Dial(ctx, realtimeURL(server.URL, ticket), nil)
	if err == nil {
		t.Fatal("replayed ticket unexpectedly connected")
	}
	if response == nil || response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("replay response=%v error=%v", response, err)
	}
	response.Body.Close()
}

func TestRealtimeTicketExpiry(t *testing.T) {
	now := time.Unix(1_700_000_000, 0).UTC()
	tickets := newTicketStore(func() time.Time { return now })
	authenticated := store.AuthContext{AccountID: "account", WorkspaceID: "workspace", DeviceID: "device"}
	ticket, _, err := tickets.create(authenticated)
	if err != nil {
		t.Fatal(err)
	}
	now = now.Add(realtimeTicketTTL)
	if _, ok := tickets.consume(ticket); ok {
		t.Fatal("expired ticket was accepted")
	}
}

func TestRealtimeTicketRevocationAndReplacement(t *testing.T) {
	tickets := newTicketStore(time.Now)
	authenticated := store.AuthContext{AccountID: "account", WorkspaceID: "workspace", DeviceID: "device"}
	first, _, err := tickets.create(authenticated)
	if err != nil {
		t.Fatal(err)
	}
	second, _, err := tickets.create(authenticated)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := tickets.consume(first); ok {
		t.Fatal("replaced ticket was accepted")
	}
	tickets.revokeDevice(authenticated.DeviceID)
	if _, ok := tickets.consume(second); ok {
		t.Fatal("revoked device ticket was accepted")
	}
}

func TestRealtimeHubScopesWorkspacesAndDisconnectsSlowOrRevokedDevices(t *testing.T) {
	hub := newRealtimeHub()
	firstCancelled := make(chan struct{})
	first := hub.register(store.AuthContext{WorkspaceID: "workspace_a", DeviceID: "device_a"}, func() { close(firstCancelled) })
	secondCancelled := make(chan struct{})
	second := hub.register(store.AuthContext{WorkspaceID: "workspace_b", DeviceID: "device_b"}, func() { close(secondCancelled) })

	frame := realtimeFrame{Type: "change", Cursor: "epoch.1"}
	hub.publish("workspace_a", frame)
	select {
	case <-first.queue:
	default:
		t.Fatal("matching workspace did not receive frame")
	}
	select {
	case <-second.queue:
		t.Fatal("different workspace received frame")
	default:
	}

	for range realtimeQueueSize + 1 {
		hub.publish("workspace_a", frame)
	}
	select {
	case <-firstCancelled:
	case <-time.After(time.Second):
		t.Fatal("slow client was not disconnected")
	}
	hub.disconnectDevice("device_b")
	select {
	case <-secondCancelled:
	case <-time.After(time.Second):
		t.Fatal("revoked device was not disconnected")
	}
}

type v2EnrolledDevice struct {
	WorkspaceID string `json:"workspaceId"`
	DeviceID    string `json:"deviceId"`
	Token       string `json:"token"`
	Cursor      string `json:"cursor"`
}

func enrollV2Device(t *testing.T, serverURL string, first bool) v2EnrolledDevice {
	t.Helper()
	publicKey, fingerprint := publicIdentity(t, elliptic.P256())
	body := map[string]any{
		"deviceKeyFingerprint": fingerprint,
		"publicKeyAlgorithm":   "ECDH-P256-SPKI",
		"publicKey":            publicKey,
		"encryptedName":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"encryptedIcon":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"capabilities":         []string{"tabs"},
	}
	if first {
		body["recoveryEnvelope"] = map[string]any{"cryptoVersion": 1, "nonce": testNonce, "ciphertext": testCiphertext}
	}
	response := basicJSON(t, serverURL+"/v1/devices", body)
	if response.StatusCode != http.StatusCreated {
		payload, _ := io.ReadAll(response.Body)
		response.Body.Close()
		t.Fatalf("enroll status=%d body=%s", response.StatusCode, payload)
	}
	var result v2EnrolledDevice
	decodeResponse(t, response, &result)
	return result
}

func v2Envelope(workspaceID, writerID, targetID, changeID, mutationID, baseRevision string) map[string]any {
	return map[string]any{
		"changeId": changeID, "mutationId": mutationID, "workspaceId": workspaceID,
		"deviceId": writerID, "entity": "tabs", "entityId": targetID, "operation": "delta",
		"baseRevision": baseRevision, "schemaVersion": 2, "cryptoVersion": 1, "keyVersion": 1,
		"nonce": testNonce, "ciphertext": testCiphertext,
	}
}

func dialRealtime(t *testing.T, serverURL, token string) *websocket.Conn {
	t.Helper()
	return dialTicket(t, serverURL, createTicket(t, serverURL, token))
}

func createTicket(t *testing.T, serverURL, token string) string {
	t.Helper()
	response := bearerJSON(t, http.MethodPost, serverURL+"/v2/realtime/tickets", token, "", map[string]any{})
	if response.StatusCode != http.StatusCreated {
		body, _ := io.ReadAll(response.Body)
		response.Body.Close()
		t.Fatalf("ticket status=%d body=%s", response.StatusCode, body)
	}
	var value struct {
		Ticket string `json:"ticket"`
	}
	decodeResponse(t, response, &value)
	return value.Ticket
}

func dialTicket(t *testing.T, serverURL, ticket string) *websocket.Conn {
	t.Helper()
	ctx, cancel := context.WithTimeout(t.Context(), 2*time.Second)
	defer cancel()
	conn, response, err := websocket.Dial(ctx, realtimeURL(serverURL, ticket), nil)
	if err != nil {
		if response != nil {
			payload, _ := io.ReadAll(response.Body)
			response.Body.Close()
			t.Fatalf("dial status=%d body=%s error=%v", response.StatusCode, payload, err)
		}
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.CloseNow() })
	return conn
}

func realtimeURL(serverURL, ticket string) string {
	return "ws" + strings.TrimPrefix(serverURL, "http") + "/v2/realtime?ticket=" + ticket
}
