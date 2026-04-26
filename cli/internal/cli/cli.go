package cli

import (
	"bufio"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"
)

const (
	profileFull = "full"
)

var semverMajorRe = regexp.MustCompile(`^v?(\d+)(?:\.|$)`)

type BuildInfo struct {
	Version string
	Commit  string
	Date    string
}

type Paths struct {
	Root          string
	RuntimeDir    string
	ConfigPath    string
	StatePath     string
	ActiveCompose string
	BackupCompose string
}

type PortsConfig struct {
	Frontend               int `json:"frontend"`
	Backend                int `json:"backend"`
	Postgres               int `json:"postgres"`
	Redis                  int `json:"redis"`
	RabbitMQ               int `json:"rabbitmq"`
	RabbitMQManagement     int `json:"rabbitmq_management"`
	Elasticsearch          int `json:"elasticsearch"`
	ElasticsearchTransport int `json:"elasticsearch_transport"`
	Kibana                 int `json:"kibana"`
	MinioAPI               int `json:"minio_api"`
	MinioConsole           int `json:"minio_console"`
}

type Config struct {
	ProjectName    string      `json:"project_name"`
	DefaultProfile string      `json:"default_profile"`
	DefaultVersion string      `json:"default_version"`
	ImageRegistry  string      `json:"image_registry"`
	BackendImage   string      `json:"backend_image"`
	FrontendImage  string      `json:"frontend_image"`
	ReleaseRepo    string      `json:"release_repo"`
	DataDir        string      `json:"data_dir"`
	Ports          PortsConfig `json:"ports"`
	Auth           AuthConfig  `json:"auth"`
}

type State struct {
	CurrentProfile  string `json:"current_profile"`
	CurrentVersion  string `json:"current_version"`
	PreviousVersion string `json:"previous_version"`
	ComposeFile     string `json:"compose_file"`
	UpdatedAt       string `json:"updated_at"`
}

type runtimeApp struct {
	build                  BuildInfo
	paths                  Paths
	cfg                    Config
	state                  State
	stdout                 io.Writer
	stderr                 io.Writer
	migratedDefaultProfile bool
	migratedStateProfile   bool
}

func Execute(args []string, build BuildInfo, stdout, stderr io.Writer) int {
	app, err := newRuntime(build, stdout, stderr)
	if err != nil {
		fmt.Fprintf(stderr, "init failed: %v\n", err)
		return 1
	}

	if len(args) == 0 {
		app.printHelp()
		return 0
	}

	cmd := strings.ToLower(args[0])
	subArgs := args[1:]

	switch cmd {
	case "help", "-h", "--help":
		app.printHelp()
		return 0
	case "up":
		return app.cmdUp(subArgs)
	case "down":
		return app.cmdDown(subArgs)
	case "status":
		return app.cmdStatus(subArgs)
	case "logs":
		return app.cmdLogs(subArgs)
	case "doctor":
		return app.cmdDoctor(subArgs)
	case "config":
		return app.cmdConfig(subArgs)
	case "auth":
		return app.cmdAuth(subArgs)
	case "upgrade":
		return app.cmdUpgrade(subArgs)
	case "uninstall":
		return app.cmdUninstall(subArgs)
	case "version":
		app.printVersion()
		return 0
	default:
		fmt.Fprintf(app.stderr, "unknown command: %s\n\n", cmd)
		app.printHelp()
		return 2
	}
}

func newRuntime(build BuildInfo, stdout, stderr io.Writer) (*runtimeApp, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return nil, fmt.Errorf("resolve home dir: %w", err)
	}

	root := filepath.Join(home, ".loganalysis")
	runtimeDir := filepath.Join(root, "runtime")
	paths := Paths{
		Root:          root,
		RuntimeDir:    runtimeDir,
		ConfigPath:    filepath.Join(root, "config.json"),
		StatePath:     filepath.Join(root, "state.json"),
		ActiveCompose: filepath.Join(runtimeDir, "compose.yaml"),
		BackupCompose: filepath.Join(runtimeDir, "compose.backup.yaml"),
	}

	if err := os.MkdirAll(runtimeDir, 0o755); err != nil {
		return nil, fmt.Errorf("create runtime dir: %w", err)
	}

	cfg, migratedDefaultProfile, err := loadConfig(paths)
	if err != nil {
		return nil, err
	}
	state, err := loadState(paths.StatePath)
	if err != nil {
		return nil, err
	}
	migratedStateProfile := false
	if state.CurrentProfile != "" && state.CurrentProfile != profileFull {
		state.CurrentProfile = profileFull
		migratedStateProfile = true
	}

	if err := os.MkdirAll(cfg.DataDir, 0o755); err != nil {
		return nil, fmt.Errorf("create data dir: %w", err)
	}

	app := &runtimeApp{
		build:                  build,
		paths:                  paths,
		cfg:                    cfg,
		state:                  state,
		stdout:                 stdout,
		stderr:                 stderr,
		migratedDefaultProfile: migratedDefaultProfile,
		migratedStateProfile:   migratedStateProfile,
	}
	if migratedStateProfile {
		if err := app.saveState(); err != nil {
			return nil, err
		}
	}
	if migratedDefaultProfile || migratedStateProfile {
		fmt.Fprintln(stdout, "migrated legacy profile setting to full")
	}
	return app, nil
}

func defaultConfig(paths Paths) Config {
	return Config{
		ProjectName:    "loganalysis",
		DefaultProfile: profileFull,
		DefaultVersion: "latest",
		ImageRegistry:  "docker.io/cityseason",
		ReleaseRepo:    "3362345814/loganalysis-monorepo",
		DataDir:        filepath.Join(paths.Root, "data"),
		Auth: AuthConfig{
			Enabled:     true,
			JwtTtlHours: 24,
		},
		Ports: PortsConfig{
			Frontend:               3000,
			Backend:                8080,
			Postgres:               5432,
			Redis:                  6379,
			RabbitMQ:               5672,
			RabbitMQManagement:     15672,
			Elasticsearch:          9200,
			ElasticsearchTransport: 9300,
			Kibana:                 5601,
			MinioAPI:               9000,
			MinioConsole:           9001,
		},
	}
}

func (c *Config) applyDefaults(def Config) {
	if c.ProjectName == "" {
		c.ProjectName = def.ProjectName
	}
	if c.DefaultProfile == "" {
		c.DefaultProfile = def.DefaultProfile
	}
	if c.DefaultProfile != profileFull {
		c.DefaultProfile = profileFull
	}
	if c.DefaultVersion == "" {
		c.DefaultVersion = def.DefaultVersion
	}
	if c.ImageRegistry == "" {
		c.ImageRegistry = def.ImageRegistry
	} else {
		c.ImageRegistry = migrateImageRegistry(c.ImageRegistry)
	}
	if c.ReleaseRepo == "" {
		c.ReleaseRepo = def.ReleaseRepo
	}
	if c.DataDir == "" {
		c.DataDir = def.DataDir
	}
	if c.Ports.Frontend == 0 {
		c.Ports.Frontend = def.Ports.Frontend
	}
	if c.Ports.Backend == 0 {
		c.Ports.Backend = def.Ports.Backend
	}
	if c.Ports.Postgres == 0 {
		c.Ports.Postgres = def.Ports.Postgres
	}
	if c.Ports.Redis == 0 {
		c.Ports.Redis = def.Ports.Redis
	}
	if c.Ports.RabbitMQ == 0 {
		c.Ports.RabbitMQ = def.Ports.RabbitMQ
	}
	if c.Ports.RabbitMQManagement == 0 {
		c.Ports.RabbitMQManagement = def.Ports.RabbitMQManagement
	}
	if c.Ports.Elasticsearch == 0 {
		c.Ports.Elasticsearch = def.Ports.Elasticsearch
	}
	if c.Ports.ElasticsearchTransport == 0 {
		c.Ports.ElasticsearchTransport = def.Ports.ElasticsearchTransport
	}
	if c.Ports.Kibana == 0 {
		c.Ports.Kibana = def.Ports.Kibana
	}
	if c.Ports.MinioAPI == 0 {
		c.Ports.MinioAPI = def.Ports.MinioAPI
	}
	if c.Ports.MinioConsole == 0 {
		c.Ports.MinioConsole = def.Ports.MinioConsole
	}
	if !c.Auth.Enabled && c.Auth.AdminUsername == "" && c.Auth.AdminPasswordHash == "" && c.Auth.JwtSecret == "" {
		c.Auth.Enabled = def.Auth.Enabled
	}
	if c.Auth.JwtTtlHours <= 0 {
		c.Auth.JwtTtlHours = def.Auth.JwtTtlHours
	}
}

