package sqlite

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

func TestV2DeltaIsIdempotentAndUsesCAS(t *testing.T) {
	repository := enrolledTestStore(t)
	authenticated, err := repository.DefaultAuthContext(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	authenticated.DeviceID = "device_a"
	change := testDelta("change_delta_a", "mutation_a", "device_a", 0, "ciphertext_a")
	stored, cursor, err := repository.PushDelta(t.Context(), authenticated, change)
	if err != nil {
		t.Fatal(err)
	}
	if stored.Duplicate || stored.Revision != 1 || cursor == "" {
		t.Fatalf("unexpected first delta: %+v cursor=%q", stored, cursor)
	}
	if _, _, err := repository.PutTabSnapshot(t.Context(), "device_a", "device_a", 1, store.TabSnapshot{
		ChangeID: "legacy_after_promotion", Revision: 2, SchemaVersion: 1,
		CryptoVersion: 1, KeyVersion: 1, Nonce: "nonce", Ciphertext: "ciphertext",
	}); !errors.Is(err, store.ErrProtocolUpgradeRequired) {
		t.Fatalf("v1 write after v2 promotion error = %v", err)
	}
	retry, retryCursor, err := repository.PushDelta(t.Context(), authenticated, change)
	if err != nil {
		t.Fatal(err)
	}
	if !retry.Duplicate || retryCursor != cursor {
		t.Fatalf("retry = %+v cursor=%q", retry, retryCursor)
	}
	tampered := change
	tampered.Ciphertext = "ciphertext_b"
	if _, _, err := repository.PushDelta(t.Context(), authenticated, tampered); !errors.Is(err, store.ErrIdempotencyConflict) {
		t.Fatalf("tampered retry error = %v", err)
	}
	stale := testDelta("change_delta_b", "mutation_b", "device_a", 0, "ciphertext_b")
	if _, _, err := repository.PushDelta(t.Context(), authenticated, stale); !errors.Is(err, store.ErrRevisionConflict) {
		t.Fatalf("stale delta error = %v", err)
	}
	second := testDelta("change_delta_c", "mutation_c", "device_a", 1, "ciphertext_c")
	_, _, err = repository.PushDelta(t.Context(), authenticated, second)
	if err != nil {
		t.Fatal(err)
	}
	_, oldRetryCursor, err := repository.PushDelta(t.Context(), authenticated, change)
	if err != nil {
		t.Fatal(err)
	}
	if oldRetryCursor != cursor {
		t.Fatalf("old retry cursor must identify original commit: got=%q original=%q", oldRetryCursor, cursor)
	}
}

func TestV2DeltaTenantIsolation(t *testing.T) {
	repository := enrolledTestStore(t)
	first, err := repository.DefaultAuthContext(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	first.DeviceID = "device_a"
	second := addSecondTenant(t, repository)

	if _, _, err := repository.PushDelta(t.Context(), first, testDelta("change_first", "mutation_first", "device_a", 0, "ciphertext_first")); err != nil {
		t.Fatal(err)
	}
	if _, _, err := repository.PushDelta(t.Context(), second, testDelta("change_second", "mutation_second", "device_b", 0, "ciphertext_second")); err != nil {
		t.Fatal(err)
	}
	firstPull, err := repository.PullDeltas(t.Context(), first, "", 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	secondPull, err := repository.PullDeltas(t.Context(), second, "", 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(firstPull.Changes) != 1 || firstPull.Changes[0].WorkspaceID != first.WorkspaceID || firstPull.Changes[0].Ciphertext != "ciphertext_first" {
		t.Fatalf("first tenant leaked or missed changes: %+v", firstPull)
	}
	if len(secondPull.Changes) != 1 || secondPull.Changes[0].WorkspaceID != second.WorkspaceID || secondPull.Changes[0].Ciphertext != "ciphertext_second" {
		t.Fatalf("second tenant leaked or missed changes: %+v", secondPull)
	}
	crossTenant := testDelta("change_cross", "mutation_cross", "device_b", 1, "ciphertext_cross")
	if _, _, err := repository.PushDelta(t.Context(), first, crossTenant); !errors.Is(err, store.ErrDeviceNotFound) {
		t.Fatalf("cross-tenant target error = %v", err)
	}
}

func TestV1CommittedRetryRemainsIdempotentAfterV2Promotion(t *testing.T) {
	repository := enrolledTestStore(t)
	authenticated, err := repository.DefaultAuthContext(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	authenticated.DeviceID = "device_a"
	snapshot := store.TabSnapshot{
		ChangeID: "legacy_committed", Revision: 1, SchemaVersion: 1,
		CryptoVersion: 1, KeyVersion: 1, Nonce: "nonce", Ciphertext: "ciphertext",
	}
	first, firstCursor, err := repository.PutTabSnapshot(
		t.Context(), "device_a", "device_a", 0, snapshot,
	)
	if err != nil {
		t.Fatal(err)
	}
	if _, _, err := repository.PushDelta(
		t.Context(), authenticated,
		testDelta("change_after_legacy", "mutation_after_legacy", "device_a", 1, "delta_ciphertext"),
	); err != nil {
		t.Fatal(err)
	}
	retry, retryCursor, err := repository.PutTabSnapshot(
		t.Context(), "device_a", "device_a", 0, snapshot,
	)
	if err != nil {
		t.Fatal(err)
	}
	if retry.ChangeID != first.ChangeID || retryCursor != firstCursor {
		t.Fatalf("v1 retry changed after promotion: retry=%+v cursor=%q", retry, retryCursor)
	}
}

func testDelta(changeID, mutationID, targetID string, baseRevision int64, ciphertext string) store.Change {
	return store.Change{
		ChangeID:      changeID,
		MutationID:    mutationID,
		Entity:        "tabs",
		EntityID:      targetID,
		Operation:     "delta",
		BaseRevision:  baseRevision,
		SchemaVersion: 2,
		CryptoVersion: 1,
		KeyVersion:    1,
		Nonce:         "nonce",
		Ciphertext:    ciphertext,
	}
}

func addSecondTenant(t *testing.T, repository *Store) store.AuthContext {
	t.Helper()
	now := time.Now().UnixMilli()
	statements := []struct {
		query string
		args  []any
	}{
		{`INSERT INTO accounts(id, created_at) VALUES('acct_second', ?)`, []any{now}},
		{`INSERT INTO workspaces(id, kdf_algorithm, kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism, created_at) VALUES('workspace_second', 'argon2id-v1', 'salt', 65536, 3, 4, ?)`, []any{now}},
		{`INSERT INTO workspace_members(account_id, workspace_id, role, created_at) VALUES('acct_second', 'workspace_second', 'owner', ?)`, []any{now}},
		{`INSERT INTO v2_workspace_state(workspace_id, head_sequence) VALUES('workspace_second', 0)`, nil},
		{`INSERT INTO devices(id, workspace_id, account_id, public_key_algorithm, public_key, encrypted_name_nonce, encrypted_name_ciphertext, encrypted_icon_nonce, encrypted_icon_ciphertext, capabilities_json, created_at, last_seen_at) VALUES('device_b', 'workspace_second', 'acct_second', 'X25519', 'public_key', 'nonce', 'ciphertext', 'nonce', 'ciphertext', '["tabs"]', ?, ?)`, []any{now, now}},
	}
	for _, statement := range statements {
		if _, err := repository.db.ExecContext(context.Background(), statement.query, statement.args...); err != nil {
			t.Fatal(err)
		}
	}
	return store.AuthContext{AccountID: "acct_second", WorkspaceID: "workspace_second", DeviceID: "device_b"}
}
