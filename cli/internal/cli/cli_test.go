package cli

import (
	"path/filepath"
	"testing"
)

func TestMajorChanged(t *testing.T) {
	tests := []struct {
		from    string
		to      string
		changed bool
	}{
		{"v1.2.3", "v1.5.0", false},
		{"1.2.3", "v2.0.0", true},
		{"latest", "v2.0.0", false},
		{"v2", "v3.1.0", true},
	}

	for _, tt := range tests {
		if got := majorChanged(tt.from, tt.to); got != tt.changed {
			t.Fatalf("majorChanged(%q, %q) = %v, want %v", tt.from, tt.to, got, tt.changed)
		}
	}
}

func TestSetPort(t *testing.T) {
	var p int
	if err := setPort(&p, "8080"); err != nil {
		t.Fatalf("setPort failed: %v", err)
	}
	if p != 8080 {
		t.Fatalf("port mismatch: got %d", p)
	}
	if err := setPort(&p, "70000"); err == nil {
		t.Fatal("expected out-of-range error")
	}
}

func TestConfigDefaults(t *testing.T) {
	paths := Paths{Root: filepath.Join("/tmp", "loganalysis-test")}
	cfg := defaultConfig(paths)
	if cfg.DefaultProfile != profileFull {
		t.Fatalf("default profile mismatch: %s", cfg.DefaultProfile)
	}
	if cfg.Ports.Backend != 8080 || cfg.Ports.Frontend != 3000 {
		t.Fatalf("default ports mismatch: %+v", cfg.Ports)
	}
}

func TestAutoResolveProfilePortsMinimal(t *testing.T) {
	ports := PortsConfig{
		Frontend:           3000,
		Backend:            8080,
		Postgres:           5432,
		Redis:              5432,
		RabbitMQ:           5672,
		RabbitMQManagement: 15672,
	}
	busy := map[int]bool{
		3000: true,
		3001: true,
		8080: true,
		5432: true,
		5672: true,
	}
	available := func(port int) bool {
		return !busy[port]
	}

	resolved, changes, err := autoResolveProfilePorts(profileMinimal, ports, available)
	if err != nil {
		t.Fatalf("autoResolveProfilePorts failed: %v", err)
	}

	if resolved.Frontend != 3002 {
		t.Fatalf("frontend expected 3002, got %d", resolved.Frontend)
	}
	if resolved.Backend != 8081 {
		t.Fatalf("backend expected 8081, got %d", resolved.Backend)
	}
	if resolved.Postgres != 5433 {
		t.Fatalf("postgres expected 5433, got %d", resolved.Postgres)
	}
	if resolved.Redis != 5434 {
		t.Fatalf("redis expected 5434, got %d", resolved.Redis)
	}
	if resolved.RabbitMQ != 5673 {
		t.Fatalf("rabbitmq expected 5673, got %d", resolved.RabbitMQ)
	}
	if resolved.RabbitMQManagement != 15672 {
		t.Fatalf("rabbitmq_management expected 15672, got %d", resolved.RabbitMQManagement)
	}
	if len(changes) != 5 {
		t.Fatalf("expected 5 remap changes, got %d (%v)", len(changes), changes)
	}
}

func TestAutoResolveProfilePortsDBOnlyTouchesDBPorts(t *testing.T) {
	ports := PortsConfig{
		Frontend: 3000,
		Backend:  8080,
		Postgres: 5432,
		Redis:    6379,
	}
	busy := map[int]bool{
		3000: true,
		8080: true,
		5432: true,
	}
	available := func(port int) bool {
		return !busy[port]
	}

	resolved, _, err := autoResolveProfilePorts(profileDB, ports, available)
	if err != nil {
		t.Fatalf("autoResolveProfilePorts failed: %v", err)
	}

	if resolved.Frontend != 3000 || resolved.Backend != 8080 {
		t.Fatalf("db profile should not mutate frontend/backend ports: %+v", resolved)
	}
	if resolved.Postgres != 5433 {
		t.Fatalf("postgres expected 5433, got %d", resolved.Postgres)
	}
	if resolved.Redis != 6379 {
		t.Fatalf("redis expected 6379, got %d", resolved.Redis)
	}
}

func TestAutoResolveProfilePortsInvalidProfile(t *testing.T) {
	_, _, err := autoResolveProfilePorts("unknown", PortsConfig{}, func(int) bool { return true })
	if err == nil {
		t.Fatal("expected error for invalid profile")
	}
}
