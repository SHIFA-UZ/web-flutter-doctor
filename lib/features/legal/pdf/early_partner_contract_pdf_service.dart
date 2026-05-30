// lib/features/legal/pdf/early_partner_contract_pdf_service.dart

import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import 'early_partner_contract_pdf_data.dart';

const double _marginPt = 40;

PdfColor get _brandTeal => PdfColor(0 / 255, 187 / 255, 176 / 255);

PdfPageFormat get _pageFormat => PdfPageFormat.a4.copyWith(
      marginLeft: _marginPt,
      marginRight: _marginPt,
      marginTop: _marginPt,
      marginBottom: _marginPt,
    );

/// Generates the SHIFA early-user partnership contract (Uzbek), A4, branded.
Future<Uint8List> generateEarlyPartnerContractPdf({
  required EarlyPartnerContractPdfData data,
}) async {
  final fontData = await rootBundle.load('assets/fonts/DejaVuSans.ttf');
  final font = pw.Font.ttf(fontData);

  pw.ImageProvider? logoImage;
  try {
    final logoData = await rootBundle.load('assets/branding/shifa_logo.png');
    logoImage = pw.MemoryImage(logoData.buffer.asUint8List());
  } catch (_) {}

  final pdf = pw.Document();
  final pageTheme = pw.PageTheme(
    pageFormat: _pageFormat,
    theme: pw.ThemeData.withFont(base: font),
    buildBackground: (ctx) {
      if (logoImage == null) return pw.SizedBox();
      return pw.FullPage(
        ignoreMargins: true,
        child: pw.Center(
          child: pw.Opacity(
            opacity: 0.07,
            child: pw.Image(
              logoImage,
              width: 280,
              height: 280,
              fit: pw.BoxFit.contain,
            ),
          ),
        ),
      );
    },
  );

  pdf.addPage(
    pw.MultiPage(
      pageTheme: pageTheme,
      maxPages: 12,
      build: (ctx) => _contractBody(data, font, logoImage),
      footer: (ctx) => _footer(font, ctx.pageNumber, ctx.pagesCount, data),
    ),
  );

  await Future<void>.delayed(Duration.zero);
  return pdf.save();
}

