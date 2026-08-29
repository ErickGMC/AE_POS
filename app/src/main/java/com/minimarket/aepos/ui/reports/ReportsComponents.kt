package com.minimarket.aepos.ui.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimarket.aepos.data.repository.SaleRepository
import com.minimarket.aepos.domain.model.PaymentMethod
import com.minimarket.aepos.domain.model.Sale
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportsUiState(
    val sales: List<Sale> = emptyList(),
    val selectedSale: Sale? = null,
    val totalRevenue: Double = 0.0,
    val cashTotal: Double = 0.0,
    val digitalTotal: Double = 0.0,
    val totalTickets: Int = 0
)

class ReportsViewModel(
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        observeSales()
    }

    private fun observeSales() {
        viewModelScope.launch {
            saleRepository.allSalesFlow.collect { list ->
                val activeSales = list.filterNot { it.anulado }
                val totalRev = activeSales.sumOf { it.total }
                val cash = activeSales.filter { it.metodoPago == PaymentMethod.EFECTIVO }.sumOf { it.total }
                val digital = activeSales.filter { it.metodoPago != PaymentMethod.EFECTIVO }.sumOf { it.total }

                _uiState.update {
                    it.copy(
                        sales = list,
                        totalRevenue = Math.round(totalRev * 100.0) / 100.0,
                        cashTotal = Math.round(cash * 100.0) / 100.0,
                        digitalTotal = Math.round(digital * 100.0) / 100.0,
                        totalTickets = activeSales.size
                    )
                }
            }
        }
    }

    fun selectSale(sale: Sale) {
        _uiState.update { it.copy(selectedSale = sale) }
    }

    fun clearSelectedSale() {
        _uiState.update { it.copy(selectedSale = null) }
    }

    fun cancelSale(saleId: String) {
        viewModelScope.launch {
            saleRepository.cancelSale(saleId)
        }
    }
}

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    currentUser: User,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(14.dp)
    ) {
        Text(
            text = "Historial & Reportes",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            color = Color.White
        )
        Text(
            text = "${state.totalTickets} ventas concretadas hoy",
            style = MaterialTheme.typography.bodySmall,
            color = Slate400
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Tarjetas de Métricas Resumen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Total Vendido",
                value = "S/ %.2f".format(state.totalRevenue),
                icon = Icons.Default.Paid,
                accentColor = Emerald400,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Efectivo",
                value = "S/ %.2f".format(state.cashTotal),
                icon = Icons.Default.AttachMoney,
                accentColor = Teal400,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Digital (Yape)",
                value = "S/ %.2f".format(state.digitalTotal),
                icon = Icons.Default.QrCode2,
                accentColor = Purple400,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Tickets Emitidos (${state.sales.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (state.sales.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aún no hay ventas emitidas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.sales, key = { it.id }) { sale ->
                    SaleHistoryCard(
                        sale = sale,
                        onClick = { viewModel.selectSale(sale) }
                    )
                }
            }
        }
    }

    // Modal de Detalle de Venta
    state.selectedSale?.let { sale ->
        SaleDetailDialog(
            sale = sale,
            canVoid = currentUser.hasPermission(User.PERM_SALES_WRITE) || currentUser.isAdmin,
            onDismiss = { viewModel.clearSelectedSale() },
            onVoidSale = { viewModel.cancelSale(sale.id) }
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(1.dp, Slate800)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Slate400
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun SaleHistoryCard(
    sale: Sale,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(1.dp, Slate800),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sale.numeroComprobante,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (sale.anulado) Red900.copy(alpha = 0.3f) else Emerald700.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (sale.anulado) Red500.copy(alpha = 0.4f) else Emerald500.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (sale.anulado) "ANULADO" else sale.metodoPago.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = if (sale.anulado) Red400 else Emerald300,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${sale.fecha} • ${sale.clienteNombre ?: "Público General"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "S/ %.2f".format(sale.total),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    ),
                    color = if (sale.anulado) Slate500 else Emerald400
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