func loadConfig(paths Paths) (Config, bool, error) {
	def := defaultConfig(paths)

	buf, err := os.ReadFile(paths.ConfigPath)
	if errors.Is(err, os.ErrNotExist) {
		if err := saveJSON(paths.ConfigPath, def); err != nil {
			return Config{}, false, err
		}
		return def, false, nil
	}
	if err != nil {
		return Config{}, false, fmt.Errorf("read config: %w", err)
	}

	var cfg Config
	if err := json.Unmarshal(buf, &cfg); err != nil {
		return Config{}, false, fmt.Errorf("parse config: %w", err)
	}
	originalProfile := cfg.DefaultProfile

	cfg.applyDefaults(def)
	if err := saveJSON(paths.ConfigPath, cfg); err != nil {
		return Config{}, false, err
	}
	migratedDefaultProfile := originalProfile != "" && originalProfile != profileFull
	return cfg, migratedDefaultProfile, nil
}

func loadState(path string) (State, error) {
	buf, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return State{}, nil
	}
	if err != nil {
		return State{}, fmt.Errorf("read state: %w", err)
	}
	var st State
	if err := json.Unmarshal(buf, &st); err != nil {
		return State{}, fmt.Errorf("parse state: %w", err)
	}
	return st, nil
}

func saveJSON(path string, v any) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return fmt.Errorf("prepare path %s: %w", path, err)
	}
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal %s: %w", path, err)
	}
	if err := os.WriteFile(path, append(data, '\n'), 0o644); err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	return nil
}

func (a *runtimeApp) saveConfig() error {
	return saveJSON(a.paths.ConfigPath, a.cfg)
}

func (a *runtimeApp) saveState() error {
	return saveJSON(a.paths.StatePath, a.state)
}

func (a *runtimeApp) printHelp() {
	fmt.Fprintln(a.stdout, "loganalysis - one-click stack manager")
	fmt.Fprintln(a.stdout, "")
	fmt.Fprintln(a.stdout, "Usage:")
	fmt.Fprintln(a.stdout, "  loganalysis up [--version vX.Y.Z] [--auto-port] [--no-auto-port]")
	fmt.Fprintln(a.stdout, "  loganalysis down [--remove-volumes]")
	fmt.Fprintln(a.stdout, "  loganalysis status")
	fmt.Fprintln(a.stdout, "  loganalysis logs [service] [-f] [--tail N]")
	fmt.Fprintln(a.stdout, "  loganalysis doctor")
	fmt.Fprintln(a.stdout, "  loganalysis config set <key> <value>")
	fmt.Fprintln(a.stdout, "  loganalysis config get <key>")
	fmt.Fprintln(a.stdout, "  loganalysis config list")
	fmt.Fprintln(a.stdout, "  loganalysis auth set-admin --username <name>")
	fmt.Fprintln(a.stdout, "  loganalysis auth passwd")
	fmt.Fprintln(a.stdout, "  loganalysis auth show")
	fmt.Fprintln(a.stdout, "  loganalysis upgrade [--to vX.Y.Z] [--allow-major] [--force] [--auto-port] [--no-auto-port]")
	fmt.Fprintln(a.stdout, "  loganalysis uninstall [--purge-data] [--keep-cli]")
	fmt.Fprintln(a.stdout, "  loganalysis version")
}

func (a *runtimeApp) printVersion() {
	fmt.Fprintf(a.stdout, "version: %s\n", a.build.Version)
	fmt.Fprintf(a.stdout, "commit:  %s\n", a.build.Commit)
	fmt.Fprintf(a.stdout, "date:    %s\n", a.build.Date)
}

func validateProfile(profile string) error {
	if profile == profileFull {
		return nil
	}
	return fmt.Errorf("invalid profile %q, expected only: %s", profile, profileFull)
}

func (a *runtimeApp) cmdUp(args []string) int {
	fs := flag.NewFlagSet("up", flag.ContinueOnError)
	fs.SetOutput(a.stderr)
	version := fs.String("version", a.defaultUpVersion(), "image version tag")
	autoPort := fs.Bool("auto-port", true, "auto-resolve host port conflicts")
	noAutoPort := fs.Bool("no-auto-port", false, "disable auto-resolve host port conflicts")

	if err := fs.Parse(args); err != nil {
		return 2
	}

	if err := a.ensureDockerReady(); err != nil {
		fmt.Fprintf(a.stderr, "docker precheck failed: %v\n", err)
		return 1
	}
	if err := a.ensureAuthConfigForUp(); err != nil {
		fmt.Fprintln(a.stderr, err)
		return 1
	}
	enableAutoPort := *autoPort && !*noAutoPort

	portsToUse := a.cfg.Ports
	var portChanges []string
	if enableAutoPort {
		resolved, changes, err := autoResolveProfilePorts(profileFull, a.cfg.Ports, isPortAvailable)
		if err != nil {
			fmt.Fprintf(a.stderr, "auto-port failed: %v\n", err)
			return 1
		}
		portsToUse = resolved
		portChanges = changes
		if len(portChanges) > 0 {
			fmt.Fprintln(a.stdout, "auto-port remapped host ports:")
			for _, item := range portChanges {
				fmt.Fprintf(a.stdout, "- %s\n", item)
			}
		}
	}

	if err := a.applyStack(profileFull, *version, portsToUse); err != nil {
		fmt.Fprintf(a.stderr, "failed to start stack: %v\n", err)
		return 1
	}

	if enableAutoPort && len(portChanges) > 0 {
		a.cfg.Ports = portsToUse
		if err := a.saveConfig(); err != nil {
			fmt.Fprintf(a.stderr, "warning: failed to persist remapped ports: %v\n", err)
		}
	}

	a.state.PreviousVersion = a.state.CurrentVersion
	a.state.CurrentProfile = profileFull
	a.state.CurrentVersion = *version
	a.state.ComposeFile = a.paths.ActiveCompose
	a.state.UpdatedAt = time.Now().Format(time.RFC3339)
	if err := a.saveState(); err != nil {
		fmt.Fprintf(a.stderr, "warning: failed to save state: %v\n", err)
	}

	fmt.Fprintf(a.stdout, "stack started with profile=%s version=%s\n", profileFull, *version)
	return 0
}

func (a *runtimeApp) defaultUpVersion() string {
	configured := strings.TrimSpace(a.cfg.DefaultVersion)
	if configured != "" && configured != "latest" {
		return configured
	}

	current := strings.TrimSpace(a.build.Version)
	if current != "" && current != "dev" {
		return current
	}

	if configured != "" {
		return configured
	}
	return "latest"
}

