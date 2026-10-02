package com.fynx.app.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class FriendsRecommendation(val username:String,val displayName:String,val mutualFriends:Int,val reason:String,val photoId:String?=null)

@Composable
fun FriendsPanel(onOpenProfile:(String)->Unit={},onOpenChat:(String)->Unit={}) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var query by rememberSaveable{mutableStateOf("")}
    var section by rememberSaveable{mutableStateOf("Friends")}
    var searchMethod by rememberSaveable{mutableStateOf(FynxPeopleSearchMethod.USERNAME)}
    var filterOpen by remember{mutableStateOf(false)}
    var friends by remember{mutableStateOf(emptyList<FynxSocialClient.User>())}
    var incoming by remember{mutableStateOf(emptyList<FynxSocialClient.FriendRequest>())}
    var outgoing by remember{mutableStateOf(emptyList<FynxSocialClient.FriendRequest>())}
    var blocked by remember{mutableStateOf(emptyList<FynxSocialClient.User>())}
    var searchResults by remember{mutableStateOf(emptyList<FynxSocialClient.User>())}
    var recommendations by remember{mutableStateOf(emptyList<FriendsRecommendation>())}
    var loading by remember{mutableStateOf(true)}
    var refreshInFlight by remember{mutableStateOf(false)}
    var busyUsername by remember{mutableStateOf<String?>(null)}
    var message by remember{mutableStateOf<String?>(null)}
    var openMenuUsername by remember{mutableStateOf<String?>(null)}
    val listState=rememberLazyListState()

    suspend fun refresh(){
        if(refreshInFlight)return
        refreshInFlight=true;loading=true;message=null
        try{
            val fr=FynxSocialClient.friends(context);val rr=FynxSocialClient.requests(context);val br=FynxSocialClient.blocked(context)
            friends=fr.getOrElse{emptyList()};val requests=rr.getOrElse{emptyList()}
            incoming=requests.filter{it.status.equals("incoming",true)};outgoing=requests.filter{it.status.equals("outgoing",true)};blocked=br.getOrElse{emptyList()}
            val error=fr.exceptionOrNull()?:rr.exceptionOrNull()?:br.exceptionOrNull();if(error!=null)message=error.message?:"Could not load your connections."
        }finally{loading=false;refreshInFlight=false}
    }

    suspend fun loadRecommendations(){
        val body=JSONObject().apply{put("name","get_people_recommendations");put("arguments",JSONObject().apply{put("limit",8);put("offset",0)})}.toString()
        FynxBackendClient.postJson(context,"/api/assistant/tools",body).onSuccess{raw->
            val people=JSONObject(raw).optJSONObject("result")?.optJSONArray("people")?:return@onSuccess
            recommendations=buildList{
                for(i in 0 until people.length()){
                    val p=people.optJSONObject(i)?:continue;val username=p.optString("username").trim();if(username.isBlank())continue
                    add(FriendsRecommendation(username,p.optString("displayName").ifBlank{username},p.optInt("mutualFriends"),p.optString("reason"),FynxProfileRemoteClient.cachedProfilePhotoId(context,username)))
                }
            }.take(4)
        }
    }

    LaunchedEffect(Unit){refresh();loadRecommendations()}
    LaunchedEffect(query,searchMethod){
        val trimmed=query.trim();val normalizedPhone=FynxPeopleDiscovery.normalizePhone(trimmed)
        val ready=if(searchMethod==FynxPeopleSearchMethod.PHONE)normalizedPhone.length>=7 else trimmed.removePrefix("@").length>=2
        if(!ready){searchResults=emptyList();return@LaunchedEffect}
        FynxSocialClient.searchUsers(context,if(searchMethod==FynxPeopleSearchMethod.PHONE)normalizedPhone else trimmed.removePrefix("@"),phoneSearch=searchMethod==FynxPeopleSearchMethod.PHONE)
            .onSuccess{searchResults=it;section="Discover"}.onFailure{searchResults=emptyList();message=it.message?:"Search failed."}
    }

    val normalizedQuery=if(searchMethod==FynxPeopleSearchMethod.USERNAME)query.trim().removePrefix("@") else FynxPeopleDiscovery.normalizePhone(query)
    val friendNames=friends.map{it.username.lowercase()}.toSet();val blockedNames=blocked.map{it.username.lowercase()}.toSet();val incomingNames=incoming.map{it.username.lowercase()}.toSet();val outgoingNames=outgoing.map{it.username.lowercase()}.toSet()
    val discover=searchResults.filter{val n=it.username.lowercase();n !in friendNames&&n !in blockedNames&&n !in incomingNames&&n !in outgoingNames}
    val visibleRecommendations=recommendations.filter{val n=it.username.lowercase();n !in friendNames&&n !in blockedNames&&n !in incomingNames&&n !in outgoingNames}
    fun userFromRequest(r:FynxSocialClient.FriendRequest)=FynxSocialClient.User(r.username,r.displayName,"")
    fun runAction(username:String,action:suspend()->Result<Unit>){scope.launch{busyUsername=username;message=null;val result=action();if(result.isSuccess)refresh()else message=result.exceptionOrNull()?.message?:"That action could not be completed.";busyUsername=null}}

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={ (context as? Activity)?.onBackPressed() },modifier=Modifier.requiredSize(44.dp).semantics{contentDescription="Back"}){Icon(Icons.Default.ArrowBack,"Back")}
            Text("Friends",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.requiredSize(44.dp))
        }
        LazyColumn(state=listState,modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=20.dp)){
            item{
                Row(Modifier.fillMaxWidth().padding(top=2.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(Modifier.size(56.dp),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.People,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(31.dp))}}
                    Spacer(Modifier.width(14.dp));Column{Text("Find People",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Connect with people on FYNX",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
            item{
                Box{
                    OutlinedTextField(value=query,onValueChange={query=it.take(80)},modifier=Modifier.fillMaxWidth().padding(end=58.dp),singleLine=true,leadingIcon={Icon(Icons.Default.Search,"Search")},placeholder={Text(if(searchMethod==FynxPeopleSearchMethod.USERNAME)"Search username or name" else "Search phone number")},shape=RoundedCornerShape(28.dp))
                    Box(Modifier.align(Alignment.CenterEnd)){
                        IconButton(onClick={filterOpen=true},modifier=Modifier.size(52.dp).semantics{contentDescription="Search filters"}){Icon(Icons.Default.Tune,"Search filters")}
                        DropdownMenu(expanded=filterOpen,onDismissRequest={filterOpen=false}){
                            DropdownMenuItem(text={Text("Username or name")},onClick={searchMethod=FynxPeopleSearchMethod.USERNAME;query="";filterOpen=false})
                            DropdownMenuItem(text={Text("Phone number")},onClick={searchMethod=FynxPeopleSearchMethod.PHONE;query="";filterOpen=false})
                        }
                    }
                }
            }
            item{
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),RoundedCornerShape(28.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)){
                    listOf("Friends","Requests","Sent","Discover").forEach{tab->
                        val selected=section==tab
                        Surface(Modifier.weight(1f).height(48.dp).clickable{section=tab},shape=RoundedCornerShape(24.dp),color=if(selected)MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent){
                            Box(contentAlignment=Alignment.Center){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                Text(tab,fontWeight=if(selected)FontWeight.Bold else FontWeight.SemiBold,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                if(tab=="Requests"&&incoming.isNotEmpty())Surface(shape=CircleShape,color=if(selected)MaterialTheme.colorScheme.onPrimary.copy(alpha=.18f)else MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp)){Box(contentAlignment=Alignment.Center){Text(incoming.size.toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold)}}
                            }}
                        }
                    }
                }
            }
            if(section=="Friends"){
                if(visibleRecommendations.isNotEmpty())item{
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("People you may know",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={section="Discover"}){Text("See all")}}
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(horizontal=2.dp)){items(visibleRecommendations,key={"rec_${it.username}"}){person->RecommendationCard(person,busyUsername==person.username,onOpenProfile){runAction(person.username){FynxSocialClient.sendRequest(context,person.username)}}}}
                    }
                }
                item{Row(Modifier.fillMaxWidth().padding(top=2.dp),verticalAlignment=Alignment.CenterVertically){Text("Your Friends",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(friends.size.toString(),color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold)}}
                if(loading)item{Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
                else if(friends.isEmpty())emptyState("No friends yet","Accepted FYNX connections will appear here.")
                else items(friends,key={"friend_${it.username}"}){person->FriendRow(person,busyUsername==person.username,openMenuUsername==person.username,{openMenuUsername=if(it)person.username else null},onOpenProfile,onOpenChat){runAction(person.username){FynxSocialClient.removeFriend(context,person.username)}}}
            }else{
                item{if(message!=null){Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text(message!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);if(!loading)OutlinedButton(onClick={scope.launch{refresh()}},shape=FynxDesign.ControlShape){Text("Retry")}}}}
                when(section){
                    "Requests"->{if(incoming.isEmpty())emptyState("No incoming requests","Friend requests from other FYNX accounts will appear here.")else items(incoming,key={"incoming_${it.id}"}){r->RequestRow(userFromRequest(r),"Confirm","Delete",busyUsername==r.username,onOpenProfile,onOpenChat,{runAction(r.username){FynxSocialClient.acceptRequest(context,r.id)}},{runAction(r.username){FynxSocialClient.rejectRequest(context,r.id)}})}}
                    "Sent"->{if(outgoing.isEmpty())emptyState("No sent requests","Requests you send will appear here until they are accepted or rejected.")else items(outgoing,key={"outgoing_${it.id}"}){r->RequestRow(userFromRequest(r),"Cancel",null,busyUsername==r.username,onOpenProfile,onOpenChat,{runAction(r.username){FynxSocialClient.cancelRequest(context,r.id)}})}}
                    else->{if(searchMethod==FynxPeopleSearchMethod.PHONE&&normalizedQuery.length<7)emptyState("Phone discovery","Enter a valid phone number with country code.")else if(searchMethod==FynxPeopleSearchMethod.USERNAME&&normalizedQuery.length<2)emptyState("Search for a FYNX user","Type at least two characters of a username or display name.")else if(discover.isEmpty())emptyState("No matching people","No available FYNX account matched that search.")else items(discover,key={"discover_${it.username}"}){person->RequestRow(person,"Add",null,busyUsername==person.username,onOpenProfile,onOpenChat,{runAction(person.username){FynxSocialClient.sendRequest(context,person.username)}})}}
                }
            }
        }
    }
}

