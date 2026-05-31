import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_date_range_provider.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_web.dart'
    as file_download;

class HomeDashboardExportService {
  const HomeDashboardExportService(this.ref);

  final Ref ref;

  Future<void> exportCsv() async {
    final range = ref.read(homeDashboardDateRangeProvider);
    final overview = await ref.read(doctorAnalyticsOverviewProvider.future);
    final trend = await ref.read(doctorAnalyticsTrendProvider.future);
    final types = await ref.read(doctorConsultationTypesProvider.future);
    final engagement = await ref.read(doctorEngagementProvider.future);

    final buffer = StringBuffer()
      ..writeln('SHIFA Dashboard Export')
      ..writeln('Period,${range.start.toIso8601String()},${range.end.toIso8601String()}')
      ..writeln()
      ..writeln('Metric,Value')
      ..writeln('Appointments today,${overview.appointmentsToday}')
      ..writeln('Completed today,${overview.completedToday}')
      ..writeln('Cancelled today,${overview.cancelledToday}')
      ..writeln('New patients today,${overview.newPatientsToday}')
      ..writeln('Active patients (30d),${engagement.activePatients}')
      ..writeln('Documents received (30d),${engagement.documentsReceived}')
      ..writeln('Video consultations,${types.video}')
      ..writeln('In-person consultations,${types.inPerson}')
      ..writeln()
      ..writeln('Date,Appointments');

    for (final point in trend) {
      buffer.writeln('${point.date},${point.count}');
    }

    final bytes = Uint8List.fromList(utf8.encode(buffer.toString()));
    final filename =
        'shifa_dashboard_${range.start.toIso8601String().substring(0, 10)}_${range.end.toIso8601String().substring(0, 10)}.csv';

    await file_download.downloadBytes(
      bytes,
      filename: filename,
      mimeType: 'text/csv;charset=utf-8',
    );
  }
}

final homeDashboardExportServiceProvider =
    Provider<HomeDashboardExportService>((ref) => HomeDashboardExportService(ref));
