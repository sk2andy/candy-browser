package api

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/sk2andy/candy-browser/sync/server/internal/config"
	"github.com/sk2andy/candy-browser/sync/server/internal/store/sqlite"
)

const (
	testUsername   = "candy"
	testPassword   = "correct horse battery staple"
	testNonce      = "AAAAAAAAAAAAAAAA"
	testCiphertext = "AAAAAAAAAAAAAAAAAAAAAA"
)

func TestDiscoveryAndHealthAreUnauthenticated(t *testing.T) {
	server := newTestServer(t, 1<<20)
	for _, path := range []string{"/.well-known/candy-sync", "/healthz", "/readyz"} {
		response := get(t, server.URL+path, "")
		defer response.Body.Close()
		if response.StatusCode != http.StatusOK {
			t.Fatalf("GET %s status = %d", path, response.StatusCode)
		}
		if response.Header.Get("Content-Type") != "application/json" {
			t.Fatalf("GET %s content type = %q", path, response.Header.Get("Content-Type"))
		}
	}
}

func TestDiscoveryAdvertisesEncryptedDeviceIcons(t *testing.T) {
	server := newTestServer(t, 1<<20)
	response := get(t, server.URL+"/.well-known/candy-sync", "")
	var discovery struct {
		Features  []string `json:"features"`
		AllowHTTP bool     `json:"allowHttp"`
	}
	decodeResponse(t, response, &discovery)
	if !slices.Contains(discovery.Features, "encrypted-device-icons") {
		t.Fatalf("features = %v", discovery.Features)
	}
	if !slices.Contains(discovery.Features, "tab-mutations-v2") || !slices.Contains(discovery.Features, "realtime") {
		t.Fatalf("v2 features = %v", discovery.Features)
	}
	if discovery.AllowHTTP {
		t.Fatal("test server unexpectedly advertises remote HTTP")
	}
}

func TestBootstrapRejectsInvalidBasicWithProblemJSON(t *testing.T) {
	server := newTestServer(t, 1<<20)
	request, err := http.NewRequest(http.MethodGet, server.URL+"/v1/bootstrap", nil)
	if err != nil {
		t.Fatal(err)
	}
	request.SetBasicAuth(testUsername, "wrong password value")
	response := do(t, request)
	defer response.Body.Close()
	if response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("status = %d", response.StatusCode)
	}
	if response.Header.Get("Content-Type") != "application/problem+json" {
		t.Fatalf("content type = %q", response.Header.Get("Content-Type"))
	}
	var value problem
	decodeResponse(t, response, &value)
	if value.Code != "invalid_credentials" || value.RequestID == "" {
		t.Fatalf("unexpected problem: %+v", value)
	}
}

