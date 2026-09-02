package api

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/sk2andy/candy-browser/sync/server/internal/auth"
	"github.com/sk2andy/candy-browser/sync/server/internal/config"
	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

type Server struct {
	cfg     config.Config
	store   store.Repository
	auth    *auth.Authenticator
	limiter *auth.AttemptLimiter
	logger  *slog.Logger
	now     func() time.Time
}

type problem struct {
	Type      string `json:"type"`
	Title     string `json:"title"`
	Status    int    `json:"status"`
	Code      string `json:"code"`
	Detail    string `json:"detail,omitempty"`
	RequestID string `json:"requestId,omitempty"`
}

type requestContextKey string

const requestIDKey requestContextKey = "request-id"

func New(cfg config.Config, repository store.Repository, logger *slog.Logger) http.Handler {
	if logger == nil {
		logger = slog.Default()
	}
	server := &Server{
		cfg:     cfg,
		store:   repository,
		auth:    auth.New(cfg.Username, cfg.Password),
		limiter: auth.NewAttemptLimiter(),
		logger:  logger,
		now:     time.Now,
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /.well-known/candy-sync", server.discovery)
	mux.HandleFunc("GET /healthz", server.health)
	mux.HandleFunc("GET /readyz", server.ready)
	mux.HandleFunc("GET /v1/bootstrap", server.requireBasic(server.bootstrap))
	mux.HandleFunc("POST /v1/devices", server.requireBasic(server.enrollDevice))
	mux.HandleFunc("GET /v1/devices", server.requireBearer(server.listDevices))
	mux.HandleFunc("DELETE /v1/devices/{deviceId}", server.requireBasic(server.revokeDevice))
	mux.HandleFunc("POST /v1/sync/push", server.requireBearer(server.push))
	mux.HandleFunc("GET /v1/sync/pull", server.requireBearer(server.pull))
	mux.HandleFunc("POST /v1/sync/ack", server.requireBearer(server.ack))
	mux.HandleFunc("GET /v1/sync/snapshot", server.requireBearer(server.snapshot))
	mux.HandleFunc("PUT /v1/devices/{deviceId}/tabs", server.requireBearer(server.putTabs))
	return server.requestMiddleware(mux)
}

func (s *Server) requestMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestID, err := randomIdentifier("req_", 12)
		if err != nil {
			writeProblem(w, r, http.StatusInternalServerError, "internal_error", "request identity unavailable")
			return
		}
		ctx := context.WithValue(r.Context(), requestIDKey, requestID)
		started := s.now()
		wrapped := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(wrapped, r.WithContext(ctx))
		s.logger.InfoContext(ctx, "http request",
			"request_id", requestID,
			"method", r.Method,
			"path", r.URL.Path,
			"status", wrapped.status,
			"duration_ms", s.now().Sub(started).Milliseconds(),
		)
	})
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (w *statusWriter) WriteHeader(status int) {
	w.status = status
	w.ResponseWriter.WriteHeader(status)
}

func (s *Server) requireBasic(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		peer := s.rateLimitPeer(r)
		if allowed, retry := s.limiter.Allow(peer); !allowed {
			w.Header().Set("Retry-After", strconv.Itoa(max(1, int(math.Ceil(retry.Seconds())))))
			writeProblem(w, r, http.StatusTooManyRequests, "rate_limited", "too many authentication attempts")
			return
		}
		username, password, ok := r.BasicAuth()
		if !ok || !s.auth.CheckBasic(username, password) {
			s.limiter.Failure(peer)
			w.Header().Set("WWW-Authenticate", `Basic realm="Candy Sync", charset="UTF-8"`)
			writeProblem(w, r, http.StatusUnauthorized, "invalid_credentials", "valid server credentials required")
			return
		}
		s.limiter.Success(peer)
		next(w, r)
	}
}

func (s *Server) rateLimitPeer(r *http.Request) string {
	peer := r.RemoteAddr
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		peer = host
	}
	if s.cfg.ClientIPHeader == "" {
		return peer
	}
	forwarded := r.Header.Get(s.cfg.ClientIPHeader)
	if s.cfg.ClientIPHeader == "X-Forwarded-For" {
		parts := strings.Split(forwarded, ",")
		forwarded = strings.TrimSpace(parts[len(parts)-1])
	}
	if parsed := net.ParseIP(strings.TrimSpace(forwarded)); parsed != nil {
		return parsed.String()
	}
	return peer
}

