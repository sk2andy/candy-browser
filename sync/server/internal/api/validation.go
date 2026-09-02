package api

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strconv"
	"strings"

	"github.com/sk2andy/candy-browser/sync/server/internal/store"
)

var (
	errBodyTooLarge     = errors.New("request body too large")
	identifierPattern   = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)
	allowedCapabilities = map[string]bool{
		"tabs":      true,
		"bookmarks": true,
		"groups":    true,
	}
	allowedEntities = map[string]bool{
		"bookmark":     true,
		"reading_item": true,
		"tab_group":    true,
		"trail":        true,
		"history_item": true,
		"tabs":         true,
	}
)

func decodeJSON(w http.ResponseWriter, r *http.Request, limit int64, destination any) error {
	r.Body = http.MaxBytesReader(w, r.Body, limit)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(destination); err != nil {
		if strings.Contains(err.Error(), "request body too large") {
			return errBodyTooLarge
		}
		return fmt.Errorf("decode JSON: %w", err)
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		if strings.Contains(fmt.Sprint(err), "request body too large") {
			return errBodyTooLarge
		}
		return errors.New("request body must contain exactly one JSON object")
	}
	return nil
}

func validateIdentifier(value string, max int) error {
	if value == "" || len(value) > max || !identifierPattern.MatchString(strings.ReplaceAll(value, "-", "_")) {
		return errors.New("identifier contains unsupported characters or length")
	}
	return nil
}

func validateOpaque(value string, minimum, maximum int) error {
	if len(value) < minimum || len(value) > maximum || strings.Contains(value, "=") {
		return errors.New("opaque value has unsupported length or padding")
	}
	decoded, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil || len(decoded) == 0 {
		return errors.New("opaque value must be unpadded base64url")
	}
	return nil
}

func validateNonce(value string) error {
	decoded, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil || len(decoded) != 12 || strings.Contains(value, "=") {
		return errors.New("nonce must encode exactly 12 bytes as unpadded base64url")
	}
	return nil
}

func validateEncryptedValue(value encryptedValueDTO) error {
	if err := validateNonce(value.Nonce); err != nil {
		return err
	}
	return validateOpaque(value.Ciphertext, 22, 512<<10)
}

func validateEncryptedIcon(value encryptedValueDTO) error {
	if err := validateNonce(value.Nonce); err != nil {
		return err
	}
	return validateOpaque(value.Ciphertext, 22, 4096)
}

func validateDevicePublicIdentity(algorithm, encodedPublicKey, encodedFingerprint string) error {
	if algorithm != "ECDH-P256-SPKI" {
		return errors.New("publicKeyAlgorithm must be ECDH-P256-SPKI")
	}
	if strings.Contains(encodedPublicKey, "=") || strings.Contains(encodedFingerprint, "=") {
		return errors.New("public key and fingerprint must use unpadded base64url")
	}
	publicKeyBytes, err := base64.RawURLEncoding.DecodeString(encodedPublicKey)
	if err != nil {
		return errors.New("publicKey must be DER SPKI encoded as unpadded base64url")
	}
	parsed, err := x509.ParsePKIXPublicKey(publicKeyBytes)
	if err != nil {
		return errors.New("publicKey must contain a valid DER SPKI public key")
	}
	publicKey, ok := parsed.(*ecdsa.PublicKey)
	if !ok || publicKey.Curve == nil || publicKey.Curve.Params().Name != "P-256" || !publicKey.Curve.IsOnCurve(publicKey.X, publicKey.Y) {
		return errors.New("publicKey must contain a P-256 public key")
	}
	canonical, err := x509.MarshalPKIXPublicKey(publicKey)
	if err != nil || !bytes.Equal(canonical, publicKeyBytes) {
		return errors.New("publicKey must use canonical DER SPKI encoding")
	}
	fingerprint, err := base64.RawURLEncoding.DecodeString(encodedFingerprint)
	if err != nil || len(fingerprint) != sha256.Size {
		return errors.New("deviceKeyFingerprint must encode exactly 32 bytes as unpadded base64url")
	}
	digest := sha256.Sum256(publicKeyBytes)
	if subtle.ConstantTimeCompare(fingerprint, digest[:]) != 1 {
		return errors.New("deviceKeyFingerprint must match publicKey")
	}
	return nil
}

func validateRecoveryEnvelope(value recoveryEnvelopeDTO) error {
	if value.CryptoVersion != 1 {
		return errors.New("recovery envelope cryptoVersion must be 1")
	}
	return validateEncryptedValue(encryptedValueDTO{Nonce: value.Nonce, Ciphertext: value.Ciphertext})
}

