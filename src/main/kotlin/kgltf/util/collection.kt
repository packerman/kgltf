package kgltf.util

inline fun <K, V> Iterable<K>.buildMap(f: (K) -> V): Map<K, V> = HashMap<K, V>().apply {
    for (k in this@buildMap) {
        set(k, f(k))
    }
}.toMap()

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
