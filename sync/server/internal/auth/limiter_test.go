package auth

import (
	"testing"
	"time"
)

func TestAttemptLimiterBlocksAfterFiveFailuresAndRecovers(t *testing.T) {
	limiter := NewAttemptLimiter()
	now := time.Date(2026, 9, 2, 10, 0, 0, 0, time.UTC)
	limiter.now = func() time.Time { return now }
	for range 4 {
		limiter.Failure("client-a")
		if allowed, _ := limiter.Allow("client-a"); !allowed {
			t.Fatal("client blocked before fifth failure")
		}
	}
	limiter.Failure("client-a")
	if allowed, retry := limiter.Allow("client-a"); allowed || retry != time.Minute {
		t.Fatalf("blocked = %v retry = %v", !allowed, retry)
	}
	if allowed, _ := limiter.Allow("client-b"); !allowed {
		t.Fatal("independent client was blocked")
	}
	now = now.Add(time.Minute)
	if allowed, _ := limiter.Allow("client-a"); !allowed {
		t.Fatal("client did not recover after block period")
	}
}

func TestAttemptLimiterSuccessClearsFailures(t *testing.T) {
	limiter := NewAttemptLimiter()
	for range 4 {
		limiter.Failure("client")
	}
	limiter.Success("client")
	for range 4 {
		limiter.Failure("client")
	}
	if allowed, _ := limiter.Allow("client"); !allowed {
		t.Fatal("success did not clear prior failures")
	}
}
