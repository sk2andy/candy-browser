package sqlite

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
	"github.com/sk2andy/candy-browser/sync/server/migrations"
	_ "modernc.org/sqlite"
)

const currentMigrationVersion = 2

type Store struct {
	db *sql.DB
}

func Open(ctx context.Context, path string) (*Store, error) {
	if path != ":memory:" {
		if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
			return nil, fmt.Errorf("create database directory: %w", err)
		}
	}
	dsn := path
	if path != ":memory:" {
		dsn = "file:" + filepath.ToSlash(path)
	}
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	result := &Store{db: db}
	if err := result.configure(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	if err := result.migrate(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	if err := result.initialize(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	if path != ":memory:" {
		_ = os.Chmod(path, 0o600)
	}
	return result, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) Ready(ctx context.Context) error {
	if err := s.db.PingContext(ctx); err != nil {
		return err
	}
	var version int
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(MAX(version), 0) FROM schema_migrations`).Scan(&version); err != nil {
		return err
	}
	if version != currentMigrationVersion {
		return fmt.Errorf("database migration version %d, want %d", version, currentMigrationVersion)
	}
	return nil
}

func (s *Store) configure(ctx context.Context) error {
	for _, statement := range []string{
		`PRAGMA foreign_keys = ON`,
		`PRAGMA busy_timeout = 5000`,
		`PRAGMA synchronous = FULL`,
		`PRAGMA journal_mode = WAL`,
		`PRAGMA wal_autocheckpoint = 1000`,
	} {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			return fmt.Errorf("configure sqlite: %w", err)
		}
	}
	return nil
}

func (s *Store) migrate(ctx context.Context) error {
	if _, err := s.db.ExecContext(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version INTEGER PRIMARY KEY,
			name TEXT NOT NULL,
			checksum TEXT NOT NULL,
			applied_at INTEGER NOT NULL
		)`); err != nil {
		return fmt.Errorf("create migration table: %w", err)
	}
	names, err := fs.Glob(migrations.Files, "*.sql")
	if err != nil {
		return err
	}
	sort.Strings(names)
	var newestApplied int
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(MAX(version), 0) FROM schema_migrations`).Scan(&newestApplied); err != nil {
		return err
	}
	if newestApplied > len(names) {
		return fmt.Errorf("database migration version %d is newer than supported version %d", newestApplied, len(names))
	}
	for version, name := range names {
		data, err := migrations.Files.ReadFile(name)
		if err != nil {
			return err
		}
		digest := sha256.Sum256(data)
		checksum := hex.EncodeToString(digest[:])
		migrationVersion := version + 1
		var existing string
		err = s.db.QueryRowContext(ctx, `SELECT checksum FROM schema_migrations WHERE version = ?`, migrationVersion).Scan(&existing)
		if err == nil {
			if existing != checksum {
				return fmt.Errorf("migration %d checksum changed", migrationVersion)
			}
			continue
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return err
		}
		tx, err := s.db.BeginTx(ctx, nil)
		if err != nil {
			return err
		}
		if _, err = tx.ExecContext(ctx, string(data)); err == nil {
			_, err = tx.ExecContext(ctx,
				`INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES(?, ?, ?, ?)`,
				migrationVersion, name, checksum, time.Now().UnixMilli())
		}
		if err != nil {
			_ = tx.Rollback()
			return fmt.Errorf("apply migration %s: %w", name, err)
		}
		if err := tx.Commit(); err != nil {
			return fmt.Errorf("commit migration %s: %w", name, err)
		}
	}
	return nil
}

func (s *Store) initialize(ctx context.Context) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var count int
	if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM server_state`).Scan(&count); err != nil {
		return err
	}
	if count == 0 {
		epoch, err := randomID("epoch_", 16)
		if err != nil {
			return err
		}
		workspaceID, err := randomID("wsp_", 16)
		if err != nil {
			return err
		}
		salt, err := randomID("", 16)
		if err != nil {
			return err
		}
		now := time.Now().UnixMilli()
		if _, err := tx.ExecContext(ctx, `INSERT INTO server_state(singleton, server_epoch, created_at) VALUES(1, ?, ?)`, epoch, now); err != nil {
			return err
		}
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO workspaces(
				id, kdf_algorithm, kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism, created_at
			) VALUES(?, 'argon2id-v1', ?, 65536, 3, 4, ?)`, workspaceID, salt, now); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func randomID(prefix string, bytes int) (string, error) {
	value := make([]byte, bytes)
	if _, err := rand.Read(value); err != nil {
		return "", err
	}
	return prefix + base64.RawURLEncoding.EncodeToString(value), nil
}

func placeholders(count int) string {
	if count <= 0 {
		return ""
	}
	return strings.TrimSuffix(strings.Repeat("?,", count), ",")
}

var _ store.Repository = (*Store)(nil)
