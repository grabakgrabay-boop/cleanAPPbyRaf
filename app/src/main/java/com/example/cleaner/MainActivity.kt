package com.example.cleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var items by remember { mutableStateOf<List<JunkItem>>(emptyList()) }
        var selected by remember { mutableStateOf<Set<JunkItem>>(emptySet()) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Tekan Pindai untuk mencari file sampah") }

        val totalSelected = selected.sumOf { it.size }

        Scaffold(topBar = { TopAppBar(title = { Text("Pembersih Sampah") }) }) { pad ->
            Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                Text(status, style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        if (!hasAccess()) {
                            requestAccess()
                            Toast.makeText(this@MainActivity,
                                "Izinkan akses file, lalu tekan Pindai lagi", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        busy = true
                        status = "Memindai..."
                        scope.launch {
                            val found = withContext(Dispatchers.IO) { Cleaner.scan(this@MainActivity) }
                            items = found.sortedByDescending { it.size }
                            selected = items.toSet()
                            status = "Ditemukan ${items.size} item (${formatSize(items.sumOf { it.size })})"
                            busy = false
                        }
                    }) { Text("Pindai") }

                    Button(enabled = !busy && selected.isNotEmpty(), onClick = {
                        busy = true
                        scope.launch {
                            val toDelete = selected.toList()
                            val freed = withContext(Dispatchers.IO) { Cleaner.clean(toDelete) }
                            items = items - toDelete.toSet()
                            selected = emptySet()
                            status = "Selesai! Ruang dibebaskan: ${formatSize(freed)}"
                            busy = false
                        }
                    }) { Text("Bersihkan (${formatSize(totalSelected)})") }
                }

                OutlinedButton(onClick = {
                    // Cache aplikasi lain hanya bisa dibersihkan lewat pengaturan sistem
                    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                }) { Text("Buka Pengaturan Penyimpanan Sistem") }

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                LazyColumn {
                    items(items) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item in selected,
                                onCheckedChange = { c ->
                                    selected = if (c) selected + item else selected - item
                                })
                            Column(Modifier.weight(1f)) {
                                Text(item.file.name, maxLines = 1)
                                Text("${item.category} • ${item.file.parent}",
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            Text(formatSize(item.size))
                        }
                    }
                }
            }
        }
    }
}
