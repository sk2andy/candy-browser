package auth

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"strings"
)

const tokenPrefix = "cst1_"

type Authenticator struct {
	usernameHash [sha256.Size]byte
	passwordHash [sha256.Size]byte
	tokenKey     [sha256.Size]byte
}

func New(username, password string) *Authenticator {
	usernameHash := sha256.Sum256([]byte(username))
	passwordHash := sha256.Sum256([]byte(password))
	keyInput := append([]byte("candy-sync-token-hash-v1\x00"+username+"\x00"), []byte(password)...)
	tokenKey := sha256.Sum256(keyInput)
	return &Authenticator{usernameHash: usernameHash, passwordHash: passwordHash, tokenKey: tokenKey}
}

func (a *Authenticator) CheckBasic(username, password string) bool {
	usernameHash := sha256.Sum256([]byte(username))
	passwordHash := sha256.Sum256([]byte(password))
	return subtle.ConstantTimeCompare(usernameHash[:], a.usernameHash[:]) == 1 &&
		subtle.ConstantTimeCompare(passwordHash[:], a.passwordHash[:]) == 1
}

func (a *Authenticator) NewToken() (token, selector string, hash []byte, err error) {
	selectorBytes := make([]byte, 12)
	secret := make([]byte, 32)
	if _, err = rand.Read(selectorBytes); err != nil {
		return "", "", nil, err
	}
	if _, err = rand.Read(secret); err != nil {
		return "", "", nil, err
	}
	selector = base64.RawURLEncoding.EncodeToString(selectorBytes)
	encodedSecret := base64.RawURLEncoding.EncodeToString(secret)
	token = tokenPrefix + selector + "." + encodedSecret
	return token, selector, a.HashTokenSecret(secret), nil
}

func (a *Authenticator) ParseAndHashToken(token string) (string, []byte, error) {
	if !strings.HasPrefix(token, tokenPrefix) {
		return "", nil, errors.New("invalid token prefix")
	}
	parts := strings.Split(strings.TrimPrefix(token, tokenPrefix), ".")
	if len(parts) != 2 || parts[0] == "" || parts[1] == "" {
		return "", nil, errors.New("invalid token format")
	}
	secret, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil || len(secret) != 32 {
		return "", nil, errors.New("invalid token secret")
	}
	if selector, err := base64.RawURLEncoding.DecodeString(parts[0]); err != nil || len(selector) != 12 {
		return "", nil, errors.New("invalid token selector")
	}
	return parts[0], a.HashTokenSecret(secret), nil
}

func (a *Authenticator) HashTokenSecret(secret []byte) []byte {
	mac := hmac.New(sha256.New, a.tokenKey[:])
	_, _ = mac.Write(secret)
	return mac.Sum(nil)
}

func EqualTokenHash(left, right []byte) bool {
	return hmac.Equal(left, right)
}
