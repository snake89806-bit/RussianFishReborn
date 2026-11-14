package store

import (
	"path/filepath"
	"testing"
	"time"
)

func TestPersistenceAndLeaderboard(t *testing.T) {
	path := filepath.Join(t.TempDir(), "game.json")
	s, e := New(path)
	if e != nil {
		t.Fatal(e)
	}
	u, e := s.CreateUser("angler", "Рыбак", "hash", "player")
	if e != nil {
		t.Fatal(e)
	}
	_, e = s.AddCatch(Catch{ID: "event-000001", UserID: u.ID, FishID: "pike", WeightKg: 4.2, CaughtAt: time.Now()})
	if e != nil {
		t.Fatal(e)
	}
	s2, e := New(path)
	if e != nil {
		t.Fatal(e)
	}
	board := s2.Leaderboard(10)
	if len(board) != 1 || board[0].BestWeightKg != 4.2 {
		t.Fatalf("unexpected leaderboard: %#v", board)
	}
	added, e := s2.AddCatch(Catch{ID: "event-000001", UserID: u.ID, FishID: "pike", WeightKg: 4.2, CaughtAt: time.Now()})
	if e != nil || added {
		t.Fatal("duplicate event was not ignored")
	}
}
