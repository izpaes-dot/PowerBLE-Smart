package com.izpaes.powerblesmart

object CscParser {
    fun parse(b: ByteArray): Int? {
        if (b.size < 5) return null
        val flags=b[0].toInt() and 0xff
        var i=1
        var lastCrank:Int?=null
        var lastTime:Int?=null
        if((flags and 0x02)!=0) {
            if(i+3>=b.size) return null
            i+=4
            if(i+1>=b.size) return null
            lastCrank=(b[i].toInt() and 0xff) or ((b[i+1].toInt() and 0xff) shl 8)
            i+=2
            if(i+1>=b.size) return null
            lastTime=(b[i].toInt() and 0xff) or ((b[i+1].toInt() and 0xff) shl 8)
        }
        // O pacote CSC traz contagem/tempo; para uma medição robusta, o cálculo
        // temporal deve usar o estado anterior. Este parser inicial expõe a rotação
        // como evento de crank para a camada superior.
        return if(lastCrank != null) lastCrank else null
    }
}
