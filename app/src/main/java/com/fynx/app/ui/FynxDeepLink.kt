package com.fynx.app.ui

import android.net.Uri

sealed interface FynxDeepLinkDestination {
    data object Home : FynxDeepLinkDestination
    data class Invite(val code: String?, val groupId: String? = null) : FynxDeepLinkDestination
    data class Profile(val username: String) : FynxDeepLinkDestination
    data class Chat(val username: String) : FynxDeepLinkDestination
    data class Call(val username: String, val video: Boolean) : FynxDeepLinkDestination
    data class Group(val id: String) : FynxDeepLinkDestination
    data class Marketplace(val listingId: String?) : FynxDeepLinkDestination
    data object Stories : FynxDeepLinkDestination
    data object Money : FynxDeepLinkDestination
}

object FynxDeepLinkParser {
    private const val FYNX_HOST = "fynx.app"
    private const val INVITE_PATH = "/invite"
    private const val HOME_PATH = "/home"
    private const val PROFILE_PATH = "/profile"
    private const val CHAT_PATH = "/chat"
    private const val CALL_PATH = "/call"
    private const val GROUP_PATH = "/group"
    private const val MARKETPLACE_PATH = "/marketplace"
    private const val STORIES_PATH = "/stories"
    private const val MONEY_PATH = "/money"

    fun homeWebLink(): String = "https://$FYNX_HOST$HOME_PATH"
    fun homeAppLink(): String = "fynx://home"
    fun inviteWebLink(code: String?, groupId: String? = null): String {
        val normalized=code?.trim()?.takeIf{it.isNotBlank()}?:return "https://$FYNX_HOST$INVITE_PATH"
        return Uri.Builder().scheme("https").authority(FYNX_HOST).path(INVITE_PATH).appendQueryParameter("code",normalized).apply{groupId?.trim()?.takeIf{it.isNotBlank()}?.let{appendQueryParameter("groupId",it)}}.build().toString()
    }
    fun profileWebLink(username:String):String=routeWebLink(PROFILE_PATH,username)
    fun profileAppLink(username:String):String=routeAppLink("profile",username)
    fun chatWebLink(username:String):String=routeWebLink(CHAT_PATH,username)
    fun chatAppLink(username:String):String=routeAppLink("chat",username)
    fun callAppLink(username:String, video:Boolean=false):String=Uri.Builder().scheme("fynx").authority("call").appendPath(username.trim().removePrefix("@")).appendQueryParameter("video",video.toString()).build().toString()
    fun groupWebLink(id:String):String=routeWebLink(GROUP_PATH,id)
    fun groupAppLink(id:String):String=routeAppLink("group",id)
    fun marketplaceWebLink(listingId:String?=null):String=routeWebLink(MARKETPLACE_PATH,listingId)
    fun marketplaceAppLink(listingId:String?=null):String=routeAppLink("marketplace",listingId)
    fun storiesWebLink():String="https://$FYNX_HOST$STORIES_PATH"
    fun storiesAppLink():String="fynx://stories"
    fun moneyWebLink():String="https://$FYNX_HOST$MONEY_PATH"
    fun moneyAppLink():String="fynx://money"
    private fun routeWebLink(path:String,value:String?):String=Uri.Builder().scheme("https").authority(FYNX_HOST).path(path).apply{value?.trim()?.takeIf{it.isNotBlank()}?.let{appendPath(it.removePrefix("@"))}}.build().toString()
    private fun routeAppLink(host:String,value:String?):String=Uri.Builder().scheme("fynx").authority(host).apply{value?.trim()?.takeIf{it.isNotBlank()}?.let{appendPath(it.removePrefix("@"))}}.build().toString()
    private fun cleanIdentifier(value:String?):String?=value?.trim()?.removePrefix("@")?.takeIf{it.isNotBlank()}

    fun parse(uri:Uri?):FynxDeepLinkDestination?{
        if(uri==null)return null
        val isFynxScheme=uri.scheme.equals("fynx",true)
        val isFynxWeb=uri.scheme.equals("https",true)&&uri.host.equals(FYNX_HOST,true)
        if(!isFynxScheme&&!isFynxWeb)return null
        val normalizedPath=uri.path.orEmpty().trim('/').split('/').filter{it.isNotBlank()}
        val first=normalizedPath.firstOrNull()?.lowercase().orEmpty()
        val valueIndex = if (isFynxScheme) 0 else 1
        val value=cleanIdentifier(normalizedPath.getOrNull(valueIndex))
        val host=uri.host.orEmpty().lowercase()
        val queryCode=uri.getQueryParameter("code")?.trim()?.takeIf{it.isNotBlank()}
        val queryGroupId=uri.getQueryParameter("groupId")?.trim()?.takeIf{it.isNotBlank()}
        val queryVideo=uri.getQueryParameter("video")?.equals("true",true)==true
        if(isFynxScheme)return when(host){
            "home"->if(normalizedPath.isEmpty())FynxDeepLinkDestination.Home else null
            "stories"->if(normalizedPath.isEmpty())FynxDeepLinkDestination.Stories else null
            "money"->if(normalizedPath.isEmpty())FynxDeepLinkDestination.Money else null
            "profile"->if(normalizedPath.size==1)value?.let{FynxDeepLinkDestination.Profile(it)}else null
            "chat"->if(normalizedPath.size==1)value?.let{FynxDeepLinkDestination.Chat(it)}else null
            "call"->if(normalizedPath.size==1)value?.let{FynxDeepLinkDestination.Call(it,queryVideo)}else null
            "group"->when{
                normalizedPath.size==1->value?.let{FynxDeepLinkDestination.Group(it)}
                normalizedPath.size==3&&normalizedPath[1].equals("invite",true)->cleanIdentifier(normalizedPath[2])?.let{FynxDeepLinkDestination.Invite("${value.orEmpty()}:$it",value)}
                else->null
            }
            "marketplace"->if(normalizedPath.size<=1)FynxDeepLinkDestination.Marketplace(value)else null
            "invite"->if(normalizedPath.size<=1)FynxDeepLinkDestination.Invite(queryCode?:value,queryGroupId)else null
            else->null
        }
        return when(first){
            ""->if(normalizedPath.isEmpty())FynxDeepLinkDestination.Home else null
            "home"->if(normalizedPath.size==1)FynxDeepLinkDestination.Home else null
            "invite"->if(normalizedPath.size==1)FynxDeepLinkDestination.Invite(queryCode?:value,queryGroupId)else null
            "profile"->if(normalizedPath.size==2)value?.let{FynxDeepLinkDestination.Profile(it)}else null
            "chat"->if(normalizedPath.size==2)value?.let{FynxDeepLinkDestination.Chat(it)}else null
            "call"->if(normalizedPath.size==2)value?.let{FynxDeepLinkDestination.Call(it,queryVideo)}else null
            "group"->if(normalizedPath.size==2)value?.let{FynxDeepLinkDestination.Group(it)}else null
            "marketplace"->if(normalizedPath.size<=2)FynxDeepLinkDestination.Marketplace(value)else null
            "stories"->if(normalizedPath.size==1)FynxDeepLinkDestination.Stories else null
            "money"->if(normalizedPath.size==1)FynxDeepLinkDestination.Money else null
            else->null
        }
    }
}
