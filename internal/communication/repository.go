package communication

import (
	"context"
	"math"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/ClickHouse/clickhouse-go/v2/lib/driver"
)

type Repository struct {
	Conn driver.Conn
}

// findCommunication searches the database for any spans starting within the time span given by fromUnixNano (inclusive) and toUnixNano (exclusive)
// where the span has a parent span. The child and parent span pairs are grouped by the visualization objects they represent, where the parent span
// indicates the source object and the child indicates the target object. A single communication is inferred for any such source-target pair.
// For each communication, general metrics are computed. To restrict search for spans to those associated with a specific commit, the commitHash value
// can be used. If left empty, then the search is explicitly restricted to spans that have no associated commit.
func (r *Repository) findCommunication(
	ctx context.Context, landscapeToken string, fromUnixNano uint64, toUnixNano uint64, commitHash string,
) (CommSummary, error) {

	params := []any{
		clickhouse.Named("landscapeToken", landscapeToken),
		clickhouse.Named("from", fromUnixNano),
		clickhouse.Named("to", toUnixNano),
		clickhouse.Named("commit", commitHash),
	}

	cs := []Comm{}

	err := r.Conn.Select(ctx, &cs, `
		// Find communications and aggregate metrics
		WITH comms AS (
			SELECT
				p.ExplorvizTelemetryKey AS SourceTelemetryKey,
				c.ExplorvizTelemetryKey AS TargetTelemetryKey,
				toFloat64(COUNT(c.SpanId)) AS RequestCount,
				toFloat64(COUNT(DISTINCT c.ExplorvizFuncName)) AS FunctionCount,
				toFloat64(sum(c.Duration)) AS ExecutionTime,
				min(c.Timestamp_ns) AS FromUnixNano,
				max(c.Timestamp_ns + c.Duration) AS ToUnixNano
			FROM otel_traces c
			INNER JOIN otel_traces p
				ON c.ParentSpanId = p.SpanId
				AND c.ExplorvizTokenId = p.ExplorvizTokenId
			WHERE
				c.ExplorvizTokenId = @landscapeToken
				AND c.Timestamp_ns >= @from
				AND c.Timestamp_ns < @to
				AND coalesce(c.SpanAttributes['vcs.ref.head.revision'], '') = @commit
			GROUP BY
				c.ExplorvizTelemetryKey, p.ExplorvizTelemetryKey
		)

		// Combine backwards and forwards calls into bidirectional communication
		SELECT
			a.SourceTelemetryKey AS SourceTelemetryKey,
			a.TargetTelemetryKey AS TargetTelemetryKey,
			b.SourceTelemetryKey != '' AS Bidirectional,
			least(a.FromUnixNano, coalesce(nullIf(b.FromUnixNano, 0), a.FromUnixNano)) AS FromUnixNano,
			greatest(a.ToUnixNano, coalesce(nullIf(b.ToUnixNano, 0), a.ToUnixNano)) AS ToUnixNano,
			map (
				'requestCount', a.RequestCount + b.RequestCount,
				'functionCount', a.FunctionCount + b.FunctionCount,
				'executionTime', a.ExecutionTime + b.ExecutionTime
			) AS Metrics
		FROM comms a
		LEFT JOIN comms b
			ON a.SourceTelemetryKey = b.TargetTelemetryKey
			AND a.TargetTelemetryKey = b.SourceTelemetryKey
		WHERE
			a.SourceTelemetryKey <= a.TargetTelemetryKey
			OR b.SourceTelemetryKey = '';
	`, params...)
	if err != nil {
		return CommSummary{}, err
	}

	if len(cs) == 0 {
		return CommSummary{
			Comms:          []Comm{},
			FromUnixNano:   0,
			ToUnixNano:     0,
			MetricsSummary: map[string]MetricRange{},
		}, nil
	}

	var from int64 = math.MaxInt64
	var to int64 = math.MinInt64
	ms := make(map[string]MetricRange)
	for i, c := range cs {
		cs[i].ID = c.SourceTelemetryKey + "-" + c.TargetTelemetryKey
		cs[i].Name = c.SourceTelemetryKey + " - " + c.TargetTelemetryKey
		from = min(cs[i].FromUnixNano, from)
		to = max(cs[i].ToUnixNano, to)
		for k, v := range c.Metrics {
			if m, ok := ms[k]; ok {
				m.Min = min(m.Min, v)
				m.Max = max(m.Max, v)
				ms[k] = m
			} else {
				ms[k] = MetricRange{
					Min: v,
					Max: v,
				}
			}
		}
	}

	return CommSummary{
		Comms:          cs,
		FromUnixNano:   from,
		ToUnixNano:     to,
		MetricsSummary: ms,
	}, nil
}
