package com.izpaes.powerblesmart

object HeartRateParser {
    fun parse(b:ByteArray):Int? {
        if(b.isEmpty()) return null
        val flags=b[0].toInt() and 0xff
        return if((flags and 1)==0) {
            if(b.size<2) null else b[1].toInt() and 0xff
        } else {
            if(b.size<3) null else (b[1].toInt() and 0xff) or ((b[2].toInt() and 0xff) shl 8)
        }
    }
}
