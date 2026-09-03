package migrations

import "embed"

// Files contains forward-only SQLite migrations.
//
//go:embed *.sql
var Files embed.FS
