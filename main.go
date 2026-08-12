//go:generate go run ./cmd/reghook/reghook.go

package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/ClickHouse/clickhouse-go/v2/lib/driver"
	"github.com/peterbourgon/ff/v4"
	"github.com/peterbourgon/ff/v4/ffhelp"
	"golang.org/x/sync/errgroup"

	"github.com/ExplorViz/trace-service/internal/communication"
	"github.com/ExplorViz/trace-service/internal/function"
	"github.com/ExplorViz/trace-service/internal/timestamp"
	"github.com/ExplorViz/trace-service/internal/trace"
)

func main() {
	fs := ff.NewFlagSet("trace-service")
	var (
		httpPort   = fs.Int('p', "port", 8081, "port to listen on for incoming HTTP requests")
		dbHostAddr = fs.String('a', "db-addr", "localhost:19000", "network endpoint at which the Clickhouse database runs")
		dbName     = fs.StringLong("db-name", "default", "name of the Clickhouse database to use")
		dbUser     = fs.String('u', "db-user", "default", "username to use with the Clickhouse instance")
		dbPass     = fs.String('P', "db-pass", "", "password to use with the Clickhouse instance (insecure, prefer using env var)")
		logLevel   = fs.StringEnum('l', "log-level", "log level: info, error, debug", "info", "error", "debug")
	)

	if err := ff.Parse(fs, os.Args[1:], ff.WithEnvVarPrefix("EXPLORVIZ")); err != nil {
		fmt.Println(err)
		fmt.Printf("%s\n", ffhelp.Flags(fs))
		os.Exit(0)
	}

	switch *logLevel {
	case "info":
		slog.SetLogLoggerLevel(slog.LevelInfo)
	case "error":
		slog.SetLogLoggerLevel(slog.LevelError)
	case "debug":
		slog.SetLogLoggerLevel(slog.LevelDebug)
	}

	conn, err := dbConnect(*dbHostAddr, *dbName, *dbUser, *dbPass)
	if err != nil {
		slog.Error("failed to establish database connection", "error", err, "hostAddress", *dbHostAddr)
		os.Exit(1)
	}

	mux := http.NewServeMux()

	commRepo := communication.Repository{Conn: conn}
	commHandler := communication.NewHandler(commRepo)
	commHandler.Register(mux)

	funcRepo := function.Repository{Conn: conn}
	funcHandler := function.NewHandler(funcRepo)
	funcHandler.Register(mux)

	timestampRepo := timestamp.Repository{Conn: conn}
	timestampHandler := timestamp.NewHandler(timestampRepo)
	timestampHandler.Register(mux)

	traceRepo := trace.Repository{Conn: conn}
	traceHandler := trace.NewHandler(traceRepo)
	traceHandler.Register(mux)

	srv := &http.Server{Addr: ":" + strconv.Itoa(*httpPort), Handler: corsHandler(addContentTypeJSON(mux))}

	ctx, cancel := context.WithCancel(context.Background())
	eg, ctx := errgroup.WithContext(ctx)
	eg.Go(srv.ListenAndServe)

	go func() {
		sigs := make(chan os.Signal, 2)
		signal.Notify(sigs, os.Interrupt, syscall.SIGTERM)

		<-sigs
		slog.Info("received interrupt signal; gracefully stopping ...")
		if err := srv.Shutdown(ctx); err != nil && err != ctx.Err() {
			slog.Warn("error occurred during server shutdown", "error", err)
		}
		cancel()

		<-sigs
		slog.Info("received second interrupt signal; exiting immediately")
		os.Exit(1)
	}()

	fmt.Print(`
  ______            _         __      ___
 |  ____|          | |        \ \    / (_)
 | |__  __  ___ __ | | ___  _ _\ \  / / _ ____
 |  __| \ \/ / '_ \| |/ _ \| '__\ \/ / | |_  /
 | |____ >  <| |_) | | (_) | |   \  /  | |/ /
 |______/_/\_\ .__/|_|\___/|_|    \/   |_/___|
             | |
             |_|                 trace-service

`)

	if err := eg.Wait(); err != nil && err != http.ErrServerClosed {
		slog.Error("unexpected server shutdown", "error", err)
	}
}

func dbConnect(hostAddr string, dbName string, user string, pass string) (driver.Conn, error) {
	var (
		ctx       = context.Background()
		conn, err = clickhouse.Open(&clickhouse.Options{
			Addr: []string{hostAddr},
			Auth: clickhouse.Auth{
				Database: dbName,
				Username: user,
				Password: pass,
			},
		})
	)

	if err != nil {
		return nil, err
	}

	if err := conn.Ping(ctx); err != nil {
		if exception, ok := err.(*clickhouse.Exception); ok {
			fmt.Printf("Exception [%d] %s \n%s\n", exception.Code, exception.Message, exception.StackTrace)
		}
		return nil, err
	}
	return conn, nil
}

func addContentTypeJSON(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("Content-Type", "application/json")
		next.ServeHTTP(w, r)
	})
}

func corsHandler(next http.Handler) http.Handler {
	allowedOrigins := map[string]struct{}{
		"http://localhost:4200":                                 {},
		"http://localhost:8080":                                 {},
		"https://demo.explorviz.uni-kiel.de":                    {},
		"https://explorviz.sustainkieker.kieker-monitoring.net": {},
	}

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if _, ok := allowedOrigins[origin]; ok {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "*")
			w.Header().Set("Access-Control-Allow-Credentials", "true")
			w.Header().Set("Access-Control-Max-Age", "86400")
		}

		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusNoContent)
		} else {
			next.ServeHTTP(w, r)
		}
	})
}
