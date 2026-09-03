package sqlite

import (
	"bytes"
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
	"github.com/sk2andy/candy-browser/sync/server/migrations"
)

func TestStoreBootstrapPersistsAcrossRestart(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "candy-sync.sqlite3")
	first, err := Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	before, err := first.Bootstrap(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if before.Initialized || before.WorkspaceID == "" || before.ServerEpoch == "" || before.KDFSalt == "" {
		t.Fatalf("unexpected bootstrap: %+v", before)
	}
	if err := first.Close(); err != nil {
		t.Fatal(err)
	}
	second, err := Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	defer second.Close()
	after, err := second.Bootstrap(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if after.WorkspaceID != before.WorkspaceID || after.ServerEpoch != before.ServerEpoch || after.KDFSalt != before.KDFSalt {
		t.Fatalf("bootstrap identity changed across restart: before=%+v after=%+v", before, after)
	}
}

func TestEnrollmentRequiresFirstAndKeepsRecoveryEnvelopeImmutable(t *testing.T) {
	repository := openTestStore(t)
	ctx := context.Background()
	params := testEnrollment("device_a", "selector_a")
	if _, err := repository.EnrollDevice(ctx, params); !errors.Is(err, store.ErrConflict) {
		t.Fatalf("first device without recovery error = %v", err)
	}
	params.Recovery = &store.RecoveryEnvelope{CryptoVersion: 1, Nonce: "nonce", Ciphertext: "ciphertext"}
	if _, err := repository.EnrollDevice(ctx, params); err != nil {
		t.Fatal(err)
	}
	devices, err := repository.ListDevices(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(devices) != 1 || devices[0].EncryptedIconNonce != "icon_nonce" || devices[0].EncryptedIconCiphertext != "icon_ciphertext" {
		t.Fatalf("encrypted icon was not persisted: %+v", devices)
	}
	bootstrap, err := repository.Bootstrap(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if !bootstrap.Initialized || bootstrap.RecoveryCiphertext != "ciphertext" {
		t.Fatalf("recovery envelope missing: %+v", bootstrap)
	}
	second := testEnrollment("device_b", "selector_b")
	second.Recovery = &store.RecoveryEnvelope{CryptoVersion: 1, Nonce: "different", Ciphertext: "different"}
	if _, err := repository.EnrollDevice(ctx, second); !errors.Is(err, store.ErrConflict) {
		t.Fatalf("recovery replacement error = %v", err)
	}
	second.Recovery = nil
	if _, err := repository.EnrollDevice(ctx, second); err != nil {
		t.Fatal(err)
	}
}

func TestEncryptedDeviceIconMigrationPreservesLegacyRows(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "legacy.sqlite3")
	db, err := sql.Open("sqlite", "file:"+filepath.ToSlash(path))
	if err != nil {
		t.Fatal(err)
	}
	migrationOne, err := migrations.Files.ReadFile("0001_initial.sql")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, `
		CREATE TABLE schema_migrations (
			version INTEGER PRIMARY KEY,
			name TEXT NOT NULL,
			checksum TEXT NOT NULL,
			applied_at INTEGER NOT NULL
		)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, string(migrationOne)); err != nil {
		t.Fatal(err)
	}
	digest := sha256.Sum256(migrationOne)
	if _, err := db.ExecContext(ctx, `INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES(1, ?, ?, 0)`, "0001_initial.sql", hex.EncodeToString(digest[:])); err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, `INSERT INTO server_state(singleton, server_epoch, created_at) VALUES(1, 'epoch_legacy', 0)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, `
		INSERT INTO workspaces(
			id, kdf_algorithm, kdf_salt, kdf_memory_kib, kdf_iterations,
			kdf_parallelism, recovery_crypto_version, recovery_nonce,
			recovery_ciphertext, created_at
		) VALUES('workspace_legacy', 'argon2id-v1', 'salt', 65536, 3, 4, 1, 'nonce', 'ciphertext', 0)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, `
		INSERT INTO devices(
			id, workspace_id, public_key_algorithm, public_key,
			encrypted_name_nonce, encrypted_name_ciphertext, capabilities_json,
			created_at, last_seen_at
		) VALUES('device_legacy', 'workspace_legacy', 'ECDH-P256-SPKI', 'public_key',
			'name_nonce', 'name_ciphertext', '["tabs"]', 0, 0)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, `
		INSERT INTO changes(
			change_id, device_id, entity, entity_id, operation, base_revision,
			revision, schema_version, crypto_version, key_version, nonce,
			ciphertext, envelope_hash, created_at
		) VALUES('legacy_tab_snapshot', 'device_legacy', 'tabs', 'device_legacy',
			'snapshot', 6, 7, 1, 1, 1, 'nonce', 'ciphertext', X'01', 0)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, `
		INSERT INTO tab_snapshots(
			device_id, revision, schema_version, crypto_version, key_version,
			nonce, ciphertext, updated_sequence
		) SELECT 'device_legacy', 7, 1, 1, 1, 'nonce', 'ciphertext', sequence
		  FROM changes WHERE change_id = 'legacy_tab_snapshot'`); err != nil {
		t.Fatal(err)
	}
	if err := db.Close(); err != nil {
		t.Fatal(err)
	}

	repository, err := Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	defer repository.Close()
	devices, err := repository.ListDevices(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(devices) != 1 || devices[0].ID != "device_legacy" || devices[0].EncryptedIconNonce != "" || devices[0].EncryptedIconCiphertext != "" {
		t.Fatalf("legacy device changed during migration: %+v", devices)
	}
	authenticated, err := repository.DefaultAuthContext(ctx)
	if err != nil {
		t.Fatal(err)
	}
	authenticated.DeviceID = "device_legacy"
	if result, _, err := repository.PushDelta(ctx, authenticated, testDelta(
		"first_v2_delta", "first_v2_mutation", "device_legacy", 7, "v2_ciphertext",
	)); err != nil {
		t.Fatalf("first v2 delta after v1 migration: %v", err)
	} else if result.Revision != 8 {
		t.Fatalf("first v2 revision = %d, want 8", result.Revision)
	}
	if _, err := repository.EnrollDevice(ctx, testEnrollment("device_new", "selector_new")); err != nil {
		t.Fatal(err)
	}
	devices, err = repository.ListDevices(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(devices) != 2 || devices[1].EncryptedIconCiphertext != "icon_ciphertext" {
		t.Fatalf("new encrypted icon missing after migration: %+v", devices)
	}
}

func TestPushPullAckAndSnapshot(t *testing.T) {
	repository := enrolledTestStore(t)
	ctx := context.Background()
	bootstrap, err := repository.Bootstrap(ctx)
	if err != nil {
		t.Fatal(err)
	}
	change := testChange("change_a", "bookmark_a", 0, "ciphertext_a")
	results, cursor, err := repository.Push(ctx, "device_a", []store.Change{change})
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 1 || results[0].Duplicate || results[0].Revision != 1 || cursor == "" {
		t.Fatalf("unexpected push result: %+v cursor=%s", results, cursor)
	}
	duplicates, duplicateCursor, err := repository.Push(ctx, "device_a", []store.Change{change})
	if err != nil {
		t.Fatal(err)
	}
	if !duplicates[0].Duplicate || duplicateCursor != cursor {
		t.Fatalf("retry was not idempotent: %+v %s", duplicates, duplicateCursor)
	}
	tampered := change
	tampered.Ciphertext = "different_ciphertext"
	if _, _, err := repository.Push(ctx, "device_a", []store.Change{tampered}); !errors.Is(err, store.ErrIdempotencyConflict) {
		t.Fatalf("tampered retry error = %v", err)
	}
	stale := testChange("change_b", "bookmark_a", 0, "ciphertext_b")
	if _, _, err := repository.Push(ctx, "device_a", []store.Change{stale}); !errors.Is(err, store.ErrRevisionConflict) {
		t.Fatalf("stale revision error = %v", err)
	}
	pulled, err := repository.Pull(ctx, bootstrap.ServerEpoch, 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(pulled.Changes) != 1 || pulled.Changes[0].Ciphertext != "ciphertext_a" || pulled.HasMore {
		t.Fatalf("unexpected pull: %+v", pulled)
	}
	if err := repository.Ack(ctx, "device_a", bootstrap.ServerEpoch, pulled.Head); err != nil {
		t.Fatal(err)
	}
	if err := repository.Ack(ctx, "device_a", "wrong_epoch", 0); !errors.Is(err, store.ErrCursorReset) {
		t.Fatalf("wrong epoch ack error = %v", err)
	}
	snapshot, err := repository.Snapshot(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(snapshot.Entities) != 1 || snapshot.Entities[0].Ciphertext != "ciphertext_a" {
		t.Fatalf("unexpected snapshot: %+v", snapshot)
	}
}

func TestTabSnapshotStateUsesCASAndAuditsCrossDeviceWriter(t *testing.T) {
	repository := enrolledTestStore(t)
	ctx := context.Background()
	second := testEnrollment("device_b", "selector_b")
	if _, err := repository.EnrollDevice(ctx, second); err != nil {
		t.Fatal(err)
	}
	first := store.TabSnapshot{
		ChangeID: "change_tabs_a",
		Revision: 1, SchemaVersion: 1, CryptoVersion: 1, KeyVersion: 1,
		Nonce: "nonce_a", Ciphertext: "ciphertext_a",
	}
	stored, cursor, err := repository.PutTabSnapshot(ctx, "device_b", "device_a", 0, first)
	if err != nil {
		t.Fatal(err)
	}
	if stored.Sequence == 0 || cursor == "" {
		t.Fatalf("snapshot missing sequence: %+v", stored)
	}
	if _, _, err := repository.PutTabSnapshot(ctx, "device_b", "device_a", 0, store.TabSnapshot{
		ChangeID: "change_tabs_a",
		Revision: 1, SchemaVersion: 1, CryptoVersion: 1, KeyVersion: 1,
		Nonce: "nonce_b", Ciphertext: "ciphertext_b",
	}); !errors.Is(err, store.ErrIdempotencyConflict) {
		t.Fatalf("changed retry error = %v", err)
	}
	snapshot, err := repository.Snapshot(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(snapshot.Tabs) != 1 || snapshot.Tabs[0].Ciphertext != "ciphertext_a" || snapshot.Tabs[0].ChangeID == "" || snapshot.Tabs[0].BaseRevision != 0 || snapshot.Tabs[0].WriterDeviceID != "device_b" || snapshot.Tabs[0].DeviceID != "device_a" {
		t.Fatalf("unexpected tab snapshot: %+v", snapshot.Tabs)
	}
	pulled, err := repository.Pull(ctx, "", 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(pulled.Changes) != 1 || pulled.Changes[0].DeviceID != "device_b" || pulled.Changes[0].EntityID != "device_a" {
		t.Fatalf("writer/target audit metadata was not preserved: %+v", pulled.Changes)
	}
}

func TestConcurrentCompareAndSwapHasOneWinner(t *testing.T) {
	repository := enrolledTestStore(t)
	ctx := context.Background()
	changes := []store.Change{
		testChange("change_a", "bookmark_a", 0, "ciphertext_a"),
		testChange("change_b", "bookmark_a", 0, "ciphertext_b"),
	}
	start := make(chan struct{})
	errorsSeen := make(chan error, len(changes))
	var group sync.WaitGroup
	for _, change := range changes {
		change := change
		group.Add(1)
		go func() {
			defer group.Done()
			<-start
			_, _, err := repository.Push(ctx, "device_a", []store.Change{change})
			errorsSeen <- err
		}()
	}
	close(start)
	group.Wait()
	close(errorsSeen)
	var successes int
	var conflicts int
	for err := range errorsSeen {
		switch {
		case err == nil:
			successes++
		case errors.Is(err, store.ErrRevisionConflict):
			conflicts++
		default:
			t.Fatalf("unexpected concurrency error: %v", err)
		}
	}
	if successes != 1 || conflicts != 1 {
		t.Fatalf("successes=%d conflicts=%d", successes, conflicts)
	}
}

func TestTokenHashStoredWithoutSecret(t *testing.T) {
	repository := openTestStore(t)
	ctx := context.Background()
	params := testEnrollment("device_a", "selector_a")
	params.TokenHash = []byte("hash-only-value")
	params.Recovery = &store.RecoveryEnvelope{CryptoVersion: 1, Nonce: "nonce", Ciphertext: "ciphertext"}
	if _, err := repository.EnrollDevice(ctx, params); err != nil {
		t.Fatal(err)
	}
	var storedHash []byte
	if err := repository.db.QueryRow(`SELECT token_hash FROM device_tokens WHERE selector = ?`, params.TokenSelector).Scan(&storedHash); err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(storedHash, params.TokenHash) {
		t.Fatalf("stored hash = %q", storedHash)
	}
}

func TestPullPaginatesBeforeResponseLimitAndSnapshotFailsClosed(t *testing.T) {
	repository := enrolledTestStore(t)
	ctx := context.Background()
	bootstrap, err := repository.Bootstrap(ctx)
	if err != nil {
		t.Fatal(err)
	}
	for index := range 6 {
		change := testChange(
			fmt.Sprintf("change_%d", index),
			fmt.Sprintf("bookmark_%d", index),
			0,
			strings.Repeat("A", 140<<10),
		)
		if _, _, err := repository.Push(ctx, "device_a", []store.Change{change}); err != nil {
			t.Fatal(err)
		}
	}
	pulled, err := repository.Pull(ctx, bootstrap.ServerEpoch, 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(pulled.Changes) >= 6 || !pulled.HasMore {
		t.Fatalf("large pull was not paginated: changes=%d hasMore=%v", len(pulled.Changes), pulled.HasMore)
	}
	if _, err := repository.Snapshot(ctx); !errors.Is(err, store.ErrSnapshotTooLarge) {
		t.Fatalf("large snapshot error = %v", err)
	}
}

func TestOpenRejectsDatabaseFromFutureMigration(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "future.sqlite3")
	repository, err := Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := repository.db.ExecContext(ctx, `
		INSERT INTO schema_migrations(version, name, checksum, applied_at)
		VALUES(999, 'future.sql', 'future', 0)`); err != nil {
		t.Fatal(err)
	}
	if err := repository.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(ctx, path); err == nil || !strings.Contains(err.Error(), "newer than supported") {
		t.Fatalf("future database error = %v", err)
	}
}

func TestPullRejectsLegacySingleChangeAboveResponseLimit(t *testing.T) {
	repository := enrolledTestStore(t)
	ctx := context.Background()
	bootstrap, err := repository.Bootstrap(ctx)
	if err != nil {
		t.Fatal(err)
	}
	change := testChange("legacy_oversize", "bookmark_oversize", 0, strings.Repeat("A", 800<<10))
	if _, _, err := repository.Push(ctx, "device_a", []store.Change{change}); err != nil {
		t.Fatal(err)
	}
	if _, err := repository.Pull(ctx, bootstrap.ServerEpoch, 0, 1); !errors.Is(err, store.ErrResponseTooLarge) {
		t.Fatalf("oversized pull error = %v", err)
	}
}

func openTestStore(t *testing.T) *Store {
	t.Helper()
	repository, err := Open(context.Background(), filepath.Join(t.TempDir(), "candy-sync.sqlite3"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := repository.Close(); err != nil {
			t.Errorf("close store: %v", err)
		}
	})
	return repository
}

func enrolledTestStore(t *testing.T) *Store {
	t.Helper()
	repository := openTestStore(t)
	params := testEnrollment("device_a", "selector_a")
	params.Recovery = &store.RecoveryEnvelope{CryptoVersion: 1, Nonce: "nonce", Ciphertext: "ciphertext"}
	if _, err := repository.EnrollDevice(context.Background(), params); err != nil {
		t.Fatal(err)
	}
	return repository
}

func testEnrollment(deviceID, selector string) store.EnrollDeviceParams {
	return store.EnrollDeviceParams{
		DeviceID:                deviceID,
		PublicKeyAlgorithm:      "X25519",
		PublicKey:               "public_key",
		EncryptedNameNonce:      "name_nonce",
		EncryptedNameCiphertext: "name_ciphertext",
		EncryptedIconNonce:      "icon_nonce",
		EncryptedIconCiphertext: "icon_ciphertext",
		CapabilitiesJSON:        `["tabs","bookmarks"]`,
		TokenSelector:           selector,
		TokenHash:               []byte("token_hash_" + selector),
	}
}

func testChange(changeID, entityID string, baseRevision int64, ciphertext string) store.Change {
	return store.Change{
		ChangeID:      changeID,
		Entity:        "bookmark",
		EntityID:      entityID,
		Operation:     "upsert",
		BaseRevision:  baseRevision,
		SchemaVersion: 1,
		CryptoVersion: 1,
		KeyVersion:    1,
		Nonce:         "nonce",
		Ciphertext:    ciphertext,
	}
}
