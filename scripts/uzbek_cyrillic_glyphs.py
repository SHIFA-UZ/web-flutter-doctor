#!/usr/bin/env python3
"""Adds the Uzbek Cyrillic letters Manrope lacks (Қ қ Ғ ғ Ҳ ҳ) to the app's Manrope files, in place.

Manrope 4.5 has no glyph for them: the phones fall back to a system font, the web (no system fonts) draws nothing,
so Ўзбекча showed gaps. Each letter is built from Manrope's own К, Г, Х with a descender or a bar sized from the
same weight's strokes: the stem of `I` / `ı` and the descender depth of Ц / ц. Re-run after updating the fonts
(letters already present are left alone):

    python3 scripts/uzbek_cyrillic_glyphs.py

Needs fontTools (`pip install fonttools`).
"""
import glob
import os

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

FONTS = os.path.join(os.path.dirname(__file__), "..", "composeApp/src/commonMain/composeResources/font/manrope_*.ttf")

# new letter → (base letter, what is added, stem reference, descender reference)
LETTERS = {
    "Қ": ("К", "descender", "I", "Ц"),
    "қ": ("к", "descender", "ı", "ц"),
    "Ҳ": ("Х", "descender", "I", "Ц"),
    "ҳ": ("х", "descender", "ı", "ц"),
    "Ғ": ("Г", "bar", "I", None),
    "ғ": ("г", "bar", "ı", None),
}


def bounds(glyph_set, name):
    pen = BoundsPen(glyph_set)
    glyph_set[name].draw(pen)
    return pen.bounds  # xMin, yMin, xMax, yMax


def outline(glyph_set, name):
    pen = DecomposingRecordingPen(glyph_set)
    glyph_set[name].draw(pen)
    return pen


def baseline_right(recording):
    """The rightmost x of the points on the baseline: where the right leg of К or Х ends."""
    xs = [x for _, args in recording.value for (x, y) in args if abs(y) <= 1]
    return max(xs)


def rect(pen, x0, y0, x1, y1):
    # Clockwise, as TrueType's outer contours are.
    pen.moveTo((x0, y0))
    pen.lineTo((x0, y1))
    pen.lineTo((x1, y1))
    pen.lineTo((x1, y0))
    pen.closePath()


def add_letters(path):
    font = TTFont(path)
    cmap = font.getBestCmap()
    glyph_set = font.getGlyphSet()
    added = []
    for letter, (base_letter, part, stem_letter, desc_letter) in LETTERS.items():
        if ord(letter) in cmap:
            continue
        base = cmap[ord(base_letter)]
        stem_b = bounds(glyph_set, cmap[ord(stem_letter)])
        stem = stem_b[2] - stem_b[0]
        x_min, _, _, y_max = bounds(glyph_set, base)
        recording = outline(glyph_set, base)
        pen = TTGlyphPen(None)
        recording.replay(pen)
        if part == "descender":
            depth = bounds(glyph_set, cmap[ord(desc_letter)])[1]  # negative
            right = baseline_right(recording)
            rect(pen, round(right - stem), round(depth), round(right), round(stem * 0.9))
        else:  # the bar across the stem, a little left of it and well to the right
            thick = round(stem * 0.88)
            mid = round(y_max * 0.47)
            rect(pen, round(x_min - stem * 0.35), mid - thick // 2, round(x_min + stem * 2.2), mid - thick // 2 + thick)
        name = "uni%04X" % ord(letter)
        font["glyf"][name] = pen.glyph()  # also lists the name in glyf's glyph order
        if name not in font.getGlyphOrder():
            font.setGlyphOrder(font.getGlyphOrder() + [name])
        glyph = font["glyf"][name]
        glyph.recalcBounds(font["glyf"])
        font["hmtx"][name] = (font["hmtx"][base][0], glyph.xMin)
        for table in font["cmap"].tables:
            if table.isUnicode():
                table.cmap[ord(letter)] = name
        added.append(letter)
    if added:
        font.save(path)
    return added


if __name__ == "__main__":
    for path in sorted(glob.glob(FONTS)):
        print(os.path.basename(path), "added:", "".join(add_letters(path)) or "nothing (already there)")
