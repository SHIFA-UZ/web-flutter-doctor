/// DTOs for the doctor's practice locations.
///
/// Matches `DoctorLocationController.LocationDto` on the backend.
class DoctorLocationDto {
  final int? id;
  final String label;
  final String? clinic;
  final String? address;
  final double? latitude;
  final double? longitude;
  final String? locationCountry;
  final String? locationRegion;
  final String? locationDistrict;
  final String? locationCity;
  final String? locationPostalCode;
  final String? locationStreetAddress;
  final bool isPrimary;

  DoctorLocationDto({
    this.id,
    required this.label,
    this.clinic,
    this.address,
    this.latitude,
    this.longitude,
    this.locationCountry,
    this.locationRegion,
    this.locationDistrict,
    this.locationCity,
    this.locationPostalCode,
    this.locationStreetAddress,
    this.isPrimary = false,
  });

  factory DoctorLocationDto.fromJson(Map<String, dynamic> j) => DoctorLocationDto(
        id: (j['id'] as num?)?.toInt(),
        label: (j['label'] as String?) ?? '',
        clinic: j['clinic'] as String?,
        address: j['address'] as String?,
        latitude: (j['latitude'] as num?)?.toDouble(),
        longitude: (j['longitude'] as num?)?.toDouble(),
        locationCountry: j['locationCountry'] as String?,
        locationRegion: j['locationRegion'] as String?,
        locationDistrict: j['locationDistrict'] as String?,
        locationCity: j['locationCity'] as String?,
        locationPostalCode: j['locationPostalCode'] as String?,
        locationStreetAddress: j['locationStreetAddress'] as String?,
        isPrimary: j['isPrimary'] == true,
      );

  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'label': label,
        if (clinic != null) 'clinic': clinic,
        if (address != null) 'address': address,
        if (latitude != null) 'latitude': latitude,
        if (longitude != null) 'longitude': longitude,
        if (locationCountry != null) 'locationCountry': locationCountry,
        if (locationRegion != null) 'locationRegion': locationRegion,
        if (locationDistrict != null) 'locationDistrict': locationDistrict,
        if (locationCity != null) 'locationCity': locationCity,
        if (locationPostalCode != null) 'locationPostalCode': locationPostalCode,
        if (locationStreetAddress != null)
          'locationStreetAddress': locationStreetAddress,
        'isPrimary': isPrimary,
      };

  DoctorLocationDto copyWith({
    int? id,
    String? label,
    String? clinic,
    String? address,
    double? latitude,
    double? longitude,
    String? locationCountry,
    String? locationRegion,
    String? locationDistrict,
    String? locationCity,
    String? locationPostalCode,
    String? locationStreetAddress,
    bool? isPrimary,
  }) =>
      DoctorLocationDto(
        id: id ?? this.id,
        label: label ?? this.label,
        clinic: clinic ?? this.clinic,
        address: address ?? this.address,
        latitude: latitude ?? this.latitude,
        longitude: longitude ?? this.longitude,
        locationCountry: locationCountry ?? this.locationCountry,
        locationRegion: locationRegion ?? this.locationRegion,
        locationDistrict: locationDistrict ?? this.locationDistrict,
        locationCity: locationCity ?? this.locationCity,
        locationPostalCode: locationPostalCode ?? this.locationPostalCode,
        locationStreetAddress:
            locationStreetAddress ?? this.locationStreetAddress,
        isPrimary: isPrimary ?? this.isPrimary,
      );
}
