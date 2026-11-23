package com.example.quran

data class SurahMeta(
    val number: Int,
    val nameAr: String,
    val nameEn: String,
    val ayahCount: Int,
    val isMakki: Boolean,
    val startPage: Int
)

data class JuzMeta(
    val number: Int,
    val nameAr: String,
    val startPage: Int,
    val startSurah: Int,
    val startAyah: Int
)

data class HizbQuarterMeta(
    val hizb: Int,
    val quarter: Int, // 1..4
    val page: Int
)

data class LinguisticAnalysis(
    val tafsir: String,
    val tafsirRef: String,
    val irab: String,
    val sarf: String
)

data class AyahRecord(
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val textUthmani: String,
    val linguistics: LinguisticAnalysis? = null
)

object QuranCatalog {

    // Complete 114 Surahs with verified Madinah Mushaf (Hafs) start pages (1..604)
    val surahs: List<SurahMeta> = listOf(
        SurahMeta(1, "الفاتحة", "Al-Fatihah", 7, true, 1),
        SurahMeta(2, "البقرة", "Al-Baqarah", 286, false, 2),
        SurahMeta(3, "آل عمران", "Ali 'Imran", 200, false, 50),
        SurahMeta(4, "النساء", "An-Nisa", 176, false, 77),
        SurahMeta(5, "المائدة", "Al-Ma'idah", 120, false, 106),
        SurahMeta(6, "الأنعام", "Al-An'am", 165, true, 128),
        SurahMeta(7, "الأعراف", "Al-A'raf", 206, true, 151),
        SurahMeta(8, "الأنفال", "Al-Anfal", 75, false, 177),
        SurahMeta(9, "التوبة", "At-Tawbah", 129, false, 187),
        SurahMeta(10, "يونس", "Yunus", 109, true, 208),
        SurahMeta(11, "هود", "Hud", 123, true, 221),
        SurahMeta(12, "يوسف", "Yusuf", 111, true, 235),
        SurahMeta(13, "الرعد", "Ar-Ra'd", 43, false, 249),
        SurahMeta(14, "إبراهيم", "Ibrahim", 52, true, 255),
        SurahMeta(15, "الحجر", "Al-Hijr", 99, true, 262),
        SurahMeta(16, "النحل", "An-Nahl", 128, true, 267),
        SurahMeta(17, "الإسراء", "Al-Isra", 111, true, 282),
        SurahMeta(18, "الكهف", "Al-Kahf", 110, true, 293),
        SurahMeta(19, "مريم", "Maryam", 98, true, 305),
        SurahMeta(20, "طه", "Taha", 135, true, 312),
        SurahMeta(21, "الأنبياء", "Al-Anbya", 112, true, 322),
        SurahMeta(22, "الحج", "Al-Hajj", 78, false, 332),
        SurahMeta(23, "المؤمنون", "Al-Mu'minun", 118, true, 342),
        SurahMeta(24, "النور", "An-Nur", 64, false, 350),
        SurahMeta(25, "الفرقان", "Al-Furqan", 77, true, 359),
        SurahMeta(26, "الشعراء", "Ash-Shu'ara", 227, true, 367),
        SurahMeta(27, "النمل", "An-Naml", 93, true, 377),
        SurahMeta(28, "القصص", "Al-Qasas", 88, true, 385),
        SurahMeta(29, "العنكبوت", "Al-'Ankabut", 69, true, 396),
        SurahMeta(30, "الروم", "Ar-Rum", 60, true, 404),
        SurahMeta(31, "لقمان", "Luqman", 34, true, 411),
        SurahMeta(32, "السجدة", "As-Sajdah", 30, true, 415),
        SurahMeta(33, "الأحزاب", "Al-Ahzab", 73, false, 418),
        SurahMeta(34, "سبأ", "Saba", 54, true, 428),
        SurahMeta(35, "فاطر", "Fatir", 45, true, 434),
        SurahMeta(36, "يس", "Ya-Sin", 83, true, 440),
        SurahMeta(37, "الصافات", "As-Saffat", 182, true, 446),
        SurahMeta(38, "ص", "Sad", 88, true, 453),
        SurahMeta(39, "الزمر", "Az-Zumar", 75, true, 458),
        SurahMeta(40, "غافر", "Ghafir", 85, true, 467),
        SurahMeta(41, "فصلت", "Fussilat", 54, true, 477),
        SurahMeta(42, "الشورى", "Ash-Shuraa", 53, true, 483),
        SurahMeta(43, "الزخرف", "Az-Zukhruf", 89, true, 489),
        SurahMeta(44, "الدخان", "Ad-Dukhan", 59, true, 496),
        SurahMeta(45, "الجاثية", "Al-Jathiyah", 37, true, 499),
        SurahMeta(46, "الأحقاف", "Al-Ahqaf", 35, true, 502),
        SurahMeta(47, "محمد", "Muhammad", 38, false, 507),
        SurahMeta(48, "الفتح", "Al-Fath", 29, false, 511),
        SurahMeta(49, "الحجرات", "Al-Hujurat", 18, false, 515),
        SurahMeta(50, "ق", "Qaf", 45, true, 518),
        SurahMeta(51, "الذاريات", "Adh-Dhariyat", 60, true, 520),
        SurahMeta(52, "الطور", "At-Tur", 49, true, 523),
        SurahMeta(53, "النجم", "An-Najm", 62, true, 526),
        SurahMeta(54, "القمر", "Al-Qamar", 55, true, 528),
        SurahMeta(55, "الرحمن", "Ar-Rahman", 78, false, 531),
        SurahMeta(56, "الواقعة", "Al-Waqi'ah", 96, true, 534),
        SurahMeta(57, "الحديد", "Al-Hadid", 29, false, 537),
        SurahMeta(58, "المجادلة", "Al-Mujadila", 22, false, 542),
        SurahMeta(59, "الحشر", "Al-Hashr", 24, false, 545),
        SurahMeta(60, "الممتحنة", "Al-Mumtahanah", 13, false, 549),
        SurahMeta(61, "الصف", "As-Saf", 14, false, 551),
        SurahMeta(62, "الجمعة", "Al-Jumu'ah", 11, false, 553),
        SurahMeta(63, "المنافقون", "Al-Munafiqun", 11, false, 554),
        SurahMeta(64, "التغابن", "At-Taghabun", 18, false, 556),
        SurahMeta(65, "الطلاق", "At-Talaq", 12, false, 558),
        SurahMeta(66, "التحريم", "At-Tahrim", 12, false, 560),
        SurahMeta(67, "الملك", "Al-Mulk", 30, true, 562),
        SurahMeta(68, "القلم", "Al-Qalam", 52, true, 564),
        SurahMeta(69, "الحاقة", "Al-Haqqah", 52, true, 566),
        SurahMeta(70, "المعارج", "Al-Ma'arij", 44, true, 568),
        SurahMeta(71, "نوح", "Nuh", 28, true, 570),
        SurahMeta(72, "الجن", "Al-Jinn", 28, true, 572),
        SurahMeta(73, "المزمل", "Al-Muzzammil", 20, true, 574),
        SurahMeta(74, "المدثر", "Al-Muddaththir", 56, true, 575),
        SurahMeta(75, "القيامة", "Al-Qiyamah", 40, true, 577),
        SurahMeta(76, "الإنسان", "Al-Insan", 31, false, 578),
        SurahMeta(77, "المرسلات", "Al-Mursalat", 50, true, 580),
        SurahMeta(78, "النبأ", "An-Naba", 40, true, 582),
        SurahMeta(79, "النازعات", "An-Nazi'at", 46, true, 583),
        SurahMeta(80, "عبس", "'Abasa", 42, true, 585),
        SurahMeta(81, "التكوير", "At-Takwir", 29, true, 586),
        SurahMeta(82, "الانفطار", "Al-Infitar", 19, true, 587),
        SurahMeta(83, "المطففين", "Al-Mutaffifin", 36, true, 587),
        SurahMeta(84, "الانشقاق", "Al-Inshiqaq", 25, true, 589),
        SurahMeta(85, "البروج", "Al-Buruj", 22, true, 590),
        SurahMeta(86, "الطارق", "At-Tariq", 17, true, 591),
        SurahMeta(87, "الأعلى", "Al-A'la", 19, true, 591),
        SurahMeta(88, "الغاشية", "Al-Ghashiyah", 26, true, 592),
        SurahMeta(89, "الفجر", "Al-Fajr", 30, true, 593),
        SurahMeta(90, "البلد", "Al-Balad", 20, true, 594),
        SurahMeta(91, "الشمس", "Ash-Shams", 15, true, 595),
        SurahMeta(92, "الليل", "Al-Layl", 21, true, 595),
        SurahMeta(93, "الضحى", "Ad-Duhaa", 11, true, 596),
        SurahMeta(94, "الشرح", "Ash-Sharh", 8, true, 596),
        SurahMeta(95, "التين", "At-Tin", 8, true, 597),
        SurahMeta(96, "العلق", "Al-'Alaq", 19, true, 597),
        SurahMeta(97, "القدر", "Al-Qadr", 5, true, 598),
        SurahMeta(98, "البينة", "Al-Bayyinah", 8, false, 598),
        SurahMeta(99, "الزلزلة", "Az-Zalzalah", 8, false, 599),
        SurahMeta(100, "العاديات", "Al-'Adiyat", 11, true, 599),
        SurahMeta(101, "القارعة", "Al-Qari'ah", 11, true, 600),
        SurahMeta(102, "التكاثر", "At-Takathur", 8, true, 600),
        SurahMeta(103, "العصر", "Al-'Asr", 3, true, 601),
        SurahMeta(104, "الهمزة", "Al-Humazah", 9, true, 601),
        SurahMeta(105, "الفيل", "Al-Fil", 5, true, 601),
        SurahMeta(106, "قريش", "Quraysh", 4, true, 602),
        SurahMeta(107, "الماعون", "Al-Ma'un", 7, true, 602),
        SurahMeta(108, "الكوثر", "Al-Kawthar", 3, true, 602),
        SurahMeta(109, "الكافرون", "Al-Kafirun", 6, true, 603),
        SurahMeta(110, "النصر", "An-Nasr", 3, false, 603),
        SurahMeta(111, "المسد", "Al-Masad", 5, true, 603),
        SurahMeta(112, "الإخلاص", "Al-Ikhlas", 4, true, 604),
        SurahMeta(113, "الفلق", "Al-Falaq", 5, true, 604),
        SurahMeta(114, "الناس", "An-Nas", 6, true, 604)
    )

