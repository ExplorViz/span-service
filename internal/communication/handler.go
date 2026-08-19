package communication

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
	mux.HandleFunc("GET /v3/landscapes/{landscapeToken}/communication", h.getComm)
}

func (h *Handler) getComm(w http.ResponseWriter, r *http.Request) {
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

	cs, err := h.repo.findCommunication(r.Context(), lt, from, to, commit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if err := json.NewEncoder(w).Encode(cs); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}
