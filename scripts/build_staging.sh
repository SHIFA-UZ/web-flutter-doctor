#!/bin/bash
# Build script for Firebase Staging deployment
# Usage: ./scripts/build_staging.sh [API_BASE_URL]
# NOTE: API_BASE_URL should NOT include /api - the code adds it automatically

set -e  # Exit on error

# Default API URL (you can override via command line)
API_BASE_URL=${1:-"https://shifa-doctor-staging-default-rtdb.firebaseio.com"}

# Remove trailing /api if present
API_BASE_URL="${API_BASE_URL%/api}"

echo "🏗️  Building Flutter web app for STAGING..."
echo "📍 API Base URL: $API_BASE_URL"
echo "ℹ️  (Code will add /api to paths automatically)"
echo ""

# Clean previous build
echo "🧹 Cleaning previous build..."
flutter clean

# Get dependencies
echo "📦 Getting dependencies..."
flutter pub get

# Build for web with staging configuration
echo "🔨 Building web release..."
flutter build web --release \
  --dart-define=API_BASE_URL="$API_BASE_URL" \
  --dart-define=ENVIRONMENT=staging \
  --base-href="/"

echo ""
echo "✅ Build complete! Output: build/web/"
echo ""
echo "📤 To deploy to Firebase:"
echo "   firebase deploy --only hosting --project staging"
echo ""
