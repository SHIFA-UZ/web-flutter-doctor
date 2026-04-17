// Location Picker Widget - Structured, User-Friendly Design
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart' as latlong;
import 'package:geolocator/geolocator.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/services/google_geocoding_service.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

/// Structured location data model
class LocationData {
  final double latitude;
  final double longitude;
  final String country;
  final String region; // Viloyat
  final String district; // Tuman
  final String city;
  final String postalCode;
  final String streetAddress; // Editable

  LocationData({
    required this.latitude,
    required this.longitude,
    required this.country,
    required this.region,
    required this.district,
    required this.city,
    required this.postalCode,
    required this.streetAddress,
  });
}

class LocationPickerSection extends StatefulWidget {
  final double? latitude;
  final double? longitude;
  final String? locationCountry;
  final String? locationRegion;
  final String? locationDistrict;
  final String? locationCity;
  final String? locationPostalCode;
  final String? locationStreetAddress;
  final Function(Map<String, dynamic>) onLocationSelected;

  const LocationPickerSection({
    Key? key,
    required this.latitude,
    required this.longitude,
    this.locationCountry,
    this.locationRegion,
    this.locationDistrict,
    this.locationCity,
    this.locationPostalCode,
    this.locationStreetAddress,
    required this.onLocationSelected,
  }) : super(key: key);

  @override
  State<LocationPickerSection> createState() => _LocationPickerSectionState();
}

class _LocationPickerSectionState extends State<LocationPickerSection> {
  // Structured location fields (readonly, auto-filled)
  final TextEditingController _countryController = TextEditingController();
  final TextEditingController _regionController = TextEditingController();
  final TextEditingController _districtController = TextEditingController();
  final TextEditingController _cityController = TextEditingController();
  final TextEditingController _postalCodeController = TextEditingController();
  
  // Street address (editable)
  final TextEditingController _streetAddressController = TextEditingController();
  
  bool _isReverseGeocoding = false;
  late latlong.LatLng? _selectedLocation;

  @override
  void initState() {
    super.initState();
    _selectedLocation = null;
    
    // Load existing structured location data from backend if available
    if (widget.locationCountry != null) _countryController.text = widget.locationCountry!;
    if (widget.locationRegion != null) _regionController.text = widget.locationRegion!;
    if (widget.locationDistrict != null) _districtController.text = widget.locationDistrict!;
    if (widget.locationCity != null) _cityController.text = widget.locationCity!;
    if (widget.locationPostalCode != null) _postalCodeController.text = widget.locationPostalCode!;
    if (widget.locationStreetAddress != null) _streetAddressController.text = widget.locationStreetAddress!;
    
    if (widget.latitude != null && widget.longitude != null) {
      _selectedLocation = latlong.LatLng(widget.latitude!, widget.longitude!);
      // Only reverse geocode if we don't already have structured data
      if (widget.locationCountry == null && widget.locationRegion == null) {
        _reverseGeocode();
      }
    }
  }

  @override
  void didUpdateWidget(LocationPickerSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    
    // Update structured fields if they changed
    if (oldWidget.locationCountry != widget.locationCountry) {
      _countryController.text = widget.locationCountry ?? '';
    }
    if (oldWidget.locationRegion != widget.locationRegion) {
      _regionController.text = widget.locationRegion ?? '';
    }
    if (oldWidget.locationDistrict != widget.locationDistrict) {
      _districtController.text = widget.locationDistrict ?? '';
    }
    if (oldWidget.locationCity != widget.locationCity) {
      _cityController.text = widget.locationCity ?? '';
    }
    if (oldWidget.locationPostalCode != widget.locationPostalCode) {
      _postalCodeController.text = widget.locationPostalCode ?? '';
    }
    if (oldWidget.locationStreetAddress != widget.locationStreetAddress) {
      _streetAddressController.text = widget.locationStreetAddress ?? '';
    }
    
    if (oldWidget.latitude != widget.latitude || oldWidget.longitude != widget.longitude) {
      if (widget.latitude != null && widget.longitude != null) {
        _selectedLocation = latlong.LatLng(widget.latitude!, widget.longitude!);
        // Only reverse geocode if we don't already have structured data
        if (widget.locationCountry == null && widget.locationRegion == null) {
          _reverseGeocode();
        }
      }
    }
  }

  @override
  void dispose() {
    _countryController.dispose();
    _regionController.dispose();
    _districtController.dispose();
    _cityController.dispose();
    _postalCodeController.dispose();
    _streetAddressController.dispose();
    super.dispose();
  }

