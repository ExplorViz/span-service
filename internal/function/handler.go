package function

import (
	"encoding/json"
	"math"
	"net/http"
	"strconv"
)

type Handler struct {
	repo Repository
}

func NewHandler(r Repository) Handler {
	return Handler{
		repo: r,
	}
}

func (h *Handler) Register(mux *http.ServeMux) {
	mux.HandleFunc("POST /v3/landscapes/{landscapeToken}/communication/functions", h.getFuncs)
}

func (h *Handler) getFuncs(w http.ResponseWriter, r *http.Request) {
	lt := r.PathValue("landscapeToken")
	if lt == "" {
		http.Error(w, "Missing or invalid landscape token in path parameter", http.StatusBadRequest)
		return
	}

	query := r.URL.Query()

	from, err := strconv.ParseUint(query.Get("from"), 10, 64)
	if err != nil {
		from = 0
	}

	to, err := strconv.ParseUint(query.Get("to"), 10, 64)
	if err != nil {
		to = math.MaxUint64
	}

	commit := query.Get("commit")

	var freqs []funcRequest
	if err := json.NewDecoder(r.Body).Decode(&freqs); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if len(freqs) == 0 {
		http.Error(w, "Received empty or invalid request body", http.StatusBadRequest)
		return
	}

	for _, freq := range freqs {
		if freq.SourceTelemetryKey == "" || freq.TargetTelemetryKey == "" {
			http.Error(w, "A request object is missing source or target visualization object ID", http.StatusBadRequest)
			return
		}
	}

	funcs, err := h.repo.findCommFunctions(r.Context(), lt, freqs, from, to, commit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if err := json.NewEncoder(w).Encode(funcs); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}
