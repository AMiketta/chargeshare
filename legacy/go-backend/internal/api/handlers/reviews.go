package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/pkg/apperror"
)

// ReviewsHandler handles review endpoints.
type ReviewsHandler struct {
	db       *pgxpool.Pool
	validate *validator.Validate
	log      *zap.Logger
}

// NewReviewsHandler creates a new ReviewsHandler.
func NewReviewsHandler(db *pgxpool.Pool, validate *validator.Validate, log *zap.Logger) *ReviewsHandler {
	return &ReviewsHandler{db: db, validate: validate, log: log}
}

// Create handles POST /api/v1/reviews
func (h *ReviewsHandler) Create(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	var req models.CreateReviewRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	bookingID, err := uuid.Parse(req.BookingID)
	if err != nil {
		apperror.BadRequest("invalid booking_id").Write(w)
		return
	}

	ctx := r.Context()

	// Verify the booking belongs to the user and is completed.
	var wallboxID uuid.UUID
	var status string
	err = h.db.QueryRow(ctx,
		`SELECT wallbox_id, status FROM bookings WHERE id = $1 AND driver_id = $2`,
		bookingID, userID,
	).Scan(&wallboxID, &status)
	if err != nil {
		apperror.NotFound("booking not found or not yours").Write(w)
		return
	}
	if status != "completed" {
		apperror.BadRequest("can only review completed bookings").Write(w)
		return
	}

	var review models.Review
	err = h.db.QueryRow(ctx,
		`INSERT INTO reviews (booking_id, wallbox_id, author_id, rating, comment)
		 VALUES ($1, $2, $3, $4, $5)
		 RETURNING id, booking_id, wallbox_id, author_id, rating, comment, created_at`,
		bookingID, wallboxID, userID, req.Rating, req.Comment,
	).Scan(
		&review.ID, &review.BookingID, &review.WallboxID, &review.AuthorID,
		&review.Rating, &review.Comment, &review.CreatedAt,
	)
	if err != nil {
		h.log.Error("insert review", zap.Error(err))
		apperror.Internal("could not create review").Write(w)
		return
	}

	// Update wallbox rating aggregate.
	h.db.Exec(ctx,
		`UPDATE wallboxes
		    SET rating_count = rating_count + 1,
		        rating_avg   = (rating_avg * rating_count + $2) / (rating_count + 1),
		        updated_at   = NOW()
		  WHERE id = $1`,
		wallboxID, req.Rating,
	)

	apperror.WriteJSON(w, http.StatusCreated, review)
}

// ListForWallbox handles GET /api/v1/wallboxes/:id/reviews
func (h *ReviewsHandler) ListForWallbox(w http.ResponseWriter, r *http.Request) {
	wallboxID, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid wallbox id").Write(w)
		return
	}

	limit := 20
	offset := 0
	if v := r.URL.Query().Get("limit"); v != "" {
		if i, err := strconv.Atoi(v); err == nil && i > 0 && i <= 100 {
			limit = i
		}
	}
	if v := r.URL.Query().Get("offset"); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			offset = i
		}
	}

	var total int
	h.db.QueryRow(r.Context(),
		`SELECT COUNT(*) FROM reviews WHERE wallbox_id = $1`, wallboxID,
	).Scan(&total)

	rows, err := h.db.Query(r.Context(),
		`SELECT r.id, r.booking_id, r.wallbox_id, r.author_id, r.rating, r.comment, r.created_at,
		        u.name, u.avatar_url
		   FROM reviews r
		   JOIN users u ON u.id = r.author_id
		  WHERE r.wallbox_id = $1
		  ORDER BY r.created_at DESC
		  LIMIT $2 OFFSET $3`,
		wallboxID, limit, offset,
	)
	if err != nil {
		h.log.Error("list reviews", zap.Error(err))
		apperror.Internal("could not list reviews").Write(w)
		return
	}
	defer rows.Close()

	reviews := make([]*models.Review, 0)
	for rows.Next() {
		var rev models.Review
		var authorName string
		var authorAvatar *string
		err := rows.Scan(
			&rev.ID, &rev.BookingID, &rev.WallboxID, &rev.AuthorID,
			&rev.Rating, &rev.Comment, &rev.CreatedAt,
			&authorName, &authorAvatar,
		)
		if err != nil {
			h.log.Error("scan review", zap.Error(err))
			continue
		}
		rev.Author = &models.User{Name: authorName, AvatarURL: authorAvatar}
		reviews = append(reviews, &rev)
	}

	apperror.WriteJSON(w, http.StatusOK, models.ReviewListResponse{
		Reviews: reviews,
		Total:   total,
		Limit:   limit,
		Offset:  offset,
	})
}