func TestEncryptedSyncLifecycle(t *testing.T) {
	server := newTestServer(t, 1<<20)
	testPublicKey, testFingerprint := publicIdentity(t, elliptic.P256())
	bootstrapRequest, err := http.NewRequest(http.MethodGet, server.URL+"/v1/bootstrap", nil)
	if err != nil {
		t.Fatal(err)
	}
	bootstrapRequest.SetBasicAuth(testUsername, testPassword)
	bootstrapResponse := do(t, bootstrapRequest)
	if bootstrapResponse.StatusCode != http.StatusOK {
		t.Fatalf("bootstrap status = %d", bootstrapResponse.StatusCode)
	}
	var bootstrap struct {
		WorkspaceID string `json:"workspaceId"`
		Initialized bool   `json:"initialized"`
		ServerEpoch string `json:"serverEpoch"`
	}
	decodeResponse(t, bootstrapResponse, &bootstrap)
	if bootstrap.WorkspaceID == "" || bootstrap.ServerEpoch == "" || bootstrap.Initialized {
		t.Fatalf("unexpected bootstrap: %+v", bootstrap)
	}

	enrollment := map[string]any{
		"deviceKeyFingerprint": testFingerprint,
		"publicKeyAlgorithm":   "ECDH-P256-SPKI",
		"publicKey":            testPublicKey,
		"encryptedName":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"encryptedIcon":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"capabilities":         []string{"tabs", "bookmarks"},
		"recoveryEnvelope": map[string]any{
			"cryptoVersion": 1,
			"nonce":         testNonce,
			"ciphertext":    testCiphertext,
		},
	}
	enrollResponse := basicJSON(t, server.URL+"/v1/devices", enrollment)
	if enrollResponse.StatusCode != http.StatusCreated {
		body, _ := io.ReadAll(enrollResponse.Body)
		t.Fatalf("enroll status = %d body=%s", enrollResponse.StatusCode, body)
	}
	var enrolled struct {
		WorkspaceID string `json:"workspaceId"`
		DeviceID    string `json:"deviceId"`
		Token       string `json:"token"`
		Cursor      string `json:"cursor"`
	}
	decodeResponse(t, enrollResponse, &enrolled)
	if enrolled.WorkspaceID != bootstrap.WorkspaceID || enrolled.DeviceID == "" || !strings.HasPrefix(enrolled.Token, "cst1_") {
		t.Fatalf("unexpected enrollment: %+v", enrolled)
	}

	change := map[string]any{
		"changeId":      "change_a",
		"deviceId":      enrolled.DeviceID,
		"entity":        "tabs",
		"entityId":      enrolled.DeviceID,
		"operation":     "snapshot",
		"baseRevision":  "0",
		"schemaVersion": 1,
		"cryptoVersion": 1,
		"keyVersion":    1,
		"nonce":         testNonce,
		"ciphertext":    testCiphertext,
	}
	pushResponse := bearerJSON(t, http.MethodPost, server.URL+"/v1/sync/push", enrolled.Token, "change_a", map[string]any{"changes": []any{change}})
	if pushResponse.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(pushResponse.Body)
		t.Fatalf("push status = %d body=%s", pushResponse.StatusCode, body)
	}
	var pushed struct {
		Cursor    string            `json:"cursor"`
		Revisions map[string]string `json:"revisions"`
	}
	decodeResponse(t, pushResponse, &pushed)
	if pushed.Cursor == "" || pushed.Revisions["change_a"] != "1" {
		t.Fatalf("unexpected push: %+v", pushed)
	}

	retryResponse := bearerJSON(t, http.MethodPost, server.URL+"/v1/sync/push", enrolled.Token, "change_a", map[string]any{"changes": []any{change}})
	if retryResponse.StatusCode != http.StatusOK {
		t.Fatalf("retry status = %d", retryResponse.StatusCode)
	}
	retryResponse.Body.Close()

	mismatchedKey := bearerJSON(t, http.MethodPost, server.URL+"/v1/sync/push", enrolled.Token, "different_key", map[string]any{"changes": []any{change}})
	if mismatchedKey.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("mismatched idempotency key status = %d", mismatchedKey.StatusCode)
	}
	mismatchedKey.Body.Close()

	tampered := make(map[string]any, len(change))
	for key, value := range change {
		tampered[key] = value
	}
	tampered["ciphertext"] = "BBBBBBBBBBBBBBBBBBBBBB"
	tamperedRetry := bearerJSON(t, http.MethodPost, server.URL+"/v1/sync/push", enrolled.Token, "change_a", map[string]any{"changes": []any{tampered}})
	if tamperedRetry.StatusCode != http.StatusConflict {
		t.Fatalf("tampered idempotent retry status = %d", tamperedRetry.StatusCode)
	}
	tamperedRetry.Body.Close()

	pullRequest, err := http.NewRequest(http.MethodGet, server.URL+"/v1/sync/pull?after="+enrolled.Cursor, nil)
	if err != nil {
		t.Fatal(err)
	}
	pullRequest.Header.Set("Authorization", "Bearer "+enrolled.Token)
	pullResponse := do(t, pullRequest)
	if pullResponse.StatusCode != http.StatusOK {
		t.Fatalf("pull status = %d", pullResponse.StatusCode)
	}
	var pulled struct {
		Changes    []changeDTO `json:"changes"`
		NextCursor string      `json:"nextCursor"`
	}
	decodeResponse(t, pullResponse, &pulled)
	if len(pulled.Changes) != 1 || pulled.Changes[0].Ciphertext != testCiphertext || pulled.Changes[0].Revision != "1" {
		t.Fatalf("unexpected pull: %+v", pulled)
	}

	ackResponse := bearerJSON(t, http.MethodPost, server.URL+"/v1/sync/ack", enrolled.Token, "", map[string]string{"cursor": pulled.NextCursor})
	if ackResponse.StatusCode != http.StatusNoContent {
		t.Fatalf("ack status = %d", ackResponse.StatusCode)
	}
	ackResponse.Body.Close()

	snapshotRequest, err := http.NewRequest(http.MethodGet, server.URL+"/v1/sync/snapshot", nil)
	if err != nil {
		t.Fatal(err)
	}
	snapshotRequest.Header.Set("Authorization", "Bearer "+enrolled.Token)
	snapshotResponse := do(t, snapshotRequest)
	if snapshotResponse.StatusCode != http.StatusOK {
		t.Fatalf("snapshot status = %d", snapshotResponse.StatusCode)
	}
	var snapshot struct {
		Cursor string           `json:"cursor"`
		Tabs   []map[string]any `json:"tabSnapshots"`
	}
	decodeResponse(t, snapshotResponse, &snapshot)
	if snapshot.Cursor != pushed.Cursor || len(snapshot.Tabs) != 1 {
		t.Fatalf("unexpected snapshot: %+v", snapshot)
	}
	if snapshot.Tabs[0]["changeId"] != "change_a" || snapshot.Tabs[0]["baseRevision"] != "0" || snapshot.Tabs[0]["entity"] != "tabs" {
		t.Fatalf("snapshot is missing authenticated metadata: %+v", snapshot.Tabs[0])
	}

	devicesRequest, err := http.NewRequest(http.MethodGet, server.URL+"/v1/devices", nil)
	if err != nil {
		t.Fatal(err)
	}
	devicesRequest.Header.Set("Authorization", "Bearer "+enrolled.Token)
	devicesResponse := do(t, devicesRequest)
	if devicesResponse.StatusCode != http.StatusOK {
		t.Fatalf("devices status = %d", devicesResponse.StatusCode)
	}
	var devices struct {
		Devices []struct {
			EncryptedIcon encryptedValueDTO `json:"encryptedIcon"`
		} `json:"devices"`
	}
	decodeResponse(t, devicesResponse, &devices)
	if len(devices.Devices) != 1 || devices.Devices[0].EncryptedIcon.Nonce != testNonce || devices.Devices[0].EncryptedIcon.Ciphertext != testCiphertext {
		t.Fatalf("unexpected encrypted device icon: %+v", devices)
	}

	revokeRequest, err := http.NewRequest(http.MethodDelete, server.URL+"/v1/devices/"+enrolled.DeviceID, nil)
	if err != nil {
		t.Fatal(err)
	}
	revokeRequest.SetBasicAuth(testUsername, testPassword)
	revokeResponse := do(t, revokeRequest)
	if revokeResponse.StatusCode != http.StatusNoContent {
		t.Fatalf("revoke status = %d", revokeResponse.StatusCode)
	}
	revokeResponse.Body.Close()

	afterRevoke := get(t, server.URL+"/v1/devices", enrolled.Token)
	defer afterRevoke.Body.Close()
	if afterRevoke.StatusCode != http.StatusUnauthorized {
		t.Fatalf("revoked token status = %d", afterRevoke.StatusCode)
	}
}

