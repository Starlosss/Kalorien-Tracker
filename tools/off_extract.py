#!/usr/bin/env python3
"""Erzeugt die gebuendelten Lebensmittel-Dateien der App aus den Open-Food-Facts-Daten.

Ausgabe:
  app/src/main/assets/products.csv     Barcode-Tabelle deutscher Handelsprodukte
  app/src/main/assets/base_foods.json  Grundnahrungsmittel (153 handgepflegte + generierte)

Datenquelle: Open Food Facts, Parquet-Dump "food.parquet" (ODbL).
Das Skript rechnet nichts hoch und erfindet keine Naehrwerte: jeder Wert stammt
entweder aus dem Dump oder aus der handgepflegten Liste.

Das Skript ist deterministisch: gleiche Eingabedateien ergeben Byte-gleiche Ausgabe.
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

import duckdb

# ---------------------------------------------------------------- Pfade

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
MANUAL_JSON = REPO / "tools" / "base_foods_manual.json"

CACHE = Path(os.environ.get("OFF_CACHE", Path.home() / ".cache" / "off"))
PARQUET = Path(os.environ.get("OFF_PARQUET", CACHE / "food.parquet"))
TAXONOMY = Path(os.environ.get("OFF_TAXONOMY", CACHE / "categories.json"))

OUT_CSV = ASSETS / "products.csv"
OUT_JSON = ASSETS / "base_foods.json"

PARQUET_URL = (
    "https://huggingface.co/datasets/openfoodfacts/product-database/"
    "resolve/main/food.parquet"
)
TAXONOMY_URL = "https://static.openfoodfacts.org/data/taxonomies/categories.json"

# ---------------------------------------------------------------- Schwellen

MAX_PRODUCTS = 40000          # Obergrenze der Barcode-Tabelle
MAX_CSV_BYTES = 6 * 1024 * 1024   # products.csv darf 6 MB nicht ueberschreiten
MIN_CATEGORY_PRODUCTS = 30    # Mindestzahl Produkte je generierter Kategorie
MAX_CATEGORIES = 1200         # Obergrenze generierter Grundnahrungsmittel
MIN_CATEGORY_DEPTH = 3        # Abstand zur Wurzel der Taxonomie, darunter sind es Sammeltoepfe
MAX_CATEGORY_SHARE = 0.05     # Kategorien ueber 5 % aller Produkte sind Sammeltoepfe
MAX_NAME_LEN = 70             # Namenslaenge in products.csv
MIN_CATEGORY_NAME_LEN = 3     # kuerzere Kategorienamen werden verworfen
SERVING_MIN, SERVING_MAX = 1.0, 2000.0   # plausible Portionsgroesse in Gramm
UNREACHABLE = 10 ** 6         # Ersatzabstand bei einem Zyklus in der Taxonomie

# ---------------------------------------------------------------- SQL

# Die Naehrwerte liegen als Liste von Structs vor; der Wert je 100 g steht im
# Feld mit dem Namen "100g".
def _nut(name: str) -> str:
    return (
        'list_extract(list_filter(nutriments, x -> x.name = '
        "'" + name + "'"
        '), 1)."100g"'
    )


BASE_SQL = """
WITH raw AS (
  SELECT
    code,
    coalesce(
      list_extract(list_filter(product_name, x -> x.lang = 'de'),   1)."text",
      list_extract(list_filter(product_name, x -> x.lang = 'main'), 1)."text",
      list_extract(list_filter(product_name, x -> x.lang = 'en'),   1)."text"
    )                                              AS name_raw,
    trim(split_part(coalesce(brands, ''), ',', 1)) AS brand_raw,
    coalesce(unique_scans_n, 0)                    AS scans,
    TRY_CAST(serving_quantity AS DOUBLE)           AS serving_raw,
    categories_tags,
    __KCAL__      AS kcal,
    __PROTEIN__   AS protein,
    __CARBS__     AS carbs,
    __FAT__       AS fat,
    __FIBER__     AS fiber,
    __SUGAR__     AS sugar,
    __SATURATED__ AS saturated,
    __SALT__      AS salt
  FROM read_parquet('__PARQUET__')
  WHERE list_contains(countries_tags, 'en:germany')
    AND coalesce(obsolete, FALSE) = FALSE
),
clean AS (
  SELECT
    code,
    regexp_replace(trim(name_raw),  '\\s+', ' ', 'g') AS name,
    regexp_replace(trim(brand_raw), '\\s+', ' ', 'g') AS brand,
    scans,
    CASE WHEN serving_raw BETWEEN __SERVING_MIN__ AND __SERVING_MAX__
         THEN serving_raw END AS serving,
    categories_tags,
    kcal, protein, carbs, fat, fiber, sugar, saturated, salt
  FROM raw
  WHERE regexp_matches(code, '^[0-9]{8,14}$')
    AND name_raw IS NOT NULL
    AND length(trim(name_raw)) > 0
    AND kcal    IS NOT NULL AND kcal    BETWEEN 1 AND 900
    AND protein IS NOT NULL AND protein BETWEEN 0 AND 100
    AND carbs   IS NOT NULL AND carbs   BETWEEN 0 AND 100
    AND fat     IS NOT NULL AND fat     BETWEEN 0 AND 100
    AND abs(kcal - (4 * protein + 4 * carbs + 9 * fat))
        <= greatest(50, 0.3 * kcal)
),
ranked AS (
  SELECT *, row_number() OVER (PARTITION BY code ORDER BY scans DESC, name ASC) AS rn
  FROM clean
),
products AS (
  SELECT * EXCLUDE (rn) FROM ranked WHERE rn = 1
)
"""


def base_sql() -> str:
    sql = BASE_SQL
    for token, name in (
        ("__KCAL__", "energy-kcal"),
        ("__PROTEIN__", "proteins"),
        ("__CARBS__", "carbohydrates"),
        ("__FAT__", "fat"),
        ("__FIBER__", "fiber"),
        ("__SUGAR__", "sugars"),
        ("__SATURATED__", "saturated-fat"),
        ("__SALT__", "salt"),
    ):
        sql = sql.replace(token, _nut(name))
    sql = sql.replace("__PARQUET__", str(PARQUET))
    sql = sql.replace("__SERVING_MIN__", repr(SERVING_MIN))
    sql = sql.replace("__SERVING_MAX__", repr(SERVING_MAX))
    return sql


PRODUCTS_SQL = """
SELECT code, name, brand, kcal, protein, carbs, fat,
       fiber, sugar, saturated, salt, serving, scans
