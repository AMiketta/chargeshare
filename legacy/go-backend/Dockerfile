# Stage 1: Build
FROM golang:1.22-alpine AS builder

RUN apk add --no-cache git ca-certificates

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -ldflags="-w -s" -o chargeschare-api ./cmd/api

# Stage 2: Runtime
FROM alpine:latest

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

COPY --from=builder /app/chargeschare-api .
COPY --from=builder /app/internal/db/migrations ./internal/db/migrations

EXPOSE 8080

CMD ["./chargeschare-api"]
