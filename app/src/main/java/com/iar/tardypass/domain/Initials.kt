package com.iar.tardypass.domain

/**
 * Initials for a receipt line and the staff picker badge.
 *
 * First and last token rather than the first two, because "Jane Q. Doe" should
 * read JD, not JQ — the middle initial is the least useful letter to a person
 * scanning a printed pass.
 */
fun initialsFrom(displayName: String): String {
    val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when (parts.size) {
        0 -> ""
        1 -> parts[0].first().uppercase()
        else -> parts.first().first().uppercase() + parts.last().first().uppercase()
    }
}
