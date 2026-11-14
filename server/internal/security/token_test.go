package security

import (
	"testing"
	"time"
)

func TestTokenRoundTripAndTamper(t *testing.T) {
	secret := []byte("a-long-test-secret-that-is-at-least-32-bytes")
	token, e := IssueToken(secret, "user-1", "player", time.Hour)
	if e != nil {
		t.Fatal(e)
	}
	c, e := VerifyToken(secret, token)
	if e != nil || c.UserID != "user-1" {
		t.Fatalf("bad claims: %#v %v", c, e)
	}
	if _, e = VerifyToken(secret, token+"x"); e == nil {
		t.Fatal("tampered token accepted")
	}
}
