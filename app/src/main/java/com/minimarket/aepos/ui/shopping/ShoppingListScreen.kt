package com.minimarket.aepos.ui.shopping

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimarket.aepos.data.repository.ProductRepository
import com.minimarket.aepos.domain.model.Product
import com.minimarket.aepos.ui.components.CategoryDropdownSelector
import com.minimarket.aepos.ui.components.CategoryFilterRow
import com.minimarket.aepos.ui.components.SearchBarField
import com.minimarket.aepos.ui.components.UnitDropdownSelector
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val quantity: Double = 1.0,
    val price: Double? = null,
    val unit: String = "UND",
    val isFromInventory: Boolean = true,
    val productId: String? = null
)

data class ShoppingUiState(
    val selectedItems: List<ShoppingItem> = emptyList(),
    val inventoryProducts: List<Product> = emptyList(),
    val categories: List<String> = listOf("Todos"),
    val searchInventoryQuery: String = "",
    val selectedCategory: String = "Todos",
    
    val includePrice: Boolean = false,
    val includeQuantity: Boolean = true,
    
    val isInventoryPickerOpen: Boolean = false,
    val isExternalDialogOpen: Boolean = false,
    val isLoading: Boolean = false
)

class ShoppingListViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        observeInventory()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            productRepository.getCategoriesFlow().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
    }

    private fun observeInventory() {
        viewModelScope.launch {
            combine(
                productRepository.getAllProductsFlow(),
                _uiState.map { it.searchInventoryQuery }.distinctUntilChanged(),
                _uiState.map { it.selectedCategory }.distinctUntilChanged()
            ) { allProds, query, category ->
                allProds.filter { prod ->
                    val matchesQuery = query.isBlank() || prod.nombre.contains(query, ignoreCase = true) ||
                            (prod.codigoBarras != null && prod.codigoBarras.contains(query, ignoreCase = true))
                    val matchesCat = category == "Todos" || category.isBlank() || prod.categoria.equals(category, ignoreCase = true)
                    matchesQuery && matchesCat
                }
            }.collect { list ->
                _uiState.update { it.copy(inventoryProducts = list) }
            }
        }
    }

    fun onSearchInventoryChanged(q: String) {
        _uiState.update { it.copy(searchInventoryQuery = q) }
    }

    fun onCategorySelected(c: String) {
        _uiState.update { it.copy(selectedCategory = c, searchInventoryQuery = "") }
    }

    fun addItemFromInventory(product: Product) {
        val newItem = ShoppingItem(
            name = product.nombre,
            category = product.categoria,
            quantity = 10.0,
            price = product.precio,
            unit = product.unidadMedida,
            isFromInventory = true,
            productId = product.id
        )
        _uiState.update { it.copy(selectedItems = it.selectedItems + newItem) }
    }

    fun addExternalItem(name: String, category: String, qty: Double, price: Double?, unit: String) {
        val newItem = ShoppingItem(
            name = name,
            category = category,
            quantity = qty,
            price = price,
            unit = unit,
            isFromInventory = false
        )
        _uiState.update { it.copy(selectedItems = it.selectedItems + newItem, isExternalDialogOpen = false) }
    }

    fun removeItem(id: String) {
        _uiState.update { it.copy(selectedItems = it.selectedItems.filter { item -> item.id != id }) }
    }

    fun updateItemQuantity(id: String, qty: Double) {
        _uiState.update { state ->
            state.copy(selectedItems = state.selectedItems.map {
                if (it.id == id) it.copy(quantity = qty) else it
            })
        }
    }

    fun toggleIncludePrice(value: Boolean) {
        _uiState.update { it.copy(includePrice = value) }
    }

    fun toggleIncludeQuantity(value: Boolean) {
        _uiState.update { it.copy(includeQuantity = value) }
    }

    fun openInventoryPicker() {
        _uiState.update { it.copy(isInventoryPickerOpen = true) }
    }

    fun closeInventoryPicker() {
        _uiState.update { it.copy(isInventoryPickerOpen = false) }
    }

    fun openExternalDialog() {
        _uiState.update { it.copy(isExternalDialogOpen = true) }
    }

    fun closeExternalDialog() {
        _uiState.update { it.copy(isExternalDialogOpen = false) }
    }
}

