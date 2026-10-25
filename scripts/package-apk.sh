#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./gradlew :app:assembleRelease :app:lintRelease :app:testReleaseUnitTest
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Sportcast-TV-0.5.0.apk
(cd dist && shasum -a 256 Sportcast-TV-*.apk > SHA256SUMS)
