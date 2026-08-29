package com.minimarket.aepos.ui.users

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimarket.aepos.data.repository.UserRepository
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.domain.model.UserRole
import com.minimarket.aepos.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class UsersUiState(
    val currentUser: User = User(
        id = "user_admin_001",
        username = "admin",
        nombreCompleto = "Administrador Principal",
        pin = "1234",
        role = UserRole.ADMIN,
        permisos = listOf("all"),
        activo = true
    ),
    val allUsers: List<User> = emptyList(),
    val isUserSwitchOpen: Boolean = false,
    val isAddEditUserOpen: Boolean = false,
    val editingUser: User? = null,
    val errorMessage: String? = null,
    val validationError: String? = null,
    val isProcessing: Boolean = false
)

class UsersViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    init {
        observeUsers()
    }

    private fun observeUsers() {
        viewModelScope.launch {
            userRepository.allUsersFlow.collect { users ->
                _uiState.update { state ->
                    val updatedCurrent = users.find { it.id == state.currentUser.id } ?: state.currentUser
                    state.copy(allUsers = users, currentUser = updatedCurrent)
                }
            }
        }
    }

    fun openUserSwitch() {
        _uiState.update { it.copy(isUserSwitchOpen = true, errorMessage = null) }
    }

    fun closeUserSwitch() {
        _uiState.update { it.copy(isUserSwitchOpen = false, errorMessage = null) }
    }

    fun switchUserWithPin(pin: String): Boolean {
        val user = _uiState.value.allUsers.find { it.pin == pin.trim() && it.activo }
        return if (user != null) {
            _uiState.update { it.copy(currentUser = user, isUserSwitchOpen = false, errorMessage = null) }
            true
        } else {
            _uiState.update { it.copy(errorMessage = "PIN incorrecto o usuario inactivo") }
            false
        }
    }

    fun openAddUser() {
        _uiState.update { it.copy(isAddEditUserOpen = true, editingUser = null, validationError = null) }
    }

    fun openEditUser(user: User) {
        _uiState.update { it.copy(isAddEditUserOpen = true, editingUser = user, validationError = null) }
    }

    fun closeAddEditUser() {
        _uiState.update { it.copy(isAddEditUserOpen = false, editingUser = null, validationError = null) }
    }

    fun saveUser(
        username: String,
        nombreCompleto: String,
        email: String?,
        pin: String,
        role: UserRole,
        permisos: List<String>
    ) {
        val currentId = _uiState.value.editingUser?.id
        
        // Validaciones Profesionales
        if (username.length < 3) {
            _uiState.update { it.copy(validationError = "El usuario debe tener al menos 3 caracteres") }
            return
        }
        if (nombreCompleto.isBlank()) {
            _uiState.update { it.copy(validationError = "El nombre completo es obligatorio") }
            return
        }
        if (pin.length != 4 && pin.length != 6) {
            _uiState.update { it.copy(validationError = "El PIN debe ser de 4 o 6 dígitos") }
            return
        }

        // Verificar unicidad de username y PIN (localmente)
        val otherUsers = _uiState.value.allUsers.filter { it.id != currentId }
        if (otherUsers.any { it.username.equals(username, ignoreCase = true) }) {
            _uiState.update { it.copy(validationError = "El nombre de usuario ya existe") }
            return
        }
        if (otherUsers.any { it.pin == pin }) {
            _uiState.update { it.copy(validationError = "Este PIN ya está asignado a otro usuario") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val userToSave = User(
                id = currentId ?: UUID.randomUUID().toString(),
                username = username.trim(),
                nombreCompleto = nombreCompleto.trim(),
                email = email?.takeIf { it.isNotBlank() },
                pin = pin.trim(),
                role = role,
                permisos = if (role == UserRole.ADMIN) listOf("all") else permisos,
                activo = true
            )
            userRepository.saveUser(userToSave)
            _uiState.update { it.copy(isProcessing = false) }
            closeAddEditUser()
        }
    }

    fun toggleUserActive(user: User) {
        if (user.id == "user_admin_001") return
        viewModelScope.launch {
            userRepository.saveUser(user.copy(activo = !user.activo))
        }
    }

    fun deleteUser(id: String) {
        if (id == "user_admin_001" || id == _uiState.value.currentUser.id) return
        viewModelScope.launch {
            userRepository.deleteUser(id)
        }
    }
}

