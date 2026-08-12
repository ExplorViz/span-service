package trace

import (
	"encoding/json"
	"fmt"
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
	mux.HandleFunc("GET /v3/landscapes/{landscapeToken}/entities/{vizObjectId}/spans", h.getEntitySpans)
	mux.HandleFunc("POST /v3/landscapes/{landscapeToken}/communication/spans", h.getCommunicationSpans)
	mux.HandleFunc("DELETE /v3/landscapes/{landscapeToken}/trace-data", h.deleteTraceData)
}

func (h *Handler) getEntitySpans(w http.ResponseWriter, r *http.Request) {
	lt := r.PathValue("landscapeToken")
	if lt == "" {
		http.Error(w, "Missing or invalid landscape token in path parameter", http.StatusBadRequest)
		return
	}

	vizObjectID := r.PathValue("vizObjectId")
	if vizObjectID == "" {
		http.Error(w, "Missing or invalid visualization object ID in path parameter", http.StatusBadRequest)
		return
	}

	from, err := strconv.ParseUint(r.URL.Query().Get("from"), 10, 64)
	if err != nil {
		from = 0
	}

	to, err := strconv.ParseUint(r.URL.Query().Get("to"), 10, 64)
	if err != nil {
		to = math.MaxUint64
	}

	commit := r.URL.Query().Get("commit")

	limit, err := strconv.ParseUint(r.URL.Query().Get("limit"), 10, 64)
	if err != nil {
		limit = 0
	}

	offset, err := strconv.ParseUint(r.URL.Query().Get("offset"), 10, 64)
	if err != nil {
		offset = 0
	}

	var s []Span
	if s, err = h.repo.findEntitySpans(r.Context(), lt, vizObjectID, from, to, commit, limit, offset); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if err := json.NewEncoder(w).Encode(s); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}

func (h *Handler) getCommunicationSpans(w http.ResponseWriter, r *http.Request) {
	lt := r.PathValue("landscapeToken")
	if lt == "" {
		http.Error(w, "Missing or invalid landscape token in path parameter", http.StatusBadRequest)
		return
	}

	from, err := strconv.ParseUint(r.URL.Query().Get("from"), 10, 64)
	if err != nil {
		from = 0
	}

	to, err := strconv.ParseUint(r.URL.Query().Get("to"), 10, 64)
	if err != nil {
		to = math.MaxUint64
	}

	commit := r.URL.Query().Get("commit")

	limit, err := strconv.ParseUint(r.URL.Query().Get("limit"), 10, 64)
	if err != nil {
		limit = 0
	}

	offset, err := strconv.ParseUint(r.URL.Query().Get("offset"), 10, 64)
	if err != nil {
		offset = 0
	}

	var sreqs []commSpansRequest
	if err := json.NewDecoder(r.Body).Decode(&sreqs); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	for _, sreq := range sreqs {
		if sreq.SourceVizObjectId == "" || sreq.TargetVizObjectId == "" {
			http.Error(w, "A request object is missing source or target visualization object ID", http.StatusBadRequest)
			return
		}
	}

	var cs CommSpans
	if len(sreqs) == 0 {
		cs = CommSpans{
			Spans: map[string]Span{},
			Pairs: []SpanPair{},
		}
	} else {
		if cs, err = h.repo.findCommunicationSpans(r.Context(), lt, sreqs, from, to, commit, limit, offset); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	if err := json.NewEncoder(w).Encode(cs); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}

func (h *Handler) deleteTraceData(w http.ResponseWriter, r *http.Request) {
	lt := r.PathValue("landscapeToken")
	if lt == "" {
		http.Error(w, "Missing or invalid landscape token in path parameter", http.StatusBadRequest)
		return
	}

	if err := h.repo.deleteAll(r.Context(), lt); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if err := json.NewEncoder(w).Encode(fmt.Sprintf("Trace data successfully deleted for landscape %s", lt)); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}
