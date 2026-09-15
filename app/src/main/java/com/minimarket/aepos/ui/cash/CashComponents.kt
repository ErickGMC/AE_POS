package com.minimarket.aepos.ui.cash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimarket.aepos.data.local.entity.CashMovementEntity
import com.minimarket.aepos.data.local.entity.CashShiftEntity
import com.minimarket.aepos.data.repository.CashRepository
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.ui.reports.MetricCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CashUiState(
    val activeShift: CashShiftEntity? = null,
    val movements: List<CashMovementEntity> = emptyList(),
    val isOpenShiftOpen: Boolean = false,
    val isCloseShiftOpen: Boolean = false,
    val isMovementOpen: Boolean = false,
    val movementType: String = "ingreso" // "ingreso" | "egreso"
)

class CashViewModel(
    private val cashRepository: CashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashUiState())
    val uiState: StateFlow<CashUiState> = _uiState.asStateFlow()

    init {
        observeActiveShift()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveShift() {
        viewModelScope.launch {
            cashRepository.activeShiftFlow.collect { shift ->
                _uiState.update { it.copy(activeShift = shift) }
            }
        }

        viewModelScope.launch {
            cashRepository.activeShiftFlow
                .flatMapLatest { shift ->
                    if (shift != null) {
                        cashRepository.getMovementsFlow(shift.id)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { movs ->
                    _uiState.update { it.copy(movements = movs) }
                }
        }
    }

    fun openStartShiftDialog() {
        _uiState.update { it.copy(isOpenShiftOpen = true) }
    }

    fun closeStartShiftDialog() {
        _uiState.update { it.copy(isOpenShiftOpen = false) }
    }

    fun openCloseShiftDialog() {
        _uiState.update { it.copy(isCloseShiftOpen = true) }
    }

    fun closeCloseShiftDialog() {
        _uiState.update { it.copy(isCloseShiftOpen = false) }
    }

    fun openMovementDialog(type: String) {
        _uiState.update { it.copy(isMovementOpen = true, movementType = type) }
    }

    fun closeMovementDialog() {
        _uiState.update { it.copy(isMovementOpen = false) }
    }

    fun startShift(initialAmount: Double, cashier: String) {
        viewModelScope.launch {
            cashRepository.openShift(initialAmount, cashier)
            closeStartShiftDialog()
        }
    }

    fun submitCloseShift(finalRealAmount: Double, notes: String?) {
        viewModelScope.launch {
            val shift = _uiState.value.activeShift ?: return@launch
            cashRepository.closeShift(shift.id, finalRealAmount, notes)
            closeCloseShiftDialog()
        }
    }

    fun submitMovement(amount: Double, reason: String) {
        viewModelScope.launch {
            val shift = _uiState.value.activeShift ?: return@launch
            cashRepository.registerMovement(shift.id, _uiState.value.movementType, amount, reason)
            closeMovementDialog()
        }
    }
}

@Composable
fun CashScreen(
    viewModel: CashViewModel,
    currentUser: User? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Control de Caja",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (state.activeShift != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.activeShift != null) "Caja Abierta • ${state.activeShift?.cajero}" else "Caja Cerrada",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.activeShift != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (state.activeShift == null) {
                Button(
                    onClick = { viewModel.openStartShiftDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Abrir Caja", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = { viewModel.openCloseShiftDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cerrar Caja", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        state.activeShift?.let { shift ->
            // Métricas de la Caja Actual Material 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Monto Inicial",
                    value = "S/ %.2f".format(shift.montoInicial),
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Ventas Efectivo",
                    value = "S/ %.2f".format(shift.totalVentasEfectivo),
                    icon = Icons.Default.AttachMoney,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Total en Caja",
                    value = "S/ %.2f".format(shift.montoEsperado),
                    icon = Icons.Default.Paid,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botones de Movimientos Material 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { viewModel.openMovementDialog("ingreso") },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Ingreso Efectivo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Surface(
                    onClick = { viewModel.openMovementDialog("egreso") },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.RemoveCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("- Egreso / Gasto", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Movimientos del Turno (${state.movements.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.movements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay movimientos registrados en este turno", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.movements, key = { it.id }) { mov ->
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = mov.motivo,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mov.fecha,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${if (mov.tipo == "ingreso") "+" else "-"} S/ %.2f".format(mov.monto),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = if (mov.tipo == "ingreso") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "La caja se encuentra cerrada",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Abre un nuevo turno para registrar ventas y movimientos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Modal Apertura
    if (state.isOpenShiftOpen) {
        OpenCashDialog(
            defaultCashier = currentUser?.nombreCompleto ?: "Cajero",
            onDismiss = { viewModel.closeStartShiftDialog() },
            onConfirm = { initial, cashier -> viewModel.startShift(initial, cashier) }
        )
    }

    // Modal Cierre
    if (state.isCloseShiftOpen) {
        state.activeShift?.let { shift ->
            CloseCashDialog(
                expectedAmount = shift.montoEsperado,
                onDismiss = { viewModel.closeCloseShiftDialog() },
                onConfirm = { real, notes -> viewModel.submitCloseShift(real, notes) }
            )
        }
    }

    // Modal Movimiento
    if (state.isMovementOpen) {
        CashMovementDialog(
            type = state.movementType,
            onDismiss = { viewModel.closeMovementDialog() },
            onConfirm = { amount, reason -> viewModel.submitMovement(amount, reason) }
        )
    }
}

@Composable
fun OpenCashDialog(
    defaultCashier: String = "Cajero",
    onDismiss: () -> Unit,
    onConfirm: (initialAmount: Double, cashier: String) -> Unit
) {
    var initialStr by remember { mutableStateOf("0.00") }
    var cashier by remember { mutableStateOf(defaultCashier) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Apertura de Caja / Turno",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = initialStr,
                    onValueChange = { initialStr = it },
                    label = { Text("Monto Inicial en Efectivo (S/)") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = MaterialTheme.colorScheme.primary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cashier,
                    onValueChange = { cashier = it },
                    label = { Text("Nombre del Cajero") },
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.secondary) },
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            onConfirm(initialStr.toDoubleOrNull() ?: 0.0, cashier)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Abrir Turno", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CloseCashDialog(
    expectedAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (realAmount: Double, notes: String?) -> Unit
) {
    var realStr by remember { mutableStateOf("%.2f".format(expectedAmount)) }
    var notes by remember { mutableStateOf("") }

    val real = realStr.toDoubleOrNull() ?: 0.0
    val diff = real - expectedAmount

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Cierre de Caja y Arqueo (Z)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "MONTO ESPERADO EN SISTEMA:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "S/ %.2f".format(expectedAmount),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = realStr,
                    onValueChange = { realStr = it },
                    label = { Text("Efectivo Real Contado (S/)") },
                    leadingIcon = { Icon(Icons.Default.Paid, null, tint = MaterialTheme.colorScheme.primary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (diff == 0.0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else if (diff > 0) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, if (diff == 0.0) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else if (diff > 0) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (diff == 0.0) "Caja Cuadrada" else if (diff > 0) "Sobrante:" else "Faltante:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Diferencia: ${if (diff >= 0) "+S/ " else "-S/ "}%.2f".format(Math.abs(diff)),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                            color = if (diff == 0.0) MaterialTheme.colorScheme.primary else if (diff > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones (Opcional)") },
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { onConfirm(real, notes.takeIf { it.isNotBlank() }) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirmar Cierre", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CashMovementDialog(
    type: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, reason: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (type == "ingreso") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = if (type == "ingreso") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (type == "ingreso") "Nuevo Ingreso de Efectivo" else "Nuevo Egreso / Gasto",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Monto (S/) *") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = if (type == "ingreso") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo / Concepto *") },
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val quickReasons = if (type == "ingreso") {
                    listOf("Aporte Capital", "Cambio Inicial", "Cobro Pendiente", "Otros")
                } else {
                    listOf("Pago Proveedor", "Insumos / Bolsas", "Servicios", "Adelanto Sueldo", "Limpieza", "Varios")
                }

                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickReasons) { item ->
                        val isSelected = reason.equals(item, ignoreCase = true)
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isSelected) {
                                if (type == "ingreso") MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                            } else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    if (type == "ingreso") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                } else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable { reason = item }
                        ) {
                            Text(
                                text = item,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    if (type == "ingreso") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val canSave = (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 && reason.isNotBlank()
                    Button(
                        onClick = { onConfirm(amountStr.toDoubleOrNull() ?: 0.0, reason) },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "ingreso") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Registrar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