@Composable private fun RecommendationCard(person:FriendsRecommendation,busy:Boolean,onOpenProfile:(String)->Unit,onAdd:()->Unit){
    Card(Modifier.width(174.dp),shape=FynxDesign.CardShape,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.45f))){
        Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
            IconButton(onClick={onOpenProfile(person.username)},modifier=Modifier.size(66.dp)){FynxRemoteProfileAvatar(person.photoId,person.displayName,Modifier.size(58.dp),ownerUsername=person.username)}
            Text(person.displayName,fontWeight=FontWeight.Bold,maxLines=1);val mutual=if(person.mutualFriends>0)"${person.mutualFriends} mutual friend${if(person.mutualFriends==1)""else"s"}"else person.reason.replaceFirstChar{it.uppercase()};Text(mutual,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,maxLines=1)
            Button(onClick=onAdd,enabled=!busy,modifier=Modifier.fillMaxWidth().height(42.dp),shape=FynxDesign.ControlShape,contentPadding=PaddingValues(horizontal=8.dp)){if(busy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary)else{Icon(Icons.Default.PersonAdd,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Add")}}
        }
    }
}

@Composable private fun FriendRow(person:FynxSocialClient.User,busy:Boolean,menuOpen:Boolean,onMenuChange:(Boolean)->Unit,onOpenProfile:(String)->Unit,onOpenChat:(String)->Unit,onRemove:()->Unit){
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        IconButton(onClick={onOpenProfile(person.username)},modifier=Modifier.size(54.dp)){FynxRemoteProfileAvatar(person.profilePhotoMediaId,person.displayName.ifBlank{person.username},Modifier.size(48.dp),ownerUsername=person.username)}
        Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(person.displayName.ifBlank{person.username},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1);Text(if(person.username.startsWith("@"))person.username else "@${person.username}",color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
        if(busy)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)else{IconButton(onClick={onOpenChat(person.username)},modifier=Modifier.size(48.dp).semantics{contentDescription="Open chat"}){Icon(Icons.Default.ChatBubbleOutline,"Open chat")};Box{IconButton(onClick={onMenuChange(true)},modifier=Modifier.size(48.dp).semantics{contentDescription="Friend options"}){Icon(Icons.Default.MoreVert,"Friend options")};DropdownMenu(expanded=menuOpen,onDismissRequest={onMenuChange(false)}){DropdownMenuItem(text={Text("View profile")},onClick={onMenuChange(false);onOpenProfile(person.username)});DropdownMenuItem(text={Text("Remove friend")},onClick={onMenuChange(false);onRemove())}}}}
    }
}

