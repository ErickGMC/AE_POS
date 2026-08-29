package com.minimarket.aepos.ui.inventory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.minimarket.aepos.data.repository.StockFilterOption
import com.minimarket.aepos.domain.model.Product
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.ui.components.CategoryFilterRow
import com.minimarket.aepos.ui.components.SearchBarField
import com.minimarket.aepos.ui.scanner.BarcodeScannerModal
import com.minimarket.aepos.ui.theme.*

@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    currentUser: User,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val canEdit = currentUser.isAdmin || currentUser.hasPermission(User.PERM_INVENTORY_WRITE)
    var viewingProductDetail by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Slate950,
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { viewModel.openAddProduct() },
                    containerColor = Emerald500,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.shadow(8.dp, CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Nuevo Producto")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cabecera de Catálogo e Inventario
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (canEdit) "Inventario & Productos" else "Consulta de Precios & Stock",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = Color.White
                    )
                    Text(
                        text = if (canEdit) "${state.products.size} productos en catálogo" else "${state.products.size} productos disponibles para consulta",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                if (!canEdit) {
                    Surface(
                        color = Emerald500.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Modo Consulta",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                        }
                    }
                }
            }

            // Buscador con escáner de código de barras
            SearchBarField(
                query = state.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onScanBarcodeClick = { viewModel.openScanner() },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )

            // Filtros de Stock (Todos, En Stock, Stock Bajo, Agotados)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(StockFilterOption.entries) { filter ->
                    val isSelected = state.selectedStockFilter == filter
                    val label = when (filter) {
                        StockFilterOption.ALL -> "Todos"
                        StockFilterOption.IN_STOCK -> "En Stock (>10)"
                        StockFilterOption.LOW_STOCK -> "Stock Bajo (≤10)"
                        StockFilterOption.OUT_OF_STOCK -> "Agotados (0)"
                    }
                    Surface(
                        onClick = { viewModel.onStockFilterSelected(filter) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Emerald500.copy(alpha = 0.2f) else Slate850,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Emerald400 else Slate700
                        )
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Emerald400 else Slate300,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Filtro por Categorías
            CategoryFilterRow(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )

            // Lista de Productos
            if (state.products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Slate600,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No se encontraron productos con estos filtros",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            canEdit = canEdit,
                            isAdmin = currentUser.isAdmin,
                            onEdit = { viewModel.openEditProduct(product) },
                            onDelete = { viewModel.deleteProduct(product.id) },
                            onViewDetail = { viewingProductDetail = product }
                        )
                    }
                }
            }
        }
    }

    if (viewingProductDetail != null) {
        ProductDetailDialog(
            product = viewingProductDetail!!,
            isAdmin = currentUser.isAdmin,
            onDismiss = { viewingProductDetail = null }
        )
    }

    if (state.isScannerOpen) {
        BarcodeScannerModal(
            onBarcodeScanned = { code -> viewModel.onBarcodeScanned(code) },
            onDismiss = { viewModel.closeScanner() }
        )
    }

    if (state.isAddEditOpen) {
        AddEditProductDialog(
            product = state.editingProduct,
            scannedBarcode = state.scannedBarcodeForForm,
            selectedImageUri = state.selectedImageUri,
            isSaving = state.isSaving,
            onOpenScanner = { viewModel.openScanner() },
            onImageSelected = { uri -> viewModel.onImageSelected(context, uri) },
            onDismiss = { viewModel.closeAddEdit() },
            onSave = { barcode, name, desc, cat, price, cost, stock, unit, dispWeb, dest ->
                viewModel.saveProduct(context, barcode, name, desc, cat, price, cost, stock, unit, dispWeb, dest)
            }
        )
    }

    state.errorMessage?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = Red500
        ) {
            Text(text = error, color = Color.White)
        }
    }
}

