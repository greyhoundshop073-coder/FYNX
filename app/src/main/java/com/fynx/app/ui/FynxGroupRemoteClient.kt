package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object FynxGroupRemoteClient {
    data class RemoteMessage(val id:String,val text:String,val senderUsername:String,val timestamp:Long,val attachmentMediaId:String?,val attachmentType:String?,val attachmentUrl:String?,val replyToId:String?=null,val reaction:String?=null,val edited:Boolean=false,val deleted:Boolean=false,val pinned:Boolean=false)
    data class RemotePermissions(val sendMessages:Boolean=true,val sendMedia:Boolean=true,val addMembers:Boolean=true,val inviteLinks:Boolean=true,val approveNewMembers:Boolean=false,val disappearingSeconds:Int=0)
    data class RemoteMember(val username:String,val role:String,val joinedAt:Long,val alreadyMember:Boolean=false)
    data class RemoteInvite(val groupId:String,val token:String,val name:String,val description:String,val memberCount:Int,val approveNewMembers:Boolean,val alreadyMember:Boolean,val pending:Boolean)
    data class RemoteJoinRequest(val username:String,val requestedAt:Long)
    data class RemotePollOption(val id:String,val text:String,val voteCount:Int)
    data class RemotePoll(val id:String,val question:String,val multipleChoice:Boolean,val closed:Boolean,val options:List<RemotePollOption>)
    data class RemotePost(val id:String,val text:String,val authorUsername:String,val marketplaceProductId:String?,val pinned:Boolean,val createdAt:Long,val poll:RemotePoll?)
    data class RemoteEvent(val id:String,val title:String,val description:String,val startsAt:Long,val endsAt:Long?,val location:String,val creatorUsername:String,val goingCount:Int,val maybeCount:Int,val notGoingCount:Int,val myResponse:String?)
    data class RemoteContent(val posts:List<RemotePost>,val events:List<RemoteEvent>)

    suspend fun syncGroup(context:Context,group:FynxGroup):Result<Unit> = runCatching { val body=JSONObject().apply{put("ownerUsername",group.ownerUsername.trim().removePrefix("@"));put("name",group.name);put("groupPhotoMediaId",group.groupPhotoMediaId ?: JSONObject.NULL);put("description",group.description);put("visibility",group.visibility.name);put("members",JSONArray().apply{group.members.forEach{member->put(JSONObject().apply{put("username",member.username.trim().removePrefix("@"));put("role",member.role.name)})}})};FynxBackendClient.postJson(context,"/api/groups/${group.id}/sync",body.toString()).getOrThrow();Unit }
    suspend fun updateGroupPhoto(context:Context,groupId:String,mediaId:String?):Result<String?> = runCatching { val body=JSONObject().apply{put("mediaId",mediaId?.trim()?.takeIf{it.isNotBlank()}?:JSONObject.NULL)}; val o=JSONObject(FynxBackendClient.patchJson(context,"/api/groups/$groupId/photo",body.toString()).getOrThrow()); o.optString("groupPhotoMediaId").takeIf{it.isNotBlank()&&it!="null"} }
    suspend fun loadMembers(context:Context,groupId:String):Result<List<RemoteMember>> = runCatching { val a=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/members").getOrThrow()).optJSONArray("members")?:JSONArray();buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(RemoteMember(o.optString("username"),o.optString("role","MEMBER"),o.optLong("joinedAt")))}} }
    suspend fun addMember(context:Context,groupId:String,username:String):Result<RemoteMember> = runCatching {
        val body=JSONObject().put("username",username.trim().removePrefix("@"))
        val root=JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/members",body.toString()).getOrThrow())
        val member=root.getJSONObject("member")
        RemoteMember(member.optString("username"),member.optString("role","MEMBER"),member.optLong("joinedAt"),root.optBoolean("alreadyMember",false))
    }
    suspend fun removeMember(context:Context,groupId:String,username:String):Result<Unit> = runCatching {
        FynxBackendClient.delete(context,"/api/groups/$groupId/members/${Uri.encode(username.trim().removePrefix("@"))}").getOrThrow()
        Unit
    }
    suspend fun promoteMember(context:Context,groupId:String,username:String):Result<Unit> = runCatching {
        FynxBackendClient.postJson(context,"/api/groups/$groupId/members/${Uri.encode(username.trim().removePrefix("@"))}/promote",JSONObject().toString()).getOrThrow()
        Unit
    }
    suspend fun demoteMember(context:Context,groupId:String,username:String):Result<Unit> = runCatching {
        FynxBackendClient.postJson(context,"/api/groups/$groupId/members/${Uri.encode(username.trim().removePrefix("@"))}/demote",JSONObject().toString()).getOrThrow()
        Unit
    }
    suspend fun loadPermissions(context:Context,groupId:String):Result<RemotePermissions> = runCatching { val o=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/permissions").getOrThrow()).optJSONObject("permissions")?:JSONObject();RemotePermissions(o.optBoolean("send_messages",true),o.optBoolean("send_media",true),o.optBoolean("add_members",true),o.optBoolean("invite_links",true),o.optBoolean("approve_new_members",false),o.optInt("disappearing_seconds",0)) }
    suspend fun updatePermissions(context:Context,groupId:String,permissions:RemotePermissions):Result<RemotePermissions> = runCatching { val body=JSONObject().apply{put("sendMessages",permissions.sendMessages);put("sendMedia",permissions.sendMedia);put("addMembers",permissions.addMembers);put("inviteLinks",permissions.inviteLinks);put("approveNewMembers",permissions.approveNewMembers);put("disappearingSeconds",permissions.disappearingSeconds)};val o=JSONObject(FynxBackendClient.patchJson(context,"/api/groups/$groupId/permissions",body.toString()).getOrThrow()).optJSONObject("permissions")?:JSONObject();RemotePermissions(o.optBoolean("send_messages",permissions.sendMessages),o.optBoolean("send_media",permissions.sendMedia),o.optBoolean("add_members",permissions.addMembers),o.optBoolean("invite_links",permissions.inviteLinks),o.optBoolean("approve_new_members",permissions.approveNewMembers),o.optInt("disappearing_seconds",permissions.disappearingSeconds)) }
    suspend fun createInvite(context:Context,groupId:String):Result<Pair<String,String>> = runCatching { val o=JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/invites",JSONObject().toString()).getOrThrow());o.getString("token") to o.getString("inviteUri") }
    suspend fun revokeInvite(context:Context,groupId:String,token:String):Result<Unit> = runCatching { FynxBackendClient.postJson(context,"/api/groups/$groupId/invites/${Uri.encode(token)}/revoke",JSONObject().toString()).getOrThrow();Unit }
    suspend fun previewInvite(context:Context,groupId:String,token:String):Result<RemoteInvite> = runCatching { val o=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/invites/${Uri.encode(token)}").getOrThrow());val i=o.getJSONObject("invite");RemoteInvite(i.getString("groupId"),i.getString("token"),i.optString("name","Group"),i.optString("description"),i.optInt("memberCount"),i.optBoolean("approveNewMembers"),o.optBoolean("alreadyMember"),o.optBoolean("pending")) }
    suspend fun joinInvite(context:Context,groupId:String,token:String):Result<JSONObject> = runCatching { JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/invites/${Uri.encode(token)}/join",JSONObject().toString()).getOrThrow()) }
    suspend fun requestInviteJoin(context:Context,groupId:String,token:String):Result<JSONObject> = runCatching { JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/invites/${Uri.encode(token)}/request",JSONObject().toString()).getOrThrow()) }
    suspend fun loadJoinRequests(context:Context,groupId:String):Result<List<RemoteJoinRequest>> = runCatching { val a=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/join-requests").getOrThrow()).optJSONArray("requests")?:JSONArray();buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(RemoteJoinRequest(o.optString("username"),o.optLong("requestedAt")))}} }
    suspend fun approveJoinRequest(context:Context,groupId:String,username:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/join-requests/${Uri.encode(username)}/approve",JSONObject().toString()).getOrThrow();Unit}
    suspend fun rejectJoinRequest(context:Context,groupId:String,username:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/join-requests/${Uri.encode(username)}/reject",JSONObject().toString()).getOrThrow();Unit}
    suspend fun loadMessages(context:Context,groupId:String):Result<List<RemoteMessage>> = runCatching {
        val a=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/messages").getOrThrow()).optJSONArray("messages")?:JSONArray()
        buildList {
            for(i in 0 until a.length()) {
                runCatching {
                    val o=a.getJSONObject(i)
                    val rawId=o.optString("id").trim()
                    val safeId=rawId.ifBlank { "group-remote-$i-" + o.optLong("timestamp") }
                    RemoteMessage(safeId,o.optString("text"),o.optString("senderUsername"),o.optLong("timestamp"),o.optString("attachmentMediaId").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentType").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentUrl").takeIf{it.isNotBlank()&&it!="null"},o.optString("replyToId").takeIf{it.isNotBlank()&&it!="null"},o.optString("reaction").takeIf{it.isNotBlank()&&it!="null"},o.optBoolean("edited"),o.optBoolean("deleted"),o.optBoolean("pinned"))
                }.getOrNull()?.let(::add)
            }
        }.distinctBy { it.id }
    }
    suspend fun sendMessage(context:Context,groupId:String,message:ChatMessage):Result<RemoteMessage> = runCatching {
        val chatKey = "group_$groupId"
        val persisted = FynxChatStore.load(context, chatKey).firstOrNull { it.id == message.id }
        var mediaId: String? = message.mediaId ?: persisted?.mediaId
            ?: message.attachmentUri?.substringAfterLast('/')?.takeIf { it.all(Char::isDigit) == true }
        var mediaType = message.attachmentType ?: persisted?.attachmentType

        // Upload once and cache the remote media ID before any retry of the message POST.
        // Both POST attempts retain the same message ID and attachment media ID.
        if (mediaId == null && message.attachmentUri != null) {
            val uri = Uri.parse(message.attachmentUri!!)
            val mime = groupAttachmentMimeType(context, uri, mediaType)
            val uploaded = FynxProductionMessaging.uploadMedia(context, uri, mime).getOrThrow()
            mediaId = uploaded.id
            mediaType = mediaType ?: mime.substringBefore('/')
            val saved = FynxChatStore.load(context, chatKey)
            if (saved.any { it.id == message.id }) {
                FynxChatStore.save(context, chatKey, saved.map {
                    if (it.id == message.id) it.copy(mediaId = mediaId, attachmentType = mediaType) else it
                })
            }
        }

        val body = JSONObject().apply {
            put("id", message.id)
            put("text", message.text)
            if (mediaId != null) put("attachmentMediaId", mediaId)
            if (mediaType != null) put("attachmentType", mediaType)
            if (message.replyToId != null) put("replyToId", message.replyToId)
        }

        suspend fun postGroupMessage(): RemoteMessage {
            val response = FynxBackendClient.postJson(
                context, "/api/groups/$groupId/messages", body.toString()
            ).getOrThrow()
            return toRemoteMessage(JSONObject(response).getJSONObject("message"))
        }

        val firstResult = runCatching { postGroupMessage() }
        firstResult.getOrElse { firstError ->
            // A response can be lost after the server commits. Only replay the stable ID when
            // authenticated history confirms that this message is already persisted.
            val persistedOnServer = loadMessages(context, groupId).getOrNull()
                ?.firstOrNull { it.id == message.id }
            if (persistedOnServer == null) throw firstError

            // Idempotent backend replay re-broadcasts the canonical ID, recovering a transient
            // realtime publish failure. A second error remains visible as retryable failure.
            runCatching { postGroupMessage() }.getOrElse { retryError ->
                if (retryError !== firstError) retryError.addSuppressed(firstError)
                throw retryError
            }
        }
    }
    suspend fun editMessage(context:Context,groupId:String,messageId:String,text:String):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","edit");put("text",text)})
    suspend fun reactToMessage(context:Context,groupId:String,messageId:String,reaction:String?):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","reaction");if(reaction!=null)put("reaction",reaction)})
    suspend fun setPinned(context:Context,groupId:String,messageId:String,pinned:Boolean):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","pin");put("pinned",pinned)})
    suspend fun deleteMessage(context:Context,groupId:String,messageId:String):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","delete")})
    private suspend fun messageAction(context:Context,groupId:String,messageId:String,body:JSONObject):Result<RemoteMessage> = runCatching{toRemoteMessage(JSONObject(FynxBackendClient.patchJson(context,"/api/groups/$groupId/messages/${Uri.encode(messageId)}",body.toString()).getOrThrow()).getJSONObject("message"))}

    suspend fun loadContent(context:Context,groupId:String):Result<RemoteContent> = runCatching {
        val root=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/content").getOrThrow())
        val posts=root.optJSONArray("posts")?:JSONArray();val events=root.optJSONArray("events")?:JSONArray()
        val mappedPosts=buildList { for(i in 0 until posts.length()){val p=posts.getJSONObject(i);val po=p.optJSONObject("poll");val options=buildList{if(po!=null){val a=po.optJSONArray("options")?:JSONArray();for(j in 0 until a.length()){val o=a.getJSONObject(j);add(RemotePollOption(o.getString("id"),o.optString("text"),o.optInt("voteCount")))}}};add(RemotePost(p.getString("id"),p.optString("text"),p.optString("authorUsername"),p.optString("marketplaceProductId").takeIf{it.isNotBlank()&&it!="null"},p.optBoolean("pinned"),p.optLong("createdAt"),po?.let{RemotePoll(it.getString("id"),it.optString("question"),it.optBoolean("multipleChoice"),it.optBoolean("closed"),options)}))} }
        val mappedEvents=buildList { for(i in 0 until events.length()){val e=events.getJSONObject(i);add(RemoteEvent(e.getString("id"),e.optString("title"),e.optString("description"),e.optLong("startsAt"),e.optLong("endsAt").takeIf{it>0},e.optString("location"),e.optString("creatorUsername"),e.optInt("goingCount"),e.optInt("maybeCount"),e.optInt("notGoingCount"),e.optString("myResponse").takeIf{it.isNotBlank()}))} }
        RemoteContent(mappedPosts,mappedEvents)
    }
    suspend fun createPoll(context:Context,groupId:String,question:String,options:List<String>,multipleChoice:Boolean):Result<Unit> = runCatching { val body=JSONObject().apply{put("question",question);put("multipleChoice",multipleChoice);put("options",JSONArray(options))};FynxBackendClient.postJson(context,"/api/groups/$groupId/polls",body.toString()).getOrThrow();Unit }
    suspend fun votePoll(context:Context,groupId:String,pollId:String,optionId:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/polls/${Uri.encode(pollId)}/votes",JSONObject().apply{put("optionId",optionId)}.toString()).getOrThrow();Unit}
    suspend fun setGroupPostPinned(context:Context,groupId:String,postId:String,pinned:Boolean):Result<Unit> = runCatching {FynxBackendClient.patchJson(context,"/api/groups/$groupId/posts/${Uri.encode(postId)}/pin",JSONObject().apply{put("pinned",pinned)}.toString()).getOrThrow();Unit}
    suspend fun createEvent(context:Context,groupId:String,title:String,description:String,startsAt:String,location:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/events",JSONObject().apply{put("title",title);put("description",description);put("startsAt",startsAt);put("location",location)}.toString()).getOrThrow();Unit}
    suspend fun rsvpEvent(context:Context,groupId:String,eventId:String,response:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/events/${Uri.encode(eventId)}/rsvp",JSONObject().apply{put("response",response)}.toString()).getOrThrow();Unit}

    private fun toRemoteMessage(o:JSONObject):RemoteMessage {
        val rawId=o.optString("id").trim()
        val safeId=rawId.ifBlank { "group-response-" + o.optLong("timestamp") + "-" + o.optString("senderUsername").hashCode() }
        return RemoteMessage(safeId,o.optString("text"),o.optString("senderUsername"),o.optLong("timestamp"),o.optString("attachmentMediaId").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentType").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentUrl").takeIf{it.isNotBlank()&&it!="null"},o.optString("replyToId").takeIf{it.isNotBlank()&&it!="null"},o.optString("reaction").takeIf{it.isNotBlank()&&it!="null"},o.optBoolean("edited"),o.optBoolean("deleted"),o.optBoolean("pinned"))
    }
    fun toChatMessage(m:RemoteMessage,currentUsername:String,baseUrl:String)=ChatMessage(m.text,m.senderUsername.equals(currentUsername.removePrefix("@"),true),m.id,m.timestamp,delivered=true,read=true,replyToId=m.replyToId,reaction=m.reaction,edited=m.edited,attachmentUri=m.attachmentUrl?.let{if(it.startsWith("http"))it else baseUrl.trimEnd('/')+it},attachmentType=m.attachmentType,senderUsername=m.senderUsername,pinned=m.pinned)
}


private fun groupAttachmentMimeType(context: Context, uri: Uri, mediaType: String?): String {
    context.contentResolver.getType(uri)?.trim()?.lowercase()?.takeIf { '/' in it }?.let { return it }
    val extension = (uri.lastPathSegment.orEmpty().substringAfterLast('.', "")).lowercase()
    val byExtension = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "pdf" -> "application/pdf"
        "txt", "log", "csv" -> "text/plain"
        "zip" -> "application/zip"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> null
    }
    if (byExtension != null) return byExtension
    return when (mediaType) {
        "video", "video_note" -> "video/mp4"
        "audio" -> "audio/mp4"
        else -> "application/octet-stream"
    }
}
