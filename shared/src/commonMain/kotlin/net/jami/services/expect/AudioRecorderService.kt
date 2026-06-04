package net.jami.services.expect

expect class AudioRecorderService {
    fun startRecording(outputPath: String)
    fun stopRecording(): String?
    fun cancelRecording()
    val isRecording: Boolean
}
