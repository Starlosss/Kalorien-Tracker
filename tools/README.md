# Werkzeuge zur Lebensmitteldatenbank

## Was hier liegt

- `off_extract.py` erzeugt die beiden gebuendelten Asset-Dateien der App aus dem
  Open-Food-Facts-Datensatz.
- `dishes.py` haengt gekochte Gerichte aus der USDA-Erhebungsdatenbank an
  `base_foods.json` an. Es laeuft nach `off_extract.py`, nie davor.
- `dishes_mapping.csv` ist die handgepflegte Zuordnung deutscher Gerichtnamen zu
  USDA-Lebensmitteln. Spalten: `deutscher_name,fdc_id,portion_g,fndds_beschreibung`.
  Die vierte Spalte steht nur da, damit sich die Zuordnung ohne Nachschlagen
  pruefen laesst; das Skript liest sie nicht.
- `dishes_block.json` ist die Merkliste von `dishes.py`: die Namen, die es zuletzt
  geschrieben hat. Nur diese Eintraege darf es wieder entfernen. Nicht von Hand
  aendern, das Skript schreibt sie bei jedem Lauf neu.
- `base_foods_manual.json` ist die handgepflegte Liste mit 153 Grundnahrungsmitteln.
  Diese Datei ist die Quelle der Wahrheit fuer diese 153 Eintraege und wird nicht
  vom Skript veraendert. Die Namen darin (zum Beispiel "Reis (gekocht)") werden vom
  Analyse-Katalog der App erwartet und duerfen nicht umbenannt werden.

## Was das Skript erzeugt

- `app/src/main/assets/products.csv`
  Barcode-Tabelle deutscher Handelsprodukte, damit der Scanner ohne Internet
  funktioniert. Ohne Kopfzeile, aufsteigend nach Barcode sortiert.
  Spalten: `barcode,name,brand,kcal,protein,carbs,fat,fiber,sugar,saturated,salt,serving`
  Alle Naehrwerte je 100 g, Punkt als Dezimaltrennzeichen, `serving` in Gramm.
  Ein leeres Feld bedeutet: dieser Wert fehlt in den Daten. Er wird nicht geschaetzt.
  Textfelder stehen nur dann in Anfuehrungszeichen, wenn sie ein Komma enthalten;
  Anfuehrungszeichen selbst werden aus den Texten entfernt.

- `app/src/main/assets/base_foods.json`
  Grundnahrungsmittel im kompakten Format
  `{"n","k","p","c","f","fi","s","sf","sa","sv"}`, je 100 g, `sv` ist die
  Standardportion in Gramm. Die 153 handgepflegten Eintraege stehen unveraendert
  am Anfang, danach folgen die aus Kategorien erzeugten Eintraege und zuletzt die
  gekochten Gerichte aus `dishes.py`.

## Auswahlregeln

Fuer `products.csv`:
- `countries_tags` enthaelt `en:germany`, Produkt nicht als veraltet markiert
- Barcode besteht aus 8 bis 14 Ziffern
- ein Produktname ist vorhanden (bevorzugt deutsch, sonst Hauptsprache, sonst englisch)
- Kalorien zwischen 1 und 900 je 100 g
- Eiweiss, Kohlenhydrate und Fett jeweils zwischen 0 und 100
- die Makronaehrstoffe passen zu den Kalorien:
  `abs(kcal - (4*Eiweiss + 4*Kohlenhydrate + 9*Fett)) <= max(50, 0.3*kcal)`
- Rangfolge nach `unique_scans_n` absteigend, die besten 40000 Produkte
- die Datei darf 6 MB nicht ueberschreiten; wuerde sie es, fallen die am
  seltensten gescannten Zeilen weg

Fuer die erzeugten Eintraege in `base_foods.json`:
- dieselbe Produktmenge, aber nur Produkte mit allen acht Naehrwerten
- Gruppierung nach `categories_tags`, mindestens 30 Produkte je Kategorie
- je Naehrwert der Median, Standardportion der Median von `serving_quantity`,
  sonst 100 g
- der Kategoriename kommt aus der deutschen Bezeichnung der
  Open-Food-Facts-Kategorie-Taxonomie; Kategorien ohne deutschen Namen oder mit
  einem Namen unter drei Zeichen fallen weg
- hoechstens 1200 erzeugte Eintraege, Dubletten gegen die handgepflegten Namen
  werden entfernt (Vergleich in Kleinschreibung)

Fuer die Gerichte aus `dishes.py`:
- jedes Gericht steht mit seiner `fdc_id` in `dishes_mapping.csv`
- ein Gericht faellt weg, wenn die `fdc_id` in den USDA-Daten fehlt, kein
  Energiewert vorhanden ist oder einer der acht Naehrwerte fehlt
- fremde Namen sind tabu: steht ein Name aus `dishes_mapping.csv` schon in der
  handgepflegten Liste oder im aus Open Food Facts erzeugten Block, bricht der
  Lauf ab, nennt die Zeile und schreibt nichts. Solche Zeilen muessen umbenannt
  oder entfernt werden.