func TestBasicAuthenticationRateLimit(t *testing.T) {
	server := newTestServer(t, 1<<20)
	for attempt := 1; attempt <= 6; attempt++ {
		request, err := http.NewRequest(http.MethodGet, server.URL+"/v1/bootstrap", nil)
		if err != nil {
			t.Fatal(err)
		}
		request.SetBasicAuth(testUsername, "wrong password value")
		response := do(t, request)
		response.Body.Close()
		if attempt < 6 && response.StatusCode != http.StatusUnauthorized {
			t.Fatalf("attempt %d status = %d", attempt, response.StatusCode)
		}
		if attempt == 6 && (response.StatusCode != http.StatusTooManyRequests || response.Header.Get("Retry-After") == "") {
			t.Fatalf("rate limit status = %d retry-after = %q", response.StatusCode, response.Header.Get("Retry-After"))
		}
	}
}

func TestRequestBodyLimit(t *testing.T) {
	server := newTestServer(t, 256)
	request, err := http.NewRequest(http.MethodPost, server.URL+"/v1/devices", strings.NewReader(`{"padding":"`+strings.Repeat("a", 512)+`"}`))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Content-Type", "application/json")
	request.SetBasicAuth(testUsername, testPassword)
	response := do(t, request)
	defer response.Body.Close()
	if response.StatusCode != http.StatusRequestEntityTooLarge {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("status = %d body=%s", response.StatusCode, body)
	}
}

