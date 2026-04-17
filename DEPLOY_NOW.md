# 🚀 Deploy Doctor App to Firebase - Quick Guide

## ✅ Prerequisites Check

You have:
- ✅ Firebase CLI installed (v15.4.0)
- ✅ Firebase project created (`shifa-doctor-staging`)
- ✅ Firebase initialized in project
- ✅ Backend deployed to Railway

## 🎯 What You Need

**Your Railway Backend URL** - Get it from Railway dashboard:
1. Go to: https://railway.app
2. Click on your backend service
3. Go to **"Settings"** → **"Networking"**
4. Copy your **Public Domain** (e.g., `https://shifa-backend.railway.app`)

## 🚀 Deploy in 2 Steps

### Option 1: Using the Deployment Script (Recommended)

```powershell
cd C:\shifa_doc_app_v1
.\deploy.ps1 https://your-railway-backend.railway.app
```

**Example:**
```powershell
.\deploy.ps1 https://shifa-backend.railway.app
```

The script will:
1. Build the app with your Railway backend URL
2. Deploy to Firebase
3. Show you the live URL

### Option 2: Manual Steps

```powershell
cd C:\shifa_doc_app_v1

# Step 1: Build (do NOT include /api - code adds it automatically)
.\scripts\build_staging.bat https://your-railway-backend.railway.app

# Step 2: Deploy
firebase deploy --only hosting
```

## 📝 Important Notes

1. **Backend URL Format**: 
   - If your Railway URL is `https://shifa-backend.railway.app`
   - Use: `https://shifa-backend.railway.app` (do NOT add `/api`)
   - The code automatically adds `/api` to all API paths

2. **First Time**: The build may take 3-5 minutes. Subsequent builds are faster.

3. **After Deployment**: Your app will be live at:
   - `https://shifa-doctor-staging.web.app`
   - Or check Firebase Console for the exact URL

## ✅ Verify Deployment

1. Open your Firebase URL in a browser
2. Try to sign in
3. Check browser console (F12) for errors
4. Verify API calls are working

## 🔄 Update Your App

To update after making changes:

```powershell
cd C:\shifa_doc_app_v1
# Use Railway URL WITHOUT /api
.\deploy.ps1 https://your-railway-backend.railway.app
```

## 🆘 Troubleshooting

### Build Fails
- Make sure Flutter is installed: `flutter --version`
- Try: `flutter clean` then rebuild

### Deployment Fails
- Check you're logged in: `firebase login`
- Verify project: `firebase projects:list`

### CORS Errors
- Make sure Railway backend has `PUBLIC_BASE_URL` set to your Firebase URL
- Backend should allow: `https://*.web.app` and `https://*.firebaseapp.com`

### API Not Working
- Verify Railway backend is running
- Test backend directly: `https://your-backend.railway.app/api/auth/login`
- Check Railway logs for errors

## 📚 Full Documentation

For detailed instructions, see: `DEPLOY_TO_FIREBASE.md`
