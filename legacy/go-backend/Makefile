BINARY := chargeschare-api
MODULE  := github.com/chargeschare/backend

.PHONY: build run test migrate-up migrate-down docker-up docker-down lint tidy

build:
	go build -o $(BINARY) ./cmd/api

run:
	go run ./cmd/api/main.go

test:
	go test ./...

tidy:
	go mod tidy

migrate-up:
	migrate -path internal/db/migrations -database "$(DATABASE_URL)" up

migrate-down:
	migrate -path internal/db/migrations -database "$(DATABASE_URL)" down

docker-up:
	docker compose up -d

docker-down:
	docker compose down

lint:
	golangci-lint run ./...

vet:
	go vet ./...
