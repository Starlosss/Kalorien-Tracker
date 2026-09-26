# Kalorien-Tracker: Vervollständigung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app complete for daily private use: a food database large enough that real meals resolve, German barcodes that work offline, unknown foods that learn themselves in, a calculator checked against other calculators, photo recognition measured against ground truth, and copy and UI that do not read as machine-written.

**Architecture:** Two new bundled data assets generated from the Open Food Facts database by a Python script under `tools/`. The product table is imported into Room once in the background, so barcode scans hit the local database first and work offline. The analyzer's unknown ingredients are persisted on meal save. Recognition quality is measured by a JVM harness that runs the real production prompt and parser against Nutrition5k dishes, which carry ground-truth grams and calories.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, OkHttp, LiteRT-LM (Gemma 4 E2B), Python 3 with DuckDB for the data pipeline, Nutrition5k as the benchmark dataset.

**Spec:** This plan is the spec; it is written from the user's request of 2026-09-26 and from `/mnt/hdd/Starlos/Projekte/Kalorien-Tracker.md`.

## Global Constraints

- German UI text only. No emojis anywhere, in the app or in reports.
- No em dash or en dash used as a dramatic pause in UI copy. Dashes stay only in ranges ("10–30 Sekunden"), date ranges, and as the "–" placeholder for a missing value.
- Nutrition numbers are never invented. Every value in a bundled asset comes from the Open Food Facts data, and the script that produced it is committed.
- Open Food Facts data is ODbL. The app must name the source and the licence where the data is visible to the user.
- The app stays offline-first: no network call is required for any core flow. Online lookup remains behind the existing privacy toggle.
- Release APK must stay under 60 MB. The bundled product asset must not exceed 6 MB compressed in the APK.
- Branch `claude/hopeful-pascal-hy7fby`. Commit per task, push after each reviewed task. Never force-push.
- Commit trailer: `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Every task ends with `./gradlew testDebugUnitTest` green before the commit.
- Build commands run through the DNS workaround: `~/.local/bin/netfix ./gradlew …` with `JAVA_HOME=~/.local/opt/jdk-21.0.12.1+1` and `ANDROID_HOME=~/Android/Sdk`.

## Review Focus

- **A barcode that exists in the bundled table but with broken nutriments** (kcal 0 or absurd): the app must not log a 0 kcal product silently. Covered in Task 1 (filter) and Task 2 (import guard).
- **A barcode scanned as UPC-A (12 digits) or EAN-8 (8 digits)**: German retail uses both alongside EAN-13; lookup must find the product anyway. Covered in Task 3.
- **The product import running while the user scans**: the first scan may come before the import finished. The lookup must fall back to online instead of reporting "not found". Covered in Task 2.
- **An analyzer ingredient whose name already exists in the database with different values**: auto-adding must not create duplicates that then win the search. Covered in Task 4.
- **A dish photo where the model returns grams far above anything on a plate** (a 2 kg steak): the sanitising already clamps at 2000 g, but the benchmark must report how often it clamps, or the clamp hides a broken estimate. Covered in Task 6.

---

### Task 1: Generate the food and product assets from Open Food Facts

**Files:**
- Create: `tools/off_extract.py`
- Create: `tools/README.md`
- Modify: `app/src/main/assets/base_foods.json` (regenerated, currently 153 entries)
- Create: `app/src/main/assets/products.csv`

**Interfaces:**
- Produces: `app/src/main/assets/products.csv`, a header-less CSV sorted by barcode with the columns `barcode,name,brand,kcal,protein,carbs,fat,fiber,sugar,saturated,salt,serving`. Numbers use a dot as the decimal separator, text fields are quoted only when they contain a comma.
- Produces: `app/src/main/assets/base_foods.json`, the existing compact format: a JSON array of `{"n","k","p","c","f","fi","s","sf","sa","sv"}` objects, per 100 g, `sv` = default serving in grams.

- [ ] **Step 1: Install DuckDB for the current user**

```bash
~/.local/bin/netfix pip install --user duckdb
python3 -c "import duckdb; print(duckdb.__version__)"
```

- [ ] **Step 2: Write `tools/off_extract.py`**

The script queries the Open Food Facts parquet dump directly over HTTPS so the 7.8 GB file never has to land on disk. DuckDB reads only the columns and row groups it needs.

Requirements the script must meet:
- Source: `https://huggingface.co/datasets/openfoodfacts/product-database/resolve/main/food.parquet`, read with `duckdb` and `httpfs`.
- Products kept for `products.csv`: `countries_tags` contains `en:germany`, `code` is 8 to 14 digits, a product name exists, `energy-kcal_100g` is between 1 and 900, protein/carbs/fat are each between 0 and 100, and the macros are plausible against the calories (`abs(kcal - (4*p + 4*c + 9*f)) <= max(50, 0.3*kcal)`).
- Ranking: descending `unique_scans_n`, then keep the top 40000. Write the result sorted by barcode ascending so the app can rely on it.
- Names: prefer `product_name_de`, else `product_name`. Collapse whitespace, cut at 70 characters.
- For `base_foods.json`: group the same German product set by `categories_tags`, keep categories with at least 30 products, and take the **median** of every nutrient per category. The category name comes from the German category taxonomy name; skip categories whose name is not German or is shorter than 3 characters. Cap at 1200 categories. Default serving is the median `serving_quantity` where present, else 100.
- Keep the 153 hand-written entries of the current `base_foods.json`: they carry the names the analyzer's own catalogue uses ("Reis (gekocht)") and must stay, ahead of the generated ones. Deduplicate generated entries against them by lowercased name.
- Print a summary: number of products, number of generated foods, the ten most-scanned products, and the file sizes.