    val juzList: List<JuzMeta> = listOf(
        JuzMeta(1, "الجزء الأول", 1, 1, 1),
        JuzMeta(2, "الجزء الثاني", 22, 2, 142),
        JuzMeta(3, "الجزء الثالث", 42, 2, 253),
        JuzMeta(4, "الجزء الرابع", 62, 3, 93),
        JuzMeta(5, "الجزء الخامس", 82, 4, 24),
        JuzMeta(6, "الجزء السادس", 102, 4, 148),
        JuzMeta(7, "الجزء السابع", 121, 5, 82),
        JuzMeta(8, "الجزء الثامن", 142, 6, 111),
        JuzMeta(9, "الجزء التاسع", 162, 7, 88),
        JuzMeta(10, "الجزء العاشر", 182, 8, 41),
        JuzMeta(11, "الجزء الحادي عشر", 201, 9, 93),
        JuzMeta(12, "الجزء الثاني عشر", 222, 11, 6),
        JuzMeta(13, "الجزء الثالث عشر", 242, 12, 53),
        JuzMeta(14, "الجزء الرابع عشر", 262, 15, 1),
        JuzMeta(15, "الجزء الخامس عشر", 282, 17, 1),
        JuzMeta(16, "الجزء السادس عشر", 302, 18, 75),
        JuzMeta(17, "الجزء السابع عشر", 322, 21, 1),
        JuzMeta(18, "الجزء الثامن عشر", 342, 23, 1),
        JuzMeta(19, "الجزء التاسع عشر", 362, 25, 21),
        JuzMeta(20, "الجزء العشرون", 382, 27, 56),
        JuzMeta(21, "الجزء الحادي والعشرون", 402, 29, 46),
        JuzMeta(22, "الجزء الثاني والعشرون", 422, 33, 31),
        JuzMeta(23, "الجزء الثالث والعشرون", 442, 36, 28),
        JuzMeta(24, "الجزء الرابع والعشرون", 462, 39, 32),
        JuzMeta(25, "الجزء الخامس والعشرون", 482, 41, 47),
        JuzMeta(26, "الجزء السادس والعشرون", 502, 46, 1),
        JuzMeta(27, "الجزء السابع والعشرون", 522, 51, 31),
        JuzMeta(28, "الجزء الثامن والعشرون", 542, 58, 1),
        JuzMeta(29, "الجزء التاسع والعشرون", 562, 67, 1),
        JuzMeta(30, "الجزء الثلاثون", 582, 78, 1)
    )

