package api

import (
	"errors"
	"fmt"
	"net/http"
	"strconv"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

type v2ChangeDTO struct {
	ChangeID      string `json:"changeId"`
	MutationID    string `json:"mutationId"`
	WorkspaceID   string `json:"workspaceId"`
	DeviceID      string `json:"deviceId"`
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

type v2PushRequest struct {
	Changes []v2ChangeDTO `json:"changes"`
}

func (s *Server) pushDelta(w http.ResponseWriter, r *http.Request, authenticated store.AuthContext) {
	if err := validateIdempotencyKey(r.Header.Get("Idempotency-Key")); err != nil {
		writeProblem(w, r, http.StatusBadRequest, "invalid_idempotency_key", err.Error())
		return
	}
	var request v2PushRequest
	if err := decodeJSON(w, r, s.cfg.MaxBodyBytes, &request); err != nil {
		s.writeDecodeError(w, r, err)
		return
	}
	if len(request.Changes) != 1 {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_batch", "Candy Sync v2 accepts exactly one change per push")
		return
	}
	input := request.Changes[0]
	if input.ChangeID != r.Header.Get("Idempotency-Key") {
		writeProblem(w, r, http.StatusUnprocessableEntity, "idempotency_mismatch", "Idempotency-Key must equal changeId")
		return
	}
	change, err := validateV2Change(input, authenticated)
	if err != nil {
		writeProblem(w, r, http.StatusUnprocessableEntity, "invalid_change", err.Error())
		return
	}
	result, cursor, err := s.store.PushDelta(r.Context(), authenticated, change)
	switch {
	case errors.Is(err, store.ErrIdempotencyConflict):
		writeProblem(w, r, http.StatusConflict, "idempotency_conflict", err.Error())
		return
	case errors.Is(err, store.ErrRevisionConflict):
		writeProblem(w, r, http.StatusConflict, "revision_conflict", err.Error())
		return
	case errors.Is(err, store.ErrDeviceNotFound):
		writeProblem(w, r, http.StatusNotFound, "target_device_not_found", "target device does not exist in authenticated workspace")
		return
	case errors.Is(err, store.ErrDeviceRevoked):
		writeProblem(w, r, http.StatusConflict, "target_device_revoked", "target device is revoked")
		return
	case err != nil:
		s.internalError(w, r, err)
		return
	}
	committed := v2ChangeToDTO(change)
	committed.Revision = strconv.FormatInt(result.Revision, 10)
	if !result.Duplicate {
		s.hub.publish(authenticated.WorkspaceID, realtimeFrame{Type: "change", Cursor: cursor, Change: committed})
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"cursor": cursor,
		"results": []map[string]string{{
			"changeId": input.ChangeID,
			"revision": strconv.FormatInt(result.Revision, 10),
		}},
	})
}

func (s *Server) pullDeltas(w http.ResponseWriter, r *http.Request, authenticated store.AuthContext) {
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
	result, err := s.store.PullDeltas(r.Context(), authenticated, epoch, after, limit)
	if errors.Is(err, store.ErrResponseTooLarge) {
		writeProblem(w, r, http.StatusRequestEntityTooLarge, "response_too_large", "stored change exceeds the v2 response limit")
		return
	}
	if errors.Is(err, store.ErrCursorReset) {
		writeProblem(w, r, http.StatusGone, "cursor_reset", "cursor no longer belongs to current workspace state")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	changes := make([]v2ChangeDTO, 0, len(result.Changes))
	nextSequence := after
	for _, change := range result.Changes {
		changes = append(changes, v2ChangeToDTO(change))
		nextSequence = change.Sequence
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"changes":    changes,
		"nextCursor": formatCursor(result.Epoch, nextSequence),
		"hasMore":    result.HasMore,
	})
}

func validateV2Change(value v2ChangeDTO, authenticated store.AuthContext) (store.Change, error) {
	if err := validateIdentifier(value.ChangeID, 128); err != nil {
		return store.Change{}, fmt.Errorf("invalid changeId: %w", err)
	}
	if err := validateIdentifier(value.MutationID, 128); err != nil {
		return store.Change{}, fmt.Errorf("invalid mutationId: %w", err)
	}
	if value.WorkspaceID != authenticated.WorkspaceID {
		return store.Change{}, errors.New("workspaceId must match authenticated workspace")
	}
	if value.DeviceID != authenticated.DeviceID {
		return store.Change{}, errors.New("deviceId must match authenticated device")
	}
	if value.Entity != "tabs" || value.Operation != "delta" {
		return store.Change{}, errors.New("v2 changes require entity tabs and operation delta")
	}
	if err := validateIdentifier(value.EntityID, 128); err != nil {
		return store.Change{}, fmt.Errorf("invalid entityId: %w", err)
	}
	if value.Revision != "" {
		return store.Change{}, errors.New("revision is assigned by server and must be omitted")
	}
	base, err := parseRevision(value.BaseRevision)
	if err != nil {
		return store.Change{}, fmt.Errorf("invalid baseRevision: %w", err)
	}
	if value.SchemaVersion != 2 || value.CryptoVersion != 1 || value.KeyVersion != 1 {
		return store.Change{}, errors.New("unsupported schema, crypto, or key version")
	}
	if err := validateNonce(value.Nonce); err != nil {
		return store.Change{}, err
	}
	if err := validateOpaque(value.Ciphertext, 22, 262166); err != nil {
		return store.Change{}, errors.New("ciphertext must be unpadded base64url within limits")
	}
	return store.Change{
		ChangeID:      value.ChangeID,
		MutationID:    value.MutationID,
		WorkspaceID:   authenticated.WorkspaceID,
		DeviceID:      authenticated.DeviceID,
		Entity:        "tabs",
		EntityID:      value.EntityID,
		Operation:     "delta",
		BaseRevision:  base,
		Revision:      base + 1,
		SchemaVersion: 2,
		CryptoVersion: 1,
		KeyVersion:    value.KeyVersion,
		Nonce:         value.Nonce,
		Ciphertext:    value.Ciphertext,
	}, nil
}

func v2ChangeToDTO(value store.Change) v2ChangeDTO {
	return v2ChangeDTO{
		ChangeID:      value.ChangeID,
		MutationID:    value.MutationID,
		WorkspaceID:   value.WorkspaceID,
		DeviceID:      value.DeviceID,
		Entity:        "tabs",
		EntityID:      value.EntityID,
		Operation:     "delta",
		BaseRevision:  strconv.FormatInt(value.BaseRevision, 10),
		Revision:      strconv.FormatInt(value.Revision, 10),
		SchemaVersion: 2,
		CryptoVersion: 1,
		KeyVersion:    value.KeyVersion,
		Nonce:         value.Nonce,
		Ciphertext:    value.Ciphertext,
	}
}
