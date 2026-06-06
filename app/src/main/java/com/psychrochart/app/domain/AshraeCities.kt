package com.psychrochart.app.domain

data class DesignCity(
    val name: String,
    val country: String,
    val altitudeM: Double,
    /** 1 % cooling design dry-bulb temperature (°C) */
    val summerDbt: Double,
    /** Coincident wet-bulb temperature (°C) */
    val summerWbt: Double,
    /** 99.6 % heating design dry-bulb temperature (°C) */
    val winterDbt: Double,
)

val ashraeCities: List<DesignCity> = listOf(
    // ── USA ──────────────────────────────────────────────────────────────────────
    DesignCity("Miami, FL",          "USA",       4.0,  33.3, 26.1,  8.9),
    DesignCity("Houston, TX",        "USA",      12.0,  37.2, 25.6,  0.6),
    DesignCity("Phoenix, AZ",        "USA",     340.0,  43.3, 22.8,  1.1),
    DesignCity("Las Vegas, NV",      "USA",     664.0,  42.2, 22.2, -1.7),
    DesignCity("Los Angeles, CA",    "USA",      32.0,  32.2, 21.7,  7.8),
    DesignCity("San Francisco, CA",  "USA",       5.0,  27.2, 18.3,  4.4),
    DesignCity("Seattle, WA",        "USA",      29.0,  29.4, 18.9, -2.2),
    DesignCity("Denver, CO",         "USA",    1611.0,  34.4, 15.6, -9.4),
    DesignCity("Chicago, IL",        "USA",     189.0,  33.3, 23.9,-14.4),
    DesignCity("Minneapolis, MN",    "USA",     287.0,  34.4, 23.9,-20.6),
    DesignCity("New York, NY",       "USA",      10.0,  33.3, 23.3, -6.1),
    DesignCity("Boston, MA",         "USA",       9.0,  32.2, 23.3,-10.6),
    DesignCity("Atlanta, GA",        "USA",     308.0,  35.0, 24.4, -4.4),
    DesignCity("Dallas, TX",         "USA",     145.0,  40.0, 25.0, -2.8),
    DesignCity("Washington, DC",     "USA",      20.0,  34.4, 24.4, -6.7),
    DesignCity("Portland, OR",       "USA",       6.0,  32.8, 20.0, -2.8),
    // ── Canada ───────────────────────────────────────────────────────────────────
    DesignCity("Toronto",            "Canada",  173.0,  33.3, 23.3,-14.4),
    DesignCity("Vancouver",          "Canada",    4.0,  27.8, 18.9, -4.4),
    DesignCity("Calgary",            "Canada",  1099.0, 31.1, 16.7,-23.9),
    DesignCity("Montreal",           "Canada",   36.0,  30.6, 23.3,-20.6),
    // ── Europe ───────────────────────────────────────────────────────────────────
    DesignCity("London",             "UK",       25.0,  28.3, 19.4, -3.3),
    DesignCity("Paris",              "France",   75.0,  30.6, 19.4, -5.0),
    DesignCity("Frankfurt",          "Germany",  112.0, 32.2, 19.4, -9.4),
    DesignCity("Amsterdam",          "Netherlands", 4.0, 26.7, 18.3, -5.0),
    DesignCity("Madrid",             "Spain",   667.0,  37.2, 20.6, -2.8),
    DesignCity("Rome",               "Italy",    46.0,  35.0, 22.8,  1.1),
    // ── Middle East ───────────────────────────────────────────────────────────────
    DesignCity("Dubai",              "UAE",       5.0,  45.0, 26.1, 11.1),
    DesignCity("Riyadh",             "Saudi Arabia", 620.0, 44.0, 18.0, 3.3),
    DesignCity("Doha",               "Qatar",    11.0,  43.3, 28.3, 12.2),
    // ── Asia ─────────────────────────────────────────────────────────────────────
    DesignCity("Mumbai",             "India",    11.0,  34.4, 27.2, 16.7),
    DesignCity("Delhi",              "India",   216.0,  43.3, 25.0,  5.6),
    DesignCity("Bangalore",          "India",   921.0,  34.4, 21.7,  9.4),
    DesignCity("Singapore",          "Singapore",16.0,  34.4, 27.8, 23.3),
    DesignCity("Bangkok",            "Thailand",  7.0,  37.2, 28.3, 19.4),
    DesignCity("Hong Kong",          "China",    33.0,  33.3, 27.8, 10.0),
    DesignCity("Shanghai",           "China",     4.0,  36.7, 27.8, -2.2),
    DesignCity("Beijing",            "China",    54.0,  35.0, 26.1,-11.7),
    DesignCity("Tokyo",              "Japan",     8.0,  36.7, 27.8, -0.6),
    // ── Australia / NZ ────────────────────────────────────────────────────────────
    DesignCity("Sydney",             "Australia", 6.0,  33.3, 22.8,  5.6),
    DesignCity("Melbourne",          "Australia",31.0,  38.3, 19.4,  4.4),
    DesignCity("Brisbane",           "Australia", 5.0,  34.4, 25.6,  6.1),
)
