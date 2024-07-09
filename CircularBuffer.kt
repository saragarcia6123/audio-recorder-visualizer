class CircularBuffer<T>(private val size: Int) {

    private var elements: Array<Any?> = arrayOfNulls(size)
    private var head: Int = 0
    private var tail: Int = 0
    private var count: Int = 0

    val isFull: Boolean
        get() = count == size

    val isEmpty: Boolean
        get() = count == 0

    fun add(element: T) {
        elements[tail] = element
        tail = (tail + 1) % size
        if (isFull) {
            head = (head + 1) % size
        } else {
            count++
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun remove(): T? {
        if (isEmpty) return null
        val element = elements[head]
        head = (head + 1) % size
        count--
        return element as T
    }

    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> {
        return (0 until count).map { index ->
            elements[(head + index) % size] as T
        }
    }

    fun first(): Any? {
        if (elements.isEmpty())
            throw NoSuchElementException("CircularBuffer is empty.")
        return elements[0]
    }

}