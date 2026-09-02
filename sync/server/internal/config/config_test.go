package config

import (
	"strings"
	"testing"
)

func TestLoadRequiresCredentials(t *testing.T) {
	_, err := load(mapLookup(map[string]string{}))
	if err == nil || !strings.Contains(err.Error(), "CANDY_SYNC_USERNAME") {
		t.Fatalf("expected username error, got %v", err)
	}
}

func TestLoadRejectsServerPassphrase(t *testing.T) {
	_, err := load(mapLookup(map[string]string{
		"CANDY_SYNC_USERNAME":   "candy",
		"CANDY_SYNC_PASSWORD":   "correct horse battery staple",
		"CANDY_SYNC_PASSPHRASE": "never-server-side",
	}))
	if err == nil || !strings.Contains(err.Error(), "belongs only on client devices") {
		t.Fatalf("expected E2EE error, got %v", err)
	}
}

func TestLoadRejectsEvenEmptyServerPassphraseVariable(t *testing.T) {
	_, err := load(mapLookup(map[string]string{
		"CANDY_SYNC_USERNAME":   "candy",
		"CANDY_SYNC_PASSWORD":   "correct horse battery staple",
		"CANDY_SYNC_PASSPHRASE": "",
	}))
	if err == nil || !strings.Contains(err.Error(), "belongs only on client devices") {
		t.Fatalf("expected E2EE error, got %v", err)
	}
}

func TestLoadRejectsUsernameThatBasicAuthCannotRepresent(t *testing.T) {
	for _, username := range []string{" candy", "candy ", "can:dy"} {
		_, err := load(mapLookup(map[string]string{
			"CANDY_SYNC_USERNAME": username,
			"CANDY_SYNC_PASSWORD": "correct horse battery staple",
		}))
		if err == nil {
			t.Fatalf("username %q was accepted", username)
		}
	}
}

func TestLoadAppliesSafeDefaults(t *testing.T) {
	cfg, err := load(mapLookup(map[string]string{
		"CANDY_SYNC_USERNAME": "candy",
		"CANDY_SYNC_PASSWORD": "correct horse battery staple",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if cfg.ListenAddress != ":8080" || cfg.DatabasePath != "/data/candy-sync.sqlite3" {
		t.Fatalf("unexpected defaults: %+v", cfg)
	}
	if cfg.MaxBodyBytes != 1<<20 || cfg.MaxBatch != 250 {
		t.Fatalf("unexpected limits: %+v", cfg)
	}
}

func TestLoadAcceptsLocalHTTPOnly(t *testing.T) {
	for _, test := range []struct {
		url     string
		wantErr bool
	}{
		{url: "http://localhost:8080", wantErr: false},
		{url: "https://sync.example.net", wantErr: false},
		{url: "http://sync.example.net", wantErr: true},
		{url: "https://user@example.net", wantErr: true},
	} {
		t.Run(test.url, func(t *testing.T) {
			_, err := load(mapLookup(map[string]string{
				"CANDY_SYNC_USERNAME":   "candy",
				"CANDY_SYNC_PASSWORD":   "correct horse battery staple",
				"CANDY_SYNC_PUBLIC_URL": test.url,
			}))
			if (err != nil) != test.wantErr {
				t.Fatalf("error = %v, wantErr = %v", err, test.wantErr)
			}
		})
	}
}

func TestLoadRejectsBatchAboveProtocolMaximum(t *testing.T) {
	_, err := load(mapLookup(map[string]string{
		"CANDY_SYNC_USERNAME":  "candy",
		"CANDY_SYNC_PASSWORD":  "correct horse battery staple",
		"CANDY_SYNC_MAX_BATCH": "251",
	}))
	if err == nil || !strings.Contains(err.Error(), "between 1 and 250") {
		t.Fatalf("batch limit error = %v", err)
	}
}

func TestLoadValidatesTrustedClientIPHeader(t *testing.T) {
	for _, header := range []string{"", "X-Forwarded-For", "X-Real-IP"} {
		_, err := load(mapLookup(map[string]string{
			"CANDY_SYNC_USERNAME":         "candy",
			"CANDY_SYNC_PASSWORD":         "correct horse battery staple",
			"CANDY_SYNC_CLIENT_IP_HEADER": header,
		}))
		if err != nil {
			t.Fatalf("header %q: %v", header, err)
		}
	}
	_, err := load(mapLookup(map[string]string{
		"CANDY_SYNC_USERNAME":         "candy",
		"CANDY_SYNC_PASSWORD":         "correct horse battery staple",
		"CANDY_SYNC_CLIENT_IP_HEADER": "Forwarded",
	}))
	if err == nil {
		t.Fatal("unsupported client IP header was accepted")
	}
}

func mapLookup(values map[string]string) func(string) (string, bool) {
	return func(name string) (string, bool) {
		value, ok := values[name]
		return value, ok
	}
}
