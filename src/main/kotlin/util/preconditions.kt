package util

inline fun warn(value: Boolean, lazyMessage: () -> Any): Unit {
    if (!value) {
        val message = lazyMessage()
        System.err.println(message)
    }
}