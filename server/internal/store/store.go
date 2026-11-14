package store

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

type User struct {
	ID           string    `json:"id"`
	Username     string    `json:"username"`
	DisplayName  string    `json:"displayName"`
	PasswordHash string    `json:"-"`
	Role         string    `json:"role"`
	Coins        int64     `json:"coins"`
	Banned       bool      `json:"banned"`
	CreatedAt    time.Time `json:"createdAt"`
}
type Catch struct {
	ID         string    `json:"id"`
	UserID     string    `json:"userId"`
	FishID     string    `json:"fishId"`
	WeightKg   float64   `json:"weightKg"`
	LocationID string    `json:"locationId"`
	CaughtAt   time.Time `json:"caughtAt"`
	CreatedAt  time.Time `json:"createdAt"`
}
type EventConfig struct {
	Enabled bool      `json:"enabled"`
	Title   string    `json:"title"`
	Message string    `json:"message"`
	Updated time.Time `json:"updatedAt"`
}
type Announcement struct {
	ID        string    `json:"id"`
	Title     string    `json:"title"`
	Message   string    `json:"message"`
	CreatedAt time.Time `json:"createdAt"`
}
type AuditEntry struct {
	ID        string    `json:"id"`
	ActorID   string    `json:"actorId"`
	Action    string    `json:"action"`
	TargetID  string    `json:"targetId,omitempty"`
	Details   string    `json:"details,omitempty"`
	CreatedAt time.Time `json:"createdAt"`
}
type Disk struct {
	Users           map[string]User                 `json:"users"`
	Catches         []Catch                         `json:"catches"`
	EventIDs        map[string]bool                 `json:"eventIds"`
	Events          EventConfig                     `json:"events"`
	Tournaments     EventConfig                     `json:"tournaments"`
	Announcements   []Announcement                  `json:"announcements"`
	TournamentJoins map[string]map[string]time.Time `json:"tournamentJoins"`
	Audit           []AuditEntry                    `json:"audit"`
}
type Store struct {
	mu   sync.RWMutex
	path string
	data Disk
}

