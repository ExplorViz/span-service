package function

import (
	"context"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/ClickHouse/clickhouse-go/v2/lib/driver"
)

type Repository struct {
	Conn driver.Conn
}

// findCommFunctions searches the database for any function calls underlying the communication between a source and target visualization object.
// The searched time interval can be further restricted using fromUnixNano (inclusive) and toUnixNano (inclusive). To restrict search for spans
// to those associated with a specific commit, the commitHash value can be used. If left empty, then the search is explicitly restricted to spans
// that have no associated commit.
func (r Repository) findCommFunctions(
	ctx context.Context, landscapeToken string, freqs []funcRequest, fromUnixNano uint64, toUnixNano uint64, commitHash string,
) ([]FunctionCall, error) {

	comms := make([]clickhouse.GroupSet, len(freqs))
	for i, freq := range freqs {
		comms[i] = clickhouse.GroupSet{Value: []any{
			min(freq.SourceVizObjectId, freq.TargetVizObjectId),
			max(freq.SourceVizObjectId, freq.TargetVizObjectId),
		}}
	}

	params := []any{
		clickhouse.Named("landscapeToken", landscapeToken),
		clickhouse.Named("comms", comms),
		clickhouse.Named("from", fromUnixNano),
		clickhouse.Named("to", toUnixNano),
		clickhouse.Named("commit", commitHash),
	}

	fns := []FunctionCall{}

	err := r.Conn.Select(ctx, &fns, `
		SELECT
			c.ExplorvizEntityId AS ID,
			c.ExplorvizFuncName AS FuncName,
			(p.ExplorvizVizObjectId, c.ExplorvizVizObjectId) IN (@comms) AS IsForward,
			count() AS CallCount,
			sum(Duration) AS ExecutionTime
		FROM otel_traces c
		INNER JOIN otel_traces p
			ON c.ParentSpanId = p.SpanId
			AND c.ExplorvizTokenId = p.ExplorvizTokenId
		WHERE
			c.ExplorvizTokenId = @landscapeToken
			AND (
				(c.ExplorvizVizObjectId, p.ExplorvizVizObjectId) IN (@comms)
				OR (p.ExplorvizVizObjectId, c.ExplorvizVizObjectId) IN (@comms)
			)
			AND c.Timestamp_ns >= @from
			AND c.Timestamp_ns <= @to
			AND coalesce(c.SpanAttributes['vcs.ref.head.revision'], '') = @commit
		GROUP BY c.ExplorvizEntityId, c.ExplorvizVizObjectId, p.ExplorvizVizObjectId, c.ExplorvizFuncName;
	`, params...)
	if err != nil {
		return []FunctionCall{}, err
	}

	return fns, nil
}
