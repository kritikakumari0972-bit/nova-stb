package com.nova.stb.data

/** Keeps remote file operations inside Android shared storage for the first release. */
object RemotePath {
    const val ROOT = "/sdcard"

    fun normalize(raw: String): String {
        require(raw.isNotBlank()) { "Remote path is empty." }
        require(raw.startsWith("/")) { "Remote paths must be absolute." }
        val parts = mutableListOf<String>()
        raw.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> {
                    require(parts.isNotEmpty()) { "Path escapes shared storage." }
                    parts.removeAt(parts.lastIndex)
                }
                else -> {
                    require(part.none { it == '\n' || it == '\r' || it == '\u0000' }) {
                        "Path contains an unsupported control character."
                    }
                    parts += part
                }
            }
        }
        val normalized = "/" + parts.joinToString("/")
        require(normalized == ROOT || normalized.startsWith("$ROOT/")) {
            "NovaSTB currently browses shared storage only ($ROOT)."
        }
        return normalized
    }

    fun child(parent: String, name: String): String {
        val safeParent = normalize(parent)
        require(name.isNotBlank() && name != "." && name != "..") { "Invalid file name." }
        require('/' !in name && '\n' !in name && '\r' !in name && '\u0000' !in name) {
            "File name contains an unsupported character."
        }
        return normalize("$safeParent/$name")
    }

    fun parent(path: String): String {
        val safe = normalize(path)
        if (safe == ROOT) return ROOT
        val candidate = safe.substringBeforeLast('/', ROOT)
        return if (candidate.length < ROOT.length) ROOT else normalize(candidate)
    }

    fun isInsideSharedStorage(path: String): Boolean =
        runCatching { normalize(path) }.isSuccess
}