func New(path string) (*Store, error) {
	s := &Store{path: path, data: Disk{Users: map[string]User{}, EventIDs: map[string]bool{}, TournamentJoins: map[string]map[string]time.Time{}}}
	raw, err := os.ReadFile(path)
	if err == nil {
		if err := json.Unmarshal(raw, &s.data); err != nil {
			return nil, err
		}
		if s.data.Users == nil {
			s.data.Users = map[string]User{}
		}
		if s.data.EventIDs == nil {
			s.data.EventIDs = map[string]bool{}
		}
		if s.data.TournamentJoins == nil {
			s.data.TournamentJoins = map[string]map[string]time.Time{}
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	return s, nil
}
func id() string { b := make([]byte, 16); _, _ = rand.Read(b); return hex.EncodeToString(b) }
func (s *Store) saveLocked() error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0700); err != nil {
		return err
	}
	raw, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err = os.WriteFile(tmp, raw, 0600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}
func (s *Store) CreateUser(username, display, hash, role string) (User, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	key := strings.ToLower(username)
	for _, u := range s.data.Users {
		if strings.EqualFold(u.Username, key) {
			return User{}, errors.New("username already exists")
		}
	}
	u := User{ID: id(), Username: username, DisplayName: display, PasswordHash: hash, Role: role, Coins: 100, CreatedAt: time.Now().UTC()}
	s.data.Users[u.ID] = u
	return u, s.saveLocked()
}
func (s *Store) UserByUsername(username string) (User, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	for _, u := range s.data.Users {
		if strings.EqualFold(u.Username, username) {
			return u, true
		}
	}
	return User{}, false
}
func (s *Store) UserByID(userID string) (User, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	u, ok := s.data.Users[userID]
	return u, ok
}
func (s *Store) SetBanned(userID string, banned bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.data.Users[userID]
	if !ok {
		return errors.New("user not found")
	}
	if u.Role == "admin" {
		return errors.New("administrator accounts cannot be banned by this endpoint")
	}
	u.Banned = banned
	s.data.Users[userID] = u
	return s.saveLocked()
}
func (s *Store) Users(limit int) []User {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]User, 0, len(s.data.Users))
	for _, u := range s.data.Users {
		u.PasswordHash = ""
		out = append(out, u)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt.After(out[j].CreatedAt) })
	if limit > 0 && len(out) > limit {
		out = out[:limit]
	}
	return out
}
func (s *Store) GrantCoins(userID string, amount int64, actorID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.data.Users[userID]
	if !ok {
		return errors.New("user not found")
	}
	if amount < 1 || amount > 100000 {
		return errors.New("amount outside allowed bounds")
	}
	if u.Coins > 1000000000-amount {
		return errors.New("balance limit exceeded")
	}
	u.Coins += amount
	s.data.Users[userID] = u
	s.data.Audit = append(s.data.Audit, AuditEntry{ID: id(), ActorID: actorID, Action: "coins.grant", TargetID: userID, Details: "amount=" + strconv.FormatInt(amount, 10), CreatedAt: time.Now().UTC()})
	return s.saveLocked()
}
func (s *Store) SetFeature(kind string, cfg EventConfig, actorID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	cfg.Title = strings.TrimSpace(cfg.Title)
	cfg.Message = strings.TrimSpace(cfg.Message)
	cfg.Updated = time.Now().UTC()
	if len([]rune(cfg.Title)) > 80 || len([]rune(cfg.Message)) > 500 {
		return errors.New("title or message too long")
	}
	switch kind {
	case "events":
		s.data.Events = cfg
	case "tournaments":
		s.data.Tournaments = cfg
	default:
		return errors.New("unknown feature")
	}
	s.data.Audit = append(s.data.Audit, AuditEntry{ID: id(), ActorID: actorID, Action: kind + ".update", Details: cfg.Title, CreatedAt: time.Now().UTC()})
	return s.saveLocked()
}
func (s *Store) FeatureState() (EventConfig, EventConfig) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.data.Events, s.data.Tournaments
}
func (s *Store) AddAnnouncement(title, message, actorID string) (Announcement, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	title = strings.TrimSpace(title)
	message = strings.TrimSpace(message)
	if len([]rune(title)) < 2 || len([]rune(title)) > 80 || len([]rune(message)) < 2 || len([]rune(message)) > 500 {
		return Announcement{}, errors.New("title and message must be 2..80 and 2..500 characters")
	}
	a := Announcement{ID: id(), Title: title, Message: message, CreatedAt: time.Now().UTC()}
	s.data.Announcements = append([]Announcement{a}, s.data.Announcements...)
	if len(s.data.Announcements) > 100 {
		s.data.Announcements = s.data.Announcements[:100]
	}
	s.data.Audit = append(s.data.Audit, AuditEntry{ID: id(), ActorID: actorID, Action: "announcement.create", TargetID: a.ID, Details: title, CreatedAt: time.Now().UTC()})
	return a, s.saveLocked()
}
func (s *Store) Announcements(limit int) []Announcement {
	s.mu.RLock()
	defer s.mu.RUnlock()
	n := len(s.data.Announcements)
	if limit > 0 && n > limit {
		n = limit
	}
	return append([]Announcement(nil), s.data.Announcements[:n]...)
}
func (s *Store) AuditLog(limit int) []AuditEntry {
	s.mu.RLock()
	defer s.mu.RUnlock()
	n := len(s.data.Audit)
	if limit > 0 && n > limit {
		n = limit
	}
	out := make([]AuditEntry, n)
	for i := 0; i < n; i++ {
		out[i] = s.data.Audit[len(s.data.Audit)-1-i]
	}
	return out
}
func (s *Store) RecordAudit(actorID, action, targetID, details string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data.Audit = append(s.data.Audit, AuditEntry{ID: id(), ActorID: actorID, Action: action, TargetID: targetID, Details: details, CreatedAt: time.Now().UTC()})
	return s.saveLocked()
}

func (s *Store) AddCatch(c Catch) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.data.EventIDs[c.ID] {
		return false, nil
	}
	if _, ok := s.data.Users[c.UserID]; !ok {
		return false, errors.New("user not found")
	}
	c.CreatedAt = time.Now().UTC()
	s.data.Catches = append(s.data.Catches, c)
	s.data.EventIDs[c.ID] = true
	if err := s.saveLocked(); err != nil {
		return false, err
	}
	return true, nil
}

type Standing struct {
	Rank          int     `json:"rank"`
	UserID        string  `json:"userId"`
	DisplayName   string  `json:"displayName"`
	BestWeightKg  float64 `json:"bestWeightKg"`
	TotalWeightKg float64 `json:"totalWeightKg"`
	Catches       int     `json:"catches"`
}

