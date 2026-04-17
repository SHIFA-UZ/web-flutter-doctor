# Timezone Debug Instructions

**Issue:** Home screen shows UTC time, Calendar shows CET (local) time. They should both show the same timezone (doctor's practice timezone).

## Step 1: Run the App with Debug Logging

```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
```

## Step 2: Check Browser Console

Open browser DevTools (F12) and look for these debug messages:

### Expected Debug Output:

```
=== TODAY APPOINTMENTS PROVIDER ===
Doctor timezone: Europe/Berlin (or your actual timezone)
Query date (doctor's today): 2024-03-15

Converting appointment: startAt=2024-03-15T08:00:00Z, timezone=Europe/Berlin
utcIsoToTimeOfDayInZone: input=2024-03-15T08:00:00Z, targetZone=Europe/Berlin
utcIsoToTimeOfDayInZone: output=9:0
Converted to: 9:0 - 10:0

=== HOME SCREEN APPOINTMENT ===
Doctor timezone: Europe/Berlin
Appointment start: 9:0
Patient: John Doe

getNowInTimezone called with: Europe/Berlin
  → Success: 14:30 in Europe/Berlin
CalendarScreen: Profile loaded with timezone Europe/Berlin
```

## Step 3: Identify the Problem

Look for these specific issues:

### Issue 1: Timezone is NULL
```
Doctor timezone: null
```
**Solution:** Doctor needs to set their practice timezone in Profile screen.

### Issue 2: Timezone Conversion Failing
```
utcIsoToTimeOfDayInZone: input=2024-03-15T08:00:00Z, targetZone=Europe/Berlin
  → Invalid timezone "Europe/Berlin", falling back to UTC
```
**Solution:** Timezone package not initialized or invalid timezone string.

### Issue 3: Different Timezones in Home vs Calendar
```
# In home screen:
Doctor timezone: null
Appointment start: 8:0

# In calendar:
Doctor timezone: Europe/Berlin
Appointment start: 9:0
```
**Solution:** Profile provider returning different values at different times.

## Step 4: Check Profile

### Option A: Via Browser Console
Run this in browser console when logged in:
```javascript
// Check local storage
localStorage.getItem('shifa_doctor_auth_token')
```

### Option B: Via Profile Screen
1. Go to Profile tab (avatar at bottom of sidebar)
2. Check what timezone is displayed
3. Should show IANA timezone like "Europe/Berlin", "America/New_York", "Asia/Tashkent"

## Step 5: Manual Test Conversion

Add temporary button to home screen that prints:
```dart
ElevatedButton(
  onPressed: () {
    final profile = ref.read(profileAllProvider).valueOrNull;
    final tz = profile?.profile['timeZone'];
    print('=== MANUAL TEST ===');
    print('Profile timezone: $tz');
    print('Profile keys: ${profile?.profile.keys}');
    print('Full profile: ${profile?.profile}');

    final testUtc = '2024-03-15T08:00:00Z';
    final converted = CalendarEntry.utcIsoToTimeOfDayInZone(testUtc, tz);
    print('08:00 UTC → ${converted.hour}:${converted.minute} in $tz');
  },
  child: Text('Test Timezone Conversion'),
)
```

## Common Problems & Solutions

### Problem 1: Profile Timezone is NULL
**Symptoms:** Both show UTC time, or inconsistent times
**Cause:** Doctor hasn't set their practice timezone
**Fix:** Go to Profile → Set Practice Timezone

### Problem 2: Profile Key Name Wrong
**Symptoms:** Always null even though timezone is set
**Cause:** Backend uses different key name (e.g., 'practice_time_zone' instead of 'timeZone')
**Fix:** Check backend response, update key name in code

### Problem 3: Timezone String Invalid
**Symptoms:** Falls back to UTC despite timezone being set
**Cause:** Timezone string not valid IANA name (e.g., "CET" instead of "Europe/Berlin")
**Fix:** Update profile to use proper IANA timezone

### Problem 4: Profile Not Loaded in Home Screen
**Symptoms:** Home screen shows UTC, calendar shows correct time
**Cause:** Race condition - home loads before profile
**Fix:** Add loading state until profile available

## Quick Diagnostic Commands

```bash
# Check if timezone package is initialized
grep "tz.initializeTimeZones" lib/main.dart
# Should show: tz.initializeTimeZones(); in main()

# Check all timezone reads
grep -r "profile\['timeZone'\]" lib/
# Should show consistent key name everywhere

# Check conversion function usage
grep -r "utcIsoToTimeOfDayInZone" lib/
# Should show it's used in both home and calendar
```

## Expected Behavior

**Both home screen AND calendar should show the same time:**
- If doctor timezone is `Europe/Berlin` (UTC+1 in winter, UTC+2 in summer)
- And backend sends `2024-03-15T08:00:00Z` (08:00 UTC)
- Both should display: **09:00** (or 10:00 if DST active)

**NOT:**
- ❌ Home: 08:00 (UTC)
- ❌ Calendar: 09:00 (CET)
- ✅ Home: 09:00 (CET)
- ✅ Calendar: 09:00 (CET)

## Next Steps

1. Run app with `flutter run -d chrome`
2. Copy ALL console output to a file
3. Share the output so we can see what timezone values are being used
4. Check Profile screen to see what timezone is set

**Once you share the console output, I can identify exactly where the conversion is failing!**
