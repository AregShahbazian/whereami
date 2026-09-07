package com.mby4m.whereami

/**
 * Release builds have no demo override — the app always shows the real fix.
 *
 * The debug source set replaces this with a fixed made-up reading, so store
 * screenshots never contain a real location. Keeping the two implementations in
 * separate source sets means the shipped binary contains no demo data at all and
 * R8 strips the branch entirely.
 */
object DemoLocation {
    fun fix(): Fix? = null
}
