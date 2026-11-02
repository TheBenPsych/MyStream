package com.github.damontecres.wholphin.ui.main

/*
 * Benflix Home: the numbers that tune how Home loads (how many items per row, how long to wait,
 * how long saved lists are kept). Change them here to adjust Home without touching the loading code.
 */

internal const val COME_BACK_DAYS = 21L
internal const val COME_BACK_POOL = 60
internal const val SPOTLIGHT_POOL = 30
internal const val SPOTLIGHT_TOP_BILLED = 5
internal const val SPOTLIGHT_CHOICES = 5
internal const val TASTE_MAX = 300
internal const val TASTE_CACHE_MS = 20_000L
internal const val ANIME_ID_CACHE_MS = 60 * 60 * 1000L
internal const val ANIME_ROW_MIN = 5
internal const val ANIME_SIMILAR_FETCH = 40
internal const val ANIME_GEM_MIN_RATING = 7.5
internal const val ANIME_GENRE_ROWS = 2
internal const val BECAUSE_LOOKBACK = 30
internal const val BECAUSE_TRIES = 6
internal const val EXTRA_ROW_MAX = 20
internal const val GENRE_ROWS = 3
internal const val GENRE_MIN = 6
internal const val TRENDING_MIN = 5
internal const val FEATURED_NEWEST = 3
internal const val FEATURED_RESUME = 2
internal const val FEATURED_BECAUSE = 2
internal const val FEATURED_FAVORITES = 2
internal const val FEATURED_MAX = 10
internal const val FEATURED_SOURCE_TIMEOUT_MS = 3_000L
internal const val KIDS_AGAIN_MIN = 3
internal const val KIDS_AGAIN_MAX = 20
internal const val TRENDING_CACHE_MS = 30 * 60 * 1000L
internal const val ON_THIS_DAY_POOL = 3000
internal const val FEATURED_PICKS = 3
internal const val TRENDING_PAGES = 3
internal const val TRENDING_TOP = 10
internal const val THEMED_MAX = 20
internal const val THEMED_FETCH = 60

/** How many name searches the themed row runs at once */
internal const val THEMED_PARALLEL = 6
internal const val COLLECTION_INDEX_MAX = 400
internal const val COLLECTION_INDEX_PARALLEL = 6
internal const val BECAUSE_MAX = 20
internal const val BECAUSE_MIN = 4
internal const val COLLECTIONS_FETCH = 100
internal const val COLLECTIONS_MAX = 20
internal const val COLLECTIONS_MIN = 3
internal const val TRAILER_DELAY_MS = 1_500L
internal const val TONIGHT_FROM_HOUR = 18
internal const val TONIGHT_MIN_RATING = 7.0
internal const val TONIGHT_POOL = 200
internal const val TONIGHT_FETCH = 4
internal const val SECOND_WAVE_MAX_WAIT_MS = 2_500L
internal const val SLOW_ROWS_REFRESH_MS = 10 * 60 * 1000L
internal const val THIRD_WAVE_DELAY_MS = 400L
internal const val COLLECTION_INDEX_MAX_AGE_MS = 24 * 60 * 60 * 1000L
internal const val HOME_SNAPSHOT_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
internal const val SNAPSHOT_SAVE_DELAY_MS = 15_000L
// Tiles per row kept in the saved Home. Fewer = faster to read when the app opens (reading 20 per row
// took over 3 s on an older TV); the full rows replace them a moment later anyway.
internal const val SNAPSHOT_ROW_MAX = 10
internal const val HOME_SNAPSHOT_FORMAT = "f1"
