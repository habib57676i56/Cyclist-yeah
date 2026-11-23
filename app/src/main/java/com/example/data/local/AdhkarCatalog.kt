package com.example.data.local

data class DhikrItem(
    val id: String,
    val category: String, // MORNING, EVENING, AFTER_PRAYER, SLEEP
    val arabicText: String,
    val translationEn: String,
    val reference: String,
    val targetCount: Int
)

object AdhkarCatalog {
    val items: List<DhikrItem> = listOf(
        // Morning Adhkar
        DhikrItem(
            id = "m_1",
            category = "MORNING",
            arabicText = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
            translationEn = "We have reached the morning and at this very time all sovereignty belongs to Allah...",
            reference = "Sahih Muslim 2723",
            targetCount = 1
        ),
        DhikrItem(
            id = "m_2",
            category = "MORNING",
            arabicText = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
            translationEn = "In the name of Allah with whose name nothing is harmed on earth nor in heaven...",
            reference = "Sunan Abi Dawud 5088 / At-Tirmidhi 3388",
            targetCount = 3
        ),
        DhikrItem(
            id = "m_3",
            category = "MORNING",
            arabicText = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            translationEn = "Glory and praise be to Allah.",
            reference = "Sahih Muslim 2692",
            targetCount = 100
        ),
        // Evening Adhkar
        DhikrItem(
            id = "e_1",
            category = "EVENING",
            arabicText = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ",
            translationEn = "We have reached the evening and at this very time all sovereignty belongs to Allah...",
            reference = "Sahih Muslim 2723",
            targetCount = 1
        ),
        DhikrItem(
            id = "e_2",
            category = "EVENING",
            arabicText = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
            translationEn = "I seek refuge in the perfect words of Allah from the evil of what He has created.",
            reference = "Sahih Muslim 2709",
            targetCount = 3
        ),
        // After Prayer Adhkar
        DhikrItem(
            id = "p_1",
            category = "AFTER_PRAYER",
            arabicText = "أَسْتَغْفِرُ اللَّهَ (ثلاثاً) اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ",
            translationEn = "I seek Allah's forgiveness (3x). O Allah, You are Peace and from You comes peace.",
            reference = "Sahih Muslim 591",
            targetCount = 1
        ),
        DhikrItem(
            id = "p_2",
            category = "AFTER_PRAYER",
            arabicText = "سُبْحَانَ اللَّهِ (33) وَالْحَمْدُ لِلَّهِ (33) وَاللَّهُ أَكْبَرُ (33)",
            translationEn = "SubhanAllah (33), Alhamdulillah (33), Allahu Akbar (33).",
            reference = "Sahih Muslim 597",
            targetCount = 33
        ),
        // Sleep Adhkar
        DhikrItem(
            id = "s_1",
            category = "SLEEP",
            arabicText = "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا",
            translationEn = "In Your name, O Allah, I die and I live.",
            reference = "Sahih Al-Bukhari 6324",
            targetCount = 1
        )
    )
}
