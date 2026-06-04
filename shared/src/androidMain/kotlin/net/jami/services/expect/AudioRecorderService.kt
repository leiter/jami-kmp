package net.jami.services.expect

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

actual class AudioRecorderService(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentPath: String? = null

    actual val isRecording: Boolean get() = recorder != null

    actual fun startRecording(outputPath: String) {
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder!!.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)
            prepare()
            start()
        }
        currentPath = outputPath
    }

    actual fun stopRecording(): String? {
        recorder?.apply { stop(); release() }
        recorder = null
        return currentPath.also { currentPath = null }
    }

    actual fun cancelRecording() {
        recorder?.apply { stop(); release() }
        recorder = null
        currentPath?.let { File(it).delete() }
        currentPath = null
    }
}
