# Firebase Staging - Quick Start Guide

## 🚀 5-Minute Setup

### Step 1: Install Firebase CLI (One-time)
```powershell
npm install -g firebase-tools
```

### Step 2: Login
```powershell
firebase login
```
(Opens browser - sign in with Google)

### Step 3: Create Firebase Project
1. Go to: https://console.firebase.google.com/
2. Click "Add project"
3. Name: `shifa-doctor-staging`
4. Click "Create project"

### Step 4: Initialize (One-time)
```powershell
cd shifa_doc_app_v1
firebase init hosting
```

**Answer:**
- Select your project: `shifa-doctor-staging`
- Public directory: `build/web`
- Single-page app: **Yes**
- GitHub: **No**

### Step 5: Build & Deploy
```powershell
# Build for staging
.\scripts\build_staging.bat https://your-backend-api-url.com/api

# Deploy to Firebase
firebase deploy --only hosting
```

### Step 6: Get Your URL
After deployment, you'll see:
```
✔ Deploy complete!

Project Console: https://console.firebase.google.com/project/shifa-doctor-staging/overview
Hosting URL: https://shifa-doctor-staging.web.app
```

**That's it!** Your app is live at the Hosting URL.

## 📝 Important Notes

1. **Backend API URL**: Replace `https://your-backend-api-url.com/api` with your actual backend URL
2. **CORS**: Make sure your backend allows requests from your Firebase URL
3. **Updates**: Run build + deploy commands again to update

## 🔄 Update Workflow

Every time you want to update staging:

```powershell
# 1. Build
.\scripts\build_staging.bat https://your-backend-api-url.com/api

# 2. Deploy
firebase deploy --only hosting

# 3. Done! (~30 seconds)
```

## ❓ Need Help?

See `FIREBASE_SETUP.md` for detailed instructions.
