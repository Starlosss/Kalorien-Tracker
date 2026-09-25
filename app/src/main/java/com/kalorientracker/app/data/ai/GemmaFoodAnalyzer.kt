package com.kalorientracker.app.data.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.kalorientracker.app.data.analyzer.AnalysisContext
import com.kalorientracker.app.data.analyzer.AnalysisInput
import com.kalorientracker.app.data.analyzer.AnalysisResult
import com.kalorientracker.app.data.analyzer.FollowUpAnswer
import com.kalorientracker.app.data.analyzer.GemmaPrompt
import com.kalorientracker.app.data.image.PhotoStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs Gemma 4 E2B on the device via LiteRT-LM. Photos never leave the phone. The engine is created
 * lazily (GPU first, CPU as fallback) and released after a short idle period to give memory back.
 */
@Singleton
class GemmaFoodAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelManager,
    private val photos: PhotoStorage,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: Engine? = null
    private var releaseJob: Job? = null

    /** Returns null when the model produced nothing usable; throws when the runtime itself fails. */
    suspend fun analyze(input: AnalysisInput): AnalysisResult? = withContext(Dispatchers.Default) {
        val images = input.photoPaths.take(MAX_IMAGES).mapNotNull { photos.analysisCopy(it) }
        try {
            val parts = buildList<Content> {
                images.forEach { add(Content.ImageFile(it.absolutePath)) }
                add(Content.Text(GemmaPrompt.analysisPrompt(input.description, images.size, input.corrections)))
            }
            val text = generate(parts)
            GemmaPrompt.parse(text, input.description, images.size, input.corrections)
        } finally {
            images.forEach(File::delete)
        }
    }

    /** Follow-up answers are applied in a text-only round; the images are not needed again. */
    suspend fun answerFollowUp(analysis: AnalysisContext, answers: List<FollowUpAnswer>): AnalysisResult? =
        withContext(Dispatchers.Default) {
            val text = generate(listOf(Content.Text(GemmaPrompt.followUpPrompt(analysis, answers))))
            GemmaPrompt.parse(text, analysis.description, analysis.photoCount, analysis.corrections, allowQuestions = false)
                ?.let { it.copy(context = it.context.copy(photoCount = analysis.photoCount)) }
        }

    private suspend fun generate(parts: List<Content>): String = mutex.withLock {
        releaseJob?.cancel()
        val engine = engineOrCreate()
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
        try {
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
            scheduleRelease()
        }
    }

    private fun engineOrCreate(): Engine {
        engine?.let { return it }
        val path = models.modelFile?.takeIf { models.isReady() }?.absolutePath
            ?: throw IllegalStateException("KI-Modell ist nicht geladen.")
        val created = runCatching { create(path, Backend.GPU()) }
            .onFailure { Log.w(TAG, "GPU backend unavailable, falling back to CPU", it) }
            .getOrElse { create(path, Backend.CPU()) }
        engine = created
        return created
    }

    private fun create(path: String, backend: Backend): Engine {
        val config = EngineConfig(path, backend, backend, null, MAX_TOKENS, MAX_IMAGES, context.cacheDir.absolutePath)
        return Engine(config).also { it.initialize() }
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            mutex.withLock {
                engine?.close()
                engine = null
            }
        }
    }

    companion object {
        private const val TAG = "GemmaFoodAnalyzer"
        const val MAX_IMAGES = 3
        private const val MAX_TOKENS = 4096
        private const val MAX_OUTPUT_TOKENS = 1024
        private const val TOP_K = 40
        private const val TOP_P = 0.9
        private const val TEMPERATURE = 0.2
        private const val SEED = 0
        private const val IDLE_RELEASE_MS = 90_000L
    }
}
