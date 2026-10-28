package com.alaa.universe

import android.app.AlertDialog
import android.content.Context
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

/** One foreground player. No URL, credentials, errors or analytics are logged by this app. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackSession(private val context:Context, private val repositoryProvider:()->ProviderRepository, private val status:(String)->Unit) {
    constructor(context:Context, fixed:ProviderRepository, status:(String)->Unit):this(context,{fixed},status)
    private val repository get()=repositoryProvider()
    init { androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF) }
    var player:ExoPlayer?=null; private set
    private var view:PlayerView?=null
    var entry:MediaEntry?=null; private set
    var zap:((Int)->Unit)?=null
    fun attach(target:PlayerView) {
        view?.player=null;view=target
        target.player=player
        target.controllerShowTimeoutMs=4000
        target.setShowSubtitleButton(true)
        target.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
    }
    fun play(item:MediaEntry) {
        try {
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
                        }
                        override fun onPlayerError(error:PlaybackException) {status("Unable to play this stream. Check connection or choose another item.")}
                    })
                }
            entry=item
            p.trackSelectionParameters=p.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
            view?.player=p
            p.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build()).build())
            p.prepare();p.play()
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
            KeyEvent.KEYCODE_MENU->{trackMenu();return true}
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
    fun trackMenu(){AlertDialog.Builder(context).setTitle("Playback options").setItems(arrayOf("Audio","Subtitles","Aspect ratio")){_,i->
        when(i){0->tracks(C.TRACK_TYPE_AUDIO);1->tracks(C.TRACK_TYPE_TEXT);else->AlertDialog.Builder(context).setTitle("Aspect ratio").setItems(arrayOf("Fit","Fill","Zoom")){_,n->
            view?.resizeMode=when(n){1->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;2->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;else->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT}
        }.show()}
    }.show()}
    fun tracks(type:Int){
        val p=player?:return
        val options=mutableListOf<Pair<Tracks.Group,Int>>()
        p.currentTracks.groups.filter{it.type==type}.forEach{g->repeat(g.length){if(g.isTrackSupported(it))options.add(g to it)}}
        val labels=mutableListOf(if(type==C.TRACK_TYPE_TEXT)"Off" else "Auto")
        options.forEachIndexed { n,(g,i)->
            val f=g.getTrackFormat(i);labels.add(listOfNotNull(f.label,f.language?.takeIf{it!="und"}).distinct().joinToString(" · ").ifBlank{"Track ${n+1}"})
        }
        if(options.isEmpty())labels.add("No supported embedded tracks")
        AlertDialog.Builder(context).setTitle(if(type==C.TRACK_TYPE_TEXT)"Subtitles" else "Audio")
            .setItems(labels.toTypedArray()){_,i->
                if(i==0)p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT).build()
                else options.getOrNull(i-1)?.let{(g,n)->p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,n)).build()}
            }.show()
    }
    fun stop(){view?.player=null;view=null;player?.release();player=null;entry=null
        (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
}
