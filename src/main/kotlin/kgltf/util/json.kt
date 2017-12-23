package kgltf.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

private val jsonParser = JsonParser()

val gson = Gson()

inline fun <reified T> fromJson(jsonElement: JsonElement): T = gson.fromJson(jsonElement, T::class.java)

fun <T> fromJsonByToken(jsonElement: JsonElement): T = gson.fromJson(jsonElement, object : TypeToken<T>() {}.type)

fun parseJson(string: String): JsonElement = jsonParser.parse(string)
