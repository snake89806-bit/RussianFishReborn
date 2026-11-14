package main

import (
	"encoding/json"
	"errors"
	"log"
	"math"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"russianfishingreborn/server/internal/security"
	"russianfishingreborn/server/internal/store"
)

type Server struct {
	db     *store.Store
	secret []byte
}
type response map[string]any

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func fail(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, response{"error": msg})
}
func decode(w http.ResponseWriter, r *http.Request, v any) error {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	d := json.NewDecoder(r.Body)
	d.DisallowUnknownFields()
	return d.Decode(v)
}
func (s *Server) auth(r *http.Request) (store.User, error) {
	h := r.Header.Get("Authorization")
	if !strings.HasPrefix(h, "Bearer ") {
		return store.User{}, errors.New("missing bearer token")
	}
	c, e := security.VerifyToken(s.secret, strings.TrimPrefix(h, "Bearer "))
	if e != nil {
		return store.User{}, e
	}
	u, ok := s.db.UserByID(c.UserID)
	if !ok || u.Banned {
		return store.User{}, errors.New("account unavailable")
	}
	return u, nil
}
func (s *Server) admin(r *http.Request) (store.User, error) {
	u, e := s.auth(r)
	if e == nil && u.Role != "admin" {
		e = errors.New("admin role required")
	}
	return u, e
}
func (s *Server) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, response{"status": "ok", "service": "rf-reborn-api"})
	})
	mux.HandleFunc("POST /v1/register", s.register)
	mux.HandleFunc("POST /v1/login", s.login)
	mux.HandleFunc("GET /v1/me", s.me)
	mux.HandleFunc("GET /v1/leaderboard", s.leaderboard)
	mux.HandleFunc("POST /v1/tournaments/join", s.joinTournament)
	mux.HandleFunc("GET /v1/tournaments/leaderboard", s.tournamentLeaderboard)
	mux.HandleFunc("POST /v1/catches", s.catchFish)
	mux.HandleFunc("POST /v1/admin/users/{id}/ban", s.setBan)
	mux.HandleFunc("POST /v1/admin/users/{id}/unban", s.setBan)
	mux.HandleFunc("GET /v1/features", s.features)
	mux.HandleFunc("GET /v1/announcements", s.announcements)
	mux.HandleFunc("GET /v1/admin/users", s.adminUsers)
	mux.HandleFunc("GET /v1/admin/audit", s.adminAudit)
	mux.HandleFunc("POST /v1/admin/users/{id}/coins", s.grantCoins)
	mux.HandleFunc("PUT /v1/admin/events", s.setFeature)
	mux.HandleFunc("PUT /v1/admin/tournaments", s.setFeature)
	mux.HandleFunc("POST /v1/admin/announcements", s.createAnnouncement)
	return securityHeaders(mux)
}
func (s *Server) register(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Username    string `json:"username"`
		DisplayName string `json:"displayName"`
		Password    string `json:"password"`
	}
	if decode(w, r, &in) != nil {
		fail(w, 400, "invalid request")
		return
	}
	in.Username = strings.TrimSpace(in.Username)
	in.DisplayName = strings.TrimSpace(in.DisplayName)
	if !regexp.MustCompile(`^[A-Za-z0-9_]{3,20}$`).MatchString(in.Username) || len([]rune(in.DisplayName)) < 2 || len([]rune(in.DisplayName)) > 24 || len(in.Password) < 10 || len(in.Password) > 128 {
		fail(w, 400, "username 3-20 chars, display name 2-24 chars, password 10-128 chars required")
		return
	}
	hash, e := security.HashPassword(in.Password)
	if e != nil {
		fail(w, 500, "could not create account")
		return
	}
	u, e := s.db.CreateUser(in.Username, in.DisplayName, hash, "player")
	if e != nil {
		fail(w, 409, "username already exists")
		return
	}
	s.session(w, u)
}
func (s *Server) login(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if decode(w, r, &in) != nil {
		fail(w, 400, "invalid request")
		return
	}
	u, ok := s.db.UserByUsername(strings.TrimSpace(in.Username))
	if !ok || !security.VerifyPassword(in.Password, u.PasswordHash) || u.Banned {
		fail(w, 401, "invalid credentials or account unavailable")
		return
	}
	s.session(w, u)
}
func (s *Server) session(w http.ResponseWriter, u store.User) {
	token, e := security.IssueToken(s.secret, u.ID, u.Role, 72*time.Hour)
	if e != nil {
		fail(w, 500, "could not create session")
		return
	}
	writeJSON(w, 200, response{"token": token, "expiresIn": int64((72 * time.Hour).Seconds()), "user": u})
}
func (s *Server) me(w http.ResponseWriter, r *http.Request) {
	u, e := s.auth(r)
	if e != nil {
		fail(w, 401, e.Error())
		return
	}
	writeJSON(w, 200, u)
}
func (s *Server) leaderboard(w http.ResponseWriter, r *http.Request) {
	limit := 50
	if v := r.URL.Query().Get("limit"); v != "" {
		n, e := strconv.Atoi(v)
		if e != nil || n < 1 || n > 100 {
			fail(w, 400, "limit must be 1..100")
			return
		}
		limit = n
	}
	writeJSON(w, 200, response{"items": s.db.Leaderboard(limit), "period": "all_time"})
}
func (s *Server) catchFish(w http.ResponseWriter, r *http.Request) {
	u, e := s.auth(r)
	if e != nil {
		fail(w, 401, e.Error())
		return
	}
	var in struct {
		EventID    string    `json:"clientEventId"`
		FishID     string    `json:"fishId"`
		WeightKg   float64   `json:"weightKg"`
		LocationID string    `json:"locationId"`
		CaughtAt   time.Time `json:"caughtAt"`
	}
	if decode(w, r, &in) != nil {
		fail(w, 400, "invalid request")
		return
	}
	if len(in.EventID) < 8 || len(in.EventID) > 80 || len(in.FishID) < 2 || len(in.FishID) > 48 || len(in.LocationID) < 2 || len(in.LocationID) > 48 || math.IsNaN(in.WeightKg) || math.IsInf(in.WeightKg, 0) || in.WeightKg <= 0 || in.WeightKg > 100 || in.CaughtAt.IsZero() || in.CaughtAt.After(time.Now().Add(5*time.Minute)) || in.CaughtAt.Before(time.Now().Add(-30*24*time.Hour)) {
		fail(w, 400, "catch data outside allowed bounds")
		return
	}
	c := store.Catch{ID: in.EventID, UserID: u.ID, FishID: in.FishID, WeightKg: in.WeightKg, LocationID: in.LocationID, CaughtAt: in.CaughtAt.UTC()}
	added, e := s.db.AddCatch(c)
	if e != nil {
		fail(w, 500, "could not save catch")
		return
	}
	writeJSON(w, 201, response{"accepted": true, "duplicate": !added, "catchId": in.EventID})
}

