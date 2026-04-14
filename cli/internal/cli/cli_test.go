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

func TestAutoResolveProfilePortsFull(t *testing.T) {
	ports := PortsConfig{
		Frontend:               3000,
		Backend:                8080,
		Postgres:               5432,
		Redis:                  5432,
		RabbitMQ:               5672,
		RabbitMQManagement:     15672,
		Elasticsearch:          9200,
		ElasticsearchTransport: 9300,
		Kibana:                 5601,
		MinioAPI:               9000,
		MinioConsole:           9001,
	}
	busy := map[int]bool{
		3000: true,
		3001: true,
		8080: true,
		5432: true,
		5672: true,
		9200: true,
		9300: true,
		5601: true,
	}
	available := func(port int) bool {
		return !busy[port]
	}

	resolved, changes, err := autoResolveProfilePorts(profileFull, ports, available)
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
	if resolved.Elasticsearch != 9201 {
		t.Fatalf("elasticsearch expected 9201, got %d", resolved.Elasticsearch)
	}
	if resolved.ElasticsearchTransport != 9301 {
		t.Fatalf("elasticsearch_transport expected 9301, got %d", resolved.ElasticsearchTransport)
	}
	if resolved.Kibana != 5602 {
		t.Fatalf("kibana expected 5602, got %d", resolved.Kibana)
	}
	if len(changes) != 8 {
		t.Fatalf("expected 8 remap changes, got %d (%v)", len(changes), changes)
	}
}

func TestAutoResolveProfilePortsInvalidProfile(t *testing.T) {
	_, _, err := autoResolveProfilePorts("unknown", PortsConfig{}, func(int) bool { return true })
	if err == nil {
		t.Fatal("expected error for invalid profile")
	}
}