List<pw.Widget> _contractBody(
  EarlyPartnerContractPdfData data,
  pw.Font font,
  pw.ImageProvider? logoImage,
) {
  return [
    _header(font, logoImage, data),
    pw.SizedBox(height: 12),
    _titleBlock(font),
    pw.SizedBox(height: 14),
    _metaStrip(font, data),
    pw.SizedBox(height: 16),
    _section('1. MAQSAD', font, [
      _p(
        font,
        'Mazkur Shartnoma SHIFA va Hamkor o\'rtasida SHIFA telemeditsina platformasini '
        'dastlabki joriy etish bosqichida sinovdan o\'tkazish, tekshirish va takomillashtirish '
        'maqsadida hamkorlik munosabatlarini belgilaydi. Har ikki tomon ushbu hamkorlik '
        'O\'zbekistonda qulay raqamli sog\'liqni saqlash tizimini rivojlantirish uchun '
        'o\'zaro manfaatli ekanini tan oladi.',
      ),
    ]),
    _section('2. SHIFA TOMONIDAN TAQDIM ETILADIGAN XIZMATLAR', font, [
      _p(
        font,
        'Mazkur Shartnoma amal qiladigan davr mobaynida SHIFA Hamkorga quyidagilarni bepul taqdim etadi:',
      ),
      pw.SizedBox(height: 8),
      _subsection('2.1 Platformadan to\'liq foydalanish', font),
      _bullets(font, [
        'SHIFA shifokorlar ilovasining barcha funksiyalaridan to\'liq foydalanish (shifokor-hamkorlar uchun)',
        'SHIFA bemorlar ilovasining barcha funksiyalaridan to\'liq foydalanish (bemor-hamkorlar uchun)',
        'Shartnoma muddati davomida chiqarilgan barcha yangilanishlar va yangi funksiyalar',
        'Qabul yozuvlari, raqamli tibbiy kartalar va telemeditsina konsultatsiyalarini boshqarishda hech qanday cheklovlarning yo\'qligi',
      ]),
      pw.SizedBox(height: 8),
      _subsection('2.2 Masofaviy qo\'llab-quvvatlash', font),
      _bullets(font, [
        'Telegram/WhatsApp orqali alohida texnik yordam',
        'Javob berish muddati: 24 ish soati ichida',
        'Ulanish, sozlash va nosozliklarni bartaraf etishda yordam',
        'Muammolarni ustuvor tartibda hal qilish',
      ]),
      pw.SizedBox(height: 8),
      _subsection('2.3 Hujjatlar va o\'qitish', font),
      _bullets(font, [
        'Platformaning barcha funksiyalari bo\'yicha to\'liq foydalanuvchi hujjatlari',
        'Muhim funksiyalar bo\'yicha videoqo\'llanmalar va bosqichma-bosqich ko\'rsatmalar',
        'Yangi funksiyalar chiqarilishi bilan yangilanadigan materiallar',
      ]),
    ]),
    _section('3. HAMKORNING MAJBURIYATLARI', font, [
      _p(
        font,
        '2-bo\'limda ko\'rsatilgan xizmatlar evaziga Hamkor quyidagilarga rozilik bildiradi:',
      ),
      pw.SizedBox(height: 8),
      _subsection('3.1 Haftalik fikr-mulohaza sessiyalari', font),
      _bullets(font, [
        'Haftasiga bir (1) marta masofaviy fikr-mulohaza sessiyasida ishtirok etish (taxminan 20–30 daqiqa)',
        'Sessiyalar har ikki tomon kelishgan vaqtda o\'tkaziladi',
        'Platformaning qulayligi, funksiyalari va ishlashi bo\'yicha halol va konstruktiv fikr-mulohaza bildirish',
        'Hafta davomida aniqlangan xatolar, muammolar yoki takliflar haqida xabar berish',
        'Minimal ishtirok: rejalashtirilgan sessiyalarning kamida 80 foizida qatnashish (bayramlar va favqulodda holatlar hisobga olingan holda)',
      ]),
      pw.SizedBox(height: 8),
      _subsection('3.2 Platforma vakilligi', font),
      _bullets(font, [
        'SHIFA platformasini professional va shaxsiy doiralarda faol targ\'ib qilish',
        'Ijobiy tajriba bilan hamkasblar, bemorlar yoki hamjamiyat a\'zolari bilan bo\'lishish',
        'Zarurat tug\'ilganda yangi foydalanuvchilarni jalb qilishga ko\'maklashish',
        'Shartnoma muddati davomida kamida bir (1) ta fikr-mulohaza yoki keyslar tavsifini taqdim etish (Hamkor tomonidan mazmuni tasdiqlangan holda)',
        'Oldindan yozma rozilik mavjud bo\'lsa, reklama materiallarida (foto, iqtibos yoki video) ishtirok etish',
      ]),
    ]),
    _section('4. MA\'LUMOTLAR VA MAXFIYLIK', font, [
      _bullets(font, [
        'SHIFA barcha bemor ma\'lumotlari va tibbiy axborotni O\'zbekiston Respublikasining amaldagi ma\'lumotlarni himoya qilish qonunchiligiga muvofiq qayta ishlash majburiyatini oladi.',
        'Fikr-mulohaza sessiyalarining yozuvlari (agar mavjud bo\'lsa) faqat mahsulotni ichki takomillashtirish maqsadlarida ishlatiladi.',
        'Hech qanday shaxsiy tibbiy ma\'lumotlar aniq roziliksiz uchinchi shaxslarga berilmaydi.',
        'Hamkor SHIFAning yozma ruxsatisiz platformaga oid maxfiy ma\'lumotlar, chiqarilmagan funksiyalar skrinshotlari yoki ichki yozishmalarni uchinchi shaxslarga bermaslikka rozilik bildiradi.',
      ]),
    ]),
    _section('5. INTELLEKTUAL MULK', font, [
      _bullets(font, [
        'Fikr-mulohaza sessiyalarida bildirilgan barcha takliflar va g\'oyalar SHIFA tomonidan mahsulotni rivojlantirish uchun qo\'shimcha kompensatsiyasiz ishlatilishi mumkin.',
        'SHIFA platformasi, uning dizayni, kodi va brendi SHIFAning mutlaq mulki bo\'lib qoladi.',
      ]),
    ]),
    _section('6. AMAL QILISH MUDDATI VA BEKOR QILISH', font, [
      _bullets(font, [
        'Mazkur Shartnoma kuchga kirgan kundan boshlab ${data.termMonths} (olti) oy davomida amal qiladi.',
        'Har qanday tomon 14 kun oldin yozma ravishda xabardor qilish orqali Shartnomani bekor qilishi mumkin.',
        'Bekor qilingandan so\'ng Hamkorning platformaga kirishi standart tarif shartlariga o\'tkaziladi (qo\'llaniladigan tariflar haqida 30 kun oldin xabardor qilinadi).',
      ]),
    ]),
    _section('7. SHARTNOMA YAKUNLANGANDAN KEYINGI AFZALLIKLAR', font, [
      _p(
        font,
        '${data.termMonths} oylik hamkorlik muvaffaqiyatli yakunlangandan so\'ng Hamkor quyidagilarga ega bo\'ladi:',
      ),
      pw.SizedBox(height: 6),
      _bullets(font, [
        'Har qanday pullik tarif rejasining dastlabki 3 oyi uchun 50% chegirma',
        'Platforma profilida "Founding Partner" belgisi',
        'Kelajakdagi beta-funksiyalarga ustuvor kirish huquqi',
      ]),
    ]),
    _section('8. JAVOBGARLIKNI CHEKLASH', font, [
      _bullets(font, [
        'SHIFA platformani dastlabki joriy etish bosqichida "qanday bo\'lsa, shunday" holatda taqdim etadi. SHIFA platforma barqarorligini ta\'minlash uchun barcha sa\'y-harakatlarni amalga oshirsa-da, vaqtinchalik nosozliklar yoki xatolar yuz berishi mumkin.',
        'SHIFA sinov davrida platformadan foydalana olmaslik natijasida yuzaga kelgan bilvosita zararlar uchun javobgar emas.',
        'Hamkor bu sinov bosqichi ekanini tasdiqlaydi va zaxira variant sifatida bemorlar bilan aloqa qilishning muqobil usullarini saqlab turishga rozilik bildiradi.',
      ]),
    ]),
    _section('9. UMUMIY QOIDALAR', font, [
      _bullets(font, [
        'Mazkur Shartnoma tomonlar o\'rtasidagi to\'liq o\'zaro tushunishni ifodalaydi.',
        'Har qanday o\'zgartirishlar yozma shaklda rasmiylashtirilishi va har ikki tomon tomonidan imzolanishi kerak.',
        'Mazkur Shartnoma O\'zbekiston Respublikasi qonunchiligiga muvofiq tartibga solinadi.',
        'Nizolar birinchi navbatda halol muzokaralar yo\'li bilan hal qilinadi. Agar nizo 30 kun ichida hal qilinmasa, u O\'zbekistonning vakolatli sudlariga topshiriladi.',
      ]),
    ]),
    pw.SizedBox(height: 18),
    _partiesBlock(data, font),
    pw.SizedBox(height: 16),
    _signaturesBlock(data, font),
    pw.SizedBox(height: 14),
    _acknowledgement(font),
  ];
}

