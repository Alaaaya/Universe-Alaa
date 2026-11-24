package com.alaa.universe

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.security.MessageDigest
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.app.Activity
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import android.graphics.Color
import android.graphics.Typeface

/** One foreground player. No URL, credentials, errors or analytics are logged by this app. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackSession(
    private val context:Context,
    private val repositoryProvider:()->ProviderRepository,
    private val status:(String)->Unit,
    private val progressChanged:(MediaEntry,Long,Long,Boolean)->Unit = { _,_,_,_-> },
    private val seriesScope:()->String? = { null }
) {
    constructor(context:Context, fixed:ProviderRepository, status:(String)->Unit):this(context,{fixed},status)
    private val repository get()=repositoryProvider()
    init { androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF) }
    var player:ExoPlayer?=null; private set
    private var view:PlayerView?=null
    var entry:MediaEntry?=null; private set
    var zap:((Int)->Unit)?=null
    private val progressHandler=Handler(Looper.getMainLooper())
    private val trackPrefs=context.applicationContext.getSharedPreferences("playback-track-preferences-v1",Context.MODE_PRIVATE)
    private var lastAppliedTrackKey=""
    private val progressTick=object:Runnable{override fun run(){
        checkpoint()
        if(player?.isPlaying==true)progressHandler.postDelayed(this,10_000L)
    }}
    private fun checkpoint(ended:Boolean=false){
        val current=entry?:return
        val p=player?:return
        runCatching{progressChanged(current,p.currentPosition.coerceAtLeast(0),p.duration.takeIf{it>0}?:0L,ended)}
    }
    private fun scheduleCheckpoint(){progressHandler.removeCallbacks(progressTick);if(player?.isPlaying==true)progressHandler.postDelayed(progressTick,10_000L)}
    fun attach(target:PlayerView) {
        if(view===target){if(target.player!==player)target.player=player;return}
        view?.player=null;view=target
        target.player=player
        target.controllerShowTimeoutMs=4000
        target.setShowSubtitleButton(true)
        target.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
    }
    fun play(item:MediaEntry,resumePositionMs:Long=0L) {
        try {
            checkpoint()
            progressHandler.removeCallbacks(progressTick)
            val url=repository.streamUrl(item)
            val p=player?:ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory {
                    PlaylistDataSource(DefaultHttpDataSource.Factory().setUserAgent("universe-Alaa/0.2")
                        .setConnectTimeoutMs(15000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(false).createDataSource(), allowContent = { entry?.let { repository.isVisible(it) } == true }) { repository.login()?.allowHttp==true }
                }))
                .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build().also {
                    player=it
                    it.setAudioAttributes(AudioAttributes.DEFAULT,true)
                    it.setHandleAudioBecomingNoisy(true)
                    it.addListener(object:Player.Listener {
                        override fun onPlaybackStateChanged(state:Int) {
                            status(when(state){Player.STATE_READY->"";Player.STATE_BUFFERING->"Buffering…";Player.STATE_ENDED->"Playback ended";else->""})
                            if(state==Player.STATE_ENDED)checkpoint(ended=true) else if(state==Player.STATE_READY)checkpoint()
                            scheduleCheckpoint()
                        }
                        override fun onTracksChanged(tracks:Tracks) { applySavedTrackPreferences();applySubtitleStyle() }
                        override fun onPlayerError(error:PlaybackException) {status("Unable to play this stream. Check connection or choose another item.")}
                    })
                }
            entry=item
            lastAppliedTrackKey=""
            p.trackSelectionParameters=p.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
            view?.player=p
            p.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build()).build())
            p.prepare()
            applySubtitleStyle()
            if(resumePositionMs>0L)p.seekTo(resumePositionMs)
            p.play();scheduleCheckpoint()
            (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_:Exception) {status("Stream unavailable. Check playlist settings.")}
    }
    fun key(event:KeyEvent):Boolean {
        if(event.action!=KeyEvent.ACTION_DOWN)return false
        val p=player?:return false
        when(event.keyCode){
            KeyEvent.KEYCODE_CHANNEL_UP->{if(entry?.kind=="live"){zap?.invoke(1);return true}}
            KeyEvent.KEYCODE_CHANNEL_DOWN->{if(entry?.kind=="live"){zap?.invoke(-1);return true}}
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE->{if(p.playWhenReady)p.pause() else p.play();return true}
            KeyEvent.KEYCODE_MEDIA_PLAY->{p.play();return true}
            KeyEvent.KEYCODE_MEDIA_PAUSE->{p.pause();return true}
            KeyEvent.KEYCODE_CAPTIONS->{tracks(C.TRACK_TYPE_TEXT);return true}
            KeyEvent.KEYCODE_MENU->{if(entry?.kind in setOf("movie","episode")){trackMenu();return true}}
        }
        if(view?.isControllerFullyVisible!=true){
            when(event.keyCode){
                KeyEvent.KEYCODE_DPAD_LEFT->{if(entry?.kind!="live")p.seekBack();return true}
                KeyEvent.KEYCODE_DPAD_RIGHT->{if(entry?.kind!="live")p.seekForward();return true}
                KeyEvent.KEYCODE_DPAD_UP->{if(entry?.kind=="live"){zap?.invoke(1);return true}}
                KeyEvent.KEYCODE_DPAD_DOWN->{if(entry?.kind=="live"){zap?.invoke(-1);return true}}
                KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER->{view?.showController();return true}
            }
        }
        return false
    }
    private fun subtitleStyleKey():String?=trackPreferenceKey(999)
    private fun applySubtitleStyle(){
        val key=subtitleStyleKey()?:return
        val saved=runCatching{trackPrefs.getString("subtitle_style_$key",null)?.let(::JSONObject)}.getOrNull()?:return
        val size=saved.optInt("size",1)
        val background=saved.optBoolean("background",true)
        view?.subtitleView?.apply{
            setFractionalTextSize(when(size){0->0.045f;2->0.075f;else->0.06f})
            setStyle(CaptionStyleCompat(Color.WHITE,if(background)0xd9000000.toInt() else Color.TRANSPARENT,
                Color.TRANSPARENT,CaptionStyleCompat.EDGE_TYPE_OUTLINE,Color.BLACK,Typeface.DEFAULT))
        }
    }
    fun subtitleAppearanceMenu(){
        if(entry?.kind !in setOf("movie","episode"))return
        val key=subtitleStyleKey()?:return
        val prefs=trackPrefs
        val saved=runCatching{prefs.getString("subtitle_style_$key",null)?.let(::JSONObject)}.getOrNull()?:JSONObject()
        val size=saved.optInt("size",1);val background=saved.optBoolean("background",true)
        val options=arrayOf("حجم صغير","حجم متوسط","حجم كبير",if(background)"إخفاء خلفية الترجمة" else "إظهار خلفية الترجمة")
        PremiumDialogs.builder(context).setTitle("مظهر الترجمة").setSingleChoiceItems(options, -1){dialog,which->
            val next=saved
            when(which){0,1,2->next.put("size",which);3->next.put("background",!background)}
            prefs.edit().putString("subtitle_style_$key",next.toString()).apply()
            applySubtitleStyle();dialog.dismiss()
        }.show()
    }
    fun sleepTimerMenu(){
        val options=arrayOf("إيقاف المؤقت","15 دقيقة","30 دقيقة","45 دقيقة","60 دقيقة","90 دقيقة")
        PremiumDialogs.builder(context).setTitle("مؤقت النوم").setItems(options){_,which->
            if(which==0){sleepHandler.removeCallbacks(sleepStop);status("Sleep timer off")}
            else {sleepHandler.removeCallbacks(sleepStop);sleepHandler.postDelayed(sleepStop,longArrayOf(0,15,30,45,60,90)[which]*60_000L);status("Sleep timer set")}
        }.show()
    }
    private val sleepHandler=Handler(Looper.getMainLooper())
    private val sleepStop=Runnable{player?.pause();checkpoint();status("Sleep timer finished")}
    fun moreMenu(){
        val vod=entry?.kind in setOf("movie","episode")
        val items=mutableListOf<Pair<String,()->Unit>>()
        if(vod){items.add("Audio track" to {tracks(C.TRACK_TYPE_AUDIO)});items.add("Subtitle track" to {tracks(C.TRACK_TYPE_TEXT)});items.add("Subtitle appearance" to {subtitleAppearanceMenu()});items.add("Playback speed" to {speedMenu()})}
        items.add("Aspect ratio" to {aspectRatioMenu()});items.add("Sleep timer" to {sleepTimerMenu()})
        PremiumDialogs.builder(context).setTitle("Playback options").setItems(items.map{it.first}.toTypedArray()){_,i->items.getOrNull(i)?.second?.invoke()}.show()
    }
    fun aspectRatioMenu(){
        PremiumDialogs.builder(context).setTitle("Aspect ratio").setItems(arrayOf("Fit","Fill","Zoom")){_,n->
            view?.resizeMode=when(n){1->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;2->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;else->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT}
        }.show()
    }
    fun speedMenu(){
        if(entry?.kind !in setOf("movie","episode"))return
        val p=player?:return
        val speeds=floatArrayOf(0.5f,0.75f,1f,1.25f,1.5f,2f)
        val labels=speeds.map{speed->(if(kotlin.math.abs(p.playbackParameters.speed-speed)<0.01f)"✓ " else "")+"${speed}×"}.toTypedArray()
        PremiumDialogs.builder(context).setTitle("Playback speed").setItems(labels){_,which->p.setPlaybackSpeed(speeds[which])}.show()
    }
    fun trackMenu()=moreMenu()
    private fun trackPreferenceKey(type:Int):String? {
        val current=entry?:return null
        val scope=when(current.kind){
            "episode"->seriesScope()?.takeIf{it.isNotBlank()}?.let{"series:$it"} ?: return null
            "movie"->"movie:${current.id}"
            else->return null
        }
        val bytes=MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
        return "${bytes.joinToString(""){"%02x".format(it)}}:$type"
    }
    private fun rememberTrack(type:Int,format:Format?) {
        if(type !in listOf(C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_TEXT))return
        val key=trackPreferenceKey(type)?:return
        if(format==null){trackPrefs.edit().remove(key).apply();return}
        trackPrefs.edit().putString(key,JSONObject().put("language",format.language?:"").put("label",format.label?:"").toString()).apply()
    }
    private fun applySavedTrackPreferences(){
        val p=player?:return
        val current=entry?:return
        if(current.kind !in setOf("movie","episode"))return
        for(type in listOf(C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_TEXT)){
            val key=trackPreferenceKey(type)?:continue
            val token="$key:${current.id}:${current.season}"
            if(token==lastAppliedTrackKey)continue
            val saved=runCatching{trackPrefs.getString(key,null)?.let(::JSONObject)}.getOrNull()?:continue
            val language=saved.optString("language");val label=saved.optString("label")
            val option=p.currentTracks.groups.asSequence().filter{it.type==type}.flatMap{g->
                (0 until g.length).asSequence().filter{g.isTrackSupported(it)}.map{g to it}
            }.map{(g,i)->Triple(g,i,g.getTrackFormat(i))}.firstOrNull{(_,_,f)->
                (language.isNotBlank() && f.language.equals(language,true)) || (label.isNotBlank() && f.label==label)
            } ?: continue
            p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,false)
                .setOverrideForType(TrackSelectionOverride(option.first.mediaTrackGroup,option.second)).build()
            lastAppliedTrackKey=token
        }
    }
    fun tracks(type:Int){
        val current=entry?:return
        if(type in listOf(C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_TEXT) && current.kind !in setOf("movie","episode"))return
        val p=player?:return
        val options=mutableListOf<Pair<Tracks.Group,Int>>()
        p.currentTracks.groups.filter{it.type==type}.forEach{g->repeat(g.length){if(g.isTrackSupported(it))options.add(g to it)}}
        val labels=mutableListOf(if(type==C.TRACK_TYPE_TEXT)"Off" else "Auto")
        options.forEachIndexed { n,(g,i)->
            val f=g.getTrackFormat(i);labels.add(listOfNotNull(f.label,f.language?.takeIf{it!="und"},if(type==C.TRACK_TYPE_VIDEO&&f.height>0)"${f.height}p" else null).distinct().joinToString(" · ").ifBlank{"Track ${n+1}"})
        }
        if(options.isEmpty())labels.add("No supported embedded tracks")
        PremiumDialogs.builder(context).setTitle(if(type==C.TRACK_TYPE_TEXT)"Subtitles" else if(type==C.TRACK_TYPE_VIDEO)"Quality" else "Audio")
            .setItems(labels.toTypedArray()){_,i->
                if(i==0){
                    p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT).build()
                    rememberTrack(type,null)
                } else options.getOrNull(i-1)?.let{(g,n)->
                    p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,n)).build()
                    rememberTrack(type,g.getTrackFormat(n));lastAppliedTrackKey=""
                }
            }.show()
    }
    fun stop(){
        checkpoint();progressHandler.removeCallbacks(progressTick);sleepHandler.removeCallbacks(sleepStop)
        view?.player=null;view=null;player?.release();player=null;entry=null;lastAppliedTrackKey=""
        (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
}
