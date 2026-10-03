package handlers

import (
	"context"
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/internal/services"
	"github.com/chargeschare/backend/pkg/apperror"
)

// WallboxHandler handles wallbox endpoints.
type WallboxHandler struct {
	db         *pgxpool.Pool
	wallboxSvc *services.WallboxService
	validate   *validator.Validate
	log        *zap.Logger
}

// NewWallboxHandler creates a new WallboxHandler.
func NewWallboxHandler(db *pgxpool.Pool, wallboxSvc *services.WallboxService, validate *validator.Validate, log *zap.Logger) *WallboxHandler {
	return &WallboxHandler{db: db, wallboxSvc: wallboxSvc, validate: validate, log: log}
}

// List handles GET /api/v1/wallboxes
func (h *WallboxHandler) List(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()

	filter := models.WallboxListFilter{
		RadiusM: 10_000, // default 10 km
		Limit:   20,
		Offset:  0,
	}

	if v := q.Get("lat"); v != "" {
		f, err := strconv.ParseFloat(v, 64)
		if err == nil {
			filter.Lat = &f
		}
	}
	if v := q.Get("lng"); v != "" {
		f, err := strconv.ParseFloat(v, 64)
		if err == nil {
			filter.Lng = &f
		}
	}
	if v := q.Get("radius"); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			filter.RadiusM = f
		}
	}
	if v := q.Get("connector"); v != "" {
		filter.ConnectorType = &v
	}
	if v := q.Get("min_power"); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			filter.MinPowerKW = &f
		}
	}
	if v := q.Get("max_price"); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			filter.MaxPriceKwh = &f
		}
	}
	if v := q.Get("limit"); v != "" {
		if i, err := strconv.Atoi(v); err == nil && i > 0 && i <= 100 {
			filter.Limit = i
		}
	}
	if v := q.Get("offset"); v != "" {
		if i, err := strconv.Atoi(v); err == nil && i >= 0 {
			filter.Offset = i
		}
	}

	wallboxes, err := h.listWallboxes(r.Context(), filter)
	if err != nil {
		h.log.Error("list wallboxes", zap.Error(err))
		apperror.Internal("could not list wallboxes").Write(w)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"wallboxes": wallboxes,
		"limit":     filter.Limit,
		"offset":    filter.Offset,
	})
}

// Create handles POST /api/v1/wallboxes
func (h *WallboxHandler) Create(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	var req models.CreateWallboxRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	// Defaults.
	minDur := 30
	maxDur := 480
	if req.MinDurationMin != nil {
		minDur = *req.MinDurationMin
	}
	if req.MaxDurationMin != nil {
		maxDur = *req.MaxDurationMin
	}

	availJSON, err := json.Marshal(req.Availability)
	if err != nil {
		apperror.Internal("invalid availability").Write(w)
		return
	}

	var wb models.Wallbox
	err = h.db.QueryRow(r.Context(),
		`INSERT INTO wallboxes (owner_id, name, address, city, postal_code, lat, lng,
		                        power_kw, connector_type, price_per_kwh,
		                        min_duration_min, max_duration_min, is_metered,
		                        ocpp_id, model, manufacturer, photos, availability)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18)
		 RETURNING id, owner_id, name, address, city, postal_code, lat, lng,
		           power_kw, connector_type, price_per_kwh, min_duration_min, max_duration_min,
		           is_online, is_metered, ocpp_id, model, manufacturer, photos,
		           availability, rating_avg, rating_count, created_at, updated_at`,
		userID, req.Name, req.Address, req.City, req.PostalCode, req.Lat, req.Lng,
		req.PowerKW, req.ConnectorType, req.PricePerKwh,
		minDur, maxDur, req.IsMetered,
		req.OcppID, req.Model, req.Manufacturer, req.Photos, availJSON,
	).Scan(
		&wb.ID, &wb.OwnerID, &wb.Name, &wb.Address, &wb.City, &wb.PostalCode,
		&wb.Lat, &wb.Lng, &wb.PowerKW, &wb.ConnectorType, &wb.PricePerKwh,
		&wb.MinDurationMin, &wb.MaxDurationMin, &wb.IsOnline, &wb.IsMetered,
		&wb.OcppID, &wb.Model, &wb.Manufacturer, &wb.Photos, &wb.Availability,
		&wb.RatingAvg, &wb.RatingCount, &wb.CreatedAt, &wb.UpdatedAt,
	)
	if err != nil {
		h.log.Error("insert wallbox", zap.Error(err))
		apperror.Internal("could not create wallbox").Write(w)
		return
	}
	apperror.WriteJSON(w, http.StatusCreated, wb)
}

// GetByID handles GET /api/v1/wallboxes/:id
func (h *WallboxHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}
	wb, err := h.fetchWallbox(r.Context(), id)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, wb)
}

