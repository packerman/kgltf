package kgltf.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

private val jsonParser = JsonParser()

val gson = Gson()

inline fun <reified T> fromJson(jsonElement: JsonElement): T = gson.fromJson(jsonElement, T::class.java)

fun <T> fromJsonByToken(jsonElement: JsonElement): T = gson.fromJson(jsonElement, object : TypeToken<T>() {}.type)

fun parseJsonObject(string: String): JsonObject = jsonParser.parse(string).asJsonObject

inline fun <T> JsonObject.map(member: String, f: (JsonElement) -> T): List<T> = getAsJsonArray(member).map(f)
inline fun <T> JsonObject.mapIndexed(member: String, f: (Int, JsonElement) -> T): List<T> = getAsJsonArray(member).mapIndexed(f)
inline fun JsonObject.forEachIndexed(member: String, f: (Int, JsonElement) -> Unit) = getAsJsonArray(member).forEachIndexed(f)
inline fun JsonObject.forEachObjectIndexed(member: String, f: (Int, JsonObject) -> Unit): Unit = getAsJsonArray(member)?.forEachIndexed { i, e -> f(i, e.asJsonObject) } ?: Unit

fun JsonObject.count(member: String): Int = getAsJsonArray(member)?.size() ?: 0

fun JsonObject.getAsInt(key: String): Int = getAsJsonPrimitive(key).asInt
fun JsonObject.getAsString(key: String): String = getAsJsonPrimitive(key).asString

fun JsonElement.count(member: String): Int = this.asJsonObject.count(member)
