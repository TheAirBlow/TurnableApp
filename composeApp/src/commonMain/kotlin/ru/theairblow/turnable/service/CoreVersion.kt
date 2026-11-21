package ru.theairblow.turnable.service

/** Oldest Turnable release whose service speaks the protocol this app needs; a custom core binary skips the check. */
const val MIN_CORE_VERSION = "v0.8.4"

/** The release the on-device service downloads when none is installed. */
const val DEFAULT_CORE_VERSION = MIN_CORE_VERSION

/** Numeric parts of a release tag like `v0.10.2`, padded to three. */
fun coreVersionParts(tag: String): List<Int> =
    tag.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }.let { it + List(3 - it.size.coerceAtMost(3)) { 0 } }.take(3)

fun compareCoreVersions(a: String, b: String): Int {
    val x = coreVersionParts(a)
    val y = coreVersionParts(b)
    return x.indices.firstNotNullOfOrNull { i -> (x[i] compareTo y[i]).takeIf { it != 0 } } ?: 0
}

fun isSupportedCore(tag: String): Boolean = compareCoreVersions(tag, MIN_CORE_VERSION) >= 0