@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { viewModel.openExternalDialog() },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .size(46.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, "Producto Externo", modifier = Modifier.size(20.dp))
                }
                FloatingActionButton(
                    onClick = { viewModel.openInventoryPicker() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.shadow(8.dp, CircleShape)
                ) {
                    Icon(Icons.Default.Inventory, "Desde Inventario")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(14.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lista de Compras & Pedidos",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.selectedItems.size} ítems listados para reposición",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.selectedItems.isNotEmpty()) {
                    Button(
                        onClick = { shareShoppingList(context, state) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Configuración de la lista
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LabelSwitch("Cantidades", state.includeQuantity) { viewModel.toggleIncludeQuantity(it) }
                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    LabelSwitch("Precios Estimados", state.includePrice) { viewModel.toggleIncludePrice(it) }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lista de items seleccionados agrupados por categoría
            if (state.selectedItems.isEmpty()) {
                EmptyShoppingListState()
            } else {
                val groupedItems = state.selectedItems.groupBy { it.category }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedItems.forEach { (category, items) ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            ) {
                                Text(
                                    text = "${category.uppercase()} (${items.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        items(items, key = { it.id }) { item ->
                            SelectedItemRow(
                                item = item,
                                onRemove = { viewModel.removeItem(item.id) },
                                onQtyChange = { viewModel.updateItemQuantity(item.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo Selector de Inventario
    if (state.isInventoryPickerOpen) {
        InventoryPickerDialog(
            state = state,
            onDismiss = { viewModel.closeInventoryPicker() },
            onSearchChange = { viewModel.onSearchInventoryChanged(it) },
            onCategorySelect = { viewModel.onCategorySelected(it) },
            onProductSelected = { viewModel.addItemFromInventory(it) }
        )
    }

    // Diálogo Producto Externo
    if (state.isExternalDialogOpen) {
        ExternalProductDialog(
            availableCategories = state.categories,
            onDismiss = { viewModel.closeExternalDialog() },
            onAdd = { name, cat, qty, price, unit -> 
                viewModel.addExternalItem(name, cat, qty, price, unit) 
            }
        )
    }
}

@Composable
fun LabelSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(6.dp))
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
        )
    }
}

@Composable
fun SelectedItemRow(
    item: ShoppingItem,
    onRemove: () -> Unit,
    onQtyChange: (Double) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isFromInventory) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            "EXTERNO",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Control de cantidad
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { if (item.quantity > 1) onQtyChange(item.quantity - 1) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
                Text(
                    text = "${item.quantity.toInt()} ${item.unit}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = { onQtyChange(item.quantity + 1) },
                    modifier = Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun InventoryPickerDialog(
    state: ShoppingUiState,
    onDismiss: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onProductSelected: (Product) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Seleccionar de Inventario",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                SearchBarField(
                    query = state.searchInventoryQuery,
                    onQueryChange = onSearchChange,
                    onScanBarcodeClick = {},
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                CategoryFilterRow(
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    onCategorySelected = onCategorySelect
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.inventoryProducts) { prod ->
                        val alreadyInList = state.selectedItems.any { it.productId == prod.id }
                        Surface(
                            color = if (alreadyInList) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, if (alreadyInList) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable(!alreadyInList) { onProductSelected(prod) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.nombre,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alreadyInList) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Stock: ${prod.stock.toInt()} ${prod.unidadMedida} • S/ %.2f".format(prod.precio),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { onProductSelected(prod) },
                                    enabled = !alreadyInList,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (alreadyInList) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                                        contentDescription = null,
                                        tint = if (alreadyInList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExternalProductDialog(
    availableCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onAdd: (String, String, Double, Double?, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Abarrotes") }
    var qtyStr by remember { mutableStateOf("1") }
    var priceStr by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("UND") }

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
                    "Producto No Registrado",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Producto *") },
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Cant.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    UnitDropdownSelector(
                        selectedUnit = unit,
                        onUnitSelected = { unit = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                CategoryDropdownSelector(
                    selectedCategory = category,
                    onCategorySelected = { category = it },
                    availableCategories = availableCategories,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Precio Sugerido (Opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { onAdd(name, category, qtyStr.toDoubleOrNull() ?: 1.0, priceStr.toDoubleOrNull(), unit) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Añadir", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyShoppingListState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Tu lista está vacía", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Usa los botones flotantes para añadir productos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shareShoppingList(context: Context, state: ShoppingUiState) {
    val grouped = state.selectedItems.groupBy { it.category }
    val sb = StringBuilder()
    sb.append("📋 *PEDIDO DE MERCADERÍA - AE POS*\n")
    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    sb.append("📍 *Origen:* Minimarket Flor\n")
    sb.append("📅 *Fecha:* ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())}\n")
    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

    var totalEstimado = 0.0

    grouped.forEach { (category, items) ->
        sb.append("📂 *${category.uppercase()}*\n")
        items.forEach { item ->
            sb.append("• ${item.name}")
            
            val details = mutableListOf<String>()
            if (state.includeQuantity) {
                details.add("${item.quantity.toInt()} ${item.unit}")
            }
            if (state.includePrice && item.price != null) {
                details.add("S/ ${"%.2f".format(item.price)}")
                totalEstimado += (item.price * item.quantity)
            }
            
            if (details.isNotEmpty()) {
                sb.append(" _(${details.joinToString(" • ")})_")
            }
            sb.append("\n")
        }
        sb.append("\n")
    }

    if (state.includePrice && totalEstimado > 0) {
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("💰 *INVERSIÓN ESTIMADA: S/ ${"%.2f".format(totalEstimado)}*\n")
        sb.append("*(Precios referenciales según inventario)*\n")
    }
    
    sb.append("\n_Generado automáticamente desde AE POS Móvil_")

    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(intent, "Enviar Pedido por WhatsApp"))
}
