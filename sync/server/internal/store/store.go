package store

import (
	"context"
	"errors"
	"time"
)

var (
	ErrConflict                = errors.New("conflict")
	ErrCursorReset             = errors.New("cursor reset required")
	ErrDeviceNotFound          = errors.New("device not found")
	ErrDeviceRevoked           = errors.New("device revoked")
	ErrIdempotencyConflict     = errors.New("idempotency conflict")
	ErrRevisionConflict        = errors.New("revision conflict")
	ErrSnapshotTooLarge        = errors.New("snapshot too large")
	ErrResponseTooLarge        = errors.New("response too large")
	ErrProtocolUpgradeRequired = errors.New("protocol upgrade required")
)

type Bootstrap struct {
	WorkspaceID           string
	Initialized           bool
	ServerEpoch           string
	KDFAlgorithm          string
	KDFSalt               string
	KDFMemoryKiB          int
	KDFIterations         int
	KDFParallelism        int
	RecoveryCryptoVersion int
	RecoveryNonce         string
	RecoveryCiphertext    string
}

type RecoveryEnvelope struct {
	CryptoVersion int
	Nonce         string
	Ciphertext    string
}

type EnrollDeviceParams struct {
	DeviceID                string
	PublicKeyAlgorithm      string
	PublicKey               string
	EncryptedNameNonce      string
	EncryptedNameCiphertext string
	EncryptedIconNonce      string
	EncryptedIconCiphertext string
	CapabilitiesJSON        string
	TokenSelector           string
	TokenHash               []byte
	TokenExpiresAt          *time.Time
	Recovery                *RecoveryEnvelope
}

type Device struct {
	ID                      string
	PublicKeyAlgorithm      string
	PublicKey               string
	EncryptedNameNonce      string
	EncryptedNameCiphertext string
	EncryptedIconNonce      string
	EncryptedIconCiphertext string
	CapabilitiesJSON        string
	CreatedAt               time.Time
	LastSeenAt              time.Time
	RevokedAt               *time.Time
}

type Token struct {
	AccountID   string
	WorkspaceID string
	DeviceID    string
	Hash        []byte
	Expires     *time.Time
	Revoked     *time.Time
}

// AuthContext is server-derived tenant identity. Callers must never populate it
// from request JSON, query parameters, or WebSocket frames.
type AuthContext struct {
	AccountID   string
	WorkspaceID string
	DeviceID    string
}

type Change struct {
	Sequence      int64
	ChangeID      string
	MutationID    string
	WorkspaceID   string
	DeviceID      string
	Entity        string
	EntityID      string
	Operation     string
	BaseRevision  int64
	Revision      int64
	SchemaVersion int
	CryptoVersion int
	KeyVersion    int
	Nonce         string
	Ciphertext    string
}

type PushResult struct {
	ChangeID  string
	Sequence  int64
	Revision  int64
	Duplicate bool
}

type PullResult struct {
	Epoch   string
	Changes []Change
	Head    int64
	HasMore bool
}

type TabSnapshot struct {
	ChangeID       string
	WriterDeviceID string
	DeviceID       string
	BaseRevision   int64
	Revision       int64
	SchemaVersion  int
	CryptoVersion  int
	KeyVersion     int
	Nonce          string
	Ciphertext     string
	Sequence       int64
}

type Snapshot struct {
	Epoch    string
	Head     int64
	Entities []Change
	Tabs     []TabSnapshot
}

type Repository interface {
	Close() error
	Ready(context.Context) error
	Bootstrap(context.Context) (Bootstrap, error)
	EnrollDevice(context.Context, EnrollDeviceParams) (Device, error)
	Token(context.Context, string) (Token, error)
	ListDevices(context.Context) ([]Device, error)
	RevokeDevice(context.Context, string) error
	Push(context.Context, string, []Change) ([]PushResult, string, error)
	Pull(context.Context, string, int64, int) (PullResult, error)
	Ack(context.Context, string, string, int64) error
	Snapshot(context.Context) (Snapshot, error)
	PutTabSnapshot(context.Context, string, string, int64, TabSnapshot) (TabSnapshot, string, error)
	DefaultAuthContext(context.Context) (AuthContext, error)
	PushDelta(context.Context, AuthContext, Change) (PushResult, string, error)
	PullDeltas(context.Context, AuthContext, string, int64, int) (PullResult, error)
}
