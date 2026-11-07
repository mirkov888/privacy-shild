@file:OptIn(ExperimentalMaterial3Api::class)
package com.mora.shield

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ModPerm(val pkg:String,val op:String,val age:Long)
private data class ModTraffic(val day:String,val pkg:String,val bytes:Long)

@Composable
fun ModuleV36Screen() {
    val ctx= LocalContext.current
    val scope= rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(Store.cfg["ar_days"] ?: "30") }
    var excludes by remember { mutableStateOf(Store.cfg["ar_ex"] ?: "") }
    var vaultPkgs by remember { mutableStateOf(Store.cfg["vault_pkgs"] ?: "") }
    var limit by remember { mutableStateOf("200") }
    var perms by remember { mutableStateOf<List<ModPerm>>(emptyList()) }
    var traffic by remember { mutableStateOf<List<ModTraffic>>(emptyList()) }
    var purge by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Store.cfg) {
        days=Store.cfg["ar_days"] ?: "30"
        excludes=Store.cfg["ar_ex"] ?: ""
        vaultPkgs=Store.cfg["vault_pkgs"] ?: ""
    }
    fun runCmd(args:String, parse:(String)->Unit = {}) {
        if(busy) return
        busy=true
        scope.launch {
            val r=withContext(Dispatchers.IO){ Ps.run(args) }
            output=r.out.ifBlank { if(r.ok) "Selesai." else "Perintah gagal: ${r.code}" }
            parse(r.out)
            busy=false
            Store.refresh(ctx)
        }
    }
    fun setKey(key:String,value:String) {
        if(busy) return
        busy=true
        scope.launch {
            val r=withContext(Dispatchers.IO){ Ps.set(key,value) }
            if(r.ok) { Store.cfg=Store.cfg+(key to value); output="$key tersimpan." }
            else output="Gagal: ${r.out}"
            busy=false
        }
    }
    val v=Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(Store.version)?.groupValues?.drop(1)?.mapNotNull{it.toIntOrNull()} ?: emptyList()
    val ok=v.size==3 && (v[0]>3 || (v[0]==3 && v[1]>=6))
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Integrasi Mora Privacy Shield",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Modul ${Store.version.ifBlank{"tidak terbaca"}} · API v3.6–v3.8",style=MaterialTheme.typography.bodySmall)
            if(!ok) Text("Perlu modul v3.6.0 atau lebih baru dan akses root.",color=MaterialTheme.colorScheme.error)
        }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Pengawas jaringan",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            ModuleToggle("Pengawas jaringan",Store.cfg["nw_on"]=="1",ok&&!busy){setKey("nw_on",if(it)"1" else "0")}; ModuleToggle("Deteksi 2G/operator",Store.cfg["nw_2g"]=="1",ok&&!busy){setKey("nw_2g",if(it)"1" else "0")}; ModuleToggle("Deteksi Wi-Fi kembar",Store.cfg["nw_wifi"]=="1",ok&&!busy){setKey("nw_wifi",if(it)"1" else "0")}
            Text("Aksi otomatis Wi-Fi",fontWeight=FontWeight.Medium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled=ok&&!busy,onClick={setKey("nw_act","off")}){Text("Aksi mati")}
                OutlinedButton(enabled=ok&&!busy,onClick={setKey("nw_act","wifioff")}){Text("Wi-Fi off")}
            }
            ModuleToggle("Mode uji aksi Wi-Fi",Store.cfg["nw_dry"]!="0",ok&&!busy){setKey("nw_dry",if(it)"1" else "0")}
            Text("Mode uji harus dimatikan secara sadar sebelum Wi-Fi benar-benar diputus.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Audit & cabut izin",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            ModuleToggle("Cabut izin otomatis",Store.cfg["ar_on"]=="1",ok&&!busy){setKey("ar_on",if(it)"1" else "0")}
            OutlinedTextField(days,{days=it.filter(Char::isDigit).take(3)},label={Text("Batas hari (7–365)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            OutlinedTextField(excludes,{excludes=it},label={Text("Pengecualian package, pisahkan koma")},modifier=Modifier.fillMaxWidth(),minLines=2)
            ModuleToggle("Mode uji cabut izin",Store.cfg["ar_dry"]!="0",ok&&!busy){setKey("ar_dry",if(it)"1" else "0")}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled=ok&&!busy,onClick={val n=days.toIntOrNull(); if(n==null||n !in 7..365) output="Batas harus 7–365 hari." else setKey("ar_days",n.toString())}){Text("Simpan hari")}
                OutlinedButton(enabled=ok&&!busy,onClick={setKey("ar_ex",excludes.trim())}){Text("Simpan pengecualian")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=ok&&!busy,onClick={runCmd("permaudit"){raw->perms=raw.lineSequence().mapNotNull{l->val p=l.split("|",limit=4);if(p.size==4&&p[0]=="A")ModPerm(p[1],p[2],p[3].toLongOrNull()?:-1)else null}.toList()}}){Text("Audit izin")}
                OutlinedButton(enabled=ok&&!busy,onClick={runCmd("arrun")}){Text("Jalankan arrun")}
            }
            perms.take(80).forEach { Text("${it.pkg} · ${it.op} · ${if(it.age<0)"tidak tercatat" else "${it.age/86400} hari"}",style=MaterialTheme.typography.bodySmall) }
            Text("Cabut izin nyata hanya berjalan jika mode uji dimatikan. Modul v3.7 melewati aplikasi inti.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Riwayat data keluar",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            ModuleToggle("Aktifkan pencatatan data keluar (nb_on)",Store.cfg["nb_on"]=="1",ok&&!busy){setKey("nb_on",if(it)"1" else "0")}
            Text("Modul merekam counter berkala. Riwayat tersedia setelah ada sampel baru.")
            OutlinedTextField(limit,{limit=it.filter(Char::isDigit).take(4)},label={Text("Jumlah baris (maks. 2000)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            Button(enabled=ok&&!busy,onClick={val n=(limit.toIntOrNull()?:200).coerceIn(1,2000);runCmd("traffic $n"){raw->traffic=raw.lineSequence().mapNotNull{l->val p=l.split("|",limit=4);if(p.size==4&&p[0]=="D")ModTraffic(p[1],p[2],p[3].toLongOrNull()?:0)else null}.toList()}}){Text("Muat trafik")}
            traffic.take(100).forEach{Text("${it.day} · ${it.pkg} · ${moduleFormatBytes(it.bytes)}",style=MaterialTheme.typography.bodySmall)}
            Text("TRAFSPIKE tampil sebagai kejadian di Monitor > Timeline.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Brankas aplikasi",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            OutlinedTextField(vaultPkgs,{vaultPkgs=it},label={Text("Package aplikasi, pisahkan koma")},modifier=Modifier.fillMaxWidth(),minLines=2)
            OutlinedButton(enabled=ok&&!busy,onClick={setKey("vault_pkgs",vaultPkgs.trim())}){Text("Simpan daftar")}
            Text("Status: ${if(Store.cfg["vault_on"]=="1")"AKTIF" else "nonaktif"}")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=ok&&!busy&&vaultPkgs.isNotBlank(),onClick={runCmd("vault on")}){Text("Aktifkan brankas")}
                OutlinedButton(enabled=ok&&!busy,onClick={runCmd("vault off")}){Text("Pulihkan aplikasi")}
            }
            Text("Launcher, dialer, keyboard, SMS, asisten, Mora Shield, dan pengelola root dilewati oleh modul. Data aplikasi tidak dihapus.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Profil situasi",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=ok&&!busy,onClick={runCmd("sprofile border")}){Text("Border")}
                OutlinedButton(enabled=ok&&!busy,onClick={runCmd("sprofile home")}){Text("Home")}
            }
            Text("Border mengaktifkan pengawas jaringan dan brankas bila daftar paket terisi. Home membuka brankas aktif.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Hapus riwayat modul",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled=ok&&!busy,onClick={purge="traffic"}){Text("Trafik")}
                OutlinedButton(enabled=ok&&!busy,onClick={purge="wifi"}){Text("Wi-Fi")}
                OutlinedButton(enabled=ok&&!busy,onClick={purge="all"}){Text("Semua")}
            }
        } } }
        if(output.isNotBlank()) item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("Hasil perintah",fontWeight=FontWeight.SemiBold);Text(output,style=MaterialTheme.typography.bodySmall)}} }
    }
    if(purge!=null) AlertDialog(onDismissRequest={purge=null},title={Text("Hapus riwayat ${purge}?")},text={Text("Tindakan ini tidak dapat dibatalkan.")},confirmButton={TextButton(onClick={val p=purge!!;purge=null;runCmd("purge $p")}){Text("Hapus")}},dismissButton={TextButton(onClick={purge=null}){Text("Batal")}})
}

@Composable
private fun ModuleToggle(label:String, checked:Boolean, enabled:Boolean, onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,Modifier.weight(1f))
        Switch(checked=checked,enabled=enabled,onCheckedChange=onChange)
    }
}

private fun moduleFormatBytes(value:Long):String {
    if(value<0L) return "Tidak didukung"
    if(value<1024L) return "$value B"
    val units=arrayOf("KB","MB","GB","TB")
    var amount=value.toDouble()
    var index=-1
    do { amount/=1024.0; index++ } while(amount>=1024.0 && index<units.lastIndex)
    return String.format(java.util.Locale.getDefault(),"%.1f %s",amount,units[index])
}