    // 60 Hizbs mapped systematically across the 30 Juz (2 Hizbs per Juz, 4 quarters each)
    val hizbQuarters: List<HizbQuarterMeta> by lazy {
        val list = mutableListOf<HizbQuarterMeta>()
        for (juzIdx in juzList.indices) {
            val startP = juzList[juzIdx].startPage
            val endP = if (juzIdx < juzList.lastIndex) juzList[juzIdx + 1].startPage - 1 else 604
            val span = (endP - startP + 1).coerceAtLeast(1)
            for (hOffset in 0..1) {
                val hizbNum = juzIdx * 2 + hOffset + 1
                for (q in 1..4) {
                    val idxInJuz = hOffset * 4 + (q - 1)
                    val p = (startP + (idxInJuz * span) / 8).coerceIn(1, 604)
                    list.add(HizbQuarterMeta(hizbNum, q, p))
                }
            }
        }
        list
    }

    // Verified bundled Uthmani Ayahs + referenced Tafsir, I'rab, and Sarf
    private val bundledAyahs: MutableList<AyahRecord> = mutableListOf(
        // Surah 1: Al-Fatihah (Page 1)
        AyahRecord(
            1, 1, 1, "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
            LinguisticAnalysis(
                tafsir = "أبتدئ قراءة القرآن باسم الله مستعيناً به، والرحمن ذو الرحمة العامة لجميع الخلق، والرحيم ذو الرحمة الخاصة بالمؤمنين.",
                tafsirRef = "التفسير الميسر — مجمع الملك فهد",
                irab = "بِسْمِ: جار ومجرور متعلقان بمحذوف تقديره أبتدئ. اللَّهِ: لفظ الجلالة مضاف إليه مجرور بالكسرة. الرَّحْمَٰنِ الرَّحِيمِ: نعتان مجروران للفظ الجلالة.",
                sarf = "اسم: وزن (فِعْل) أصله س م و. الرحمن: صيغة مبالغة على وزن (فَعْلَان) من الجذر (ر ح م). الرحيم: صيغة مبالغة / صفة مشبهة على وزن (فَعِيل) من (ر ح م)."
            )
        ),
        AyahRecord(
            1, 2, 1, "ٱلۡحَمۡدُ لِلَّهِ رَبِّ ٱلۡعَٰلَمِينَ",
            LinguisticAnalysis(
                tafsir = "الثناء الكامل على الله بصفاته التي كلها أوصاف كمال، وبنعمه الظاهرة والباطنة، وهو المربي لجميع العالمين بنعمه.",
                tafsirRef = "التفسير الميسر وتفسير ابن كثير",
                irab = "الْحَمْدُ: مبتدأ مرفوع بالضمة. لِلَّهِ: جار ومجرور متعلقان بخبر محذوف. رَبِّ: نعت أو بدل مجرور وهو مضاف. الْعَالَمِينَ: مضاف إليه مجرور بالياء لأنه ملحق بجمع المذكر السالم.",
                sarf = "الحمد: مصدر ثلاثي على وزن (فَعْل) من (ح م د). رب: صفة على وزن (فَعْل) من (ر ب ب)."
            )
        ),
        AyahRecord(
            1, 3, 1, "ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
            LinguisticAnalysis(
                tafsir = "تأكيد لصفة الرحمة الواسعة التي يغمر الله بها عباده في الدنيا والآخرة.",
                tafsirRef = "التفسير الميسر",
                irab = "الرَّحْمَٰنِ الرَّحِيمِ: نعتان مجروران للفظ الجلالة في الآية السابقة.",
                sarf = "الرحمن (فَعْلَان)، الرحيم (فَعِيل)، جذرهما الثلاثي (ر ح م)."
            )
        ),
        AyahRecord(
            1, 4, 1, "مَٰلِكِ يَوۡمِ ٱلدِّينِ",
            LinguisticAnalysis(
                tafsir = "المنفرد بملك يوم القيامة والحساب والجزاء، حيث لا ملك يومئذ إلا له سبحانه.",
                tafsirRef = "التفسير الميسر",
                irab = "مَالِكِ: نعت مجرور للفظ الجلالة وهو مضاف. يَوْمِ: مضاف إليه مجرور. الدِّينِ: مضاف إليه مجرور.",
                sarf = "مالك: اسم فاعل على وزن (فَاعِل) من الفعل الثلاثي (مَلَكَ) وجذره (م ل ك). الدين: مصدر على وزن (فِعْل) من (د ي ن)."
            )
        ),
        AyahRecord(
            1, 5, 1, "إِيَّاكَ نَعۡبُدُ وَإِيَّاكَ نَسۡتَعِينُ",
            LinguisticAnalysis(
                tafsir = "نخصك وحدك بالعبادة والطاعة، ونطلب العون منك وحدك في جميع أمورنا.",
                tafsirRef = "التفسير الميسر",
                irab = "إِيَّاكَ: ضمير نصب منفصل مبني في محل نصب مفعول به مقدم (يفيد الحصر). نَعْبُدُ: فعل مضارع مرفوع وفاعله ضمير مستتر تقديره نحن. وَإِيَّاكَ نَسْتَعِينُ: معطوفة على الجملة السابقة بنفس الإعراب.",
                sarf = "نعبد: مضارع من (عَبَدَ - يَعْبُدُ) وزن (نَفْعُلُ)، الجذر (ع ب د). نستعين: مضارع مزيد من (اسْتَعَانَ - يَسْتَعِينُ) على وزن (اسْتَفْعَلَ)، الجذر (ع و ن)."
            )
        ),
        AyahRecord(
            1, 6, 1, "ٱهۡدِنَا ٱلصِّرَٰطَ ٱلۡمُسۡتَقِيمَ",
            LinguisticAnalysis(
                tafsir = "دلّنا وأرشدنا وثبتنا على الطريق الواضح الموصل إلى رضاك وجنتك وهو الإسلام.",
                tafsirRef = "التفسير الميسر",
                irab = "اهْدِنَا: فعل أمر للدعاء مبني على حذف حرف العلة، و(نا) ضمير متصل في محل نصب مفعول به أول، والفاعل مستتر وجوباً. الصِّرَاطَ: مفعول به ثان منصوب. الْمُسْتَقِيمَ: نعت منصوب.",
                sarf = "اهدِ: أمر من (هَدَى - يَهْدِي) معتل ناقص يائي، الجذر (هـ د ي). المستقيم: اسم فاعل من (اسْتَقَامَ) على وزن (مُسْتَفْعِل)، الجذر (ق و م)."
            )
        ),
        AyahRecord(
            1, 7, 1, "صِرَٰطَ ٱلَّذِينَ أَنۡعَمۡتَ عَلَيۡهِمۡ غَيۡرِ ٱلۡمَغۡضُوبِ عَلَيۡهِمۡ وَلَا ٱلضَّآلِّينَ",
            LinguisticAnalysis(
                tafsir = "طريق المنعم عليهم من النبيين والصديقين والشهداء والصالحين، غير طريق من غضب الله عليهم لتركهم الحق على علم، ولا الضالين عن الهدى.",
                tafsirRef = "التفسير الميسر وتفسير الطبري",
                irab = "صِرَاطَ: بدل مطابق من الصراط الأولى منصوب. الَّذِينَ: اسم موصول في محل جر مضاف إليه. أَنْعَمْتَ: فعل ماض مبني على السكون والتاء فاعل. غَيْرِ: بدل أو نعت للذين مجرور. الْمَغْضُوبِ: مضاف إليه مجرور. الضَّالِّينَ: معطوف مجرور بالياء لأنه جمع مذكر سالم.",
                sarf = "أنعمت: فعل ماضٍ رباعي على وزن (أَفْعَلَ) من الجذر (ن ع م). المغضوب: اسم مفعول ثلاثي على وزن (مَفْعُول) من (غ ض ب). الضالين: جمع اسم فاعل من (ضَلَّ) مضعف ثلاثي (ض ل ل)."
            )
        ),

        // Surah 2:255 Ayat al-Kursi (Page 42)
        AyahRecord(
            2, 255, 42,
            "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلۡحَيُّ ٱلۡقَيُّومُۚ لَا تَأۡخُذُهُۥ سِنَةٞ وَلَا نَوۡمٞۚ لَّهُۥ مَا فِي ٱلسَّمَٰوَٰتِ وَمَا فِي ٱلۡأَرۡضِۗ مَن ذَا ٱلَّذِي يَشۡفَعُ عِندَهُۥٓ إِلَّا بِإِذۡنِهِۦۚ يَعۡلَمُ مَا بَيۡنَ أَيۡدِيهِمۡ وَمَا خَلۡفَهُمۡۖ وَلَا يُحِيطُونَ بِشَيۡءٖ مِّنۡ عِلۡمِهِۦٓ إِلَّا بِمَا شَآءَۚ وَسِعَ كُرۡسِيُّهُ ٱلسَّمَٰوَٰتِ وَٱلۡأَرۡضَۖ وَلَا يَـُٔودُهُۥ حِفۡظُهُمَاۚ وَهُوَ ٱلۡعَلِيُّ ٱلۡعَظِيمُ",
            LinguisticAnalysis(
                tafsir = "أعظم آية في كتاب الله، تقرر توحيد الألوهية والكمال المطلق لله في حياته وقيوميته وعلمه وسلطانه، وأن الكرسي يسع السماوات والأرض ولا يثقله حفظهما.",
                tafsirRef = "تفسير ابن كثير والتفسير الميسر",
                irab = "اللَّهُ: لفظ الجلالة مبتدأ مرفوع. لَا إِلَٰهَ إِلَّا هُوَ: لا نافية للجنس واسمها مبني على الفتح وخبرها محذوف، وهُوَ بدل من الضمير المستتر. الْحَيُّ الْقَيُّومُ: خبران للمبتدأ مرفوعان.",
                sarf = "الحي: صفة مشبهة من (ح ي ي). القيوم: صيغة مبالغة على وزن (فَيْعُول) من (ق و م). يَؤُودُهُ: مضارع من (آدَ يَؤُودُ) بمعنى أثقله، الجذر (أ و د)."
            )
        ),

        // Surah 103: Al-Asr (Page 601)
        AyahRecord(
            103, 1, 601, "وَٱلۡعَصۡرِ",
            LinguisticAnalysis("أقسم الله بالدهر والزمان لما فيه من العبر وتقلّب الأحوال.", "التفسير الميسر", "الواو حرف قسم وجر، العصر اسم مجرور بواو القسم وعلامة جره الكسرة.", "العصر: اسم ثلاثي على وزن (فَعْل)، جذره (ع ص ر).")
        ),
        AyahRecord(
            103, 2, 601, "إِنَّ ٱلۡإِنسَٰنَ لَفِي خُسۡرٍ",
            LinguisticAnalysis("جواب القسم: إن جنس الإنسان لفي هلاك ونقصان.", "التفسير الميسر", "إنّ حرف توكيد ونصب، الإنسان اسمها منصوب، اللام المزحلقة، في خسر جار ومجرور متعلقان بمحذوف خبر إن.", "خُسْر: مصدر ثلاثي على وزن (فُعْل) من (خ س ر).")
        ),
        AyahRecord(
            103, 3, 601, "إِلَّا ٱلَّذِينَ ءَامَنُواْ وَعَمِلُواْ ٱلصَّٰلِحَٰتِ وَتَوَاصَوۡاْ بِٱلۡحَقِّ وَتَوَاصَوۡاْ بِٱلصَّبۡرِ",
            LinguisticAnalysis("استثنى الله من الخسران أهل الإيمان الصادق، والعمل الصالح، والتواصي بالحق، والتواصي بالصبر على الطاعة وعن المعصية.", "التفسير الميسر", "إلّا أداة استثناء، الذين اسم موصول في محل نصب مستثنى، آمنوا فعل ماضٍ وواو الجماعة فاعل.", "تواصوا: فعل ماضٍ مزيد على وزن (تَفَاعَلُوا) يفيد المشاركة، الجذر (و ص ي).")
        ),

        // Surah 108: Al-Kawthar (Page 602)
        AyahRecord(108, 1, 602, "إِنَّآ أَعۡطَيۡنَٰكَ ٱلۡكَوۡثَرَ",
            LinguisticAnalysis("إنا منحناك يا محمد الخير الكثير في الدنيا والآخرة، ومنه نهر الكوثر في الجنة.", "التفسير الميسر", "إنا: إن واسمها، أعطيناك: فعل ماض وفاعله ومفعوله الأول، الكوثر: مفعول به ثانٍ منصوب.", "أعطى: رباعي على وزن (أَفْعَلَ) من (ع ط و). الكوثر: فَوْعَل من الكثرة (ك ث ر).")
        ),
        AyahRecord(108, 2, 602, "فَصَلِّ لِرَبِّكَ وَٱنۡحَرۡ",
            LinguisticAnalysis("فأخلص لربك صلاتك كلها، واذبح نسكك له وحده شكراً على نعمه.", "التفسير الميسر", "الفاء عاطفة، صلِّ فعل أمر مبني على حذف حرف العلة، وانحر فعل أمر مبني على السكون.", "صلِّ: أمر من (صَلَّى) المضعف، الجذر (ص ل و). انحر: أمر ثلاثي من (ن ح ر).")
        ),
        AyahRecord(108, 3, 602, "إِنَّ شَانِئَكَ هُوَ ٱلۡأَبۡتَرُ",
            LinguisticAnalysis("إن مبغضك ومعاديك هو المنقطع الأثر المقطوع من كل خير.", "التفسير الميسر", "إن حرف توكيد، شانئك اسمها منصوب، هو ضمير فصل، الأبتر خبر إن مرفوع.", "شانئ: اسم فاعل من (شَنَأَ) بمعنى أبغض، الجذر (ش ن أ). الأبتر: أفعل من (ب ت ر).")
        ),

        // Surah 112: Al-Ikhlas (Page 604)
        AyahRecord(
            112, 1, 604, "قُلۡ هُوَ ٱللَّهُ أَحَدٌ",
            LinguisticAnalysis("قل يا محمد: هو الله الواحد الأحد المتفرد بصفات الكمال، لا شريك له.", "التفسير الميسر", "قل: فعل أمر والفاعل مستتر. هو: ضمير الشأن مبتدأ أول. اللَّهُ: مبتدأ ثانٍ. أَحَدٌ: خبر المبتدأ الثاني، والجملة خبر الأول.", "قل: أمر ثلاثي أجوف من (قَالَ - يَقُولُ)، الجذر (ق و ل). أحد: وزن (فَعَل) مبدل من (وَحَد).")
        ),
        AyahRecord(
            112, 2, 604, "ٱللَّهُ ٱلصَّمَدُ",
            LinguisticAnalysis("الله المقصود في قضاء الحوائج على الدوام، السيد الذي كمل في سؤدده.", "التفسير الميسر", "اللَّهُ: مبتدأ مرفوع. الصَّمَدُ: خبر مرفوع بالضمة.", "الصمد: صفة على وزن (فَعَل) من (ص م د).")
        ),
        AyahRecord(
            112, 3, 604, "لَمۡ يَلِدۡ وَلَمۡ يُولَدۡ",
            LinguisticAnalysis("تنزه سبحانه عن أن يكون له ولد أو والد، لغناه المطلق وأزليته.", "التفسير الميسر", "لَمْ: حرف جزم. يَلِدْ: مضارع مجزوم بالسكون. وَلَمْ يُولَدْ: مضارع مبني للمجهول مجزوم بالسكون ونائب الفاعل مستتر.", "يلد / يولد: من الثلاثي المثال (وَلَدَ - يَلِدُ)، حذفت واوه في المعلوم، الجذر (و ل د).")
        ),
        AyahRecord(
            112, 4, 604, "وَلَمۡ يَكُن لَّهُۥ كُفُوًا أَحَدُۢ",
            LinguisticAnalysis("وليس له سبحانه مثيل ولا مكافئ ولا نظير في ذاته وأسمائه وصفاته وأفعاله.", "التفسير الميسر", "يَكُنْ: مضارع ناقص مجزوم. لَهُ: جار ومجرور متعلقان بكفواً. كُفُوًا: خبر يكن مقدم منصوب. أَحَدٌ: اسم يكن مؤخر مرفوع.", "يكن: من (كَانَ) الأجوف (ك و ن). كفواً: وزن (فُعُل) من (ك ف أ).")
        ),

        // Surah 113: Al-Falaq (Page 604)
        AyahRecord(113, 1, 604, "قُلۡ أَعُوذُ بِرَبِّ ٱلۡفَلَقِ",
            LinguisticAnalysis("قل أعتصم وألتجئ برب الصبح الذي يفلقه بقدرته.", "التفسير الميسر", "قل فعل أمر، أعوذ فعل مضارع مرفوع، برب جار ومجرور، الفلق مضاف إليه.", "أعوذ: مضارع أجوف واوي من (عَاذَ - يَعُوذُ)، الجذر (ع و ذ). الفلق: وزن (فَعَل) من (ف ل ق).")
        ),
        AyahRecord(113, 2, 604, "مِن شَرِّ مَا خَلَقَ",
            LinguisticAnalysis("من أذى وشر جميع المخلوقات التي يصدر منها الشر.", "التفسير الميسر", "من شر جار ومجرور متعلقان بأعوذ، ما اسم موصول مضاف إليه، خلق فعل ماضٍ.", "خلق: فعل ثلاثي على وزن (فَعَلَ)، الجذر (خ ل ق).")
        ),
        AyahRecord(113, 3, 604, "وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ",
            LinguisticAnalysis("ومن شر الليل المظلم إذا دخل واشتد ظلامه.", "التفسير الميسر", "غاسق مضاف إليه مجرور، إذا ظرف زمان، وقب فعل ماضٍ.", "غاسق: اسم فاعل (فَاعِل) من (غ س ق). وقب: ثلاثي مثال (و ق ب).")
        ),
        AyahRecord(113, 4, 604, "وَمِن شَرِّ ٱلنَّفَّٰثَٰتِ فِي ٱلۡعُقَدِ",
            LinguisticAnalysis("ومن شر الساحرات اللاتي ينفثن في عقد الخيوط لإيذاء الناس.", "التفسير الميسر", "النفاثات مضاف إليه مجرور، في العقد جار ومجرور.", "النفاثات: جمع صيغة مبالغة (فَعَّالَة) من (ن ف ث).")
        ),
        AyahRecord(113, 5, 604, "وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ",
            LinguisticAnalysis("ومن شر كل حاسد يتمنى زوال النعمة عن غيره إذا عمل بمقتضى حسده.", "التفسير الميسر", "حاسد مضاف إليه مجرور، إذا ظرفية، حسد فعل ماضٍ.", "حاسد: اسم فاعل من (حَسَدَ)، الجذر (ح س د).")
        ),

        // Surah 114: An-Nas (Page 604)
        AyahRecord(114, 1, 604, "قُلۡ أَعُوذُ بِرَبِّ ٱلنَّاسِ",
            LinguisticAnalysis("قل أستعيذ وألتجئ بمربي الناس ومدبر شؤونهم.", "التفسير الميسر", "قل فعل أمر، أعوذ مضارع مرفوع، برب الناس جار ومجرور ومضاف إليه.", "أعوذ: من (ع و ذ).")
        ),
        AyahRecord(114, 2, 604, "مَلِكِ ٱلنَّاسِ",
            LinguisticAnalysis("المالك المتصرف في جميع أمور الناس الغني عنهم.", "التفسير الميسر", "ملك: بدل مجرور من رب، الناس مضاف إليه.", "مَلِك: صفة مشبهة على وزن (فَعِل) من (م ل ك).")
        ),
        AyahRecord(114, 3, 604, "إِلَٰهِ ٱلنَّاسِ",
            LinguisticAnalysis("المعبود بحق الذي لا معبود سواه.", "التفسير الميسر", "إله: بدل مجرور، الناس مضاف إليه.", "إله: وزن (فِعَال) من (أ ل هـ).")
        ),
        AyahRecord(114, 4, 604, "مِن شَرِّ ٱلۡوَسۡوَاسِ ٱلۡخَنَّاسِ",
            LinguisticAnalysis("من شر الشيطان الذي يوسوس في الصدور ويخنس ويتوارى عند ذكر الله.", "التفسير الميسر", "الوسواس مضاف إليه، الخناس نعت مجرور.", "الوسواس: اسم رباعي مضعف (فَعْلَال). الخناس: صيغة مبالغة (فَعَّال) من (خ ن س).")
        ),
        AyahRecord(114, 5, 604, "ٱلَّذِي يُوَسۡوِسُ فِي صُدُورِ ٱلنَّاسِ",
            LinguisticAnalysis("الذي يلقي الشكوك والشرور في قلوب البشر.", "التفسير الميسر", "الذي نعت، يوسوس مضارع مرفوع، في صدور جار ومجرور.", "يوسوس: مضارع رباعي مجرد من (وَسْوَسَ) على وزن (يُفَعْلِلُ).")
        ),
        AyahRecord(114, 6, 604, "مِنَ ٱلۡجِنَّةِ وَٱلنَّاسِ",
            LinguisticAnalysis("أي الشياطين الموسوسين من الجن ومن الإنس على السواء.", "التفسير الميسر", "من الجنة جار ومجرور، والناس معطوف مجرور.", "الجنة: اسم جمع من (ج ن ن).")
        )
    )

