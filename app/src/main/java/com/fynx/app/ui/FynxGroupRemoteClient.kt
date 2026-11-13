package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object FynxGroupRemoteClient {
    data class RemoteMessage(val id:String,val text:String,val senderUsername:String,val timestamp:Long,val attachmentMediaId:String?,val attachmentType:String?,val attachmentUrl:String?,val replyToId:String?=null,val reaction:String?=null,val edited:Boolean=false,val deleted:Boolean=false,val pinned:Boolean=false,val readBy:List<String> = emptyList())
    data class RemoteMessagePage(val messages:List<RemoteMessage>,val hasMore:Boolean)
    private val sendMutex = Mutex()
    private val rsvpMutex = Mutex()
    data class RemotePermissions(val sendMessages:Boolean=true,val sendMedia:Boolean=true,val addMembers:Boolean=true,val inviteLinks:Boolean=true,val approveNewMembers:Boolean=false,val disappearingSeconds:Int=0,val showHistoryToNewMembers:Boolean=true)
    data class RemoteMember(val username:String,val role:String,val joinedAt:Long)
    data class RemoteInvite(val groupId:String,val token:String,val name:String,val description:String,val memberCount:Int,val approveNewMembers:Boolean,val alreadyMember:Boolean,val pending:Boolean)
    data class RemoteJoinRequest(val username:String,val requestedAt:Long)
    data class RemotePollOption(val id:String,val text:String,val voteCount:Int,val selectedByMe:Boolean=false)
    data class RemotePoll(val id:String,val question:String,val multipleChoice:Boolean,val closed:Boolean,val options:List<RemotePollOption>)
    data class RemotePost(val id:String,val text:String,val authorUsername:String,val marketplaceProductId:String?,val pinned:Boolean,val createdAt:Long,val poll:RemotePoll?)
    data class RemoteEvent(val id:String,val title:String,val description:String,val startsAt:Long,val endsAt:Long?,val location:String,val creatorUsername:String,val goingCount:Int,val maybeCount:Int,val notGoingCount:Int,val myResponse:String?,val pendingResponse:Boolean=false)
    data class RemoteContent(val posts:List<RemotePost>,val events:List<RemoteEvent>)

    suspend fun loadMyGroups(context:Context):Result<List<FynxGroup>> = runCatching {
        val root=JSONObject(FynxBackendClient.get(context,"/api/groups").getOrThrow())
        val array=root.optJSONArray("groups")?:JSONArray()
        buildList {
            for(index in 0 until array.length()) {
                val item=array.optJSONObject(index)?:continue
                val membersJson=item.optJSONArray("members")?:JSONArray()
                val members=buildList {
                    for(memberIndex in 0 until membersJson.length()) {
                        val member=membersJson.optJSONObject(memberIndex)?:continue
                        val role=runCatching { FynxGroupRole.valueOf(member.optString("role","MEMBER").uppercase()) }.getOrDefault(FynxGroupRole.MEMBER)
                        add(FynxGroupMember(member.optString("username"),role))
                    }
                }
                val visibility=runCatching { FynxGroupVisibility.valueOf(item.optString("visibility","PRIVATE").uppercase()) }.getOrDefault(FynxGroupVisibility.PRIVATE)
                val photo=item.optString("groupPhotoMediaId").takeIf { it.isNotBlank() && it!="null" }
                val description=item.optString("description").takeIf { it.trim().length >= 2 } ?: "A FYNX community"
                val group=FynxGroup(item.optString("id"),item.optString("name"),description,visibility,item.optString("ownerUsername"),members,photo)
                if(FynxGroupsBatch1.validate(group).isEmpty()) add(group)
            }
        }.distinctBy { it.id }
    }

    suspend fun syncGroup(context:Context,group:FynxGroup):Result<Unit> = runCatching { val body=JSONObject().apply{put("ownerUsername",group.ownerUsername.trim().removePrefix("@"));put("name",group.name);put("groupPhotoMediaId",group.groupPhotoMediaId ?: JSONObject.NULL);put("description",group.description);put("visibility",group.visibility.name);put("members",JSONArray().apply{group.members.forEach{member->put(JSONObject().apply{put("username",member.username.trim().removePrefix("@"));put("role",member.role.name)})}})};FynxBackendClient.postJson(context,"/api/groups/${group.id}/sync",body.toString()).getOrThrow();Unit }
    suspend fun updateGroupPhoto(context:Context,groupId:String,mediaId:String?):Result<String?> = runCatching { val body=JSONObject().apply{put("mediaId",mediaId?.trim()?.takeIf{it.isNotBlank()}?:JSONObject.NULL)}; val o=JSONObject(FynxBackendClient.patchJson(context,"/api/groups/$groupId/photo",body.toString()).getOrThrow()); o.optString("groupPhotoMediaId").takeIf{it.isNotBlank()&&it!="null"} }
    suspend fun loadMembers(context:Context,groupId:String):Result<List<RemoteMember>> = runCatching { val a=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/members").getOrThrow()).optJSONArray("members")?:JSONArray();buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(RemoteMember(o.optString("username"),o.optString("role","MEMBER"),o.optLong("joinedAt")))}} }
    suspend fun addMember(context:Context,groupId:String,username:String):Result<RemoteMember> = runCatching {
        val body=JSONObject().put("username",username.trim().removePrefix("@"))
        val root=JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/members",body.toString()).getOrThrow())
        val member=root.getJSONObject("member")
        RemoteMember(member.optString("username"),member.optString("role","MEMBER"),member.optLong("joinedAt"))
    }
    suspend fun promoteMember(context:Context,groupId:String,username:String):Result<Unit> = runCatching {
        FynxBackendClient.postJson(context, "/api/groups/" + groupId + "/members/" + Uri.encode(username.trim().removePrefix("@")) + "/promote", "{}").getOrThrow()
        Unit
    }
    suspend fun demoteMember(context:Context,groupId:String,username:String):Result<Unit> = runCatching {
        FynxBackendClient.postJson(context, "/api/groups/" + groupId + "/members/" + Uri.encode(username.trim().removePrefix("@")) + "/demote", "{}").getOrThrow()
        Unit
    }
    suspend fun removeMember(context:Context,groupId:String,username:String):Result<Unit> = runCatching {
        FynxBackendClient.delete(context, "/api/groups/" + groupId + "/members/" + Uri.encode(username.trim().removePrefix("@"))).getOrThrow()
        Unit
    }
    suspend fun transferOwnership(context:Context,groupId:String,username:String):Result<String> = runCatching {
        val body=JSONObject().put("username",username.trim().removePrefix("@"))
        val root=JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/ownership",body.toString()).getOrThrow())
        root.optString("ownerUsername").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Ownership transfer was not confirmed by the server.")
    }
    suspend fun leaveGroup(context:Context,groupId:String):Result<Unit> = runCatching {
        FynxBackendClient.postJson(context,"/api/groups/$groupId/leave","{}").getOrThrow()
        Unit
    }
    suspend fun loadPermissions(context:Context,groupId:String):Result<RemotePermissions> = runCatching { val o=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/permissions").getOrThrow()).optJSONObject("permissions")?:JSONObject();RemotePermissions(o.optBoolean("send_messages",true),o.optBoolean("send_media",true),o.optBoolean("add_members",true),o.optBoolean("invite_links",true),o.optBoolean("approve_new_members",false),o.optInt("disappearing_seconds",0),o.optBoolean("show_history_to_new_members",true)) }
    suspend fun updatePermissions(context:Context,groupId:String,permissions:RemotePermissions):Result<RemotePermissions> = runCatching { val body=JSONObject().apply{put("sendMessages",permissions.sendMessages);put("sendMedia",permissions.sendMedia);put("addMembers",permissions.addMembers);put("inviteLinks",permissions.inviteLinks);put("approveNewMembers",permissions.approveNewMembers);put("disappearingSeconds",permissions.disappearingSeconds);put("showHistoryToNewMembers",permissions.showHistoryToNewMembers)};val o=JSONObject(FynxBackendClient.patchJson(context,"/api/groups/$groupId/permissions",body.toString()).getOrThrow()).optJSONObject("permissions")?:JSONObject();RemotePermissions(o.optBoolean("send_messages",permissions.sendMessages),o.optBoolean("send_media",permissions.sendMedia),o.optBoolean("add_members",permissions.addMembers),o.optBoolean("invite_links",permissions.inviteLinks),o.optBoolean("approve_new_members",permissions.approveNewMembers),o.optInt("disappearing_seconds",permissions.disappearingSeconds),o.optBoolean("show_history_to_new_members",permissions.showHistoryToNewMembers)) }
    suspend fun createInvite(context:Context,groupId:String):Result<Pair<String,String>> = runCatching { val o=JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/invites",JSONObject().toString()).getOrThrow());o.getString("token") to o.getString("inviteUri") }
    suspend fun revokeInvite(context:Context,groupId:String,token:String):Result<Unit> = runCatching { FynxBackendClient.postJson(context,"/api/groups/$groupId/invites/${Uri.encode(token)}/revoke",JSONObject().toString()).getOrThrow();Unit }
    suspend fun previewInvite(context:Context,groupId:String,token:String):Result<RemoteInvite> = runCatching { val o=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/invites/${Uri.encode(token)}").getOrThrow());val i=o.getJSONObject("invite");RemoteInvite(i.getString("groupId"),i.getString("token"),i.optString("name","Group"),i.optString("description"),i.optInt("memberCount"),i.optBoolean("approveNewMembers"),o.optBoolean("alreadyMember"),o.optBoolean("pending")) }
    suspend fun joinInvite(context:Context,groupId:String,token:String):Result<JSONObject> = runCatching { JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/invites/${Uri.encode(token)}/join",JSONObject().toString()).getOrThrow()) }
    suspend fun requestInviteJoin(context:Context,groupId:String,token:String):Result<JSONObject> = runCatching { JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/invites/${Uri.encode(token)}/request",JSONObject().toString()).getOrThrow()) }
    suspend fun loadJoinRequests(context:Context,groupId:String):Result<List<RemoteJoinRequest>> = runCatching { val a=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/join-requests").getOrThrow()).optJSONArray("requests")?:JSONArray();buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(RemoteJoinRequest(o.optString("username"),o.optLong("requestedAt")))}} }
    suspend fun approveJoinRequest(context:Context,groupId:String,username:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/join-requests/${Uri.encode(username)}/approve",JSONObject().toString()).getOrThrow();Unit}
    suspend fun rejectJoinRequest(context:Context,groupId:String,username:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/join-requests/${Uri.encode(username)}/reject",JSONObject().toString()).getOrThrow();Unit}
    suspend fun loadMessagePage(context:Context,groupId:String,before:RemoteMessage?=null,limit:Int=50,query:String?=null):Result<RemoteMessagePage> = runCatching {
        val safeLimit=limit.coerceIn(1,100)
        val cleanQuery=query?.trim()?.replace(Regex("\\s+")," ")?.takeIf { it.isNotBlank() }
        require(cleanQuery==null || cleanQuery.length in 2..100) { "Message search must contain between 2 and 100 characters." }
        val cursor=before?.let { "&beforeTimestamp=${it.timestamp}&beforeId=${Uri.encode(it.id)}" }.orEmpty()
        val search=cleanQuery?.let { "&q=${Uri.encode(it)}" }.orEmpty()
        val root=JSONObject(FynxBackendClient.get(context,"/api/groups/$groupId/messages?limit=$safeLimit$cursor$search").getOrThrow())
        val a=root.optJSONArray("messages")?:JSONArray()
        val parsed=buildList {
            for(i in 0 until a.length()) {
                runCatching {
                    val o=a.getJSONObject(i)
                    val rawId=o.optString("id").trim()
                    val safeId=rawId.ifBlank { "group-remote-$i-" + o.optLong("timestamp") }
                    RemoteMessage(safeId,o.optString("text"),o.optString("senderUsername"),o.optLong("timestamp"),o.optString("attachmentMediaId").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentType").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentUrl").takeIf{it.isNotBlank()&&it!="null"},o.optString("replyToId").takeIf{it.isNotBlank()&&it!="null"},o.optString("reaction").takeIf{it.isNotBlank()&&it!="null"},o.optBoolean("edited"),o.optBoolean("deleted"),o.optBoolean("pinned"),o.optJSONArray("readBy")?.let { values -> buildList { for(index in 0 until values.length()) values.optString(index).takeIf { it.isNotBlank() }?.let(::add) } } ?: emptyList())
                }.getOrNull()?.let(::add)
            }
        }.distinctBy { it.id }
        RemoteMessagePage(parsed,root.optBoolean("hasMore",false))
    }
    suspend fun markMessagesRead(context:Context,groupId:String,messageIds:List<String>):Result<List<RemoteMessage>> = runCatching {
        val safeIds=messageIds.distinct().filter { it.matches(Regex("[A-Za-z0-9_-]{8,80}")) }.take(100)
        if(safeIds.isEmpty())return@runCatching emptyList()
        val body=JSONObject().put("messageIds",JSONArray(safeIds))
        val root=JSONObject(FynxBackendClient.postJson(context,"/api/groups/$groupId/read",body.toString()).getOrThrow())
        val values=root.optJSONArray("messages")?:JSONArray()
        buildList { for(index in 0 until values.length()) runCatching { toRemoteMessage(values.getJSONObject(index)) }.getOrNull()?.let(::add) }
    }
    suspend fun loadMessages(context:Context,groupId:String):Result<List<RemoteMessage>> =
        loadMessagePage(context,groupId).map { it.messages }
    suspend fun sendMessage(
        context: Context,
        groupId: String,
        message: ChatMessage,
        expectedAccountKey: String? = FynxAuthStore.accountStorageKey(context),
        requestToken: String? = FynxBackendClient.accessToken(context)
    ): Result<RemoteMessage> = sendMutex.withLock {
        runCatching {
            check(FynxBackendClient.isRequestSessionCurrent(expectedAccountKey, FynxAuthStore.accountStorageKey(context), requestToken, FynxBackendClient.accessToken(context))) {
                "Your account changed before this group message could be sent."
            }
            val chatKey = "group_$groupId"
            val persisted = FynxChatStore.load(context, chatKey).firstOrNull { it.id == message.id }
            var mediaId:String?=message.mediaId?:persisted?.mediaId?:message.attachmentUri?.substringAfterLast('/')?.takeIf{it?.all(Char::isDigit)==true}
            var mediaType = message.attachmentType ?: persisted?.attachmentType
            if (mediaId == null && message.attachmentUri != null) {
                val uri = Uri.parse(message.attachmentUri!!)
                val mime=groupAttachmentMimeType(context,uri,mediaType)
                val uploaded = FynxProductionMessaging.uploadMedia(
                    context, uri, mime, expectedAccountKey, requestToken
                ).getOrThrow()
                mediaId = uploaded.id
                mediaType = mediaType ?: mime.substringBefore('/')
                val saved = FynxChatStore.load(context, chatKey)
                if (saved.any { it.id == message.id }) FynxChatStore.save(context, chatKey, saved.map{if(it.id==message.id)it.copy(mediaId=mediaId,attachmentType=mediaType)else it})
            }
            check(FynxBackendClient.isRequestSessionCurrent(expectedAccountKey, FynxAuthStore.accountStorageKey(context), requestToken, FynxBackendClient.accessToken(context))) {
                "Your account changed while the attachment was uploading. The message was not sent."
            }
            val body = JSONObject().apply {
                put("id", message.id)
                put("text", message.text)
                if (mediaId != null) put("attachmentMediaId", mediaId)
                if (mediaType != null) put("attachmentType", mediaType)
                if (message.replyToId != null) put("replyToId", message.replyToId)
            }
            val result = runCatching {
                val raw = FynxBackendClient.postJsonForSession(
                    context, "/api/groups/$groupId/messages", body.toString(), requestToken
                ).getOrThrow()
                toRemoteMessage(JSONObject(raw).getJSONObject("message"))
            }
            result.getOrElse { error ->
                if (FynxBackendClient.isRequestSessionCurrent(expectedAccountKey, FynxAuthStore.accountStorageKey(context), requestToken, FynxBackendClient.accessToken(context))) {
                    loadMessages(context, groupId).getOrNull()?.firstOrNull { it.id == message.id }?.let { return@getOrElse it }
                }
                throw error
            }
        }
    }
    suspend fun editMessage(context:Context,groupId:String,messageId:String,text:String):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","edit");put("text",text)})
    suspend fun reactToMessage(context:Context,groupId:String,messageId:String,reaction:String?):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","reaction");if(reaction!=null)put("reaction",reaction)})
    suspend fun setPinned(context:Context,groupId:String,messageId:String,pinned:Boolean):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","pin");put("pinned",pinned)})
    suspend fun deleteMessage(context:Context,groupId:String,messageId:String):Result<RemoteMessage> = messageAction(context,groupId,messageId,JSONObject().apply{put("action","delete")})
    private suspend fun messageAction(context:Context,groupId:String,messageId:String,body:JSONObject):Result<RemoteMessage> = runCatching{toRemoteMessage(JSONObject(FynxBackendClient.patchJson(context,"/api/groups/$groupId/messages/${Uri.encode(messageId)}",body.toString()).getOrThrow()).getJSONObject("message"))}

    private fun contentPreferences(context:Context,groupId:String):android.content.SharedPreferences? {
        val account=FynxAuthStore.accountStorageKey(context)?.takeIf{it.isNotBlank()}?:return null
        val encoded=Base64.encodeToString(account.toByteArray(Charsets.UTF_8),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val safeGroup=groupId.replace(Regex("[^A-Za-z0-9_.-]"),"_").take(100).ifBlank{"unknown"}
        return context.getSharedPreferences("fynx_group_content_${encoded}_${safeGroup}",Context.MODE_PRIVATE)
    }

    private fun loadPendingRsvps(context:Context,groupId:String):Map<String,String> {
        val raw=contentPreferences(context,groupId)?.getString("pending_rsvps",null)?:return emptyMap()
        return runCatching {
            val json=JSONObject(raw)
            buildMap { val keys=json.keys();while(keys.hasNext()){val key=keys.next();val value=json.optString(key).uppercase();if(key.isNotBlank()&&value in setOf("GOING","MAYBE","NOT_GOING"))put(key,value)} }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    private fun savePendingRsvp(context:Context,groupId:String,eventId:String,response:String) {
        val prefs=contentPreferences(context,groupId)?:return
        val values=loadPendingRsvps(context,groupId).toMutableMap()
        values[eventId]=response
        prefs.edit().putString("pending_rsvps",JSONObject(values).toString()).apply()
    }

    @Synchronized
    private fun clearPendingRsvp(context:Context,groupId:String,eventId:String,expectedResponse:String?=null):Boolean {
        val prefs=contentPreferences(context,groupId)?:return false
        val values=loadPendingRsvps(context,groupId).toMutableMap()
        if(expectedResponse!=null && values[eventId]!=expectedResponse) return false
        values.remove(eventId)
        val edit=prefs.edit()
        if(values.isEmpty())edit.remove("pending_rsvps") else edit.putString("pending_rsvps",JSONObject(values).toString())
        edit.apply()
        return true
    }

    private fun countRsvp(current:Int,old:String?,next:String?,bucket:String):Int =
        (current-(if(old==bucket)1 else 0)+(if(next==bucket)1 else 0)).coerceAtLeast(0)

    fun applyEventResponse(content:RemoteContent,eventId:String,response:String,pending:Boolean):RemoteContent =
        content.copy(events=content.events.map { event ->
            if(event.id!=eventId)return@map event
            val next=response.uppercase()
            event.copy(
                goingCount=countRsvp(event.goingCount,event.myResponse,next,"GOING"),
                maybeCount=countRsvp(event.maybeCount,event.myResponse,next,"MAYBE"),
                notGoingCount=countRsvp(event.notGoingCount,event.myResponse,next,"NOT_GOING"),
                myResponse=next,
                pendingResponse=pending
            )
        })

    private fun applyPendingRsvps(context:Context,groupId:String,content:RemoteContent):RemoteContent =
        loadPendingRsvps(context,groupId).entries.fold(content) { current,entry ->
            applyEventResponse(current,entry.key,entry.value,true)
        }

    suspend fun syncPendingRsvps(context:Context,groupId:String):Int {
        val account=FynxAuthStore.accountStorageKey(context)?.takeIf{it.isNotBlank()}?:return 0
        var completed=0
        loadPendingRsvps(context,groupId).forEach { entry ->
            if(FynxAuthStore.accountStorageKey(context)!=account)return completed
            rsvpMutex.withLock {
                if(FynxAuthStore.accountStorageKey(context)!=account)return@withLock
                if(loadPendingRsvps(context,groupId)[entry.key]!=entry.value)return@withLock
                val result=FynxBackendClient.postJson(context,
                    "/api/groups/" + groupId + "/events/" + Uri.encode(entry.key) + "/rsvp",
                    JSONObject().put("response",entry.value).toString())
                if(FynxAuthStore.accountStorageKey(context)!=account)return@withLock
                if(result.isSuccess && clearPendingRsvp(context,groupId,entry.key,entry.value))completed++
            }
        }
        return completed
    }

    suspend fun drainPendingWork(context:Context):Int {
        val account=FynxAuthStore.accountStorageKey(context)?.takeIf{it.isNotBlank()}?:return 0
        var completed=0
        val pendingGroupIds = FynxGroupsStore.pendingSyncGroups(context)
        for(group in FynxGroupsStore.load(context)) {
            if(FynxAuthStore.accountStorageKey(context)!=account)return completed
            if (group.id in pendingGroupIds) {
                val groupSync = syncGroup(context, group)
                if(FynxAuthStore.accountStorageKey(context)!=account)return completed
                if (groupSync.isSuccess) {
                    FynxGroupsStore.markSynced(context, group.id)
                    completed++
                } else {
                    continue
                }
            }
            val pending=FynxChatStore.load(context,"group_${group.id}")
                .filter { it.fromMe && !it.delivered }
                .sortedBy { it.timestamp }
            for(message in pending) {
                if(FynxAuthStore.accountStorageKey(context)!=account)return completed
                val result=sendMessage(context,group.id,message)
                if(FynxAuthStore.accountStorageKey(context)!=account)return completed
                if(result.isSuccess) {
                    val remote=result.getOrThrow()
                    FynxChatStore.replaceMessage(context,"group_${group.id}",message.id,
                        toChatMessage(remote,FynxAuthStore.storedUsername(context).orEmpty(),FynxBackendClient.baseUrl(context)))
                    deleteStagedAttachment(context,message.attachmentUri)
                    completed++
                } else break
            }
            if(FynxAuthStore.accountStorageKey(context)!=account)return completed
            completed+=syncPendingRsvps(context,group.id)
        }
        return completed
    }

    private fun deleteStagedAttachment(context:Context,rawUri:String?) {
        if(rawUri.isNullOrBlank())return
        runCatching {
            val uri=Uri.parse(rawUri)
            if(uri.scheme!="file")return@runCatching
            val file=File(uri.path?:return@runCatching).canonicalFile
            val account=FynxAuthStore.accountStorageKey(context)?.takeIf{it.isNotBlank()}?:return@runCatching
            val suffix=Base64.encodeToString(account.toByteArray(Charsets.UTF_8),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val expected=File(context.filesDir,"fynx_group_pending_media_$suffix").canonicalFile
            if(file.parentFile==expected)file.delete()
        }
    }

    private fun parseContent(raw:String):RemoteContent {
        val root=JSONObject(raw)
        val posts=root.optJSONArray("posts")?:JSONArray()
        val events=root.optJSONArray("events")?:JSONArray()
        val mappedPosts=buildList {
            for(i in 0 until posts.length()) {
                val post=posts.getJSONObject(i)
                val poll=post.optJSONObject("poll")
                val options=buildList {
                    if(poll!=null) {
                        val array=poll.optJSONArray("options")?:JSONArray()
                        for(j in 0 until array.length()) {
                            val option=array.getJSONObject(j)
                            add(RemotePollOption(option.getString("id"),option.optString("text"),option.optInt("voteCount"),option.optBoolean("selectedByMe")))
                        }
                    }
                }
                add(RemotePost(
                    post.getString("id"),post.optString("text"),post.optString("authorUsername"),
                    post.optString("marketplaceProductId").takeIf{it.isNotBlank()&&it!="null"},
                    post.optBoolean("pinned"),post.optLong("createdAt"),
                    poll?.let{RemotePoll(it.getString("id"),it.optString("question"),it.optBoolean("multipleChoice"),it.optBoolean("closed"),options)}
                ))
            }
        }
        val mappedEvents=buildList {
            for(i in 0 until events.length()) {
                val event=events.getJSONObject(i)
                add(RemoteEvent(
                    event.getString("id"),event.optString("title"),event.optString("description"),
                    event.optLong("startsAt"),event.optLong("endsAt").takeIf{it>0},
                    event.optString("location"),event.optString("creatorUsername"),
                    event.optInt("goingCount"),event.optInt("maybeCount"),event.optInt("notGoingCount"),
                    event.optString("myResponse").takeIf{it.isNotBlank()}
                ))
            }
        }
        return RemoteContent(mappedPosts,mappedEvents)
    }

    fun loadCachedContent(context:Context,groupId:String):RemoteContent? {
        val raw=contentPreferences(context,groupId)?.getString("content_json",null)?:return null
        return runCatching { applyPendingRsvps(context,groupId,parseContent(raw)) }.getOrNull()
    }

    suspend fun loadContent(context:Context,groupId:String):Result<RemoteContent> = runCatching {
        val account=FynxAuthStore.accountStorageKey(context)?.takeIf{it.isNotBlank()}
            ?: throw IllegalStateException("Sign in to load group content.")
        syncPendingRsvps(context,groupId)
        val raw=FynxBackendClient.get(context,"/api/groups/$groupId/content").getOrThrow()
        if(FynxAuthStore.accountStorageKey(context)!=account)throw IllegalStateException("FYNX account changed while group content was loading.")
        contentPreferences(context,groupId)?.edit()?.putString("content_json",raw)?.apply()
        applyPendingRsvps(context,groupId,parseContent(raw))
    }

    suspend fun rsvpEvent(context:Context,groupId:String,eventId:String,response:String):Result<Boolean> = runCatching {
        rsvpMutex.withLock {
            val account=FynxAuthStore.accountStorageKey(context)?.takeIf{it.isNotBlank()}
                ?: throw IllegalStateException("Sign in before responding to a group event.")
            val normalized=response.uppercase()
            require(normalized in setOf("GOING","MAYBE","NOT_GOING")) { "Choose Going, Maybe, or Not going." }
            val result=FynxBackendClient.postJson(context,
                "/api/groups/" + groupId + "/events/" + Uri.encode(eventId) + "/rsvp",
                JSONObject().put("response",normalized).toString())
            if(FynxAuthStore.accountStorageKey(context)!=account)throw IllegalStateException("FYNX account changed while your event response was saving.")
            if(result.isSuccess) {
                clearPendingRsvp(context,groupId,eventId)
                false
            } else {
                val failure=result.exceptionOrNull()?:IllegalStateException("Event response could not be saved.")
                val status=Regex("(?:HTTP|status)[^0-9]*([0-9]{3})",RegexOption.IGNORE_CASE).find(failure.message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
                if(status!=null&&status in 400..499)throw failure
                savePendingRsvp(context,groupId,eventId,normalized)
                true
            }
        }
    }

    suspend fun createPoll(context:Context,groupId:String,question:String,options:List<String>,multipleChoice:Boolean):Result<Unit> = runCatching { val body=JSONObject().apply{put("question",question);put("multipleChoice",multipleChoice);put("options",JSONArray(options))};FynxBackendClient.postJson(context,"/api/groups/$groupId/polls",body.toString()).getOrThrow();Unit }
    suspend fun votePoll(context:Context,groupId:String,pollId:String,optionId:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/polls/${Uri.encode(pollId)}/votes",JSONObject().apply{put("optionId",optionId)}.toString()).getOrThrow();Unit}
    suspend fun setGroupPostPinned(context:Context,groupId:String,postId:String,pinned:Boolean):Result<Unit> = runCatching {FynxBackendClient.patchJson(context,"/api/groups/$groupId/posts/${Uri.encode(postId)}/pin",JSONObject().apply{put("pinned",pinned)}.toString()).getOrThrow();Unit}
    suspend fun createEvent(context:Context,groupId:String,title:String,description:String,startsAt:String,location:String):Result<Unit> = runCatching {FynxBackendClient.postJson(context,"/api/groups/$groupId/events",JSONObject().apply{put("title",title);put("description",description);put("startsAt",startsAt);put("location",location)}.toString()).getOrThrow();Unit}

    fun fromRealtimeMessage(messageJson:String):RemoteMessage? = runCatching { toRemoteMessage(JSONObject(messageJson)) }.getOrNull()

    private fun toRemoteMessage(o:JSONObject):RemoteMessage {
        val rawId=o.optString("id").trim()
        val safeId=rawId.ifBlank { "group-response-" + o.optLong("timestamp") + "-" + o.optString("senderUsername").hashCode() }
        return RemoteMessage(safeId,o.optString("text"),o.optString("senderUsername"),o.optLong("timestamp"),o.optString("attachmentMediaId").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentType").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentUrl").takeIf{it.isNotBlank()&&it!="null"},o.optString("replyToId").takeIf{it.isNotBlank()&&it!="null"},o.optString("reaction").takeIf{it.isNotBlank()&&it!="null"},o.optBoolean("edited"),o.optBoolean("deleted"),o.optBoolean("pinned"),o.optJSONArray("readBy")?.let { values -> buildList { for(index in 0 until values.length()) values.optString(index).takeIf { it.isNotBlank() }?.let(::add) } } ?: emptyList())
    }
    fun toChatMessage(m:RemoteMessage,currentUsername:String,baseUrl:String)=ChatMessage(if(m.deleted)"Message deleted" else m.text,m.senderUsername.equals(currentUsername.removePrefix("@"),true),m.id,m.timestamp,delivered=true,read=false,replyToId=m.replyToId,reaction=m.reaction,edited=m.edited,attachmentUri=m.attachmentUrl?.let{if(it.startsWith("http"))it else baseUrl.trimEnd('/')+it},attachmentType=m.attachmentType,senderUsername=m.senderUsername,pinned=m.pinned,deleted=m.deleted,readBy=m.readBy)
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
