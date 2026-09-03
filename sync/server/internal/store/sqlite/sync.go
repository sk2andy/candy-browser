package sqlite

import (
	"bytes"
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

const maxResponseOpaqueBytes = 768 << 10

func (s *Store) Push(ctx context.Context, deviceID string, changes []store.Change) ([]store.PushResult, string, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, "", err
	}
	defer tx.Rollback()

	epoch, err := epochFrom(tx.QueryRowContext(ctx, `SELECT server_epoch FROM server_state WHERE singleton = 1`))
	if err != nil {
		return nil, "", err
	}
	for _, change := range changes {
		if change.Entity != "tabs" {
			continue
		}
		var existing int
		err := tx.QueryRowContext(ctx, `
			SELECT 1 FROM changes WHERE device_id = ? AND change_id = ?`,
			deviceID, change.ChangeID,
		).Scan(&existing)
		if err == nil {
			continue
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return nil, "", err
		}
		var protocolFloor int
		err = tx.QueryRowContext(ctx, `
			SELECT w.protocol_floor
			FROM devices d JOIN workspaces w ON w.id = d.workspace_id
			WHERE d.id = ?`, deviceID).Scan(&protocolFloor)
		if errors.Is(err, sql.ErrNoRows) {
			return nil, "", store.ErrDeviceNotFound
		}
		if err != nil {
			return nil, "", err
		}
		if protocolFloor >= 2 {
			return nil, "", store.ErrProtocolUpgradeRequired
		}
	}
	results := make([]store.PushResult, 0, len(changes))
	for _, change := range changes {
		change.DeviceID = deviceID
		if change.Revision == 0 {
			change.Revision = change.BaseRevision + 1
		}
		digest, err := changeHash(change)
		if err != nil {
			return nil, "", err
		}
		var existingSequence int64
		var existingRevision int64
		var existingHash []byte
		err = tx.QueryRowContext(ctx, `
			SELECT sequence, revision, envelope_hash
			FROM changes
			WHERE device_id = ? AND change_id = ?`, deviceID, change.ChangeID).Scan(
			&existingSequence,
			&existingRevision,
			&existingHash,
		)
		if err == nil {
			if !bytes.Equal(existingHash, digest) {
				return nil, "", fmt.Errorf("%w: change %s", store.ErrIdempotencyConflict, change.ChangeID)
			}
			results = append(results, store.PushResult{
				ChangeID:  change.ChangeID,
				Sequence:  existingSequence,
				Revision:  existingRevision,
				Duplicate: true,
			})
			continue
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return nil, "", err
		}
		if change.Entity == "tabs" {
			var revokedAt sql.NullInt64
			if err := tx.QueryRowContext(ctx, `SELECT revoked_at FROM devices WHERE id = ?`, change.EntityID).Scan(&revokedAt); errors.Is(err, sql.ErrNoRows) {
				return nil, "", store.ErrDeviceNotFound
			} else if err != nil {
				return nil, "", err
			} else if revokedAt.Valid {
				return nil, "", store.ErrDeviceRevoked
			}
		}

		currentRevision, err := currentRevision(ctx, tx, change.Entity, change.EntityID)
		if err != nil {
			return nil, "", err
		}
		if currentRevision != change.BaseRevision {
			return nil, "", fmt.Errorf("%w: %s/%s is at %d", store.ErrRevisionConflict, change.Entity, change.EntityID, currentRevision)
		}
		if change.Revision != change.BaseRevision+1 {
			return nil, "", fmt.Errorf("%w: revision must equal base revision plus one", store.ErrRevisionConflict)
		}
		result, err := tx.ExecContext(ctx, `
			INSERT INTO changes(
				change_id, device_id, entity, entity_id, operation,
				base_revision, revision, schema_version, crypto_version,
				key_version, nonce, ciphertext, envelope_hash, created_at
			) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
			change.ChangeID,
			deviceID,
			change.Entity,
			change.EntityID,
			change.Operation,
			change.BaseRevision,
			change.Revision,
			change.SchemaVersion,
			change.CryptoVersion,
			change.KeyVersion,
			change.Nonce,
			change.Ciphertext,
			digest,
			time.Now().UnixMilli(),
		)
		if err != nil {
			return nil, "", err
		}
		sequence, err := result.LastInsertId()
		if err != nil {
			return nil, "", err
		}
		if change.Entity == "tabs" {
			_, err = tx.ExecContext(ctx, `
				INSERT INTO tab_snapshots(
					device_id, revision, schema_version, crypto_version,
					key_version, nonce, ciphertext, updated_sequence
				) VALUES(?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(device_id) DO UPDATE SET
					revision = excluded.revision,
					schema_version = excluded.schema_version,
					crypto_version = excluded.crypto_version,
					key_version = excluded.key_version,
					nonce = excluded.nonce,
					ciphertext = excluded.ciphertext,
					updated_sequence = excluded.updated_sequence`,
				change.EntityID,
				change.Revision,
				change.SchemaVersion,
				change.CryptoVersion,
				change.KeyVersion,
				change.Nonce,
				change.Ciphertext,
				sequence,
			)
			if err == nil {
				var workspaceID string
				if err = tx.QueryRowContext(ctx, `SELECT workspace_id FROM devices WHERE id = ?`, change.EntityID).Scan(&workspaceID); err == nil {
					_, err = tx.ExecContext(ctx, `
						INSERT INTO v2_tab_heads(workspace_id, target_device_id, revision)
						VALUES(?, ?, ?)
						ON CONFLICT(workspace_id, target_device_id) DO UPDATE SET revision = excluded.revision`,
						workspaceID, change.EntityID, change.Revision,
					)
				}
			}
		} else {
			_, err = tx.ExecContext(ctx, `
				INSERT INTO entity_state(
					entity, entity_id, revision, operation, device_id,
					schema_version, crypto_version, key_version, nonce,
					ciphertext, updated_sequence
				) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(entity, entity_id) DO UPDATE SET
					revision = excluded.revision,
					operation = excluded.operation,
					device_id = excluded.device_id,
					schema_version = excluded.schema_version,
					crypto_version = excluded.crypto_version,
					key_version = excluded.key_version,
					nonce = excluded.nonce,
					ciphertext = excluded.ciphertext,
					updated_sequence = excluded.updated_sequence`,
				change.Entity,
				change.EntityID,
				change.Revision,
				change.Operation,
				deviceID,
				change.SchemaVersion,
				change.CryptoVersion,
				change.KeyVersion,
				change.Nonce,
				change.Ciphertext,
				sequence,
			)
		}
		if err != nil {
			return nil, "", err
		}
		results = append(results, store.PushResult{
			ChangeID: change.ChangeID,
			Sequence: sequence,
			Revision: change.Revision,
		})
	}
	head, err := headFrom(tx.QueryRowContext(ctx, `SELECT COALESCE(MAX(sequence), 0) FROM changes`))
	if err != nil {
		return nil, "", err
	}
	if err := tx.Commit(); err != nil {
		return nil, "", err
	}
	return results, formatCursor(epoch, head), nil
}

func (s *Store) Pull(ctx context.Context, epoch string, after int64, limit int) (store.PullResult, error) {
	currentEpoch, head, err := s.epochAndHead(ctx)
	if err != nil {
		return store.PullResult{}, err
	}
	if epoch != "" && epoch != currentEpoch || after < 0 || after > head {
		return store.PullResult{}, store.ErrCursorReset
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT sequence, change_id, device_id, entity, entity_id, operation,
		       base_revision, revision, schema_version, crypto_version,
		       key_version, nonce, ciphertext
		FROM changes
		WHERE sequence > ?
		ORDER BY sequence
		LIMIT ?`, after, limit+1)
	if err != nil {
		return store.PullResult{}, err
	}
	defer rows.Close()
	changes := make([]store.Change, 0, limit)
	responseBytes := 0
	truncatedBySize := false
	for rows.Next() {
		var change store.Change
		if err := scanChange(rows, &change); err != nil {
			return store.PullResult{}, err
		}
		changeBytes := len(change.Nonce) + len(change.Ciphertext) + 512
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

func (s *Store) Ack(ctx context.Context, deviceID, epoch string, sequence int64) error {
	currentEpoch, head, err := s.epochAndHead(ctx)
	if err != nil {
		return err
	}
	if epoch != currentEpoch || sequence < 0 || sequence > head {
		return store.ErrCursorReset
	}
	_, err = s.db.ExecContext(ctx, `
		INSERT INTO device_cursors(device_id, server_epoch, sequence, acknowledged_at)
		VALUES(?, ?, ?, ?)
		ON CONFLICT(device_id) DO UPDATE SET
			server_epoch = excluded.server_epoch,
			sequence = MAX(device_cursors.sequence, excluded.sequence),
			acknowledged_at = excluded.acknowledged_at`,
		deviceID, epoch, sequence, time.Now().UnixMilli())
	return err
}

func (s *Store) Snapshot(ctx context.Context) (store.Snapshot, error) {
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return store.Snapshot{}, err
	}
	defer tx.Rollback()
	var epoch string
	var head int64
	err = tx.QueryRowContext(ctx, `
		SELECT ss.server_epoch, COALESCE(MAX(c.sequence), 0)
		FROM server_state ss LEFT JOIN changes c
		WHERE ss.singleton = 1`).Scan(&epoch, &head)
	if err != nil {
		return store.Snapshot{}, err
	}
	entityRows, err := tx.QueryContext(ctx, `
		SELECT c.sequence, c.change_id, es.device_id, es.entity, es.entity_id,
		       es.operation, c.base_revision, es.revision, es.schema_version,
		       es.crypto_version, es.key_version, es.nonce, es.ciphertext
		FROM entity_state es
		JOIN changes c ON c.sequence = es.updated_sequence
		ORDER BY es.entity, es.entity_id`)
	if err != nil {
		return store.Snapshot{}, err
	}
	var entities []store.Change
	responseBytes := 0
	for entityRows.Next() {
		var change store.Change
		if err := scanChange(entityRows, &change); err != nil {
			entityRows.Close()
			return store.Snapshot{}, err
		}
		responseBytes += len(change.Nonce) + len(change.Ciphertext) + 512
		if responseBytes > maxResponseOpaqueBytes {
			entityRows.Close()
			return store.Snapshot{}, store.ErrSnapshotTooLarge
		}
		entities = append(entities, change)
	}
	if err := entityRows.Close(); err != nil {
		return store.Snapshot{}, err
	}

	tabRows, err := tx.QueryContext(ctx, `
		SELECT c.change_id, c.device_id, ts.device_id, c.base_revision, ts.revision,
		       ts.schema_version, ts.crypto_version, ts.key_version,
		       ts.nonce, ts.ciphertext, ts.updated_sequence
		FROM tab_snapshots ts
		JOIN changes c ON c.sequence = ts.updated_sequence
		ORDER BY ts.device_id`)
	if err != nil {
		return store.Snapshot{}, err
	}
	defer tabRows.Close()
	var tabs []store.TabSnapshot
	for tabRows.Next() {
		var tab store.TabSnapshot
		if err := tabRows.Scan(
			&tab.ChangeID,
			&tab.WriterDeviceID,
			&tab.DeviceID,
			&tab.BaseRevision,
			&tab.Revision,
			&tab.SchemaVersion,
			&tab.CryptoVersion,
			&tab.KeyVersion,
			&tab.Nonce,
			&tab.Ciphertext,
			&tab.Sequence,
		); err != nil {
			return store.Snapshot{}, err
		}
		responseBytes += len(tab.Nonce) + len(tab.Ciphertext) + 256
		if responseBytes > maxResponseOpaqueBytes {
			return store.Snapshot{}, store.ErrSnapshotTooLarge
		}
		tabs = append(tabs, tab)
	}
	if err := tabRows.Err(); err != nil {
		return store.Snapshot{}, err
	}
	if err := tabRows.Close(); err != nil {
		return store.Snapshot{}, err
	}
	if err := tx.Commit(); err != nil {
		return store.Snapshot{}, err
	}
	return store.Snapshot{Epoch: epoch, Head: head, Entities: entities, Tabs: tabs}, nil
}

func (s *Store) PutTabSnapshot(ctx context.Context, writerDeviceID, targetDeviceID string, expectedRevision int64, tab store.TabSnapshot) (store.TabSnapshot, string, error) {
	if tab.ChangeID == "" {
		return store.TabSnapshot{}, "", fmt.Errorf("change id is required")
	}
	change := store.Change{
		ChangeID:      tab.ChangeID,
		Entity:        "tabs",
		EntityID:      targetDeviceID,
		Operation:     "snapshot",
		BaseRevision:  expectedRevision,
		Revision:      tab.Revision,
		SchemaVersion: tab.SchemaVersion,
		CryptoVersion: tab.CryptoVersion,
		KeyVersion:    tab.KeyVersion,
		Nonce:         tab.Nonce,
		Ciphertext:    tab.Ciphertext,
	}
	var existing int
	existingErr := s.db.QueryRowContext(ctx, `SELECT 1 FROM changes WHERE device_id = ? AND change_id = ?`, writerDeviceID, tab.ChangeID).Scan(&existing)
	if errors.Is(existingErr, sql.ErrNoRows) {
		var revokedAt sql.NullInt64
		if err := s.db.QueryRowContext(ctx, `SELECT revoked_at FROM devices WHERE id = ?`, targetDeviceID).Scan(&revokedAt); errors.Is(err, sql.ErrNoRows) {
			return store.TabSnapshot{}, "", store.ErrDeviceNotFound
		} else if err != nil {
			return store.TabSnapshot{}, "", err
		} else if revokedAt.Valid {
			return store.TabSnapshot{}, "", store.ErrDeviceRevoked
		}
	} else if existingErr != nil {
		return store.TabSnapshot{}, "", existingErr
	}
	results, cursor, err := s.Push(ctx, writerDeviceID, []store.Change{change})
	if err != nil {
		return store.TabSnapshot{}, "", err
	}
	tab.DeviceID = targetDeviceID
	tab.WriterDeviceID = writerDeviceID
	tab.BaseRevision = expectedRevision
	tab.Sequence = results[0].Sequence
	return tab, cursor, nil
}

func (s *Store) epochAndHead(ctx context.Context) (string, int64, error) {
	var epoch string
	var head int64
	err := s.db.QueryRowContext(ctx, `
		SELECT ss.server_epoch, COALESCE(MAX(c.sequence), 0)
		FROM server_state ss LEFT JOIN changes c
		WHERE ss.singleton = 1`).Scan(&epoch, &head)
	return epoch, head, err
}

func currentRevision(ctx context.Context, tx *sql.Tx, entity, entityID string) (int64, error) {
	query := `SELECT revision FROM entity_state WHERE entity = ? AND entity_id = ?`
	args := []any{entity, entityID}
	if entity == "tabs" {
		query = `SELECT revision FROM tab_snapshots WHERE device_id = ?`
		args = []any{entityID}
	}
	var revision int64
	err := tx.QueryRowContext(ctx, query, args...).Scan(&revision)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	return revision, err
}

func changeHash(change store.Change) ([]byte, error) {
	change.Sequence = 0
	data, err := json.Marshal(change)
	if err != nil {
		return nil, err
	}
	digest := sha256.Sum256(data)
	return digest[:], nil
}

func scanChange(scanner interface{ Scan(...any) error }, change *store.Change) error {
	return scanner.Scan(
		&change.Sequence,
		&change.ChangeID,
		&change.DeviceID,
		&change.Entity,
		&change.EntityID,
		&change.Operation,
		&change.BaseRevision,
		&change.Revision,
		&change.SchemaVersion,
		&change.CryptoVersion,
		&change.KeyVersion,
		&change.Nonce,
		&change.Ciphertext,
	)
}

func epochFrom(row *sql.Row) (string, error) {
	var epoch string
	err := row.Scan(&epoch)
	return epoch, err
}

func headFrom(row *sql.Row) (int64, error) {
	var head int64
	err := row.Scan(&head)
	return head, err
}

func formatCursor(epoch string, sequence int64) string {
	return fmt.Sprintf("%s.%d", epoch, sequence)
}