    fun surahByNumber(number: Int): SurahMeta? = surahs.firstOrNull { it.number == number }

    fun surahsOnPage(page: Int): List<SurahMeta> {
        val clamped = page.coerceIn(1, 604)
        return surahs.filterIndexed { idx, surah ->
            val nextStart = if (idx < surahs.lastIndex) surahs[idx + 1].startPage else 605
            clamped in surah.startPage..nextStart
        }.ifEmpty { listOf(surahs.last()) }
    }

    fun juzForPage(page: Int): JuzMeta {
        val clamped = page.coerceIn(1, 604)
        return juzList.lastOrNull { it.startPage <= clamped } ?: juzList.first()
    }

    fun hizbForPage(page: Int): HizbQuarterMeta {
        val clamped = page.coerceIn(1, 604)
        return hizbQuarters.lastOrNull { it.page <= clamped } ?: hizbQuarters.first()
    }

    fun ayahsOnPage(page: Int): List<AyahRecord> =
        bundledAyahs.filter { it.page == page }.sortedWith(compareBy({ it.surah }, { it.ayah }))

    fun ayahsForSurahRange(surah: Int, startAyah: Int, endAyah: Int): List<AyahRecord> =
        bundledAyahs.filter { it.surah == surah && it.ayah in startAyah..endAyah }
            .sortedBy { it.ayah }