- [ ] **Step 3: Run the script and check the output**

```bash
cd ~/Kalorien-Tracker && ~/.local/bin/netfix python3 tools/off_extract.py
ls -lh app/src/main/assets/
head -3 app/src/main/assets/products.csv
python3 -c "import json;d=json.load(open('app/src/main/assets/base_foods.json'));print(len(d),'Einträge');print(d[0]);print(d[-1])"
```

Expected: `products.csv` under 6 MB with 40000 lines, `base_foods.json` with clearly more than 153 entries, and the first 153 unchanged.

- [ ] **Step 4: Spot-check three real German barcodes against the live API**

Pick three barcodes from the generated file, for instance a Milka bar, a Haribo bag, and a supermarket own brand, and compare the bundled values with what the Open Food Facts API returns today.

```bash
for c in 7622210449283 4001686301203 4337185645013; do
  grep -m1 "^$c," app/src/main/assets/products.csv
  ~/.local/bin/netfix curl -s "https://world.openfoodfacts.org/api/v2/product/$c.json?fields=product_name,nutriments" | python3 -c "import json,sys;d=json.load(sys.stdin);p=d.get('product',{});print('  API:',p.get('product_name'),p.get('nutriments',{}).get('energy-kcal_100g'))"
done
```

Expected: the bundled kcal matches the API within rounding. Note any mismatch in the commit message.

- [ ] **Step 5: Write `tools/README.md`**

One page: what the script does, how to run it, where the data comes from, that the data is ODbL and the app therefore names Open Food Facts, and when to regenerate.

- [ ] **Step 6: Commit**

```bash
git add tools app/src/main/assets
git commit -m "Generate the food and product tables from Open Food Facts"
```

---

### Task 2: Import the product table into Room in the background

**Files:**
- Create: `app/src/main/java/com/kalorientracker/app/data/db/ProductCatalogImporter.kt`
- Create: `app/src/test/java/com/kalorientracker/app/data/db/ProductCsvParserTest.kt`
- Modify: `app/src/main/java/com/kalorientracker/app/data/repository/FoodRepository.kt`
- Modify: `app/src/main/java/com/kalorientracker/app/KalorienTrackerApp.kt`

