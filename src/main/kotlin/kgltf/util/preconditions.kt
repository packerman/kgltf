package kgltf.util

import java.util.logging.Logger

inline fun warnWhen(value: Boolean, lazyMessage: () -> Any): Unit {
    if (value) {
        val message = lazyMessage()
        Logger.getGlobal().warning(message.toString())
    }
}
