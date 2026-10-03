package ocpp

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	"github.com/chargeschare/backend/internal/services"
)

// MessageHandler dispatches OCPP 1.6 messages.
type MessageHandler struct {
	db  *pgxpool.Pool
	log *zap.Logger
}

// NewMessageHandler creates a new MessageHandler.
func NewMessageHandler(db *pgxpool.Pool, log *zap.Logger) *MessageHandler {
	return &MessageHandler{db: db, log: log}
}

// Dispatch parses and routes an incoming OCPP message, returning the serialised response.
func (h *MessageHandler) Dispatch(cp *ChargePoint, data []byte) ([]byte, error) {
	msg, err := UnmarshalOCPP(data)
	if err != nil {
		return nil, fmt.Errorf("parse ocpp message: %w", err)
	}

	if msg.TypeID != MessageTypeCall {
		// Ignore responses from chargepoints (they are in reply to our commands).
		return nil, nil
	}

	payloadBytes, _ := msg.Payload.(json.RawMessage)

	switch msg.Action {
	case "BootNotification":
		return h.handleBootNotification(cp, msg.UniqueID, payloadBytes)
	case "Heartbeat":
		return h.handleHeartbeat(msg.UniqueID)
	case "Authorize":
		return h.handleAuthorize(cp, msg.UniqueID, payloadBytes)
	case "StartTransaction":
		return h.handleStartTransaction(cp, msg.UniqueID, payloadBytes)
	case "StopTransaction":
		return h.handleStopTransaction(cp, msg.UniqueID, payloadBytes)
	case "MeterValues":
		return h.handleMeterValues(cp, msg.UniqueID, payloadBytes)
	case "StatusNotification":
		return h.handleStatusNotification(cp, msg.UniqueID, payloadBytes)
	default:
		h.log.Warn("unknown ocpp action", zap.String("action", msg.Action), zap.String("cp_id", cp.ID))
		errResp, _ := MarshalCallError(msg.UniqueID, "NotImplemented", fmt.Sprintf("action %s not implemented", msg.Action))
		return errResp, nil
	}
}

func (h *MessageHandler) handleBootNotification(cp *ChargePoint, uniqueID string, payload json.RawMessage) ([]byte, error) {
	var req BootNotificationRequest
	if err := json.Unmarshal(payload, &req); err != nil {
		return MarshalCallError(uniqueID, "FormationViolation", "invalid BootNotification payload")
	}

	cp.model = req.ChargePointModel
	cp.vendor = req.ChargePointVendor
	cp.registered = true

	// Update wallbox online status.
	_, err := h.db.Exec(context.Background(),
		`UPDATE wallboxes SET is_online = TRUE, updated_at = NOW() WHERE ocpp_id = $1`,
		cp.ID,
	)
	if err != nil {
		h.log.Error("update wallbox online status", zap.Error(err), zap.String("ocpp_id", cp.ID))
	}

	resp := BootNotificationResponse{
		CurrentTime: time.Now().UTC(),
		Interval:    300,
		Status:      RegistrationAccepted,
	}
	return MarshalCallResult(uniqueID, resp)
}

func (h *MessageHandler) handleHeartbeat(uniqueID string) ([]byte, error) {
	resp := HeartbeatResponse{CurrentTime: time.Now().UTC()}
	return MarshalCallResult(uniqueID, resp)
}

func (h *MessageHandler) handleAuthorize(cp *ChargePoint, uniqueID string, payload json.RawMessage) ([]byte, error) {
	var req AuthorizeRequest
	if err := json.Unmarshal(payload, &req); err != nil {
		return MarshalCallError(uniqueID, "FormationViolation", "invalid Authorize payload")
	}

	// For MVP: accept all valid RFID tags (could check against users table in production).
	h.log.Info("authorize request", zap.String("cp_id", cp.ID), zap.String("id_tag", req.IDTag))

	resp := AuthorizeResponse{
		IDTagInfo: IdTagInfo{Status: "Accepted"},
	}
	return MarshalCallResult(uniqueID, resp)
}

