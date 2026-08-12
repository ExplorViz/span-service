package communication

// A MetricRange records the minimum and maximum encountered values for a particular metric.
type MetricRange struct {
	Min float64 `json:"min"`
	Max float64 `json:"max"`
}

// A Comm represents a communication relationship between two entities derived from OpenTelemetry span parent-child relationships.
//
// A communication is inferred when a span produced by the source visualization object acts as the parent of a child span produced by the target object.
// Each unique source-target pair is represented by a single Comm instance, regardless of how many such span pairs exist.
type Comm struct {
	ID                string `json:"id"`
	Name              string `json:"name"`
	SourceVizObjectId string `json:"sourceEntityKey"`
	TargetVizObjectId string `json:"targetEntityKey"`

	// A Comm is considered bidirectional if communication in both directions is observed, meaning there exists
	// at least one span pair where the source entity's span is the parent of the span produced by the target entity,
	// and at least one span where the target entity's span is the parent of the span produced by the source entity.
	Bidirectional bool `json:"isBidirectional"`

	// Earliest nanosecond Unix epoch timestamp at which a span represented by this Comm begins.
	FromUnixNano int64 `json:"fromUnixNano,string"`

	// Latest nanosecond Unix epoch timestamp at which a span represented by this Comm ends.
	ToUnixNano int64 `json:"toUnixNano,string"`

	Metrics map[string]float64 `json:"metrics"`
}

// A CommSummary contains all communication for a particular visualization state.
type CommSummary struct {
	Comms []Comm `json:"communications"`

	// Minimum from-timestamp among all communications within this summary.
	FromUnixNano int64 `json:"fromUnixNano,string"`

	// Maximum to-timestamp among all communications within this summary.
	ToUnixNano int64 `json:"toUnixNano,string"`

	// Minimum and maximum value of metrics across all communications within this summary.
	MetricsSummary map[string]MetricRange `json:"metrics"`
}
