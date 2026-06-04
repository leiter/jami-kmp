package net.jami.services.expect

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSError
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual class AudioRecorderService {
    private var audioRecorder: AVAudioRecorder? = null
    private var currentPath: String? = null

    actual val isRecording: Boolean get() = audioRecorder?.recording == true

    actual fun startRecording(outputPath: String) {
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryRecord, error = null)
            setActive(true, error = null)
        }
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to 44100.0,
            AVNumberOfChannelsKey to 1,
        )
        val url = NSURL.fileURLWithPath(outputPath)
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            audioRecorder = AVAudioRecorder(url, settings, err.ptr)
        }
        audioRecorder?.record()
        currentPath = outputPath
    }

    actual fun stopRecording(): String? {
        audioRecorder?.stop()
        audioRecorder = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
        return currentPath.also { currentPath = null }
    }

    actual fun cancelRecording() {
        audioRecorder?.stop()
        audioRecorder?.deleteRecording()
        audioRecorder = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
        currentPath = null
    }
}
