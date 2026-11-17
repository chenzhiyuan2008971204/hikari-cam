package com.hikari.engine.gl

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import java.io.FileDescriptor
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig as JConfig
import javax.microedition.khronos.egl.EGLDisplay as JDisplay
import android.opengl.GLSurfaceView

/** Size of a recorded video for a frame aspect (width / height): [shortSide] on the short edge, both sides even. */
object VideoSize {
    fun forAspect(aspect: Float, shortSide: Int = 1080): Pair<Int, Int> {
        val a = aspect.coerceIn(0.4f, 2.5f)
        fun even(v: Float) = (Math.round(v) / 2) * 2
        return if (a <= 1f) even(shortSide.toFloat()) to even(shortSide / a) else even(shortSide * a) to even(shortSide.toFloat())
    }
}

/**
 * Chooses an EGL config the video encoder's surface can share: RGB 8-8-8 and flagged recordable (EGL_RECORDABLE_ANDROID),
 * falling back to the plain RGB 8-8-8 config GLSurfaceView would pick itself.
 */
class RecordableConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: JDisplay): JConfig {
        for (recordable in listOf(true, false)) {
            val a = ArrayList<Int>().apply {
                addAll(listOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 0,
                    0x3040 /* EGL_RENDERABLE_TYPE */, 0x40 /* ES3 */))
                if (recordable) addAll(listOf(0x3142 /* EGL_RECORDABLE_ANDROID */, 1))
                add(EGL10.EGL_NONE)
            }.toIntArray()
            val n = IntArray(1)
            if (!egl.eglChooseConfig(display, a, null, 0, n) || n[0] <= 0) continue
            val out = arrayOfNulls<JConfig>(n[0])
            egl.eglChooseConfig(display, a, out, n[0], n)
            // prefer a config without alpha or depth the surface does not need
            return out.filterNotNull().firstOrNull() ?: continue
        }
        val a = intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, 0x3040, 0x40, EGL10.EGL_NONE)
        val n = IntArray(1)
        val out = arrayOfNulls<JConfig>(1)
        check(egl.eglChooseConfig(display, a, out, 1, n) && n[0] > 0) { "no EGL config" }
        return out[0]!!
    }
}

/**
 * Records the film-processed picture as an H.264 / AAC mp4. The renderer draws every preview frame a second time, with
 * the same look, into [inputSurface] (so the video carries the film look, grain and all, exactly like the preview);
 * this class owns the encoders, the muxer and the threads that drain them.
 *
 * Time stamps: video frames carry the draw time, the microphone the time its samples were read, both counted from
 * [startNs], so picture and sound stay together.
 */
