package util

fun <K, V> Iterable<K>.buildMap(f: (K) -> V): Map<K, V> = HashMap<K, V>().apply {
    for (k in this@buildMap) {
        set(k, f(k))
    }
}.toMap()
