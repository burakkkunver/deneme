"""Demo için uygun mahalleyi seçmek üzere aday merkezleri karşılaştırır.

Her merkez için rastgele noktalar OSRM ile en yakın yola bağlanır, uygulamadaki
filtreyle (isimli, otoyol/kavşak olmayan, 80 m'den yakın) elenir ve kalanlar için
OSRM trip ile yol mesafesi / kuş uçuşu oranı hesaplanır.
"""
import json
import math
import random
import re
import time
import urllib.request

BASE = "https://router.project-osrm.org"
MAJOR_ROAD = re.compile(r"otoyol|kavşa|bağlantı|evre yolu|köprü|viyadük|tünel|\bTEM\b|\bE-?5\b|\bD-?100\b|\bO-?[1-7]\b", re.I)
CENTERS = {
    "Ataşehir merkez": (40.9923, 29.1244),
    "Kadıköy Göztepe": (40.9775, 29.0640),
    "Kadıköy Feneryolu": (40.9790, 29.0450),
    "Şişli Kurtuluş": (41.0560, 28.9830),
    "Beşiktaş Levent": (41.0785, 29.0140),
    "Üsküdar Ünalan": (41.0050, 29.0600),
    "Maltepe Bağlarbaşı": (40.9330, 29.1300),
    "Bakırköy Kartaltepe": (40.9870, 28.8700),
}


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "rota-asistani-ci-explore"})
    with urllib.request.urlopen(request, timeout=40) as response:
        return json.load(response)


def haversine(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(min(1.0, math.sqrt(h)))


def disk(center, radius, rng):
    r = radius * math.sqrt(rng.random())
    theta = 2 * math.pi * rng.random()
    return (
        center[0] + r * math.cos(theta) / 111_320,
        center[1] + r * math.sin(theta) / (111_320 * math.cos(math.radians(center[0]))),
    )


def coords(points):
    return ";".join(f"{lon:.6f},{lat:.6f}" for lat, lon in points)


for label, center in CENTERS.items():
    for radius in (750, 1000):
        rng = random.Random(2026)
        points = [disk(center, radius, rng) for _ in range(95)]
        try:
            snaps = get(f"{BASE}/table/v1/driving/{coords(points)}?destinations=0")["sources"]
            valid = [
                (s["location"][1], s["location"][0])
                for s in snaps
                if s.get("name") and s["distance"] <= 80 and not MAJOR_ROAD.search(s["name"])
            ]
            time.sleep(1)
            sample = valid[:65]
            trip = get(f"{BASE}/trip/v1/driving/{coords(sample)}?source=first&roundtrip=false&destination=any&overview=false")
            order = sorted(range(len(sample)), key=lambda i: trip["waypoints"][i]["waypoint_index"])
            straight = sum(haversine(sample[order[i]], sample[order[i + 1]]) for i in range(len(order) - 1))
            road = trip["trips"][0]["distance"]
            print(
                f"{label:<22} r={radius:>4} m  geçerli {len(valid):>2}/95  "
                f"yol {road / 1000:5.1f} km  kuş uçuşu {straight / 1000:5.1f} km  oran {road / straight:4.2f}"
            )
        except Exception as error:  # noqa: BLE001 - keşif betiği
            print(f"{label:<22} r={radius:>4} m  hata: {error}")
        time.sleep(1)
