#!/usr/bin/env bash
# Build-time retrieval only; the installed app has no INTERNET permission.
set -euo pipefail
mkdir -p app/src/main/assets/docquad
destination=app/src/main/assets/docquad/docquadnet256_trained_opset17.ort
curl -fL --retry 4 \
  https://raw.githubusercontent.com/egdels/makeacopy/cdf0e66833e5a918b90643692e2894a25262f284/app/src/main/assets/docquad/docquadnet256_trained_opset17.ort \
  -o "$destination"
actual=$(git hash-object "$destination")
expected=1cfaaf7ff3fdc04c29749f62051e961fe60accdc
if [ "$actual" != "$expected" ]; then
  rm -f "$destination"
  echo "DocQuad model hash mismatch" >&2
  exit 1
fi
echo "DocQuad model pinned hash verified"
