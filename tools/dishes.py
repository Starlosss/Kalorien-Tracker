#!/usr/bin/env python3
"""Gekochte Gerichte aus der USDA-Erhebungsdatenbank an base_foods.json anhaengen.

Open Food Facts kennt nur verpackte Produkte. Gulasch, Auflauf, Eintopf oder
Hackbraten stehen dort nicht. Diese Gerichte kommen aus der USDA FoodData
Central Survey-Datenbank (FNDDS). Sie ist gemeinfrei und enthaelt zusammen-
gesetzte, gekochte Gerichte mit vollstaendigen Naehrwerten je 100 g.

Uebersetzt werden nur die Namen. Kein Naehrwert wird geschaetzt oder angepasst.

Das Skript ist deterministisch und mehrfach ausfuehrbar. In `dishes_block.json`
merkt es sich, welche Namen es zuletzt geschrieben hat. Beim naechsten Lauf
entfernt es genau diese Eintraege und baut den Gerichte-Block neu auf. Deshalb
bleibt keine Waise stehen, wenn eine Zeile der Zuordnungstabelle umbenannt oder
geloescht wird, und zweimaliges Laufen erzeugt keine Dubletten.

Fremde Eintraege fasst das Skript nie an. Steht ein Name aus der Zuordnungs-
tabelle schon in der handgepflegten Liste oder im aus Open Food Facts erzeugten
Block, bricht der Lauf mit einer Fehlermeldung ab und nennt die Zeile. Er
ueberschreibt sie nicht.

Reihenfolge: erst off_extract.py laufen lassen, dann dieses Skript. off_extract.py
schreibt base_foods.json komplett neu und wuerde die Gerichte sonst entfernen.
"""

import csv
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAPPING = Path(__file__).resolve().parent / "dishes_mapping.csv"
MANUAL = Path(__file__).resolve().parent / "base_foods_manual.json"
# Merkliste: welche Namen dieses Skript zuletzt geschrieben hat. Nur diese
# Eintraege darf es wieder entfernen.
STATE = Path(__file__).resolve().parent / "dishes_block.json"
OUT_JSON = ROOT / "app/src/main/assets/base_foods.json"

# Heruntergeladen von
# https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_csv_2024-10-31.zip
DEFAULT_FNDDS = Path.home() / ".cache/fndds/FoodData_Central_survey_food_csv_2024-10-31"
FNDDS = Path(os.environ.get("FNDDS_DIR", DEFAULT_FNDDS))

# In food_nutrient.csv steht in der Spalte nutrient_id die alte USDA-Nummer,
# nicht die ID aus nutrient.csv. Deshalb wird nach diesen Nummern gesucht.
NUTRIENT_NUMBERS = {
    "208": "kcal",      # Energy, KCAL
    "203": "protein",   # Protein, g
    "205": "carbs",     # Carbohydrate, by difference, g
    "204": "fat",       # Total lipid (fat), g
    "291": "fiber",     # Fiber, total dietary, g
    "269": "sugar",     # Total Sugars, g
    "606": "saturated", # Fatty acids, total saturated, g
    "307": "sodium",    # Sodium, mg
}


def fail(message):
    print("Fehler: " + message, file=sys.stderr)
    raise SystemExit(1)


def jnum(value, digits=1):
    """Auf `digits` Stellen runden und ganze Zahlen ohne Nachkomma schreiben."""
    if value is None:
        return None
    rounded = round(float(value), digits)
    if rounded == int(rounded):
        return int(rounded)
    return rounded


def read_fndds():
    """fdc_id -> (Beschreibung, Naehrwerte je 100 g)."""
    food_csv = FNDDS / "food.csv"
    nutrient_csv = FNDDS / "food_nutrient.csv"
    for path in (food_csv, nutrient_csv):
        if not path.is_file():
            fail("Datei fehlt: " + str(path)
                 + "\nFNDDS-Verzeichnis ueber FNDDS_DIR setzen, siehe tools/README.md")

    descriptions = {}
    with food_csv.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            descriptions[row["fdc_id"]] = row["description"]

    nutrients = {}
    with nutrient_csv.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            key = NUTRIENT_NUMBERS.get(row["nutrient_id"])
            if key is None:
                continue
            amount = row["amount"].strip()
            if not amount:
                continue
            nutrients.setdefault(row["fdc_id"], {})[key] = float(amount)

    return descriptions, nutrients


def read_mapping():
    if not MAPPING.is_file():
        fail("Zuordnungstabelle fehlt: " + str(MAPPING))
    with MAPPING.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        fail("Zuordnungstabelle ist leer")
    return rows


