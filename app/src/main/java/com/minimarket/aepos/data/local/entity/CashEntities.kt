package com.minimarket.aepos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "cajas_turnos",
    indices = [
        androidx.room.Index(value = ["sincronizado"])
    ]
)
data class CashShiftEntity(
    @PrimaryKey
    val id: String,
    val fechaApertura: String,
    val fechaCierre: String? = null,
    val montoInicial: Double,
    val totalVentasEfectivo: Double = 0.0,
    val totalVentasDigital: Double = 0.0,
    val totalIngresos: Double = 0.0,
    val totalEgresos: Double = 0.0,
    val montoFinalReal: Double? = null,
    val diferencia: Double? = null,
    val estado: String = "abierta", // "abierta" | "cerrada"
    val cajero: String = "Cajero Principal",
    val observaciones: String? = null,
    val sincronizado: Int = 1
) {
    val montoEsperado: Double
        get() = Math.round((montoInicial + totalVentasEfectivo + totalIngresos - totalEgresos) * 100.0) / 100.0
}

@Entity(
    tableName = "cajas_movimientos",
    indices = [
        androidx.room.Index(value = ["sincronizado"]),
        androidx.room.Index(value = ["turnoId"])
    ]
)
data class CashMovementEntity(
    @PrimaryKey
    val id: String,
    val turnoId: String,
    val tipo: String, // "ingreso" | "egreso"
    val monto: Double,
    val motivo: String,
    val fecha: String,
    val sincronizado: Int = 1
)
