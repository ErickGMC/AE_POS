package com.minimarket.aepos.utils

import android.view.KeyEvent

/**
 * Receptor desacoplado para pistolas y escáneres de código de barras físicos (USB-OTG o Bluetooth HID).
 * Los lectores de hardware emulan un teclado ingresando los caracteres a alta velocidad y terminando con ENTER.
 */
class BarcodeHardwareReceiver(
    private val onBarcodeScanned: (String) -> Unit
) {
    private val buffer = StringBuilder()
    private var lastKeyTimestamp = 0L

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()

        // Si pasaron más de 1.5 segundos entre teclas, reiniciar buffer (evitar residuos de tipeo manual)
        if (currentTime - lastKeyTimestamp > 1500) {
            buffer.setLength(0)
        }
        lastKeyTimestamp = currentTime

        if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
            val code = buffer.toString().trim()
            buffer.setLength(0)
            if (code.length >= 3) {
                onBarcodeScanned(code)
                return true
            }
            return false
        }

        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0 && unicodeChar.toChar().isLetterOrDigit() || unicodeChar.toChar() in "-_#.") {
            buffer.append(unicodeChar.toChar())
            return false
        }

        return false
    }
}
