private const val TAG = "TaskAudioRecorder"

interface TaskAudioRecorderInterface {

    var currentVolume: Float
    var durationMillis: Long

    fun startRecording(
        outputFile: File,
        repositoryLocalAudioRecorder: RepositoryLocalAudioRecorder,
        launchInBackground: (suspend CoroutineScope.() -> Unit) -> Job
    ): Boolean
    fun stopRecording()

}

private const val SAMPLE_RATE = 44100

class TaskAudioRecorder(
    private val context: Context,
    override var currentVolume: Float = 0f,
    override var durationMillis: Long = 0L
): TaskAudioRecorderInterface {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private lateinit var outputFile: File

    private var startTimeMillis: Long = 0

    override fun startRecording(
        outputFile: File,
        repositoryLocalAudioRecorder: RepositoryLocalAudioRecorder,
        launchInBackground: (suspend CoroutineScope.() -> Unit) -> Job
    ): Boolean {
        this.outputFile = outputFile

        stopRecording()

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, channelConfig, audioFormat, bufferSizeInBytes)

        if (NoiseSuppressor.isAvailable()) {
            val noiseSuppressor = NoiseSuppressor.create(audioRecord!!.audioSessionId)
            if (noiseSuppressor != null) {
                noiseSuppressor.enabled = true
            } else {
                Log.w("$TAG:StartRecording", "Failed to create NoiseSuppressor.")
            }
        } else {
            Log.w("$TAG:StartRecording", "NoiseSuppressor not available on this device.")
        }

        audioRecord?.startRecording()
        isRecording = true
        startTimeMillis = System.currentTimeMillis()
        durationMillis = 0L

        recordingThread = Thread({update(launchInBackground, repositoryLocalAudioRecorder, bufferSizeInBytes) }, "AudioRecorder Thread")

        try {
            recordingThread?.start()
        } catch (e: IllegalThreadStateException) {
            Log.w("$TAG:StartRecording", "Failed to start recording: Recording Thread was already started")
        }

        return true
    }

    override fun stopRecording() {
        if (audioRecord != null) {

            isRecording = false
            recordingThread?.interrupt()
            durationMillis = 0L

            try {
                recordingThread?.join(1000)
                if (recordingThread?.isAlive == true) {
                    Log.w("$TAG:StopRecording", "Failed to stop recording: Recording Thread did not terminate in time")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w("$TAG:StopRecording", "Recording Thread was interrupted")
            }

            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            } catch (e: IllegalStateException) {
                Log.w("$TAG:StopRecording", "Failed to stop recording: AudioRecord could not be terminated")
            }

        } else {
            Log.w("$TAG:StopRecording", "Failed to stop recording: AudioRecord is null")
        }
    }

    private fun update(
        launchInBackground: (suspend CoroutineScope.() -> Unit) -> Job,
        repositoryLocalAudioRecorder: RepositoryLocalAudioRecorder,
        bufferSizeInBytes: Int
    ) {

        val shortData = ShortArray(bufferSizeInBytes / 2)
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(outputFile)
        } catch (e: IOException) {
            Log.w("$TAG:Update", "Failed to write to Output File")
        }

        val bufferMagnitudes = CircularBuffer<FloatArray>(5)

        while (isRecording) {

            val currentTimeMillis = System.currentTimeMillis()

            val readSize = audioRecord?.read(shortData, 0, shortData.size) ?: 0

            currentVolume = calculateVolume(shortData, readSize)
            val minDecibels = 50 //decibels to register enough sound to consider not background noise

            if (currentVolume > minDecibels) {
                startTimeMillis = currentTimeMillis
            }

            durationMillis = currentTimeMillis - startTimeMillis

            if (readSize > 0) {

                var magnitudes = if (currentVolume > minDecibels) {
                    processMagnitudes(shortData, readSize, repositoryLocalAudioRecorder)
                } else {
                    FloatArray(repositoryLocalAudioRecorder.numberOfMagnitudes) {0f}
                }
                bufferMagnitudes.add(magnitudes)
                magnitudes = calculateAverageMagnitudes(bufferMagnitudes)
                magnitudes = gaussianWeightedDistribution(magnitudes)
                magnitudes = normalizeMagnitudes(magnitudes)
                magnitudes = smoothMagnitudes(magnitudes, repositoryLocalAudioRecorder.magnitudes.value)

                launchInBackground {
                    repositoryLocalAudioRecorder.postMagnitudes(magnitudes)
                }

                val data = shortArrayToByteArray(shortData)

                try {
                    fos?.write(data)
                } catch (e: IOException) {
                    fos?.close()
                    e.printStackTrace()
                }

            }

        }
        try {
            fos?.close()
        } catch (e: IOException) {
            Log.w("$TAG:Update", "Failed to close FileOutputStream")
            e.printStackTrace()
        }
        stopRecording()
    }

    private fun calculateAverageMagnitudes(bufferMagnitudes: CircularBuffer<FloatArray>): FloatArray {

        val magnitudesList = bufferMagnitudes.toList()

        if (magnitudesList.isEmpty()) return floatArrayOf()

        val listSize = magnitudesList.size
        val arraySize = magnitudesList.first().size

        val sums = FloatArray(arraySize)

        magnitudesList.forEach { array ->
            array.forEachIndexed { index, value ->
                sums[index] += value
            }
        }

        for (i in sums.indices) {
            sums[i] = sums[i] / listSize
        }

        return sums
    }

    private fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortArray)
        return byteArray
    }

    private fun processMagnitudes(shortData: ShortArray, readSize: Int, repositoryLocalAudioRecorder: RepositoryLocalAudioRecorder): FloatArray {

        val fftSize = calculateFFTSize(readSize)
        val floatData = performFFT(shortData, readSize, fftSize)
        val magnitudes = calculateMagnitudes(floatData, fftSize)
        val gatedMagnitudes = applyNoiseGate(magnitudes, threshold = 10.0)
        val numberOfMagnitudes = repositoryLocalAudioRecorder.numberOfMagnitudes
        val binnedMagnitudes = binMagnitudes(gatedMagnitudes, fftSize, numberOfMagnitudes)

        return binnedMagnitudes
    }

    private fun calculateFFTSize(readSize: Int): Int {
        return 2.0.pow(ceil(log2(readSize.toDouble()))).toInt()
    }

    private fun performFFT(shortData: ShortArray, readSize: Int, fftSize: Int): FloatArray {

        val floatData = FloatArray(fftSize) { i ->
            if (i < readSize) (shortData[i] * windowFunction(i, readSize)).toFloat() else 0f
        }

        val fft = FFT(fftSize)
        fft.forwardTransform(floatData)

        return floatData

    }

    private fun calculateMagnitudes(floatData: FloatArray, fftSize: Int): FloatArray {

        if (audioRecord == null) return FloatArray(10) { 0f }

        val halfSize = fftSize / 2
        val magnitudes = FloatArray(halfSize) { 0f }
        for (i in 0 until halfSize) {
            val real = floatData[2 * i]
            val imaginary = floatData[2 * i + 1]
            magnitudes[i] = sqrt(real * real + imaginary * imaginary)
        }

        return magnitudes
    }

    private fun windowFunction(index: Int, size: Int): Double {
        return 0.5 - 0.5 * cos(2.0 * Math.PI * index / (size - 1))
    }

    private fun binMagnitudes(magnitudes: FloatArray, fftSize: Int, numberOfMagnitudes: Int): FloatArray {

        val logMin = log10(400.0) //Frequency in hz
        val logMax = log10(800.0) //Frequency in hz
        val logRange = logMax - logMin
        val binnedMagnitudes = FloatArray(numberOfMagnitudes) { 0f }

        for (i in 0 until numberOfMagnitudes) {
            val logStart = logMin + i * (logRange / numberOfMagnitudes)
            val logEnd = logMin + (i + 1) * (logRange / numberOfMagnitudes)
            val startFreq = 10.0.pow(logStart).toInt()
            val endFreq = 10.0.pow(logEnd).toInt()
            val startBin = frequencyToBin(startFreq, fftSize)
            val endBin = frequencyToBin(endFreq, fftSize)

            var sum = 0f
            var count = 0
            for (j in startBin until endBin) {
                if (j < magnitudes.size) {
                    sum += magnitudes[j]
                    count++
                }
            }
            
            binnedMagnitudes[i] = if (count > 0) sum / count else 0f
        }
        return binnedMagnitudes
    }

    private fun frequencyToBin(frequency: Int, fftSize: Int): Int {
        return (frequency * fftSize / SAMPLE_RATE)
    }

    private fun gaussianWeightedDistribution(magnitudes: FloatArray): FloatArray {

        val size = magnitudes.size
        val midpoint = size / 2.0
        val spread = (size / 4.0) / 1.5

        val weights = FloatArray(size) { i ->
            exp(-((i - midpoint).pow(2) / (2 * spread.pow(2)))).toFloat()
        }

        // Normalize
        val weightSum = weights.sum()
        for (i in weights.indices) {
            weights[i] /= weightSum
        }

        return FloatArray(size) { i ->
            magnitudes[i] * weights[i] * currentVolume/2
        }
    }

    private fun applyNoiseGate(magnitudes: FloatArray, threshold: Double, marginFactor: Float = 1.2f): FloatArray {
        return magnitudes.map { magnitude ->
            if (magnitude > (getNoiseFloor(magnitudes, threshold) * marginFactor))
                magnitude
            else
                0f
        }.toFloatArray()
    }

    private fun getNoiseFloor(magnitudes: FloatArray, threshold: Double): Float {
        val currentNoiseLevel = magnitudes.average() * threshold
        return min(Double.MAX_VALUE, currentNoiseLevel).toFloat()
    }

    private fun normalizeMagnitudes(volumeData: FloatArray): FloatArray {

        if (volumeData.isEmpty()) return volumeData

        val minValue = volumeData.minOrNull() ?: 0f
        val maxValue = volumeData.maxOrNull() ?: 0f

        val range = maxValue - minValue

        return if (range == 0f) volumeData.map { 0f }.toFloatArray()

        else volumeData.map { (it - minValue) / range }.toFloatArray()
    }

    private fun smoothMagnitudes(currentMagnitudes: FloatArray, previousMagnitudes: FloatArray?): FloatArray {
        val smoothed = FloatArray(currentMagnitudes.size)
        val previous = previousMagnitudes ?: currentMagnitudes

        for (i in currentMagnitudes.indices) {
            smoothed[i] = (currentMagnitudes[i] + previous[i]) / 2f
        }

        return smoothed
    }

    private fun calculateVolume(buffer: ShortArray, readSize: Int): Float {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val amplitude = if (readSize > 0) (sum / readSize) else 0.0
        val decibels = 10 * log10(amplitude)
        return decibels.toFloat()
    }

}