func (s *Store) Leaderboard(limit int) []Standing {
	s.mu.RLock()
	defer s.mu.RUnlock()
	m := map[string]*Standing{}
	for _, c := range s.data.Catches {
		u, ok := s.data.Users[c.UserID]
		if !ok || u.Banned {
			continue
		}
		x := m[c.UserID]
		if x == nil {
			x = &Standing{UserID: c.UserID, DisplayName: u.DisplayName}
			m[c.UserID] = x
		}
		x.Catches++
		x.TotalWeightKg += c.WeightKg
		if c.WeightKg > x.BestWeightKg {
			x.BestWeightKg = c.WeightKg
		}
	}
	out := make([]Standing, 0, len(m))
	for _, v := range m {
		out = append(out, *v)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].BestWeightKg == out[j].BestWeightKg {
			return out[i].TotalWeightKg > out[j].TotalWeightKg
		}
		return out[i].BestWeightKg > out[j].BestWeightKg
	})
	if limit > len(out) {
		limit = len(out)
	}
	if limit < 0 {
		limit = 0
	}
	out = out[:limit]
	for i := range out {
		out[i].Rank = i + 1
	}
	return out
}

// TournamentID changes whenever an administrator updates the tournament configuration.
func (s *Store) TournamentID() (string, EventConfig) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	cfg := s.data.Tournaments
	if cfg.Updated.IsZero() {
		return "", cfg
	}
	return cfg.Updated.UTC().Format(time.RFC3339Nano), cfg
}

func (s *Store) JoinTournament(userID string) (string, time.Time, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	cfg := s.data.Tournaments
	if !cfg.Enabled || cfg.Updated.IsZero() {
		return "", time.Time{}, errors.New("tournament is not active")
	}
	u, ok := s.data.Users[userID]
	if !ok || u.Banned {
		return "", time.Time{}, errors.New("account unavailable")
	}
	tid := cfg.Updated.UTC().Format(time.RFC3339Nano)
	if s.data.TournamentJoins == nil {
		s.data.TournamentJoins = map[string]map[string]time.Time{}
	}
	if s.data.TournamentJoins[tid] == nil {
		s.data.TournamentJoins[tid] = map[string]time.Time{}
	}
	joined, exists := s.data.TournamentJoins[tid][userID]
	if !exists {
		joined = time.Now().UTC()
		s.data.TournamentJoins[tid][userID] = joined
		if err := s.saveLocked(); err != nil {
			return "", time.Time{}, err
		}
	}
	return tid, joined, nil
}

func (s *Store) TournamentStatus(userID string, limit int) (string, EventConfig, time.Time, bool, []Standing) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	cfg := s.data.Tournaments
	if cfg.Updated.IsZero() {
		return "", cfg, time.Time{}, false, nil
	}
	tid := cfg.Updated.UTC().Format(time.RFC3339Nano)
	joinedAt, joined := s.data.TournamentJoins[tid][userID]
	if !cfg.Enabled {
		joined = false
	}
	totals := map[string]*Standing{}
	for _, c := range s.data.Catches {
		j, ok := s.data.TournamentJoins[tid][c.UserID]
		if !ok || c.CaughtAt.Before(cfg.Updated) || c.CaughtAt.Before(j) {
			continue
		}
		u, ok := s.data.Users[c.UserID]
		if !ok || u.Banned {
			continue
		}
		v := totals[c.UserID]
		if v == nil {
			v = &Standing{UserID: c.UserID, DisplayName: u.DisplayName}
			totals[c.UserID] = v
		}
		v.Catches++
		v.TotalWeightKg += c.WeightKg
		if c.WeightKg > v.BestWeightKg {
			v.BestWeightKg = c.WeightKg
		}
	}
	// Include joined players with zero catches, not just users who have submitted catches.
	if cfg.Enabled {
		for uid := range s.data.TournamentJoins[tid] {
			if u, ok := s.data.Users[uid]; ok && !u.Banned && totals[uid] == nil {
				totals[uid] = &Standing{UserID: uid, DisplayName: u.DisplayName}
			}
		}
	}
	out := make([]Standing, 0, len(totals))
	for _, v := range totals {
		out = append(out, *v)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].BestWeightKg == out[j].BestWeightKg {
			if out[i].TotalWeightKg == out[j].TotalWeightKg {
				return out[i].DisplayName < out[j].DisplayName
			}
			return out[i].TotalWeightKg > out[j].TotalWeightKg
		}
		return out[i].BestWeightKg > out[j].BestWeightKg
	})
	if limit < 1 {
		limit = 50
	}
	if limit > len(out) {
		limit = len(out)
	}
	out = out[:limit]
	for i := range out {
		out[i].Rank = i + 1
	}
	return tid, cfg, joinedAt, joined, out
}