func (a *runtimeApp) cmdDown(args []string) int {
	fs := flag.NewFlagSet("down", flag.ContinueOnError)
	fs.SetOutput(a.stderr)
	removeVolumes := fs.Bool("remove-volumes", false, "remove docker volumes")
	if err := fs.Parse(args); err != nil {
		return 2
	}

	composeFile := a.resolveComposeFile()
	if composeFile == "" {
		fmt.Fprintln(a.stderr, "no active compose file found; run `loganalysis up` first")
		return 1
	}

	cmdArgs := []string{"down", "--remove-orphans"}
	if *removeVolumes {
		cmdArgs = append(cmdArgs, "-v")
	}
	if err := a.runCompose(composeFile, cmdArgs...); err != nil {
		fmt.Fprintf(a.stderr, "down failed: %v\n", err)
		return 1
	}

	a.state.CurrentProfile = ""
	a.state.CurrentVersion = ""
	a.state.ComposeFile = composeFile
	a.state.UpdatedAt = time.Now().Format(time.RFC3339)
	if err := a.saveState(); err != nil {
		fmt.Fprintf(a.stderr, "warning: failed to save state: %v\n", err)
	}

	fmt.Fprintln(a.stdout, "stack stopped")
	return 0
}

func (a *runtimeApp) cmdStatus(args []string) int {
	if len(args) > 0 {
		fmt.Fprintln(a.stderr, "status does not accept positional arguments")
		return 2
	}
	composeFile := a.resolveComposeFile()
	if composeFile == "" {
		fmt.Fprintln(a.stderr, "no active compose file found; run `loganalysis up` first")
		return 1
	}

	if err := a.runCompose(composeFile, "ps"); err != nil {
		fmt.Fprintf(a.stderr, "status failed: %v\n", err)
		return 1
	}

	if a.state.CurrentVersion != "" || a.state.CurrentProfile != "" {
		fmt.Fprintf(a.stdout, "current profile=%s version=%s\n", a.state.CurrentProfile, a.state.CurrentVersion)
	}
	return 0
}

func (a *runtimeApp) cmdLogs(args []string) int {
	fs := flag.NewFlagSet("logs", flag.ContinueOnError)
	fs.SetOutput(a.stderr)
	follow := fs.Bool("f", false, "follow logs")
	tail := fs.String("tail", "200", "number of lines")
	if err := fs.Parse(args); err != nil {
		return 2
	}

	composeFile := a.resolveComposeFile()
	if composeFile == "" {
		fmt.Fprintln(a.stderr, "no active compose file found; run `loganalysis up` first")
		return 1
	}

	cmdArgs := []string{"logs", "--tail", *tail}
	if *follow {
		cmdArgs = append(cmdArgs, "-f")
	}
	if rest := fs.Args(); len(rest) > 0 {
		cmdArgs = append(cmdArgs, rest[0])
	}

	if err := a.runCompose(composeFile, cmdArgs...); err != nil {
		fmt.Fprintf(a.stderr, "logs failed: %v\n", err)
		return 1
	}
	return 0
}

func (a *runtimeApp) cmdDoctor(args []string) int {
	if len(args) > 0 {
		fmt.Fprintln(a.stderr, "doctor does not accept positional arguments")
		return 2
	}

	type result struct {
		name   string
		status string
		detail string
	}
	results := make([]result, 0, 16)
	fail := false

	if _, err := exec.LookPath("docker"); err != nil {
		results = append(results, result{"docker", "FAIL", err.Error()})
		fail = true
	} else {
		results = append(results, result{"docker", "PASS", "found"})
	}

	if _, err := detectComposeCommand(); err != nil {
		results = append(results, result{"docker compose", "FAIL", err.Error()})
		fail = true
	} else {
		results = append(results, result{"docker compose", "PASS", "found"})
	}

	if err := a.runSimple("docker", "info"); err != nil {
		results = append(results, result{"docker daemon", "FAIL", err.Error()})
		fail = true
	} else {
		results = append(results, result{"docker daemon", "PASS", "running"})
	}

	registryAddr := registryAddressForConnectivity(a.cfg.ImageRegistry)
	if err := checkRegistryConnectivity(registryAddr, 5*time.Second); err != nil {
		results = append(results, result{"registry connectivity", "FAIL", err.Error()})
		fail = true
	} else {
		results = append(results, result{"registry connectivity", "PASS", registryAddr + " reachable"})
	}

	portFailures := a.checkPortAvailability()
	if len(portFailures) > 0 {
		fail = true
		for _, item := range portFailures {
			results = append(results, result{"port " + item.port, "FAIL", item.err.Error()})
		}
	} else {
		results = append(results, result{"ports", "PASS", "all configured ports are available"})
	}

	if err := a.runSimple("docker", "system", "df"); err != nil {
		results = append(results, result{"docker disk stats", "WARN", err.Error()})
	} else {
		results = append(results, result{"docker disk stats", "PASS", "docker system df succeeded"})
	}

	freeBytes, err := a.detectDiskFreeBytes()
	if err != nil {
		results = append(results, result{"disk space", "WARN", err.Error()})
	} else {
		const minBytes = 5 * 1024 * 1024 * 1024
		if freeBytes < minBytes {
			results = append(results, result{"disk space", "FAIL", fmt.Sprintf("only %.2f GB available", float64(freeBytes)/(1024*1024*1024))})
			fail = true
		} else {
			results = append(results, result{"disk space", "PASS", fmt.Sprintf("%.2f GB available", float64(freeBytes)/(1024*1024*1024))})
		}
	}

	fmt.Fprintln(a.stdout, "doctor report:")
	for _, r := range results {
		fmt.Fprintf(a.stdout, "- %-22s [%s] %s\n", r.name, r.status, r.detail)
	}

	if fail {
		return 1
	}
	return 0
}

type portFailure struct {
	port string
	err  error
}

type portSlot struct {
	name  string
	value *int
}

func autoResolveProfilePorts(profile string, ports PortsConfig, available func(int) bool) (PortsConfig, []string, error) {
	if available == nil {
		return ports, nil, errors.New("available function is nil")
	}

	resolved := ports
	slots, err := profilePortSlots(profile, &resolved)
	if err != nil {
		return ports, nil, err
	}

	occupied := map[int]string{}
	changes := make([]string, 0)

	for _, slot := range slots {
		current := *slot.value
		if current <= 0 || current > 65535 {
			return ports, nil, fmt.Errorf("%s has invalid port: %d", slot.name, current)
		}

		if owner, exists := occupied[current]; !exists && available(current) {
			occupied[current] = slot.name
			continue
		} else if exists && owner == slot.name {
			continue
		}

		next, err := nextAvailablePort(current, occupied, available)
		if err != nil {
			return ports, nil, fmt.Errorf("resolve %s from %d: %w", slot.name, current, err)
		}
		*slot.value = next
		occupied[next] = slot.name
		changes = append(changes, fmt.Sprintf("%s %d -> %d", slot.name, current, next))
	}

	return resolved, changes, nil
}

func profilePortSlots(profile string, ports *PortsConfig) ([]portSlot, error) {
	if profile != profileFull {
		return nil, fmt.Errorf("invalid profile: %s", profile)
	}
	return []portSlot{
		{name: "frontend", value: &ports.Frontend},
		{name: "backend", value: &ports.Backend},
		{name: "postgres", value: &ports.Postgres},
		{name: "redis", value: &ports.Redis},
		{name: "rabbitmq", value: &ports.RabbitMQ},
		{name: "rabbitmq_management", value: &ports.RabbitMQManagement},
		{name: "elasticsearch", value: &ports.Elasticsearch},
		{name: "elasticsearch_transport", value: &ports.ElasticsearchTransport},
		{name: "kibana", value: &ports.Kibana},
		{name: "minio_api", value: &ports.MinioAPI},
		{name: "minio_console", value: &ports.MinioConsole},
	}, nil
}

