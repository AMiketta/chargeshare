package ocpp

import (
	"encoding/json"
	"fmt"
	"time"
)

// OCPP 1.6 message type identifiers.
const (
	MessageTypeCall       = 2
	MessageTypeCallResult = 3
	MessageTypeCallError  = 4
)

// RawMessage is a raw OCPP message envelope.
type RawMessage struct {
	TypeID      int
	UniqueID    string
	Action      string      // only for Call
	Payload     interface{} // raw payload
	ErrorCode   string      // only for CallError
	ErrorDesc   string
	ErrorDetail interface{}
}

// UnmarshalOCPP parses a raw OCPP JSON frame into a RawMessage.
func UnmarshalOCPP(data []byte) (*RawMessage, error) {
	var raw []json.RawMessage
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("unmarshal ocpp frame: %w", err)
	}
	if len(raw) < 3 {
		return nil, fmt.Errorf("invalid ocpp message length")
	}

	var typeID int
	if err := json.Unmarshal(raw[0], &typeID); err != nil {
		return nil, fmt.Errorf("parse type id: %w", err)
	}

	var uniqueID string
	if err := json.Unmarshal(raw[1], &uniqueID); err != nil {
		return nil, fmt.Errorf("parse unique id: %w", err)
	}

	msg := &RawMessage{
		TypeID:   typeID,
		UniqueID: uniqueID,
	}

	switch typeID {
	case MessageTypeCall:
		if len(raw) < 4 {
			return nil, fmt.Errorf("call message requires 4 elements")
		}
		if err := json.Unmarshal(raw[2], &msg.Action); err != nil {
			return nil, fmt.Errorf("parse action: %w", err)
		}
		msg.Payload = raw[3]
	case MessageTypeCallResult:
		msg.Payload = raw[2]
	case MessageTypeCallError:
		if len(raw) < 5 {
			return nil, fmt.Errorf("call error requires 5 elements")
		}
		if err := json.Unmarshal(raw[2], &msg.ErrorCode); err != nil {
			return nil, fmt.Errorf("parse error code: %w", err)
		}
		if err := json.Unmarshal(raw[3], &msg.ErrorDesc); err != nil {
			return nil, fmt.Errorf("parse error desc: %w", err)
		}
		msg.ErrorDetail = raw[4]
	}
	return msg, nil
}

// MarshalCallResult encodes a CallResult as OCPP JSON.
func MarshalCallResult(uniqueID string, payload interface{}) ([]byte, error) {
	return json.Marshal([]interface{}{MessageTypeCallResult, uniqueID, payload})
}

// MarshalCallError encodes a CallError as OCPP JSON.
func MarshalCallError(uniqueID, errorCode, errorDesc string) ([]byte, error) {
	return json.Marshal([]interface{}{MessageTypeCallError, uniqueID, errorCode, errorDesc, struct{}{}})
}

// MarshalCall encodes a Call as OCPP JSON.
func MarshalCall(uniqueID, action string, payload interface{}) ([]byte, error) {
	return json.Marshal([]interface{}{MessageTypeCall, uniqueID, action, payload})
}

// --- Request / Response types ---

// BootNotificationRequest is sent by a chargepoint on startup.
type BootNotificationRequest struct {
	ChargePointModel        string `json:"chargePointModel"`
	ChargePointVendor       string `json:"chargePointVendor"`
	ChargePointSerialNumber string `json:"chargePointSerialNumber,omitempty"`
	FirmwareVersion         string `json:"firmwareVersion,omitempty"`
}

// RegistrationStatus values for BootNotification response.
type RegistrationStatus string

const (
	RegistrationAccepted RegistrationStatus = "Accepted"
	RegistrationPending  RegistrationStatus = "Pending"
	RegistrationRejected RegistrationStatus = "Rejected"
)

// BootNotificationResponse is the backend's reply.
type BootNotificationResponse struct {
	CurrentTime time.Time          `json:"currentTime"`
	Interval    int                `json:"interval"`
	Status      RegistrationStatus `json:"status"`
}

// HeartbeatRequest is an empty keep-alive message.
type HeartbeatRequest struct{}

// HeartbeatResponse contains the current backend time.
type HeartbeatResponse struct {
	CurrentTime time.Time `json:"currentTime"`
}

// AuthorizeRequest asks the backend whether an ID tag is allowed to charge.
type AuthorizeRequest struct {
	IDTag string `json:"idTag"`
}

// IdTagInfo carries authorisation result.
type IdTagInfo struct {
	Status      string     `json:"status"` // "Accepted", "Blocked", "Expired", "Invalid"
	ExpiryDate  *time.Time `json:"expiryDate,omitempty"`
	ParentIDTag *string    `json:"parentIdTag,omitempty"`
}

// AuthorizeResponse is the reply to an Authorize call.
type AuthorizeResponse struct {
	IDTagInfo IdTagInfo `json:"idTagInfo"`
}

// StartTransactionRequest is sent when a chargepoint starts a session.
type StartTransactionRequest struct {
	ConnectorID   int       `json:"connectorId"`
	IDTag         string    `json:"idTag"`
	MeterStart    int       `json:"meterStart"`
	Timestamp     time.Time `json:"timestamp"`
	ReservationID *int      `json:"reservationId,omitempty"`
}

// StartTransactionResponse is the reply to StartTransaction.
type StartTransactionResponse struct {
	TransactionID int       `json:"transactionId"`
	IDTagInfo     IdTagInfo `json:"idTagInfo"`
}

// StopTransactionRequest is sent when a chargepoint stops a session.
type StopTransactionRequest struct {
	MeterStop     int       `json:"meterStop"`
	Timestamp     time.Time `json:"timestamp"`
	TransactionID int       `json:"transactionId"`
	Reason        string    `json:"reason,omitempty"`
	IDTag         *string   `json:"idTag,omitempty"`
}

// StopTransactionResponse is the reply to StopTransaction.
type StopTransactionResponse struct {
	IDTagInfo *IdTagInfo `json:"idTagInfo,omitempty"`
}

// MeterValuesRequest carries meter readings (MVP: log and ignore).
type MeterValuesRequest struct {
	ConnectorID   int         `json:"connectorId"`
	TransactionID *int        `json:"transactionId,omitempty"`
	MeterValue    interface{} `json:"meterValue,omitempty"`
}

// MeterValuesResponse is the empty reply to MeterValues.
type MeterValuesResponse struct{}

// StatusNotificationRequest is sent when connector status changes.
type StatusNotificationRequest struct {
	ConnectorID     int    `json:"connectorId"`
	ErrorCode       string `json:"errorCode"`
	Status          string `json:"status"` // "Available","Preparing","Charging","SuspendedEVSE",...
	Info            string `json:"info,omitempty"`
	VendorID        string `json:"vendorId,omitempty"`
	VendorErrorCode string `json:"vendorErrorCode,omitempty"`
}

// StatusNotificationResponse is the empty reply.
type StatusNotificationResponse struct{}

// RemoteStartTransactionRequest is sent by the backend to start a session.
type RemoteStartTransactionRequest struct {
	ConnectorID *int   `json:"connectorId,omitempty"`
	IDTag       string `json:"idTag"`
}

// RemoteStartTransactionResponse is the reply.
type RemoteStartTransactionResponse struct {
	Status string `json:"status"` // "Accepted" or "Rejected"
}

// RemoteStopTransactionRequest is sent by the backend to stop a session.
type RemoteStopTransactionRequest struct {
	TransactionID int `json:"transactionId"`
}

// RemoteStopTransactionResponse is the reply.
type RemoteStopTransactionResponse struct {
	Status string `json:"status"`
}