@Composable
fun UsersScreen(
    viewModel: UsersViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Slate950,
        floatingActionButton = {
            if (state.currentUser.isAdmin) {
                FloatingActionButton(
                    onClick = { viewModel.openAddUser() },
                    containerColor = Emerald500,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Nuevo Usuario")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Personal & Accesos",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = Color.White
                    )
                    Text(
                        text = "Gestión de cajeros y roles POS",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                Button(
                    onClick = { viewModel.openUserSwitch() },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate850),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Slate700),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, tint = Emerald400, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cambiar PIN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sesión Actual Pro
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Surface(
                            shape = CircleShape,
                            color = if (state.currentUser.isAdmin) Emerald500 else Blue500,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = state.currentUser.nombreCompleto.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = Color.White
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = Emerald400,
                            modifier = Modifier.size(14.dp).border(2.dp, Slate900, CircleShape)
                        ) {}
                    }
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SESIÓN ACTIVA",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Emerald400,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = state.currentUser.nombreCompleto,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "${state.currentUser.role.label} • @${state.currentUser.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Personal Registrado (${state.allUsers.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.allUsers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Emerald500)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.allUsers, key = { it.id }) { user ->
                        UserItemRow(
                            user = user,
                            isCurrent = user.id == state.currentUser.id,
                            onEdit = { viewModel.openEditUser(user) },
                            onDelete = { viewModel.deleteUser(user.id) }
                        )
                    }
                }
            }
        }
    }

    if (state.isUserSwitchOpen) {
        UserSwitchDialog(
            errorMessage = state.errorMessage,
            onDismiss = { viewModel.closeUserSwitch() },
            onPinSubmitted = { pin -> viewModel.switchUserWithPin(pin) }
        )
    }

    if (state.isAddEditUserOpen) {
        AddEditUserDialog(
            user = state.editingUser,
            validationError = state.validationError,
            isProcessing = state.isProcessing,
            onDismiss = { viewModel.closeAddEditUser() },
            onSave = { username, name, email, pin, role, perms ->
                viewModel.saveUser(username, name, email, pin, role, perms)
            }
        )
    }
}

