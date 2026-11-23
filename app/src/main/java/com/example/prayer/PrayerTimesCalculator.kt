package com.example.prayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class CalculationMethod(
    val displayAr: String,
    val displayEn: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaMinutesAfterMaghrib: Int? = null
) {
    DAMASCUS_SYRIA("توقيت دمشق وسوريا", "Syria / Damascus Standard", 18.5, 17.5),
    UMM_AL_QURA("أم القرى", "Umm Al-Qura (Makkah)", 18.5, 0.0, 90),
    MUSLIM_WORLD_LEAGUE("رابطة العالم الإسلامي", "Muslim World League", 18.0, 17.0),
    EGYPTIAN("الهيئة المصرية العامة", "Egyptian General Authority", 19.5, 17.5),
    ISNA("أمريكا الشمالية (ISNA)", "ISNA (North America)", 15.0, 15.0),
    KARACHI("جامعة كراتشي", "Univ. of Islamic Sciences, Karachi", 18.0, 18.0),
    TEHRAN("طهران", "Institute of Geophysics, Tehran", 17.7, 14.0);

    companion object {
        fun fromName(name: String?): CalculationMethod =
            entries.firstOrNull { it.name == name } ?: DAMASCUS_SYRIA
    }
}

data class OfflineCity(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double
)

data class DailyPrayerTimes(
    val fajrMinutes: Int,
    val sunriseMinutes: Int,
    val dhuhrMinutes: Int,
    val asrMinutes: Int,
    val maghribMinutes: Int,
    val ishaMinutes: Int
) {
    fun formatTime(totalMinutes: Int): String {
        val m = ((totalMinutes % 1440) + 1440) % 1440
        val h = m / 60
        val min = m % 60
        return "%02d:%02d".format(h, min)
    }

    fun nextPrayer(nowMinutes: Int): Pair<String, Int> {
        val list = listOf(
            "FAJR" to fajrMinutes,
            "SUNRISE" to sunriseMinutes,
            "DHUHR" to dhuhrMinutes,
            "ASR" to asrMinutes,
            "MAGHRIB" to maghribMinutes,
            "ISHA" to ishaMinutes
        )
        for ((name, t) in list) {
            if (t > nowMinutes) return name to (t - nowMinutes)
        }
        return "FAJR" to ((fajrMinutes + 1440) - nowMinutes)
    }
}

object PrayerTimesCalculator {

    val offlineCities: List<OfflineCity> = listOf(
        OfflineCity("damascus", "دمشق، سوريا", "Damascus, Syria", 33.5138, 36.2765),
        OfflineCity("aleppo", "حلب، سوريا", "Aleppo, Syria", 36.2021, 37.1343),
        OfflineCity("homs", "حمص، سوريا", "Homs, Syria", 34.7324, 36.7137),
        OfflineCity("hama", "حماة، سوريا", "Hama, Syria", 35.1318, 36.7578),
        OfflineCity("latakia", "اللاذقية، سوريا", "Latakia, Syria", 35.5317, 35.7901),
        OfflineCity("tartus", "طرطوس، سوريا", "Tartus, Syria", 34.8890, 35.8866),
        OfflineCity("deir_ez_zor", "دير الزور، سوريا", "Deir ez-Zor, Syria", 35.3359, 40.1408),
        OfflineCity("daraa", "درعا، سوريا", "Daraa, Syria", 32.6189, 36.1021),
        OfflineCity("qamishli", "القامشلي، سوريا", "Qamishli, Syria", 37.0522, 41.2317),
        OfflineCity("makkah", "مكة المكرمة", "Makkah, Saudi Arabia", 21.3891, 39.8579),
        OfflineCity("madinah", "المدينة المنورة", "Madinah, Saudi Arabia", 24.5247, 39.5692),
        OfflineCity("riyadh", "الرياض", "Riyadh, Saudi Arabia", 24.7136, 46.6753),
        OfflineCity("cairo", "القاهرة", "Cairo, Egypt", 30.0444, 31.2357),
        OfflineCity("amman", "عمّان", "Amman, Jordan", 31.9454, 35.9284),
        OfflineCity("beirut", "بيروت", "Beirut, Lebanon", 33.8938, 35.5018),
        OfflineCity("baghdad", "بغداد", "Baghdad, Iraq", 33.3152, 44.3661),
        OfflineCity("istanbul", "إسطنبول", "Istanbul, Turkey", 41.0082, 28.9784),
        OfflineCity("tashkent", "طشقند، أوزبكستان", "Tashkent, Uzbekistan", 41.2995, 69.2401),
        OfflineCity("london", "لندن", "London, UK", 51.5074, -0.1278),
        OfflineCity("new_york", "نيويورك", "New York, USA", 40.7128, -74.0060)
    )

