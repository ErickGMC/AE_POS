package com.minimarket.aepos.ui.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.minimarket.aepos.domain.model.PaymentMethod
import com.minimarket.aepos.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutModalSheet(
    state: SalesUiState,
    onDismiss: () -> Unit,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onCashReceivedChanged: (String) -> Unit,
    onCustomerInfoChanged: (name: String, doc: String) -> Unit,
    onConfirmSale: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Slate900,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = Slate700
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Completar Cobro",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Tarjeta de Total a Cobrar
            Surface(
                color = Slate850,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Slate700),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TOTAL A COBRAR",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = Slate400,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "S/ %.2f".format(state.total),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 36.sp
                        ),
                        color = Emerald400
                    )
                    Text(
                        text = "${state.totalItemsCount.toInt()} productos en el ticket",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selector de Método de Pago
            Text(
                text = "Selecciona Método de Pago",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Slate300,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PaymentMethod.entries.filter { it != PaymentMethod.MIXTO }) { method ->
                    val isSelected = state.selectedPaymentMethod == method
                    val icon = when (method) {
                        PaymentMethod.EFECTIVO -> Icons.Default.AttachMoney
                        PaymentMethod.YAPE -> Icons.Default.QrCode2
                        PaymentMethod.PLIN -> Icons.Default.QrCode
                        PaymentMethod.TARJETA -> Icons.Default.CreditCard
                        PaymentMethod.TRANSFERENCIA -> Icons.Default.AccountBalance
                        PaymentMethod.MIXTO -> Icons.Default.AccountBalanceWallet
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPaymentMethodSelected(method) },
                        color = if (isSelected) Emerald500.copy(alpha = 0.2f) else Slate850,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Emerald400 else Slate700
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Emerald400 else Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = method.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else Slate400
                            )
                        }
                    }
                }
            }

            // Sección Efectivo con Vuelto y Billetes Rápidos
            if (state.selectedPaymentMethod == PaymentMethod.EFECTIVO) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.cashReceived,
                    onValueChange = onCashReceivedChanged,
                    label = { Text("Efectivo Recibido (S/)") },
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = Emerald400)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = Slate700,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pastillas de montos rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "Exacto" to "%.2f".format(state.total),
                        "S/ 10" to "10",
                        "S/ 20" to "20",
                        "S/ 50" to "50",
                        "S/ 100" to "100"
                    )
                    presets.forEach { (label, value) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCashReceivedChanged(value) },
                            color = Slate800,
                            border = BorderStroke(1.dp, Slate700),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald400
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Resumen de Vuelto
                val received = state.cashReceived.toDoubleOrNull() ?: 0.0
                val isInsufficient = received < state.total && received > 0.0

                Surface(
                    color = if (isInsufficient) Red900.copy(alpha = 0.3f) else Emerald700.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (isInsufficient) Red500.copy(alpha = 0.5f) else Emerald500.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isInsufficient) "Falta recibir:" else "Vuelto a entregar:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = if (isInsufficient) "S/ %.2f".format(state.total - received) else "S/ %.2f".format(state.changeAmount),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            ),
                            color = if (isInsufficient) Red400 else Emerald300
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botón Confirmar Venta
            val isEnabled = !state.isProcessing && (state.selectedPaymentMethod != PaymentMethod.EFECTIVO || (state.cashReceived.toDoubleOrNull() ?: 0.0) >= state.total)

            Button(
                onClick = onConfirmSale,
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald500,
                    disabledContainerColor = Slate800
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(if (isEnabled) 8.dp else 0.dp, RoundedCornerShape(16.dp))
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EMITIR COMPROBANTE (M001)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                    )
                }
            }
        }
    }
}

@Composable
fun SaleSuccessDialog(
    comprobante: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Emerald500.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Éxito",
                        tint = Emerald400,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "¡Venta Exitosa!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Comprobante emitido correctamente",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Slate850,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = comprobante,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Emerald400,
                        modifier = Modifier
                            .padding(14.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = "Siguiente Venta",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

