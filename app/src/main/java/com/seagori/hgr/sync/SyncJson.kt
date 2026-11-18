package com.seagori.hgr.sync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

fun JsonObject.bool(key: String): Boolean? = string(key)?.toBooleanStrictOrNull()

fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.stringList(key: String): List<String> {
    val value = this[key] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return value.mapNotNull { (it as? JsonPrimitive)?.content }
}

fun syncPayload(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject(block)

fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Long?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Int?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Boolean?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}