func nextAvailablePort(start int, occupied map[int]string, available func(int) bool) (int, error) {
	for p := start; p <= 65535; p++ {
		if _, exists := occupied[p]; exists {
			continue
		}
		if available(p) {
			return p, nil
		}
	}
	for p := 1024; p < start; p++ {
		if _, exists := occupied[p]; exists {
			continue
		}
		if available(p) {
			return p, nil
		}
	}
	return 0, errors.New("no free port found")
}

func isPortAvailable(port int) bool {
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		return false
	}
	_ = ln.Close()
	return true
}

func (a *runtimeApp) checkPortAvailability() []portFailure {
	ports := map[string]int{
		"frontend":                a.cfg.Ports.Frontend,
		"backend":                 a.cfg.Ports.Backend,
		"postgres":                a.cfg.Ports.Postgres,
		"redis":                   a.cfg.Ports.Redis,
		"rabbitmq":                a.cfg.Ports.RabbitMQ,
		"rabbitmq_management":     a.cfg.Ports.RabbitMQManagement,
		"elasticsearch":           a.cfg.Ports.Elasticsearch,
		"elasticsearch_transport": a.cfg.Ports.ElasticsearchTransport,
		"kibana":                  a.cfg.Ports.Kibana,
		"minio_api":               a.cfg.Ports.MinioAPI,
		"minio_console":           a.cfg.Ports.MinioConsole,
	}
	failures := make([]portFailure, 0)
	for name, p := range ports {
		if p <= 0 {
			continue
		}
		if !isPortAvailable(p) {
			failures = append(failures, portFailure{port: fmt.Sprintf("%s(%d)", name, p), err: fmt.Errorf("already in use")})
			continue
		}
	}
	return failures
}

func (a *runtimeApp) cmdAuth(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(a.stderr, "usage: loganalysis auth <set-admin|passwd|show> ...")
		return 2
	}

	sub := strings.ToLower(args[0])
	subArgs := args[1:]

	switch sub {
	case "set-admin":
		fs := flag.NewFlagSet("auth set-admin", flag.ContinueOnError)
		fs.SetOutput(a.stderr)
		username := fs.String("username", "", "admin username")
		if err := fs.Parse(subArgs); err != nil {
			return 2
		}
		if !isTTY() {
			fmt.Fprintln(a.stderr, "auth set-admin requires an interactive TTY for password input")
			return 1
		}
		name := strings.TrimSpace(*username)
		if name == "" {
			fmt.Fprintln(a.stderr, "usage: loganalysis auth set-admin --username <name>")
			return 2
		}

		password, err := promptPasswordWithConfirm(a.stdout, "new admin password")
		if err != nil {
			fmt.Fprintf(a.stderr, "failed to read password: %v\n", err)
			return 1
		}
		hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
		if err != nil {
			fmt.Fprintf(a.stderr, "failed to hash password: %v\n", err)
			return 1
		}

		a.cfg.Auth.Enabled = true
		a.cfg.Auth.AdminUsername = name
		a.cfg.Auth.AdminPasswordHash = string(hash)
		if strings.TrimSpace(a.cfg.Auth.JwtSecret) == "" {
			secret, err := generateJWTSecret()
			if err != nil {
				fmt.Fprintf(a.stderr, "failed to generate jwt secret: %v\n", err)
				return 1
			}
			a.cfg.Auth.JwtSecret = secret
		}
		if a.cfg.Auth.JwtTtlHours <= 0 {
			a.cfg.Auth.JwtTtlHours = 24
		}
		if err := a.saveConfig(); err != nil {
			fmt.Fprintf(a.stderr, "save config failed: %v\n", err)
			return 1
		}
		fmt.Fprintln(a.stdout, "admin credentials configured")
		return 0

	case "passwd":
		if strings.TrimSpace(a.cfg.Auth.AdminUsername) == "" || strings.TrimSpace(a.cfg.Auth.AdminPasswordHash) == "" {
			fmt.Fprintln(a.stderr, "admin credentials are not initialized, run `loganalysis auth set-admin --username <name>` first")
			return 1
		}
		if !isTTY() {
			fmt.Fprintln(a.stderr, "auth passwd requires an interactive TTY for password input")
			return 1
		}

		current, err := promptPassword(a.stdout, "current password: ")
		if err != nil {
			fmt.Fprintf(a.stderr, "failed to read current password: %v\n", err)
			return 1
		}
		if err := bcrypt.CompareHashAndPassword([]byte(a.cfg.Auth.AdminPasswordHash), []byte(current)); err != nil {
			fmt.Fprintln(a.stderr, "current password is incorrect")
			return 1
		}

		nextPassword, err := promptPasswordWithConfirm(a.stdout, "new admin password")
		if err != nil {
			fmt.Fprintf(a.stderr, "failed to read new password: %v\n", err)
			return 1
		}
		hash, err := bcrypt.GenerateFromPassword([]byte(nextPassword), bcrypt.DefaultCost)
		if err != nil {
			fmt.Fprintf(a.stderr, "failed to hash password: %v\n", err)
			return 1
		}

		secret, err := generateJWTSecret()
		if err != nil {
			fmt.Fprintf(a.stderr, "failed to rotate jwt secret: %v\n", err)
			return 1
		}

		a.cfg.Auth.AdminPasswordHash = string(hash)
		a.cfg.Auth.JwtSecret = secret
		if err := a.saveConfig(); err != nil {
			fmt.Fprintf(a.stderr, "save config failed: %v\n", err)
			return 1
		}

		restarted, err := a.restartBackendIfRunning()
		if err != nil {
			fmt.Fprintf(a.stderr, "password updated, but backend recreate failed: %v\n", err)
			fmt.Fprintln(a.stdout, "new password will take effect on next `loganalysis up`")
			return 1
		}
		if restarted {
			fmt.Fprintln(a.stdout, "password updated, backend recreated, and existing tokens revoked")
			return 0
		}
		fmt.Fprintln(a.stdout, "password updated and tokens rotated; backend is not running, changes take effect on next `loganalysis up`")
		return 0

	case "show":
		fmt.Fprintf(a.stdout, "enabled: %t\n", a.cfg.Auth.Enabled)
		fmt.Fprintf(a.stdout, "admin_username: %s\n", maskSensitive(a.cfg.Auth.AdminUsername))
		fmt.Fprintf(a.stdout, "admin_password_hash: %s\n", maskSensitive(a.cfg.Auth.AdminPasswordHash))
		fmt.Fprintf(a.stdout, "jwt_secret: %s\n", maskSensitive(a.cfg.Auth.JwtSecret))
		fmt.Fprintf(a.stdout, "jwt_ttl_hours: %d\n", a.cfg.Auth.JwtTtlHours)
		return 0

	default:
		fmt.Fprintf(a.stderr, "unknown auth subcommand: %s\n", sub)
		return 2
	}
}