    fun findAyah(surah: Int, ayah: Int): AyahRecord? =
        bundledAyahs.firstOrNull { it.surah == surah && it.ayah == ayah }

    fun pageForAyah(surah: Int, ayah: Int): Int {
        findAyah(surah, ayah)?.let { return it.page }
        val meta = surahByNumber(surah) ?: return 1
        val nextStart = surahByNumber(surah + 1)?.startPage ?: 605
        val span = (nextStart - meta.startPage).coerceAtLeast(1)
        val fraction = ((ayah - 1).coerceAtLeast(0).toFloat() / meta.ayahCount.coerceAtLeast(1))
        return (meta.startPage + (fraction * span).toInt()).coerceIn(meta.startPage, 604)
    }

    fun allLoadedAyahs(): List<AyahRecord> = bundledAyahs.toList()

    fun searchAyahs(query: String): List<AyahRecord> {
        val normQ = com.example.ai.ArabicNlpNormalizer.normalizeArabicText(query)
        if (normQ.isEmpty()) return emptyList()
        return bundledAyahs.filter { record ->
            com.example.ai.ArabicNlpNormalizer.normalizeArabicText(record.textUthmani).contains(normQ, ignoreCase = true)
        }
    }

    /**
     * Merges verified Ayahs & optional Tafsir/I'rab/Sarf imported from an external Mushaf resource pack.
     */
    fun mergeImportedAyahs(imported: List<AyahRecord>) {
        val map = bundledAyahs.associateBy { it.surah to it.ayah }.toMutableMap()
        for (item in imported) {
            val existing = map[item.surah to item.ayah]
            map[item.surah to item.ayah] = if (existing != null && item.linguistics == null) {
                item.copy(linguistics = existing.linguistics)
            } else {
                item
            }
        }
        bundledAyahs.clear()
        bundledAyahs.addAll(map.values.sortedWith(compareBy({ it.page }, { it.surah }, { it.ayah })))
    }
}
