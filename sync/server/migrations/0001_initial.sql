CREATE TABLE server_state (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    server_epoch TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE workspaces (
    id TEXT PRIMARY KEY,
    kdf_algorithm TEXT NOT NULL,
    kdf_salt TEXT NOT NULL,
    kdf_memory_kib INTEGER NOT NULL,
    kdf_iterations INTEGER NOT NULL,
    kdf_parallelism INTEGER NOT NULL,
    recovery_crypto_version INTEGER,
    recovery_nonce TEXT,
    recovery_ciphertext TEXT,
    created_at INTEGER NOT NULL,
    CHECK (
        (recovery_crypto_version IS NULL AND recovery_nonce IS NULL AND recovery_ciphertext IS NULL) OR
        (recovery_crypto_version IS NOT NULL AND recovery_nonce IS NOT NULL AND recovery_ciphertext IS NOT NULL)
    )
);

CREATE TABLE devices (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    public_key_algorithm TEXT NOT NULL,
    public_key TEXT NOT NULL,
    encrypted_name_nonce TEXT NOT NULL,
    encrypted_name_ciphertext TEXT NOT NULL,
    capabilities_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    revoked_at INTEGER
);

CREATE TABLE device_tokens (
    selector TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    token_hash BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER,
    revoked_at INTEGER
);

CREATE INDEX device_tokens_device_idx ON device_tokens(device_id);

CREATE TABLE changes (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    change_id TEXT NOT NULL,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    entity TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    base_revision INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    schema_version INTEGER NOT NULL,
    crypto_version INTEGER NOT NULL,
    key_version INTEGER NOT NULL,
    nonce TEXT NOT NULL,
    ciphertext TEXT NOT NULL,
    envelope_hash BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE(device_id, change_id)
);

CREATE INDEX changes_sequence_idx ON changes(sequence);

CREATE TABLE entity_state (
    entity TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    revision INTEGER NOT NULL,
    operation TEXT NOT NULL,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    schema_version INTEGER NOT NULL,
    crypto_version INTEGER NOT NULL,
    key_version INTEGER NOT NULL,
    nonce TEXT NOT NULL,
    ciphertext TEXT NOT NULL,
    updated_sequence INTEGER NOT NULL REFERENCES changes(sequence) ON DELETE CASCADE,
    PRIMARY KEY(entity, entity_id)
);

CREATE TABLE tab_snapshots (
    device_id TEXT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    revision INTEGER NOT NULL,
    schema_version INTEGER NOT NULL,
    crypto_version INTEGER NOT NULL,
    key_version INTEGER NOT NULL,
    nonce TEXT NOT NULL,
    ciphertext TEXT NOT NULL,
    updated_sequence INTEGER NOT NULL REFERENCES changes(sequence) ON DELETE CASCADE
);

CREATE TABLE device_cursors (
    device_id TEXT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    server_epoch TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    acknowledged_at INTEGER NOT NULL
);
