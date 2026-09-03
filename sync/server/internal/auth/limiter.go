package auth

import (
	"sync"
	"time"
)

const (
	maxBasicFailures = 5
	basicBlockPeriod = time.Minute
	maxLimiterKeys   = 4096
)

type attemptState struct {
	failures     int
	blockedUntil time.Time
	updatedAt    time.Time
}

// AttemptLimiter bounds online password guessing without storing credentials.
// It deliberately keys on the transport peer rather than trusting forwarded headers.
type AttemptLimiter struct {
	mu      sync.Mutex
	entries map[string]attemptState
	now     func() time.Time
}

func NewAttemptLimiter() *AttemptLimiter {
	return &AttemptLimiter{entries: make(map[string]attemptState), now: time.Now}
}

func (l *AttemptLimiter) Allow(key string) (bool, time.Duration) {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	state, ok := l.entries[key]
	if !ok || !now.Before(state.blockedUntil) {
		if ok && !state.blockedUntil.IsZero() {
			delete(l.entries, key)
		}
		return true, 0
	}
	return false, state.blockedUntil.Sub(now)
}

func (l *AttemptLimiter) Failure(key string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	state := l.entries[key]
	if !state.blockedUntil.IsZero() && !now.Before(state.blockedUntil) {
		state = attemptState{}
	}
	state.failures++
	state.updatedAt = now
	if state.failures >= maxBasicFailures {
		state.blockedUntil = now.Add(basicBlockPeriod)
	}
	l.entries[key] = state
	if len(l.entries) > maxLimiterKeys {
		l.prune(now)
	}
}

func (l *AttemptLimiter) Success(key string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	delete(l.entries, key)
}

func (l *AttemptLimiter) prune(now time.Time) {
	for key, state := range l.entries {
		if !now.Before(state.blockedUntil) && now.Sub(state.updatedAt) >= basicBlockPeriod {
			delete(l.entries, key)
		}
	}
	if len(l.entries) <= maxLimiterKeys {
		return
	}
	for key := range l.entries {
		delete(l.entries, key)
		if len(l.entries) <= maxLimiterKeys/2 {
			return
		}
	}
}