pw.Widget _header(
  pw.Font font,
  pw.ImageProvider? logoImage,
  EarlyPartnerContractPdfData data,
) {
  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.stretch,
    children: [
      pw.Row(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        children: [
          pw.Row(
            mainAxisSize: pw.MainAxisSize.min,
            crossAxisAlignment: pw.CrossAxisAlignment.center,
            children: [
              if (logoImage != null)
                pw.Container(
                  width: 52,
                  height: 52,
                  child: pw.Image(logoImage, fit: pw.BoxFit.contain),
                ),
              if (logoImage != null) pw.SizedBox(width: 12),
              pw.Column(
                crossAxisAlignment: pw.CrossAxisAlignment.start,
                children: [
                  pw.Text(
                    'SHIFA',
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 22,
                      fontWeight: pw.FontWeight.bold,
                    ),
                  ),
                  pw.Text(
                    'Raqamli tibbiy platforma',
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 9,
                      color: PdfColors.grey700,
                    ),
                  ),
                ],
              ),
            ],
          ),
          pw.Column(
            crossAxisAlignment: pw.CrossAxisAlignment.end,
            children: [
              pw.Text(
                'Hujjat turi: Hamkorlik shartnomasi',
                style: pw.TextStyle(
                  font: font,
                  fontSize: 9,
                  color: PdfColors.grey700,
                ),
              ),
              pw.Text(
                'Versiya: Erta foydalanuvchi',
                style: pw.TextStyle(
                  font: font,
                  fontSize: 9,
                  color: PdfColors.grey700,
                ),
              ),
            ],
          ),
        ],
      ),
      pw.SizedBox(height: 10),
      pw.Container(height: 2, color: _brandTeal),
    ],
  );
}

