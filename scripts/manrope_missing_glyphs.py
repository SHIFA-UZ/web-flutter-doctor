#!/usr/bin/env python3
"""Adds the characters the app's strings use but Manrope lacks to the app's Manrope files, in place.

The phones fall back to a system font for a missing character; the web has no system fonts and draws nothing. Manrope
4.5 lacks Ўзбекча's Қ қ Ғ ғ Ҳ ҳ, Uzbek Latin's ʻ (oʻ, gʻ) and ʼ, and the symbols ⋮ ✓ ⚠ ◷ (and the emoji selector after
⚠). Everything is built from Manrope's own glyphs and stroke weights, per font file:

- Қ қ Ҳ ҳ: К к Х х with a descender (the stem of I / ı, the depth of Ц / ц); Ғ ғ: Г г with a bar.
- ʻ ʼ: the same glyphs as ‘ ’.
- ⋮: three of Manrope's dots; ✓: two strokes of the stem's weight; ⚠: a triangle of that weight around Manrope's "!";
  ◷ (the chat's "sending" clock): a ring with two hands.
- U+FE0F (emoji presentation selector): an empty glyph of no width.

`StringsTest` checks that every character of every string is in the fonts. Re-run after updating the fonts (what's
already there is left alone):

    python3 scripts/manrope_missing_glyphs.py

Needs fontTools (`pip install fonttools`).
"""
import glob
import math
import os

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.transformPen import TransformPen
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

# character → the existing character whose glyph it shares
ALIASES = {"ʻ": "‘", "ʼ": "’"}


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


def polygon(pen, points, hole=False):
    """A closed polygon, clockwise as TrueType's outer contours are (counter-clockwise for a hole)."""
    area = sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]))
    if (area > 0) != hole:  # positive area = counter-clockwise
        points = points[::-1]
    pen.moveTo(tuple(map(round, points[0])))
    for p in points[1:]:
        pen.lineTo(tuple(map(round, p)))
    pen.closePath()


def rect(pen, x0, y0, x1, y1):
    polygon(pen, [(x0, y0), (x0, y1), (x1, y1), (x1, y0)])


def stroke(pen, a, b, width):
    """A straight stroke from a to b, extended by half its width at both ends so joined strokes meet."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    nx, ny = -uy * width / 2, ux * width / 2
    a = (a[0] - ux * width / 2, a[1] - uy * width / 2)
    b = (b[0] + ux * width / 2, b[1] + uy * width / 2)
    polygon(pen, [(a[0] + nx, a[1] + ny), (b[0] + nx, b[1] + ny), (b[0] - nx, b[1] - ny), (a[0] - nx, a[1] - ny)])


def add_glyph(font, char, glyph, advance):
    name = "uni%04X" % ord(char)
    font["glyf"][name] = glyph  # also lists the name in glyf's glyph order
    if name not in font.getGlyphOrder():
        font.setGlyphOrder(font.getGlyphOrder() + [name])
    glyph.recalcBounds(font["glyf"])
    font["hmtx"][name] = (advance, getattr(glyph, "xMin", 0))
    map_char(font, char, name)


def map_char(font, char, name):
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[ord(char)] = name


def add_letters(font, glyph_set, cmap, added):
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
        add_glyph(font, letter, pen.glyph(), font["hmtx"][base][0])
        added.append(letter)


def add_symbols(font, glyph_set, cmap, added):
    cap = bounds(glyph_set, cmap[ord("I")])
    stem, cap_height = cap[2] - cap[0], cap[3]
    side = 120  # side bearing of the new symbols

    if ord("⋮") not in cmap:
        dot = cmap[ord(".")]
        dx0, _, dx1, dy1 = bounds(glyph_set, dot)
        advance = font["hmtx"][dot][0]
        pen = TTGlyphPen(None)
        for y in (0, (cap_height - dy1) / 2, cap_height - dy1):  # three dots over the cap height
            outline(glyph_set, dot).replay(TransformPen(pen, (1, 0, 0, 1, 0, round(y))))
        add_glyph(font, "⋮", pen.glyph(), advance)
        added.append("⋮")

    if ord("✓") not in cmap:
        width, height = cap_height * 0.95, cap_height * 0.8
        x0, x1 = side + stem / 2, side + stem / 2 + width
        pen = TTGlyphPen(None)
        corner = (x0 + width * 0.36, stem / 2)
        stroke(pen, (x0, height * 0.48), corner, stem)
        stroke(pen, corner, (x1, height), stem)
        add_glyph(font, "✓", pen.glyph(), round(x1 + stem / 2 + side))
        added.append("✓")

    if ord("⚠") not in cmap:
        height = cap_height
        width = height * 1.15
        a, b, c = (side, 0), (side + width, 0), (side + width / 2, height)
        # The hole: the triangle shrunk towards its incentre by the stroke's width.
        la, lb, lc = math.dist(b, c), math.dist(a, c), math.dist(a, b)
        p = la + lb + lc
        incentre = ((la * a[0] + lb * b[0] + lc * c[0]) / p, (la * a[1] + lb * b[1] + lc * c[1]) / p)
        inradius = math.sqrt((p / 2 - la) * (p / 2 - lb) * (p / 2 - lc) / (p / 2))
        k = (inradius - stem * 0.9) / inradius
        inner = [(incentre[0] + (x - incentre[0]) * k, incentre[1] + (y - incentre[1]) * k) for x, y in (a, b, c)]
        pen = TTGlyphPen(None)
        polygon(pen, [a, b, c])
        polygon(pen, inner, hole=True)
        bang = cmap[ord("!")]
        bx0, _, bx1, by1 = bounds(glyph_set, bang)
        scale = height * 0.42 / by1
        dx = side + width / 2 - (bx0 + bx1) / 2 * scale
        outline(glyph_set, bang).replay(TransformPen(pen, (scale, 0, 0, scale, round(dx), round(height * 0.17))))
        add_glyph(font, "⚠", pen.glyph(), round(side + width + side))
        added.append("⚠")

    if ord("◷") not in cmap:  # the chat's "sending" clock: a ring and two hands, the hands to the upper right
        radius = cap_height * 0.45
        cx, cy = side + radius, cap_height / 2
        width = stem * 0.8
        ring = [(cx + radius * math.cos(t), cy + radius * math.sin(t)) for t in (i * math.pi / 24 for i in range(48))]
        hole = [(cx + (radius - width) * math.cos(t), cy + (radius - width) * math.sin(t)) for t in (i * math.pi / 24 for i in range(48))]
        pen = TTGlyphPen(None)
        polygon(pen, ring)
        polygon(pen, hole, hole=True)
        stroke(pen, (cx, cy), (cx, cy + radius * 0.55), width)
        stroke(pen, (cx, cy), (cx + radius * 0.45, cy), width)
        add_glyph(font, "◷", pen.glyph(), round(cx + radius + side))
        added.append("◷")

    if 0xFE0F not in cmap:
        add_glyph(font, "️", TTGlyphPen(None).glyph(), 0)
        added.append("U+FE0F")


def complete(path):
    font = TTFont(path)
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    added = []
    add_letters(font, glyph_set, cmap, added)
    add_symbols(font, glyph_set, cmap, added)
    for char, same in ALIASES.items():
        if ord(char) not in cmap:
            map_char(font, char, cmap[ord(same)])
            added.append(char)
    if added:
        font.save(path)
    return added


if __name__ == "__main__":
    for path in sorted(glob.glob(FONTS)):
        print(os.path.basename(path), "added:", " ".join(complete(path)) or "nothing (already there)")
