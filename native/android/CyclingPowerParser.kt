package com.izpaes.powerblesmart

object CyclingPowerParser {
    fun parse(b:ByteArray):Int? {
        if(b.size<4) return null
        // Flags (2 bytes) + instantaneous power sint16 (2 bytes)
        val lo=b[2].toInt() and 0xff
        val hi=b[3].toInt()
        return (lo or (hi shl 8))
    }
}
