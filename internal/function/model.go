package function

// A FunctionCall gives detailed information about communication representing a function execution.
type FunctionCall struct {
	ID       string `json:"id"`
	FuncName string `json:"name"`

	// The function call is considered forward if the source entity calls a function from the target entity.
	// This distinction is only meaningful for bidirectional communication, as otherwise all function calls will be forward.
	IsForward bool `json:"isForward"`

	// Number of times this function was called in the given time range.
	CallCount uint64 `json:"callCount"`

	// Sum of durations for all calls of this function.
	ExecutionTime uint64 `json:"executionTime"`
}

type funcRequest struct {
	SourceTelemetryKey string `json:"source"`
	TargetTelemetryKey string `json:"target"`
}