type deviceHandler func(http.ResponseWriter, *http.Request, string)

func (s *Server) requireBearer(next deviceHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		header := r.Header.Get("Authorization")
		if !strings.HasPrefix(header, "Bearer ") || strings.Contains(strings.TrimPrefix(header, "Bearer "), " ") {
			writeProblem(w, r, http.StatusUnauthorized, "invalid_token", "valid device token required")
			return
		}
		selector, candidateHash, err := s.auth.ParseAndHashToken(strings.TrimPrefix(header, "Bearer "))
		if err != nil {
			writeProblem(w, r, http.StatusUnauthorized, "invalid_token", "valid device token required")
			return
		}
		token, err := s.store.Token(r.Context(), selector)
		if err != nil || !auth.EqualTokenHash(candidateHash, token.Hash) || token.Revoked != nil || token.Expires != nil && !s.now().Before(*token.Expires) {
			writeProblem(w, r, http.StatusUnauthorized, "invalid_token", "valid device token required")
			return
		}
		next(w, r, token.DeviceID)
	}
}

func (s *Server) discovery(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"protocol":  "candy-sync",
		"versions":  []int{1},
		"allowHttp": s.cfg.AllowHTTP,
		"features":  []string{"e2ee", "delta-sync", "encrypted-snapshot", "tab-snapshots", "encrypted-device-icons", "editable-tab-profiles"},
		"limits": map[string]any{
			"batchChanges": s.cfg.MaxBatch,
			"payloadBytes": s.cfg.MaxBodyBytes,
			"devices":      1000,
		},
	})
}

