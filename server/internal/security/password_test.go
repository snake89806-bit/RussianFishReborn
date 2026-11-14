package security

import "testing"

func TestPasswordHashRoundTrip(t *testing.T) {
	h, e := HashPassword("correct-horse-battery")
	if e != nil {
		t.Fatal(e)
	}
	if !VerifyPassword("correct-horse-battery", h) {
		t.Fatal("password did not verify")
	}
	if VerifyPassword("wrong-password", h) {
		t.Fatal("wrong password verified")
	}
}
