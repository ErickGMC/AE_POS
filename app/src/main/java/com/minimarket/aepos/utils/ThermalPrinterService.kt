package com.minimarket.aepos.utils

import com.minimarket.aepos.domain.model.Sale
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Servicio generador de comandos ESC/POS estándar para impresoras térmicas (58mm y 80mm)
 * comúnmente utilizadas en puntos de venta (Bluetooth / USB / Red).
 */
object ThermalPrinterService {

    private val ESC: Byte = 0x1B
    private val GS: Byte = 0x1D

    /**
     * Construye los bytes ESC/POS para imprimir un ticket de venta completo.
     * @param sale La venta a imprimir
     * @param businessName Nombre comercial de la tienda
     * @param ruc RUC o identificación tributaria
     * @param address Dirección física de la tienda
     * @param paperWidthCols 32 columnas para rollo de 58mm, 48 columnas para rollo de 80mm
     */
    fun buildSaleTicketBytes(
        sale: Sale,
        businessName: String = "MINIMARKET FLOR",
        ruc: String = "RUC: 10458923411",
        address: String = "Av. Principal 123 - Lima",
        paperWidthCols: Int = 32
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val charset = Charset.forName("CP437")

        fun write(vararg bytes: Byte) = out.write(bytes)
        fun writeText(text: String) = out.write(text.toByteArray(charset))
        fun line(text: String = "") = writeText("$text\n")

        // 1. Inicializar Impresora (ESC @)
        write(ESC, '@'.code.toByte())

        // 2. Encabezado Centrado (ESC a 1)
        write(ESC, 'a'.code.toByte(), 1)
        write(ESC, 'E'.code.toByte(), 1) // Negrita ON
        line(businessName)
        write(ESC, 'E'.code.toByte(), 0) // Negrita OFF
        line(ruc)
        line(address)
        line("--------------------------------")

        // 3. Datos del Comprobante (Alineado Izquierda ESC a 0)
        write(ESC, 'a'.code.toByte(), 0)
        line("TICKET: ${sale.numeroComprobante}")
        line("FECHA : ${sale.fecha}")
        line("PAGO  : ${sale.metodoPago.label}")
        if (!sale.clienteNombre.isNullOrBlank()) {
            line("CLIENTE: ${sale.clienteNombre}")
        }
        line("--------------------------------")

        // 4. Detalle de Ítems
        line("CANT. DESCRIPCION        TOTAL")
        line("--------------------------------")
        for (item in sale.items) {
            val cantStr = if (item.cantidad % 1.0 == 0.0) "${item.cantidad.toInt()}x" else "%.3fx".format(item.cantidad)
            val subtotalStr = "S/%.2f".format(item.subtotal)
            val name = item.productoNombre.take(18)

            val leftPart = "$cantStr $name".padEnd(22, ' ')
            val lineStr = (leftPart + subtotalStr.padStart(10, ' ')).take(paperWidthCols)
            line(lineStr)
        }
        line("--------------------------------")

        // 5. Total (Grande y Centrado)
        write(ESC, 'a'.code.toByte(), 2) // Alineado Derecha
        write(ESC, 'E'.code.toByte(), 1) // Negrita ON
        line("TOTAL: S/ %.2f".format(sale.total))
        write(ESC, 'E'.code.toByte(), 0) // Negrita OFF

        // 6. Pie de Página
        write(ESC, 'a'.code.toByte(), 1) // Centrado
        line()
        line("¡Gracias por su compra!")
        line("Conserve su comprobante")
        line()
        line()
        line()

        // 7. Corte de Papel (GS V 66 0)
        write(GS, 'V'.code.toByte(), 66, 0)

        return out.toByteArray()
    }
}
