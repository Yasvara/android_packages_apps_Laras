/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.data

import androidx.annotation.StringRes
import org.json.JSONException
import org.json.JSONObject

/** The settings table a preference lives in. */
enum class Namespace { SYSTEM, SECURE, GLOBAL, LINEAGE_SYSTEM, LINEAGE_SECURE }

/**
 * A typed system setting.
 *
 * Settings providers store every value as a string, so a preference only has to map between
 * that string and a Kotlin value; a missing or unreadable string reads as [default].
 */
sealed class Pref<T>(val namespace: Namespace, val key: String, val default: T) {
    abstract fun decode(raw: String?): T

    /** The string to store, or null to delete the setting so its owner's default applies. */
    abstract fun encode(value: T): String?

    override fun toString() = "$namespace/$key"
}

/** A preference its owner stores as an int. */
sealed class IntPref<T>(namespace: Namespace, key: String, default: T) :
    Pref<T>(namespace, key, default) {
    abstract fun fromInt(raw: Int): T

    abstract fun toInt(value: T): Int

    final override fun decode(raw: String?) = raw?.trim()?.toIntOrNull()?.let(::fromInt) ?: default

    final override fun encode(value: T) = toInt(value).toString()
}

class SwitchPref(namespace: Namespace, key: String, default: Boolean) :
    IntPref<Boolean>(namespace, key, default) {
    override fun fromInt(raw: Int) = raw != 0

    override fun toInt(value: Boolean) = if (value) 1 else 0
}

/** One option of a [ChoicePref]; [value] is the raw int the setting owner expects. */
interface ChoiceOption {
    val value: Int
    @get:StringRes val label: Int
}

/** A preference chosen from a fixed list of options. */
interface OptionsPref {
    val choices: List<ChoiceOption>
}

class ChoicePref<E>(namespace: Namespace, key: String, val options: List<E>, default: E) :
    IntPref<E>(namespace, key, default), OptionsPref where E : Enum<E>, E : ChoiceOption {
    override val choices: List<ChoiceOption> get() = options

    override fun fromInt(raw: Int) = options.firstOrNull { it.value == raw } ?: default

    override fun toInt(value: E) = value.value
}

/**
 * One field of a JSON object setting that several features share, like `yasvara_theme`
 * (SPEC-0003 §5.2). It reads its own field, and is written by merging into the object as stored
 * ([merge]), so the other fields keep their values. The default removes the field; once no field
 * is left the setting is deleted, so its owners treat it as unused.
 */
class JsonFieldPref<E>(
    namespace: Namespace,
    key: String,
    val field: String,
    val options: List<E>,
    default: E,
    private val id: (E) -> String,
) : Pref<E>(namespace, key, default), OptionsPref where E : Enum<E>, E : ChoiceOption {
    override val choices: List<ChoiceOption> get() = options

    override fun decode(raw: String?): E {
        val stored = raw?.let { runCatching { JSONObject(it).optString(field) }.getOrNull() }
        return options.firstOrNull { id(it) == stored } ?: default
    }

    /** The value alone, as if nothing else were stored; writers use [merge]. */
    override fun encode(value: E) = merge(null, value)

    /** [stored] with this field set to [value]. An unreadable object is replaced. */
    @Suppress("UNCHECKED_CAST")
    fun merge(stored: String?, value: Any?): String? {
        val option = value as E
        return mergeJsonFields(stored, mapOf(field to if (option == default) null else id(option)))
    }
}

/**
 * [stored], a shared JSON object setting (SPEC-0003 §5.2), with [fields] set; a null value
 * removes its field. An unreadable object is replaced; once no field is left the result is null,
 * so the setting is deleted and its owners treat it as unused.
 */
fun mergeJsonFields(stored: String?, fields: Map<String, String?>): String? {
    val json = stored?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
    fields.forEach { (field, value) -> if (value == null) json.remove(field) else json.put(field, value) }
    json.remove(JSON_VERSION)
    if (json.length() == 0) return null
    return JSONObject().put(JSON_VERSION, 1).also { out ->
        json.keys().forEach { out.put(it, json.get(it)) }
    }.toString()
}

private const val JSON_VERSION = "v"

/** A whole number chosen on a slider, stored as an int and kept within [range]. */
class IntSliderPref(namespace: Namespace, key: String, default: Int, val range: IntRange) :
    IntPref<Int>(namespace, key, default) {
    override fun fromInt(raw: Int) = raw.coerceIn(range)

    override fun toInt(value: Int) = value.coerceIn(range)
}

/** A number chosen on a slider, kept within [range]; [steps] as for a Material slider. */
class FloatPref(
    namespace: Namespace,
    key: String,
    default: Float,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int = 0,
) : Pref<Float>(namespace, key, default) {
    override fun decode(raw: String?) =
        raw?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(range) ?: default

    override fun encode(value: Float) = value.coerceIn(range).toString()
}

/** A value stored as text, read and written unchanged; null means the setting is not set. */
class StringPref(namespace: Namespace, key: String) : Pref<String?>(namespace, key, null) {
    override fun decode(raw: String?) = raw

    override fun encode(value: String?) = value
}

/**
 * A structured value stored as JSON text, as SystemUI's clock face and the Yasvara lock screen
 * settings are. [read] must not throw on well-formed JSON of an unexpected shape: return null
 * and the preference reads as [default], so a value written by a newer or older build never
 * breaks Laras.
 */
class JsonPref<T>(
    namespace: Namespace,
    key: String,
    default: T,
    private val read: (JSONObject) -> T?,
    private val write: (T) -> JSONObject?,
) : Pref<T>(namespace, key, default) {
    override fun decode(raw: String?): T {
        if (raw.isNullOrBlank()) return default
        return try {
            read(JSONObject(raw)) ?: default
        } catch (e: JSONException) {
            default
        }
    }

    override fun encode(value: T) = write(value)?.toString()
}

/** An immutable read of a set of preferences. */
class PrefSnapshot(private val values: Map<Pref<*>, Any?>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(pref: Pref<T>): T =
        if (pref in values) values[pref] as T else pref.default

    fun isChanged(pref: Pref<*>) = get(pref) != pref.default

    fun <T> with(pref: Pref<T>, value: T) = PrefSnapshot(values + (pref to value))
}

/** Changes preferences on behalf of the UI. */
interface PrefEditor {
    fun <T> set(pref: Pref<T>, value: T)

    fun reset(pref: Pref<*>)
}

/**
 * Writes [value] to a preference whose type is only known at runtime. Callers pass a value read
 * from the same preference's options, so the unchecked cast always holds.
 */
@Suppress("UNCHECKED_CAST")
fun PrefEditor.setAny(pref: Pref<*>, value: Any?) = set(pref as Pref<Any?>, value)