func (a *runtimeApp) ensureAuthConfigForUp() error {
	if !a.cfg.Auth.Enabled {
		return nil
	}

	changed := false
	if strings.TrimSpace(a.cfg.Auth.AdminUsername) == "" || strings.TrimSpace(a.cfg.Auth.AdminPasswordHash) == "" {
		if !isTTY() {
			return errors.New("auth is enabled but admin credentials are missing; run `loganalysis auth set-admin --username <name>` first")
		}
		fmt.Fprintln(a.stdout, "auth is enabled and admin credentials are not initialized, entering setup")

		if strings.TrimSpace(a.cfg.Auth.AdminUsername) == "" {
			username, err := promptLine(a.stdout, "admin username: ")
			if err != nil {
				return fmt.Errorf("read admin username: %w", err)
			}
			if username == "" {
				return errors.New("admin username cannot be empty")
			}
			a.cfg.Auth.AdminUsername = username
			changed = true
		}

		password, err := promptPasswordWithConfirm(a.stdout, "admin password")
		if err != nil {
			return fmt.Errorf("read admin password: %w", err)
		}
		hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
		if err != nil {
			return fmt.Errorf("hash admin password: %w", err)
		}
		a.cfg.Auth.AdminPasswordHash = string(hash)
		changed = true
	}

	if strings.TrimSpace(a.cfg.Auth.JwtSecret) == "" {
		secret, err := generateJWTSecret()
		if err != nil {
			return fmt.Errorf("generate jwt secret: %w", err)
		}
		a.cfg.Auth.JwtSecret = secret
		changed = true
	}
	if a.cfg.Auth.JwtTtlHours <= 0 {
		a.cfg.Auth.JwtTtlHours = 24
		changed = true
	}

	if changed {
		if err := a.saveConfig(); err != nil {
			return fmt.Errorf("save auth config: %w", err)
		}
		fmt.Fprintln(a.stdout, "auth config initialized")
	}
	return nil
}

func (a *runtimeApp) cmdConfig(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(a.stderr, "usage: loganalysis config <set|get|list|path> ...")
		return 2
	}

	sub := strings.ToLower(args[0])
	subArgs := args[1:]

	switch sub {
	case "set":
		if len(subArgs) != 2 {
			fmt.Fprintln(a.stderr, "usage: loganalysis config set <key> <value>")
			return 2
		}
		if err := a.setConfigKey(subArgs[0], subArgs[1]); err != nil {
			fmt.Fprintf(a.stderr, "config set failed: %v\n", err)
			return 1
		}
		if subArgs[0] == "data_dir" {
			if err := os.MkdirAll(a.cfg.DataDir, 0o755); err != nil {
				fmt.Fprintf(a.stderr, "failed to create data_dir: %v\n", err)
				return 1
			}
		}
		if err := a.saveConfig(); err != nil {
			fmt.Fprintf(a.stderr, "save config failed: %v\n", err)
			return 1
		}
		fmt.Fprintf(a.stdout, "%s updated\n", subArgs[0])
		return 0
	case "get":
		if len(subArgs) != 1 {
			fmt.Fprintln(a.stderr, "usage: loganalysis config get <key>")
			return 2
		}
		val, err := a.getConfigKey(subArgs[0])
		if err != nil {
			fmt.Fprintf(a.stderr, "config get failed: %v\n", err)
			return 1
		}
		fmt.Fprintln(a.stdout, val)
		return 0
	case "list":
		buf, _ := json.MarshalIndent(a.cfg, "", "  ")
		fmt.Fprintln(a.stdout, string(buf))
		return 0
	case "path":
		fmt.Fprintln(a.stdout, a.paths.ConfigPath)
		return 0
	default:
		fmt.Fprintf(a.stderr, "unknown config subcommand: %s\n", sub)
		return 2
	}
}

func (a *runtimeApp) setConfigKey(key, value string) error {
	switch key {
	case "project_name":
		a.cfg.ProjectName = value
	case "default_profile":
		if err := validateProfile(value); err != nil {
			return err
		}
		a.cfg.DefaultProfile = value
	case "default_version":
		a.cfg.DefaultVersion = value
	case "image_registry":
		a.cfg.ImageRegistry = migrateImageRegistry(strings.TrimRight(value, "/"))
	case "backend_image":
		a.cfg.BackendImage = value
	case "frontend_image":
		a.cfg.FrontendImage = value
	case "release_repo":
		a.cfg.ReleaseRepo = strings.Trim(value, " ")
	case "data_dir":
		a.cfg.DataDir = value
	case "auth.enabled":
		enabled, err := parseBoolValue(value)
		if err != nil {
			return fmt.Errorf("invalid auth.enabled: %w", err)
		}
		a.cfg.Auth.Enabled = enabled
	case "auth.admin_username":
		a.cfg.Auth.AdminUsername = strings.TrimSpace(value)
	case "auth.admin_password_hash":
		a.cfg.Auth.AdminPasswordHash = strings.TrimSpace(value)
	case "auth.jwt_secret":
		a.cfg.Auth.JwtSecret = strings.TrimSpace(value)
	case "auth.jwt_ttl_hours":
		ttl, err := strconv.Atoi(strings.TrimSpace(value))
		if err != nil {
			return fmt.Errorf("invalid auth.jwt_ttl_hours %q: %w", value, err)
		}
		if ttl <= 0 {
			return fmt.Errorf("auth.jwt_ttl_hours must be > 0")
		}
		a.cfg.Auth.JwtTtlHours = ttl
	case "ports.frontend":
		return setPort(&a.cfg.Ports.Frontend, value)
	case "ports.backend":
		return setPort(&a.cfg.Ports.Backend, value)
	case "ports.postgres":
		return setPort(&a.cfg.Ports.Postgres, value)
	case "ports.redis":
		return setPort(&a.cfg.Ports.Redis, value)
	case "ports.rabbitmq":
		return setPort(&a.cfg.Ports.RabbitMQ, value)
	case "ports.rabbitmq_management":
		return setPort(&a.cfg.Ports.RabbitMQManagement, value)
	case "ports.elasticsearch":
		return setPort(&a.cfg.Ports.Elasticsearch, value)
	case "ports.elasticsearch_transport":
		return setPort(&a.cfg.Ports.ElasticsearchTransport, value)
	case "ports.kibana":
		return setPort(&a.cfg.Ports.Kibana, value)
	case "ports.minio_api":
		return setPort(&a.cfg.Ports.MinioAPI, value)
	case "ports.minio_console":
		return setPort(&a.cfg.Ports.MinioConsole, value)
	default:
		return fmt.Errorf("unsupported key: %s", key)
	}
	return nil
}

func (a *runtimeApp) getConfigKey(key string) (string, error) {
	switch key {
	case "project_name":
		return a.cfg.ProjectName, nil
	case "default_profile":
		return a.cfg.DefaultProfile, nil
	case "default_version":
		return a.cfg.DefaultVersion, nil
	case "image_registry":
		return a.cfg.ImageRegistry, nil
	case "backend_image":
		return a.cfg.BackendImage, nil
	case "frontend_image":
		return a.cfg.FrontendImage, nil
	case "release_repo":
		return a.cfg.ReleaseRepo, nil
	case "data_dir":
		return a.cfg.DataDir, nil
	case "auth.enabled":
		return strconv.FormatBool(a.cfg.Auth.Enabled), nil
	case "auth.admin_username":
		return a.cfg.Auth.AdminUsername, nil
	case "auth.admin_password_hash":
		return a.cfg.Auth.AdminPasswordHash, nil
	case "auth.jwt_secret":
		return a.cfg.Auth.JwtSecret, nil
	case "auth.jwt_ttl_hours":
		return strconv.Itoa(a.cfg.Auth.JwtTtlHours), nil
	case "ports.frontend":
		return strconv.Itoa(a.cfg.Ports.Frontend), nil
	case "ports.backend":
		return strconv.Itoa(a.cfg.Ports.Backend), nil
	case "ports.postgres":
		return strconv.Itoa(a.cfg.Ports.Postgres), nil
	case "ports.redis":
		return strconv.Itoa(a.cfg.Ports.Redis), nil
	case "ports.rabbitmq":
		return strconv.Itoa(a.cfg.Ports.RabbitMQ), nil
	case "ports.rabbitmq_management":
		return strconv.Itoa(a.cfg.Ports.RabbitMQManagement), nil
	case "ports.elasticsearch":
		return strconv.Itoa(a.cfg.Ports.Elasticsearch), nil
	case "ports.elasticsearch_transport":
		return strconv.Itoa(a.cfg.Ports.ElasticsearchTransport), nil
	case "ports.kibana":
		return strconv.Itoa(a.cfg.Ports.Kibana), nil
	case "ports.minio_api":
		return strconv.Itoa(a.cfg.Ports.MinioAPI), nil
	case "ports.minio_console":
		return strconv.Itoa(a.cfg.Ports.MinioConsole), nil
	default:
		return "", fmt.Errorf("unsupported key: %s", key)
	}
}

