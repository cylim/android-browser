package app.jrny.browser.tab

/**
 * What a page's file field accepts, read from its `accept` list: MIME types such as `image/png`
 * (or every image type), and suffixes such as `.pdf` (SPEC, "File upload"). Plain Kotlin, so it
 * is unit-tested on the JVM; [mimeOf] gives the MIME type of a suffix without its dot.
 */
class FileTypes(private val accept: List<String>, private val mimeOf: (String) -> String?) {

    /** The MIME types to show in the file picker. Empty means any file. */
    val mimeTypes: List<String> = run {
        val types = accept.map { entry ->
            when {
                entry.startsWith(".") -> mimeOf(entry.removePrefix("."))
                entry.contains('/') -> entry
                else -> null
            }
        }
        // A suffix nothing knows, or an explicit "any", leaves the choice open.
        if (types.isEmpty() || null in types || "*/*" in types) emptyList() else types.filterNotNull().distinct()
    }

    /** True when the field takes photos: an image type, or any file. */
    val takesImages: Boolean get() = takes("image")

    /** True when the field takes videos: a video type, or any file. */
    val takesVideos: Boolean get() = takes("video")

    private fun takes(kind: String): Boolean = mimeTypes.isEmpty() || mimeTypes.any { it.substringBefore('/') == kind }
}
