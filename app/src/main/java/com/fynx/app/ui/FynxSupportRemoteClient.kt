package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/** Dedicated case-based support API. Support conversations are deliberately separate from personal DMs. */
object FynxSupportRemoteClient {
    data class SupportCase(
        val id: String,
        val caseNumber: String,
        val category: String,
        val subject: String,
        val status: String,
        val createdAt: String,
        val updatedAt: String,
        val assignedAdminId: String? = null,
        val requesterId: String? = null,
        val requesterUsername: String? = null,
        val requesterDisplayName: String? = null,
        val assignedUsername: String? = null,
        val assignedDisplayName: String? = null,
        val lastMessage: String? = null
    )

    data class SupportMessage(
        val id: String,
        val senderId: String,
        val username: String,
        val displayName: String,
        val body: String,
        val visibility: String = "PUBLIC",
        val createdAt: String
    )

    data class SupportEvent(
        val id: String,
        val eventType: String,
        val details: String,
        val createdAt: String,
        val username: String?
    )

    data class SupportAttachment(
        val id: String,
        val mediaId: String,
        val mimeType: String,
        val byteSize: Int,
        val createdAt: String
    )

    suspend fun createCase(
        context: Context,
        category: String,
        subject: String,
        description: String,
        clientRequestId: String = UUID.randomUUID().toString()
    ): Result<SupportCase> = FynxBackendClient.postJson(
        context,
        "/api/support/cases",
        JSONObject().put("category", category).put("subject", subject.trim())
            .put("description", description.trim()).put("clientRequestId", clientRequestId).toString()
    ).mapCatching { raw ->
        parseCase(JSONObject(raw).getJSONObject("case"))
    }

    suspend fun myCases(context: Context): Result<List<SupportCase>> =
        FynxBackendClient.get(context, "/api/support/cases/mine").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("cases") ?: org.json.JSONArray()
            buildList { for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(parseCase(it)) } }
        }

    suspend fun userMessages(context: Context, caseId: String): Result<List<SupportMessage>> =
        FynxBackendClient.get(context, "/api/support/cases/${encode(caseId)}/messages").mapCatching { raw ->
            parseMessages(JSONObject(raw).optJSONArray("messages"), includeInternal = false)
        }

    suspend fun replyAsUser(
        context: Context,
        caseId: String,
        body: String,
        clientMessageId: String = UUID.randomUUID().toString()
    ): Result<Unit> = FynxBackendClient.postJson(
        context,
        "/api/support/cases/${encode(caseId)}/messages",
        JSONObject().put("body", body.trim()).put("clientMessageId", clientMessageId).toString()
    ).map { Unit }

    suspend fun attachMedia(context: Context, caseId: String, mediaId: String): Result<Unit> =
        FynxBackendClient.postJson(
            context,
            "/api/support/cases/${encode(caseId)}/attachments",
            JSONObject().put("mediaId", mediaId.toLongOrNull() ?: JSONObject.NULL).toString()
        ).map { Unit }

    suspend fun attachments(context: Context, caseId: String): Result<List<SupportAttachment>> =
        FynxBackendClient.get(context, "/api/support/cases/${encode(caseId)}/attachments").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("attachments") ?: org.json.JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(SupportAttachment(item.optString("id"), item.optString("mediaId"), item.optString("mimeType"), item.optInt("byteSize"), item.optString("createdAt")))
                }
            }
        }

    fun attachmentUrl(caseId: String, attachmentId: String): String =
        "/api/support/cases/${encode(caseId)}/attachments/${encode(attachmentId)}"

    suspend fun adminCases(context: Context, status: String? = null): Result<List<SupportCase>> {
        val suffix = status?.takeIf { it.isNotBlank() }?.let { "?status=${encode(it)}" }.orEmpty()
        return FynxBackendClient.get(context, "/api/admin/support/cases$suffix").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("cases") ?: org.json.JSONArray()
            buildList { for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(parseCase(it)) } }
        }
    }

    suspend fun adminMessages(context: Context, caseId: String): Result<List<SupportMessage>> =
        FynxBackendClient.get(context, "/api/admin/support/cases/${encode(caseId)}/messages").mapCatching { raw ->
            parseMessages(JSONObject(raw).optJSONArray("messages"), includeInternal = true)
        }

    suspend fun claimCase(context: Context, caseId: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/admin/support/cases/${encode(caseId)}/assign", "{}").map { Unit }

    suspend fun assignCase(context: Context, caseId: String, adminId: String?): Result<Unit> =
        FynxBackendClient.postJson(
            context,
            "/api/admin/support/cases/${encode(caseId)}/assign",
            JSONObject().put("adminId", adminId ?: "").toString()
        ).map { Unit }

    suspend fun replyAsAdmin(
        context: Context,
        caseId: String,
        body: String,
        internal: Boolean = false,
        clientMessageId: String = UUID.randomUUID().toString()
    ): Result<Unit> = FynxBackendClient.postJson(
        context,
        "/api/admin/support/cases/${encode(caseId)}/messages",
        JSONObject().put("body", body.trim())
            .put("visibility", if (internal) "INTERNAL" else "PUBLIC")
            .put("clientMessageId", clientMessageId).toString()
    ).map { Unit }

    suspend fun updateStatus(context: Context, caseId: String, status: String): Result<Unit> =
        FynxBackendClient.patchJson(
            context,
            "/api/admin/support/cases/${encode(caseId)}/status",
            JSONObject().put("status", status).toString()
        ).map { Unit }

    suspend fun events(context: Context, caseId: String): Result<List<SupportEvent>> =
        FynxBackendClient.get(context, "/api/admin/support/cases/${encode(caseId)}/events").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("events") ?: org.json.JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(SupportEvent(item.optString("id"), item.optString("event_type"), item.optJSONObject("details")?.toString().orEmpty(), item.optString("created_at"), item.optString("username").takeIf(String::isNotBlank)))
                }
            }
        }

    private fun parseCase(item: JSONObject) = SupportCase(
        id = item.optString("id"),
        caseNumber = item.optString("case_number"),
        category = item.optString("category"),
        subject = item.optString("subject"),
        status = item.optString("status"),
        createdAt = item.optString("created_at"),
        updatedAt = item.optString("updated_at"),
        assignedAdminId = item.optString("assigned_admin_id").takeIf { it.isNotBlank() && it != "null" },
        requesterId = item.optString("requester_id").takeIf(String::isNotBlank),
        requesterUsername = item.optString("requester_username").takeIf(String::isNotBlank),
        requesterDisplayName = item.optString("requester_display_name").takeIf(String::isNotBlank),
        assignedUsername = item.optString("assigned_username").takeIf(String::isNotBlank),
        assignedDisplayName = item.optString("assigned_display_name").takeIf(String::isNotBlank),
        lastMessage = item.optString("last_message").takeIf(String::isNotBlank)
    )

    private fun parseMessages(array: org.json.JSONArray?, includeInternal: Boolean): List<SupportMessage> =
        buildList {
            if (array == null) return@buildList
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val visibility = item.optString("visibility", "PUBLIC")
                if (!includeInternal && visibility == "INTERNAL") continue
                add(SupportMessage(
                    id = item.optString("id"),
                    senderId = item.optString("senderId"),
                    username = item.optString("username"),
                    displayName = item.optString("displayName"),
                    body = item.optString("body"),
                    visibility = visibility,
                    createdAt = item.optString("createdAt")
                ))
            }
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
