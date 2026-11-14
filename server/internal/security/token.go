package security

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
	"time"
)

type Claims struct {
	UserID  string `json:"sub"`
	Role    string `json:"role"`
	Expires int64  `json:"exp"`
}

var b64 = base64.RawURLEncoding

func IssueToken(secret []byte, userID, role string, ttl time.Duration) (string, error) {
	header, _ := json.Marshal(map[string]string{"alg": "HS256", "typ": "JWT"})
	payload, err := json.Marshal(Claims{UserID: userID, Role: role, Expires: time.Now().Add(ttl).Unix()})
	if err != nil {
		return "", err
	}
	unsigned := b64.EncodeToString(header) + "." + b64.EncodeToString(payload)
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(unsigned))
	return unsigned + "." + b64.EncodeToString(mac.Sum(nil)), nil
}

func VerifyToken(secret []byte, token string) (Claims, error) {
	var claims Claims
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return claims, errors.New("invalid token")
	}
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(parts[0] + "." + parts[1]))
	sig, err := b64.DecodeString(parts[2])
	if err != nil || !hmac.Equal(sig, mac.Sum(nil)) {
		return claims, errors.New("invalid signature")
	}
	raw, err := b64.DecodeString(parts[1])
	if err != nil {
		return claims, errors.New("invalid payload")
	}
	if err := json.Unmarshal(raw, &claims); err != nil {
		return claims, err
	}
	if claims.UserID == "" || claims.Expires < time.Now().Unix() {
		return claims, errors.New("expired token")
	}
	return claims, nil
}
