package apperror

import (
	"encoding/json"
	"net/http"
)

// Error codes used across the API.
const (
	CodeUnauthorized    = "UNAUTHORIZED"
	CodeForbidden       = "FORBIDDEN"
	CodeNotFound        = "NOT_FOUND"
	CodeValidation      = "VALIDATION_ERROR"
	CodeConflict        = "CONFLICT"
	CodeInternalError   = "INTERNAL_ERROR"
	CodeBadRequest      = "BAD_REQUEST"
	CodePaymentRequired = "PAYMENT_REQUIRED"
)

// AppError is a structured API error.
type AppError struct {
	HTTPStatus int    `json:"-"`
	Code       string `json:"code"`
	Message    string `json:"error"`
}

// Error implements the error interface.
func (e *AppError) Error() string {
	return e.Message
}

// New creates a new AppError.
func New(httpStatus int, code, message string) *AppError {
	return &AppError{
		HTTPStatus: httpStatus,
		Code:       code,
		Message:    message,
	}
}

// Common constructors.

func Unauthorized(message string) *AppError {
	return New(http.StatusUnauthorized, CodeUnauthorized, message)
}

func Forbidden(message string) *AppError {
	return New(http.StatusForbidden, CodeForbidden, message)
}

func NotFound(message string) *AppError {
	return New(http.StatusNotFound, CodeNotFound, message)
}

func ValidationError(message string) *AppError {
	return New(http.StatusUnprocessableEntity, CodeValidation, message)
}

func Conflict(message string) *AppError {
	return New(http.StatusConflict, CodeConflict, message)
}

func Internal(message string) *AppError {
	return New(http.StatusInternalServerError, CodeInternalError, message)
}

func BadRequest(message string) *AppError {
	return New(http.StatusBadRequest, CodeBadRequest, message)
}

// Write writes the error as a JSON response.
func (e *AppError) Write(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(e.HTTPStatus)
	_ = json.NewEncoder(w).Encode(e)
}

// WriteError writes an error as a JSON response, handling both AppError and generic errors.
func WriteError(w http.ResponseWriter, err error) {
	var appErr *AppError
	var ok bool

	if appErr, ok = err.(*AppError); !ok {
		appErr = Internal("an unexpected error occurred")
	}
	appErr.Write(w)
}

// WriteJSON writes a JSON response with the given status code.
func WriteJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