func (s *Server) joinTournament(w http.ResponseWriter, r *http.Request) {
	u, err := s.auth(r)
	if err != nil {
		fail(w, 401, err.Error())
		return
	}
	tid, joinedAt, err := s.db.JoinTournament(u.ID)
	if err != nil {
		fail(w, 409, err.Error())
		return
	}
	writeJSON(w, 200, response{"joined": true, "tournamentId": tid, "joinedAt": joinedAt})
}
func (s *Server) tournamentLeaderboard(w http.ResponseWriter, r *http.Request) {
	u, err := s.auth(r)
	if err != nil {
		fail(w, 401, err.Error())
		return
	}
	tid, cfg, joinedAt, joined, items := s.db.TournamentStatus(u.ID, 50)
	writeJSON(w, 200, response{"enabled": cfg.Enabled, "title": cfg.Title, "message": cfg.Message, "tournamentId": tid, "joined": joined, "joinedAt": joinedAt, "items": items, "scoring": "best_weight_then_total_weight"})
}

func (s *Server) features(w http.ResponseWriter, r *http.Request) {
	events, tournaments := s.db.FeatureState()
	writeJSON(w, 200, response{"events": events, "tournaments": tournaments})
}
func (s *Server) announcements(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, response{"items": s.db.Announcements(30)})
}
func (s *Server) adminUsers(w http.ResponseWriter, r *http.Request) {
	if _, e := s.admin(r); e != nil {
		fail(w, 403, e.Error())
		return
	}
	writeJSON(w, 200, response{"items": s.db.Users(200)})
}
func (s *Server) adminAudit(w http.ResponseWriter, r *http.Request) {
	if _, e := s.admin(r); e != nil {
		fail(w, 403, e.Error())
		return
	}
	writeJSON(w, 200, response{"items": s.db.AuditLog(200)})
}
func (s *Server) grantCoins(w http.ResponseWriter, r *http.Request) {
	actor, e := s.admin(r)
	if e != nil {
		fail(w, 403, e.Error())
		return
	}
	var in struct {
		Amount int64 `json:"amount"`
	}
	if decode(w, r, &in) != nil || in.Amount < 1 || in.Amount > 100000 {
		fail(w, 400, "amount must be 1..100000")
		return
	}
	if e = s.db.GrantCoins(r.PathValue("id"), in.Amount, actor.ID); e != nil {
		fail(w, 400, e.Error())
		return
	}
	writeJSON(w, 200, response{"ok": true})
}
func (s *Server) setFeature(w http.ResponseWriter, r *http.Request) {
	actor, e := s.admin(r)
	if e != nil {
		fail(w, 403, e.Error())
		return
	}
	var cfg store.EventConfig
	if decode(w, r, &cfg) != nil {
		fail(w, 400, "invalid request")
		return
	}
	kind := "events"
	if strings.Contains(r.URL.Path, "tournaments") {
		kind = "tournaments"
	}
	if e = s.db.SetFeature(kind, cfg, actor.ID); e != nil {
		fail(w, 400, e.Error())
		return
	}
	writeJSON(w, 200, response{"ok": true, "enabled": cfg.Enabled})
}
func (s *Server) createAnnouncement(w http.ResponseWriter, r *http.Request) {
	actor, e := s.admin(r)
	if e != nil {
		fail(w, 403, e.Error())
		return
	}
	var in struct {
		Title   string `json:"title"`
		Message string `json:"message"`
	}
	if decode(w, r, &in) != nil {
		fail(w, 400, "invalid request")
		return
	}
	a, e := s.db.AddAnnouncement(in.Title, in.Message, actor.ID)
	if e != nil {
		fail(w, 400, e.Error())
		return
	}
	writeJSON(w, 201, a)
}
func (s *Server) setBan(w http.ResponseWriter, r *http.Request) {
	actor, e := s.admin(r)
	if e != nil {
		fail(w, 403, e.Error())
		return
	}
	if actor.ID == r.PathValue("id") {
		fail(w, 400, "cannot change own account status")
		return
	}
	ban := !strings.HasSuffix(r.URL.Path, "/unban")
	targetID := r.PathValue("id")
	if e := s.db.SetBanned(targetID, ban); e != nil {
		fail(w, 404, e.Error())
		return
	}
	action := "user.ban"
	if !ban {
		action = "user.unban"
	}
	if e := s.db.RecordAudit(actor.ID, action, targetID, ""); e != nil {
		fail(w, 500, "status changed but audit write failed")
		return
	}
	writeJSON(w, 200, response{"ok": true, "banned": ban})
}
func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(w, r)
	})
}
func main() {
	addr := env("LISTEN_ADDR", ":8080")
	path := env("DATA_FILE", "./data/game.json")
	secret := os.Getenv("JWT_SECRET")
	if len(secret) < 32 {
		log.Fatal("JWT_SECRET must contain at least 32 characters")
	}
	db, e := store.New(path)
	if e != nil {
		log.Fatal(e)
	}
	// Bootstrap exactly one owner/admin from deployment secrets. Never expose role assignment in public registration.
	adminName, adminPass := os.Getenv("ADMIN_USERNAME"), os.Getenv("ADMIN_PASSWORD")
	if adminName != "" && adminPass != "" {
		if existing, ok := db.UserByUsername(adminName); !ok {
			hash, err := security.HashPassword(adminPass)
			if err != nil {
				log.Fatal(err)
			}
			if _, err = db.CreateUser(adminName, "Владелец", hash, "admin"); err != nil {
				log.Fatal(err)
			}
			log.Printf("bootstrapped admin account %q", adminName)
		} else if existing.Role != "admin" {
			log.Fatalf("ADMIN_USERNAME %q already belongs to a non-admin account; choose a new owner username", adminName)
		}
	}
	srv := &Server{db: db, secret: []byte(secret)}
	httpServer := &http.Server{Addr: addr, Handler: srv.routes(), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 15 * time.Second, IdleTimeout: 60 * time.Second}
	log.Printf("API listening on %s (put behind HTTPS reverse proxy)", addr)
	if e = httpServer.ListenAndServe(); e != nil && e != http.ErrServerClosed {
		log.Fatal(e)
	}
}
func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}
