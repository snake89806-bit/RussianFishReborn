package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"

	"russianfishingreborn/server/internal/security"
	"russianfishingreborn/server/internal/store"
)

func testServer(t *testing.T) (*Server, store.User, store.User) {
	t.Helper()
	db, err := store.New(filepath.Join(t.TempDir(), "db.json"))
	if err != nil {
		t.Fatal(err)
	}
	player, err := db.CreateUser("player01", "Игрок", "unused", "player")
	if err != nil {
		t.Fatal(err)
	}
	admin, err := db.CreateUser("owner01", "Владелец", "unused", "admin")
	if err != nil {
		t.Fatal(err)
	}
	return &Server{db: db, secret: []byte("test-secret-longer-than-32-characters")}, player, admin
}
func tokenFor(t *testing.T, s *Server, u store.User) string {
	t.Helper()
	v, e := security.IssueToken(s.secret, u.ID, u.Role, time.Hour)
	if e != nil {
		t.Fatal(e)
	}
	return v
}
func TestPlayerCannotBanAndAdminCan(t *testing.T) {
	s, player, admin := testServer(t)
	target, err := s.db.CreateUser("target01", "Цель", "unused", "player")
	if err != nil {
		t.Fatal(err)
	}
	call := func(token string) int {
		req := httptest.NewRequest(http.MethodPost, "/v1/admin/users/"+target.ID+"/ban", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		w := httptest.NewRecorder()
		s.routes().ServeHTTP(w, req)
		return w.Code
	}
	if got := call(tokenFor(t, s, player)); got != 403 {
		t.Fatalf("player got %d, want 403", got)
	}
	if got := call(tokenFor(t, s, admin)); got != 200 {
		t.Fatalf("admin got %d, want 200", got)
	}
	u, _ := s.db.UserByID(target.ID)
	if !u.Banned {
		t.Fatal("target was not banned")
	}
}
func TestRegisterAlwaysCreatesPlayer(t *testing.T) {
	s, _, _ := testServer(t)
	body, _ := json.Marshal(map[string]string{"username": "newangler", "displayName": "Новый", "password": "long-enough-password"})
	req := httptest.NewRequest(http.MethodPost, "/v1/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	s.routes().ServeHTTP(w, req)
	if w.Code != 200 {
		t.Fatalf("register status %d: %s", w.Code, w.Body.String())
	}
	var got struct {
		User store.User `json:"user"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &got); err != nil {
		t.Fatal(err)
	}
	if got.User.Role != "player" {
		t.Fatalf("public registration assigned role %q", got.User.Role)
	}
}

func TestAdminConsoleEndpointsAreProtectedAndAudited(t *testing.T) {
	s, player, admin := testServer(t)
	call := func(method, path, token, body string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(method, path, bytes.NewBufferString(body))
		if token != "" {
			req.Header.Set("Authorization", "Bearer "+token)
		}
		w := httptest.NewRecorder()
		s.routes().ServeHTTP(w, req)
		return w
	}
	playerToken, adminToken := tokenFor(t, s, player), tokenFor(t, s, admin)
	if got := call("GET", "/v1/admin/users", playerToken, "").Code; got != 403 {
		t.Fatalf("player admin users status=%d", got)
	}
	if got := call("POST", "/v1/admin/users/"+player.ID+"/coins", playerToken, `{"amount":25}`).Code; got != 403 {
		t.Fatalf("player grant status=%d", got)
	}
	if got := call("POST", "/v1/admin/users/"+player.ID+"/coins", adminToken, `{"amount":25}`).Code; got != 200 {
		t.Fatalf("admin grant status=%d: %s", got, call("POST", "/v1/admin/users/"+player.ID+"/coins", adminToken, `{"amount":25}`).Body.String())
	}
	updated, _ := s.db.UserByID(player.ID)
	if updated.Coins != 125 {
		t.Fatalf("coins=%d want 125", updated.Coins)
	}
	if got := call("PUT", "/v1/admin/events", adminToken, `{"enabled":true,"title":"Весенний клёв","message":"Событие активно"}`).Code; got != 200 {
		t.Fatalf("event config status=%d", got)
	}
	if got := call("POST", "/v1/admin/announcements", adminToken, `{"title":"Новости","message":"Открыт новый сезон"}`).Code; got != 201 {
		t.Fatalf("announcement status=%d", got)
	}
	if got := call("GET", "/v1/admin/audit", adminToken, "").Code; got != 200 {
		t.Fatalf("audit status=%d", got)
	}
	if len(s.db.AuditLog(20)) < 3 {
		t.Fatalf("expected audit entries, got %d", len(s.db.AuditLog(20)))
	}
	var users struct {
		Items []store.User `json:"items"`
	}
	w := call("GET", "/v1/admin/users", adminToken, "")
	if err := json.Unmarshal(w.Body.Bytes(), &users); err != nil {
		t.Fatal(err)
	}
	for _, u := range users.Items {
		if u.PasswordHash != "" {
			t.Fatal("admin user list leaked password hash")
		}
	}
}

func TestOnlineTournamentJoinAndServerRanking(t *testing.T) {
	s, player, admin := testServer(t)
	adminToken, playerToken := tokenFor(t, s, admin), tokenFor(t, s, player)
	call := func(method, path, token, body string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(method, path, bytes.NewBufferString(body))
		if token != "" {
			req.Header.Set("Authorization", "Bearer "+token)
		}
		w := httptest.NewRecorder()
		s.routes().ServeHTTP(w, req)
		return w
	}
	if got := call("POST", "/v1/tournaments/join", playerToken, "{}").Code; got != 409 {
		t.Fatalf("join while inactive status=%d", got)
	}
	if got := call("PUT", "/v1/admin/tournaments", adminToken, `{"enabled":true,"title":"Кубок щуки","message":"Ловим трофеи"}`).Code; got != 200 {
		t.Fatalf("enable tournament status=%d", got)
	}
	joined := call("POST", "/v1/tournaments/join", playerToken, "{}")
	if joined.Code != 200 {
		t.Fatalf("join status=%d: %s", joined.Code, joined.Body.String())
	}
	var first struct {
		TournamentID string    `json:"tournamentId"`
		JoinedAt     time.Time `json:"joinedAt"`
	}
	if err := json.Unmarshal(joined.Body.Bytes(), &first); err != nil {
		t.Fatal(err)
	}
	again := call("POST", "/v1/tournaments/join", playerToken, "{}")
	var second struct {
		TournamentID string    `json:"tournamentId"`
		JoinedAt     time.Time `json:"joinedAt"`
	}
	_ = json.Unmarshal(again.Body.Bytes(), &second)
	if first.TournamentID == "" || !first.JoinedAt.Equal(second.JoinedAt) {
		t.Fatal("join should be idempotent")
	}
	_, err := s.db.AddCatch(store.Catch{ID: "tournament-catch-001", UserID: player.ID, FishID: "pike", WeightKg: 7.25, LocationID: "river", CaughtAt: time.Now().UTC()})
	if err != nil {
		t.Fatal(err)
	}
	board := call("GET", "/v1/tournaments/leaderboard", playerToken, "")
	if board.Code != 200 {
		t.Fatalf("board status=%d", board.Code)
	}
	var got struct {
		Joined bool             `json:"joined"`
		Items  []store.Standing `json:"items"`
	}
	if err := json.Unmarshal(board.Body.Bytes(), &got); err != nil {
		t.Fatal(err)
	}
	if !got.Joined || len(got.Items) != 1 || got.Items[0].BestWeightKg != 7.25 {
		t.Fatalf("unexpected tournament board: %#v", got)
	}
	if code := call("GET", "/v1/tournaments/leaderboard", "", "").Code; code != 401 {
		t.Fatalf("anonymous leaderboard status=%d", code)
	}
}
