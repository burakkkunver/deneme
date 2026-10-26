"""Emülatördeki uygulamanın kaydettiği rotayı OSRM ile karşılaştırır.

Amaç: yol ağı mesafesinin kuş uçuşuna göre makul olup olmadığını ve hangi
durakların ana yollara/otoyola "yapıştığını" görmek.
Kullanım: python3 route-diagnostics.py rota_asistani_state.xml
"""
import json
import math
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

BASE = "https://router.project-osrm.org"


def haversine(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(min(1.0, math.sqrt(h)))


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "rota-asistani-ci-diagnostics"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main(path):
    root = ET.parse(path).getroot()
    state = json.loads(root.find("string[@name='state']").text)
    stops = {s["id"]: s for s in state["stops"]}
    order = [stops[i] for i in state["routeIds"] if i in stops]
    depot = state["depot"]
    print(
        "uygulama: yol ağı={} plan={:.0f} m, en-yakın={:.0f} m, liste={:.0f} m, {} durak".format(
            state.get("plannedOnRoads"),
            state.get("optimizedMeters") or 0,
            state.get("greedyMeters") or 0,
            state.get("baselineMeters") or 0,
            len(order),
        )
    )
    points = [(depot["lat"], depot["lon"])] + [(s["lat"], s["lon"]) for s in order]
    straight = [haversine(points[i], points[i + 1]) for i in range(len(points) - 1)]
    print("kuş uçuşu rota: {:.0f} m (x1.3 = {:.0f} m)".format(sum(straight), sum(straight) * 1.3))

    coords = ";".join("{:.6f},{:.6f}".format(lon, lat) for lat, lon in points)
    for label, extra in (("varsayılan", ""), ("otoyolsuz", "&exclude=motorway")):
        try:
            data = get(f"{BASE}/route/v1/driving/{coords}?overview=false{extra}")
            if data.get("code") != "Ok":
                print(f"OSRM rota ({label}): code={data.get('code')} {data.get('message')}")
                continue
            legs = data["routes"][0]["legs"]
            print("OSRM rota ({}): {:.0f} m".format(label, data["routes"][0]["distance"]))
            worst = sorted(range(len(legs)), key=lambda k: legs[k]["distance"] - straight[k], reverse=True)[:6]
            for k in worst:
                print("   bacak {:>2}: yol {:6.0f} m, kuş uçuşu {:5.0f} m".format(k + 1, legs[k]["distance"], straight[k]))
        except Exception as error:  # noqa: BLE001 - teşhis betiği
            print(f"OSRM rota ({label}) hatası: {error}")
        time.sleep(1)

    print("durakların yapıştığı yollar (uzak olanlar işaretli):")
    names = {}
    for stop in order:
        try:
            data = get(f"{BASE}/nearest/v1/driving/{stop['lon']:.6f},{stop['lat']:.6f}?number=1")
            waypoint = data["waypoints"][0]
            name = waypoint.get("name") or "(isimsiz)"
            names[name] = names.get(name, 0) + 1
            if waypoint["distance"] > 60:
                print("   {:>4} {:<40} {:6.1f} m  <-- uzak".format(stop["id"], name, waypoint["distance"]))
        except Exception as error:  # noqa: BLE001
            print(f"   {stop['id']} hata: {error}")
        time.sleep(0.3)
    print("   yol adlarına göre durak sayısı:", sorted(names.items(), key=lambda kv: -kv[1])[:15])


if __name__ == "__main__":
    main(sys.argv[1])