func setPort(dst *int, value string) error {
	p, err := strconv.Atoi(value)
	if err != nil {
		return fmt.Errorf("invalid port %q: %w", value, err)
	}
	if p <= 0 || p > 65535 {
		return fmt.Errorf("port out of range: %d", p)
	}
	*dst = p
	return nil
}

func parseBoolValue(raw string) (bool, error) {
	v := strings.TrimSpace(raw)
	if v == "" {
		return false, fmt.Errorf("value cannot be empty")
	}
	parsed, err := strconv.ParseBool(v)
	if err != nil {
		return false, err
	}
	return parsed, nil
}

func migrateImageRegistry(raw string) string {
	registry := strings.TrimSpace(strings.TrimRight(raw, "/"))
	if registry == "" {
		return registry
	}
	switch registry {
	case "3362345814", "docker.io/3362345814", "https://docker.io/3362345814", "ghcr.io/3362345814":
		return "docker.io/cityseason"
	}
	if strings.HasPrefix(registry, "ghcr.io/") {
		return "docker.io/" + strings.TrimPrefix(registry, "ghcr.io/")
	}
	return registry
}

func (a *runtimeApp) cmdUpgrade(args []string) int {
	fs := flag.NewFlagSet("upgrade", flag.ContinueOnError)
	fs.SetOutput(a.stderr)
	to := fs.String("to", "latest", "target version tag")
	allowMajor := fs.Bool("allow-major", false, "allow major version upgrade")
	force := fs.Bool("force", false, "force pull and restart even when target matches current version")
	autoPort := fs.Bool("auto-port", true, "auto-resolve host port conflicts")
	noAutoPort := fs.Bool("no-auto-port", false, "disable auto-resolve host port conflicts")

	if err := fs.Parse(args); err != nil {
		return 2
	}
	if err := a.ensureDockerReady(); err != nil {
		fmt.Fprintf(a.stderr, "docker precheck failed: %v\n", err)
		return 1
	}

	target := strings.TrimSpace(*to)
	if target == "latest" {
		latest, err := fetchLatestTag(a.cfg.ReleaseRepo)
		if err == nil && latest != "" {
			target = latest
			fmt.Fprintf(a.stdout, "resolved latest version: %s\n", target)
		} else {
			fmt.Fprintf(a.stderr, "warning: failed to resolve latest release, fallback to image tag 'latest': %v\n", err)
			target = "latest"
		}
	}

	old := a.state.CurrentVersion
	if old == "" {
		old = a.cfg.DefaultVersion
	}

	if majorChanged(old, target) && !*allowMajor {
		fmt.Fprintf(a.stderr, "major upgrade blocked: %s -> %s, re-run with --allow-major to continue\n", old, target)
		return 1
	}
	if sameVersionTag(old, target) && !*force {
		fmt.Fprintf(a.stdout, "current version %s already matches target %s; use --force to pull again\n", old, target)
		return 0
	}

	profile := a.state.CurrentProfile
	if profile == "" {
		profile = a.cfg.DefaultProfile
	}
	if err := validateProfile(profile); err != nil {
		fmt.Fprintf(a.stderr, "invalid profile in state/config: %v\n", err)
		return 1
	}

	if err := backupFile(a.paths.ActiveCompose, a.paths.BackupCompose); err != nil && !errors.Is(err, os.ErrNotExist) {
		fmt.Fprintf(a.stderr, "backup compose file failed: %v\n", err)
		return 1
	}

	enableAutoPort := *autoPort && !*noAutoPort
	portsToUse := a.cfg.Ports
	var portChanges []string
	if enableAutoPort {
		resolved, changes, err := autoResolveProfilePorts(profile, a.cfg.Ports, isPortAvailable)
		if err != nil {
			fmt.Fprintf(a.stderr, "auto-port failed: %v\n", err)
			return 1
		}
		portsToUse = resolved
		portChanges = changes
		if len(portChanges) > 0 {
			fmt.Fprintln(a.stdout, "auto-port remapped host ports:")
			for _, item := range portChanges {
				fmt.Fprintf(a.stdout, "- %s\n", item)
			}
		}
	}

	if err := a.applyStack(profile, target, portsToUse); err != nil {
		fmt.Fprintf(a.stderr, "upgrade failed, trying rollback: %v\n", err)
		if rbErr := a.rollback(old); rbErr != nil {
			fmt.Fprintf(a.stderr, "rollback failed: %v\n", rbErr)
		}
		return 1
	}

	if enableAutoPort && len(portChanges) > 0 {
		a.cfg.Ports = portsToUse
		if err := a.saveConfig(); err != nil {
			fmt.Fprintf(a.stderr, "warning: failed to persist remapped ports: %v\n", err)
		}
	}

	a.state.PreviousVersion = old
	a.state.CurrentVersion = target
	a.state.CurrentProfile = profile
	a.state.ComposeFile = a.paths.ActiveCompose
	a.state.UpdatedAt = time.Now().Format(time.RFC3339)
	if err := a.saveState(); err != nil {
		fmt.Fprintf(a.stderr, "warning: failed to save state: %v\n", err)
	}

	if err := a.selfUpdate(target); err != nil {
		fmt.Fprintf(a.stderr, "warning: stack upgraded but CLI self-update skipped: %v\n", err)
	}

	fmt.Fprintf(a.stdout, "upgrade succeeded: %s -> %s\n", old, target)
	return 0
}

func (a *runtimeApp) rollback(oldVersion string) error {
	if _, err := os.Stat(a.paths.BackupCompose); err == nil {
		if err := copyFile(a.paths.BackupCompose, a.paths.ActiveCompose); err != nil {
			return fmt.Errorf("restore compose file: %w", err)
		}
	}
	if oldVersion == "" {
		return nil
	}
	if err := a.runCompose(a.paths.ActiveCompose, "pull"); err != nil {
		return fmt.Errorf("rollback pull: %w", err)
	}
	if err := a.runCompose(a.paths.ActiveCompose, "up", "-d"); err != nil {
		return fmt.Errorf("rollback up: %w", err)
	}
	return nil
}

func (a *runtimeApp) selfUpdate(target string) error {
	if target == "" || target == "latest" {
		return nil
	}
	exePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("resolve executable: %w", err)
	}
	asset := fmt.Sprintf("loganalysis-%s-%s", runtime.GOOS, runtime.GOARCH)
	if runtime.GOOS == "windows" {
		asset += ".exe"
	}
	url := fmt.Sprintf("https://github.com/%s/releases/download/%s/%s", a.cfg.ReleaseRepo, target, asset)

	tmp := exePath + ".download"
	if err := downloadFile(url, tmp); err != nil {
		return fmt.Errorf("download %s: %w", url, err)
	}
	if err := os.Chmod(tmp, 0o755); err != nil && runtime.GOOS != "windows" {
		_ = os.Remove(tmp)
		return fmt.Errorf("chmod update file: %w", err)
	}

	if runtime.GOOS == "windows" {
		pending := exePath + ".new"
		if err := os.Rename(tmp, pending); err != nil {
			_ = os.Remove(tmp)
			return fmt.Errorf("stage windows update: %w", err)
		}
		fmt.Fprintf(a.stdout, "new CLI binary staged at %s (replace executable after process exits)\n", pending)
		return nil
	}

	backup := exePath + ".bak"
	_ = os.Remove(backup)
	if err := os.Rename(exePath, backup); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("backup executable: %w", err)
	}
	if err := os.Rename(tmp, exePath); err != nil {
		_ = os.Rename(backup, exePath)
		_ = os.Remove(tmp)
		return fmt.Errorf("replace executable: %w", err)
	}
	_ = os.Remove(backup)
	fmt.Fprintln(a.stdout, "CLI binary updated successfully")
	return nil
}