func validateCapabilities(values []string) ([]string, error) {
	if len(values) == 0 || len(values) > 16 {
		return nil, errors.New("capabilities must contain between 1 and 16 items")
	}
	seen := make(map[string]bool, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		if !allowedCapabilities[value] || seen[value] {
			return nil, errors.New("capabilities contain unsupported or duplicate values")
		}
		seen[value] = true
		result = append(result, value)
	}
	return result, nil
}

func validateIdempotencyKey(value string) error {
	if len(value) < 8 || len(value) > 128 {
		return errors.New("Idempotency-Key must contain between 8 and 128 characters")
	}
	if !identifierPattern.MatchString(value) {
		return errors.New("Idempotency-Key must use letters, digits, underscore, or hyphen")
	}
	return nil
}

func validateChange(value changeDTO, authenticatedDeviceID string) (store.Change, error) {
	if err := validateIdentifier(value.ChangeID, 128); err != nil {
		return store.Change{}, fmt.Errorf("invalid changeId: %w", err)
	}
	if value.DeviceID != "" && value.DeviceID != authenticatedDeviceID {
		return store.Change{}, errors.New("deviceId must match authenticated device")
	}
	if !allowedEntities[value.Entity] {
		return store.Change{}, errors.New("unsupported entity")
	}
	if err := validateIdentifier(value.EntityID, 128); err != nil {
		return store.Change{}, fmt.Errorf("invalid entityId: %w", err)
	}
	if value.Entity == "tabs" && value.EntityID != authenticatedDeviceID {
		return store.Change{}, errors.New("tabs entityId must match authenticated device")
	}
	if value.Operation != "upsert" && value.Operation != "delete" && value.Operation != "snapshot" {
		return store.Change{}, errors.New("unsupported operation")
	}
	if value.Entity == "tabs" && value.Operation != "snapshot" {
		return store.Change{}, errors.New("tabs operation must be snapshot")
	}
	base, err := parseRevision(value.BaseRevision)
	if err != nil {
		return store.Change{}, fmt.Errorf("invalid baseRevision: %w", err)
	}
	revision := int64(0)
	if value.Revision != "" {
		revision, err = parseRevision(value.Revision)
		if err != nil || revision != base+1 {
			return store.Change{}, errors.New("revision must equal baseRevision plus one")
		}
	}
	if err := validateEnvelope(value.SchemaVersion, value.CryptoVersion, value.KeyVersion, value.Nonce, value.Ciphertext); err != nil {
		return store.Change{}, err
	}
	return store.Change{
		ChangeID:      value.ChangeID,
		DeviceID:      authenticatedDeviceID,
		Entity:        value.Entity,
		EntityID:      value.EntityID,
		Operation:     value.Operation,
		BaseRevision:  base,
		Revision:      revision,
		SchemaVersion: value.SchemaVersion,
		CryptoVersion: value.CryptoVersion,
		KeyVersion:    value.KeyVersion,
		Nonce:         value.Nonce,
		Ciphertext:    value.Ciphertext,
	}, nil
}

func validateEnvelope(schemaVersion, cryptoVersion, keyVersion int, nonce, ciphertext string) error {
	if schemaVersion != 1 || cryptoVersion != 1 || keyVersion < 1 {
		return errors.New("unsupported schema, crypto, or key version")
	}
	if err := validateNonce(nonce); err != nil {
		return err
	}
	if err := validateOpaque(ciphertext, 22, 512<<10); err != nil {
		return errors.New("ciphertext must be unpadded base64url within limits")
	}
	return nil
}

func parseRevision(value string) (int64, error) {
	if value == "" || value[0] == '+' || len(value) > 19 {
		return 0, errors.New("revision must be an unsigned decimal string")
	}
	result, err := strconv.ParseInt(value, 10, 64)
	if err != nil || result < 0 {
		return 0, errors.New("revision must be an unsigned decimal string")
	}
	return result, nil
}

func parseCursor(value string) (string, int64, error) {
	if value == "" {
		return "", 0, nil
	}
	separator := strings.LastIndexByte(value, '.')
	if separator <= 0 || separator == len(value)-1 {
		return "", 0, errors.New("cursor must contain epoch and sequence")
	}
	if err := validateIdentifier(value[:separator], 128); err != nil {
		return "", 0, errors.New("cursor epoch is invalid")
	}
	sequence, err := parseRevision(value[separator+1:])
	if err != nil {
		return "", 0, errors.New("cursor sequence is invalid")
	}
	return value[:separator], sequence, nil
}

func formatCursor(epoch string, sequence int64) string {
	return epoch + "." + strconv.FormatInt(sequence, 10)
}
