#!/usr/bin/env bash
set -e

WIREMOCK_URL="https://subject-access-request-mock-service-dev.hmpps.service.justice.gov.uk/__admin"
REPO_ROOT="$(git rev-parse --show-toplevel)"
WIREMOCK_DIR="$REPO_ROOT/wiremock"

# Upload all static files
echo "Uploading static files..."
find "$WIREMOCK_DIR/__files" -type f | while read -r filepath; do
  filename="${filepath#$WIREMOCK_DIR/__files/}"
  echo "Uploading $filename"
  curl -s -k -o /dev/null -w "%{http_code} - $filename\n" \
    -X PUT "$WIREMOCK_URL/files/$filename" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@$filepath"
done

# Upload all mapping files
echo "Uploading mappings in $WIREMOCK_DIR/mappings/*.json ..."
for file in "$WIREMOCK_DIR"/mappings/*.json; do
  if [ -f "$file" ]; then
    echo "Uploading $file"
    curl -s -k -o /dev/null -w "%{http_code} - $file\n" \
      -X POST "$WIREMOCK_URL/mappings" \
      -H "Content-Type: application/json" \
      --data-binary "@$file"
  fi
done
