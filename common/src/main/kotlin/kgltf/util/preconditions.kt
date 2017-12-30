package kgltf.util

import java.util.logging.Logger

inline fun warnWhen(value: Boolean, lazyMessage: () -> Any): Unit {
    if (value) {
        val message = lazyMessage()
        Logger.getGlobal().warning(message.toString())
    }
}

inline fun <T, R> ifLet(value: T, ifBlock: (T) -> R, elseBlock: () -> R): R =
        value?.let(ifBlock) ?: elseBlock()