func downloadFile(url, path string) error {
	client := &http.Client{Timeout: 90 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("unexpected status: %s", resp.Status)
	}

	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer func() {
		_ = f.Close()
	}()

	if _, err := io.Copy(f, resp.Body); err != nil {
		return err
	}
	return nil
}

func fetchLatestTag(repo string) (string, error) {
	url := fmt.Sprintf("https://api.github.com/repos/%s/releases/latest", strings.TrimSpace(repo))
	client := &http.Client{Timeout: 15 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("unexpected status: %s", resp.Status)
	}
	var payload struct {
		TagName string `json:"tag_name"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return "", err
	}
	if payload.TagName == "" {
		return "", errors.New("tag_name is empty")
	}
	return payload.TagName, nil
}

func sameVersionTag(current, target string) bool {
	currentTag := strings.TrimSpace(current)
	targetTag := strings.TrimSpace(target)
	if currentTag == "" || targetTag == "" {
		return false
	}
	if currentTag == targetTag {
		return true
	}
	currentWithoutPrefix := strings.TrimPrefix(currentTag, "v")
	targetWithoutPrefix := strings.TrimPrefix(targetTag, "v")
	return currentWithoutPrefix != "" && currentWithoutPrefix == targetWithoutPrefix
}

func majorChanged(fromVersion, toVersion string) bool {
	from, okFrom := major(fromVersion)
	to, okTo := major(toVersion)
	if !okFrom || !okTo {
		return false
	}
	return from != to
}

func major(v string) (int, bool) {
	m := semverMajorRe.FindStringSubmatch(strings.TrimSpace(v))
	if len(m) != 2 {
		return 0, false
	}
	n, err := strconv.Atoi(m[1])
	if err != nil {
		return 0, false
	}
	return n, true
}

func (a *runtimeApp) cmdUninstall(args []string) int {
	fs := flag.NewFlagSet("uninstall", flag.ContinueOnError)
	fs.SetOutput(a.stderr)
	purge := fs.Bool("purge-data", false, "remove ~/.loganalysis including config and data")
	keepCLI := fs.Bool("keep-cli", false, "keep CLI binary and only remove runtime files")
	if err := fs.Parse(args); err != nil {
		return 2
	}

	composeFile := a.resolveComposeFile()
	if composeFile != "" {
		cmdArgs := []string{"down", "--remove-orphans"}
		if *purge {
			cmdArgs = append(cmdArgs, "-v")
		}
		if err := a.runCompose(composeFile, cmdArgs...); err != nil {
			fmt.Fprintf(a.stderr, "warning: stack down failed during uninstall: %v\n", err)
		}
	}

	if *purge {
		if err := os.RemoveAll(a.paths.Root); err != nil {
			fmt.Fprintf(a.stderr, "uninstall failed: %v\n", err)
			return 1
		}
		fmt.Fprintf(a.stdout, "removed %s\n", a.paths.Root)
	} else {
		_ = os.Remove(a.paths.StatePath)
		_ = os.RemoveAll(a.paths.RuntimeDir)
		fmt.Fprintln(a.stdout, "runtime files removed; config/data kept")
	}

	if *keepCLI {
		fmt.Fprintln(a.stdout, "CLI binary kept")
		return 0
	}
	if err := a.removeSelfBinary(); err != nil {
		fmt.Fprintf(a.stderr, "warning: failed to remove CLI binary: %v\n", err)
		return 1
	}
	return 0
}

func (a *runtimeApp) removeSelfBinary() error {
	exePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("resolve executable: %w", err)
	}
	exePath, err = filepath.Abs(exePath)
	if err != nil {
		return fmt.Errorf("resolve executable absolute path: %w", err)
	}

	base := strings.ToLower(filepath.Base(exePath))
	if base != "loganalysis" && base != "loganalysis.exe" {
		return fmt.Errorf("refuse to remove unexpected executable name %q at %s", filepath.Base(exePath), exePath)
	}

	if runtime.GOOS == "windows" {
		if err := scheduleWindowsSelfRemove(exePath); err != nil {
			return err
		}
		fmt.Fprintf(a.stdout, "CLI binary removal scheduled: %s\n", exePath)
		return nil
	}

	if err := os.Remove(exePath); err != nil {
		return fmt.Errorf("remove %s: %w", exePath, err)
	}
	fmt.Fprintf(a.stdout, "removed CLI binary %s\n", exePath)
	return nil
}

func scheduleWindowsSelfRemove(exePath string) error {
	ps := fmt.Sprintf(
		"Start-Sleep -Seconds 1; Remove-Item -LiteralPath %s -Force -ErrorAction Stop",
		powershellSingleQuoted(exePath),
	)
	cmd := exec.Command("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps)
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("schedule windows self removal: %w", err)
	}
	return nil
}

func powershellSingleQuoted(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}

func (a *runtimeApp) applyStack(profile, version string, ports PortsConfig) error {
	if err := validateProfile(profile); err != nil {
		return err
	}

	if err := a.renderComposeFile(a.paths.ActiveCompose, profile, version, ports); err != nil {
		return err
	}

	if err := a.runCompose(a.paths.ActiveCompose, "pull"); err != nil {
		return fmt.Errorf("compose pull: %w", err)
	}
	if err := a.runCompose(a.paths.ActiveCompose, "up", "-d"); err != nil {
		return fmt.Errorf("compose up: %w", err)
	}
	return nil
}

func (a *runtimeApp) renderComposeFile(path, profile, version string, ports PortsConfig) error {
	content, err := renderCompose(profile, templateData{
		ProjectName:   a.cfg.ProjectName,
		BackendImage:  a.backendImage(version),
		FrontendImage: a.frontendImage(version),
		Ports:         ports,
		Auth:          composeSafeAuth(a.cfg.Auth),
	})
	if err != nil {
		return err
	}

	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return fmt.Errorf("prepare compose path: %w", err)
	}
	if err := os.WriteFile(path, content, 0o644); err != nil {
		return fmt.Errorf("write compose file: %w", err)
	}
	return nil
}

func (a *runtimeApp) activeProfileAndVersion() (string, string) {
	profile := strings.TrimSpace(a.state.CurrentProfile)
	if profile == "" {
		profile = strings.TrimSpace(a.cfg.DefaultProfile)
	}
	if err := validateProfile(profile); err != nil {
		profile = profileFull
	}

	version := strings.TrimSpace(a.state.CurrentVersion)
	if version == "" {
		version = strings.TrimSpace(a.cfg.DefaultVersion)
	}
	if version == "" {
		version = "latest"
	}
	return profile, version
}

func (a *runtimeApp) backendImage(version string) string {
	if a.cfg.BackendImage != "" {
		return a.cfg.BackendImage
	}
	registry := strings.TrimRight(a.cfg.ImageRegistry, "/")
	return fmt.Sprintf("%s/loganalysis-backend:%s", registry, version)
}

func (a *runtimeApp) frontendImage(version string) string {
	if a.cfg.FrontendImage != "" {
		return a.cfg.FrontendImage
	}
	registry := strings.TrimRight(a.cfg.ImageRegistry, "/")
	return fmt.Sprintf("%s/loganalysis-frontend:%s", registry, version)
}

func (a *runtimeApp) ensureDockerReady() error {
	if _, err := exec.LookPath("docker"); err != nil {
		return fmt.Errorf("docker not found in PATH")
	}
	if _, err := detectComposeCommand(); err != nil {
		return err
	}
	if err := a.runSimple("docker", "info"); err != nil {
		return fmt.Errorf("docker daemon is not ready: %w", err)
	}
	return nil
}

func detectComposeCommand() ([]string, error) {
	if _, err := exec.LookPath("docker"); err == nil {
		cmd := exec.Command("docker", "compose", "version")
		if err := cmd.Run(); err == nil {
			return []string{"docker", "compose"}, nil
		}
	}
	if _, err := exec.LookPath("docker-compose"); err == nil {
		cmd := exec.Command("docker-compose", "version")
		if err := cmd.Run(); err == nil {
			return []string{"docker-compose"}, nil
		}
	}
	return nil, errors.New("docker compose not found (need `docker compose` or `docker-compose`)")
}

func (a *runtimeApp) runCompose(composeFile string, args ...string) error {
	cmd, err := a.composeCmd(composeFile, args...)
	if err != nil {
		return err
	}
	cmd.Stdout = a.stdout
	cmd.Stderr = a.stderr
	cmd.Stdin = os.Stdin
	return cmd.Run()
}

func (a *runtimeApp) runComposeOutput(composeFile string, args ...string) ([]byte, error) {
	cmd, err := a.composeCmd(composeFile, args...)
	if err != nil {
		return nil, err
	}
	cmd.Stderr = a.stderr
	return cmd.Output()
}

func (a *runtimeApp) composeCmd(composeFile string, args ...string) (*exec.Cmd, error) {
	prefix, err := detectComposeCommand()
	if err != nil {
		return nil, err
	}
	if len(prefix) == 2 {
		all := []string{prefix[1], "-f", composeFile, "-p", a.cfg.ProjectName}
		all = append(all, args...)
		return exec.Command(prefix[0], all...), nil
	} else {
		all := []string{"-f", composeFile, "-p", a.cfg.ProjectName}
		all = append(all, args...)
		return exec.Command(prefix[0], all...), nil
	}
}

func (a *runtimeApp) runSimple(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdout = io.Discard
	cmd.Stderr = io.Discard
	return cmd.Run()
}

func (a *runtimeApp) detectDiskFreeBytes() (uint64, error) {
	if runtime.GOOS == "windows" {
		return 0, errors.New("disk space check is currently available on unix-like systems only")
	}
	cmd := exec.Command("df", "-Pk", a.cfg.DataDir)
	out, err := cmd.Output()
	if err != nil {
		return 0, fmt.Errorf("run df: %w", err)
	}
	lines := strings.Split(strings.TrimSpace(string(out)), "\n")
	if len(lines) < 2 {
		return 0, fmt.Errorf("unexpected df output: %s", string(out))
	}
	fields := strings.Fields(lines[1])
	if len(fields) < 4 {
		return 0, fmt.Errorf("unexpected df line: %s", lines[1])
	}
	kb, err := strconv.ParseUint(fields[3], 10, 64)
	if err != nil {
		return 0, fmt.Errorf("parse available blocks: %w", err)
	}
	return kb * 1024, nil
}

func (a *runtimeApp) resolveComposeFile() string {
	candidates := []string{a.state.ComposeFile, a.paths.ActiveCompose}
	for _, p := range candidates {
		if strings.TrimSpace(p) == "" {
			continue
		}
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			return p
		}
	}
	return ""
}

func backupFile(src, dst string) error {
	if _, err := os.Stat(src); err != nil {
		return err
	}
	return copyFile(src, dst)
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return err
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer func() {
		_ = out.Close()
	}()

	if _, err := io.Copy(out, in); err != nil {
		return err
	}
	return out.Sync()
}

func composeSafeAuth(cfg AuthConfig) AuthConfig {
	safe := cfg
	safe.AdminUsername = escapeComposeValue(safe.AdminUsername)
	safe.AdminPasswordHash = escapeComposeValue(safe.AdminPasswordHash)
	safe.JwtSecret = escapeComposeValue(safe.JwtSecret)
	return safe
}

func escapeComposeValue(value string) string {
	return strings.ReplaceAll(value, "$", "$$")
}

func isTTY() bool {
	info, err := os.Stdin.Stat()
	if err != nil {
		return false
	}
	return (info.Mode() & os.ModeCharDevice) != 0
}

func promptLine(out io.Writer, prompt string) (string, error) {
	fmt.Fprint(out, prompt)
	reader := bufio.NewReader(os.Stdin)
	text, err := reader.ReadString('\n')
	if err != nil && !errors.Is(err, io.EOF) {
		return "", err
	}
	return strings.TrimSpace(text), nil
}

func promptPassword(out io.Writer, prompt string) (string, error) {
	password, err := promptLine(out, prompt)
	if err != nil {
		return "", err
	}
	if password == "" {
		return "", errors.New("password cannot be empty")
	}
	return password, nil
}

func promptPasswordWithConfirm(out io.Writer, label string) (string, error) {
	first, err := promptPassword(out, label+": ")
	if err != nil {
		return "", err
	}
	second, err := promptPassword(out, "confirm "+label+": ")
	if err != nil {
		return "", err
	}
	if first != second {
		return "", errors.New("passwords do not match")
	}
	return first, nil
}

func generateJWTSecret() (string, error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

func maskSensitive(raw string) string {
	if strings.TrimSpace(raw) == "" {
		return "(unset)"
	}
	runes := []rune(raw)
	if len(runes) <= 8 {
		return strings.Repeat("*", len(runes))
	}
	return string(runes[:4]) + strings.Repeat("*", len(runes)-8) + string(runes[len(runes)-4:])
}

func (a *runtimeApp) restartBackendIfRunning() (bool, error) {
	composeFile := a.resolveComposeFile()
	if composeFile == "" {
		return false, nil
	}

	out, err := a.runComposeOutput(composeFile, "ps", "-q", "backend")
	if err != nil {
		return false, nil
	}
	containerID := strings.TrimSpace(string(out))
	if containerID == "" {
		return false, nil
	}

	cmd := exec.Command("docker", "inspect", "-f", "{{.State.Running}}", containerID)
	state, err := cmd.Output()
	if err != nil {
		return false, nil
	}
	if strings.TrimSpace(string(state)) != "true" {
		return false, nil
	}

	profile, version := a.activeProfileAndVersion()
	if err := a.renderComposeFile(composeFile, profile, version, a.cfg.Ports); err != nil {
		return false, fmt.Errorf("refresh compose auth env: %w", err)
	}

	// Password hash and JWT secret live in container env vars, so backend
	// must be recreated (restart alone does not apply updated env).
	if err := a.runCompose(composeFile, "up", "-d", "--no-deps", "--force-recreate", "backend"); err != nil {
		return false, err
	}
	return true, nil
}

func checkRegistryConnectivity(addr string, timeout time.Duration) error {
	conn, err := net.DialTimeout("tcp", addr, timeout)
	if err != nil {
		return err
	}
	_ = conn.Close()
	return nil
}

func registryAddressForConnectivity(imageRegistry string) string {
	raw := strings.TrimSpace(imageRegistry)
	if raw == "" {
		return "docker.io:443"
	}

	raw = strings.TrimPrefix(raw, "https://")
	raw = strings.TrimPrefix(raw, "http://")
	if idx := strings.Index(raw, "/"); idx >= 0 {
		raw = raw[:idx]
	}
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "docker.io:443"
	}
	if strings.Contains(raw, ":") {
		return raw
	}
	return raw + ":443"
}
