package ai.koalla.core.util

/**
 * Constant-time string comparison to prevent timing attacks on webhook secrets.
 * Returns false immediately if lengths differ (length itself is not sensitive).
 */
fun secureCompare(
    a: String,
    b: String,
): Boolean {
    if (a.length != b.length) return false
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].code xor b[i].code)
    }
    return result == 0
}