func (s *Server) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *Server) ready(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := s.store.Ready(ctx); err != nil {
		writeProblem(w, r, http.StatusServiceUnavailable, "not_ready", "database is not ready")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ready"})
}

type recoveryEnvelopeDTO struct {
	CryptoVersion int    `json:"cryptoVersion"`
	Nonce         string `json:"nonce"`
	Ciphertext    string `json:"ciphertext"`
}

func (s *Server) bootstrap(w http.ResponseWriter, r *http.Request) {
	value, err := s.store.Bootstrap(r.Context())
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	var recovery any
	if value.Initialized {
		recovery = recoveryEnvelopeDTO{
			CryptoVersion: value.RecoveryCryptoVersion,
			Nonce:         value.RecoveryNonce,
			Ciphertext:    value.RecoveryCiphertext,
		}
	}
	w.Header().Set("Cache-Control", "no-store")
	writeJSON(w, http.StatusOK, map[string]any{
		"protocolVersion": 1,
		"cryptoVersion":   1,
		"workspaceId":     value.WorkspaceID,
		"serverEpoch":     value.ServerEpoch,
		"initialized":     value.Initialized,
		"kdf": map[string]any{
			"algorithm":   value.KDFAlgorithm,
			"salt":        value.KDFSalt,
			"memoryKiB":   value.KDFMemoryKiB,
			"iterations":  value.KDFIterations,
			"parallelism": value.KDFParallelism,
			"keyBytes":    32,
		},
		"recoveryEnvelope": recovery,
	})
}

type encryptedValueDTO struct {
	Nonce      string `json:"nonce"`
	Ciphertext string `json:"ciphertext"`
}

type enrollDeviceRequest struct {
	DeviceID             string               `json:"deviceId"`
	DeviceKeyFingerprint string               `json:"deviceKeyFingerprint,omitempty"`
	PublicKeyAlgorithm   string               `json:"publicKeyAlgorithm"`
	PublicKey            string               `json:"publicKey"`
	EncryptedName        encryptedValueDTO    `json:"encryptedName"`
	EncryptedIcon        encryptedValueDTO    `json:"encryptedIcon"`
	Capabilities         []string             `json:"capabilities"`
	RecoveryEnvelope     *recoveryEnvelopeDTO `json:"recoveryEnvelope,omitempty"`
}

func (s *Server) enrollDevice(w http.ResponseWriter, r *http.Request) {
	var request enrollDeviceRequest
	if err := decodeJSON(w, r, s.cfg.MaxBodyBytes, &request); err != nil {
		s.writeDecodeError(w, r, err)
		return
	}
	if request.DeviceID == "" {
		var err error
		request.DeviceID, err = randomUUID()
		if err != nil {
			s.internalError(w, r, err)
			return
		}
	}
	if err := validateIdentifier(request.DeviceID, 128); err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_device", "invalid device identity")
		return
	}
	if err := validateDevicePublicIdentity(request.PublicKeyAlgorithm, request.PublicKey, request.DeviceKeyFingerprint); err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_public_key", err.Error())
		return
	}
	if err := validateEncryptedValue(request.EncryptedName); err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_encrypted_name", err.Error())
		return
	}
	if err := validateEncryptedIcon(request.EncryptedIcon); err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_encrypted_icon", err.Error())
		return
	}
	capabilities, err := validateCapabilities(request.Capabilities)
	if err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_capabilities", err.Error())
		return
	}
	capabilitiesJSON, _ := json.Marshal(capabilities)
	token, selector, tokenHash, err := s.auth.NewToken()
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	var expiresAt *time.Time
	if s.cfg.TokenTTL > 0 {
		value := s.now().Add(s.cfg.TokenTTL).UTC()
		expiresAt = &value
	}
	var recovery *store.RecoveryEnvelope
	if request.RecoveryEnvelope != nil {
		if err := validateRecoveryEnvelope(*request.RecoveryEnvelope); err != nil {
			writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_recovery_envelope", err.Error())
			return
		}
		recovery = &store.RecoveryEnvelope{
			CryptoVersion: request.RecoveryEnvelope.CryptoVersion,
			Nonce:         request.RecoveryEnvelope.Nonce,
			Ciphertext:    request.RecoveryEnvelope.Ciphertext,
		}
	}
	_, err = s.store.EnrollDevice(r.Context(), store.EnrollDeviceParams{
		DeviceID:                request.DeviceID,
		PublicKeyAlgorithm:      request.PublicKeyAlgorithm,
		PublicKey:               request.PublicKey,
		EncryptedNameNonce:      request.EncryptedName.Nonce,
		EncryptedNameCiphertext: request.EncryptedName.Ciphertext,
		EncryptedIconNonce:      request.EncryptedIcon.Nonce,
		EncryptedIconCiphertext: request.EncryptedIcon.Ciphertext,
		CapabilitiesJSON:        string(capabilitiesJSON),
		TokenSelector:           selector,
		TokenHash:               tokenHash,
		TokenExpiresAt:          expiresAt,
		Recovery:                recovery,
	})
	if errors.Is(err, store.ErrConflict) {
		writeProblem(w, r, http.StatusConflict, "enrollment_conflict", err.Error())
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	bootstrap, err := s.store.Bootstrap(r.Context())
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	response := map[string]any{
		"workspaceId": bootstrap.WorkspaceID,
		"deviceId":    request.DeviceID,
		"token":       token,
		"cursor":      formatCursor(bootstrap.ServerEpoch, 0),
	}
	if expiresAt != nil {
		response["expiresAt"] = expiresAt.Format(time.RFC3339Nano)
	}
	writeJSON(w, http.StatusCreated, response)
}

type deviceResponse struct {
	DeviceID           string             `json:"deviceId"`
	PublicKeyAlgorithm string             `json:"publicKeyAlgorithm"`
	PublicKey          string             `json:"publicKey"`
	EncryptedName      encryptedValueDTO  `json:"encryptedName"`
	EncryptedIcon      *encryptedValueDTO `json:"encryptedIcon"`
	Capabilities       []string           `json:"capabilities"`
	Status             string             `json:"status"`
	CreatedAt          string             `json:"createdAt"`
	LastSeenAt         string             `json:"lastSeenAt"`
}