**Interfaces:**
- Consumes: `app/src/main/assets/products.csv` from Task 1.
- Produces: `ProductCatalogImporter.parseLine(line: String): Food?` (pure, testable) and `suspend fun importIfNeeded()`, plus `FoodRepository.catalogReady: Boolean`.

- [ ] **Step 1: Write the failing parser test**

```kotlin
class ProductCsvParserTest {
    @Test
    fun parsesAPlainLine() {
        val food = ProductCatalogImporter.parseLine("4001686301203,Goldbären,Haribo,343,6.9,77,0.5,0,46,0.1,0.07,100")!!
        assertEquals("4001686301203", food.barcode)
        assertEquals("Goldbären", food.name)
        assertEquals("Haribo", food.brand)
        assertEquals(343.0, food.per100g.kcal, 0.0)
        assertEquals(100.0, food.servingGrams!!, 0.0)
    }

    @Test
    fun parsesQuotedNamesWithCommas() {
        val food = ProductCatalogImporter.parseLine("""4000417025005,"Nuss-Nougat, extra",Nutella,539,6.3,57.5,30.9,0,56.3,10.6,0.107,15""")!!
        assertEquals("Nuss-Nougat, extra", food.name)
    }

    @Test
    fun rejectsBrokenOrImplausibleLines() {
        assertNull(ProductCatalogImporter.parseLine(""))
        assertNull(ProductCatalogImporter.parseLine("abc,Name,,100,1,1,1,0,0,0,0,100"))
        assertNull(ProductCatalogImporter.parseLine("4001686301203,Name,,0,0,0,0,0,0,0,0,100"))
        assertNull(ProductCatalogImporter.parseLine("4001686301203,Name,,5000,1,1,1,0,0,0,0,100"))
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd ~/Kalorien-Tracker && JAVA_HOME=~/.local/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk ~/.local/bin/netfix ./gradlew testDebugUnitTest --tests "*ProductCsvParserTest*"
```

Expected: FAIL, unresolved reference `ProductCatalogImporter`.

- [ ] **Step 3: Write the importer**

`parseLine` is a companion function with no Android dependency: split on commas honouring double quotes, expect twelve fields, reject a barcode that is not 8 to 14 digits, reject kcal outside 1..900, and build a `Food` with `source = FoodSource.ONLINE_CACHED`.

`importIfNeeded()` runs on `Dispatchers.IO`, returns early when a flag in `SharedPreferences` says the catalogue version is already imported, reads the asset line by line, and inserts in chunks of 2000 inside one transaction per chunk. It bumps the flag only after the last chunk, so an interrupted import simply runs again. The importer uses `foodDao.insertAll(...)` with `OnConflictStrategy.IGNORE` so an existing barcode is never overwritten.

- [ ] **Step 4: Run the test and watch it pass**

```bash
cd ~/Kalorien-Tracker && JAVA_HOME=~/.local/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk ~/.local/bin/netfix ./gradlew testDebugUnitTest --tests "*ProductCsvParserTest*"
```

- [ ] **Step 5: Start the import after the first launch**

In `KalorienTrackerApp.onCreate`, launch `importIfNeeded()` in an application-scoped coroutine on `Dispatchers.IO`. It must never block the UI and never run on the main thread.

In `FoodRepository.lookupBarcode`, when the local lookup misses and the import has not finished yet, continue to the online path exactly as today. The user must never see "not found" only because the import is still running.

- [ ] **Step 6: Measure the import in the emulator**

```bash
kt-emu build assembleDebug && kt-emu install && kt-emu run
~/Android/Sdk/platform-tools/adb logcat -d -s ProductCatalogImporter
```