@Composable
fun ProductItemCard(
    product: Product,
    canEdit: Boolean,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (canEdit) onEdit() else onViewDetail()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Imagen WebP con Coil
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate850),
                contentAlignment = Alignment.Center
            ) {
                val imageSource = product.imagenLocal ?: product.imagenUrl
                if (!imageSource.isNullOrBlank()) {
                    AsyncImage(
                        model = imageSource,
                        contentDescription = product.nombre,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Información de Producto
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.nombre,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Cod: ${product.codigoBarras ?: "Sin código"} • ${product.categoria}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "S/ %.2f".format(product.precio),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Emerald400
                        )
                    )
                    if (isAdmin) {
                        product.costo?.let { cost ->
                            Text(
                                text = "Costo: S/ %.2f".format(cost),
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        }
                    }
                }
            }

            // Indicadores de Stock y Acciones
            Column(horizontalAlignment = Alignment.End) {
                val (stockColor, stockBg) = when {
                    product.stock <= 0.0 -> Pair(Red400, Red900.copy(alpha = 0.3f))
                    product.stock <= 10.0 -> Pair(Amber400, Amber900.copy(alpha = 0.3f))
                    else -> Pair(Emerald300, Emerald700.copy(alpha = 0.25f))
                }

                Surface(
                    color = stockBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${product.stock.toInt()} ${product.unidadMedida}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = stockColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (canEdit) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = Red400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = onViewDetail,
                        shape = RoundedCornerShape(8.dp),
                        color = Emerald500.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Ver Detalle",
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Ver",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditProductDialog(
    product: Product?,
    scannedBarcode: String?,
    selectedImageUri: Uri?,
    isSaving: Boolean,
    onOpenScanner: () -> Unit,
    onImageSelected: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        barcode: String?,
        name: String,
        description: String?,
        category: String,
        price: Double,
        cost: Double?,
        stock: Double,
        unit: String,
        disponibleWeb: Boolean,
        destacado: Boolean
    ) -> Unit
) {
    var barcode by remember { mutableStateOf(scannedBarcode ?: product?.codigoBarras ?: "") }
    var name by remember { mutableStateOf(product?.nombre ?: "") }
    var description by remember { mutableStateOf(product?.descripcion ?: "") }
    var category by remember { mutableStateOf(product?.categoria ?: "Abarrotes") }
    var priceStr by remember { mutableStateOf(product?.precio?.let { "%.2f".format(it) } ?: "") }
    var costStr by remember { mutableStateOf(product?.costo?.let { "%.2f".format(it) } ?: "") }
    var stockStr by remember { mutableStateOf(product?.stock?.let { "${it.toInt()}" } ?: "10") }
    var unit by remember { mutableStateOf(product?.unidadMedida ?: "UND") }
    var disponibleWeb by remember { mutableStateOf(product?.disponible ?: true) }
    var destacado by remember { mutableStateOf(product?.destacado ?: false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onImageSelected(uri)
    }

    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) {
            barcode = scannedBarcode
        }
    }

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

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (product == null) "Nuevo Producto" else "Editar Producto",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de Imagen con conversión a WebP (600x600 px)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Slate850)
                            .border(1.dp, Slate700, RoundedCornerShape(14.dp))
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val previewSource = selectedImageUri ?: product?.imagenLocal ?: product?.imagenUrl
                        if (previewSource != null) {
                            AsyncImage(
                                model = previewSource,
                                contentDescription = "Foto Producto",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Foto", fontSize = 10.sp, color = Slate400)
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "Imagen Optimizada WebP",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Text(
                            text = "600x600 px • 80% calidad",
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald400
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Slate700),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cambiar Foto", fontSize = 11.sp, color = Slate200)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Nombre
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Producto *") },
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Código de barras con botón de escáner
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Código de Barras") },
                        trailingIcon = {
                            IconButton(onClick = onOpenScanner) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Escanear",
                                    tint = Emerald400
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Categoría") },
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Precio y Costo
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Precio Venta (S/) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text("Costo (S/)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stock y Unidad
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { stockStr = it },
                        label = { Text("Stock *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unidad (UND/KG)") },
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Switches de Tienda Web y Destacado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Disponible en Tienda Web", style = MaterialTheme.typography.bodyMedium, color = Slate300)
                    Switch(
                        checked = disponibleWeb,
                        onCheckedChange = { disponibleWeb = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Emerald500, checkedTrackColor = Emerald900)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Producto Destacado", style = MaterialTheme.typography.bodyMedium, color = Slate300)
                    Switch(
                        checked = destacado,
                        onCheckedChange = { destacado = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Amber500, checkedTrackColor = Amber900)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones Cancelar y Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Slate700),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar", color = Slate300)
                    }

                    val canSave = name.isNotBlank() && (priceStr.toDoubleOrNull() ?: 0.0) > 0.0
                    Button(
                        onClick = {
                            onSave(
                                barcode,
                                name,
                                description,
                                category,
                                priceStr.toDoubleOrNull() ?: 0.0,
                                costStr.toDoubleOrNull(),
                                stockStr.toDoubleOrNull() ?: 0.0,
                                unit,
                                disponibleWeb,
                                destacado
                            )
                        },
                        enabled = canSave && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            disabledContainerColor = Slate800
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductDetailDialog(
    product: Product,
    isAdmin: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Emerald500.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = product.categoria,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Slate800,
                            border = BorderStroke(1.dp, Slate700)
                        ) {
                            Text(
                                text = "Consulta",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate300,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Imagen WebP
                val imageSource = product.imagenLocal ?: product.imagenUrl
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate850)
                        .border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!imageSource.isNullOrBlank()) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = product.nombre,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Slate600,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Nombre
                Text(
                    text = product.nombre,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                if (!product.descripcion.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tarjeta de Precios y Stock
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate850,
                    border = BorderStroke(1.dp, Slate750),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Precio de Venta", color = Slate400, fontSize = 12.sp)
                            Text(
                                text = "S/ %.2f".format(product.precio),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Emerald400
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Slate750
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Stock en Tienda", color = Slate400, fontSize = 12.sp)
                            val (stockColor, stockBg) = when {
                                product.stock <= 0.0 -> Pair(Red400, Red900.copy(alpha = 0.3f))
                                product.stock <= 10.0 -> Pair(Amber400, Amber900.copy(alpha = 0.3f))
                                else -> Pair(Emerald300, Emerald700.copy(alpha = 0.25f))
                            }
                            Surface(
                                color = stockBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${product.stock.toInt()} ${product.unidadMedida}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = stockColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (isAdmin && product.costo != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Slate750
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Costo (Admin)", color = Slate500, fontSize = 12.sp)
                                Text(
                                    text = "S/ %.2f".format(product.costo),
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                            }
                        }

                        if (!product.codigoBarras.isNullOrBlank()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Slate750
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Código de Barras", color = Slate500, fontSize = 12.sp)
                                Text(
                                    text = product.codigoBarras,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar Consulta", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


