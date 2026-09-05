#!/bin/sh
# Starts Magellan from the Maven distribution zip.
# Run from anywhere; resolves paths relative to this script's location.
cd "$(dirname "$0")"
exec java -Xmx1200m -jar magellan2-*.jar "$@"
