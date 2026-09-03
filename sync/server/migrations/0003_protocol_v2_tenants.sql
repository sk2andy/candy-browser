CREATE TABLE accounts (
    id TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL
);

ALTER TABLE workspaces ADD COLUMN protocol_floor INTEGER NOT NULL DEFAULT 1
    CHECK(protocol_floor IN (1, 2));

CREATE TABLE workspace_members (
    account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK(role IN ('owner', 'member')),
    created_at INTEGER NOT NULL,
    PRIMARY KEY(account_id, workspace_id)
);

INSERT INTO accounts(id, created_at)
SELECT 'acct_default', created_at FROM server_state WHERE singleton = 1;

INSERT INTO workspace_members(account_id, workspace_id, role, created_at)
SELECT 'acct_default', id, 'owner', created_at FROM workspaces;

ALTER TABLE devices ADD COLUMN account_id TEXT REFERENCES accounts(id);
UPDATE devices SET account_id = 'acct_default' WHERE account_id IS NULL;

CREATE INDEX devices_workspace_idx ON devices(workspace_id, id);

CREATE TABLE v2_workspace_state (
    workspace_id TEXT PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,
    head_sequence INTEGER NOT NULL DEFAULT 0 CHECK(head_sequence >= 0)
);

INSERT INTO v2_workspace_state(workspace_id, head_sequence)
SELECT id, 0 FROM workspaces;

CREATE TABLE v2_tab_heads (
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    target_device_id TEXT NOT NULL,
    revision INTEGER NOT NULL CHECK(revision >= 0),
    PRIMARY KEY(workspace_id, target_device_id)
);

CREATE TABLE v2_changes (
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL CHECK(sequence > 0),
    change_id TEXT NOT NULL,
    mutation_id TEXT NOT NULL,
    writer_device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    target_device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    base_revision INTEGER NOT NULL CHECK(base_revision >= 0),
    revision INTEGER NOT NULL CHECK(revision = base_revision + 1),
    schema_version INTEGER NOT NULL CHECK(schema_version = 2),
    crypto_version INTEGER NOT NULL CHECK(crypto_version = 1),
    key_version INTEGER NOT NULL CHECK(key_version >= 1),
    nonce TEXT NOT NULL,
    ciphertext TEXT NOT NULL,
    envelope_hash BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY(workspace_id, sequence),
    UNIQUE(workspace_id, writer_device_id, change_id),
    UNIQUE(workspace_id, mutation_id)
);

CREATE INDEX v2_changes_pull_idx ON v2_changes(workspace_id, sequence);

INSERT INTO v2_tab_heads(workspace_id, target_device_id, revision)
SELECT d.workspace_id, ts.device_id, ts.revision
FROM tab_snapshots ts
JOIN devices d ON d.id = ts.device_id;
