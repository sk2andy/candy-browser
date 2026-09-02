package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

func (s *Store) Bootstrap(ctx context.Context) (store.Bootstrap, error) {
	var result store.Bootstrap
	var recoveryVersion sql.NullInt64
	var recoveryNonce sql.NullString
	var recoveryCiphertext sql.NullString
	err := s.db.QueryRowContext(ctx, `
		SELECT w.id, ss.server_epoch, w.kdf_algorithm, w.kdf_salt,
		       w.kdf_memory_kib, w.kdf_iterations, w.kdf_parallelism,
		       w.recovery_crypto_version, w.recovery_nonce, w.recovery_ciphertext
		FROM workspaces w CROSS JOIN server_state ss
		WHERE ss.singleton = 1
		LIMIT 1`).Scan(
		&result.WorkspaceID,
		&result.ServerEpoch,
		&result.KDFAlgorithm,
		&result.KDFSalt,
		&result.KDFMemoryKiB,
		&result.KDFIterations,
		&result.KDFParallelism,
		&recoveryVersion,
		&recoveryNonce,
		&recoveryCiphertext,
	)
	if err != nil {
		return store.Bootstrap{}, err
	}
	result.Initialized = recoveryVersion.Valid
	if recoveryVersion.Valid {
		result.RecoveryCryptoVersion = int(recoveryVersion.Int64)
		result.RecoveryNonce = recoveryNonce.String
		result.RecoveryCiphertext = recoveryCiphertext.String
	}
	return result, nil
}

