package com.cyberzilla.islamicwidget.utils

enum class HilalCriteria(
    val displayName: String,
    val description: String
) {
    NEO_MABIMS(
        "Neo-MABIMS",
        "Indonesia, Malaysia, Brunei, Singapura (2022)\nAltitude ≥ 3°, Elongasi ≥ 6.4°"
    ),
    MABIMS_LAMA(
        "MABIMS Lama",
        "MABIMS sebelum 2022\nAltitude ≥ 2°, Elongasi ≥ 3°, Umur Bulan ≥ 8 jam"
    ),
    WUJUDUL_HILAL(
        "Wujudul Hilal",
        "Muhammadiyah – Indonesia\nBulan di atas ufuk saat Maghrib (Altitude > 0°)"
    ),
    ISTANBUL_1978(
        "Istanbul 1978",
        "Beberapa negara OKI\nAltitude ≥ 5°, Elongasi ≥ 8°"
    ),
    DANJON_LIMIT(
        "Danjon Limit",
        "Batas fisika optik universal\nElongasi ≥ 7°, Bulan di atas ufuk"
    ),
    SAAO(
        "SAAO",
        "Afrika Selatan\nAltitude ≥ 3.5°, Elongasi ≥ 7°"
    ),
    UMM_AL_QURA(
        "Umm al-Qura",
        "Arab Saudi (sipil)\nKonjungsi sebelum Maghrib & Bulan terbenam setelah Matahari"
    ),
    IJTIMA_QABLA_GHURUB(
        "Ijtima' Qabla Ghurub",
        "Konjungsi sebelum Maghrib"
    );

    companion object {
        fun fromName(name: String): HilalCriteria {
            return entries.find { it.name == name } ?: NEO_MABIMS
        }
    }
}
