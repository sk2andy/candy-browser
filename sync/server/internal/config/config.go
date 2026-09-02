package config

import (
	"errors"
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	defaultListenAddress = ":8080"
	defaultDatabasePath  = "/data/candy-sync.sqlite3"
	defaultMaxBodyBytes  = int64(1 << 20)
	defaultMaxBatch      = 250
)

type Config struct {
	Username       string
	Password       string
	ListenAddress  string
	DatabasePath   string
	PublicURL      *url.URL
	LogLevel       string
	TokenTTL       time.Duration
	MaxBodyBytes   int64
	MaxBatch       int
	ClientIPHeader string
}

func Load() (Config, error) {
	return load(os.LookupEnv)
}

func load(lookup func(string) (string, bool)) (Config, error) {
	if _, ok := lookup("CANDY_SYNC_PASSPHRASE"); ok {
		return Config{}, errors.New("CANDY_SYNC_PASSPHRASE must not be configured on the server: the E2EE passphrase belongs only on client devices")
	}

	username, _ := lookup("CANDY_SYNC_USERNAME")
	password, _ := lookup("CANDY_SYNC_PASSWORD")
	if err := validateCredential("CANDY_SYNC_USERNAME", username, 1); err != nil {
		return Config{}, err
	}
	if strings.TrimSpace(username) != username || strings.Contains(username, ":") {
		return Config{}, errors.New("CANDY_SYNC_USERNAME must not start or end with whitespace or contain ':'")
	}
	if err := validateCredential("CANDY_SYNC_PASSWORD", password, 16); err != nil {
		return Config{}, err
	}

	cfg := Config{
		Username:       username,
		Password:       password,
		ListenAddress:  valueOrDefault(lookup, "CANDY_SYNC_LISTEN_ADDR", defaultListenAddress),
		DatabasePath:   valueOrDefault(lookup, "CANDY_SYNC_DB_PATH", defaultDatabasePath),
		LogLevel:       valueOrDefault(lookup, "CANDY_SYNC_LOG_LEVEL", "info"),
		MaxBodyBytes:   defaultMaxBodyBytes,
		MaxBatch:       defaultMaxBatch,
		ClientIPHeader: valueOrDefault(lookup, "CANDY_SYNC_CLIENT_IP_HEADER", ""),
	}

	if strings.TrimSpace(cfg.ListenAddress) == "" {
		return Config{}, errors.New("CANDY_SYNC_LISTEN_ADDR must not be empty")
	}
	if strings.TrimSpace(cfg.DatabasePath) == "" {
		return Config{}, errors.New("CANDY_SYNC_DB_PATH must not be empty")
	}
	if cfg.LogLevel != "debug" && cfg.LogLevel != "info" && cfg.LogLevel != "warn" && cfg.LogLevel != "error" {
		return Config{}, fmt.Errorf("CANDY_SYNC_LOG_LEVEL must be one of debug, info, warn, error")
	}
	if cfg.ClientIPHeader != "" && cfg.ClientIPHeader != "X-Forwarded-For" && cfg.ClientIPHeader != "X-Real-IP" {
		return Config{}, errors.New("CANDY_SYNC_CLIENT_IP_HEADER must be empty, X-Forwarded-For, or X-Real-IP")
	}

	if raw, ok := lookup("CANDY_SYNC_PUBLIC_URL"); ok && raw != "" {
		parsed, err := url.Parse(raw)
		if err != nil || parsed.Host == "" || parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" {
			return Config{}, errors.New("CANDY_SYNC_PUBLIC_URL must be an absolute URL without credentials, query, or fragment")
		}
		localHTTP := parsed.Scheme == "http" && isLocalHost(parsed.Hostname())
		if parsed.Scheme != "https" && !localHTTP {
			return Config{}, errors.New("CANDY_SYNC_PUBLIC_URL must use HTTPS; HTTP is allowed only for localhost")
		}
		cfg.PublicURL = parsed
	}

	if raw, ok := lookup("CANDY_SYNC_TOKEN_TTL"); ok && raw != "" {
		ttl, err := time.ParseDuration(raw)
		if err != nil || ttl < 0 {
			return Config{}, errors.New("CANDY_SYNC_TOKEN_TTL must be zero or a positive Go duration")
		}
		cfg.TokenTTL = ttl
	}
	if raw, ok := lookup("CANDY_SYNC_MAX_BODY_BYTES"); ok && raw != "" {
		value, err := strconv.ParseInt(raw, 10, 64)
		if err != nil || value < 1024 || value > 16<<20 {
			return Config{}, errors.New("CANDY_SYNC_MAX_BODY_BYTES must be between 1024 and 16777216")
		}
		cfg.MaxBodyBytes = value
	}
	if raw, ok := lookup("CANDY_SYNC_MAX_BATCH"); ok && raw != "" {
		value, err := strconv.Atoi(raw)
		if err != nil || value < 1 || value > 250 {
			return Config{}, errors.New("CANDY_SYNC_MAX_BATCH must be between 1 and 250")
		}
		cfg.MaxBatch = value
	}

	return cfg, nil
}

func validateCredential(name, value string, minimum int) error {
	if len(value) < minimum || len(value) > 1024 {
		return fmt.Errorf("%s must contain between %d and 1024 bytes", name, minimum)
	}
	if strings.ContainsAny(value, "\r\n\x00") {
		return fmt.Errorf("%s must not contain control delimiters", name)
	}
	return nil
}

func valueOrDefault(lookup func(string) (string, bool), name, fallback string) string {
	if value, ok := lookup(name); ok {
		return value
	}
	return fallback
}

func isLocalHost(host string) bool {
	return host == "localhost" || host == "127.0.0.1" || host == "::1"
}
