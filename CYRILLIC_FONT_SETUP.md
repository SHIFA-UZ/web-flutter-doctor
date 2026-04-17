# Cyrillic Font Setup for PDF Generation

To support Cyrillic characters in PDF generation, you need to:

1. Download a font that supports Cyrillic (recommended: DejaVu Sans)
   - Download from: https://dejavu-fonts.github.io/Download.html
   - Get the TTF file: DejaVuSans.ttf

2. Add the font to your project:
   - Create `assets/fonts/` directory if it doesn't exist
   - Place `DejaVuSans.ttf` in `assets/fonts/`

3. Update `pubspec.yaml`:
   ```yaml
   flutter:
     assets:
       - assets/fonts/DejaVuSans.ttf
   ```

4. The code in `patient_form_screen.dart` will automatically use the font once it's added.
