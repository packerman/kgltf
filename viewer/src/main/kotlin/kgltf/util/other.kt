package kgltf.util

fun <T> T.withAction(action: T.() -> Unit): T {
    action()
    return this
}
