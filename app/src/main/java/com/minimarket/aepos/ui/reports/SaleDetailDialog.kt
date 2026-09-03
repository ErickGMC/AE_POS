package com.minimarket.aepos.ui.reports

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.minimarket.aepos.domain.model.Sale
import com.minimarket.aepos.ui.theme.*

@Composable
fun SaleDetailDialog(
    sale: Sale,
    canVoid: Boolean,
    onDismiss: () -> Unit,
    onVoidSale: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = sale.numeroComprobante,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = Color.White
                        )
                        Text(
                            text = "${sale.fecha} • ${sale.metodoPago.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate700)

                // Cliente
                if (!sale.clienteNombre.isNullOrBlank() || !sale.clienteDocumento.isNullOrBlank()) {
                    Text(
                        text = "Cliente: ${sale.clienteNombre ?: "General"} (${sale.clienteDocumento ?: "-"})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Slate300
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Lista de Items
                Text(
                    text = "Detalle del Ticket (${sale.items.size} ítems):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sale.items) { item ->
                        Surface(
                            color = Slate850,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val prodName = item.productoNombre.ifBlank { item.productoId }
                                    Text(
                                        text = "${if (item.cantidad % 1.0 == 0.0) item.cantidad.toInt().toString() else item.cantidad.toString()}x $prodName",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "S/ %.2f c/u".format(item.precioUnitario),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400
                                    )
                                }
                                Text(
                                    text = "S/ %.2f".format(item.subtotal),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                                    color = Emerald400
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate700)

                // Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOTAL VENTA:",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "S/ %.2f".format(sale.total),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        color = if (sale.anulado) Red400 else Emerald400
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Botones de acción: Compartir por WhatsApp y Anular
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val itemsFormatted = if (sale.items.isNotEmpty()) {
                                sale.items.joinToString("\n") { itm ->
                                    val cant = if (itm.cantidad % 1.0 == 0.0) itm.cantidad.toInt().toString() else itm.cantidad.toString()
                                    val nm = itm.productoNombre.ifBlank { itm.productoId }
                                    "• $cant x $nm — S/ ${"%.2f".format(itm.subtotal)}"
                                } + "\n--------------------------------"
                            } else ""

                            val ticketText = """
                                🧾 *MINIMARKET FLOR - TICKET ELECTRÓNICO*
                                📋 *Comprobante:* ${sale.numeroComprobante}
                                📅 *Fecha:* ${sale.fecha}
                                💳 *Método de Pago:* ${sale.metodoPago.label}
                                --------------------------------
                                $itemsFormatted
                                💵 *TOTAL:* S/ ${"%.2f".format(sale.total)}
                                
                                ¡Muchas gracias por su preferencia!
                            """.trimIndent()

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, ticketText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Enviar Comprobante WhatsApp"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!sale.anulado && canVoid) {
                        OutlinedButton(
                            onClick = {
                                onVoidSale()
                                onDismiss()
                            },
                            border = BorderStroke(1.dp, Red500.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Red400, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Anular", color = Red400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

