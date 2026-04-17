# Admin panel logout when switching to Users menu – analysis

## What happens

1. You are logged into the **admin panel** (staging) and can use **Dashboard** (and possibly Tokens, etc.).
2. When you switch to the **Users** menu, the app **logs you out** and sends you to the login screen.

## Root cause (chain of events)

1. **Users screen loads**  
   Tapping "Users" shows `AdminUsersScreen`, which watches:
   - `adminUsersProvider(UsersProviderParams(...))`

2. **Provider calls the API**  
   That provider calls:
   - `GET /api/admin/users` (with optional query params: role, enabled, page, size).

3. **Backend returns 401 or 403**  
   In your staging environment, this request is returning **401 Unauthorized** or **403 Forbidden**.

4. **App treats 401/403 as “logout”**  
   In `api_client.dart`, every response is passed to `_checkUnauthorized()`:

   ```dart
   void _checkUnauthorized(http.Response response) {
     if ((response.statusCode == 401 || response.statusCode == 403) && _onUnauthorized != null) {
       _onUnauthorized!();
     }
   }
   ```

   In `api_providers.dart`, that callback is set to:

   ```dart
   client.setUnauthorizedCallback(() {
     ref.read(authProvider.notifier).logout();
   });
   ```

   So **any** 401 or 403 from **any** API call (including `GET /api/admin/users`) triggers a **global logout**.

5. **Auth listener sends you to login**  
   In `app.dart`, when auth goes from authenticated → unauthenticated, the app does:

   ```dart
   navigatorKey.currentState?.pushNamedAndRemoveUntil(AppRoutes.login, (route) => false);
   ```

   So you end up on the **doctor login** screen.

So the **direct reason** you are logged out when switching to Users is:  
**the `GET /api/admin/users` request in staging is returning 401 or 403, and the app is designed to logout on any 401/403.**

## Why might `/api/admin/users` return 401 or 403 in staging?

Backend behaviour:

- **`GET /api/admin/users`** in `AdminController.kt` does **not** check read-only; it only requires the same `hasRole("ADMIN")` as the rest of `/api/admin/**`. So in code, listing users is allowed for any admin.
- Dashboard uses `GET /api/admin/dashboard/stats` with the same security. If Dashboard works, the same token and role should be valid for Users **unless** something is different about the users request or environment.

So the 401/403 in staging is likely due to one of:

1. **401 – token not sent or invalid**
   - **Flutter web**: token might not be attached on that specific request (e.g. timing, storage, or provider scope).
   - **Token expired** between opening Dashboard and opening Users (short-lived JWT in staging).
   - **Staging backend**: different JWT secret/issuer so the token is rejected.

2. **403 – “forbidden”**
   - **Staging-only config**: e.g. proxy, API gateway, or extra security in front of the backend that returns 403 for `/api/admin/users` (or for certain methods).
   - **Backend bug in staging**: e.g. exception in `listUsers` that gets mapped to 403.

3. **CORS / preflight**
   - Less common, but a failed or mis-handled preflight could result in a response that the client sees as 401/403.

## How to confirm

1. **Browser DevTools (Network)**  
   In staging, open the app, go to Admin → Users, and immediately check the **Network** tab:
   - Find the request to `/api/admin/users` (or your staging API base + `/api/admin/users`).
   - Check **status code** (401 vs 403) and **response body**.
   - Check **Request headers** and confirm `Authorization: Bearer <token>` is present and unchanged from the Dashboard request.

2. **Backend logs**  
   In staging backend logs, look for the same request (path, method, and optional user/admin id). That will show whether the request reached the backend and whether it was rejected (and why).

3. **Console logs in the app**  
   `api_client.dart` already logs when the Authorization header is missing on GET. If you see  
   `API GET /api/admin/users: WARNING - No Authorization header!`  
   when switching to Users, the token is not being sent for that request.

## Recommendations

### 1. Fix the underlying 401/403 in staging (primary)

- Ensure the **same** JWT that works for Dashboard is sent on `GET /api/admin/users` (same base URL, same `ApiClient`/token).
- If token is missing on that request, fix Flutter web token handling (e.g. when the token is set on `ApiClient` and how it’s stored/restored).
- If token is sent but backend returns 401, check staging JWT config (secret, issuer, audience).
- If you see 403, check staging proxy/gateway and backend logs for that path; fix config or backend so listing users is allowed for the same admin that can access dashboard.

### 2. Don’t treat every 401/403 as a full logout (UX improvement)

- **Option A**: For **403** in the admin panel, do **not** call the global logout; instead show an “Access denied” message and optionally redirect to **admin** login only.
- **Option B**: Only trigger logout on **401** (clearly “not authenticated”), and handle **403** as “forbidden” (e.g. snackbar + stay on admin, or redirect to admin login).
- This avoids one failing admin endpoint (e.g. misconfigured users endpoint in staging) from logging the user out of the whole app.

### 3. Easier debugging

- In `_checkUnauthorized`, log the **request path** (and status code) when calling the callback, so in staging you can see exactly which request caused the logout (e.g. “401 on GET /api/admin/users”).

---

**Summary:** You are logged out when switching to Users because the app calls `GET /api/admin/users`, that call returns **401 or 403** in staging, and the app is designed to **logout on any 401/403**. Fix the staging 401/403 (token + backend/config), and optionally change the app so 403 in admin does not trigger a full logout.