  Future<void> _reverseGeocode() async {
    if (_selectedLocation == null) return;

    setState(() => _isReverseGeocoding = true);
    try {
      final result = await GoogleGeocodingService.reverseGeocode(
        _selectedLocation!.latitude,
        _selectedLocation!.longitude,
      );

      if (mounted && result != null) {
        setState(() {
          // Auto-fill structured fields (readonly)
          _countryController.text = result['country'] as String? ?? '';
          _regionController.text = result['region'] as String? ?? '';
          _districtController.text = result['district'] as String? ?? '';
          _cityController.text = result['city'] as String? ?? '';
          _postalCodeController.text = result['postalCode'] as String? ?? '';
          
          // Auto-fill street address (editable)
          _streetAddressController.text = result['streetAddress'] as String? ?? '';
        });
        
        // Send all structured location data to parent
        widget.onLocationSelected({
          'latitude': _selectedLocation!.latitude,
          'longitude': _selectedLocation!.longitude,
          'locationCountry': result['country'] as String? ?? '',
          'locationRegion': result['region'] as String? ?? '',
          'locationDistrict': result['district'] as String? ?? '',
          'locationCity': result['city'] as String? ?? '',
          'locationPostalCode': result['postalCode'] as String? ?? '',
          'locationStreetAddress': result['streetAddress'] as String? ?? '',
        });
        
        debugPrint('Location reverse geocoded successfully');
      }
    } catch (e, stackTrace) {
      debugPrint('Reverse geocoding error: $e');
      debugPrint('Stack trace: $stackTrace');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.translate('couldNotGetAddressDetails') ?? 'Could not get address details. Please try selecting a different location.'),
            duration: const Duration(seconds: 3),
            backgroundColor: Colors.orange,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isReverseGeocoding = false);
      }
    }
  }

  Future<void> _showMapPicker() async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => _MapPickerScreen(
          initialLocation: _selectedLocation ?? 
              (widget.latitude != null && widget.longitude != null
                  ? latlong.LatLng(widget.latitude!, widget.longitude!)
                  : const latlong.LatLng(41.2995, 69.2401)), // Default to Tashkent
        ),
      ),
    );

    if (result != null && result is latlong.LatLng) {
      setState(() {
        _selectedLocation = result;
      });
      // Wait a bit for state to update, then reverse geocode
      await Future.delayed(const Duration(milliseconds: 50));
      await _reverseGeocode();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Row(
              children: [
                Icon(Icons.location_on, size: 20, color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 8),
                Text(
                  l10n.location,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Primary Action: Select Location on Map
            ShifaPrimaryButton(
              onPressed: _isReverseGeocoding ? null : _showMapPicker,
              isLoading: _isReverseGeocoding,
              icon: Icons.map,
              label: l10n.selectLocationOnMap,
              width: ButtonWidth.fill,
            ),
            const SizedBox(height: 20),

            // Structured Location Fields (Readonly, Auto-filled)
            if (_countryController.text.isNotEmpty ||
                _regionController.text.isNotEmpty ||
                _districtController.text.isNotEmpty ||
                _cityController.text.isNotEmpty)
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Country
                  if (_countryController.text.isNotEmpty)
                    _buildReadonlyField(
                      label: AppLocalizations.of(context)!.translate('country') ?? 'Country',
                      controller: _countryController,
                    ),
                  if (_countryController.text.isNotEmpty) const SizedBox(height: 12),

                  // Region (Viloyat)
                  if (_regionController.text.isNotEmpty)
                    _buildReadonlyField(
                      label: AppLocalizations.of(context)!.translate('region') ?? 'Region',
                      controller: _regionController,
                    ),
                  if (_regionController.text.isNotEmpty) const SizedBox(height: 12),

                  // District (Tuman)
                  if (_districtController.text.isNotEmpty)
                    _buildReadonlyField(
                      label: AppLocalizations.of(context)!.translate('district') ?? 'District',
                      controller: _districtController,
                    ),
                  if (_districtController.text.isNotEmpty) const SizedBox(height: 12),

                  // City
                  if (_cityController.text.isNotEmpty)
                    _buildReadonlyField(
                      label: AppLocalizations.of(context)!.translate('city') ?? 'City',
                      controller: _cityController,
                    ),
                  if (_cityController.text.isNotEmpty) const SizedBox(height: 12),

                  // Postal Code
                  if (_postalCodeController.text.isNotEmpty)
                    _buildReadonlyField(
                      label: AppLocalizations.of(context)!.translate('postalCode') ?? 'Postal Code',
                      controller: _postalCodeController,
                    ),
                  if (_postalCodeController.text.isNotEmpty) const SizedBox(height: 16),
                ],
              ),

            // Street Address (Editable)
            TextFormField(
              controller: _streetAddressController,
              decoration: InputDecoration(
                labelText: AppLocalizations.of(context)!.translate('streetAddress') ?? 'Street Address',
                hintText: AppLocalizations.of(context)!.translate('enterStreetAddress') ?? 'Enter street address, building name, floor, etc.',
                border: const OutlineInputBorder(),
                helperText: AppLocalizations.of(context)!.translate('streetAddressHelper') ?? 'You can edit this field to add building details, floor, room number, etc.',
                helperMaxLines: 2,
              ),
              maxLines: 2,
              onChanged: (value) {
                // When street address is edited, update the location data
                if (_selectedLocation != null) {
                  widget.onLocationSelected({
                    'latitude': _selectedLocation!.latitude,
                    'longitude': _selectedLocation!.longitude,
                    'locationCountry': _countryController.text,
                    'locationRegion': _regionController.text,
                    'locationDistrict': _districtController.text,
                    'locationCity': _cityController.text,
                    'locationPostalCode': _postalCodeController.text,
                    'locationStreetAddress': value,
                  });
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReadonlyField({
    required String label,
    required TextEditingController controller,
  }) {
    return TextFormField(
      controller: controller,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
        filled: true,
        fillColor: Colors.grey.shade100,
        enabled: false,
      ),
      style: TextStyle(color: Colors.grey.shade700),
    );
  }
}

// Map Picker Screen
class _MapPickerScreen extends StatefulWidget {
  final latlong.LatLng initialLocation;

  const _MapPickerScreen({Key? key, required this.initialLocation}) : super(key: key);

  @override
  State<_MapPickerScreen> createState() => _MapPickerScreenState();
}

class _MapPickerScreenState extends State<_MapPickerScreen> {
  late MapController _mapController;
  late latlong.LatLng _selectedLocation;
  bool _isGettingCurrentLocation = false;

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
    _selectedLocation = widget.initialLocation;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _mapController.move(_selectedLocation, 15.0);
    });
  }

  @override
  void dispose() {
    _mapController.dispose();
    super.dispose();
  }

  void _onMapTap(latlong.LatLng latLng) {
    setState(() {
      _selectedLocation = latLng;
    });
  }

  Future<void> _getCurrentLocation() async {
    setState(() => _isGettingCurrentLocation = true);
    
    try {
      final l10n = AppLocalizations.of(context)!;
      
      // Check if location services are enabled
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(l10n.translate('locationServicesDisabled') ?? 'Location services are disabled. Please enable them.'),
              backgroundColor: Colors.orange,
              duration: const Duration(seconds: 3),
            ),
          );
        }
        setState(() => _isGettingCurrentLocation = false);
        return;
      }

      // Check location permissions
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(l10n.translate('locationPermissionDenied') ?? 'Location permissions are denied.'),
                backgroundColor: Colors.red,
                duration: const Duration(seconds: 3),
              ),
            );
          }
          setState(() => _isGettingCurrentLocation = false);
          return;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(l10n.translate('locationPermissionDeniedForever') ?? 'Location permissions are permanently denied. Please enable them in settings.'),
              backgroundColor: Colors.red,
              duration: const Duration(seconds: 4),
            ),
          );
        }
        setState(() => _isGettingCurrentLocation = false);
        return;
      }

      // Get current position
      Position position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );

      final currentLocation = latlong.LatLng(
        position.latitude,
        position.longitude,
      );

      // Update selected location and move map
      if (mounted) {
        setState(() {
          _selectedLocation = currentLocation;
        });
        
        // Animate map to current location
        _mapController.move(currentLocation, 15.0);
        
        debugPrint('Current location obtained: ${position.latitude}, ${position.longitude}');
      }
    } catch (e, stackTrace) {
      debugPrint('Error getting current location: $e');
      debugPrint('Stack trace: $stackTrace');
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${AppLocalizations.of(context)!.translate('errorGettingCurrentLocation') ?? 'Error getting current location'}: ${e.toString()}'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isGettingCurrentLocation = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.selectLocationOnMap),
        actions: [
          IconButton(
            icon: const Icon(Icons.check),
            onPressed: () => Navigator.pop(context, _selectedLocation),
          ),
        ],
      ),
      body: Stack(
        children: [
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _selectedLocation,
              initialZoom: 15.0,
              onTap: (tapPosition, latLng) => _onMapTap(latLng),
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.shifa.doctor',
              ),
              MarkerLayer(
                markers: [
                  Marker(
                    point: _selectedLocation,
                    width: 40,
                    height: 40,
                    child: const Icon(
                      Icons.location_on,
                      color: Colors.red,
                      size: 40,
                    ),
                  ),
                ],
              ),
            ],
          ),
          // Current Location Button (Floating Action Button)
          Positioned(
            top: 16,
            right: 16,
            child: FloatingActionButton(
              onPressed: _isGettingCurrentLocation ? null : _getCurrentLocation,
              backgroundColor: Theme.of(context).colorScheme.primary,
              child: _isGettingCurrentLocation
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      ),
                    )
                  : const Icon(
                      Icons.my_location,
                      color: Colors.white,
                    ),
              tooltip: l10n.getCurrentLocation,
            ),
          ),
          // Selected Location Info Card
          Positioned(
            bottom: 20,
            left: 20,
            right: 20,
            child: Card(
              elevation: 4,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.translate('selectedLocation') ?? 'Selected Location',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    Text('${l10n.latitude}: ${_selectedLocation.latitude.toStringAsFixed(6)}'),
                    Text('${l10n.longitude}: ${_selectedLocation.longitude.toStringAsFixed(6)}'),
                    const SizedBox(height: 12),
                    ShifaPrimaryButton(
                      width: ButtonWidth.fill,
                      onPressed: () => Navigator.pop(context, _selectedLocation),
                      label: l10n.confirm,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
