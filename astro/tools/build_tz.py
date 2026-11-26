#!/usr/bin/env python3
"""Build astro/src/main/res/raw/tz_centres.txt from the IANA tz database's zone.tab (public domain).

Each zone's principal city: the backup location when the watch has no location fix, or when the saved fix is far
from the time zone the watch is set to (TzCentres.kt). zone.tab (not zone1970.tab) so every country's own zone
keeps its own city (Europe/Oslo, not Berlin).

Usage: python3 astro/tools/build_tz.py [/usr/share/zoneinfo/zone.tab]
Format: one line per zone, "Zone/Name lat lon" (degrees, 2 decimals), first line "# tzdata <version>".
"""
import re, sys
from pathlib import Path

def deg(s, width):
    """+DDMM[SS] / +DDDMM[SS] -> degrees."""
    sign = -1 if s[0] == "-" else 1
    d, m, sec = int(s[1:1 + width]), int(s[1 + width:3 + width]), int(s[3 + width:5 + width] or 0)
    return sign * (d + m / 60 + sec / 3600)

def main(src):
    src = Path(src)
    version = (src.parent / "+VERSION").read_text().strip() if (src.parent / "+VERSION").exists() else "?"
    rows = []
    for line in src.read_text().splitlines():
        if line.startswith("#") or not line.strip():
            continue
        _, coord, zone = line.split("\t")[:3]
        lat, lon = re.match(r"([+-]\d+)([+-]\d+)$", coord).groups()
        rows.append(f"{zone} {deg(lat, 2):.2f} {deg(lon, 3):.2f}")
    out = Path(__file__).resolve().parent.parent / "src" / "main" / "res" / "raw" / "tz_centres.txt"
    out.write_text(f"# tzdata {version}, zone.tab\n" + "\n".join(sorted(rows)) + "\n")
    print(f"wrote {len(rows)} zones to {out}")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/usr/share/zoneinfo/zone.tab")