    fun cityById(id: String): OfflineCity =
        offlineCities.firstOrNull { it.id == id } ?: offlineCities.first()

    fun tryDetectLastKnownLocation(context: Context): Pair<Double, Double>? {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null) return loc.latitude to loc.longitude
        }
        return null
    }

    fun calculate(
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        calendar: Calendar = Calendar.getInstance(),
        fajrOffset: Int = 0,
        dhuhrOffset: Int = 0,
        asrOffset: Int = 0,
        maghribOffset: Int = 0,
        ishaOffset: Int = 0,
        hanafiAsr: Boolean = false
    ): DailyPrayerTimes {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val tzHours = TimeZone.getDefault().getOffset(calendar.timeInMillis) / 3_600_000.0

        val jd = julianDate(year, month, day) - longitude / (15.0 * 24.0)
        val d = jd - 2451545.0

        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2.0 * g))
        val e = 23.439 - 0.00000036 * d

        val ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        val decl = asinDeg(sinDeg(e) * sinDeg(l))
        val eqTime = (q / 15.0) - fixHour(ra)

        val transit = 12.0 + tzHours - (longitude / 15.0) - eqTime
        val sunrise = transit - sunAngleTime(0.833, latitude, decl)
        val sunset = transit + sunAngleTime(0.833, latitude, decl)
        val fajr = transit - sunAngleTime(method.fajrAngle, latitude, decl)

        val asrFactor = if (hanafiAsr) 2.0 else 1.0
        val asrAlt = atanDeg(1.0 / (asrFactor + tanDeg(kotlin.math.abs(latitude - decl))))
        val asr = transit + sunAngleTime(-asrAlt, latitude, decl)

        val isha = if (method.ishaMinutesAfterMaghrib != null) {
            sunset + (method.ishaMinutesAfterMaghrib / 60.0)
        } else {
            transit + sunAngleTime(method.ishaAngle, latitude, decl)
        }

        fun toMin(hours: Double, offset: Int): Int =
            (((hours * 60.0).toInt() + offset) % 1440 + 1440) % 1440

        return DailyPrayerTimes(
            fajrMinutes = toMin(fajr, fajrOffset),
            sunriseMinutes = toMin(sunrise, 0),
            dhuhrMinutes = toMin(transit + 2.0 / 60.0, dhuhrOffset),
            asrMinutes = toMin(asr, asrOffset),
            maghribMinutes = toMin(sunset, maghribOffset),
            ishaMinutes = toMin(isha, ishaOffset)
        )
    }

    private fun sunAngleTime(angle: Double, lat: Double, decl: Double): Double {
        val num = -sinDeg(angle) - sinDeg(lat) * sinDeg(decl)
        val den = cosDeg(lat) * cosDeg(decl)
        val cosVal = (num / den).coerceIn(-0.9999, 0.9999)
        return acosDeg(cosVal) / 15.0
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun degToRad(d: Double) = d * PI / 180.0
    private fun radToDeg(r: Double) = r * 180.0 / PI
    private fun sinDeg(d: Double) = sin(degToRad(d))
    private fun cosDeg(d: Double) = cos(degToRad(d))
    private fun tanDeg(d: Double) = tan(degToRad(d))
    private fun asinDeg(x: Double) = radToDeg(asin(x.coerceIn(-1.0, 1.0)))
    private fun acosDeg(x: Double) = radToDeg(acos(x.coerceIn(-1.0, 1.0)))
    private fun atanDeg(x: Double) = radToDeg(atan(x))
    private fun atan2Deg(y: Double, x: Double) = radToDeg(atan2(y, x))
    private fun fixAngle(a: Double): Double {
        var r = a % 360.0
        if (r < 0) r += 360.0
        return r
    }
    private fun fixHour(h: Double): Double {
        var r = h % 24.0
        if (r < 0) r += 24.0
        return r
    }
}