@Composable
fun UserItemRow(
    user: User,
    isCurrent: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Slate900 else Slate900
        ),
        border = BorderStroke(1.dp, if (isCurrent) Emerald500.copy(alpha = 0.5f) else Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (user.isAdmin) Emerald500.copy(alpha = 0.2f) else Slate800,
                border = BorderStroke(1.dp, if (user.isAdmin) Emerald500.copy(alpha = 0.4f) else Slate700),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (user.isAdmin) Icons.Default.Shield else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (user.isAdmin) Emerald400 else Slate400,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.nombreCompleto,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Emerald500,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "ACTIVO",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
                Text(
                    text = "${user.role.label} • @${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
                
                // Mostrar permisos de forma compacta
                if (!user.isAdmin) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val hasWrite = user.hasPermission(User.PERM_INVENTORY_WRITE)
                        val hasReports = user.hasPermission(User.PERM_REPORTS_VIEW)
                        val hasShopping = user.hasPermission(User.PERM_SHOPPING_MANAGE)

                        if (!hasWrite && !hasReports && !hasShopping) {
                            PermissionBadge("Solo Consulta Precios", true)
                        } else {
                            PermissionBadge("Consulta", true)
                            if (hasWrite) PermissionBadge("Editar Stock", true)
                            if (hasReports) PermissionBadge("Ventas PC", true)
                            if (hasShopping) PermissionBadge("Reabastecer", true)
                        }
                    }
                }
            }

            if (!isCurrent) {
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, "Editar", tint = Slate400, modifier = Modifier.size(16.dp))
                    }
                    if (user.id != "user_admin_001") {
                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.DeleteOutline, "Eliminar", tint = Red400, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionBadge(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (active) Emerald700.copy(alpha = 0.2f) else Slate850,
        border = BorderStroke(0.5.dp, if (active) Emerald500.copy(alpha = 0.3f) else Slate750)
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Emerald300 else Slate500,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun UserSwitchDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onPinSubmitted: (String) -> Boolean
) {
    var enteredPin by remember { mutableStateOf("") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Emerald500,
        unfocusedBorderColor = Slate700,
        focusedContainerColor = Slate850,
        unfocusedContainerColor = Slate850,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Emerald500.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.3f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LockOpen, null, tint = Emerald400, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Control de Acceso",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Ingrese su PIN asignado",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { if (it.length <= 6) enteredPin = it },
                    placeholder = { Text("••••", color = Slate500) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(0.75f)
                )

                errorMessage?.let { error ->
                    Text(text = error, color = Red400, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Slate700),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Slate300)
                    }
                    Button(
                        onClick = { onPinSubmitted(enteredPin) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        enabled = enteredPin.length >= 4,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ingresar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditUserDialog(
    user: User?,
    validationError: String?,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, UserRole, List<String>) -> Unit
) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var nombreCompleto by remember { mutableStateOf(user?.nombreCompleto ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var pin by remember { mutableStateOf(user?.pin ?: "") }
    var role by remember { mutableStateOf(user?.role ?: UserRole.COLABORADOR) }
    
    // Gestión granular de permisos (Consulta activa por defecto)
    var canWriteInventory by remember { mutableStateOf(user?.hasPermission(User.PERM_INVENTORY_WRITE) ?: false) }
    var canViewReports by remember { mutableStateOf(user?.hasPermission(User.PERM_REPORTS_VIEW) ?: false) }
    var canManageShopping by remember { mutableStateOf(user?.hasPermission(User.PERM_SHOPPING_MANAGE) ?: false) }

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

    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (user == null) "Nuevo Colaborador" else "Editar Perfil",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nombreCompleto,
                    onValueChange = { nombreCompleto = it },
                    label = { Text("Nombre Completo *") },
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Usuario *") },
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it },
                        label = { Text("PIN *") },
                        placeholder = { Text("4-6 dígitos") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Rol de Acceso", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Emerald400)
                Row(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == UserRole.COLABORADOR,
                        onClick = { role = UserRole.COLABORADOR },
                        label = { Text("Colaborador / Consultor") }
                    )
                    FilterChip(
                        selected = role == UserRole.ADMIN,
                        onClick = { role = UserRole.ADMIN },
                        label = { Text("Administrador") }
                    )
                }

                if (role == UserRole.COLABORADOR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Permisos Especiales (Consulta activa por defecto)", style = MaterialTheme.typography.bodySmall, color = Slate400)
                    
                    PermissionSwitch("Crear / Modificar Productos", canWriteInventory) { canWriteInventory = it }
                    PermissionSwitch("Monitorear Ventas de la PC", canViewReports) { canViewReports = it }
                    PermissionSwitch("Gestionar Lista de Compras", canManageShopping) { canManageShopping = it }
                }

                validationError?.let {
                    Text(it, color = Red400, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Slate700),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Slate300)
                    }
                    Button(
                        onClick = {
                            val perms = mutableListOf(User.PERM_INVENTORY_READ, "inventario:consultar")
                            if (canWriteInventory) {
                                perms.add(User.PERM_INVENTORY_WRITE)
                                perms.add("inventario:modificar")
                            }
                            if (canViewReports) {
                                perms.add(User.PERM_REPORTS_VIEW)
                                perms.add("ventas:historial")
                            }
                            if (canManageShopping) {
                                perms.add(User.PERM_SHOPPING_MANAGE)
                                perms.add("compras:gestionar")
                            }
                            
                            onSave(username, nombreCompleto, email, pin, role, perms)
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (isProcessing) {
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
fun PermissionSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Slate300)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Emerald500, checkedTrackColor = Emerald900)
        )
    }
}

