package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.engine.GeminiSemanticEngine
import com.example.engine.KeyframeEngine
import com.example.engine.MediaInspector
import com.example.engine.MediaStoreExporter
import com.example.engine.SampleVideoGenerator
import com.example.engine.SpeechToTextEngine
import com.example.engine.VideoRenderEngine
import com.example.model.ExportResult
import com.example.model.FramingMode
import com.example.model.GeminiAnalysisResult
import com.example.model.MediaInspection
import com.example.model.ReferenceStyleProfile
import com.example.model.RenderProgress
import com.example.model.RenderStage
import com.example.model.SpeechBoundary
import com.example.model.TargetAspectRatio
import com.example.model.TranscriptResult
import com.example.model.ZoomKeyframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AppTab(val title: String) {
    PROJECT("Project"),
    EDIT_PLAY("Edit & Play"),
    INSPECTOR("Inspector"),
    AI_SCORING("AI Scoring"),
    EXPORT("Export")
}

@UnstableApi
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "EditorViewModel"
    private val context: Context get() = getApplication<Application>().applicationContext

    // Current Navigation Tab
    private val _currentTab = MutableStateFlow(AppTab.PROJECT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Video State
    private val _videoFile = MutableStateFlow<File?>(null)
    val videoFile: StateFlow<File?> = _videoFile.asStateFlow()

    private val _inspection = MutableStateFlow<MediaInspection?>(null)
    val inspection: StateFlow<MediaInspection?> = _inspection.asStateFlow()

    // Reference Style Profile
    private val _styleProfile = MutableStateFlow(ReferenceStyleProfile())
    val styleProfile: StateFlow<ReferenceStyleProfile> = _styleProfile.asStateFlow()

    // Speech & AI State
    private val _transcript = MutableStateFlow(TranscriptResult())
    val transcript: StateFlow<TranscriptResult> = _transcript.asStateFlow()

    private val _aiResult = MutableStateFlow(GeminiAnalysisResult())
    val aiResult: StateFlow<GeminiAnalysisResult> = _aiResult.asStateFlow()

    private val _boundaries = MutableStateFlow<List<SpeechBoundary>>(emptyList())
    val boundaries: StateFlow<List<SpeechBoundary>> = _boundaries.asStateFlow()

    private val _keyframes = MutableStateFlow<List<ZoomKeyframe>>(emptyList())
    val keyframes: StateFlow<List<ZoomKeyframe>> = _keyframes.asStateFlow()

    // Processing status notice (e.g. Gemini availability)
    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Real-time Playback State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentZoomScale = MutableStateFlow(1.00f)
    val currentZoomScale: StateFlow<Float> = _currentZoomScale.asStateFlow()

    private val _activeBoundary = MutableStateFlow<SpeechBoundary?>(null)
    val activeBoundary: StateFlow<SpeechBoundary?> = _activeBoundary.asStateFlow()

    // Rendering State
    private val _renderProgress = MutableStateFlow(RenderProgress())
    val renderProgress: StateFlow<RenderProgress> = _renderProgress.asStateFlow()

    // Single stable ExoPlayer instance
    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = duration.coerceAtLeast(0L)
                    }
                }
            })
        }
    }

    private var playbackTickerJob: Job? = null

    init {
        startPlaybackTicker()
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun dismissNotice() {
        _statusNotice.value = null
    }

    /**
     * Imports a video from device URI, copies it safely to app internal cache,
     * inspects media streams, initializes playback, and triggers speech/AI analysis.
     */
    fun importVideoFromUri(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _statusNotice.value = "Importing and copying media stream..."

            val localFile = withContext(Dispatchers.IO) {
                val destFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream, bufferSize = 64 * 1024)
                    }
                }
                destFile
            }

            if (localFile.exists() && localFile.length() > 0) {
                processLoadedVideo(localFile)
            } else {
                _statusNotice.value = "Failed to copy video from storage."
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Generates or loads a self-contained test talking-head video for instant testing.
     */
    fun loadSampleVideo(isPortrait: Boolean = true) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _statusNotice.value = "Generating test talking-head studio video..."

            val sampleFile = SampleVideoGenerator.createSampleVideo(context, isPortrait = isPortrait)
            processLoadedVideo(sampleFile)
        }
    }

    private suspend fun processLoadedVideo(file: File) {
        _videoFile.value = file
        _statusNotice.value = "Inspecting media streams & container format..."

        val inspectionResult = MediaInspector.inspectMedia(context, file)
        _inspection.value = inspectionResult

        // Setup ExoPlayer on Main thread
        withContext(Dispatchers.Main) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }

        _statusNotice.value = "Transcribing audio & detecting cadence timestamps..."
        val transcriptResult = SpeechToTextEngine.transcribeVideoAudio(
            context,
            file,
            inspectionResult.durationMs
        )
        _transcript.value = transcriptResult

        _statusNotice.value = "Running Gemini semantic boundary analysis..."
        val (aiAnalysis, notice) = GeminiSemanticEngine.analyzeSpeechBoundaries(
            transcriptResult,
            inspectionResult.detectedFps
        )
        _aiResult.value = aiAnalysis
        if (notice != null) {
            _statusNotice.value = notice
        }

        // Convert Gemini boundary items into speech boundaries
        val speechBoundaries = aiAnalysis.boundaries.map { b ->
            SpeechBoundary(
                time = b.time,
                reason = b.reason,
                confidence = b.confidence,
                source = if (notice == null) "Gemini AI" else "Acoustic Cadence",
                frameIndex = (b.time * inspectionResult.detectedFps).toLong()
            )
        }
        _boundaries.value = speechBoundaries

        // Generate automatic zoom keyframes
        val kfs = KeyframeEngine.generateKeyframes(
            boundaries = speechBoundaries,
            fps = inspectionResult.detectedFps,
            durationMs = inspectionResult.durationMs,
            profile = _styleProfile.value
        )
        _keyframes.value = kfs

        _isAnalyzing.value = false
        if (notice == null) {
            _statusNotice.value = "AI analysis complete: ${kfs.size} keyframes generated."
        }
    }

    /**
     * Imports a reference video to learn a visual style profile.
     */
    fun importReferenceVideo(uri: Uri) {
        viewModelScope.launch {
            val refFile = withContext(Dispatchers.IO) {
                val f = File(context.cacheDir, "ref_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(f).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                f
            }

            if (refFile.exists()) {
                val refInspection = MediaInspector.inspectMedia(context, refFile)
                val learnedProfile = _styleProfile.value.copy(
                    name = "Learned: ${refFile.name.take(16)}",
                    isLearnedFromReference = true,
                    referenceVideoName = refFile.name,
                    wideScale = if (refInspection.isPortrait) 0.68f else 0.72f,
                    framingBehavior = if (refInspection.isPortrait) FramingMode.FIT else FramingMode.COVER
                )
                updateStyleProfile(learnedProfile)
                _statusNotice.value = "Learned reference style profile from ${refFile.name}"
            }
        }
    }

    fun updateStyleProfile(newProfile: ReferenceStyleProfile) {
        _styleProfile.value = newProfile
        val currentInsp = _inspection.value ?: return
        val currentBounds = _boundaries.value
        val updatedKfs = KeyframeEngine.generateKeyframes(
            boundaries = currentBounds,
            fps = currentInsp.detectedFps,
            durationMs = currentInsp.durationMs,
            profile = newProfile
        )
        _keyframes.value = updatedKfs
    }

    fun updateFramingMode(mode: FramingMode) {
        updateStyleProfile(_styleProfile.value.copy(framingBehavior = mode))
    }

    fun updateTargetAspectRatio(aspect: TargetAspectRatio) {
        updateStyleProfile(_styleProfile.value.copy(targetAspectRatio = aspect))
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        updateZoomAtTime(positionMs)
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    _currentPositionMs.value = pos
                    updateZoomAtTime(pos)
                }
                delay(16) // ~60 FPS update
            }
        }
    }

    private fun updateZoomAtTime(posMs: Long) {
        val kfs = _keyframes.value
        val profile = _styleProfile.value
        val scale = KeyframeEngine.calculateInterpolatedScale(posMs, kfs, profile)
        _currentZoomScale.value = scale

        // Find active boundary around current time (within 400ms)
        val posSec = posMs / 1000.0
        val active = _boundaries.value.firstOrNull { kotlin.math.abs(it.time - posSec) <= 0.4 }
        _activeBoundary.value = active
    }

    /**
     * Executes final deterministic rendering with black video validation and MediaStore export.
     */
    fun startRender() {
        val file = _videoFile.value ?: return
        val insp = _inspection.value ?: return
        val kfs = _keyframes.value
        val profile = _styleProfile.value

        viewModelScope.launch {
            try {
                VideoRenderEngine.renderEditedVideo(
                    context = context,
                    sourceFile = file,
                    inspection = insp,
                    keyframes = kfs,
                    profile = profile,
                    onProgress = { progress ->
                        _renderProgress.value = progress
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Render failed", e)
                _renderProgress.value = RenderProgress(
                    stage = RenderStage.FAILED,
                    error = "Render failed: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackTickerJob?.cancel()
        exoPlayer.release()
    }
}
