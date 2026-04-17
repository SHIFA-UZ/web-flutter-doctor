# Google Maps Geocoding API Setup

This app now uses Google Maps Geocoding API for better address resolution. Follow these steps to set it up:

## Step 1: Get a Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the **Geocoding API**:
   - Go to "APIs & Services" > "Library"
   - Search for "Geocoding API"
   - Click "Enable"

## Step 2: Create an API Key

1. Go to "APIs & Services" > "Credentials"
2. Click "Create Credentials" > "API Key"
3. Copy your API key
4. (Recommended) Restrict the API key:
   - Click on the API key to edit it
   - Under "API restrictions", select "Restrict key"
   - Choose "Geocoding API"
   - Under "Application restrictions", you can restrict by HTTP referrer (for web) or by app package (for mobile)

## Step 3: Add API Key to the App

Open `lib/core/services/google_geocoding_service.dart` and replace:

```dart
static const String _apiKey = 'YOUR_GOOGLE_MAPS_API_KEY_HERE';
```

with your actual API key:

```dart
static const String _apiKey = 'AIzaSy...your-actual-key-here';
```

## Step 4: For Production (Recommended)

For production apps, you should:

1. **Use environment variables** or **secure storage** instead of hardcoding the API key
2. **Restrict the API key** to only allow requests from your app's domain/package
3. **Set up billing** in Google Cloud Console (Google provides $200 free credit per month)

## Pricing

Google Maps Geocoding API pricing:
- **$5.00 per 1,000 requests** (first 40,000 requests per month are free with $200 credit)
- See [Google Maps Platform Pricing](https://mapsplatform.google.com/pricing/) for details

## Testing

After adding your API key, test the location picker:
1. Click "Get Current Location" - should populate address
2. Click "Select Location on Map" - should populate address after selection
3. Enter an address and click search - should find coordinates

If you see errors, check:
- API key is correct
- Geocoding API is enabled
- API key restrictions allow your app
- Browser console for error messages
