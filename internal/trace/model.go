package trace

// A Span is a detailed representation of an OpenTelemetry span.
type Span struct {
	SpanID       string `json:"spanId"`
	TraceID      string `json:"traceId"`
	ParentSpanID string `json:"parentSpanId,omitempty"`
	Name         string `json:"name"`
	Kind         string `json:"kind"`

	StartUnixNano int64 `json:"startUnixNano,string"`
	EndUnixNano   int64 `json:"endUnixNano,string"`

	SpanAttribs     map[string]string `json:"spanAttributes"`
	ResourceAttribs map[string]string `json:"resourceAttributes"`
}

// A SpanPair contains the span IDs of two spans that are in a parent-child relationship.
type SpanPair struct {
	ParentSpanID string `json:"parentSpanId"`
	ChildSpanID  string `json:"childSpanId"`
}

// A CommSpans is used for exchanging detailed span information
// on the spans from which a particular communication was derived.
type CommSpans struct {
	// Maps the ID of a span to the detailed span information.
	Spans map[string]Span `json:"spans"`

	// Contains each parent-child span pair from which the communication was derived.
	Pairs []SpanPair `json:"pairs"`
}

// A commSpansRequest represents a pair of visualization objects for which
// detailed information of spans involved in their communication should be retrieved.
type commSpansRequest struct {
	SourceVizObjectId string `json:"source"`
	TargetVizObjectId string `json:"target"`
}
