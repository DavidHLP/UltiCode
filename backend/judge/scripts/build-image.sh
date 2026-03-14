#!/bin/bash
# Build the judge Docker image
# Usage: ./build-image.sh [tag]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"
IMAGE_NAME="${1:-ulti-judge:latest}"

echo "Building judge Docker image: $IMAGE_NAME"
echo "Context: $BACKEND_DIR"

cd "$BACKEND_DIR"

# Build the image
docker build \
    -f judge/Dockerfile.judge \
    -t "$IMAGE_NAME" \
    --build-arg NODE_ENV=production \
    .

if [ $? -eq 0 ]; then
    echo "Successfully built image: $IMAGE_NAME"
    echo ""
    echo "To test the image:"
    echo "  docker run --rm -it $IMAGE_NAME bash"
else
    echo "Failed to build image"
    exit 1
fi
