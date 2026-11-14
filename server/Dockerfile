FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/rf-api ./cmd/server

FROM alpine:3.20
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /out/rf-api /app/rf-api
RUN mkdir -p /app/data && chown -R app:app /app
USER app
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["/app/rf-api"]
