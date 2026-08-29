package com.minimarket.aepos.ui.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.minimarket.aepos.ui.theme.*

data class PagoParcial(
    val metodo: String,
    val monto: Double
)

@Composable
fun PagosMixtosDialog(
    totalVenta: Double,
    onDismiss: () -> Unit,
    onConfirm: (List<PagoParcial>) -> Unit
) {
    var montoEfectivo by remember { mutableStateOf("") }
    var montoYape by remember { mutableStateOf("") }
    var montoPlin by remember { mutableStateOf("") }
    var montoTarjeta by remember { mutableStateOf("") }

    val ef = montoEfectivo.toDoubleOrNull() ?: 0.0
    val yp = montoYape.toDoubleOrNull() ?: 0.0
    val pl = montoPlin.toDoubleOrNull() ?: 0.0
    val tj = montoTarjeta.toDoubleOrNull() ?: 0.0

    val totalCubierto = Math.round((ef + yp + pl + tj) * 100.0) / 100.0
    val restante = Math.round((totalVenta - totalCubierto) * 100.0) / 100.0
    val esValido = Math.abs(restante) < 0.01

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pago Dividido / Mixto",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Resumen de Total
                Surface(
                    color = Slate850,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate700),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Total a Pagar:", style = MaterialTheme.typography.titleSmall, color = Slate300)
                        Text(
                            text = "S/ %.2f".format(totalVenta),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            ),
                            color = Emerald400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = Slate700,
                    focusedContainerColor = Slate850,
                    unfocusedContainerColor = Slate850,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Emerald400,
                    unfocusedLabelColor = Slate400
                )

                // Inputs por cada método
                OutlinedTextField(
                    value = montoEfectivo,
                    onValueChange = { montoEfectivo = it },
                    label = { Text("Efectivo (S/)") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Emerald400, modifier = Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = montoYape,
                    onValueChange = { montoYape = it },
                    label = { Text("Yape (S/)") },
                    leadingIcon = { Icon(Icons.Default.QrCode2, null, tint = Purple400, modifier = Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = montoPlin,
                    onValueChange = { montoPlin = it },
                    label = { Text("Plin (S/)") },
                    leadingIcon = { Icon(Icons.Default.QrCode, null, tint = Blue400, modifier = Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = montoTarjeta,
                    onValueChange = { montoTarjeta = it },
                    label = { Text("Tarjeta / POS (S/)") },
                    leadingIcon = { Icon(Icons.Default.CreditCard, null, tint = Amber400, modifier = Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Estado de cobertura
                Surface(
                    color = if (esValido) Emerald700.copy(alpha = 0.2f) else Red900.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (esValido) Emerald500.copy(alpha = 0.4f) else Red500.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (esValido) "Monto Exacto Cubierto" else if (restante > 0) "Falta Cubrir:" else "Excedente:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (esValido) Emerald300 else Red400
                        )
                        Text(
                            text = "S/ %.2f".format(if (esValido) totalCubierto else Math.abs(restante)),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = if (esValido) Emerald400 else Red400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Slate700),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar", color = Slate300)
                    }

                    Button(
                        onClick = {
                            val lista = mutableListOf<PagoParcial>()
                            if (ef > 0) lista.add(PagoParcial("Efectivo", ef))
                            if (yp > 0) lista.add(PagoParcial("Yape", yp))
                            if (pl > 0) lista.add(PagoParcial("Plin", pl))
                            if (tj > 0) lista.add(PagoParcial("Tarjeta", tj))
                            onConfirm(lista)
                        },
                        enabled = esValido,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            disabledContainerColor = Slate800
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirmar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

