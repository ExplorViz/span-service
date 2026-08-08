package trace

import (
	"context"
	"strconv"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/ClickHouse/clickhouse-go/v2/lib/driver"
)

type Repository struct {
	Conn driver.Conn
}

// findEntitySpans searches the database for any spans starting within the time span given by fromUnixNano (inclusive) and toUnixNano (exclusive)
// where the span belongs to the visualization object with the specified ID. To restrict search for spans to those associated with a specific commit,
// the commitHash value can be used. If left empty, then the search is explicitly restricted to spans that have no associated commit. A limit and an
// offset can optionally be specified for pagination.
func (r *Repository) findEntitySpans(
	ctx context.Context, landscapeToken string, vizObjectID string, fromUnixNano uint64, toUnixNano uint64, commitHash string, limit uint64, offset uint64,
) ([]Span, error) {
	params := []any{
		clickhouse.Named("landscapeToken", landscapeToken),
		clickhouse.Named("vizObjectId", vizObjectID),
		clickhouse.Named("from", fromUnixNano),
		clickhouse.Named("to", toUnixNano),
		clickhouse.Named("commit", commitHash),
	}

	queryLimit := ""
	if limit > 0 {
		queryLimit = "LIMIT " + strconv.FormatUint(limit, 10)
	}
	if offset > 0 {
		queryLimit += " OFFSET " + strconv.FormatUint(offset, 10)
	}

	spans := []Span{}

	err := r.Conn.Select(ctx, &spans, `
		SELECT
			SpanId AS SpanID,
			TraceId AS TraceID,
			ParentSpanId AS ParentSpanID,
			SpanName AS Name,
			SpanKind AS Kind,
			Timestamp_ns AS StartUnixNano,
			Timestamp_ns + Duration AS EndUnixNano,
			SpanAttributes AS SpanAttribs,
			ResourceAttributes AS ResourceAttribs
		FROM otel_traces
		WHERE
			ExplorvizTokenId = @landscapeToken
			AND ExplorvizVizObjectId = @vizObjectId
			AND Timestamp_ns >= @from
			AND Timestamp_ns <= @to
			AND coalesce(SpanAttributes['vcs.ref.head.revision'], '') = @commit
		ORDER BY Timestamp_ns ASC
		`+queryLimit, params...)
	if err != nil {
		return []Span{}, err
	}

	return spans, nil
}

// findCommunicationSpans searches the database for any spans starting within the time span given by fromUnixNano (inclusive) and toUnixNano (exclusive)
// where the span has a parent span such that the span pair's visualization object IDs match any one of the provided [commSpansRequest]s. To restrict search
// for spans to those associated with a specific commit, the commitHash value can be used. If left empty, then the search is explicitly restricted to spans
// that have no associated commit. A limit and an offset can optionally be specified for pagination.
func (r *Repository) findCommunicationSpans(
	ctx context.Context, landscapeToken string, sreqs []commSpansRequest, fromUnixNano uint64, toUnixNano uint64, commitHash string, limit uint64, offset uint64,
) (CommSpans, error) {

	comms := make([]clickhouse.GroupSet, len(sreqs))
	for i, sreq := range sreqs {
		comms[i] = clickhouse.GroupSet{Value: []any{
			min(sreq.SourceVizObjectId, sreq.TargetVizObjectId),
			max(sreq.SourceVizObjectId, sreq.TargetVizObjectId),
		}}
	}

	params := []any{
		clickhouse.Named("landscapeToken", landscapeToken),
		clickhouse.Named("comms", comms),
		clickhouse.Named("from", fromUnixNano),
		clickhouse.Named("to", toUnixNano),
		clickhouse.Named("commit", commitHash),
	}

	queryLimit := ""
	if limit > 0 {
		queryLimit = "LIMIT " + strconv.FormatUint(limit, 10)
	}
	if offset > 0 {
		queryLimit += " OFFSET " + strconv.FormatUint(offset, 10)
	}

	spans := []Span{}

	err := r.Conn.Select(ctx, &spans, `
		WITH comms AS (
			SELECT
				c.TraceId AS TraceId,

				c.SpanId AS ChildSpanId,
				c.SpanName AS ChildSpanName,
				c.SpanKind AS ChildSpanKind,
				c.Timestamp_ns AS ChildStartTime,
				c.Timestamp_ns + c.Duration AS ChildEndTime,
				c.SpanAttributes AS ChildSpanAttributes,
				c.ResourceAttributes AS ChildResourceAttributes,

				p.SpanId AS ParentSpanId,
				p.ParentSpanId AS ParentParentSpanId,
				p.SpanName AS ParentSpanName,
				p.SpanKind AS ParentSpanKind,
				p.Timestamp_ns AS ParentStartTime,
				p.Timestamp_ns + p.Duration AS ParentEndTime,
				p.SpanAttributes AS ParentSpanAttributes,
				p.ResourceAttributes AS ParentResourceAttributes
			FROM otel_traces c
			INNER JOIN otel_traces p
				ON c.ParentSpanId = p.SpanId
				AND c.ExplorvizTokenId = p.ExplorvizTokenId
			WHERE
				c.ExplorvizTokenId = @landscapeToken
				AND (
					least(c.ExplorvizVizObjectId, p.ExplorvizVizObjectId),
					greatest(c.ExplorvizVizObjectId, p.ExplorvizVizObjectId)
				) IN (@comms)
				AND c.Timestamp_ns >= @from
				AND c.Timestamp_ns <= @to
				AND coalesce(c.SpanAttributes['vcs.ref.head.revision'], '') = @commit
		)

		SELECT
			TraceId AS TraceID,
			ChildSpanId AS SpanID,
			ParentSpanId AS ParentSpanID,
			ChildSpanName AS Name,
			ChildSpanKind AS Kind,
			ChildStartTime AS StartUnixNano,
			ChildEndTime AS EndUnixNano,
			ChildSpanAttributes AS SpanAttribs,
			ChildResourceAttributes AS ResourceAttribs
		FROM comms

		UNION DISTINCT

		SELECT
			TraceId AS TraceID,
			ParentSpanId AS SpanID,
			ParentParentSpanId AS ParentSpanID,
			ParentSpanName AS Name,
			ParentSpanKind AS Kind,
			ParentStartTime AS StartUnixNano,
			ParentEndTime AS EndUnixNano,
			ParentSpanAttributes AS SpanAttribs,
			ParentResourceAttributes AS ResourceAttribs
		FROM comms

		ORDER BY StartUnixNano ASC
		`+queryLimit, params...)
	if err != nil {
		return CommSpans{}, err
	}

	cs := CommSpans{
		Spans: make(map[string]Span, len(spans)),
		Pairs: make([]SpanPair, 0, len(spans)/2),
	}

	for _, span := range spans {
		cs.Spans[span.SpanID] = span
	}

	for _, span := range spans {
		if _, ok := cs.Spans[span.ParentSpanID]; ok {
			cs.Pairs = append(cs.Pairs, SpanPair{
				ParentSpanID: span.ParentSpanID,
				ChildSpanID:  span.SpanID,
			})
		}
	}

	return cs, nil
}

func (r Repository) deleteAll(ctx context.Context, landscapeToken string) error {
	return r.Conn.Exec(ctx, "DELETE FROM otel_traces WHERE ExplorvizTokenId = ?;", landscapeToken)
}