pw.Widget _titleBlock(pw.Font font) {
  return pw.Column(
    children: [
      pw.Center(
        child: pw.Text(
          'SHIFA ERTA FOYDALANUVCHI HAMKORLIK SHARTNOMASI',
          textAlign: pw.TextAlign.center,
          style: pw.TextStyle(
            font: font,
            fontSize: 16,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
      ),
      pw.SizedBox(height: 8),
      pw.Container(height: 1, color: PdfColors.grey400),
    ],
  );
}

pw.Widget _metaStrip(pw.Font font, EarlyPartnerContractPdfData data) {
  String fmt(DateTime d) =>
      '${d.day.toString().padLeft(2, '0')}.${d.month.toString().padLeft(2, '0')}.${d.year}';

  pw.Widget row(String label, String value) {
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 6),
      child: pw.Row(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          pw.SizedBox(
            width: 155,
            child: pw.Text(
              label,
              style: pw.TextStyle(
                font: font,
                fontSize: 10,
                fontWeight: pw.FontWeight.bold,
              ),
            ),
          ),
          pw.Expanded(
            child: pw.Text(
              value,
              style: pw.TextStyle(font: font, fontSize: 10),
            ),
          ),
        ],
      ),
    );
  }

  return pw.Container(
    decoration: pw.BoxDecoration(
      border: pw.Border.all(color: _brandTeal, width: 1.2),
      color: PdfColor(0 / 255, 187 / 255, 176 / 255, 0.08),
    ),
    padding: const pw.EdgeInsets.all(12),
    child: pw.Column(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        row('Shartnoma raqami:', data.contractNumber),
        row('Kuchga kirish sanasi:', fmt(data.effectiveDate)),
        row('Amal qilish muddati:', 'Kuchga kirgan kundan boshlab ${data.termMonths} (olti) oy'),
      ],
    ),
  );
}

pw.Widget _partiesBlock(EarlyPartnerContractPdfData data, pw.Font font) {
  return _section('TOMONLAR', font, [
    _subsection('Ta\'minotchi (SHIFA)', font),
    _p(font, 'SHIFA raqamli tibbiy platformasi'),
    _p(font, data.supplierLegalName),
    _p(font, data.supplierAddress),
    _p(font, '(keyingi o\'rinlarda "SHIFA" deb yuritiladi)'),
    pw.SizedBox(height: 10),
    _subsection('Erta foydalanuvchi hamkor', font),
    _labelLine(font, 'F.I.Sh.', data.partnerFullName),
    _labelLine(font, 'Klinika/Tashkilot', data.partnerClinic),
    _roleLine(font, data),
    _labelLine(font, 'Bog\'lanish telefoni', data.partnerPhone),
    _labelLine(font, 'Elektron pochta', data.partnerEmail),
    _p(font, '(keyingi o\'rinlarda "Hamkor" deb yuritiladi)'),
  ]);
}

pw.Widget _roleLine(pw.Font font, EarlyPartnerContractPdfData data) {
  String mark(bool on) => on ? '[X]' : '[ ]';
  final line =
      'Roli: ${mark(data.roleDoctor)} Shifokor   ${mark(data.rolePatient)} Bemor   ${mark(data.roleBoth)} Ikkalasi ham';
  return pw.Padding(
    padding: const pw.EdgeInsets.only(bottom: 6),
    child: pw.Text(line, style: pw.TextStyle(font: font, fontSize: 10)),
  );
}

