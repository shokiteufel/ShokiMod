"""Legt die Daten fuer die Shard-Fusionen ab: einmal die Rezepte, oft die Preise.

Laeuft als GitHub Action, nicht auf dem Spieler-PC - der Bazaar allein sind
dreieinhalb Megabyte, die niemand im Spieltakt holen will.

Herausgeschrieben werden zwei Dateien, und die Trennung ist der ganze Witz:

    shardrecipes.json   alle rund 128.000 Kombinationen, etwa anderthalb Megabyte.
                        Aendert sich nur, wenn Hypixel neue Shards bringt.
    shardprices.json    gut dreihundert Shards mit Preis, Name, Sparte und
                        Stueckzahl - vierzehn Kilobyte, alle zehn Minuten frisch.

Frueher stand hier eine fertig gerechnete Bestenliste mit vierhundert Zeilen. Das
war bequem und falsch: Wer nach einem bestimmten Shard suchte, fand nichts, weil
dessen Fusionen nicht unter den besten vierhundert waren - Abyssal Miner etwa kommt
in 1.373 Kombinationen vor und in keiner einzigen davon. Eine Liste, die nur die
Spitze kennt, kann die Frage "was mache ich mit diesem Shard" nicht beantworten.

Gerechnet wird deshalb in der Mod. Das klingt nach viel und ist es nicht:
Hunderttausend Multiplikationen kosten dort weniger als ein einzelnes Bild. Teuer
war nie das Rechnen, sondern das Holen der Bazaar-Daten - und genau das bleibt hier.

Die Rezepte kommen von SkyShards (https://github.com/Campionnn/SkyShards, MIT).
Wie viele Shards eine Fusion verschlingt, steht nicht im Rezept, sondern beim Shard
selbst: fuse_amount, meist 5, bei manchen 2. Eine Fusion nimmt von jeder Zutat diese
Menge - "Sun Fish + Sun Fish" heisst fuenf plus fuenf, nicht eins plus eins.

    python shardprofit.py [verzeichnis]
"""
from __future__ import annotations

import hashlib
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SKYSHARDS = "https://raw.githubusercontent.com/Campionnn/SkyShards/master/public"
FUSIONEN = SKYSHARDS + "/fusion-data.json"
EIGENSCHAFTEN = SKYSHARDS + "/fusion-properties.json"
BAZAAR = "https://api.hypixel.net/v2/skyblock/bazaar"
KOPF = {"User-Agent": "ShokiMod-shardprofit (+https://github.com/shokiteufel/ShokiMod)"}

# Was seltener als das gehandelt wird, ist keine Gelegenheit, sondern eine Falle:
# Der Preis stammt dann von einem einzelnen Angebot und haelt keiner Menge stand.
# Aussortiert wird nicht mehr hier - die Mod bekommt den Umsatz mitgeliefert und
# entscheidet selbst, damit der Wert auch im Fenster sichtbar gemacht werden kann
MINDESTUMSATZ = 1000


def holen(url: str) -> dict:
    anfrage = urllib.request.Request(url, headers=KOPF)
    with urllib.request.urlopen(anfrage, timeout=120) as antwort:
        return json.load(antwort)


def rezepte_bauen(fusionen: dict, handelbar: set) -> dict:
    """Alle Kombinationen, deren drei beteiligte Shards sich handeln lassen.

    Gruppiert nach Ergebnis und Ausbeute; die Zutaten stehen flach hintereinander,
    immer paarweise. Das spart gegenueber verschachtelten Paaren ein gutes halbes
    Megabyte an Klammern, und die Mod liest es in einem Durchlauf.
    """
    out = {}
    for ergebnis, nach_menge in fusionen["recipes"].items():
        if ergebnis not in handelbar:
            continue
        for menge, paare in nach_menge.items():
            flach = []
            for paar in paare:
                if not isinstance(paar, list) or len(paar) != 2:
                    continue
                a, b = paar
                if a in handelbar and b in handelbar:
                    flach.append(a)
                    flach.append(b)
            if flach:
                out.setdefault(ergebnis, {})[str(menge)] = flach
    return out


