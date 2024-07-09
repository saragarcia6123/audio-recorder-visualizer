class RepositoryLocalAudioRecorder {

    val numberOfMagnitudes = 8

    val magnitudes: MutableState<FloatArray> = mutableStateOf(FloatArray(numberOfMagnitudes) { 0f })

    fun postMagnitudes(newMagnitudes: FloatArray) {
        magnitudes.value = newMagnitudes
    }

    fun reset() {
        magnitudes.value = FloatArray(numberOfMagnitudes) { 0f }
    }

}