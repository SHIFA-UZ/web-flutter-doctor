# Location Integration - Implementation Summary

## Overview
Location integration has been added to both Doctor and Patient apps, allowing users to set their exact location (latitude/longitude) and enabling location-based filtering and sorting.

## Backend Changes (Completed)

### 1. Database Schema
- **Migration**: `V28__add_location_fields.sql`
  - Added `latitude` and `longitude` columns to `doctor_profiles` table
  - Added `latitude` and `longitude` columns to `patient_profiles` table
  - Created indexes for efficient location-based queries

### 2. Domain Models
- **DoctorProfile.kt**: Added `latitude: Double?` and `longitude: Double?` fields
- **PatientProfile.kt**: Added `latitude: Double?` and `longitude: Double?` fields

### 3. API Endpoints
- **DoctorController.kt**: 
  - Updated `ProfileDto` to include `latitude` and `longitude`
  - Updated `patchProfile` to accept and save location coordinates
  
- **PatientController.kt**:
  - Updated `PatientProfileDto` to include `latitude` and `longitude`
  - Updated `UpdateProfileRequest` to include location fields
  - Updated `updateProfile` to accept and save location coordinates

### 4. Location-Based Filtering
- **PublicDoctorController.kt**: 
  - Added location-based filtering with distance calculation (Haversine formula)
  - Query parameters:
    - `latitude`: User's latitude
    - `longitude`: User's longitude
    - `radiusKm`: Maximum distance in kilometers (default: 50km)
    - `sortBy`: Sort by 'distance' or 'rating'
  - Returns `distanceKm` in the response for each doctor

## Frontend Implementation (To Be Completed)

### Required Packages
Add to both `pubspec.yaml` files:
```yaml
dependencies:
  geolocator: ^12.0.0  # For getting current location
  google_maps_flutter: ^2.5.0  # For map picker (optional, can use simpler solution)
  # OR use a simpler approach with coordinate input
```

### Doctor App
1. **Profile Screen** (`profile_screen.dart`):
   - Add location picker button in the profile panel
   - Allow doctors to:
     - Get current location via GPS
     - Pick location on map
     - Manually enter coordinates
   - Save location when profile is updated

### Patient App
1. **Edit Profile Screen** (`edit_profile_screen.dart`):
   - Add location picker section
   - Allow patients to set their location
   - Save location when profile is updated

2. **Doctor Search/List Screen**:
   - Add location filter toggle
   - Show distance to each doctor
   - Sort by distance option
   - Filter by radius slider

## Usage Examples

### Backend API Usage

#### Update Doctor Location
```http
PATCH /api/doctors/me/profile
Content-Type: application/json

{
  "latitude": 41.2995,
  "longitude": 69.2401,
  "address": "Tashkent, Uzbekistan"
}
```

#### Update Patient Location
```http
PATCH /api/patients/me/profile
Content-Type: application/json

{
  "latitude": 41.2995,
  "longitude": 69.2401,
  "address": "Tashkent, Uzbekistan"
}
```

#### Search Doctors by Location
```http
GET /api/public/doctors?latitude=41.2995&longitude=69.2401&radiusKm=25&sortBy=distance
```

Response includes `distanceKm` for each doctor:
```json
{
  "id": 1,
  "fullName": "Dr. John Doe",
  "latitude": 41.3000,
  "longitude": 69.2500,
  "distanceKm": 1.2
}
```

## Future Enhancements
1. **Geocoding**: Convert address to coordinates automatically
2. **Reverse Geocoding**: Convert coordinates to readable address
3. **Map View**: Show doctors/patients on an interactive map
4. **Route Planning**: Show directions to doctor's clinic
5. **Location History**: Track location changes over time
6. **Privacy Controls**: Allow users to hide exact location, show only city/region

## Testing
1. Test location update via API
2. Test location-based filtering
3. Test distance calculation accuracy
4. Test sorting by distance
5. Test edge cases (null locations, invalid coordinates)