- die handgepflegten Namen haben also Vorrang: `Currywurst`, `Lasagne`, `Falafel`
  und andere bleiben so, wie sie in `base_foods_manual.json` stehen
- der deutsche Name sagt, was in den Daten steht. Wo die USDA-Beschreibung eine
  andere Sache meint als das gesuchte Gericht, heisst der Eintrag nach den Daten
  (`Wiener Wuerstchen mit Sauerkraut`, `Burger-Patty`, `Rindfleischsuppe`,
  `Bulgursalat`) oder faellt weg
- Salz wird aus Natrium gerechnet: `Salz = Natrium in mg * 2,5 / 1000`
- Kalorien ganzzahlig, Salz auf zwei Stellen, alles andere auf eine Stelle
  gerundet, wie im Rest der Datei

Es wird kein Naehrwert geschaetzt, hochgerechnet oder erfunden. Jeder Wert stammt
aus den Daten oder aus der handgepflegten Liste.

## Ausfuehren

Das Skript braucht zwei Dateien im Cache-Verzeichnis `~/.cache/off`:

```bash
mkdir -p ~/.cache/off
curl -L -o ~/.cache/off/food.parquet \
  https://huggingface.co/datasets/openfoodfacts/product-database/resolve/main/food.parquet
curl -L -o ~/.cache/off/categories.json \
  https://static.openfoodfacts.org/data/taxonomies/categories.json
```

Der Parquet-Dump ist rund 7,8 GB gross, die Taxonomie rund 4,7 MB. Danach laeuft
alles ohne Netz:

```bash
pip install --user duckdb
cd ~/Kalorien-Tracker
python3 tools/off_extract.py
```

Die Pfade lassen sich ueber die Umgebungsvariablen `OFF_CACHE`, `OFF_PARQUET` und
`OFF_TAXONOMY` umstellen.

Danach die Gerichte anhaengen. Dafuer liegt der USDA-Datensatz entpackt in
`~/.cache/fndds`:

```bash
mkdir -p ~/.cache/fndds
curl -L -o /tmp/fndds.zip \
  https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_csv_2024-10-31.zip
unzip -o /tmp/fndds.zip -d ~/.cache/fndds
cd ~/Kalorien-Tracker
python3 tools/dishes.py
```

Das Verzeichnis laesst sich ueber `FNDDS_DIR` umstellen. `dishes.py` entfernt vor
dem Anhaengen genau die Eintraege, die in `dishes_block.json` stehen, also den
eigenen Block aus dem letzten Lauf, und baut ihn danach neu auf. Zweimaliges
Laufen aendert nichts, und eine umbenannte oder geloeschte Zeile der
Zuordnungstabelle laesst keine Waise zurueck.

Wichtig ist die Reihenfolge: `off_extract.py` schreibt `base_foods.json` komplett
neu. Wer es nach `dishes.py` laufen laesst, verliert die Gerichte.

Das Skript ist deterministisch: gleiche Eingabedateien ergeben Byte-gleiche
Ausgabedateien. Jede Ausgabe ist sortiert, nichts haengt von der Reihenfolge im
Speicher ab.

## Wann neu erzeugen

- wenn die Barcode-Suche zu oft nichts findet, also etwa einmal pro Jahr
- wenn die handgepflegte Liste `base_foods_manual.json` geaendert wurde
- vor einem groesseren Release, damit die Naehrwerte nicht veralten

Dafuer beide Dateien im Cache neu herunterladen und beide Skripte erneut laufen
lassen, erst `off_extract.py`, dann `dishes.py`. Danach pruefen, dass die ersten
153 Eintraege in `base_foods.json` unveraendert sind und `products.csv` unter
6 MB bleibt.

## Datenquelle und Lizenz

Die Daten stammen von Open Food Facts (https://world.openfoodfacts.org) und stehen
unter der Open Database License (ODbL 1.0,
https://opendatacommons.org/licenses/odbl/1-0/).

Daraus folgt eine Pflicht fuer die App: sie muss Open Food Facts als Quelle nennen
und die ODbL nennen, sichtbar fuer die Nutzer, zum Beispiel im Bildschirm "Ueber
die App" oder in den Einstellungen. Ein Text dafuer:

> Lebensmitteldaten von Open Food Facts, verfuegbar unter der Open Database
> License (ODbL).

Werden die abgeleiteten Daten weitergegeben, muessen sie ebenfalls unter der ODbL
stehen.

Die gekochten Gerichte stammen aus der Survey-Datenbank (FNDDS) von USDA
FoodData Central (https://fdc.nal.usda.gov). Verwendet wurde der CSV-Export
`FoodData_Central_survey_food_csv_2024-10-31.zip`, Stand 31. Oktober 2024,
heruntergeladen am 26. September 2026. Diese Daten sind gemeinfrei (Public
Domain), eine Lizenzpflicht besteht nicht. Genannt wird die Quelle trotzdem,
damit nachvollziehbar bleibt, woher die Zahlen kommen. Uebersetzt wurden nur die
Namen, kein Naehrwert wurde angepasst.

Ein Text fuer die App:

> Gekochte Gerichte aus der Survey-Datenbank (FNDDS) von USDA FoodData Central,
> gemeinfrei.