FROM products
ORDER BY scans DESC, code ASC
LIMIT __LIMIT__
"""

COMPLETE_COUNT_SQL = """
, complete AS (
  SELECT * FROM products
  WHERE fiber IS NOT NULL AND sugar IS NOT NULL
    AND saturated IS NOT NULL AND salt IS NOT NULL
)
SELECT count(*) FROM complete
"""

CATEGORIES_SQL = """
, complete AS (
  SELECT * FROM products
  WHERE fiber IS NOT NULL AND sugar IS NOT NULL
    AND saturated IS NOT NULL AND salt IS NOT NULL
),
exploded AS (
  SELECT unnest(categories_tags) AS tag, *
  FROM complete
)
SELECT
  tag,
  count(*)                                AS n,
  quantile_cont(kcal,      0.5)           AS kcal,
  quantile_cont(protein,   0.5)           AS protein,
  quantile_cont(carbs,     0.5)           AS carbs,
  quantile_cont(fat,       0.5)           AS fat,
  quantile_cont(fiber,     0.5)           AS fiber,
  quantile_cont(sugar,     0.5)           AS sugar,
  quantile_cont(saturated, 0.5)           AS saturated,
  quantile_cont(salt,      0.5)           AS salt,
  quantile_cont(serving,   0.5)           AS serving
