# Deploy Doctor App to Firebase - Step by Step

## Prerequisites ✅

- [x] Backend deployed to Railway (you've done this!)
- [ ] Firebase CLI installed
- [ ] Firebase project created
- [ ] Firebase initialized in project

## Step-by-Step Deployment

### Step 1: Get Your Railway Backend URL

1. Go to Railway dashboard: https://railway.app
2. Click on your backend service
3. Go to **"Settings"** → **"Networking"**
4. Copy your **Public Domain** (e.g., `https://your-app.railway.app`)
5. **Your API URL will be**: `https://your-app.railway.app/api`

**Important**: Make sure your backend is running and accessible!

### Step 2: Install Firebase CLI (If Not Installed)

Open PowerShell and run:

```powershell
npm install -g firebase-tools
```

**Note**: If you get an error, you may need to install Node.js first: https://nodejs.org/

### Step 3: Login to Firebase

```powershell
firebase login
```

This will open a browser - sign in with your Google account.

### Step 4: Create Firebase Project (If Not Created)

1. Go to: https://console.firebase.google.com/
2. Click **"Add project"** (or **"Create a project"**)
3. Enter project name: `shifa-doctor-staging`
4. Click **"Continue"**
5. Disable Google Analytics (optional) → Click **"Create project"**
6. Wait for project to be created → Click **"Continue"**

### Step 5: Initialize Firebase Hosting (One-time Setup)

```powershell
cd C:\shifa_doc_app_v1
firebase init hosting
```

**Answer the prompts:**
- **"Which Firebase features do you want to set up?"** → Select **"Hosting"** (use spacebar to select, Enter to confirm)
- **"Please select an option"** → Select **"Use an existing project"**
- **"Select a default Firebase project"** → Select `shifa-doctor-staging`
- **"What do you want to use as your public directory?"** → Type: `build/web`
- **"Configure as a single-page app (rewrite all urls to /index.html)?"** → Type: **Y** (Yes)
- **"Set up automatic builds and deploys with GitHub?"** → Type: **N** (No)
- **"File build/web/index.html already exists. Overwrite?"** → Type: **N** (No)

### Step 6: Build the App with Your Railway Backend URL

Replace `https://your-app.railway.app` with your actual Railway backend URL (do NOT include `/api`):

```powershell
cd C:\shifa_doc_app_v1
.\scripts\build_staging.bat https://your-app.railway.app
```

**Example** (if your Railway URL is `https://shifa-backend.railway.app`):
```powershell
.\scripts\build_staging.bat https://shifa-backend.railway.app
```

**Important**: Do NOT add `/api` to the URL. The code automatically adds `/api` to all API paths.

This will:
- Clean previous build
- Get dependencies
- Build Flutter web app with your backend URL
- Output to `build/web/`

**Wait for build to complete** (~2-5 minutes)

### Step 7: Deploy to Firebase

```powershell
firebase deploy --only hosting
```

This will:
- Upload your built app to Firebase
- Deploy to Firebase Hosting
- Give you a public URL

**Wait for deployment** (~30 seconds)

### Step 8: Get Your App URL

After deployment, you'll see:

```
✔ Deploy complete!

Project Console: https://console.firebase.google.com/project/shifa-doctor-staging/overview
Hosting URL: https://shifa-doctor-staging.web.app
```

**Your app is live at the Hosting URL!** 🎉

### Step 9: Update Backend CORS (If Needed)

Make sure your Railway backend allows requests from your Firebase URL:

1. Go to Railway → Your Service → **"Variables"**
2. Check `PUBLIC_BASE_URL` is set to your Firebase URL (or Railway URL)
3. If CORS errors occur, verify backend CORS configuration

## ✅ Verification

Test your deployed app:

1. **Open your Firebase URL** in a browser
2. **Try to sign in** with test credentials
3. **Check browser console** (F12) for any errors
4. **Verify API calls** are going to your Railway backend

## 🔄 Updating Your App

Every time you want to update:

```powershell
cd C:\shifa_doc_app_v1

# 1. Build with your backend URL (do NOT include /api)
.\scripts\build_staging.bat https://your-app.railway.app

# 2. Deploy
firebase deploy --only hosting
```

## 🆘 Troubleshooting

### "firebase: command not found"
- Install Firebase CLI: `npm install -g firebase-tools`
- Make sure Node.js is installed: https://nodejs.org/

### "Build failed"
- Check Flutter is installed: `flutter --version`
- Check you're in the correct directory: `cd C:\shifa_doc_app_v1`
- Try: `flutter clean` then rebuild

### "CORS errors" in browser
- Verify `PUBLIC_BASE_URL` in Railway matches your Firebase URL
- Check backend CORS configuration allows Firebase domain
- Backend should allow: `https://*.web.app` and `https://*.firebaseapp.com`

### "API calls failing"
- Verify Railway backend is running
- Check Railway backend URL is correct in build command
- Test backend directly: `https://your-app.railway.app/api/auth/login`

### "Firebase project not found"
- Make sure you created the project in Firebase Console
- Check `.firebaserc` file has correct project ID
- Run `firebase use staging` to switch projects

## 📝 Quick Reference

```powershell
# Build
.\scripts\build_staging.bat https://your-railway-url.railway.app/api

# Deploy
firebase deploy --only hosting

# Check Firebase projects
firebase projects:list

# Switch Firebase project
firebase use staging
```

## 🎯 Success Checklist

- [ ] Firebase CLI installed
- [ ] Logged in to Firebase
- [ ] Firebase project created
- [ ] Firebase initialized in project
- [ ] Railway backend URL obtained
- [ ] App built successfully
- [ ] App deployed to Firebase
- [ ] App accessible at Firebase URL
- [ ] Sign-in works
- [ ] API calls work (no CORS errors)

## 📚 Related Documentation

- **Firebase Setup**: `FIREBASE_SETUP.md`
- **Firebase Quick Start**: `FIREBASE_QUICKSTART.md`
- **Railway Backend**: `../shifa-doctor-backend/RAILWAY_DEPLOYMENT.md`
