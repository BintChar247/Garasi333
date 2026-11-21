#!/usr/bin/env python3
"""Pre-seeds the Garasi desktop data file with one PC-only item and a known pairing code (CI test only)."""
import json, os, sys, time
d = os.path.join(os.path.expanduser("~"), ".garasi")
os.makedirs(os.path.join(d, "photos"), exist_ok=True)
now = int(time.time() * 1000)
data = {
    "snapshot": {"items": [{
        "uid": "pc-canon-1", "createdAt": now - 3 * 86400000, "updatedAt": now, "status": "READY",
        "category": "ELECTRONICS", "brand": "Canon", "model": "EOS M10", "variant": "Kit 15-45mm",
        "condition": "LIKE_NEW", "titleId": "Canon EOS M10 Kit 15-45mm Like New Mulus", "askPrice": 2900000,
        "descId": "Jual Canon EOS M10 kit 15-45mm, jarang dipakai. Dibuat di Garasi for Windows.",
    }]},
    "token": sys.argv[1], "port": 8737,
}
json.dump(data, open(os.path.join(d, "data.json"), "w"))
print("seeded", d)
