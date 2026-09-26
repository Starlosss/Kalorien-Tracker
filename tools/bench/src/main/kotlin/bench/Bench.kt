package bench

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.kalorientracker.app.data.analyzer.GemmaPrompt
import com.kalorientracker.app.data.analyzer.RecognizedIngredient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.exitProcess

/**
 * Measures the app's photo recognition against Nutrition5k plates whose weight and calories are
 * known. The prompt, the schema and the parser come from the production sources, which this
 * build compiles in directly (see build.gradle.kts); nothing here re-implements them.
 */

@Serializable
data class BenchIngredient(val en: String, val de: String, val grams: Double, val kcal: Double, val keys: List<String>)

@Serializable
data class BenchDish(
    val dishId: String,
    val groundTruthKcal: Double,
    val groundTruthMass: Double,
    val description: String,
    val ingredients: List<BenchIngredient>,
)

private const val PER_RUN_TIMEOUT_SECONDS = 300L
private const val MAX_TOKENS = 4096
private const val MAX_IMAGES = 3
private const val MAX_OUTPUT_TOKENS = 1024
private const val TOP_K = 40
private const val TOP_P = 0.9
private const val TEMPERATURE = 0.2
private const val SEED = 0
private const val MODEL_EDGE = 768

private val CSV_HEADER =
    "dish_id,mode,ground_truth_kcal,predicted_kcal,abs_error,rel_error,gt_mass,predicted_mass," +
        "ingredients_found,ingredients_expected,clamped,seconds"

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
    val model = File(options["--model"] ?: "${System.getProperty("user.home")}/.local/share/kalorien-bench/gemma-4-E2B-it.litertlm")
    val images = File(options["--images"] ?: "${System.getProperty("user.home")}/.local/share/kalorien-bench/images")
    val dishesFile = File(options["--dishes"] ?: "dishes.json")
    val csv = File(options["--csv"] ?: "results.csv")
    val rawLog = File(options["--raw"] ?: csv.absolutePath.removeSuffix(".csv") + "-raw.jsonl")
    val cache = File(options["--cache"] ?: System.getProperty("java.io.tmpdir") + "/kalorien-bench-cache").also { it.mkdirs() }

    val dishes = Json { ignoreUnknownKeys = true }.decodeFromString<List<BenchDish>>(dishesFile.readText())
    val done = if (csv.exists()) csv.readLines().drop(1).mapNotNull { it.substringBefore(',').ifEmpty { null }?.let { id -> id + "|" + it.split(',').getOrNull(1) } }.toSet() else emptySet()
    if (!csv.exists() || csv.length() == 0L) csv.writeText(CSV_HEADER + "\n")

    val todo = dishes.flatMap { dish -> listOf("mit_beschreibung", "ohne_beschreibung").map { dish to it } }
        .filterNot { (dish, mode) -> "${dish.dishId}|$mode" in done }
    if (todo.isEmpty()) {
        println("Nichts zu tun, alle Läufe stehen schon in ${csv.absolutePath}")
        return
    }
    println("Offene Läufe: ${todo.size}")

    val engine = Engine(EngineConfig(model.absolutePath, Backend.CPU(), Backend.CPU(), null, MAX_TOKENS, MAX_IMAGES, cache.absolutePath))
    engine.initialize()
    val worker = Executors.newSingleThreadExecutor()

    for ((dish, mode) in todo) {
        val description = if (mode == "mit_beschreibung") dish.description else ""
        val source = File(images, "${dish.dishId}.png")
        if (!source.isFile) {
            System.err.println("Foto fehlt: ${source.absolutePath}")
            continue
        }
        val prepared = analysisCopy(source, cache)
        val parts = listOf(
            Content.ImageFile(prepared.absolutePath),
            Content.Text(GemmaPrompt.analysisPrompt(description, 1, emptyList())),
        )
        val started = System.nanoTime()
        val raw = try {
            worker.submit<String> { runOnce(engine, parts) }.get(PER_RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (timeout: TimeoutException) {
            val seconds = (System.nanoTime() - started) / 1e9
            csv.appendText("${dish.dishId},$mode,${fmt(dish.groundTruthKcal)},,,,${fmt(dish.groundTruthMass)},,,${dish.ingredients.size},timeout,${fmt(seconds)}\n")
            System.err.println("Zeitüberschreitung bei ${dish.dishId} ($mode). Der Prozess wird beendet, das Skript startet neu.")
            prepared.delete()
            exitProcess(2)
        } catch (t: Throwable) {
            val seconds = (System.nanoTime() - started) / 1e9
            csv.appendText("${dish.dishId},$mode,${fmt(dish.groundTruthKcal)},,,,${fmt(dish.groundTruthMass)},,,${dish.ingredients.size},fehler,${fmt(seconds)}\n")
            System.err.println("Fehler bei ${dish.dishId} ($mode): ${t.message}")
            prepared.delete()
            exitProcess(3)
        }
        val seconds = (System.nanoTime() - started) / 1e9
        prepared.delete()

        val result = GemmaPrompt.parse(raw, description, 1, emptyList())
        val ingredients = result?.ingredients.orEmpty()
        val predictedKcal = ingredients.sumOf { it.per100g.kcal * it.estimatedGrams / 100.0 }
        val predictedMass = ingredients.sumOf { it.estimatedGrams }
        val found = dish.ingredients.count { expected -> ingredients.any { matches(it, expected) } }
        val clamped = clampFired(raw)
        val absError = abs(predictedKcal - dish.groundTruthKcal)
        val relError = absError / dish.groundTruthKcal

        csv.appendText(
            listOf(
                dish.dishId, mode, fmt(dish.groundTruthKcal), fmt(predictedKcal), fmt(absError), fmt(relError),
                fmt(dish.groundTruthMass), fmt(predictedMass), found.toString(), dish.ingredients.size.toString(),
                if (clamped) "ja" else "nein", fmt(seconds),
            ).joinToString(","),
        )
        csv.appendText("\n")
        rawLog.appendText(Json.encodeToString(RawRecord(dish.dishId, mode, description, raw, ingredients.map { "${it.name} ${it.estimatedGrams}g ${it.per100g.kcal}kcal/100g" })) + "\n")
        println("${dish.dishId} $mode: ${fmt(predictedKcal)} kcal gegen ${fmt(dish.groundTruthKcal)} kcal, ${fmt(seconds)} s")
    }

    worker.shutdownNow()
    runCatching { engine.close() }
    println("Fertig.")
    exitProcess(0)
}

@Serializable
private data class RawRecord(val dishId: String, val mode: String, val description: String, val raw: String, val parsed: List<String>)

/** Mirrors GemmaFoodAnalyzer.runOn exactly: same conversation config, same sampler, same schema. */
private fun runOnce(engine: Engine, parts: List<Content>): String {
    val conversation = engine.createConversation(
        ConversationConfig(
            Contents.of(GemmaPrompt.SYSTEM),
            emptyList(),
            emptyList(),
            SamplerConfig(TOP_K, TOP_P, TEMPERATURE, SEED),
            false,
            emptyList(),
            emptyMap(),
            null,
            false,
            MAX_OUTPUT_TOKENS,
            ThinkingConfig(false),
            true,
        ),
    )
    return try {
        val reply = conversation.sendMessage(
            Contents.of(parts),
            emptyMap(),
            null,
            null,
            null,
            MAX_OUTPUT_TOKENS,
            ThinkingConfig(false),
            ResponseFormat.json(GemmaPrompt.SCHEMA),
        )
        reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
    } finally {
        conversation.close()
    }
}

/** Same preparation the app does before inference: longest edge 768 px, JPEG quality 90. */
private fun analysisCopy(source: File, cache: File): File {
    val decoded = ImageIO.read(source)
    val longest = max(decoded.width, decoded.height)
    val scale = if (longest > MODEL_EDGE) MODEL_EDGE.toDouble() / longest else 1.0
    val width = (decoded.width * scale).toInt().coerceAtLeast(1)
    val height = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = scaled.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.drawImage(decoded, 0, 0, width, height, null)
    g.dispose()
    val out = File.createTempFile("analysis_", ".jpg", cache)
    val writer = ImageIO.getImageWritersByFormatName("jpg").next()
    FileImageOutputStream(out).use { stream ->
        writer.output = stream
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.9f
        }
        writer.write(null, IIOImage(scaled, null, null), param)
    }
    writer.dispose()
    return out
}

/** The 2000 g ceiling in GemmaPrompt.parse only shows in the raw output, so it is read back there. */
private fun clampFired(raw: String): Boolean =
    Regex("\"gramm\"\\s*:\\s*(\\d+(?:\\.\\d+)?)").findAll(raw)
        .any { (it.groupValues[1].toDoubleOrNull() ?: 0.0) > GemmaPrompt.MAX_GRAMS }

/**
 * Whether a recognised ingredient is the ground truth ingredient. Word based, not substring based,
 * because "Reis" contains "ei" and would otherwise count as an egg.
 */
private fun matches(recognized: RecognizedIngredient, expected: BenchIngredient): Boolean {
    val tokens = tokensOf(recognized.name)
    return expected.keys.any { key -> tokens.any { it == key || (key.length >= 4 && it.startsWith(key)) || (it.length >= 4 && key.startsWith(it)) } }
}

private fun tokensOf(name: String): List<String> = fold(name).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

private fun fold(text: String): String = text.lowercase(Locale.GERMANY)
    .replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")

private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
