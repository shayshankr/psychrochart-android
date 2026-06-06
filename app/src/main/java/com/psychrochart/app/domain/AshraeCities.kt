package com.psychrochart.app.domain

data class DesignCity(
    val name: String,
    val country: String,
    val altitudeM: Double,
    /** 1 % cooling design dry-bulb (°C) */
    val summerDbt: Double,
    /** Coincident wet-bulb at 1 % cooling (°C) */
    val summerWbt: Double,
    /** 99.6 % heating design dry-bulb (°C) */
    val winterDbt: Double,
    /** 1 % annual dew-point (°C) — peak dehumidification / monsoon condition */
    val dehumidDpt: Double,
    /** Mean coincident dry-bulb at 1 % dew-point (°C) */
    val dehumidDbt: Double,
)

val ashraeCities: List<DesignCity> = listOf(
    // ── USA ──────────────────────────────────────────────────────────────────────
    DesignCity("Miami, FL",          "USA",       4.0,  33.3, 26.1,  8.9,  26.1, 32.2),
    DesignCity("Houston, TX",        "USA",      12.0,  37.2, 25.6,  0.6,  24.4, 34.4),
    DesignCity("Phoenix, AZ",        "USA",     340.0,  43.3, 22.8,  1.1,  18.3, 37.8),
    DesignCity("Las Vegas, NV",      "USA",     664.0,  42.2, 22.2, -1.7,  13.9, 36.7),
    DesignCity("Los Angeles, CA",    "USA",      32.0,  32.2, 21.7,  7.8,  18.3, 27.8),
    DesignCity("San Francisco, CA",  "USA",       5.0,  27.2, 18.3,  4.4,  14.4, 22.8),
    DesignCity("Seattle, WA",        "USA",      29.0,  29.4, 18.9, -2.2,  13.9, 22.2),
    DesignCity("Denver, CO",         "USA",    1611.0,  34.4, 15.6, -9.4,  12.8, 26.7),
    DesignCity("Chicago, IL",        "USA",     189.0,  33.3, 23.9,-14.4,  22.8, 31.1),
    DesignCity("Minneapolis, MN",    "USA",     287.0,  34.4, 23.9,-20.6,  21.7, 28.9),
    DesignCity("New York, NY",       "USA",      10.0,  33.3, 23.3, -6.1,  22.2, 29.4),
    DesignCity("Boston, MA",         "USA",       9.0,  32.2, 23.3,-10.6,  21.7, 28.3),
    DesignCity("Atlanta, GA",        "USA",     308.0,  35.0, 24.4, -4.4,  23.9, 31.7),
    DesignCity("Dallas, TX",         "USA",     145.0,  40.0, 25.0, -2.8,  23.3, 34.4),
    DesignCity("Washington, DC",     "USA",      20.0,  34.4, 24.4, -6.7,  23.3, 31.7),
    DesignCity("Portland, OR",       "USA",       6.0,  32.8, 20.0, -2.8,  14.4, 25.6),
    // ── Canada ───────────────────────────────────────────────────────────────────
    DesignCity("Toronto",            "Canada",  173.0,  33.3, 23.3,-14.4,  21.7, 28.3),
    DesignCity("Vancouver",          "Canada",    4.0,  27.8, 18.9, -4.4,  16.1, 24.4),
    DesignCity("Calgary",            "Canada",  1099.0, 31.1, 16.7,-23.9,  11.7, 23.9),
    DesignCity("Montreal",           "Canada",   36.0,  30.6, 23.3,-20.6,  21.7, 27.8),
    // ── Europe ───────────────────────────────────────────────────────────────────
    DesignCity("London",             "UK",       25.0,  28.3, 19.4, -3.3,  15.6, 22.8),
    DesignCity("Paris",              "France",   75.0,  30.6, 19.4, -5.0,  17.2, 25.0),
    DesignCity("Frankfurt",          "Germany",  112.0, 32.2, 19.4, -9.4,  16.7, 24.4),
    DesignCity("Amsterdam",          "Netherlands", 4.0, 26.7, 18.3, -5.0, 15.0, 21.7),
    DesignCity("Madrid",             "Spain",   667.0,  37.2, 20.6, -2.8,  14.4, 29.4),
    DesignCity("Rome",               "Italy",    46.0,  35.0, 22.8,  1.1,  17.8, 29.4),
    // ── Middle East ───────────────────────────────────────────────────────────────
    DesignCity("Dubai",              "UAE",        5.0,  45.0, 26.1, 11.1,  27.2, 38.3),
    DesignCity("Riyadh",             "Saudi Arabia", 620.0, 44.0, 18.0, 3.3, 13.3, 38.9),
    DesignCity("Doha",               "Qatar",     11.0,  43.3, 28.3, 12.2,  27.8, 37.8),
    // ── India — North ─────────────────────────────────────────────────────────────
    DesignCity("Delhi",              "India",   216.0,  43.3, 25.0,  5.6,  27.8, 33.3),
    DesignCity("Jaipur",             "India",   431.0,  43.3, 22.2,  2.2,  24.4, 35.6),
    DesignCity("Jodhpur",            "India",   224.0,  43.3, 21.1,  5.0,  21.1, 35.6),
    DesignCity("Amritsar",           "India",   234.0,  44.4, 25.0, -0.6,  26.7, 36.7),
    DesignCity("Chandigarh",         "India",   321.0,  42.2, 24.4,  0.6,  25.6, 34.4),
    DesignCity("Lucknow",            "India",   123.0,  43.3, 26.7,  2.8,  27.2, 34.4),
    DesignCity("Kanpur",             "India",   126.0,  43.9, 27.2,  3.3,  27.8, 35.0),
    DesignCity("Agra",               "India",   169.0,  44.4, 26.1,  3.9,  27.2, 35.6),
    DesignCity("Varanasi",           "India",    83.0,  44.4, 27.8,  5.0,  28.3, 35.6),
    DesignCity("Dehradun",           "India",   699.0,  38.3, 22.2,  2.8,  21.7, 30.0),
    DesignCity("Srinagar",           "India",  1587.0,  34.4, 20.0,-11.1,  16.1, 27.2),
    // ── India — West ──────────────────────────────────────────────────────────────
    DesignCity("Mumbai",             "India",    11.0,  34.4, 27.2, 16.7,  28.9, 32.2),
    DesignCity("Pune",               "India",   559.0,  39.4, 22.8,  8.9,  21.7, 27.8),
    DesignCity("Nagpur",             "India",   312.0,  43.9, 26.7,  8.9,  25.6, 31.7),
    DesignCity("Nashik",             "India",   565.0,  40.6, 22.8,  6.7,  21.7, 28.9),
    DesignCity("Ahmedabad",          "India",    55.0,  43.3, 26.7,  5.6,  26.7, 35.0),
    DesignCity("Surat",              "India",    13.0,  38.3, 28.3, 12.2,  28.3, 32.8),
    DesignCity("Vadodara",           "India",    39.0,  43.3, 26.7,  7.2,  27.2, 34.4),
    DesignCity("Rajkot",             "India",   138.0,  42.2, 25.6,  5.6,  25.6, 33.9),
    // ── India — South ─────────────────────────────────────────────────────────────
    DesignCity("Bangalore",          "India",   921.0,  34.4, 21.7,  9.4,  21.1, 27.8),
    DesignCity("Chennai",            "India",    16.0,  39.4, 28.3, 22.2,  27.8, 35.0),
    DesignCity("Hyderabad",          "India",   541.0,  40.6, 24.4, 10.6,  24.4, 31.7),
    DesignCity("Visakhapatnam",      "India",     3.0,  37.8, 28.9, 18.3,  27.2, 33.3),
    DesignCity("Vijayawada",         "India",    26.0,  42.2, 28.9, 15.0,  28.3, 34.4),
    DesignCity("Kochi",              "India",     3.0,  35.0, 27.8, 22.8,  28.3, 31.7),
    DesignCity("Thiruvananthapuram", "India",    64.0,  36.7, 27.2, 21.1,  27.8, 32.2),
    DesignCity("Coimbatore",         "India",   417.0,  38.3, 24.4, 13.3,  23.9, 31.7),
    DesignCity("Madurai",            "India",   101.0,  40.6, 26.7, 19.4,  26.7, 33.9),
    DesignCity("Mangalore",          "India",    22.0,  35.0, 27.8, 20.0,  27.8, 31.7),
    DesignCity("Mysore",             "India",   763.0,  35.6, 22.2, 10.0,  19.4, 27.2),
    DesignCity("Hubli",              "India",   672.0,  37.8, 23.3,  9.4,  22.2, 30.0),
    // ── India — East & Central ────────────────────────────────────────────────────
    DesignCity("Kolkata",            "India",     6.0,  37.8, 28.9, 10.6,  29.4, 33.3),
    DesignCity("Bhubaneswar",        "India",    45.0,  41.7, 28.9, 11.7,  27.8, 33.9),
    DesignCity("Patna",              "India",    53.0,  42.8, 28.3,  5.0,  27.8, 34.4),
    DesignCity("Ranchi",             "India",   651.0,  38.9, 25.6,  8.3,  24.4, 31.1),
    DesignCity("Guwahati",           "India",    55.0,  37.2, 28.9,  8.3,  27.2, 31.7),
    DesignCity("Bhopal",             "India",   523.0,  42.2, 25.6,  5.6,  24.4, 31.1),
    DesignCity("Indore",             "India",   553.0,  41.7, 24.4,  7.2,  23.3, 30.6),
    DesignCity("Jabalpur",           "India",   412.0,  43.9, 26.1,  6.1,  25.6, 31.7),
    DesignCity("Raipur",             "India",   298.0,  43.3, 27.2,  9.4,  26.7, 33.3),
    // ── Asia ─────────────────────────────────────────────────────────────────────
    DesignCity("Singapore",          "Singapore", 16.0, 34.4, 27.8, 23.3,  26.7, 32.2),
    DesignCity("Bangkok",            "Thailand",   7.0, 37.2, 28.3, 19.4,  26.7, 33.3),
    DesignCity("Hong Kong",          "China",     33.0, 33.3, 27.8, 10.0,  27.2, 31.7),
    DesignCity("Shanghai",           "China",      4.0, 36.7, 27.8, -2.2,  25.6, 33.3),
    DesignCity("Beijing",            "China",     54.0, 35.0, 26.1,-11.7,  24.4, 29.4),
    DesignCity("Tokyo",              "Japan",      8.0, 36.7, 27.8, -0.6,  24.4, 31.1),
    // ── Australia / NZ ────────────────────────────────────────────────────────────
    DesignCity("Sydney",             "Australia",  6.0, 33.3, 22.8,  5.6,  19.4, 27.8),
    DesignCity("Melbourne",          "Australia", 31.0, 38.3, 19.4,  4.4,  15.6, 26.7),
    DesignCity("Brisbane",           "Australia",  5.0, 34.4, 25.6,  6.1,  22.8, 29.4),
)
