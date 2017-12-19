package kgltf.util

import java.io.File

fun File.splitExt(): Pair<String, String> {
    val index = name.lastIndexOf('.')
    return if (index == -1 || index == 0) Pair(name, "")
    else Pair(name.substring(0, index), name.substring(index))
}