FROM exploded
GROUP BY tag
HAVING count(*) >= __MIN_N__
ORDER BY n DESC, tag ASC
"""

# ---------------------------------------------------------------- Helfer


def fail(message: str) -> "None":
    print("FEHLER: " + message, file=sys.stderr)
    sys.exit(1)


def num(value, digits: int = 2) -> str:
    """Zahl mit Punkt als Dezimaltrennzeichen, ohne ueberfluessige Nullen."""
    if value is None:
        return ""
    rounded = round(float(value), digits)
    if rounded == int(rounded):
        return str(int(rounded))
    text = ("%." + str(digits) + "f") % rounded
    return text.rstrip("0").rstrip(".")


def jnum(value, digits: int = 1):
    """Zahl fuer base_foods.json: int wenn ganzzahlig, sonst gerundeter float."""
    if value is None:
        return None
    rounded = round(float(value), digits)
    return int(rounded) if rounded == int(rounded) else rounded


# Emoji und Piktogramme aus Produktnamen entfernen: die App zeigt keine Emoji.
EMOJI = re.compile(
    "["
    "\U0001F000-\U0001FAFF"   # Emoji, Piktogramme, Flaggen
    "\U00002600-\U000027BF"   # Symbole und Dingbats
    "\U00002B00-\U00002BFF"   # Pfeile und geometrische Formen
    "\U0000FE00-\U0000FE0F"   # Variantenselektoren
    "\U0000200D"               # Zero Width Joiner
    "]"
)


def clean_text(value: str, limit: int) -> str:
    """Whitespace normalisieren, Anfuehrungszeichen und Emoji entfernen, kuerzen."""
    text = EMOJI.sub(" ", value or "")
    text = text.replace('"', "").replace("\r", " ").replace("\n", " ")
    text = " ".join(text.split())
    if len(text) > limit:
        text = text[:limit].rstrip()
    return text


def csv_field(text: str) -> str:
    """Textfeld: nur bei Komma in Anfuehrungszeichen setzen."""
    return '"' + text + '"' if "," in text else text


def load_taxonomy(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def german_category_names(taxonomy: dict) -> dict:
    """Tag -> deutscher Kategoriename aus der Open-Food-Facts-Taxonomie.

    Uebersprungen werden Kategorien ohne deutschen Namen, mit einem Namen unter
    drei Zeichen und solche, deren deutscher Name gleich dem lateinischen Namen
    ist. Letzteres sind unuebersetzte botanische Gattungsnamen wie "Triticum";
    danach sucht niemand in einer Kalorien-App.
    """
    names = {}
    for tag, entry in taxonomy.items():
        if not isinstance(entry, dict):
            continue
        name = entry.get("name")
        if not isinstance(name, dict):
            continue
        german = name.get("de")
        if not isinstance(german, str):
            continue
        german = " ".join(german.split())
        if len(german) < MIN_CATEGORY_NAME_LEN:
            continue
        latin = name.get("la")
        if isinstance(latin, str) and " ".join(latin.split()).lower() == german.lower():
            continue
        names[tag] = german
    return names


def category_depths(taxonomy: dict) -> dict:
    """Tag -> kuerzester Abstand zu einer Wurzel der Taxonomie.

    Wurzeln haben den Abstand 0. Kleine Abstaende sind Sammeltoepfe wie
    "Pflanzliche Lebensmittel", die in einer Lebensmittelsuche nur stoeren.
    """
    parents = {
        tag: [p for p in (entry.get("parents") or []) if isinstance(p, str)]
        for tag, entry in taxonomy.items()
        if isinstance(entry, dict)
    }
    depths = {}

    def depth_of(tag: str, seen: frozenset) -> int:
        if tag in depths:
            return depths[tag]
        if tag in seen:
            return UNREACHABLE          # Zyklus in der Taxonomie
        known = [p for p in parents.get(tag, []) if p in parents]
        if not known:
            depths[tag] = 0
            return 0
        value = 1 + min(depth_of(p, seen | {tag}) for p in known)
        depths[tag] = value
        return value

    limit = sys.getrecursionlimit()
    sys.setrecursionlimit(max(limit, 20000))
    try:
        for tag in parents:
            depth_of(tag, frozenset())
    finally:
        sys.setrecursionlimit(limit)
    return depths


# ---------------------------------------------------------------- Ablauf


def main() -> None:
    if not PARQUET.exists():
        fail(
            "Parquet-Dump fehlt: " + str(PARQUET) + "\n"
            "Einmalig herunterladen (rund 7,8 GB):\n"
            "  mkdir -p " + str(CACHE) + "\n"
            "  curl -L -o " + str(PARQUET) + " " + PARQUET_URL
        )
    if not MANUAL_JSON.exists():
        fail("Handgepflegte Liste fehlt: " + str(MANUAL_JSON))
    if not TAXONOMY.exists():
        fail(
            "Kategorie-Taxonomie fehlt: " + str(TAXONOMY) + "\n"
            "Einmalig herunterladen (rund 4,7 MB):\n"
            "  curl -L -o " + str(TAXONOMY) + " " + TAXONOMY_URL
        )

    ASSETS.mkdir(parents=True, exist_ok=True)
    con = duckdb.connect()
    sql = base_sql()

    # ---------------- products.csv
    print("Lese Produkte aus " + str(PARQUET) + " ...")
    rows = con.sql(sql + PRODUCTS_SQL.replace("__LIMIT__", str(MAX_PRODUCTS))).fetchall()
    print("  " + str(len(rows)) + " Produkte nach Filterung und Rangfolge")

    top_ten = [
        (r[0], r[1], r[2], int(r[12]), num(r[3]))
        for r in rows[:10]
    ]

    # Zeilen in Scan-Reihenfolge bauen und das 6-MB-Budget einhalten.
    budget_used = 0
    kept = []
    dropped_for_size = 0
    dropped_empty_name = 0
    for r in rows:
        (code, name, brand, kcal, protein, carbs, fat,
         fiber, sugar, saturated, salt, serving, _scans) = r
        clean_name = clean_text(name, MAX_NAME_LEN)
        if not clean_name:
            dropped_empty_name += 1
            continue
        line = ",".join([
            code,
            csv_field(clean_name),
            csv_field(clean_text(brand, MAX_NAME_LEN)),
            num(kcal, 1), num(protein), num(carbs), num(fat),
            num(fiber), num(sugar), num(saturated), num(salt),
            num(serving, 1),
        ])
        size = len(line.encode("utf-8")) + 1
        if budget_used + size > MAX_CSV_BYTES:
            dropped_for_size += 1
            continue
        budget_used += size
        kept.append((code, line))

    kept.sort(key=lambda item: item[0])
    OUT_CSV.write_text("\n".join(line for _code, line in kept) + "\n", encoding="utf-8")

    # ---------------- base_foods.json
    manual = json.loads(MANUAL_JSON.read_text(encoding="utf-8"))
    foods = list(manual)
    seen = {str(entry["n"]).strip().lower() for entry in manual}

    taxonomy = load_taxonomy(TAXONOMY)
    german = german_category_names(taxonomy)
    depths = category_depths(taxonomy)
    print("  " + str(len(german)) + " Kategorien mit brauchbarem deutschem Namen")

    complete_total = con.sql(sql + COMPLETE_COUNT_SQL).fetchone()[0]
    share_limit = complete_total * MAX_CATEGORY_SHARE
    print("  " + str(complete_total) + " Produkte mit allen acht Naehrwerten")

    cat_rows = con.sql(
        sql + CATEGORIES_SQL.replace("__MIN_N__", str(MIN_CATEGORY_PRODUCTS))
    ).fetchall()
    print("  " + str(len(cat_rows)) + " Kategorien mit mindestens "
          + str(MIN_CATEGORY_PRODUCTS) + " vollstaendigen Produkten")

    generated = 0
    skipped_shallow = 0
    skipped_broad = 0
    for row in cat_rows:
        if generated >= MAX_CATEGORIES:
            break
        (tag, count_n, kcal, protein, carbs, fat,
         fiber, sugar, saturated, salt, serving) = row
        name = german.get(tag)
        if not name:
            continue
        if depths.get(tag, 0) < MIN_CATEGORY_DEPTH:
            skipped_shallow += 1
            continue
        if count_n > share_limit:
            skipped_broad += 1
            continue
        key = name.strip().lower()
        if key in seen:
            continue
        seen.add(key)
        foods.append({
            "n": name,
            "k": jnum(kcal, 0),
            "p": jnum(protein),
            "c": jnum(carbs),
            "f": jnum(fat),
            "fi": jnum(fiber),
            "s": jnum(sugar),
            "sf": jnum(saturated),
            "sa": jnum(salt, 2),
            "sv": jnum(serving, 0) if serving is not None else 100,
        })
        generated += 1

    lines = [json.dumps(entry, ensure_ascii=False, separators=(",", ":")) for entry in foods]
    OUT_JSON.write_text("[\n" + ",\n".join(lines) + "\n]\n", encoding="utf-8")

    # ---------------- Zusammenfassung
    print("")
    print("Zusammenfassung")
    print("  products.csv:    " + str(len(kept)) + " Zeilen, "
          + str(OUT_CSV.stat().st_size) + " Bytes")
    if dropped_for_size:
        print("  wegen 6-MB-Grenze verworfen: " + str(dropped_for_size) + " Zeilen")
    if dropped_empty_name:
        print("  wegen leerem Namen verworfen: " + str(dropped_empty_name) + " Zeilen")
    print("  uebersprungene Sammeltoepfe: " + str(skipped_shallow)
          + " zu weit oben in der Taxonomie, " + str(skipped_broad)
          + " ueber " + str(int(MAX_CATEGORY_SHARE * 100)) + " Prozent aller Produkte")
    print("  base_foods.json: " + str(len(foods)) + " Eintraege ("
          + str(len(manual)) + " handgepflegt + " + str(generated) + " generiert), "
          + str(OUT_JSON.stat().st_size) + " Bytes")
    print("")
    print("Die zehn meistgescannten Produkte")
    for i, (code, name, brand, scans, kcal) in enumerate(top_ten, start=1):
        print("  " + str(i).rjust(2) + ". " + code + "  " + str(scans).rjust(7)
              + " Scans  " + kcal.rjust(5) + " kcal  "
              + clean_text(name, MAX_NAME_LEN)
              + (" [" + clean_text(brand, 40) + "]" if brand else ""))


if __name__ == "__main__":
    main()
