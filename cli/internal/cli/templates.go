package cli

import (
	"bytes"
	"embed"
	"fmt"
	"text/template"
)

//go:embed templates/*.yaml.tmpl
var templateFS embed.FS

type templateData struct {
	ProjectName   string
	BackendImage  string
	FrontendImage string
	Ports         PortsConfig
}

func renderCompose(profile string, data templateData) ([]byte, error) {
	var file string
	switch profile {
	case profileFull:
		file = "templates/compose-full.yaml.tmpl"
	case profileMinimal:
		file = "templates/compose-minimal.yaml.tmpl"
	case profileDB:
		file = "templates/compose-db.yaml.tmpl"
	default:
		return nil, fmt.Errorf("unsupported profile: %s", profile)
	}

	raw, err := templateFS.ReadFile(file)
	if err != nil {
		return nil, fmt.Errorf("read template: %w", err)
	}

	tmpl, err := template.New(profile).Parse(string(raw))
	if err != nil {
		return nil, fmt.Errorf("parse template: %w", err)
	}

	var buf bytes.Buffer
	if err := tmpl.Execute(&buf, data); err != nil {
		return nil, fmt.Errorf("execute template: %w", err)
	}
	return buf.Bytes(), nil
}
