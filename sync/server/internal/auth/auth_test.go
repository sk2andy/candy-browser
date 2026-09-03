package auth

import "testing"

func TestBasicAuthentication(t *testing.T) {
	authenticator := New("candy", "correct horse battery staple")
	if !authenticator.CheckBasic("candy", "correct horse battery staple") {
		t.Fatal("valid credentials rejected")
	}
	if authenticator.CheckBasic("candy", "wrong horse battery staple") {
		t.Fatal("invalid credentials accepted")
	}
}

func TestDeviceTokenRoundTrip(t *testing.T) {
	authenticator := New("candy", "correct horse battery staple")
	token, selector, wantHash, err := authenticator.NewToken()
	if err != nil {
		t.Fatal(err)
	}
	gotSelector, gotHash, err := authenticator.ParseAndHashToken(token)
	if err != nil {
		t.Fatal(err)
	}
	if gotSelector != selector || !EqualTokenHash(gotHash, wantHash) {
		t.Fatal("token did not round trip")
	}
	if token == selector {
		t.Fatal("token secret missing")
	}
}

func TestPasswordChangeInvalidatesTokenHash(t *testing.T) {
	oldAuth := New("candy", "correct horse battery staple")
	token, _, oldHash, err := oldAuth.NewToken()
	if err != nil {
		t.Fatal(err)
	}
	newAuth := New("candy", "different horse battery staple")
	_, newHash, err := newAuth.ParseAndHashToken(token)
	if err != nil {
		t.Fatal(err)
	}
	if EqualTokenHash(oldHash, newHash) {
		t.Fatal("password change must invalidate existing tokens")
	}
}
