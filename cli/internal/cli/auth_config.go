package cli

type AuthConfig struct {
	Enabled           bool   `json:"enabled"`
	AdminUsername     string `json:"admin_username"`
	AdminPasswordHash string `json:"admin_password_hash"`
	JwtSecret         string `json:"jwt_secret"`
	JwtTtlHours       int    `json:"jwt_ttl_hours"`
}
