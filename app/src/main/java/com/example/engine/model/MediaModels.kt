package com.example.engine.model

data class MediaFormatOption(
    val formatId: String,
    val formatNote: String,
    val resolution: String,
    val ext: String,
    val approximateSizeBytes: Long = 0L,
    val isAudioOnly: Boolean = false,
    val directUrl: String
) {
    val displaySize: String
        get() = when {
            approximateSizeBytes <= 0 -> "Tamaño variable"
            approximateSizeBytes < 1024 * 1024 -> String.format("%.1f KB", approximateSizeBytes / 1024.0)
            approximateSizeBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", approximateSizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", approximateSizeBytes / (1024.0 * 1024.0 * 1024.0))
        }

    val displayLabel: String
        get() = if (isAudioOnly) {
            "Audio ($ext) • $formatNote • $displaySize"
        } else {
            "Video $resolution ($ext) • $displaySize"
        }
}

data class MediaExtractionResult(
    val title: String,
    val webpageUrl: String,
    val thumbnail: String? = null,
    val author: String? = null,
    val durationSeconds: Long = 0L,
    val formats: List<MediaFormatOption> = emptyList()
)