Expected: a log line with the number of imported rows and the duration. Note the duration in the commit message. If it exceeds 30 seconds, raise the chunk size and measure again.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "Import the bundled product table once in the background"
```

---

### Task 3: Make German barcodes resolve in every common format

**Files:**
- Create: `app/src/main/java/com/kalorientracker/app/data/repository/Gtin.kt`
- Create: `app/src/test/java/com/kalorientracker/app/data/repository/GtinTest.kt`
- Modify: `app/src/main/java/com/kalorientracker/app/data/repository/FoodRepository.kt`
- Modify: `app/src/main/java/com/kalorientracker/app/data/remote/OpenFoodFactsSource.kt`

**Interfaces:**
- Produces: `Gtin.candidates(raw: String): List<String>` — the codes to try, most specific first; `Gtin.isValid(code: String): Boolean` — check digit test.

- [ ] **Step 1: Write the failing test**

```kotlin
class GtinTest {
    @Test
    fun upcAIsAlsoTriedAsEan13() {
        assertEquals(listOf("012345678905", "0012345678905"), Gtin.candidates("012345678905"))
    }

    @Test
    fun ean13WithALeadingZeroIsAlsoTriedAsUpcA() {
        assertEquals(listOf("0012345678905", "012345678905"), Gtin.candidates("0012345678905"))
    }

    @Test
    fun ean8StaysAsItIs() {
        assertEquals(listOf("40123455"), Gtin.candidates("40123455"))
    }

    @Test
    fun spacesAndDashesAreIgnored() {
        assertEquals(listOf("4001686301203"), Gtin.candidates(" 4001686-301203 "))
    }

