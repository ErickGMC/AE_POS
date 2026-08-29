package com.minimarket.aepos.data.local.dao

import androidx.room.*
import com.minimarket.aepos.data.local.entity.CashMovementEntity
import com.minimarket.aepos.data.local.entity.CashShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashDao {
    @Query("SELECT * FROM cajas_turnos WHERE estado = 'abierta' ORDER BY fechaApertura DESC LIMIT 1")
    fun getActiveShiftFlow(): Flow<CashShiftEntity?>

    @Query("SELECT * FROM cajas_turnos WHERE estado = 'abierta' ORDER BY fechaApertura DESC LIMIT 1")
    suspend fun getActiveShift(): CashShiftEntity?

    @Query("SELECT * FROM cajas_turnos ORDER BY fechaApertura DESC")
    fun getAllShiftsFlow(): Flow<List<CashShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateShift(shift: CashShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: CashMovementEntity)

    @Query("SELECT * FROM cajas_movimientos WHERE turnoId = :shiftId ORDER BY fecha DESC")
    fun getMovementsForShiftFlow(shiftId: String): Flow<List<CashMovementEntity>>

    @Query("UPDATE cajas_turnos SET totalVentasEfectivo = totalVentasEfectivo + :amount WHERE id = :shiftId")
    suspend fun addCashSale(shiftId: String, amount: Double)

    @Query("UPDATE cajas_turnos SET totalVentasDigital = totalVentasDigital + :amount WHERE id = :shiftId")
    suspend fun addDigitalSale(shiftId: String, amount: Double)

    @Query("UPDATE cajas_turnos SET totalIngresos = totalIngresos + :amount WHERE id = :shiftId")
    suspend fun addIncome(shiftId: String, amount: Double)

    @Query("UPDATE cajas_turnos SET totalEgresos = totalEgresos + :amount WHERE id = :shiftId")
    suspend fun addExpense(shiftId: String, amount: Double)
}