pw.Widget _labelLine(pw.Font font, String label, String? value) {
  final filled = value != null && value.trim().isNotEmpty;
  return pw.Padding(
    padding: const pw.EdgeInsets.only(bottom: 6),
    child: pw.Row(
      crossAxisAlignment: pw.CrossAxisAlignment.end,
      children: [
        pw.Text(
          '$label: ',
          style: pw.TextStyle(
            font: font,
            fontSize: 10,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
        pw.Expanded(
          child: pw.Container(
            decoration: pw.BoxDecoration(
              border: pw.Border(
                bottom: pw.BorderSide(
                  color: filled ? PdfColors.grey800 : PdfColors.grey500,
                  width: filled ? 0 : 0.75,
                ),
              ),
            ),
            padding: const pw.EdgeInsets.only(bottom: 2),
            child: pw.Text(
              filled ? value!.trim() : ' ',
              style: pw.TextStyle(font: font, fontSize: 10),
            ),
          ),
        ),
      ],
    ),
  );
}

pw.Widget _signaturesBlock(EarlyPartnerContractPdfData data, pw.Font font) {
  String fmt(DateTime? d) {
    if (d == null) return '________________';
    return '${d.day.toString().padLeft(2, '0')} ${_monthUz(d.month)} ${d.year}';
  }

  return _section('IMZOLAR', font, [
    pw.Row(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        pw.Expanded(child: _sigColumn(
          font,
          title: 'SHIFA nomidan',
          nameLabel: 'Ism',
          name: data.shifaSignatoryName,
          roleLabel: 'Lavozim',
          role: data.shifaSignatoryTitle,
          date: fmt(data.shifaSignedDate),
        )),
        pw.SizedBox(width: 20),
        pw.Expanded(child: _sigColumn(
          font,
          title: 'Hamkor',
          nameLabel: 'Ism',
          name: data.partnerFullName?.trim().isNotEmpty == true
              ? data.partnerFullName!.trim()
              : null,
          roleLabel: null,
          role: null,
          date: null,
        )),
      ],
    ),
  ]);
}

String _monthUz(int m) {
  const names = [
    'Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'Iyun',
    'Iyul', 'Avgust', 'Sentabr', 'Oktabr', 'Noyabr', 'Dekabr',
  ];
  return names[m - 1];
}

pw.Widget _sigColumn(
  pw.Font font, {
  required String title,
  required String nameLabel,
  String? name,
  String? roleLabel,
  String? role,
  String? date,
}) {
  pw.Widget line(String label, String? value) {
    final v = (value != null && value.trim().isNotEmpty) ? value.trim() : '_______________________________';
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 8),
      child: pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          pw.Text(
            '$label:',
            style: pw.TextStyle(
              font: font,
              fontSize: 9,
              fontWeight: pw.FontWeight.bold,
            ),
          ),
          pw.SizedBox(height: 2),
          pw.Text(v, style: pw.TextStyle(font: font, fontSize: 10)),
        ],
      ),
    );
  }

  return pw.Container(
    padding: const pw.EdgeInsets.all(10),
    decoration: pw.BoxDecoration(
      border: pw.Border.all(color: PdfColors.grey600, width: 0.75),
    ),
    child: pw.Column(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        pw.Text(
          title,
          style: pw.TextStyle(
            font: font,
            fontSize: 11,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
        pw.SizedBox(height: 8),
        line(nameLabel, name),
        if (roleLabel != null) line(roleLabel, role),
        pw.SizedBox(height: 4),
        pw.Text(
          'Imzo:',
          style: pw.TextStyle(
            font: font,
            fontSize: 9,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
        pw.SizedBox(height: 18),
        pw.Container(height: 1, color: PdfColors.grey800),
        pw.SizedBox(height: 8),
        pw.Text(
          'Sana: ${date ?? '_______________________________'}',
          style: pw.TextStyle(font: font, fontSize: 10),
        ),
      ],
    ),
  );
}

pw.Widget _acknowledgement(pw.Font font) {
  return pw.Container(
    width: double.infinity,
    padding: const pw.EdgeInsets.all(10),
    decoration: pw.BoxDecoration(
      color: PdfColors.grey100,
      border: pw.Border.all(color: PdfColors.grey500, width: 0.5),
    ),
    child: pw.Text(
      'Har ikki tomon ushbu Shartnoma shartlarini o\'qib chiqqani, tushungani va ularga '
      'rozilik bildirganini tasdiqlaydi.',
      textAlign: pw.TextAlign.center,
      style: pw.TextStyle(
        font: font,
        fontSize: 10,
        fontStyle: pw.FontStyle.italic,
      ),
    ),
  );
}

pw.Widget _section(String title, pw.Font font, List<pw.Widget> body) {
  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.stretch,
    children: [
      pw.SizedBox(height: 10),
      pw.Container(
        width: double.infinity,
        padding: const pw.EdgeInsets.symmetric(vertical: 6, horizontal: 8),
        color: PdfColors.grey300,
        child: pw.Text(
          title,
          style: pw.TextStyle(
            font: font,
            fontSize: 11,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
      ),
      pw.Container(
        width: double.infinity,
        decoration: const pw.BoxDecoration(
          border: pw.Border(
            left: pw.BorderSide(color: PdfColors.grey600, width: 0.75),
            right: pw.BorderSide(color: PdfColors.grey600, width: 0.75),
            bottom: pw.BorderSide(color: PdfColors.grey600, width: 0.75),
          ),
          color: PdfColors.grey50,
        ),
        padding: const pw.EdgeInsets.all(12),
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: body,
        ),
      ),
    ],
  );
}

pw.Widget _subsection(String title, pw.Font font) {
  return pw.Padding(
    padding: const pw.EdgeInsets.only(bottom: 4),
    child: pw.Text(
      title,
      style: pw.TextStyle(
        font: font,
        fontSize: 10.5,
        fontWeight: pw.FontWeight.bold,
        color: PdfColors.grey800,
      ),
    ),
  );
}

pw.Widget _p(pw.Font font, String text) {
  return pw.Padding(
    padding: const pw.EdgeInsets.only(bottom: 4),
    child: pw.Paragraph(
      text: text,
      style: pw.TextStyle(font: font, fontSize: 10),
      margin: pw.EdgeInsets.zero,
    ),
  );
}

pw.Widget _bullets(pw.Font font, List<String> items) {
  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.start,
    children: [
      for (final item in items)
        pw.Padding(
          padding: const pw.EdgeInsets.only(left: 4, bottom: 4),
          child: pw.Row(
            crossAxisAlignment: pw.CrossAxisAlignment.start,
            children: [
              pw.Text('• ', style: pw.TextStyle(font: font, fontSize: 10)),
              pw.Expanded(
                child: pw.Text(
                  item,
                  style: pw.TextStyle(font: font, fontSize: 10),
                ),
              ),
            ],
          ),
        ),
    ],
  );
}

pw.Widget _footer(
  pw.Font font,
  int pageNumber,
  int totalPages,
  EarlyPartnerContractPdfData data,
) {
  return pw.Container(
    decoration: const pw.BoxDecoration(
      border: pw.Border(top: pw.BorderSide(color: PdfColors.grey400, width: 0.75)),
    ),
    padding: const pw.EdgeInsets.only(top: 8),
    child: pw.Column(
      children: [
        pw.Row(
          mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
          children: [
            pw.Expanded(
              child: pw.Text(
                'SHIFA — ${data.contractNumber}',
                style: pw.TextStyle(
                  font: font,
                  fontSize: 9,
                  color: PdfColors.grey700,
                ),
              ),
            ),
            pw.Text(
              'Sahifa $pageNumber / $totalPages',
              style: pw.TextStyle(
                font: font,
                fontSize: 9,
                color: PdfColors.grey700,
              ),
            ),
          ],
        ),
        pw.SizedBox(height: 4),
        pw.Center(
          child: pw.Text(
            'Maxfiy hujjat. Ruxsatsiz tarqatish taqiqlanadi.',
            style: pw.TextStyle(
              font: font,
              fontSize: 8,
              color: PdfColors.grey600,
            ),
          ),
        ),
      ],
    ),
  );
}
