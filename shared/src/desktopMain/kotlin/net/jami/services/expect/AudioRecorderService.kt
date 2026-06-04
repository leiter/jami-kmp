package net.jami.services.expect

actual class AudioRecorderService {
    actual val isRecording: Boolean = false
    actual fun startRecording(outputPath: String) {}
    actual fun stopRecording(): String? = null
    actual fun cancelRecording() {}
}
