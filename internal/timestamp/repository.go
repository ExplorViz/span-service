package timestamp

import (
	"context"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/ClickHouse/clickhouse-go/v2/lib/driver"
)

type Repository struct {
	Conn driver.Conn
}

// findTimestamps searches the database for points in time at which spans belonging to the landscape identified by landscapeToken start.
// The resulting points in time are grouped into buckets of size bucketSizeNano. The searched time interval can be further restricted
// by specifying the newest and/or oldest already known timestamp. To restrict search for spans to those associated with a specific commit,
// the commitHash value can be used. If left empty, then the search is explicitly restricted to spans that have no associated commit.
func (r *Repository) findTimestamps(
	ctx context.Context, landscapeToken string, newestUnixNano uint64, oldestUnixNano uint64, bucketSizeNano uint64, commitHash string,
) ([]Timestamp, error) {

	params := []any{
		clickhouse.Named("landscapeToken", landscapeToken),
		clickhouse.Named("bucketSize", bucketSizeNano),
		clickhouse.Named("newest", newestUnixNano),
		clickhouse.Named("oldest", oldestUnixNano),
		clickhouse.Named("commit", commitHash),
	}

	ts := []Timestamp{}

	err := r.Conn.Select(ctx, &ts, `
		WITH
			intDiv(Timestamp_ns, @bucketSize) * @bucketSize AS bucket
		SELECT
			bucket AS EpochNano,
			count() AS SpanCount
		FROM otel_traces
		WHERE
			ExplorvizTokenId = @landscapeToken
			AND (Timestamp_ns >= @newest + @bucketSize OR Timestamp_ns < @oldest)
			AND coalesce(SpanAttributes['vcs.ref.head.revision'], '') = @commit
		GROUP BY bucket
		ORDER BY bucket;
	`, params...)
	if err != nil {
		return nil, err
	}

	return ts, nil
}