func TestEnrollmentRequiresEncryptedDeviceIcon(t *testing.T) {
	server := newTestServer(t, 1<<20)
	testPublicKey, testFingerprint := publicIdentity(t, elliptic.P256())
	enrollment := map[string]any{
		"deviceKeyFingerprint": testFingerprint,
		"publicKeyAlgorithm":   "ECDH-P256-SPKI",
		"publicKey":            testPublicKey,
		"encryptedName":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"capabilities":         []string{"tabs"},
		"recoveryEnvelope": map[string]any{
			"cryptoVersion": 1,
			"nonce":         testNonce,
			"ciphertext":    testCiphertext,
		},
	}
	response := basicJSON(t, server.URL+"/v1/devices", enrollment)
	defer response.Body.Close()
	if response.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("status = %d", response.StatusCode)
	}
	var value problem
	if err := json.NewDecoder(response.Body).Decode(&value); err != nil {
		t.Fatal(err)
	}
	if value.Code != "invalid_encrypted_icon" {
		t.Fatalf("unexpected problem: %+v", value)
	}
}

func TestEnrollmentRejectsInvalidPublicDeviceIdentity(t *testing.T) {
	server := newTestServer(t, 1<<20)
	validKey, validFingerprint := publicIdentity(t, elliptic.P256())
	p384Key, p384Fingerprint := publicIdentity(t, elliptic.P384())
	cases := []struct {
		name        string
		algorithm   string
		publicKey   string
		fingerprint string
	}{
		{name: "algorithm", algorithm: "X25519", publicKey: validKey, fingerprint: validFingerprint},
		{name: "malformed SPKI", algorithm: "ECDH-P256-SPKI", publicKey: strings.Repeat("A", 122), fingerprint: validFingerprint},
		{name: "wrong curve", algorithm: "ECDH-P256-SPKI", publicKey: p384Key, fingerprint: p384Fingerprint},
		{name: "mismatched fingerprint", algorithm: "ECDH-P256-SPKI", publicKey: validKey, fingerprint: base64.RawURLEncoding.EncodeToString(make([]byte, sha256.Size))},
		{name: "missing fingerprint", algorithm: "ECDH-P256-SPKI", publicKey: validKey, fingerprint: ""},
	}
	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			enrollment := map[string]any{
				"deviceKeyFingerprint": testCase.fingerprint,
				"publicKeyAlgorithm":   testCase.algorithm,
				"publicKey":            testCase.publicKey,
				"encryptedName":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
				"encryptedIcon":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
				"capabilities":         []string{"tabs"},
				"recoveryEnvelope": map[string]any{
					"cryptoVersion": 1,
					"nonce":         testNonce,
					"ciphertext":    testCiphertext,
				},
			}
			response := basicJSON(t, server.URL+"/v1/devices", enrollment)
			defer response.Body.Close()
			if response.StatusCode != http.StatusUnprocessableEntity {
				t.Fatalf("status = %d", response.StatusCode)
			}
			var value problem
			if err := json.NewDecoder(response.Body).Decode(&value); err != nil {
				t.Fatal(err)
			}
			if value.Code != "invalid_public_key" {
				t.Fatalf("unexpected problem: %+v", value)
			}
		})
	}
}

