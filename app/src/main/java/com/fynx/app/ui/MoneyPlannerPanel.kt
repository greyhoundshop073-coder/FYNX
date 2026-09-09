package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private data class PlannerTransaction(val id:String,val title:String,val category:String,val type:String,val amount:Double,val currency:String,val date:String)
private data class PlannerBudget(val id:String,val category:String,val amount:Double,val currency:String,val period:String)
private data class PlannerGoal(val id:String,val name:String,val target:Double,val saved:Double,val currency:String,val targetDate:String?)
private data class PlannerRecurring(val id:String,val name:String,val category:String,val amount:Double,val currency:String,val frequency:String,val nextDate:String)
private data class PlannerCurrencySummary(val currency:String,val income:Double,val expenses:Double,val net:Double)

@Composable
fun MoneyPlannerPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var transactions by remember { mutableStateOf(emptyList<PlannerTransaction>()) }
    var budgets by remember { mutableStateOf(emptyList<PlannerBudget>()) }
    var goals by remember { mutableStateOf(emptyList<PlannerGoal>()) }
    var recurring by remember { mutableStateOf(emptyList<PlannerRecurring>()) }
    var currencySummaries by remember { mutableStateOf(emptyList<PlannerCurrencySummary>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Overview") }
    var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("General") }; var txType by remember { mutableStateOf("EXPENSE") }
    var budgetCategory by remember { mutableStateOf("") }; var budgetAmount by remember { mutableStateOf("") }
    var goalName by remember { mutableStateOf("") }; var goalTarget by remember { mutableStateOf("") }; var goalDate by remember { mutableStateOf("") }
    var recurringName by remember { mutableStateOf("") }; var recurringAmount by remember { mutableStateOf("") }; var recurringDate by remember { mutableStateOf("") }

    fun refresh() = scope.launch {
        loading = true; error = null
        FynxBackendClient.get(context, "/api/money-planner").onSuccess { raw ->
            runCatching {
                val root=JSONObject(raw)
                val tx=root.optJSONArray("transactions") ?: JSONArray(); val bs=root.optJSONArray("budgets") ?: JSONArray(); val gs=root.optJSONArray("goals") ?: JSONArray(); val rs=root.optJSONArray("recurring") ?: JSONArray()
                transactions=buildList { for(i in 0 until tx.length()){val o=tx.getJSONObject(i);add(PlannerTransaction(o.getString("id"),o.getString("title"),o.getString("category"),o.getString("type"),o.getDouble("amount"),o.getString("currency"),o.getString("occurredOn"))) } }
                budgets=buildList { for(i in 0 until bs.length()){val o=bs.getJSONObject(i);add(PlannerBudget(o.getString("id"),o.getString("category"),o.getDouble("amount"),o.getString("currency"),o.getString("period"))) } }
                goals=buildList { for(i in 0 until gs.length()){val o=gs.getJSONObject(i);add(PlannerGoal(o.getString("id"),o.getString("name"),o.getDouble("targetAmount"),o.getDouble("savedAmount"),o.getString("currency"),if(o.isNull("targetDate"))null else o.getString("targetDate"))) } }
                recurring=buildList { for(i in 0 until rs.length()){val o=rs.getJSONObject(i);add(PlannerRecurring(o.getString("id"),o.getString("name"),o.getString("category"),o.getDouble("amount"),o.getString("currency"),o.getString("frequency"),o.getString("nextDate"))) } }
                val summaryRoot=root.optJSONObject("summaryByCurrency")
                currencySummaries=if(summaryRoot==null) emptyList() else buildList { val keys=summaryRoot.keys(); while(keys.hasNext()){val key=keys.next();val o=summaryRoot.getJSONObject(key);add(PlannerCurrencySummary(o.getString("currency"),o.getDouble("income"),o.getDouble("expenses"),o.getDouble("net"))) } }.sortedBy{it.currency}
            }.onFailure { error=it.message ?: "Could not read Money Planner data" }
        }.onFailure { error=it.message ?: "Money Planner is unavailable" }
        loading=false
    }
    LaunchedEffect(Unit) { refresh() }

    val primarySummary=currencySummaries.firstOrNull{it.currency=="NGN"} ?: currencySummaries.firstOrNull()
    val filteredTx=transactions.filter{query.isBlank()||it.title.contains(query,true)||it.category.contains(query,true)}

    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){ Column{Text("Money Planner 💰",style=MaterialTheme.typography.headlineSmall);Text("Plan, track and understand your money",color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick={refresh()}){Text("Refresh")}}
        if(!FynxBackendClient.hasAccessToken(context)) Text("Sign in to sync your personal Money Planner securely.",color=MaterialTheme.colorScheme.error)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Overview","Transactions","Budgets","Savings","Recurring").forEach{v->FilterChip(selected=tab==v,onClick={tab=v},label={Text(v)})}}
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when(tab){
            "Overview" -> {
                if (currencySummaries.isEmpty()) Text("No money activity recorded yet.", color=MaterialTheme.colorScheme.onSurfaceVariant)
                currencySummaries.forEach { summary ->
                    Text("${summary.currency}", style=MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){MetricCard("Income",summary.income,summary.currency,Modifier.weight(1f));MetricCard("Expenses",summary.expenses,summary.currency,Modifier.weight(1f));MetricCard("Net",summary.net,summary.currency,Modifier.weight(1f))}
                }
                Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("Planner snapshot",style=MaterialTheme.typography.titleMedium);Text("${transactions.size} transactions • ${budgets.size} budgets • ${goals.size} savings goals • ${recurring.size} recurring expenses");Text("Amounts remain separated by currency; FYNX never adds USD, NGN or other currencies together.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant);val recurringByCurrency=recurring.groupBy{it.currency}.entries.joinToString(" • "){(c,v)->"$c ${money(v.sumOf{it.amount})}"};if(recurringByCurrency.isNotBlank())Text("Planned recurring: $recurringByCurrency");val budgetsByCurrency=budgets.groupBy{it.currency}.entries.joinToString(" • "){(c,v)->"$c ${money(v.sumOf{it.amount})}"};if(budgetsByCurrency.isNotBlank())Text("Budget limits: $budgetsByCurrency")}}
                primarySummary?.let { Text("Primary view: ${it.currency} • net ${money(it.net)}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Recent activity",style=MaterialTheme.typography.titleMedium); transactions.take(5).forEach{PlannerTransactionRow(it,onDelete={scope.launch{FynxBackendClient.delete(context,"/api/money-planner/transactions/${it.id}");refresh()}})}
            }
            "Transactions" -> {
                OutlinedTextField(query,{query=it},label={Text("Search transactions")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(title,{title=it},label={Text("Title")},singleLine=true,modifier=Modifier.fillMaxWidth()); OutlinedTextField(amount,{amount=it},label={Text("Amount")},singleLine=true,modifier=Modifier.fillMaxWidth()); OutlinedTextField(category,{category=it},label={Text("Category")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=txType=="INCOME",onClick={txType="INCOME"},label={Text("Income")});FilterChip(selected=txType=="EXPENSE",onClick={txType="EXPENSE"},label={Text("Expense")});Button(enabled=title.isNotBlank()&&(amount.toDoubleOrNull()?:0.0)>0,onClick={scope.launch{val body=JSONObject().apply{put("title",title);put("category",category);put("amount",amount.toDouble());put("type",txType);put("currency","NGN")};FynxBackendClient.postJson(context,"/api/money-planner/transactions",body.toString()).onSuccess{title="";amount="";refresh()}.onFailure{error=it.message}}}){Text("Add")}}
                LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.weight(1f)){items(filteredTx,key={it.id}){PlannerTransactionRow(it,onDelete={scope.launch{FynxBackendClient.delete(context,"/api/money-planner/transactions/${it.id}");refresh()}})}}
            }
            "Budgets" -> { OutlinedTextField(budgetCategory,{budgetCategory=it},label={Text("Category")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(budgetAmount,{budgetAmount=it},label={Text("Monthly limit")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(enabled=budgetCategory.isNotBlank()&&(budgetAmount.toDoubleOrNull()?:0.0)>0,onClick={scope.launch{val b=JSONObject().apply{put("category",budgetCategory);put("amount",budgetAmount.toDouble());put("currency","NGN");put("period","MONTHLY")};FynxBackendClient.postJson(context,"/api/money-planner/budgets",b.toString()).onSuccess{budgetCategory="";budgetAmount="";refresh()}.onFailure{error=it.message}}}){Text("Save Budget")};LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.weight(1f)){items(budgets,key={it.id}){b->Card(Modifier.fillMaxWidth()){ListItem(headlineContent={Text(b.category)},supportingContent={Text("${b.period.lowercase()} limit • ${b.currency}")},trailingContent={Row{Text(money(b.amount));TextButton(onClick={scope.launch{FynxBackendClient.delete(context,"/api/money-planner/budgets/${b.id}");refresh()}}){Text("Delete")}}})}}}}
            "Savings" -> { OutlinedTextField(goalName,{goalName=it},label={Text("Goal name")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(goalTarget,{goalTarget=it},label={Text("Target amount")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(goalDate,{goalDate=it},label={Text("Target date (YYYY-MM-DD, optional)")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(enabled=goalName.isNotBlank()&&(goalTarget.toDoubleOrNull()?:0.0)>0,onClick={scope.launch{val g=JSONObject().apply{put("name",goalName);put("targetAmount",goalTarget.toDouble());put("currency","NGN");if(goalDate.isNotBlank())put("targetDate",goalDate)};FynxBackendClient.postJson(context,"/api/money-planner/goals",g.toString()).onSuccess{goalName="";goalTarget="";goalDate="";refresh()}.onFailure{error=it.message}}}){Text("Create Goal")};LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.weight(1f)){items(goals,key={it.id}){g->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(g.name,style=MaterialTheme.typography.titleMedium);Text("${money(g.saved)} / ${money(g.target)} ${g.currency}")};LinearProgressIndicator(progress={(g.saved/g.target).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth());Text("${if(g.targetDate!=null)"Target ${g.targetDate}" else "No target date"}");Row{var add by remember{mutableStateOf("")};OutlinedTextField(add,{add=it},label={Text("Add saved")},singleLine=true,modifier=Modifier.weight(1f));Button(enabled=(add.toDoubleOrNull()?:0.0)>0,onClick={scope.launch{val j=JSONObject().apply{put("amount",add.toDouble())};FynxBackendClient.postJson(context,"/api/money-planner/goals/${g.id}/contributions",j.toString()).onSuccess{refresh()}}}){Text("Add")};TextButton(onClick={scope.launch{FynxBackendClient.delete(context,"/api/money-planner/goals/${g.id}");refresh()}}){Text("Delete")}}}}}}}
            "Recurring" -> { OutlinedTextField(recurringName,{recurringName=it},label={Text("Expense name")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(recurringAmount,{recurringAmount=it},label={Text("Amount")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(recurringDate,{recurringDate=it},label={Text("Next date (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(enabled=recurringName.isNotBlank()&&(recurringAmount.toDoubleOrNull()?:0.0)>0&&recurringDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),onClick={scope.launch{val j=JSONObject().apply{put("name",recurringName);put("category","Bills");put("amount",recurringAmount.toDouble());put("currency","NGN");put("frequency","MONTHLY");put("nextDate",recurringDate)};FynxBackendClient.postJson(context,"/api/money-planner/recurring",j.toString()).onSuccess{recurringName="";recurringAmount="";recurringDate="";refresh()}.onFailure{error=it.message}}}){Text("Add Recurring")};LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.weight(1f)){items(recurring,key={it.id}){r->Card(Modifier.fillMaxWidth()){ListItem(headlineContent={Text(r.name)},supportingContent={Text("${r.frequency.lowercase()} • next ${r.nextDate} • ${r.currency}")},trailingContent={Row{Text(money(r.amount));TextButton(onClick={scope.launch{FynxBackendClient.delete(context,"/api/money-planner/recurring/${r.id}");refresh()}}){Text("Delete")}}})}}}}
        }
    }
}

@Composable private fun MetricCard(label:String,value:Double,currency:String,modifier:Modifier=Modifier)=Card(modifier){Column(Modifier.padding(12.dp)){Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(value),style=MaterialTheme.typography.titleLarge);Text(currency,style=MaterialTheme.typography.bodySmall)}}
@Composable private fun PlannerTransactionRow(t:PlannerTransaction,onDelete:()->Unit)=Card(Modifier.fillMaxWidth()){ListItem(headlineContent={Text(t.title)},supportingContent={Text("${t.category} • ${t.date}")},trailingContent={Row{Text("${if(t.type=="EXPENSE")"-" else "+"}${money(t.amount)} ${t.currency}");TextButton(onClick=onDelete){Text("Delete")}}})}
private fun money(value:Double):String=NumberFormat.getNumberInstance(Locale.US).apply{minimumFractionDigits=2;maximumFractionDigits=2}.format(value)