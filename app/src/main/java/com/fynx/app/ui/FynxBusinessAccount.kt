package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxBusinessAccountPanel(onBack: () -> Unit = {}, onOpenAdvertising: () -> Unit = {}, onOpenDashboard: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var website by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(true) }; var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }; var activeListings by remember { mutableStateOf(0) }; var campaigns by remember { mutableStateOf(0) }; var budget by remember { mutableStateOf(0L) }; var spent by remember { mutableStateOf(0L) }

    fun load() = scope.launch {
        loading = true
        FynxBusinessClient.load(context).onSuccess { p -> if (p != null) { name=p.businessName; username=p.businessUsername; category=p.category; description=p.description; location=p.location; phone=p.phone; website=p.website; verified=p.verified } }
        FynxBusinessClient.overview(context).onSuccess { o -> activeListings=o.optInt("activeListings"); campaigns=o.optInt("campaigns"); budget=o.optLong("budgetKobo"); spent=o.optLong("spentKobo") }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Business Account", style = MaterialTheme.typography.headlineSmall)
        Text("Your professional FYNX identity connects Marketplace, social posts and advertising.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(name,{name=it.take(120)},Modifier.fillMaxWidth(),label={Text("Business name")},singleLine=true)
            OutlinedTextField(username,{username=it.take(32).removePrefix("@")},Modifier.fillMaxWidth(),label={Text("Business username")},singleLine=true,prefix={Text("@")})
            OutlinedTextField(category,{category=it.take(60)},Modifier.fillMaxWidth(),label={Text("Category")},singleLine=true)
            OutlinedTextField(description,{description=it.take(1000)},Modifier.fillMaxWidth(),label={Text("Business description")},minLines=3)
            OutlinedTextField(location,{location=it.take(160)},Modifier.fillMaxWidth(),label={Text("Location")},singleLine=true)
            OutlinedTextField(phone,{phone=it.take(30)},Modifier.fillMaxWidth(),label={Text("Business phone")},singleLine=true)
            OutlinedTextField(website,{website=it.take(200)},Modifier.fillMaxWidth(),label={Text("Website (HTTPS)")},singleLine=true)
            if (verified) Text("✓ Verified business",color=MaterialTheme.colorScheme.primary)
        }}
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Business overview",style=MaterialTheme.typography.titleMedium)
            Text("Active Marketplace listings: $activeListings"); Text("Advertising campaigns: $campaigns")
            Text("Campaign budget: ₦${budget / 100.0}"); Text("Campaign spend: ₦${spent / 100.0}")
        }}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick=onBack,modifier=Modifier.weight(1f)){Text("Back")}
            Button(enabled=!loading&&!saving&&name.isNotBlank()&&username.trim().removePrefix("@").isNotBlank()&&category.isNotBlank(),onClick={
                saving=true; message=null; scope.launch {
                    FynxBusinessClient.save(context,name.trim(),username.trim().removePrefix("@"),category.trim(),description.trim(),location.trim(),phone.trim(),website.trim())
                        .onSuccess { p -> verified=p.verified; message="Business profile saved."; load() }
                        .onFailure { message=it.message ?: "Unable to save business profile." }; saving=false
                }
            },modifier=Modifier.weight(1f)){Text(if(saving)"Saving…" else "Save Business")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick=onOpenAdvertising,modifier=Modifier.weight(1f)){Text("Advertise")}
            Button(onClick=onOpenDashboard,modifier=Modifier.weight(1f)){Text("Ad Dashboard")}
        }
        message?.let { Text(it,color=if(it.contains("unable",true))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    }
}