func (s *Server) listDevices(w http.ResponseWriter, r *http.Request, _ string) {
	values, err := s.store.ListDevices(r.Context())
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	devices := make([]deviceResponse, 0, len(values))
	for _, value := range values {
		var capabilities []string
		if err := json.Unmarshal([]byte(value.CapabilitiesJSON), &capabilities); err != nil {
			s.internalError(w, r, err)
			return
		}
		status := "active"
		if value.RevokedAt != nil {
			status = "revoked"
		}
		var encryptedIcon *encryptedValueDTO
		if value.EncryptedIconNonce != "" && value.EncryptedIconCiphertext != "" {
			encryptedIcon = &encryptedValueDTO{Nonce: value.EncryptedIconNonce, Ciphertext: value.EncryptedIconCiphertext}
		}
		devices = append(devices, deviceResponse{
			DeviceID:           value.ID,
			PublicKeyAlgorithm: value.PublicKeyAlgorithm,
			PublicKey:          value.PublicKey,
			EncryptedName:      encryptedValueDTO{Nonce: value.EncryptedNameNonce, Ciphertext: value.EncryptedNameCiphertext},
			EncryptedIcon:      encryptedIcon,
			Capabilities:       capabilities,
			Status:             status,
			CreatedAt:          value.CreatedAt.Format(time.RFC3339Nano),
			LastSeenAt:         value.LastSeenAt.Format(time.RFC3339Nano),
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{"devices": devices})
}

func (s *Server) revokeDevice(w http.ResponseWriter, r *http.Request) {
	deviceID := r.PathValue("deviceId")
	if err := validateIdentifier(deviceID, 128); err != nil {
		writeProblem(w, r, http.StatusNotFound, "device_not_found", "device not found")
		return
	}
	err := s.store.RevokeDevice(r.Context(), deviceID)
	if errors.Is(err, store.ErrDeviceNotFound) {
		writeProblem(w, r, http.StatusNotFound, "device_not_found", "device not found")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

type changeDTO struct {
	ChangeID      string `json:"changeId"`
	DeviceID      string `json:"deviceId,omitempty"`
	Entity        string `json:"entity"`
	EntityID      string `json:"entityId"`
	Operation     string `json:"operation"`
	BaseRevision  string `json:"baseRevision"`
	Revision      string `json:"revision,omitempty"`
	SchemaVersion int    `json:"schemaVersion"`
	CryptoVersion int    `json:"cryptoVersion"`
	KeyVersion    int    `json:"keyVersion"`
	Nonce         string `json:"nonce"`
	Ciphertext    string `json:"ciphertext"`
}

type pushRequest struct {
	Changes []changeDTO `json:"changes"`
}

func (s *Server) push(w http.ResponseWriter, r *http.Request, deviceID string) {
	if err := validateIdempotencyKey(r.Header.Get("Idempotency-Key")); err != nil {
		writeProblem(w, r, http.StatusBadRequest, "invalid_idempotency_key", err.Error())
		return
	}
	var request pushRequest
	if err := decodeJSON(w, r, s.cfg.MaxBodyBytes, &request); err != nil {
		s.writeDecodeError(w, r, err)
		return
	}
	if len(request.Changes) != 1 {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_batch", "Candy Sync v1 accepts exactly one change per push")
		return
	}
	if r.Header.Get("Idempotency-Key") != request.Changes[0].ChangeID {
		writeProblem(w, r, http.StatusUnprocessableEntity, "idempotency_mismatch", "Idempotency-Key must equal changeId")
		return
	}
	changes := make([]store.Change, 0, len(request.Changes))
	for _, input := range request.Changes {
		change, err := validateChange(input, deviceID)
		if err != nil {
			writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_change", err.Error())
			return
		}
		changes = append(changes, change)
	}
	results, cursor, err := s.store.Push(r.Context(), deviceID, changes)
	if errors.Is(err, store.ErrIdempotencyConflict) {
		writeProblem(w, r, http.StatusConflict, "idempotency_conflict", err.Error())
		return
	}
	if errors.Is(err, store.ErrRevisionConflict) {
		writeProblem(w, r, http.StatusConflict, "revision_conflict", err.Error())
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	revisions := make(map[string]string, len(results))
	for _, result := range results {
		revisions[result.ChangeID] = strconv.FormatInt(result.Revision, 10)
	}
	writeJSON(w, http.StatusOK, map[string]any{"cursor": cursor, "revisions": revisions})
}

func (s *Server) pull(w http.ResponseWriter, r *http.Request, _ string) {
	epoch, after, err := parseCursor(r.URL.Query().Get("after"))
	if err != nil {
		writeProblem(w, r, http.StatusBadRequest, "invalid_cursor", err.Error())
		return
	}
	limit := s.cfg.MaxBatch
	if raw := r.URL.Query().Get("limit"); raw != "" {
		value, err := strconv.Atoi(raw)
		if err != nil || value < 1 || value > s.cfg.MaxBatch {
			writeProblem(w, r, http.StatusBadRequest, "invalid_limit", fmt.Sprintf("limit must be between 1 and %d", s.cfg.MaxBatch))
			return
		}
		limit = value
	}
	result, err := s.store.Pull(r.Context(), epoch, after, limit)
	if errors.Is(err, store.ErrResponseTooLarge) {
		writeProblem(w, r, http.StatusRequestEntityTooLarge, "response_too_large", "stored change exceeds the v1 response limit")
		return
	}
	if errors.Is(err, store.ErrCursorReset) {
		writeProblem(w, r, http.StatusGone, "cursor_reset", "cursor no longer belongs to current server state")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	changes := make([]changeDTO, 0, len(result.Changes))
	nextSequence := after
	for _, value := range result.Changes {
		changes = append(changes, changeToDTO(value))
		nextSequence = value.Sequence
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"changes":    changes,
		"nextCursor": formatCursor(result.Epoch, nextSequence),
		"hasMore":    result.HasMore,
	})
}

func (s *Server) ack(w http.ResponseWriter, r *http.Request, deviceID string) {
	var request struct {
		Cursor string `json:"cursor"`
	}
	if err := decodeJSON(w, r, s.cfg.MaxBodyBytes, &request); err != nil {
		s.writeDecodeError(w, r, err)
		return
	}
	epoch, sequence, err := parseCursor(request.Cursor)
	if err != nil {
		writeProblem(w, r, http.StatusBadRequest, "invalid_cursor", err.Error())
		return
	}
	if err := s.store.Ack(r.Context(), deviceID, epoch, sequence); errors.Is(err, store.ErrCursorReset) {
		writeProblem(w, r, http.StatusGone, "cursor_reset", "cursor no longer belongs to current server state")
		return
	} else if err != nil {
		s.internalError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) snapshot(w http.ResponseWriter, r *http.Request, _ string) {
	value, err := s.store.Snapshot(r.Context())
	if errors.Is(err, store.ErrSnapshotTooLarge) {
		writeProblem(w, r, http.StatusRequestEntityTooLarge, "snapshot_too_large", "snapshot exceeds the v1 response limit; use paginated pull")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	changes := make([]changeDTO, 0, len(value.Entities))
	for _, entity := range value.Entities {
		changes = append(changes, changeToDTO(entity))
	}
	tabs := make([]map[string]any, 0, len(value.Tabs))
	for _, tab := range value.Tabs {
		tabs = append(tabs, tabToDTO(tab))
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"cursor":       formatCursor(value.Epoch, value.Head),
		"changes":      changes,
		"tabSnapshots": tabs,
	})
}

type putTabsRequest struct {
	ChangeID         string `json:"changeId"`
	ExpectedRevision string `json:"expectedRevision"`
	Revision         string `json:"revision"`
	SchemaVersion    int    `json:"schemaVersion"`
	CryptoVersion    int    `json:"cryptoVersion"`
	KeyVersion       int    `json:"keyVersion"`
	Nonce            string `json:"nonce"`
	Ciphertext       string `json:"ciphertext"`
}

func (s *Server) putTabs(w http.ResponseWriter, r *http.Request, authenticatedDeviceID string) {
	deviceID := r.PathValue("deviceId")
	if err := validateIdentifier(deviceID, 128); err != nil {
		writeProblem(w, r, http.StatusBadRequest, "invalid_target_device", "invalid target device identity")
		return
	}
	if err := validateIdempotencyKey(r.Header.Get("Idempotency-Key")); err != nil {
		writeProblem(w, r, http.StatusBadRequest, "invalid_idempotency_key", err.Error())
		return
	}
	var request putTabsRequest
	if err := decodeJSON(w, r, s.cfg.MaxBodyBytes, &request); err != nil {
		s.writeDecodeError(w, r, err)
		return
	}
	if request.ChangeID != r.Header.Get("Idempotency-Key") {
		writeProblem(w, r, http.StatusUnprocessableEntity, "idempotency_mismatch", "Idempotency-Key must equal changeId")
		return
	}
	expected, err := parseRevision(request.ExpectedRevision)
	if err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_revision", err.Error())
		return
	}
	revision, err := parseRevision(request.Revision)
	if err != nil || revision != expected+1 {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_revision", "revision must equal expectedRevision plus one")
		return
	}
	if err := validateEnvelope(request.SchemaVersion, request.CryptoVersion, request.KeyVersion, request.Nonce, request.Ciphertext); err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_snapshot", err.Error())
		return
	}
	value, cursor, err := s.store.PutTabSnapshot(r.Context(), authenticatedDeviceID, deviceID, expected, store.TabSnapshot{
		ChangeID:      request.ChangeID,
		Revision:      revision,
		SchemaVersion: request.SchemaVersion,
		CryptoVersion: request.CryptoVersion,
		KeyVersion:    request.KeyVersion,
		Nonce:         request.Nonce,
		Ciphertext:    request.Ciphertext,
	})
	if errors.Is(err, store.ErrRevisionConflict) || errors.Is(err, store.ErrIdempotencyConflict) {
		writeProblem(w, r, http.StatusConflict, "snapshot_conflict", err.Error())
		return
	}
	if errors.Is(err, store.ErrDeviceNotFound) {
		writeProblem(w, r, http.StatusNotFound, "target_device_not_found", "target device does not exist")
		return
	}
	if errors.Is(err, store.ErrDeviceRevoked) {
		writeProblem(w, r, http.StatusConflict, "target_device_revoked", "target device is revoked")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"revision": strconv.FormatInt(value.Revision, 10), "cursor": cursor})
}

func changeToDTO(value store.Change) changeDTO {
	return changeDTO{
		ChangeID:      value.ChangeID,
		DeviceID:      value.DeviceID,
		Entity:        value.Entity,
		EntityID:      value.EntityID,
		Operation:     value.Operation,
		BaseRevision:  strconv.FormatInt(value.BaseRevision, 10),
		Revision:      strconv.FormatInt(value.Revision, 10),
		SchemaVersion: value.SchemaVersion,
		CryptoVersion: value.CryptoVersion,
		KeyVersion:    value.KeyVersion,
		Nonce:         value.Nonce,
		Ciphertext:    value.Ciphertext,
	}
}

func tabToDTO(value store.TabSnapshot) map[string]any {
	return map[string]any{
		"changeId":      value.ChangeID,
		"deviceId":      value.WriterDeviceID,
		"entity":        "tabs",
		"entityId":      value.DeviceID,
		"operation":     "snapshot",
		"baseRevision":  strconv.FormatInt(value.BaseRevision, 10),
		"revision":      strconv.FormatInt(value.Revision, 10),
		"schemaVersion": value.SchemaVersion,
		"cryptoVersion": value.CryptoVersion,
		"keyVersion":    value.KeyVersion,
		"nonce":         value.Nonce,
		"ciphertext":    value.Ciphertext,
	}
}

func (s *Server) internalError(w http.ResponseWriter, r *http.Request, err error) {
	s.logger.ErrorContext(r.Context(), "request failed", "request_id", requestID(r), "error", err)
	writeProblem(w, r, http.StatusInternalServerError, "internal_error", "internal server error")
}

func (s *Server) writeDecodeError(w http.ResponseWriter, r *http.Request, err error) {
	if errors.Is(err, errBodyTooLarge) {
		writeProblem(w, r, http.StatusRequestEntityTooLarge, "payload_too_large", "request body exceeds configured limit")
		return
	}
	writeProblem(w, r, http.StatusBadRequest, "invalid_json", err.Error())
}

func writeProblem(w http.ResponseWriter, r *http.Request, status int, code, detail string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(problem{
		Type:      "https://candybrowser.dev/problems/" + code,
		Title:     http.StatusText(status),
		Status:    status,
		Code:      code,
		Detail:    detail,
		RequestID: requestID(r),
	})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func requestID(r *http.Request) string {
	value, _ := r.Context().Value(requestIDKey).(string)
	return value
}

func randomIdentifier(prefix string, size int) (string, error) {
	value := make([]byte, size)
	if _, err := rand.Read(value); err != nil {
		return "", err
	}
	return prefix + base64.RawURLEncoding.EncodeToString(value), nil
}

func randomUUID() (string, error) {
	value := make([]byte, 16)
	if _, err := rand.Read(value); err != nil {
		return "", err
	}
	value[6] = value[6]&0x0f | 0x40
	value[8] = value[8]&0x3f | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		value[0:4], value[4:6], value[6:8], value[8:10], value[10:16]), nil
}
