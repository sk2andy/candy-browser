package sqlite

import (
	"bytes"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

func (s *Store) PushDelta(ctx context.Context, auth store.AuthContext, change store.Change) (store.PushResult, string, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return store.PushResult{}, "", err
	}
	defer tx.Rollback()

	epoch, err := epochFrom(tx.QueryRowContext(ctx, `SELECT server_epoch FROM server_state WHERE singleton = 1`))
	if err != nil {
		return store.PushResult{}, "", err
	}
	if err := requireActiveDevice(ctx, tx, auth.WorkspaceID, auth.AccountID, auth.DeviceID); err != nil {
		return store.PushResult{}, "", err
	}

	change.WorkspaceID = auth.WorkspaceID
	change.DeviceID = auth.DeviceID
	change.Entity = "tabs"
	change.Operation = "delta"
	change.Revision = change.BaseRevision + 1
	digest, err := changeHash(change)
	if err != nil {
		return store.PushResult{}, "", err
	}
	var existing store.PushResult
	var existingHash []byte
	err = tx.QueryRowContext(ctx, `
		SELECT sequence, revision, envelope_hash
		FROM v2_changes
		WHERE workspace_id = ? AND writer_device_id = ? AND change_id = ?`,
		auth.WorkspaceID, auth.DeviceID, change.ChangeID,
	).Scan(&existing.Sequence, &existing.Revision, &existingHash)
	if err == nil {
		if !bytes.Equal(existingHash, digest) {
			return store.PushResult{}, "", fmt.Errorf("%w: change %s", store.ErrIdempotencyConflict, change.ChangeID)
		}
		existing.ChangeID = change.ChangeID
		existing.Duplicate = true
		if err := tx.Commit(); err != nil {
			return store.PushResult{}, "", err
		}
		return existing, formatCursor(epoch, existing.Sequence), nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return store.PushResult{}, "", err
	}
	if err := requireActiveTarget(ctx, tx, auth.WorkspaceID, change.EntityID); err != nil {
		return store.PushResult{}, "", err
	}

	var current int64
	err = tx.QueryRowContext(ctx, `
		SELECT revision FROM v2_tab_heads
		WHERE workspace_id = ? AND target_device_id = ?`, auth.WorkspaceID, change.EntityID).Scan(&current)
	if errors.Is(err, sql.ErrNoRows) {
		current = 0
	} else if err != nil {
		return store.PushResult{}, "", err
	}
	if current != change.BaseRevision {
		return store.PushResult{}, "", fmt.Errorf("%w: tabs/%s is at %d", store.ErrRevisionConflict, change.EntityID, current)
	}
	if _, err := tx.ExecContext(ctx, `UPDATE workspaces SET protocol_floor = 2 WHERE id = ?`, auth.WorkspaceID); err != nil {
		return store.PushResult{}, "", err
	}

	var sequence int64
	err = tx.QueryRowContext(ctx, `
		UPDATE v2_workspace_state
		SET head_sequence = head_sequence + 1
		WHERE workspace_id = ?
		RETURNING head_sequence`, auth.WorkspaceID).Scan(&sequence)
	if errors.Is(err, sql.ErrNoRows) {
		return store.PushResult{}, "", store.ErrConflict
	}
	if err != nil {
		return store.PushResult{}, "", err
	}
	_, err = tx.ExecContext(ctx, `
		INSERT INTO v2_changes(
			workspace_id, sequence, change_id, mutation_id, writer_device_id,
			target_device_id, base_revision, revision, schema_version,
			crypto_version, key_version, nonce, ciphertext, envelope_hash, created_at
		) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		auth.WorkspaceID, sequence, change.ChangeID, change.MutationID, auth.DeviceID,
		change.EntityID, change.BaseRevision, change.Revision, change.SchemaVersion,
		change.CryptoVersion, change.KeyVersion, change.Nonce, change.Ciphertext, digest,
		time.Now().UnixMilli(),
	)
	if err != nil {
		if isConstraintError(err) {
			return store.PushResult{}, "", fmt.Errorf("%w: change or mutation already exists", store.ErrIdempotencyConflict)
		}
		return store.PushResult{}, "", err
	}
	_, err = tx.ExecContext(ctx, `
		INSERT INTO v2_tab_heads(workspace_id, target_device_id, revision)
		VALUES(?, ?, ?)
		ON CONFLICT(workspace_id, target_device_id) DO UPDATE SET revision = excluded.revision`,
		auth.WorkspaceID, change.EntityID, change.Revision,
	)
	if err != nil {
		return store.PushResult{}, "", err
	}
	if err := tx.Commit(); err != nil {
		return store.PushResult{}, "", err
	}
	return store.PushResult{
		ChangeID: change.ChangeID,
		Sequence: sequence,
		Revision: change.Revision,
	}, formatCursor(epoch, sequence), nil
}

func (s *Store) PullDeltas(ctx context.Context, auth store.AuthContext, epoch string, after int64, limit int) (store.PullResult, error) {
	var currentEpoch string
	var head int64
	err := s.db.QueryRowContext(ctx, `
		SELECT ss.server_epoch, v.head_sequence
		FROM server_state ss
		JOIN v2_workspace_state v ON v.workspace_id = ?
		WHERE ss.singleton = 1`, auth.WorkspaceID).Scan(&currentEpoch, &head)
	if err != nil {
		return store.PullResult{}, err
	}
	if epoch != "" && epoch != currentEpoch || after < 0 || after > head {
		return store.PullResult{}, store.ErrCursorReset
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT sequence, change_id, mutation_id, workspace_id, writer_device_id,
		       target_device_id, base_revision, revision, schema_version,
		       crypto_version, key_version, nonce, ciphertext
		FROM v2_changes
		WHERE workspace_id = ? AND sequence > ?
		ORDER BY sequence
		LIMIT ?`, auth.WorkspaceID, after, limit+1)
	if err != nil {
		return store.PullResult{}, err
	}
	defer rows.Close()
	changes := make([]store.Change, 0, limit)
	responseBytes := 0
	truncatedBySize := false
	for rows.Next() {
		var change store.Change
		if err := rows.Scan(
			&change.Sequence, &change.ChangeID, &change.MutationID, &change.WorkspaceID,
			&change.DeviceID, &change.EntityID, &change.BaseRevision, &change.Revision,
			&change.SchemaVersion, &change.CryptoVersion, &change.KeyVersion,
			&change.Nonce, &change.Ciphertext,
		); err != nil {
			return store.PullResult{}, err
		}
		change.Entity = "tabs"
		change.Operation = "delta"
		changeBytes := len(change.Nonce) + len(change.Ciphertext) + 768
		if changeBytes > maxResponseOpaqueBytes {
			return store.PullResult{}, store.ErrResponseTooLarge
		}
		if len(changes) > 0 && responseBytes+changeBytes > maxResponseOpaqueBytes {
			truncatedBySize = true
			break
		}
		changes = append(changes, change)
		responseBytes += changeBytes
	}
	if err := rows.Err(); err != nil {
		return store.PullResult{}, err
	}
	hasMore := truncatedBySize || len(changes) > limit
	if len(changes) > limit {
		changes = changes[:limit]
	}
	return store.PullResult{Epoch: currentEpoch, Changes: changes, Head: head, HasMore: hasMore}, nil
}

func requireActiveDevice(ctx context.Context, tx *sql.Tx, workspaceID, accountID, deviceID string) error {
	var revoked sql.NullInt64
	err := tx.QueryRowContext(ctx, `
		SELECT revoked_at FROM devices
		WHERE workspace_id = ? AND account_id = ? AND id = ?`,
		workspaceID, accountID, deviceID,
	).Scan(&revoked)
	if errors.Is(err, sql.ErrNoRows) {
		return store.ErrDeviceNotFound
	}
	if err != nil {
		return err
	}
	if revoked.Valid {
		return store.ErrDeviceRevoked
	}
	return nil
}

func requireActiveTarget(ctx context.Context, tx *sql.Tx, workspaceID, deviceID string) error {
	var revoked sql.NullInt64
	err := tx.QueryRowContext(ctx, `
		SELECT revoked_at FROM devices WHERE workspace_id = ? AND id = ?`, workspaceID, deviceID,
	).Scan(&revoked)
	if errors.Is(err, sql.ErrNoRows) {
		return store.ErrDeviceNotFound
	}
	if err != nil {
		return err
	}
	if revoked.Valid {
		return store.ErrDeviceRevoked
	}
	return nil
}
