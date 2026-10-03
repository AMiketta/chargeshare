package ocpp

import (
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"go.uber.org/zap"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  4096,
	WriteBufferSize: 4096,
	CheckOrigin: func(r *http.Request) bool {
		// Accept all origins for OCPP chargepoints.
		return true
	},
	Subprotocols: []string{"ocpp1.6"},
}

// ChargePoint represents a connected charging station.
type ChargePoint struct {
	ID         string
	conn       *websocket.Conn
	mu         sync.Mutex
	lastSeen   time.Time
	model      string
	vendor     string
	registered bool
}

// Send writes an OCPP message to the chargepoint.
func (cp *ChargePoint) Send(data []byte) error {
	cp.mu.Lock()
	defer cp.mu.Unlock()
	cp.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
	return cp.conn.WriteMessage(websocket.TextMessage, data)
}

// OCPPServer manages WebSocket connections from chargepoints.
type OCPPServer struct {
	mu       sync.RWMutex
	registry map[string]*ChargePoint
	handler  *MessageHandler
	log      *zap.Logger
}

// NewOCPPServer creates a new OCPPServer.
func NewOCPPServer(handler *MessageHandler, log *zap.Logger) *OCPPServer {
	return &OCPPServer{
		registry: make(map[string]*ChargePoint),
		handler:  handler,
		log:      log,
	}
}

// HandleWebSocket upgrades the connection and starts the read loop for a chargepoint.
func (s *OCPPServer) HandleWebSocket(w http.ResponseWriter, r *http.Request) {
	chargepointID := r.PathValue("chargepoint_id")
	if chargepointID == "" {
		http.Error(w, "missing chargepoint_id", http.StatusBadRequest)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		s.log.Error("websocket upgrade failed", zap.String("cp_id", chargepointID), zap.Error(err))
		return
	}

	cp := &ChargePoint{
		ID:       chargepointID,
		conn:     conn,
		lastSeen: time.Now(),
	}

	s.register(cp)
	defer s.deregister(chargepointID)

	s.log.Info("chargepoint connected", zap.String("cp_id", chargepointID))
	s.readLoop(cp)
}

// readLoop reads incoming OCPP messages and dispatches them.
func (s *OCPPServer) readLoop(cp *ChargePoint) {
	cp.conn.SetReadLimit(64 * 1024)
	cp.conn.SetReadDeadline(time.Now().Add(5 * time.Minute))
	cp.conn.SetPongHandler(func(string) error {
		cp.conn.SetReadDeadline(time.Now().Add(5 * time.Minute))
		return nil
	})

	for {
		_, data, err := cp.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure) {
				s.log.Warn("chargepoint connection error", zap.String("cp_id", cp.ID), zap.Error(err))
			}
			return
		}
		cp.lastSeen = time.Now()
		cp.conn.SetReadDeadline(time.Now().Add(5 * time.Minute))

		s.log.Debug("ocpp message received", zap.String("cp_id", cp.ID), zap.ByteString("data", data))

		response, err := s.handler.Dispatch(cp, data)
		if err != nil {
			s.log.Error("ocpp dispatch error", zap.String("cp_id", cp.ID), zap.Error(err))
			continue
		}
		if response != nil {
			if err := cp.Send(response); err != nil {
				s.log.Error("send response failed", zap.String("cp_id", cp.ID), zap.Error(err))
				return
			}
		}
	}
}

// register adds a chargepoint to the registry.
func (s *OCPPServer) register(cp *ChargePoint) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.registry[cp.ID] = cp
}

// deregister removes a chargepoint from the registry.
func (s *OCPPServer) deregister(id string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if cp, ok := s.registry[id]; ok {
		cp.conn.Close()
		delete(s.registry, id)
	}
	s.log.Info("chargepoint disconnected", zap.String("cp_id", id))
}

// GetChargePoint returns a chargepoint by ID.
func (s *OCPPServer) GetChargePoint(id string) (*ChargePoint, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	cp, ok := s.registry[id]
	return cp, ok
}

// SendRemoteStart sends a RemoteStartTransaction command to a chargepoint.
func (s *OCPPServer) SendRemoteStart(chargepointID string, connectorID int, idTag string) error {
	cp, ok := s.GetChargePoint(chargepointID)
	if !ok {
		return fmt.Errorf("chargepoint %s not connected", chargepointID)
	}

	uniqueID := uuid.New().String()
	payload := RemoteStartTransactionRequest{
		IDTag: idTag,
	}
	if connectorID > 0 {
		payload.ConnectorID = &connectorID
	}

	data, err := MarshalCall(uniqueID, "RemoteStartTransaction", payload)
	if err != nil {
		return err
	}
	return cp.Send(data)
}

// SendRemoteStop sends a RemoteStopTransaction command to a chargepoint.
func (s *OCPPServer) SendRemoteStop(chargepointID string, transactionID int) error {
	cp, ok := s.GetChargePoint(chargepointID)
	if !ok {
		return fmt.Errorf("chargepoint %s not connected", chargepointID)
	}

	uniqueID := uuid.New().String()
	payload := RemoteStopTransactionRequest{
		TransactionID: transactionID,
	}
	data, err := MarshalCall(uniqueID, "RemoteStopTransaction", payload)
	if err != nil {
		return err
	}
	return cp.Send(data)
}