func (s *Store) EnrollDevice(ctx context.Context, params store.EnrollDeviceParams) (store.Device, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return store.Device{}, err
	}
	defer tx.Rollback()

	var workspaceID string
	var recoveryNonce sql.NullString
	if err := tx.QueryRowContext(ctx, `SELECT id, recovery_nonce FROM workspaces LIMIT 1`).Scan(&workspaceID, &recoveryNonce); err != nil {
		return store.Device{}, err
	}
	if !recoveryNonce.Valid {
		if params.Recovery == nil {
			return store.Device{}, fmt.Errorf("%w: first device must initialize immutable recovery envelope", store.ErrConflict)
		}
		if params.Recovery != nil {
			result, err := tx.ExecContext(ctx, `
			UPDATE workspaces
			SET recovery_crypto_version = ?, recovery_nonce = ?, recovery_ciphertext = ?
			WHERE id = ? AND recovery_nonce IS NULL`,
				params.Recovery.CryptoVersion,
				params.Recovery.Nonce,
				params.Recovery.Ciphertext,
				workspaceID,
			)
			if err != nil {
				return store.Device{}, err
			}
			updated, err := result.RowsAffected()
			if err != nil || updated != 1 {
				return store.Device{}, fmt.Errorf("%w: workspace was initialized concurrently", store.ErrConflict)
			}
		}
	} else if params.Recovery != nil {
		return store.Device{}, fmt.Errorf("%w: recovery envelope is immutable", store.ErrConflict)
	}

	now := time.Now().UTC()
	_, err = tx.ExecContext(ctx, `
		INSERT INTO devices(
			id, workspace_id, public_key_algorithm, public_key,
			encrypted_name_nonce, encrypted_name_ciphertext,
			encrypted_icon_nonce, encrypted_icon_ciphertext, capabilities_json,
			created_at, last_seen_at
		) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		params.DeviceID,
		workspaceID,
		params.PublicKeyAlgorithm,
		params.PublicKey,
		params.EncryptedNameNonce,
		params.EncryptedNameCiphertext,
		params.EncryptedIconNonce,
		params.EncryptedIconCiphertext,
		params.CapabilitiesJSON,
		now.UnixMilli(),
		now.UnixMilli(),
	)
	if err != nil {
		if isConstraintError(err) {
			return store.Device{}, fmt.Errorf("%w: device or token already exists", store.ErrConflict)
		}
		return store.Device{}, err
	}
	var expiresAt any
	if params.TokenExpiresAt != nil {
		expiresAt = params.TokenExpiresAt.UnixMilli()
	}
	_, err = tx.ExecContext(ctx, `
		INSERT INTO device_tokens(selector, device_id, token_hash, created_at, expires_at)
		VALUES(?, ?, ?, ?, ?)`,
		params.TokenSelector,
		params.DeviceID,
		params.TokenHash,
		now.UnixMilli(),
		expiresAt,
	)
	if err != nil {
		if isConstraintError(err) {
			return store.Device{}, fmt.Errorf("%w: device or token already exists", store.ErrConflict)
		}
		return store.Device{}, err
	}
	if err := tx.Commit(); err != nil {
		return store.Device{}, err
	}
	return store.Device{
		ID:                      params.DeviceID,
		PublicKeyAlgorithm:      params.PublicKeyAlgorithm,
		PublicKey:               params.PublicKey,
		EncryptedNameNonce:      params.EncryptedNameNonce,
		EncryptedNameCiphertext: params.EncryptedNameCiphertext,
		EncryptedIconNonce:      params.EncryptedIconNonce,
		EncryptedIconCiphertext: params.EncryptedIconCiphertext,
		CapabilitiesJSON:        params.CapabilitiesJSON,
		CreatedAt:               now,
		LastSeenAt:              now,
	}, nil
}

func (s *Store) Token(ctx context.Context, selector string) (store.Token, error) {
	var result store.Token
	var expiresAt sql.NullInt64
	var tokenRevokedAt sql.NullInt64
	var deviceRevokedAt sql.NullInt64
	err := s.db.QueryRowContext(ctx, `
		SELECT dt.device_id, dt.token_hash, dt.expires_at, dt.revoked_at, d.revoked_at
		FROM device_tokens dt
		JOIN devices d ON d.id = dt.device_id
		WHERE dt.selector = ?`, selector).Scan(
		&result.DeviceID,
		&result.Hash,
		&expiresAt,
		&tokenRevokedAt,
		&deviceRevokedAt,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return store.Token{}, store.ErrDeviceNotFound
	}
	if err != nil {
		return store.Token{}, err
	}
	if expiresAt.Valid {
		value := time.UnixMilli(expiresAt.Int64).UTC()
		result.Expires = &value
	}
	if tokenRevokedAt.Valid || deviceRevokedAt.Valid {
		value := time.Now().UTC()
		result.Revoked = &value
	}
	_, _ = s.db.ExecContext(ctx, `UPDATE devices SET last_seen_at = ? WHERE id = ?`, time.Now().UnixMilli(), result.DeviceID)
	return result, nil
}

func (s *Store) ListDevices(ctx context.Context) ([]store.Device, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, public_key_algorithm, public_key, encrypted_name_nonce,
		       encrypted_name_ciphertext, encrypted_icon_nonce,
		       encrypted_icon_ciphertext, capabilities_json, created_at,
		       last_seen_at, revoked_at
		FROM devices
		ORDER BY created_at, id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var devices []store.Device
	for rows.Next() {
		var device store.Device
		var createdAt int64
		var lastSeenAt int64
		var revokedAt sql.NullInt64
		var encryptedIconNonce sql.NullString
		var encryptedIconCiphertext sql.NullString
		if err := rows.Scan(
			&device.ID,
			&device.PublicKeyAlgorithm,
			&device.PublicKey,
			&device.EncryptedNameNonce,
			&device.EncryptedNameCiphertext,
			&encryptedIconNonce,
			&encryptedIconCiphertext,
			&device.CapabilitiesJSON,
			&createdAt,
			&lastSeenAt,
			&revokedAt,
		); err != nil {
			return nil, err
		}
		device.CreatedAt = time.UnixMilli(createdAt).UTC()
		device.LastSeenAt = time.UnixMilli(lastSeenAt).UTC()
		if encryptedIconNonce.Valid && encryptedIconCiphertext.Valid {
			device.EncryptedIconNonce = encryptedIconNonce.String
			device.EncryptedIconCiphertext = encryptedIconCiphertext.String
		}
		if revokedAt.Valid {
			value := time.UnixMilli(revokedAt.Int64).UTC()
			device.RevokedAt = &value
		}
		devices = append(devices, device)
	}
	return devices, rows.Err()
}

func (s *Store) RevokeDevice(ctx context.Context, deviceID string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	now := time.Now().UnixMilli()
	result, err := tx.ExecContext(ctx, `UPDATE devices SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ?`, now, deviceID)
	if err != nil {
		return err
	}
	updated, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if updated == 0 {
		return store.ErrDeviceNotFound
	}
	if _, err := tx.ExecContext(ctx, `UPDATE device_tokens SET revoked_at = COALESCE(revoked_at, ?) WHERE device_id = ?`, now, deviceID); err != nil {
		return err
	}
	return tx.Commit()
}

func isConstraintError(err error) bool {
	return strings.Contains(err.Error(), "constraint failed") || strings.Contains(err.Error(), "UNIQUE constraint")
}