func TestEnrollmentRejectsCapabilitiesOutsideProtocolSchema(t *testing.T) {
	server := newTestServer(t, 1<<20)
	publicKey, fingerprint := publicIdentity(t, elliptic.P256())
	for _, capabilities := range [][]string{{"tabs", "tabs"}, {"tabs", "history"}, {}, {"groups", "unknown"}} {
		enrollment := map[string]any{
			"deviceKeyFingerprint": fingerprint,
			"publicKeyAlgorithm":   "ECDH-P256-SPKI",
			"publicKey":            publicKey,
			"encryptedName":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
			"encryptedIcon":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
			"capabilities":         capabilities,
			"recoveryEnvelope": map[string]any{
				"cryptoVersion": 1,
				"nonce":         testNonce,
				"ciphertext":    testCiphertext,
			},
		}
		response := basicJSON(t, server.URL+"/v1/devices", enrollment)
		defer response.Body.Close()
		if response.StatusCode != http.StatusUnprocessableEntity {
			t.Fatalf("capabilities=%v status=%d", capabilities, response.StatusCode)
		}
		var value problem
		if err := json.NewDecoder(response.Body).Decode(&value); err != nil {
			t.Fatal(err)
		}
		if value.Code != "invalid_capabilities" {
			t.Fatalf("capabilities=%v problem=%+v", capabilities, value)
		}
	}
}

func TestActiveDeviceCanCASWriteAnotherDeviceTabProfile(t *testing.T) {
	server := newTestServer(t, 1<<20)
	publicKey, fingerprint := publicIdentity(t, elliptic.P256())
	firstEnrollment := map[string]any{
		"deviceKeyFingerprint": fingerprint,
		"publicKeyAlgorithm":   "ECDH-P256-SPKI",
		"publicKey":            publicKey,
		"encryptedName":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"encryptedIcon":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"capabilities":         []string{"tabs"},
		"recoveryEnvelope":     map[string]any{"cryptoVersion": 1, "nonce": testNonce, "ciphertext": testCiphertext},
	}
	var target struct {
		DeviceID string `json:"deviceId"`
		Token    string `json:"token"`
	}
	decodeResponse(t, basicJSON(t, server.URL+"/v1/devices", firstEnrollment), &target)
	secondEnrollment := map[string]any{
		"deviceKeyFingerprint": fingerprint,
		"publicKeyAlgorithm":   "ECDH-P256-SPKI",
		"publicKey":            publicKey,
		"encryptedName":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"encryptedIcon":        map[string]string{"nonce": testNonce, "ciphertext": testCiphertext},
		"capabilities":         []string{"tabs"},
	}
	var writer struct {
		DeviceID string `json:"deviceId"`
		Token    string `json:"token"`
		Cursor   string `json:"cursor"`
	}
	decodeResponse(t, basicJSON(t, server.URL+"/v1/devices", secondEnrollment), &writer)
	body := map[string]any{
		"changeId":         "android-edits-desktop-1",
		"expectedRevision": "0",
		"revision":         "1",
		"schemaVersion":    1,
		"cryptoVersion":    1,
		"keyVersion":       1,
		"nonce":            testNonce,
		"ciphertext":       testCiphertext,
	}
	response := bearerJSON(t, http.MethodPut, server.URL+"/v1/devices/"+target.DeviceID+"/tabs", writer.Token, "android-edits-desktop-1", body)
	if response.StatusCode != http.StatusOK {
		payload, _ := io.ReadAll(response.Body)
		response.Body.Close()
		t.Fatalf("cross-device PUT status=%d body=%s", response.StatusCode, payload)
	}
	response.Body.Close()
	pull := get(t, server.URL+"/v1/sync/pull?after="+writer.Cursor, writer.Token)
	var result struct {
		Changes []changeDTO `json:"changes"`
	}
	decodeResponse(t, pull, &result)
	if len(result.Changes) != 1 || result.Changes[0].DeviceID != writer.DeviceID || result.Changes[0].EntityID != target.DeviceID {
		t.Fatalf("writer/target metadata = %+v", result.Changes)
	}
	conflict := bearerJSON(t, http.MethodPut, server.URL+"/v1/devices/"+target.DeviceID+"/tabs", writer.Token, "android-edits-desktop-2", map[string]any{
		"changeId": "android-edits-desktop-2", "expectedRevision": "0", "revision": "1",
		"schemaVersion": 1, "cryptoVersion": 1, "keyVersion": 1, "nonce": testNonce, "ciphertext": testCiphertext,
	})
	defer conflict.Body.Close()
	if conflict.StatusCode != http.StatusConflict {
		t.Fatalf("stale cross-device PUT status=%d", conflict.StatusCode)
	}
}

