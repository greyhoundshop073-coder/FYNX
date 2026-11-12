package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Separate, authenticated support-case transport. Support conversations never share ordinary DM records. */
object FynxSupportRemoteClient {
    data class Access(val role: String, val supportAccess: Boolean)
    data class SupportCase(
        val id: String,
        val caseNumber: String,
        val userId: String,
        val username: String,
        val displayName: String,
        val subject: String,
        val category: String,
        val description: String,
        val status: String,
        val assignedAdminId: String?,
        val assignedAdminUsername: String?,
        val assignedAdminName: String?,
        val createdAt: String,
        val updatedAt: String,
        val lastMessageAt: String,
        val resolvedAt: String?
    )
    data class Message(
        val id: String,
        val senderId: String,
        val senderRole: String,
        val visibility: String,
        val message: String,
        val username: String,
        val displayName: String,
        val createdAt: String
    )
    data class Event(
        val type: String,
        val actorRole: String,
        val username: String,
        val details: JSONObject,
        val createdAt: String
    )
    data class Detail(val supportCase: SupportCase, val messages: List<Message>, val events: List<Event>)
    data class Counts(val open: Int, val inReview: Int, val waitingUser: Int, val resolved: Int, val unassigned: Int)
    data class Inbox(val cases: List<SupportCase>, val counts: Counts)
    data class Admin(val id: String, val username: String, val displayName: String, val supportAccess: Boolean, val grantedAt: String)

    suspend fun access(context: Context): Result<Access> =
        FynxBackendClient.get(context, "/api/support/access").mapCatching { raw ->
            val value = JSONObject(raw)
            Access(value.optString("role", "USER"), value.optBoolean("supportAccess"))
        }

    suspend fun myCases(context: Context): Result<List<SupportCase>> =
        FynxBackendClient.get(context, "/api/support/cases").mapCatching { raw ->
            parseCases(JSONObject(raw).optJSONArray("cases"))
        }

    suspend fun createCase(context: Context, subject: String, category: String, description: String, requestKey: String): Result<SupportCase> =
        FynxBackendClient.postJson(
            context,
            "/api/support/cases",
            JSONObject().put("subject", subject.trim()).put("category", category).put("description", description.trim()).put("requestKey", requestKey).toString()
        ).mapCatching { raw ->
            val value = JSONObject(raw).optJSONObject("supportCase") ?: throw IllegalStateException("FYNX did not return the saved support request.")
            parseCase(value)
        }

    suspend fun myCaseDetail(context: Context, id: String): Result<Detail> =
        FynxBackendClient.get(context, "/api/support/cases/${encode(id)}").mapCatching { raw -> parseDetail(JSONObject(raw)) }

    suspend fun sendUserMessage(context: Context, id: String, message: String, requestKey: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/support/cases/${encode(id)}/messages", JSONObject().put("message", message.trim()).put("requestKey", requestKey).toString()).map { Unit }

    suspend fun inbox(context: Context, status: String? = null): Result<Inbox> {
        val suffix = status?.takeIf { it.isNotBlank() }?.let { "?status=${encode(it)}" }.orEmpty()
        return FynxBackendClient.get(context, "/api/admin/support/cases$suffix").mapCatching { raw ->
            val value = JSONObject(raw)
            val counts = value.optJSONObject("counts") ?: JSONObject()
            Inbox(
                parseCases(value.optJSONArray("cases")),
                Counts(counts.optInt("open"), counts.optInt("in_review"), counts.optInt("waiting_user"), counts.optInt("resolved"), counts.optInt("unassigned"))
            )
        }
    }

    suspend fun staffCaseDetail(context: Context, id: String): Result<Detail> =
        FynxBackendClient.get(context, "/api/admin/support/cases/${encode(id)}").mapCatching { raw -> parseDetail(JSONObject(raw)) }

    suspend fun claim(context: Context, id: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/admin/support/cases/${encode(id)}/claim", "{}").map { Unit }

    suspend fun assign(context: Context, id: String, adminUserId: String?): Result<Unit> =
        FynxBackendClient.patchJson(
            context,
            "/api/admin/support/cases/${encode(id)}/assign",
            JSONObject().put("adminUserId", adminUserId?.let { it.toLongOrNull() ?: throw IllegalArgumentException("Invalid Admin account.") }).toString()
        ).map { Unit }

    suspend fun setStatus(context: Context, id: String, status: String): Result<Unit> =
        FynxBackendClient.patchJson(context, "/api/admin/support/cases/${encode(id)}/status", JSONObject().put("status", status).toString()).map { Unit }

    suspend fun sendStaffMessage(context: Context, id: String, message: String, internal: Boolean, requestKey: String): Result<Unit> =
        FynxBackendClient.postJson(
            context,
            "/api/admin/support/cases/${encode(id)}/messages",
            JSONObject().put("message", message.trim()).put("visibility", if (internal) "INTERNAL" else "PUBLIC").put("requestKey", requestKey).toString()
        ).map { Unit }

    suspend fun admins(context: Context): Result<List<Admin>> =
        FynxBackendClient.get(context, "/api/admin/support/admins").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("admins") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(Admin(item.optString("id"), item.optString("username"), item.optString("displayName"), item.optBoolean("supportAccess"), item.optString("grantedAt")))
                }
            }
        }

    suspend fun setAdminAccess(context: Context, userId: String, enabled: Boolean): Result<Unit> =
        FynxBackendClient.patchJson(
            context,
            "/api/admin/support/admins/${encode(userId)}/access",
            JSONObject().put("enabled", enabled).toString()
        ).map { Unit }

    private fun parseCases(array: JSONArray?): List<SupportCase> = buildList {
        if (array != null) for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(parseCase(item))
        }
    }

    private fun parseCase(item: JSONObject): SupportCase = SupportCase(
        id = item.optString("id"),
        caseNumber = item.optString("caseNumber"),
        userId = item.optString("userId"),
        username = item.optString("username"),
        displayName = item.optString("displayName"),
        subject = item.optString("subject"),
        category = item.optString("category"),
        description = item.optString("description"),
        status = item.optString("status"),
        assignedAdminId = item.optString("assignedAdminId").takeUnless { it.isBlank() || it == "null" },
        assignedAdminUsername = item.optString("assignedAdminUsername").takeUnless { it.isBlank() || it == "null" },
        assignedAdminName = item.optString("assignedAdminName").takeUnless { it.isBlank() || it == "null" },
        createdAt = item.optString("createdAt"),
        updatedAt = item.optString("updatedAt"),
        lastMessageAt = item.optString("lastMessageAt"),
        resolvedAt = item.optString("resolvedAt").takeUnless { it.isBlank() || it == "null" }
    )

    private fun parseDetail(value: JSONObject): Detail {
        val supportCase = value.optJSONObject("supportCase") ?: throw IllegalStateException("Support request details were not returned.")
        val messages = buildList {
            val array = value.optJSONArray("messages") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(Message(item.optString("id"), item.optString("senderId"), item.optString("senderRole"), item.optString("visibility"), item.optString("message"), item.optString("username"), item.optString("displayName"), item.optString("createdAt")))
            }
        }
        val events = buildList {
            val array = value.optJSONArray("events") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(Event(item.optString("type"), item.optString("actorRole"), item.optString("username"), item.optJSONObject("details") ?: JSONObject(), item.optString("createdAt")))
            }
        }
        return Detail(parseCase(supportCase), messages, events)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
