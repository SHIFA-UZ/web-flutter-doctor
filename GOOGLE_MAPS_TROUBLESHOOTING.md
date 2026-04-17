# Google Maps Geocoding API - Troubleshooting REQUEST_DENIED Error

## Step-by-Step Fix

### 1. Verify You Enabled the CORRECT API

The error "This API is not activated" means you need to enable the **Geocoding API** specifically:

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Go to **"APIs & Services"** > **"Library"**
4. Search for **"Geocoding API"** (NOT "Maps JavaScript API" or "Maps SDK")
5. Click on **"Geocoding API"**
6. Click **"Enable"** button
7. Wait a few seconds for it to activate

**Important:** Make sure you enable **"Geocoding API"**, not:
- ❌ Maps JavaScript API
- ❌ Maps SDK for Android
- ❌ Maps SDK for iOS
- ✅ **Geocoding API** (this is the one you need!)

### 2. Check API Key Restrictions

1. Go to **"APIs & Services"** > **"Credentials"**
2. Click on your API key (`AIzaSyD8CSmFnNEcUH7JbA-QgRhbHiRBWTt0Jg4`)
3. Under **"API restrictions"**:
   - If it says "Don't restrict key" → This is fine
   - If it says "Restrict key" → Make sure **"Geocoding API"** is in the list
4. If Geocoding API is not in the list, click **"Restrict key"** and add **"Geocoding API"**

### 3. Enable Billing (Required for Geocoding API)

Google requires billing to be enabled for Geocoding API (even though you get $200 free credit):

1. Go to **"Billing"** in Google Cloud Console
2. If no billing account is linked, create one
3. Link it to your project
4. Don't worry - you get $200 free credit per month (covers ~40,000 requests)

### 4. Verify the API Key is Correct

1. Double-check the API key in `google_geocoding_service.dart` matches the one in Google Console
2. Make sure there are no extra spaces or characters
3. The key should start with `AIzaSy...`

### 5. Wait and Retry

After enabling the API:
- Wait 1-2 minutes for changes to propagate
- Restart your Flutter app
- Try again

### 6. Test the API Directly

You can test if the API is working by opening this URL in your browser (replace with your coordinates):

```
https://maps.googleapis.com/maps/api/geocode/json?latlng=41.304238,69.267001&key=AIzaSyD8CSmFnNEcUH7JbA-QgRhbHiRBWTt0Jg4
```

If you see JSON with address data, the API is working. If you see an error, the API is not enabled.

## Quick Checklist

- [ ] Geocoding API is enabled (not Maps JavaScript API)
- [ ] API key has no restrictions OR Geocoding API is in the allowed list
- [ ] Billing is enabled on the project
- [ ] API key in code matches the one in Google Console
- [ ] Waited 1-2 minutes after enabling
- [ ] Restarted the app

## Still Not Working?

If it still doesn't work:
1. Create a NEW API key (sometimes old keys have cached restrictions)
2. Make sure you're using the correct Google Cloud project
3. Check the browser console for more detailed error messages
