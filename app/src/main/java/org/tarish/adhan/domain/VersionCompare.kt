package org.tarish.adhan.domain

/** §5.4 semantics: first 3 numeric parts, missing/non-numeric = 0, suffix after '-'/'+' ignored. */
object VersionCompare {
    fun parse(v: String): List<Int> {
        val core = v.trim().substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        return (0 until 3).map { i -> parts.getOrNull(i)?.toIntOrNull() ?: 0 }
    }

    /** true when [a] is strictly greater than [b]. */
    fun isHigher(a: String, b: String): Boolean {
        val pa = parse(a); val pb = parse(b)
        for (i in 0 until 3) {
            if (pa[i] != pb[i]) return pa[i] > pb[i]
        }
        return false
    }
}