class FilmRecorder(
    val width: Int,
    val height: Int,
    private val out: FileDescriptor,
    audio: Boolean,
    private val fps: Int = 30,
) {
    private val video: MediaCodec = MediaCodec.createEncoderByType("video/avc")
    private var audioCodec: MediaCodec? = null
    private var mic: AudioRecord? = null
    private val muxer = MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val inputSurface: Surface
    val aspect get() = width.toFloat() / height

    /** true when the microphone is part of this recording */
    var hasAudio = false
        private set

    @Volatile var startNs = 0L
        private set
    @Volatile var framesDrawn = 0
    @Volatile var failure: Throwable? = null
        private set

    private val lock = Object()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxStarted = false
    private var audioDropped = false
    private var expected = 1
    @Volatile private var stopRequested = false
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null
    private var eglSurface: EGLSurface? = null

    init {
        val f = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 0.12 * fps).toInt().coerceIn(2_000_000, 24_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        video.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = video.createInputSurface()
        if (audio) hasAudio = setUpAudio()
        expected = if (hasAudio) 2 else 1
    }

    private fun setUpAudio(): Boolean = try {
        val rate = 44100
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 16384))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); false }
        else {
            val c = MediaCodec.createEncoderByType("audio/mp4a-latm")
            c.configure(MediaFormat.createAudioFormat("audio/mp4a-latm", rate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mic = rec; audioCodec = c
            true
        }
    } catch (e: Exception) { false }   // no permission, no microphone: the video is recorded without sound

    fun start() {
        startNs = System.nanoTime()
        video.start()
        videoThread = Thread({ drain(video, true) }, "FilmRecorder-video").also { it.start() }
        val c = audioCodec
        val m = mic
        if (c != null && m != null) {
            c.start(); m.startRecording()
            audioThread = Thread({ audioLoop(c, m) }, "FilmRecorder-audio").also { it.start() }
        }
    }

    /** Microseconds-free draw time for the next video frame, nanoseconds from the start of the recording. */
    fun nextPts(): Long = System.nanoTime() - startNs

    /** GL thread: the EGL surface wrapped around the encoder's input, created on first use with the context's own config. */
    fun eglSurface(dpy: EGLDisplay, ctx: EGLContext): EGLSurface? {
        eglSurface?.let { return it }
        val id = IntArray(1)
        EGL14.eglQueryContext(dpy, ctx, EGL14.EGL_CONFIG_ID, id, 0)
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        if (!EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_CONFIG_ID, id[0], EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0) || n[0] == 0) {
            failure = failure ?: IllegalStateException("no EGL config for the video surface"); return null
        }
        val s = EGL14.eglCreateWindowSurface(dpy, cfgs[0], inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
        if (s == null || s == EGL14.EGL_NO_SURFACE) {
            failure = failure ?: IllegalStateException("video surface refused: 0x" + Integer.toHexString(EGL14.eglGetError())); return null
        }
        eglSurface = s
        return s
    }

    /** GL thread: let go of the EGL surface (call before [finish]). */
    fun releaseEgl(dpy: EGLDisplay) {
        eglSurface?.let { EGL14.eglDestroySurface(dpy, it) }
        eglSurface = null
    }

    private fun addTrack(fmt: MediaFormat, isVideo: Boolean) = synchronized(lock) {
        val t = muxer.addTrack(fmt)
        if (isVideo) videoTrack = t else audioTrack = t
        if (videoTrack >= 0 && (audioTrack >= 0 || expected == 1)) { muxer.start(); muxStarted = true; lock.notifyAll() }
    }

    /** Waits until the muxer runs; if the microphone never delivers, goes on with the picture alone. */
    private fun awaitMuxer(): Boolean = synchronized(lock) {
        val end = System.currentTimeMillis() + 4000
        while (!muxStarted && System.currentTimeMillis() < end) lock.wait(200)
        if (!muxStarted && videoTrack >= 0) { audioDropped = true; muxer.start(); muxStarted = true }
        muxStarted
    }

    private fun drain(codec: MediaCodec, isVideo: Boolean) {
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                val i = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (!isVideo && stopRequested && audioDone) return }
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> addTrack(codec.outputFormat, isVideo)
                    i >= 0 -> {
                        val buf = codec.getOutputBuffer(i)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && buf != null && awaitMuxer() && !(!isVideo && audioDropped)) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            synchronized(lock) { muxer.writeSampleData(if (isVideo) videoTrack else audioTrack, buf, info) }
                        }
                        codec.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        } catch (e: Throwable) { if (isVideo) failure = failure ?: e else audioDropped = true }   // a sound problem must not lose the picture
    }

    @Volatile private var audioDone = false

    private fun audioLoop(c: MediaCodec, m: AudioRecord) {
        val bytes = ByteArray(2048)
        val drainer = Thread({ drain(c, false) }, "FilmRecorder-audio-out").also { it.start() }
        try {
            while (!stopRequested) {
                val n = m.read(bytes, 0, bytes.size)
                if (n <= 0) continue
                val idx = c.dequeueInputBuffer(10_000)
                if (idx < 0) continue
                val ib = c.getInputBuffer(idx)!!
                ib.clear(); ib.put(bytes, 0, n)
                c.queueInputBuffer(idx, 0, n, (System.nanoTime() - startNs) / 1000, 0)
            }
            var idx = -1
            var tries = 0
            while (idx < 0 && tries++ < 100) idx = c.dequeueInputBuffer(20_000)
            if (idx >= 0) c.queueInputBuffer(idx, 0, 0, (System.nanoTime() - startNs) / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            audioDone = true
            drainer.join(5000)
        } catch (e: Throwable) { audioDropped = true; audioDone = true }
    }

    /**
     * Ends the recording and finishes the file. Blocking: call off the main thread, after [releaseEgl].
     * @return length in milliseconds, 0 if nothing was recorded
     */
    fun finish(): Long {
        stopRequested = true
        try { video.signalEndOfInputStream() } catch (e: Throwable) { failure = failure ?: e }
        videoThread?.join(6000)
        audioThread?.join(6000)
        val ms = (System.nanoTime() - startNs) / 1_000_000
        try { mic?.stop() } catch (_: Throwable) {}
        try { if (muxStarted) muxer.stop() } catch (e: Throwable) { failure = failure ?: e }
        try { muxer.release() } catch (_: Throwable) {}
        try { video.stop() } catch (_: Throwable) {}
        video.release()
        inputSurface.release()
        try { audioCodec?.stop() } catch (_: Throwable) {}
        audioCodec?.release()
        mic?.release()
        return if (muxStarted && framesDrawn > 0) ms else 0L
    }
}
