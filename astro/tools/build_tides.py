#!/usr/bin/env python3
"""Build astro/src/main/res/raw/tide_stations.bin from the TICON-4 CSV.

Source: Hart-Davis, Dettmering, Seitz (2025). TICON-4: TIdal CONstants based on GESLA-4 sea-level records.
SEANOE. https://doi.org/10.17882/109129  -- licensed CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).
Changes made here: lake gauges dropped; gauges whose record ended before 1990 dropped (historic records, e.g. Boston
Charlestown 1867-77, were ~30 min off modern predictions: harbour changes / old time conventions); duplicate gauges (same spot, several data providers) merged by keeping the
best-quality / longest record; gauges with negligible tide (M2+S2+K1+O1 < 10 cm) dropped; only the constituents in
CONS kept; amplitudes rounded to 1 mm, phases to 0.1 deg.

Usage: python3 astro/tools/build_tides.py /path/to/TICON_4.csv
Then rebuild the chart datums (they're tied to this file by checksum): see TideDatums.kt / BuildTideDatums.kt.
Format (big-endian, read by Tides.kt): "NWT1", u16 nCons, nCons x UTF(name), u16 nStations, then per station:
f32 lat, f32 lon, UTF(name), nCons x (u16 amp_mm, u16 phase_decideg).
"""
import csv, struct, sys
from pathlib import Path

# Must match Tides.kt CONS order.
CONS = ["SA", "SSA", "MM", "MSF", "MF", "2Q1", "SIGMA1", "Q1", "RHO1", "O1", "P1", "S1", "K1", "J1", "OO1",
        "EPS2", "2N2", "MU2", "N2", "NU2", "M2", "LAMBDA2", "L2", "T2", "S2", "R2", "K2", "2SM2", "MKS2",
        "M3", "S3", "MN4", "M4", "MS4", "S4", "M6", "2MS6", "M8"]
MIN_END_YEAR = 1990
QUALITY = {"No obvious issues": 0, "Possible datum issues": 1, "Possible quality control issues": 2,
           "Possible datum and quality control issues": 3}

def utf(s):
    b = s.encode("utf-8")[:200]
    return struct.pack(">H", len(b)) + b

def main(src, out):
    gauges = {}
    for r in csv.DictReader(open(src, newline="")):
        if r["type"] == "Lake" or int(r["end_date"][-4:]) < MIN_END_YEAR:
            continue
        key = (r["lat"], r["lon"], r["tide_gauge_name"])
        g = gauges.setdefault(key, {"lat": float(r["lat"]), "lon": float(r["lon"]), "name": r["tide_gauge_name"],
                                    "q": QUALITY.get(r["record_quality"], 3), "years": float(r["years_of_obs"]), "c": {}})
        if r["con"] in CONS:
            g["c"][r["con"]] = (float(r["amp"]), float(r["pha"]) % 360)
    # Merge duplicates (~1 km cells): best quality, then longest record.
    best = {}
    for g in gauges.values():
        cell = (round(g["lat"], 2), round(g["lon"], 2))
        if cell not in best or (g["q"], -g["years"]) < (best[cell]["q"], -best[cell]["years"]):
            best[cell] = g
    keep = [g for g in best.values() if sum(g["c"].get(k, (0, 0))[0] for k in ("M2", "S2", "K1", "O1")) >= 10.0]
    keep.sort(key=lambda g: (g["lat"], g["lon"]))
    buf = bytearray(b"NWT1") + struct.pack(">H", len(CONS))
    for c in CONS:
        buf += utf(c)
    buf += struct.pack(">H", len(keep))
    for g in keep:
        buf += struct.pack(">ff", g["lat"], g["lon"]) + utf(g["name"])
        for c in CONS:
            amp_cm, pha = g["c"].get(c, (0.0, 0.0))
            buf += struct.pack(">HH", min(65535, round(amp_cm * 10)), round(pha * 10) % 3600)
    Path(out).write_bytes(buf)
    print(f"{len(gauges)} gauges -> {len(best)} after merge -> {len(keep)} with tides; {len(buf)} bytes -> {out}")

if __name__ == "__main__":
    here = Path(__file__).resolve().parent
    main(sys.argv[1], here.parent / "src/main/res/raw/tide_stations.bin")
