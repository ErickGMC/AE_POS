package com.minimarket.aepos.ui.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimarket.aepos.domain.model.PaymentMethod
import com.minimarket.aepos.ui.components.*
import com.minimarket.aepos.ui.scanner.BarcodeScannerModal
import com.minimarket.aepos.ui.theme.*

@Composable
fun TabletSalesScreen(
    viewModel: SalesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // PANEL IZQUIERDO: Catálogo de Productos y Búsqueda (60% del ancho)
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Buscador con botón de escáner de cámara
            SearchBarField(
                query = state.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onScanBarcodeClick = { viewModel.openScanner() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Categorías
            CategoryFilterRow(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Grilla de Productos (3 columnas en Tablet)
            if (state.products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron productos",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            onClick = { viewModel.addToCart(product) }
                        )
                    }
                }
            }
        }

        // DIVISOR VERTICAL
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )

        // PANEL DERECHO: Ticket / Carrito y Cobro Rápido (40% del ancho)
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Encabezado del Carrito
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = Emerald400
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ticket de Venta",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state.cart.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearCart() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = Red500,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vaciar", color = Red500)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                // Lista de Items en el Ticket
                if (state.cart.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "El carrito está vacío",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.cart, key = { it.product.id }) { item ->
                            CartItemRow(
                                item = item,
                                onIncrement = { viewModel.addToCart(item.product) },
                                onDecrement = { viewModel.decrementCartItem(item.product.id) },
                                onRemove = { viewModel.removeFromCart(item.product.id) }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                // Selector de Métodos de Pago
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Método de Pago:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { viewModel.openPagosMixtos() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pago Mixto", color = Emerald400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PaymentMethod.entries) { method ->
                        val isSelected = state.selectedPaymentMethod == method
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.setPaymentMethod(method) }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Emerald500 else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            color = if (isSelected) Emerald500.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = method.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) Emerald400 else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Cálculo de Vuelto para Efectivo
                if (state.selectedPaymentMethod == PaymentMethod.EFECTIVO && state.cart.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.cashReceived,
                            onValueChange = { viewModel.setCashReceived(it) },
                            label = { Text("Recibido (S/)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        // Pastillas de billetes rápidos
                        listOf(10.0, 20.0, 50.0, 100.0).forEach { note ->
                            if (note >= state.total) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setCashReceived(note.toInt().toString()) },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "S/${note.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald400,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.changeAmount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "VUELTO AL CLIENTE:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Amber500
                            )
                            Text(
                                text = "S/ %.2f".format(state.changeAmount),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Amber500
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Resumen de Total
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL:",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "S/ %.2f".format(state.total),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón Cobrar / Completar Venta
                val canSubmit = state.cart.isNotEmpty() && !state.isProcessing &&
                        (state.selectedPaymentMethod != PaymentMethod.EFECTIVO || (state.cashReceived.toDoubleOrNull() ?: 0.0) >= state.total)

                Button(
                    onClick = { viewModel.processSale() },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "COBRAR VENTA (M001)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // Modal de Escáner de Código de Barras por Cámara (Google ML Kit)
    if (state.isScannerOpen) {
        BarcodeScannerModal(
            onBarcodeScanned = { code ->
                viewModel.onBarcodeScanned(code)
            },
            onDismiss = { viewModel.closeScanner() }
        )
    }

    // Modal de Pagos Mixtos
    if (state.isPagosMixtosOpen) {
        PagosMixtosDialog(
            totalVenta = state.total,
            onDismiss = { viewModel.closePagosMixtos() },
            onConfirm = { pagos -> viewModel.processPagoMixtoSale(pagos) }
        )
    }

    // Modal de Éxito
    state.lastSuccessComprobante?.let { comprobante ->
        SaleSuccessDialog(
            comprobante = comprobante,
            onDismiss = { viewModel.dismissSuccessDialog() }
        )
    }
}
