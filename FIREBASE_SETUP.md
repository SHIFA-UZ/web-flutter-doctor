# Firebase Hosting Setup for Staging

This guide will help you deploy your Shifa Doctor app to Firebase Hosting for staging.

## Prerequisites

1. **Google Account** - You'll need a Google account
2. **Node.js** - For Firebase CLI (if not installed, get it from [nodejs.org](https://nodejs.org/))
3. **Flutter** - Already installed ✅

## Step 1: Install Firebase CLI

### Windows (PowerShell)
```powershell
npm install -g firebase-tools
```

### Mac/Linux
```bash
npm install -g firebase-tools
```

### Verify Installation
```bash
firebase --version
```

## Step 2: Login to Firebase

```bash
firebase login
```

This will open your browser to authenticate with Google.

## Step 3: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add project"** or **"Create a project"**
3. Enter project name: `shifa-doctor-staging`
4. Disable Google Analytics (optional for staging)
5. Click **"Create project"**
6. Wait for project creation (30 seconds)

## Step 4: Initialize Firebase in Your Project

```bash
cd shifa_doc_app_v1
firebase init hosting
```

**Answer the prompts:**

1. **"Which Firebase project do you want to use?"**
   - Select: `shifa-doctor-staging` (or create new)

2. **"What do you want to use as your public directory?"**
   - Enter: `build/web`

3. **"Configure as a single-page app (rewrite all urls to /index.html)?"**
   - Answer: **Yes** ✅

4. **"Set up automatic builds and deploys with GitHub?"**
   - Answer: **No** (we'll do manual deploys for now)

5. **"File build/web/index.html already exists. Overwrite?"**
   - Answer: **No** (keep existing)

## Step 5: Update Firebase Project ID

After initialization, update `.firebaserc` if needed:

```json
{
  "projects": {
    "default": "shifa-doctor-staging",
    "staging": "shifa-doctor-staging"
  }
}
```

Replace `shifa-doctor-staging` with your actual Firebase project ID.

## Step 6: Build for Staging

### Option A: Using the Script (Recommended)

**Windows:**
```powershell
.\scripts\build_staging.bat
```

**Mac/Linux:**
```bash
chmod +x scripts/build_staging.sh
./scripts/build_staging.sh
```

### Option B: Manual Build

```bash
flutter clean
flutter pub get
flutter build web --release \
  --dart-define=API_BASE_URL=https://your-backend-url.com/api \
  --dart-define=ENVIRONMENT=staging \
  --base-href="/"
```

**Important:** Replace `https://your-backend-url.com/api` with your actual backend API URL.

## Step 7: Deploy to Firebase

```bash
firebase deploy --only hosting --project staging
```

Or simply:
```bash
firebase deploy --only hosting
```

## Step 8: Access Your Staging App

After deployment, Firebase will give you URLs like:
- **Primary URL**: `https://shifa-doctor-staging.web.app`
- **Alternative URL**: `https://shifa-doctor-staging.firebaseapp.com`

## Updating Your Backend API URL

Once you have your Firebase URL, you need to:

1. **Update CORS** in your backend to allow your Firebase URL
2. **Rebuild the app** with the correct backend API URL:

```bash
# If your backend is at https://api.example.com
.\scripts\build_staging.bat https://api.example.com/api
```

## Common Issues & Solutions

### Issue: "Firebase CLI not found"
**Solution:** Make sure Node.js is installed and Firebase CLI is in your PATH.

### Issue: "Permission denied" on scripts
**Solution (Mac/Linux):**
```bash
chmod +x scripts/build_staging.sh
```

### Issue: "Build failed"
**Solution:** 
- Make sure Flutter is up to date: `flutter upgrade`
- Clean build: `flutter clean && flutter pub get`

### Issue: "API calls failing"
**Solution:**
- Check CORS settings in your backend
- Verify API_BASE_URL is correct
- Check browser console for errors

## Deployment Workflow

### First Time Setup (One-time)
1. ✅ Install Firebase CLI
2. ✅ Login to Firebase
3. ✅ Create Firebase project
4. ✅ Initialize Firebase in project

### Regular Deployment (Every time you want to deploy)
1. Build the app: `.\scripts\build_staging.bat [API_URL]`
2. Deploy: `firebase deploy --only hosting`
3. Test: Visit your Firebase URL

## Updating the App

To update your staging app:

```bash
# 1. Build with latest changes
.\scripts\build_staging.bat https://your-backend-url.com/api

# 2. Deploy
firebase deploy --only hosting

# 3. Done! Changes are live in ~30 seconds
```

## Firebase Console Features

Visit [Firebase Console](https://console.firebase.google.com/) to:
- View deployment history
- See analytics (if enabled)
- Manage hosting settings
- View logs
- Set up custom domain (optional)

## Next Steps

After staging is working:
1. ✅ Test thoroughly with staging URL
2. ⏭️ Set up production Firebase project (separate)
3. ⏭️ Configure custom domain (optional)
4. ⏭️ Set up CI/CD for automatic deployments

## Quick Reference

```bash
# Build for staging
.\scripts\build_staging.bat https://your-api-url.com/api

# Deploy to Firebase
firebase deploy --only hosting

# View deployment history
firebase hosting:channel:list

# Rollback (if needed)
firebase hosting:clone SOURCE_SITE_ID:SOURCE_CHANNEL_ID TARGET_SITE_ID:live
```

## Support

- Firebase Docs: https://firebase.google.com/docs/hosting
- Flutter Web: https://docs.flutter.dev/deployment/web
