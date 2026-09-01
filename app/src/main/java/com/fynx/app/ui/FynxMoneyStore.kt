package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FynxMoneyTransaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val type: String,
    val date: String,
    val note: String = ""
)

data class FynxMoneyAccount(
    val id: Long,
    val name: String,
    val type: String,
    val balance: Double
)

object FynxMoneyStore {
    private const val PREFS = "fynx_money_store"
    private const val TRANSACTIONS = "transactions"
    private const val ACCOUNTS = "accounts"

    fun loadTransactions(context: Context): List<FynxMoneyTransaction> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TRANSACTIONS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                val amount = o.optDouble("amount", Double.NaN)
                val type = o.optString("type", "Expense")
                if (title.isNotBlank() && amount.isFinite() && amount > 0 && (type == "Income" || type == "Expense")) {
                    add(FynxMoneyTransaction(o.optLong("id", i.toLong()), title, amount, type, o.optString("date"), o.optString("note")))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun saveTransactions(context: Context, values: List<FynxMoneyTransaction>) {
        val array = JSONArray()
        values.takeLast(200).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("amount", item.amount)
                put("type", item.type)
                put("date", item.date)
                put("note", item.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TRANSACTIONS, array.toString()).apply()
    }

    fun addTransaction(context: Context, transaction: FynxMoneyTransaction) {
        saveTransactions(context, loadTransactions(context) + transaction)
    }

    fun loadAccounts(context: Context): List<FynxMoneyAccount> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACCOUNTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val type = o.optString("type", "Cash")
                val balance = o.optDouble("balance", Double.NaN)
                if (name.isNotBlank() && balance.isFinite()) {
                    add(FynxMoneyAccount(o.optLong("id", i.toLong()), name, type, balance))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun saveAccounts(context: Context, values: List<FynxMoneyAccount>) {
        val array = JSONArray()
        values.takeLast(50).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("type", item.type)
                put("balance", item.balance)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACCOUNTS, array.toString()).apply()
    }

    fun addAccount(context: Context, account: FynxMoneyAccount) {
        saveAccounts(context, loadAccounts(context) + account)
    }
}