// Update handles PUT /api/v1/wallboxes/:id
func (h *WallboxHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}
	userID, _ := uuid.Parse(mw.GetUserID(r.Context()))

	wb, err := h.fetchWallbox(r.Context(), id)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	if wb.OwnerID != userID && mw.GetUserRole(r.Context()) != "admin" {
		apperror.Forbidden("not the wallbox owner").Write(w)
		return
	}

	var req models.UpdateWallboxRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	var availJSON []byte
	if req.Availability != nil {
		availJSON, _ = json.Marshal(req.Availability)
	}

	ctx := r.Context()
	_, err = h.db.Exec(ctx,
		`UPDATE wallboxes
		    SET name            = COALESCE($2, name),
		        address         = COALESCE($3, address),
		        city            = COALESCE($4, city),
		        postal_code     = COALESCE($5, postal_code),
		        lat             = COALESCE($6, lat),
		        lng             = COALESCE($7, lng),
		        power_kw        = COALESCE($8, power_kw),
		        connector_type  = COALESCE($9::text, connector_type::text)::text,
		        price_per_kwh   = COALESCE($10, price_per_kwh),
		        min_duration_min= COALESCE($11, min_duration_min),
		        max_duration_min= COALESCE($12, max_duration_min),
		        is_metered      = COALESCE($13, is_metered),
		        ocpp_id         = COALESCE($14, ocpp_id),
		        model           = COALESCE($15, model),
		        manufacturer    = COALESCE($16, manufacturer),
		        availability    = COALESCE($17, availability),
		        updated_at      = NOW()
		  WHERE id = $1`,
		id,
		req.Name, req.Address, req.City, req.PostalCode,
		req.Lat, req.Lng, req.PowerKW, req.ConnectorType,
		req.PricePerKwh, req.MinDurationMin, req.MaxDurationMin,
		req.IsMetered, req.OcppID, req.Model, req.Manufacturer,
		jsonOrNil(availJSON),
	)
	if err != nil {
		h.log.Error("update wallbox", zap.Error(err))
		apperror.Internal("could not update wallbox").Write(w)
		return
	}

	wb, err = h.fetchWallbox(ctx, id)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, wb)
}

// Delete handles DELETE /api/v1/wallboxes/:id
func (h *WallboxHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}
	userID, _ := uuid.Parse(mw.GetUserID(r.Context()))

	wb, err := h.fetchWallbox(r.Context(), id)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	if wb.OwnerID != userID && mw.GetUserRole(r.Context()) != "admin" {
		apperror.Forbidden("not the wallbox owner").Write(w)
		return
	}

	_, err = h.db.Exec(r.Context(), `DELETE FROM wallboxes WHERE id = $1`, id)
	if err != nil {
		apperror.Internal("could not delete wallbox").Write(w)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// UpdateStatus handles PUT /api/v1/wallboxes/:id/status
func (h *WallboxHandler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}
	userID, _ := uuid.Parse(mw.GetUserID(r.Context()))

	wb, err := h.fetchWallbox(r.Context(), id)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	if wb.OwnerID != userID && mw.GetUserRole(r.Context()) != "admin" {
		apperror.Forbidden("not the wallbox owner").Write(w)
		return
	}

	var req models.WallboxStatusRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}

	_, err = h.db.Exec(r.Context(),
		`UPDATE wallboxes SET is_online = $2, updated_at = NOW() WHERE id = $1`,
		id, req.IsOnline,
	)
	if err != nil {
		apperror.Internal("could not update status").Write(w)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, map[string]bool{"is_online": req.IsOnline})
}

// GetAvailability handles GET /api/v1/wallboxes/:id/availability
func (h *WallboxHandler) GetAvailability(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}

	var availJSON []byte
	err = h.db.QueryRow(r.Context(),
		`SELECT availability FROM wallboxes WHERE id = $1`, id,
	).Scan(&availJSON)
	if err != nil {
		apperror.NotFound("wallbox not found").Write(w)
		return
	}

	var avail models.WeeklyAvailability
	if err := json.Unmarshal(availJSON, &avail); err != nil {
		apperror.Internal("invalid availability data").Write(w)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, avail)
}

// UpdateAvailability handles PUT /api/v1/wallboxes/:id/availability
func (h *WallboxHandler) UpdateAvailability(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}
	userID, _ := uuid.Parse(mw.GetUserID(r.Context()))

	wb, err := h.fetchWallbox(r.Context(), id)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	if wb.OwnerID != userID {
		apperror.Forbidden("not the wallbox owner").Write(w)
		return
	}

	var avail models.WeeklyAvailability
	if err := json.NewDecoder(r.Body).Decode(&avail); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}

	availJSON, err := json.Marshal(avail)
	if err != nil {
		apperror.Internal("could not encode availability").Write(w)
		return
	}

	_, err = h.db.Exec(r.Context(),
		`UPDATE wallboxes SET availability = $2, updated_at = NOW() WHERE id = $1`,
		id, availJSON,
	)
	if err != nil {
		apperror.Internal("could not update availability").Write(w)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, avail)
}

// GetSlots handles GET /api/v1/wallboxes/:id/slots?date=YYYY-MM-DD
func (h *WallboxHandler) GetSlots(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}

	dateStr := r.URL.Query().Get("date")
	if dateStr == "" {
		dateStr = time.Now().UTC().Format("2006-01-02")
	}
	date, err := time.Parse("2006-01-02", dateStr)
	if err != nil {
		apperror.BadRequest("invalid date format, use YYYY-MM-DD").Write(w)
		return
	}

	slots, err := h.wallboxSvc.GetAvailableSlots(r.Context(), id, date)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"date":  dateStr,
		"slots": slots,
	})
}

