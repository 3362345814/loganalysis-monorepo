package main

import (
	"os"

	"github.com/cityseason/loganalysis/cli/internal/cli"
)

var (
	version = "dev"
	commit  = "none"
	date    = "unknown"
)

func main() {
	build := cli.BuildInfo{
		Version: version,
		Commit:  commit,
		Date:    date,
	}
	os.Exit(cli.Execute(os.Args[1:], build, os.Stdout, os.Stderr))
}