func publicIdentity(t *testing.T, curve elliptic.Curve) (string, string) {
	t.Helper()
	x, y := curve.ScalarBaseMult([]byte{1})
	spki, err := x509.MarshalPKIXPublicKey(&ecdsa.PublicKey{Curve: curve, X: x, Y: y})
	if err != nil {
		t.Fatal(err)
	}
	digest := sha256.Sum256(spki)
	return base64.RawURLEncoding.EncodeToString(spki), base64.RawURLEncoding.EncodeToString(digest[:])
}

func newTestServer(t *testing.T, maxBody int64) *httptest.Server {
	t.Helper()
	repository, err := sqlite.Open(t.Context(), filepath.Join(t.TempDir(), "candy-sync.sqlite3"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := repository.Close(); err != nil {
			t.Errorf("close repository: %v", err)
		}
	})
	cfg := config.Config{
		Username:     testUsername,
		Password:     testPassword,
		MaxBodyBytes: maxBody,
		MaxBatch:     250,
	}
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	server := httptest.NewServer(New(cfg, repository, logger))
	t.Cleanup(server.Close)
	return server
}

func basicJSON(t *testing.T, url string, value any) *http.Response {
	t.Helper()
	body, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	request, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Content-Type", "application/json")
	request.SetBasicAuth(testUsername, testPassword)
	return do(t, request)
}

func bearerJSON(t *testing.T, method, url, token, idempotencyKey string, value any) *http.Response {
	t.Helper()
	body, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	request, err := http.NewRequest(method, url, bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Authorization", "Bearer "+token)
	if idempotencyKey != "" {
		request.Header.Set("Idempotency-Key", idempotencyKey)
	}
	return do(t, request)
}

func get(t *testing.T, url, token string) *http.Response {
	t.Helper()
	request, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		t.Fatal(err)
	}
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	return do(t, request)
}

func do(t *testing.T, request *http.Request) *http.Response {
	t.Helper()
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	return response
}

func decodeResponse(t *testing.T, response *http.Response, destination any) {
	t.Helper()
	defer response.Body.Close()
	if err := json.NewDecoder(response.Body).Decode(destination); err != nil {
		t.Fatalf("decode response: %v", err)
	}
}

func TestCursorParserRejectsMalformedValues(t *testing.T) {
	for _, value := range []string{"epoch", ".1", "epoch.-1", "epoch.+1", "epoch.1.2"} {
		t.Run(fmt.Sprintf("%q", value), func(t *testing.T) {
			if _, _, err := parseCursor(value); err == nil {
				t.Fatalf("parseCursor(%q) succeeded", value)
			}
		})
	}
}

func TestRateLimitPeerUsesRightmostTrustedProxyAddress(t *testing.T) {
	server := &Server{cfg: config.Config{ClientIPHeader: "X-Forwarded-For"}}
	request := httptest.NewRequest(http.MethodGet, "http://sync.example/v1/bootstrap", nil)
	request.RemoteAddr = "10.0.0.2:1234"
	request.Header.Set("X-Forwarded-For", "198.51.100.9, 203.0.113.7")
	if peer := server.rateLimitPeer(request); peer != "203.0.113.7" {
		t.Fatalf("peer = %q", peer)
	}
	request.Header.Set("X-Forwarded-For", "not-an-ip")
	if peer := server.rateLimitPeer(request); peer != "10.0.0.2" {
		t.Fatalf("fallback peer = %q", peer)
	}
}