// fetchWallbox retrieves a single wallbox by ID.
func (h *WallboxHandler) fetchWallbox(ctx context.Context, id uuid.UUID) (*models.Wallbox, error) {
	var wb models.Wallbox
	var availJSON []byte
	err := h.db.QueryRow(ctx,
		`SELECT id, owner_id, name, address, city, postal_code, lat, lng,
		        power_kw, connector_type, price_per_kwh, min_duration_min, max_duration_min,
		        is_online, is_metered, ocpp_id, model, manufacturer, photos,
		        availability, rating_avg, rating_count, created_at, updated_at
		   FROM wallboxes WHERE id = $1`,
		id,
	).Scan(
		&wb.ID, &wb.OwnerID, &wb.Name, &wb.Address, &wb.City, &wb.PostalCode,
		&wb.Lat, &wb.Lng, &wb.PowerKW, &wb.ConnectorType, &wb.PricePerKwh,
		&wb.MinDurationMin, &wb.MaxDurationMin, &wb.IsOnline, &wb.IsMetered,
		&wb.OcppID, &wb.Model, &wb.Manufacturer, &wb.Photos, &availJSON,
		&wb.RatingAvg, &wb.RatingCount, &wb.CreatedAt, &wb.UpdatedAt,
	)
	if err != nil {
		return nil, apperror.NotFound("wallbox not found")
	}
	if availJSON != nil {
		_ = json.Unmarshal(availJSON, &wb.Availability)
	}
	return &wb, nil
}

// listWallboxes queries the DB with geo-search and filters.
func (h *WallboxHandler) listWallboxes(ctx context.Context, f models.WallboxListFilter) ([]*models.Wallbox, error) {
	query := `SELECT id, owner_id, name, address, city, postal_code, lat, lng,
	                 power_kw, connector_type, price_per_kwh, min_duration_min, max_duration_min,
	                 is_online, is_metered, ocpp_id, model, manufacturer, photos,
	                 availability, rating_avg, rating_count, created_at, updated_at`

	if f.Lat != nil && f.Lng != nil {
		query += `, earth_distance(ll_to_earth(lat, lng), ll_to_earth($1, $2)) AS distance_m`
	} else {
		query += `, NULL::float8 AS distance_m`
	}

	query += ` FROM wallboxes WHERE 1=1`

	args := []interface{}{}
	argIdx := 1

	if f.Lat != nil && f.Lng != nil {
		args = append(args, *f.Lat, *f.Lng)
		argIdx = 3
		query += ` AND earth_distance(ll_to_earth(lat, lng), ll_to_earth($1, $2)) < $3`
		args = append(args, f.RadiusM)
		argIdx++
	}

	if f.ConnectorType != nil {
		query += ` AND connector_type = $` + strconv.Itoa(argIdx)
		args = append(args, *f.ConnectorType)
		argIdx++
	}
	if f.MinPowerKW != nil {
		query += ` AND power_kw >= $` + strconv.Itoa(argIdx)
		args = append(args, *f.MinPowerKW)
		argIdx++
	}
	if f.MaxPriceKwh != nil {
		query += ` AND price_per_kwh <= $` + strconv.Itoa(argIdx)
		args = append(args, *f.MaxPriceKwh)
		argIdx++
	}

	query += ` ORDER BY`
	if f.Lat != nil && f.Lng != nil {
		query += ` distance_m ASC,`
	}
	query += ` created_at DESC`
	query += ` LIMIT $` + strconv.Itoa(argIdx) + ` OFFSET $` + strconv.Itoa(argIdx+1)
	args = append(args, f.Limit, f.Offset)

	rows, err := h.db.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []*models.Wallbox
	for rows.Next() {
		var wb models.Wallbox
		var availJSON []byte
		var dist *float64
		err := rows.Scan(
			&wb.ID, &wb.OwnerID, &wb.Name, &wb.Address, &wb.City, &wb.PostalCode,
			&wb.Lat, &wb.Lng, &wb.PowerKW, &wb.ConnectorType, &wb.PricePerKwh,
			&wb.MinDurationMin, &wb.MaxDurationMin, &wb.IsOnline, &wb.IsMetered,
			&wb.OcppID, &wb.Model, &wb.Manufacturer, &wb.Photos, &availJSON,
			&wb.RatingAvg, &wb.RatingCount, &wb.CreatedAt, &wb.UpdatedAt, &dist,
		)
		if err != nil {
			h.log.Error("scan wallbox", zap.Error(err))
			continue
		}
		if availJSON != nil {
			_ = json.Unmarshal(availJSON, &wb.Availability)
		}
		wb.Distance = dist
		results = append(results, &wb)
	}
	return results, nil
}

func jsonOrNil(data []byte) interface{} {
	if len(data) == 0 {
		return nil
	}
	return data
}