@Composable private fun RequestRow(person:FynxSocialClient.User,primaryText:String,secondaryText:String?,busy:Boolean,onOpenProfile:(String)->Unit,onOpenChat:(String)->Unit,onPrimary:()->Unit,onSecondary:()->Unit={}){
    Card(Modifier.fillMaxWidth(),shape=FynxDesign.CardShape,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.45f))){Row(Modifier.fillMaxWidth().padding(9.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={onOpenProfile(person.username)},modifier=Modifier.size(50.dp)){FynxRemoteProfileAvatar(person.profilePhotoMediaId,person.displayName.ifBlank{person.username},Modifier.size(44.dp),ownerUsername=person.username)};Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(person.displayName.ifBlank{person.username},fontWeight=FontWeight.Bold,maxLines=1);Text("@${person.username.removePrefix("@").trim()}",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)};if(busy)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)else{if(secondaryText!=null)OutlinedButton(onClick=onSecondary,shape=FynxDesign.ControlShape){Text(secondaryText)};Spacer(Modifier.width(5.dp));Button(onClick=onPrimary,shape=FynxDesign.ControlShape){Text(primaryText)};if(primaryText=="Add")IconButton(onClick={onOpenChat(person.username)},modifier=Modifier.size(44.dp)){Icon(Icons.Default.ChatBubbleOutline,"Open chat")}}}}
}

private fun LazyListScope.emptyState(title:String,body:String){item{Card(Modifier.fillMaxWidth(),shape=FynxDesign.CardShape,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.45f))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}}}}