def preise_bauen(fusionen: dict, eigenschaften: dict, produkte: dict,
                 handelbar: set) -> dict:
    """Je Shard alles, was die Mod zum Rechnen und Anzeigen braucht.

    Die beiden Bazaar-Zahlen heissen je nach Richtung etwas anderes:

        buyPrice   was ein Stueck kostet, wenn man es sofort nimmt (Instabuy),
                   und zugleich das, was eine Sell Order einbringt
        sellPrice  was ein Stueck bringt, wenn man es sofort abgibt (Instasell),
                   und zugleich das, was eine Buy Order kostet

    Deshalb stehen beide da, statt einer vorgerechneten Spanne. Die Schluessel sind
    einbuchstabig: Bei dreihundert Shards macht das ein Drittel der Dateigroesse aus.
    """
    shards = fusionen["shards"]
    out = {}
    for code in sorted(handelbar):
        s = shards[code]
        q = produkte[s["internal_id"]]["quick_status"]
        eig = eigenschaften.get(code, {})
        out[code] = {
            "n": s.get("name", code),
            # Die Bazaar-Kennung: Mit ihr kann die Mod die Preise auch direkt bei
            # Hypixel holen, statt auf den naechsten Lauf hier zu warten
            "i": s["internal_id"],
            "s": (s.get("type") or "").upper(),
            "r": (s.get("rarity") or "").upper(),
            # Wie viele Stueck eine Fusion von diesem Shard nimmt
            "f": int(s.get("fuse_amount") or 5),
            # sofort kaufen / sofort verkaufen, je Stueck
            "k": round(q.get("buyPrice") or 0),
            "v": round(q.get("sellPrice") or 0),
            # Wochenumsatz, der kleinere der beiden Wege
            "u": int(min(q.get("buyMovingWeek", 0), q.get("sellMovingWeek", 0))),
            "g": eig.get("category") or "",
        }
    return out


def main() -> int:
    ordner = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    ordner.mkdir(parents=True, exist_ok=True)
    try:
        fusionen = holen(FUSIONEN)
        eigenschaften = holen(EIGENSCHAFTEN)
        bazaar = holen(BAZAAR)
    except (urllib.error.URLError, json.JSONDecodeError, OSError) as e:
        print("Daten holen fehlgeschlagen: %s" % e, file=sys.stderr)
        return 1

    produkte = bazaar.get("products") or {}
    if not produkte:
        print("Der Bazaar hat nichts geliefert", file=sys.stderr)
        return 1

    shards = fusionen["shards"]
    handelbar = {c for c, s in shards.items() if s.get("internal_id") in produkte}
    if not handelbar:
        print("Kein einziger Shard im Bazaar gefunden", file=sys.stderr)
        return 1

    rezepte = rezepte_bauen(fusionen, handelbar)
    kombinationen = sum(len(f) // 2 for m in rezepte.values() for f in m.values())
    if kombinationen < 1000:
        print("Nur %d Kombinationen - da stimmt etwas nicht" % kombinationen,
              file=sys.stderr)
        return 1

    # Die Kennung der Rezepte: Aendert sie sich nicht, muss die Mod die anderthalb
    # Megabyte nicht erneut holen. Sie steht in der kleinen Datei, die ohnehin alle
    # zehn Minuten kommt - so kostet die Pruefung nichts Zusaetzliches
    roh = json.dumps(rezepte, separators=(",", ":"), sort_keys=True)
    kennung = hashlib.sha256(roh.encode("utf-8")).hexdigest()[:16]

    (ordner / "shardrecipes.json").write_text(json.dumps({
        "kennung": kennung,
        "kombinationen": kombinationen,
        "quelle": "https://github.com/Campionnn/SkyShards (MIT)",
        "rezepte": rezepte,
    }, separators=(",", ":")), encoding="utf-8")

    (ordner / "shardprices.json").write_text(json.dumps({
        "aktualisiert": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "rezeptKennung": kennung,
        "kombinationen": kombinationen,
        "mindestumsatz": MINDESTUMSATZ,
        "shards": preise_bauen(fusionen, eigenschaften, produkte, handelbar),
    }, separators=(",", ":")), encoding="utf-8")

    gross = (ordner / "shardrecipes.json").stat().st_size
    klein = (ordner / "shardprices.json").stat().st_size
    print("%d Kombinationen, %d Shards - Rezepte %.1f MB (%s), Preise %.0f KB"
          % (kombinationen, len(handelbar), gross / 1e6, kennung, klein / 1e3))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
