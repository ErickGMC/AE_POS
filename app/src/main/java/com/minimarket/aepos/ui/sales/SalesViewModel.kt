package com.minimarket.aepos.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimarket.aepos.data.repository.ProductRepository
import com.minimarket.aepos.data.repository.SaleRepository
import com.minimarket.aepos.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SalesUiState(
    val searchQuery: String = "",
    val selectedCategory: String = "Todos",
    val categories: List<String> = listOf("Todos"),
    val products: List<Product> = emptyList(),
    val cart: List<CartItem> = emptyList(),
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.EFECTIVO,
    val cashReceived: String = "",
    val customerName: String = "",
    val customerDoc: String = "",
    val isCheckingOut: Boolean = false,
    val isScannerOpen: Boolean = false,
    val isPagosMixtosOpen: Boolean = false,
    val isProcessing: Boolean = false,
    val lastSuccessComprobante: String? = null,
    val errorMessage: String? = null,
    val weightDialogProduct: Product? = null,
    val initialWeightForDialog: Double = 0.500
) {
    val total: Double
        get() {
            val raw = cart.sumOf { it.subtotal }
            return Math.round(raw * 100.0) / 100.0
        }

    val totalItemsCount: Double
        get() = cart.sumOf { it.cantidad }

    val changeAmount: Double
        get() {
            val received = cashReceived.toDoubleOrNull() ?: 0.0
            val change = received - total
            return if (change > 0) Math.round(change * 100.0) / 100.0 else 0.0
        }
}