func (h *MessageHandler) handleStartTransaction(cp *ChargePoint, uniqueID string, payload json.RawMessage) ([]byte, error) {
	var req StartTransactionRequest
	if err := json.Unmarshal(payload, &req); err != nil {
		return MarshalCallError(uniqueID, "FormationViolation", "invalid StartTransaction payload")
	}

	ctx := context.Background()

	// Generate a transaction ID.
	txID, err := services.GenerateNumericID()
	if err != nil {
		return MarshalCallError(uniqueID, "InternalError", "could not generate transaction id")
	}

	// Find matching booking (wallbox with this ocpp_id, active/confirmed, overlapping now).
	var bookingID *string
	row := h.db.QueryRow(ctx,
		`SELECT b.id::text
		   FROM bookings b
		   JOIN wallboxes w ON w.id = b.wallbox_id
		  WHERE w.ocpp_id = $1
		    AND b.status IN ('confirmed','active')
		    AND b.start_time <= $2
		    AND b.end_time >= $2
		  LIMIT 1`,
		cp.ID, req.Timestamp,
	)
	var bID string
	if err := row.Scan(&bID); err == nil {
		bookingID = &bID
	}

	// Create charging session.
	now := time.Now().UTC()
	_, err = h.db.Exec(ctx,
		`INSERT INTO charging_sessions (booking_id, ocpp_id, transaction_id, meter_start, started_at)
		 VALUES ($1::uuid, $2, $3, $4, $5)`,
		bookingID, cp.ID, txID, req.MeterStart, now,
	)
	if err != nil {
		h.log.Error("insert charging session", zap.Error(err))
	}

	// Update booking to active.
	if bookingID != nil {
		_, err = h.db.Exec(ctx,
			`UPDATE bookings SET status = 'active', ocpp_transaction_id = $2, updated_at = NOW() WHERE id = $1::uuid`,
			*bookingID, txID,
		)
		if err != nil {
			h.log.Error("update booking to active", zap.Error(err))
		}
	}

	h.log.Info("transaction started",
		zap.String("cp_id", cp.ID),
		zap.Int("tx_id", txID),
		zap.Int("meter_start", req.MeterStart),
	)

	resp := StartTransactionResponse{
		TransactionID: txID,
		IDTagInfo:     IdTagInfo{Status: "Accepted"},
	}
	return MarshalCallResult(uniqueID, resp)
}

func (h *MessageHandler) handleStopTransaction(cp *ChargePoint, uniqueID string, payload json.RawMessage) ([]byte, error) {
	var req StopTransactionRequest
	if err := json.Unmarshal(payload, &req); err != nil {
		return MarshalCallError(uniqueID, "FormationViolation", "invalid StopTransaction payload")
	}

	ctx := context.Background()
	now := time.Now().UTC()

	// Update charging session.
	_, err := h.db.Exec(ctx,
		`UPDATE charging_sessions
		    SET meter_stop = $1, stopped_at = $2, stop_reason = $3
		  WHERE transaction_id = $4 AND ocpp_id = $5`,
		req.MeterStop, now, req.Reason, req.TransactionID, cp.ID,
	)
	if err != nil {
		h.log.Error("update charging session on stop", zap.Error(err))
	}

	// Calculate kWh and update booking.
	var meterStart int
	var bookingID *string
	row := h.db.QueryRow(ctx,
		`SELECT cs.meter_start, cs.booking_id::text
		   FROM charging_sessions cs
		  WHERE cs.transaction_id = $1 AND cs.ocpp_id = $2`,
		req.TransactionID, cp.ID,
	)
	if err := row.Scan(&meterStart, &bookingID); err == nil && bookingID != nil {
		actualKwh := float64(req.MeterStop-meterStart) / 1000.0

		// Get price_per_kwh for the booking.
		var pricePerKwh float64
		h.db.QueryRow(ctx,
			`SELECT price_per_kwh FROM bookings WHERE id = $1::uuid`,
			*bookingID,
		).Scan(&pricePerKwh)

		totalAmount := actualKwh * pricePerKwh

		_, err = h.db.Exec(ctx,
			`UPDATE bookings
			    SET status = 'completed', actual_kwh = $2, total_amount = $3, updated_at = NOW()
			  WHERE id = $1::uuid`,
			*bookingID, actualKwh, totalAmount,
		)
		if err != nil {
			h.log.Error("update booking on stop transaction", zap.Error(err))
		}
	}

	h.log.Info("transaction stopped",
		zap.String("cp_id", cp.ID),
		zap.Int("tx_id", req.TransactionID),
		zap.Int("meter_stop", req.MeterStop),
	)

	resp := StopTransactionResponse{}
	return MarshalCallResult(uniqueID, resp)
}

func (h *MessageHandler) handleMeterValues(cp *ChargePoint, uniqueID string, payload json.RawMessage) ([]byte, error) {
	// MVP: log and ignore.
	h.log.Debug("meter values received", zap.String("cp_id", cp.ID), zap.ByteString("payload", payload))
	resp := MeterValuesResponse{}
	return MarshalCallResult(uniqueID, resp)
}

func (h *MessageHandler) handleStatusNotification(cp *ChargePoint, uniqueID string, payload json.RawMessage) ([]byte, error) {
	var req StatusNotificationRequest
	if err := json.Unmarshal(payload, &req); err != nil {
		return MarshalCallError(uniqueID, "FormationViolation", "invalid StatusNotification payload")
	}

	isOnline := req.Status == "Available" || req.Status == "Preparing" || req.Status == "Charging"
	_, err := h.db.Exec(context.Background(),
		`UPDATE wallboxes SET is_online = $1, updated_at = NOW() WHERE ocpp_id = $2`,
		isOnline, cp.ID,
	)
	if err != nil {
		h.log.Error("update wallbox status", zap.Error(err))
	}

	h.log.Info("status notification",
		zap.String("cp_id", cp.ID),
		zap.String("status", req.Status),
		zap.Int("connector", req.ConnectorID),
	)

	resp := StatusNotificationResponse{}
	return MarshalCallResult(uniqueID, resp)
}