    @Test
    fun checkDigitIsVerified() {
        assertTrue(Gtin.isValid("4001686301203"))
        assertFalse(Gtin.isValid("4001686301204"))
        assertFalse(Gtin.isValid("123"))
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd ~/Kalorien-Tracker && JAVA_HOME=~/.local/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk ~/.local/bin/netfix ./gradlew testDebugUnitTest --tests "*GtinTest*"
```

- [ ] **Step 3: Implement `Gtin`**

Strip everything that is not a digit. A 12-digit code also gets tried with a leading zero, a 13-digit code starting with zero also without it. The check digit is the standard GTIN modulo 10 over the reversed digits with weights 3 and 1.

- [ ] **Step 4: Use the candidates in the lookup**

`FoodRepository.lookupBarcode` tries every candidate against the local database first, then online. `OpenFoodFactsSource.byBarcode` gets a User-Agent that names the app and a contact, as Open Food Facts asks for, and queries `de.openfoodfacts.org` first because it returns German product names, falling back to `world.openfoodfacts.org` when the German host has no product.

- [ ] **Step 5: Run the tests and watch them pass**

```bash
cd ~/Kalorien-Tracker && JAVA_HOME=~/.local/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk ~/.local/bin/netfix ./gradlew testDebugUnitTest
```

- [ ] **Step 6: Scan three barcodes in the emulator**

The emulator camera cannot scan a real barcode, so drive the lookup directly: add a temporary debug entry point or call the repository from an instrumented test. Simplest: put three codes into the manual search field of the barcode screen if it has one, otherwise verify through the unit tests and note in the commit message that the scan path itself was verified on the phone.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "Resolve barcodes in every common German format"
```

---

### Task 4: Keep foods the recognition found but the database did not know

**Files:**
- Modify: `app/src/main/java/com/kalorientracker/app/domain/model/` (the `FoodSource` enum)
- Modify: `app/src/main/java/com/kalorientracker/app/data/repository/FoodRepository.kt`
- Modify: `app/src/main/java/com/kalorientracker/app/ui/add/AddFlowViewModel.kt`
- Create: `app/src/test/java/com/kalorientracker/app/data/repository/RememberUnknownFoodTest.kt`

**Interfaces:**
- Produces: `suspend fun FoodRepository.remember(name: String, per100g: Nutrients, servingGrams: Double?): Food` — returns the existing food when the name is already known, otherwise inserts it with `FoodSource.AI_ESTIMATED`.

- [ ] **Step 1: Write the failing test**

```kotlin
class RememberUnknownFoodTest {
    @Test
    fun anUnknownFoodIsStoredOnce() = runTest {
        val repo = repositoryWithFoods(emptyList())
        val first = repo.remember("Ofenkartoffel", nutrients(93.0), 250.0)
        val second = repo.remember("ofenkartoffel", nutrients(93.0), 250.0)
        assertEquals(first.id, second.id)
        assertEquals(FoodSource.AI_ESTIMATED, first.source)
    }

    @Test
    fun aKnownFoodIsNeverDuplicated() = runTest {
        val repo = repositoryWithFoods(listOf(food("Reis (gekocht)", 130.0)))
        val result = repo.remember("Reis (gekocht)", nutrients(150.0), 200.0)
        assertEquals(130.0, result.per100g.kcal, 0.0)
    }
}
```

The test needs a fake `FoodDao`; write it in the test file with a `MutableList<FoodEntity>` behind it.

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd ~/Kalorien-Tracker && JAVA_HOME=~/.local/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk ~/.local/bin/netfix ./gradlew testDebugUnitTest --tests "*RememberUnknownFoodTest*"
```

- [ ] **Step 3: Implement it**

Add `AI_ESTIMATED` to `FoodSource`. `remember` looks the name up case-insensitively through the existing `byName`, returns the hit unchanged, and otherwise inserts. In `AddFlowViewModel.save`, after the meal is written, call `remember` for every ingredient that has no `foodId`. Never call it during the analysis, only on save, so discarded drafts leave nothing behind.

- [ ] **Step 4: Run the tests and watch them pass**

- [ ] **Step 5: Verify in the emulator**

Log a meal with a description the database does not know, save it, then search for that name under "Manuell". It must appear.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "Remember foods the recognition found but the database did not know"
```

---

### Task 5: Check the calculator against other calculators

**Files:**
- Create: `/mnt/hdd/Starlos/Analysen/Kalorienrechner Vergleich.md`
- Modify: the calculator only if a genuine error shows up.

- [ ] **Step 1: Define the test person**

Male, 30 years, 180 cm, 85 kg, 7000 steps a day, running three times a week for 45 minutes, goal losing weight. This is the data to enter in the emulator's onboarding and in every calculator.

- [ ] **Step 2: Read the app's result from the emulator**

Drive onboarding with the UI script, read maintenance, target and macros from the plan step, and take a screenshot.

- [ ] **Step 3: Compare against at least four independent calculators**

Use the DGE reference table itself plus calculators such as those of AOK, Techniker Krankenkasse, and a well-known fitness calculator. Note for every one: name, URL, the activity level it asks for, the result, and its stated formula.

- [ ] **Step 4: Write the analysis note**

The note holds the test person, a table of results, the spread, and a verdict: whether the app sits inside the spread of the others and how the differences are explained (basal metabolic rate versus resting energy expenditure, PAL choice, sport counted separately or inside the factor).

- [ ] **Step 5: Only if the app sits outside the spread, fix the cause**

Do not tune numbers to match another calculator. Find the reason first, then decide.

- [ ] **Step 6: Commit**

Note goes into the vault, an app change only if Step 5 found one.

---

### Task 6: Measure the photo recognition against ground truth

**Files:**
- Create: `tools/bench/` (JVM harness, Kotlin, driven by a shell script)
- Create: `/mnt/hdd/Starlos/Analysen/Foto-Erkennung Messung.md`
- Raw results: `/mnt/hdd/Starlos/Analysen/daten/foto-messung-2026-09-26.csv`
- Modify: `app/src/main/java/com/kalorientracker/app/data/analyzer/GemmaPrompt.kt` if the measurement shows a fixable weakness.

**Interfaces:**
- Consumes: `GemmaPrompt.SYSTEM`, `GemmaPrompt.analysisPrompt`, `GemmaPrompt.SCHEMA`, `GemmaPrompt.parse` — the production code, not a copy.

- [ ] **Step 1: Get the model onto the PC without downloading it again**

```bash
~/Android/Sdk/platform-tools/adb pull /sdcard/Android/data/com.kalorientracker.app/files/models/gemma-4-E2B-it.litertlm ~/.local/share/kalorien-bench/
ls -l ~/.local/share/kalorien-bench/
```

- [ ] **Step 2: Pick 25 dishes from Nutrition5k**

The metadata line format is `dish_id,total_calories,total_mass,total_fat,total_carb,total_protein,` then repeating `ingr_id,name,grams,kcal,fat,carb,protein`. Take dishes with 2 to 5 ingredients and 150 to 900 kcal, so they look like a normal plate.

```bash
~/.local/bin/netfix curl -s -o /tmp/n5k.csv "https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset/metadata/dish_metadata_cafe1.csv"
```

The overhead photo of a dish is at `https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset/imagery/realsense_overhead/<dish_id>/rgb.png`.

- [ ] **Step 3: Build the harness**

A Gradle project under `tools/bench` that depends on `com.google.ai.edge.litertlm:litertlm-jvm:0.17.1` and compiles the production files `GemmaPrompt.kt`, `DescriptionAnchors.kt`, `StubFoodAnalyzer.kt`, `FoodAnalyzer.kt` plus the domain model files they need. It runs, per dish: resize the image to 768 px, send the system prompt and the analysis prompt with an English-to-German ingredient description built from the ground truth ingredient names, then parse with `GemmaPrompt.parse`.

Two runs per dish: one with a description (the dish's ingredient names, as a user would type them) and one without, so the value of the description is measured, not assumed.

- [ ] **Step 4: Write the raw results**

One CSV row per run: `dish_id,mode,ground_truth_kcal,predicted_kcal,abs_error,rel_error,gt_mass,predicted_mass,ingredients_found,ingredients_expected,clamped,seconds`. The raw file lives in the vault, not in a temp directory.

- [ ] **Step 5: Report the numbers**

Median and mean relative error for both modes, share of dishes within 25 % of the truth, how often the 2000 g clamp fired, how many named ingredients were found. State the numbers, do not describe them as good or bad.

- [ ] **Step 6: Derive at most two concrete improvements, then measure again**

Candidates, in order of expected value: a sentence in the system prompt about the plate size as a scale, a portion prior per food group taken from the bundled database instead of leaving grams entirely to the model, and a hint that a photo taken from above shows the full plate. Change one thing, measure again on the same 25 dishes, and keep the change only if the median error improves.

- [ ] **Step 7: Write the analysis note and commit**

The note carries the question, the method, both measurement rounds, and what was changed. The code change goes into the app with its own commit.

---

### Task 7: Take the machine tone out of the copy

**Files:**
- Modify: every Kotlin file under `app/src/main/java/com/kalorientracker/app/ui/` that holds German user-visible text.
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: List every dash used as a pause**

```bash
cd ~/Kalorien-Tracker && grep -rn '"[^"]*[^0-9] – [^"]*"' --include=*.kt app/src/main | tee /tmp/dashes.txt | wc -l
```

- [ ] **Step 2: Rewrite each one as plain German**

A dash as a pause becomes a full stop and a second sentence, or a comma. Ranges and the "–" placeholder stay. Example: "Bereit – Fotos werden jetzt auf dem Gerät analysiert." becomes "Bereit. Fotos werden jetzt auf dem Gerät analysiert."

- [ ] **Step 3: Remove the other tells**

Search for and rewrite: sentences that pair two halves with "sondern" or "nicht nur … sondern auch", stacked triples, the word "nahtlos", "einfach und intuitiv", "Hinweis:" as a prefix, and any sentence that explains the app's own virtue instead of stating a fact. Copy states what is true and what the user can do.

- [ ] **Step 4: Check the result in the emulator**

Walk every screen, read the copy on the screenshots, and fix anything that still sounds written by a machine.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Write the app copy in plain German"
```

---

### Task 8: Visual polish

**Files:**
- Modify: `app/src/main/java/com/kalorientracker/app/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/kalorientracker/app/ui/statistics/StatisticsScreen.kt`
- Modify: `app/src/main/java/com/kalorientracker/app/ui/common/Components.kt`

- [ ] **Step 1: Take a screenshot of every screen in its empty state and in its filled state**

Empty states are where an app looks unfinished. Log one meal and one weight entry for the filled state.

- [ ] **Step 2: Fix what the screenshots show**

The concrete items, each verified by a screenshot before and after:
- Today with no meals: the meal list area must say what to do next, not stay blank.
- The metric chip row in the statistics screen is cut off at the right edge. Give the row a start and end padding of 20 dp and let it scroll, so no chip is ever half visible at rest.
- The weight card and the calorie ring use different corner radii. Make every card on Today use the same radius.
- The settings list rows have no pressed state. Add the same ripple the other rows use.

- [ ] **Step 3: Check every change against the screenshots**

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "Tighten the screens that looked unfinished"
```

---

### Task 9: Full walkthrough in the emulator

**Files:**
- Create: `/mnt/hdd/Starlos/Analysen/Emulator Durchlauf 2026-09-26.md`

- [ ] **Step 1: Fresh install, fresh data**

```bash
~/Android/Sdk/platform-tools/adb shell pm clear com.kalorientracker.app
kt-emu build assembleDebug && kt-emu install && kt-emu run
```

- [ ] **Step 2: Walk every flow and note the result per flow**

Onboarding with the test person from Task 5, today view, weight entry, photo via gallery with the model, follow-up question, ingredient editing, portion slider, saving, manual entry, food search against the new database, barcode lookup, statistics across every range, settings including the sources block, data export and import round trip, deleting everything.

- [ ] **Step 3: Write the walkthrough note**

One line per flow: what was done, what happened, pass or fail. Failures become fixes in this task, then the flow is walked again.

- [ ] **Step 4: Release build and final check**

```bash
cd ~/Kalorien-Tracker && JAVA_HOME=~/.local/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk ~/.local/bin/netfix ./gradlew testDebugUnitTest lintDebug assembleRelease
ls -lh app/build/outputs/apk/release/app-release.apk
```

Expected: tests green, lint without errors, APK under 60 MB.

- [ ] **Step 5: Commit and push**

```bash
git add -A && git commit -m "Walk every flow in the emulator" && git push origin claude/hopeful-pascal-hy7fby
```

---

### Task 10: Update the second brain

**Files:**
- Modify: `/mnt/hdd/Starlos/Projekte/Kalorien-Tracker.md`
- Modify: `/mnt/hdd/Starlos/Log/Änderungsprotokoll.md`
- Modify: `/mnt/hdd/Starlos/Home.md` if new notes were created

- [ ] **Step 1: Bring the project note up to date**

The state of the emulator setup, the new data pipeline, where the assets come from, the measured numbers, the new release link.

- [ ] **Step 2: Link the new analysis notes and log every change**

Newest line on top in the change log, absolute dates.

---

### Task 11: German dishes from the USDA survey database

Added after Task 1, because the data showed the gap. Open Food Facts is a database of packaged products: it gave the app ready meals in packets but almost no cooked dishes. A search for Gulasch, Auflauf, Eintopf or Pommes mit Currywurst finds nothing useful, and those are exactly what the user eats and photographs.

The numbers come from the USDA FoodData Central survey database (FNDDS), which holds composite cooked dishes with full nutrients per 100 g and is in the public domain. Only the names are translated; no nutrient value is invented or adjusted.

**Files:**
- Create: `tools/dishes.py`
- Create: `tools/dishes_mapping.csv` (the German name to FNDDS food mapping, hand-curated, committed so it can be checked)
- Modify: `app/src/main/assets/base_foods.json`
- Modify: `tools/README.md`
- Modify: `app/src/main/java/com/kalorientracker/app/ui/common/SourceNote.kt`

- [ ] **Step 1: Get the FNDDS data**

```bash
~/.local/bin/netfix curl -sL -o /tmp/fndds.zip "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_csv_2024-10-31.zip"
unzip -o /tmp/fndds.zip -d /tmp/fndds && ls /tmp/fndds
```

If that exact file name 404s, list what the FoodData Central download page offers and take the current survey (FNDDS) CSV export. Record the file name and date you used.

- [ ] **Step 2: Build the mapping**

`tools/dishes_mapping.csv` has three columns: `deutscher_name,fdc_id,portion_g`. Cover at least 80 dishes a German household actually eats, among them: Gulasch, Rindergulasch, Kartoffelauflauf, Nudelauflauf, Lasagne, Spaghetti Bolognese, Currywurst, Bratwurst mit Sauerkraut, Königsberger Klopse, Rouladen, Frikadellen, Kartoffelsalat, Nudelsalat, Linseneintopf, Erbsensuppe, Gulaschsuppe, Hühnerfrikassee, Kohlrouladen, Kassler mit Sauerkraut, Schweinebraten, Semmelknödel, Kartoffelklöße, Pfannkuchen, Kaiserschmarrn, Milchreis, Grießbrei, Rührei mit Speck, Bratkartoffeln, Kartoffelpuffer, Gemüsepfanne, Hähnchenpfanne, Reispfanne, Chili con Carne, Gyros mit Tzatziki, Falafel, Hummus, Couscoussalat, Lachsfilet gebraten, Fischstäbchen, Backfisch, Quiche, Pizzabrötchen, Flammkuchen, Käsespätzle, Maultaschen, Schupfnudeln, Grünkohl mit Pinkel, Labskaus.

For each, pick the FNDDS food whose description matches the dish, not a near relative: "Gulasch" maps to a beef stew with vegetables, not to plain beef. Write the FNDDS description into a fourth column `fndds_beschreibung` so a reader can judge the match without looking it up.

- [ ] **Step 3: Write `tools/dishes.py`**

It reads the mapping and the FNDDS CSVs, looks up the nutrients per 100 g for each `fdc_id` (energy in kcal, protein, carbohydrate, total fat, fiber, total sugars, saturated fat, sodium converted to salt with `salt = sodium_mg * 2.5 / 1000`), and appends the dishes to `app/src/main/assets/base_foods.json` in the existing compact format, after the entries that are already there. `portion_g` becomes `sv`.

Rules: skip a dish whose FNDDS id is missing or has no energy value, and report it rather than guessing. Round to one decimal. The script is deterministic and re-runnable: running it twice must not duplicate entries, so it rebuilds the dish block rather than appending blindly.

- [ ] **Step 4: Check the result**

```bash
python3 -c "
import json; d=json.load(open('app/src/main/assets/base_foods.json'))
print(len(d),'Einträge')
names=[x['n'] for x in d]
for w in ['Gulasch','Auflauf','Eintopf','Currywurst','Lasagne','Frikadelle','Kaiserschmarrn']:
    print(w, [n for n in names if w.lower() in n.lower()][:3])
print('Duplikate:', len(names)-len(set(n.lower() for n in names)))
"
```

Expected: every listed dish is found, no duplicates, and the total is the previous count plus the number of mapped dishes.

- [ ] **Step 5: Sanity-check ten dishes against their own ingredients**

A cooked dish should land between roughly 80 and 350 kcal per 100 g. Anything outside that range is either a mismatched FNDDS food or a unit error. Check ten by hand, list them in the report with their kcal, and fix the mapping where a match is wrong.

- [ ] **Step 6: Name the source in the app and in the README**

Add the USDA FoodData Central survey database to the sources block next to Open Food Facts, in the same plain German style, and record in `tools/README.md` what was downloaded, when, and that it is public domain.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "Add German dishes from the USDA survey database"
```