class SalesViewModel(
    private val productRepository: ProductRepository,
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        observeProducts()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            productRepository.getCategoriesFlow().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeProducts() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.searchQuery }.distinctUntilChanged().debounce { q ->
                    if (q.isBlank()) 0L else 250L
                },
                _uiState.map { it.selectedCategory }.distinctUntilChanged()
            ) { query, category ->
                Pair(query, category)
            }.flatMapLatest { (query, category) ->
                if (query.isNotBlank()) {
                    productRepository.searchProductsFlow(query)
                } else {
                    productRepository.getProductsByCategoryFlow(category)
                }
            }.collect { productList ->
                _uiState.update { it.copy(products = productList) }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category, searchQuery = "") }
    }

    fun openScanner() {
        _uiState.update { it.copy(isScannerOpen = true) }
    }

    fun closeScanner() {
        _uiState.update { it.copy(isScannerOpen = false) }
    }

    fun openPagosMixtos() {
        _uiState.update { it.copy(isPagosMixtosOpen = true) }
    }

    fun closePagosMixtos() {
        _uiState.update { it.copy(isPagosMixtosOpen = false) }
    }

    fun openWeightDialog(product: Product, initialWeight: Double = 0.500) {
        _uiState.update {
            it.copy(
                weightDialogProduct = product,
                initialWeightForDialog = if (initialWeight > 0.0) initialWeight else 0.500
            )
        }
    }

    fun closeWeightDialog() {
        _uiState.update {
            it.copy(weightDialogProduct = null)
        }
    }

    fun addWeightItemToCart(product: Product, weight: Double) {
        if (weight <= 0.0) return
        _uiState.update { state ->
            val currentCart = state.cart.toMutableList()
            val existingIndex = currentCart.indexOfFirst { it.product.id == product.id }

            if (existingIndex >= 0) {
                val current = currentCart[existingIndex]
                val nuevaCantidad = Math.round((current.cantidad + weight) * 1000.0) / 1000.0
                if (nuevaCantidad > product.stock) {
                    return@update state.copy(
                        weightDialogProduct = null,
                        errorMessage = "Stock insuficiente para ${product.nombre} (Stock: ${product.stock})"
                    )
                }
                currentCart[existingIndex] = current.copy(cantidad = nuevaCantidad)
            } else {
                if (weight > product.stock) {
                    return@update state.copy(
                        weightDialogProduct = null,
                        errorMessage = "Stock insuficiente para ${product.nombre} (Stock: ${product.stock})"
                    )
                }
                currentCart.add(CartItem(product = product, cantidad = weight))
            }
            state.copy(cart = currentCart, weightDialogProduct = null, errorMessage = null)
        }
    }

    fun updateCartItemQuantity(productId: String, newQuantity: Double) {
        if (newQuantity <= 0.0) {
            removeFromCart(productId)
            return
        }
        _uiState.update { state ->
            val currentCart = state.cart.toMutableList()
            val index = currentCart.indexOfFirst { it.product.id == productId }
            if (index >= 0) {
                val current = currentCart[index]
                if (newQuantity > current.product.stock) {
                    return@update state.copy(errorMessage = "Stock insuficiente para ${current.product.nombre} (Stock: ${current.product.stock})")
                }
                currentCart[index] = current.copy(cantidad = Math.round(newQuantity * 1000.0) / 1000.0)
            }
            state.copy(cart = currentCart, errorMessage = null)
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val product = productRepository.getByBarcode(barcode)
            if (product != null) {
                if (product.isWeightUnit) {
                    openWeightDialog(product)
                } else {
                    addToCart(product)
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Código '$barcode' no registrado en catálogo")
                }
            }
        }
    }

    fun addToCart(product: Product) {
        if (product.isWeightUnit) {
            openWeightDialog(product)
            return
        }
        _uiState.update { state ->
            val currentCart = state.cart.toMutableList()
            val existingIndex = currentCart.indexOfFirst { it.product.id == product.id }

            if (existingIndex >= 0) {
                val current = currentCart[existingIndex]
                val step = 1.0
                val nuevaCant = Math.round((current.cantidad + step) * 1000.0) / 1000.0
                if (nuevaCant > product.stock) {
                    return@update state.copy(errorMessage = "Stock insuficiente para ${product.nombre}")
                }
                currentCart[existingIndex] = current.copy(cantidad = nuevaCant)
            } else {
                if (product.stock < 1.0) {
                    return@update state.copy(errorMessage = "Producto agotado: ${product.nombre}")
                }
                currentCart.add(CartItem(product = product, cantidad = 1.0))
            }
            state.copy(cart = currentCart, errorMessage = null)
        }
    }

    fun decrementCartItem(productId: String) {
        _uiState.update { state ->
            val currentCart = state.cart.toMutableList()
            val index = currentCart.indexOfFirst { it.product.id == productId }
            if (index >= 0) {
                val current = currentCart[index]
                val step = if (current.product.isWeightUnit) 0.100 else 1.0
                val nuevaCantidad = Math.round((current.cantidad - step) * 1000.0) / 1000.0
                if (nuevaCantidad > 0.0) {
                    currentCart[index] = current.copy(cantidad = nuevaCantidad)
                } else {
                    currentCart.removeAt(index)
                }
            }
            state.copy(cart = currentCart)
        }
    }

    fun removeFromCart(productId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.product.id == productId })
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyList(), isCheckingOut = false, cashReceived = "") }
    }

    fun openCheckout() {
        if (_uiState.value.cart.isEmpty()) return
        _uiState.update { it.copy(isCheckingOut = true, cashReceived = "%.2f".format(it.total)) }
    }

    fun closeCheckout() {
        _uiState.update { it.copy(isCheckingOut = false) }
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(selectedPaymentMethod = method) }
    }

    fun setCashReceived(amountStr: String) {
        _uiState.update { it.copy(cashReceived = amountStr) }
    }

    fun setCustomerInfo(name: String, doc: String) {
        _uiState.update { it.copy(customerName = name, customerDoc = doc) }
    }

    fun processSale() {
        val state = _uiState.value
        if (state.cart.isEmpty()) return
        if (state.isProcessing) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val result = saleRepository.processSale(
                items = state.cart,
                total = state.total,
                paymentMethod = state.selectedPaymentMethod,
                customerName = state.customerName,
                customerDoc = state.customerDoc
            )

            result.fold(
                onSuccess = { comprobante ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCheckingOut = false,
                            cart = emptyList(),
                            cashReceived = "",
                            customerName = "",
                            customerDoc = "",
                            lastSuccessComprobante = comprobante
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = error.message ?: "Error al procesar la venta"
                        )
                    }
                }
            )
        }
    }

    fun processPagoMixtoSale(pagos: List<PagoParcial>) {
        val state = _uiState.value
        if (state.cart.isEmpty() || state.isProcessing) return

        val montoEfectivo = pagos.filter { it.metodo.equals("Efectivo", ignoreCase = true) }.sumOf { it.monto }

        _uiState.update { it.copy(isProcessing = true, isPagosMixtosOpen = false) }

        viewModelScope.launch {
            val result = saleRepository.processSale(
                items = state.cart,
                total = state.total,
                paymentMethod = PaymentMethod.MIXTO,
                customerName = state.customerName,
                customerDoc = state.customerDoc,
                montoEfectivo = montoEfectivo
            )

            result.fold(
                onSuccess = { comprobante ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCheckingOut = false,
                            cart = emptyList(),
                            cashReceived = "",
                            lastSuccessComprobante = comprobante
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = error.message ?: "Error al procesar la venta"
                        )
                    }
                }
            )
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update { it.copy(lastSuccessComprobante = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
