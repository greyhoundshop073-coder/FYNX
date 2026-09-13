package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxInvitePanel(code:String?,groupId:String?=null,onShare:()->Unit={},onOpenGroup:(String)->Unit={},onBack:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val resolved=remember(code,groupId){
        if(!groupId.isNullOrBlank()) groupId to code
        else {
            val parts=code?.split(":",limit=2)
            if(parts?.size==2&&parts[0].isNotBlank()&&parts[1].isNotBlank()) parts[0] to parts[1] else null to code
        }
    }
    val resolvedGroupId=resolved.first
    val resolvedToken=resolved.second
    var invite by remember(resolvedGroupId,resolvedToken){mutableStateOf<FynxGroupRemoteClient.RemoteInvite?>(null)}
    var loading by remember(resolvedGroupId,resolvedToken){mutableStateOf(true)}
    var joining by remember(resolvedGroupId,resolvedToken){mutableStateOf(false)}
    var message by remember(resolvedGroupId,resolvedToken){mutableStateOf<String?>(null)}

    LaunchedEffect(resolvedGroupId,resolvedToken){
        if(resolvedGroupId.isNullOrBlank()||resolvedToken.isNullOrBlank()){loading=false;return@LaunchedEffect}
        FynxGroupRemoteClient.previewInvite(context,resolvedGroupId,resolvedToken).onSuccess{invite=it;message=null}.onFailure{message=it.message?:"This invite could not be validated."}
        loading=false
    }
    fun join(){
        val id=resolvedGroupId?:return; val token=resolvedToken?:return
        joining=true;message=null
        scope.launch{
            FynxGroupRemoteClient.joinInvite(context,id,token).onSuccess{result->when{
                result.optBoolean("joined")-> {message="You joined ${invite?.name?:"the group"}.";onOpenGroup(id)}
                result.optBoolean("pending")->message="Your join request was sent for admin approval."
                else->message="The group join request could not be completed."
            }}.onFailure{message=it.message?:"Unable to join this group."}
            joining=false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}
            Column(Modifier.weight(1f)){Text("Group invite",style=MaterialTheme.typography.headlineSmall);Text("Review before joining",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.Default.GroupAdd,null,tint=MaterialTheme.colorScheme.primary)
        }
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                if(loading){CircularProgressIndicator(modifier=Modifier.size(28.dp));Text("Checking invite…",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                else if(invite!=null){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(Icons.Default.Group,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)){Text(invite!!.name,style=MaterialTheme.typography.titleLarge);Text("${invite!!.memberCount} members",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                    if(invite!!.description.isNotBlank())Text(invite!!.description,style=MaterialTheme.typography.bodyMedium)
                    when{invite!!.alreadyMember->Text("You are already a member of this group.",color=MaterialTheme.colorScheme.primary);invite!!.pending->Text("Your join request is waiting for an admin.",color=MaterialTheme.colorScheme.onSurfaceVariant);invite!!.approveNewMembers->Text("An admin must approve new members before you can enter.",color=MaterialTheme.colorScheme.onSurfaceVariant);else->Text("You can join this group now.",color=MaterialTheme.colorScheme.primary)}
                }else{Text("Invite unavailable",style=MaterialTheme.typography.titleLarge);Text(message?:"This invite is invalid, expired, or revoked.",color=MaterialTheme.colorScheme.error)}
            }
        }
        message?.let{if(invite!=null)Text(it,color=if(it.contains("joined",true))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
        if(invite!=null){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                val link=FynxDeepLinkParser.inviteWebLink(invite!!.token,invite!!.groupId)
                OutlinedButton(onClick={FynxShareActions.share(context,FynxSharePayload("FYNX group invite","Join ${invite!!.name} on FYNX.",link))},modifier=Modifier.weight(1f)){Icon(Icons.Default.Share,null);Spacer(Modifier.width(6.dp));Text("Share")}
                if(!invite!!.alreadyMember&&!invite!!.pending)Button(onClick=::join,enabled=!joining,modifier=Modifier.weight(1f)){Text(if(joining)"Joining…" else "Join group")}
                if(invite!!.alreadyMember)Button(onClick={onOpenGroup(invite!!.groupId)},modifier=Modifier.weight(1f)){Text("Open group")}
            }
        }
        Spacer(Modifier.weight(1f))
        Text("FYNX checks the invite with the server before membership changes are made.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
