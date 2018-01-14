package kgltf.util

inline fun <K, V> Iterable<K>.buildMap(f: (K) -> V): Map<K, V> = HashMap<K, V>().apply {
    for (k in this@buildMap) {
        set(k, f(k))
    }
}.toMap()

fun <K, V> buildMap(build: HashMap<K,V>.() -> Unit): Map<K, V> {
    val map = HashMap<K, V>()
    map.build()
    return map
}

fun <T> buildList(build: ArrayList<T>.() -> Unit): List<T> {
    val list = ArrayList<T>()
    list.build()
    return list
}

fun List<Int>.sums(): List<Int> {
    val list = ArrayList<Int>(this.size + 1)
    var s = 0
    for (e in this) {
        list.add(s)
        s += e
    }
    list.add(s)
    return list
}

inline fun <T> Sequence<T>.firstOrDefault(defaultValue: T, predicate: (T) -> Boolean): T {
    for (element in this) if (predicate(element)) return element
    return defaultValue
}
