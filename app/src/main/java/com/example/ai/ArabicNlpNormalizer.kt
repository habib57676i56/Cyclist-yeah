package com.example.ai

object ArabicNlpNormalizer {
    private val DIACRITICS_REGEX = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val PUNCT_REGEX = Regex("[^\\p{L}\\p{N}\\s]")

    private val STOP_WORDS = setOf(
        "في", "من", "على", "الى", "عن", "مع", "هل", "ما", "ماذا", "كيف", "لماذا",
        "او", "ثم", "ان", "أن", "إن", "لا", "لم", "لن", "قد", "كان", "هذا", "هذه",
        "ذلك", "تلك", "التي", "الذي", "هو", "هي", "هم", "انا", "نحن", "بين", "عند",
        "the", "is", "are", "what", "how", "why", "in", "of", "to", "and", "or", "a", "an"
    )

    fun stripDiacritics(input: String): String =
        input.replace(DIACRITICS_REGEX, "")

    fun normalizeArabicText(input: String): String {
        return stripDiacritics(input)
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace(PUNCT_REGEX, " ")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun normalizeQuranWord(word: String): String {
        return stripDiacritics(word)
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace(Regex("[\\u06DD\\u06DE\\u06E9﴿﴾0-9٠-٩\\p{P}]"), "")
            .trim()
    }

    fun lightStem(token: String): String {
        var t = token
        val prefixes = listOf("وال", "بال", "كال", "فال", "لل", "ال")
        for (p in prefixes) {
            if (t.length > p.length + 2 && t.startsWith(p)) {
                t = t.removePrefix(p)
                break
            }
        }
        val suffixes = listOf("ون", "ين", "ات", "ها", "هم", "كم", "نا")
        for (s in suffixes) {
            if (t.length > s.length + 2 && t.endsWith(s)) {
                t = t.removeSuffix(s)
                break
            }
        }
        return t
    }

    fun normalizeAndTokenize(input: String): List<String> {
        val norm = normalizeArabicText(input)
        if (norm.isBlank()) return emptyList()
        return norm.split(" ")
            .filter { it.length > 1 && it !in STOP_WORDS }
            .map { lightStem(it) }
    }
}