def read_own_block():
    """Namen, die dieses Skript beim letzten Lauf geschrieben hat."""
    if not STATE.is_file():
        return []
    data = json.loads(STATE.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        fail("Merkliste ist keine Liste: " + str(STATE))
    return [str(name) for name in data]


def read_manual_names():
    """Namen der handgepflegten Liste. Diese Eintraege sind unantastbar."""
    if not MANUAL.is_file():
        fail("Datei fehlt: " + str(MANUAL))
    manual = json.loads(MANUAL.read_text(encoding="utf-8"))
    return {entry["n"].strip().lower() for entry in manual}


def main():
    if not OUT_JSON.is_file():
        fail("Datei fehlt: " + str(OUT_JSON))

    descriptions, nutrients = read_fndds()
    rows = read_mapping()
    print("FNDDS: " + str(len(descriptions)) + " Lebensmittel aus " + str(FNDDS.name))
    print("Zuordnung: " + str(len(rows)) + " Gerichte")

    foods = json.loads(OUT_JSON.read_text(encoding="utf-8"))
    own = {name.strip().lower() for name in read_own_block()}

    # Fremd ist alles, was nicht aus dem letzten Lauf dieses Skripts stammt.
    # Die handgepflegte Liste zaehlt immer dazu, auch wenn die Merkliste luegt.
    foreign = ({entry["n"].strip().lower() for entry in foods} - own) | read_manual_names()

    # Erst pruefen, dann entfernen. Ein Name, der schon jemand anderem gehoert,
    # bricht den Lauf ab. Sonst wuerde das Skript stillschweigend einen
    # handgepflegten oder aus Open Food Facts erzeugten Eintrag loeschen.
    clashes = [row["deutscher_name"] for row in rows
               if row["deutscher_name"].strip().lower() in foreign]
    if clashes:
        for name in clashes:
            print("  belegt: " + name, file=sys.stderr)
        fail(str(len(clashes)) + " Name(n) aus dishes_mapping.csv stehen schon in "
             + "base_foods.json und gehoeren nicht diesem Skript.\n"
             + "Diese Zeilen umbenennen oder aus der Zuordnungstabelle entfernen. "
             + "Es wurde nichts geschrieben.")

    # Nur den eigenen Block entfernen. Das raeumt auch Eintraege weg, deren Zeile
    # in der Zuordnungstabelle inzwischen umbenannt oder geloescht wurde.
    kept = [entry for entry in foods if entry["n"].strip().lower() not in own]
    removed = len(foods) - len(kept)
    if removed:
        print("  vorhandenen Gerichte-Block entfernt: " + str(removed) + " Eintraege")

    dishes = []
    skipped = []
    seen = set()

    for row in rows:
        name = row["deutscher_name"].strip()
        fdc_id = row["fdc_id"].strip()
        key = name.lower()

        if key in seen:
            skipped.append((name, "in der Zuordnungstabelle doppelt"))
            continue
        if fdc_id not in descriptions:
            skipped.append((name, "fdc_id " + fdc_id + " steht nicht in den FNDDS-Daten"))
            continue

        values = nutrients.get(fdc_id, {})
        if not values.get("kcal"):
            skipped.append((name, "fdc_id " + fdc_id + " hat keinen Energiewert"))
            continue

        missing = [k for k in ("protein", "carbs", "fat", "fiber", "sugar",
                               "saturated", "sodium") if k not in values]
        if missing:
            skipped.append((name, "fehlende Naehrwerte: " + ", ".join(missing)))
            continue

        try:
            serving = int(float(row["portion_g"]))
        except (TypeError, ValueError):
            skipped.append((name, "portion_g ist keine Zahl"))
            continue

        seen.add(key)
        dishes.append({
            "n": name,
            # kcal ganzzahlig und Salz auf zwei Stellen, wie im Rest der Datei.
            "k": jnum(values["kcal"], 0),
            "p": jnum(values["protein"]),
            "c": jnum(values["carbs"]),
            "f": jnum(values["fat"]),
            "fi": jnum(values["fiber"]),
            "s": jnum(values["sugar"]),
            "sf": jnum(values["saturated"]),
            # Natrium in mg zu Salz in g: Salz = Natrium * 2,5 / 1000
            "sa": jnum(values["sodium"] * 2.5 / 1000, 2),
            "sv": serving,
        })

    result = kept + dishes
    lines = [json.dumps(entry, ensure_ascii=False, separators=(",", ":")) for entry in result]
    OUT_JSON.write_text("[\n" + ",\n".join(lines) + "\n]\n", encoding="utf-8")

    # Merkliste fuer den naechsten Lauf. Sortiert, damit sie sich nur aendert,
    # wenn sich die Gerichte aendern.
    STATE.write_text(
        json.dumps(sorted(entry["n"] for entry in dishes), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print("")
    print("Zusammenfassung")
    print("  uebernommen: " + str(len(dishes)) + " Gerichte")
    print("  uebersprungen: " + str(len(skipped)))
    for name, reason in skipped:
        print("    " + name + ": " + reason)
    print("  base_foods.json: " + str(len(result)) + " Eintraege, "
          + str(OUT_JSON.stat().st_size) + " Bytes")

    outside = [d for d in dishes if d["k"] < 80 or d["k"] > 350]
    if outside:
        print("")
        print("Ausserhalb von 80 bis 350 kcal je 100 g, bitte pruefen:")
        for d in sorted(outside, key=lambda e: e["k"]):
            print("  " + str(d["k"]).rjust(4) + " kcal  " + d["n"])


if __name__ == "__main__":
    main()
