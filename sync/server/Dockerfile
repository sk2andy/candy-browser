# syntax=docker/dockerfile:1.7
FROM golang:1.27.1-alpine3.23 AS build

WORKDIR /src
RUN apk add --no-cache build-base
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY . .

FROM build AS test
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go test -race -count=1 ./... && go vet ./...
CMD ["/bin/true"]

FROM build AS binary
ARG VERSION=dev
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build \
      -trimpath \
      -buildvcs=false \
      -ldflags="-s -w -buildid= -X main.version=${VERSION}" \
      -o /out/candy-sync \
      ./cmd/candy-sync

FROM alpine:3.23 AS runtime
RUN addgroup -S -g 65532 candy \
    && adduser -S -D -H -u 65532 -G candy candy \
    && mkdir -p /data \
    && chown 65532:65532 /data
COPY --from=binary /out/candy-sync /usr/local/bin/candy-sync

USER 65532:65532
EXPOSE 8080
VOLUME ["/data"]
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD ["/usr/local/bin/candy-sync", "healthcheck", "http://127.0.0.1:8080/healthz"]
ENTRYPOINT ["/usr/local/bin/candy-sync"]
