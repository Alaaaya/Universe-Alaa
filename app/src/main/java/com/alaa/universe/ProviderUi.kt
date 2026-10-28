package com.alaa.universe

import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.media3.ui.PlayerView
import java.util.concurrent.Executors

/** Real-provider routes. Lists are paged so a 50,000 item account never creates 50,000 Views. */
class ProviderUi(private val screen:UniverseScreen) {
    private val context=screen.context
    val repository=ProviderRepository(context)
    var snapshot:ProviderSnapshot?=null; private set
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var closed=false
    private var busy=false
    private var generation=0
    private var mode="live"
    private var category=""
    private var offset=0
    private var catOffset=0
    private var nextCatFocus:Int?=null
    private var nextCardFocus:Int?=null
    private var selected:MediaEntry?=null
    private var episodeList=emptyList<MediaEntry>()
    private var season=0
    private var seasonOffset=0
    private var nextSeasonFocus:Int?=null
    private var nextEpisodeFocus:Int?=null
    private var detailGeneration=0
    private var episodeOffset=0
    private var full=false
    private var statusView:TextView?=null
    private var stage:Stage?=null
    private val pictures=mutableListOf<ImageView>()
    var imageHttpAllowed=false; private set
    private var currentList=emptyList<MediaEntry>()
    private var favorites=loadSet("favorites")
    private var recents=loadSet("recent")
    val playback=PlaybackSession(context,repository){message->statusView?.text=message}
    var testMode=false
    private fun loadSet(key:String)=context.getSharedPreferences("media-state",0).getStringSet(key,emptySet())!!.toMutableSet()
    private fun identity(e:MediaEntry)=e.kind+":"+e.id
    private fun saveState(){context.getSharedPreferences("media-state",0).edit().putStringSet("favorites",favorites).putStringSet("recent",recents).apply()}
    fun start(){
        if(testMode||closed)return
        worker.execute {
            val cached=runCatching{repository.cached()}.getOrNull()
            val http=runCatching{repository.login()?.allowHttp==true}.getOrDefault(false)
            main.post{if(!closed){snapshot=cached;imageHttpAllowed=http;if(screen.page=="home")screen.home();refresh(false)}}
        }
    }
    fun refresh(notify:Boolean=true) {
        if(testMode||busy||closed)return
        busy=true
        val token=generation
        worker.execute {
            val result=runCatching{repository.refresh()}
            main.post {
                busy=false
                if(!closed&&token==generation){
                    result.onSuccess{snapshot=it;if(screen.page=="home")screen.home();if(notify)toast("Playlist updated")}
                    result.onFailure{if(notify)toast("Refresh failed. Saved playlist remains available.")}
                }
            }
        }
    }
    private fun toast(value:String){Toast.makeText(context,value,Toast.LENGTH_LONG).show()}
    fun loginDialog(){
        if(busy){toast("Playlist is updating");return}
        val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(32,12,32,12);layoutDirection=View.LAYOUT_DIRECTION_LTR}
        fun field(hint:String,password:Boolean=false)=EditText(context).apply{
            this.hint=hint;setSingleLine(true);inputType=if(password)InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            root.addView(this)
        }
        val name=field("Playlist name")
        val server=field("Server URL for Xtream")
        val username=field("Username")
        val password=field("Password",true)
        val m3u=field("Or full M3U URL",true)
        val http=CheckBox(context).apply{text="Allow insecure HTTP for this playlist (credentials may be exposed)";root.addView(this)}
        val help=TextView(context).apply{text="Use HTTPS where supported. Enter Xtream details OR an M3U URL. Saved locally with Android Keystore encryption.";root.addView(this)}
        val scroll=ScrollView(context).apply{addView(root)}
        val dialog=AlertDialog.Builder(context).setTitle("Connect playlist").setView(scroll).setNegativeButton("Cancel",null)
            .setNeutralButton("Remove saved playlist"){_,_->confirmRemove()}
            .setPositiveButton("Connect",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(busy)return@setOnClickListener
            val login=ProviderLogin(name.text.toString().trim().ifBlank{runCatching{java.net.URI(if(m3u.text.isNotBlank())m3u.text.toString().trim() else server.text.toString().trim()).host}.getOrNull()?:"Playlist"},server.text.toString().trim(),username.text.toString().trim(),password.text.toString(),m3u.text.toString().trim(),http.isChecked)
            busy=true;help.text="Connecting…";dialog.getButton(-1).isEnabled=false
            val token=generation
            worker.execute{
                val result=runCatching{repository.connect(login)}
                main.post{
                    busy=false
                    if(!closed&&generation==token){
                        result.onSuccess{
                            snapshot=it;imageHttpAllowed=login.allowHttp;favorites.clear();recents.clear();saveState()
                            password.text.clear();m3u.text.clear();dialog.dismiss();screen.home()
                        }
                        result.onFailure{help.text="Connection failed. Check the URL, credentials, subscription and HTTP permission.";dialog.getButton(-1).isEnabled=true}
                    }
                }
            }
        }}
        dialog.show()
    }
    private fun confirmRemove(){
        AlertDialog.Builder(context).setTitle("Remove this playlist?")
            .setMessage("This deletes saved credentials and private cached lists from this device. You will need to enter the login again.")
            .setNegativeButton("Cancel",null).setPositiveButton("Remove"){_,_->
                generation++;playback.stop();snapshot=null;favorites.clear();recents.clear();saveState()
                worker.execute{repository.clear()}
                screen.home()
            }.show()
    }
    fun requireConnection():Boolean {if(snapshot!=null)return true;loginDialog();return false}
    private val accent get()=when(screen.theme){"red"->0xffe50914.toInt();"gold"->0xffd8b602.toInt();else->0xff518df4.toInt()}
    private fun reset(){
        pictures.forEach{com.bumptech.glide.Glide.with(context).clear(it)};pictures.clear()
        screen.removeAllViews()
        stage=Stage(context).also{it.theme=screen.theme;screen.addView(it,FrameLayout.LayoutParams(-1,-1))}
        statusView=null
    }
    private fun text(s:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f):TextView{
        val v=TextView(context).apply{text=s;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;includeFontPadding=false;layoutDirection=View.LAYOUT_DIRECTION_LTR}
        stage!!.put(v,x,y,w,h,size);return v
    }
    private fun artwork(e:MediaEntry,x:Int,y:Int,w:Int,h:Int){
        val uri=runCatching{ProviderRepository.validatedUrl(e.icon,imageHttpAllowed)}.getOrNull()?:return
        val image=ImageView(context).apply{scaleType=ImageView.ScaleType.FIT_CENTER;isFocusable=false}
        stage!!.put(image,x,y,w,h);pictures.add(image)
        com.bumptech.glide.Glide.with(context).asBitmap().load(uri.toASCIIString()).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).override(w,h).timeout(15000).into(image)
    }
    private fun button(s:String,x:Int,y:Int,w:Int,h:Int,action:()->Unit):TextView=
        text(s,x,y,w,h).apply{
            id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            background=surface(0x44182438);setOnFocusChangeListener{_,f->background=surface(if(f)accent else 0x44182438)}
            setOnClickListener{action()};contentDescription=s
        }
    private fun focus(v:View?){v?.requestFocus()}
    private fun header(title:String){
        text("universe-Alaa",48,30,620,75,42f).setTextColor(accent)
        text(title,800,30,1072,75,43f)
    }
    private fun kindCategories()=snapshot?.categories.orEmpty().filter{it.kind==mode}
    private fun entries()=snapshot?.entries.orEmpty().filter{it.kind==mode&&when(category){"favorites"->identity(it) in favorites;"recent"->identity(it) in recents;""->true;else->it.categoryId==category}}
    fun categories(){
        playback.stop();full=false;mode="live";category="";offset=0;catOffset=0
        browse("live",true)
    }
    fun browse(kind:String,categoryOnly:Boolean=false){
        full=false;detailGeneration++
        if(mode!=kind){category="";offset=0;catOffset=0}
        mode=kind
        if(kind!="live")playback.stop()
        reset();header(if(kind=="live")"Live TV" else if(kind=="movie")"Movies" else "Series")
        val cats=listOf(MediaCategory("","All",kind),MediaCategory("favorites","Favorites",kind),MediaCategory("recent","Recently viewed",kind))+kindCategories()
        catOffset=catOffset.coerceIn(0,maxOf(0,cats.size-10))
        val catRows=cats.drop(catOffset).take(10).mapIndexed{i,c->
            button(c.name,30,160+i*76,if(categoryOnly)900 else 430,70){
                category=c.id;offset=0
                if(categoryOnly)screen.live() else browse(mode)
            }.also{v->v.setOnKeyListener{_,key,event->
                if(event.action!=KeyEvent.ACTION_DOWN)false
                else if(key==20&&i==9&&catOffset+10<cats.size){catOffset++;nextCatFocus=9;browse(mode,categoryOnly);true}
                else if(key==19&&i==0&&catOffset>0){catOffset--;nextCatFocus=0;browse(mode,categoryOnly);true}
                else false
            }}
        }
        if(categoryOnly){
            text(snapshot?.name.orEmpty(),1070,410,760,90,46f)
            text("Choose a category to watch",1070,520,760,65)
            focus(catRows.getOrNull(nextCatFocus?:0));nextCatFocus=null;return
        }
        currentList=entries();offset=offset.coerceIn(0,maxOf(0,currentList.size-1))
        val live=kind=="live";val count=if(live)12 else 10
        val items=currentList.drop(offset).take(count)
        val cards=items.mapIndexed{i,e->
            val x=if(live)490 else 500+(i%5)*274
            val y=if(live)160+i*66 else 160+(i/5)*390
            val w=if(live)470 else 253;val h=if(live)60 else 370
            button(if(live)e.name else "",x,y,w,h){
                selected=e;recents.add(identity(e));saveState()
                when(e.kind){
                    "live"->{if(playback.entry?.id==e.id)fullscreen() else {playback.play(e);nextCardFocus=i;browse("live")}}
                    "series"->{season=0;seasonOffset=0;episodeOffset=0;episodeList=emptyList();screen.seriesDetail()}
                    else->screen.movieDetail()
                }
            }.also{v->
                if(!live){
                    text(e.name.take(1),x+14,y+28,w-28,265,60f).apply{gravity=Gravity.CENTER;setTextColor(accent)}
                    artwork(e,x+5,y+5,w-10,295)
                    text(e.name,x+12,y+310,w-24,52,24f).apply{maxLines=2;gravity=Gravity.CENTER}
                }
                v.setOnKeyListener{_,key,event->
                    if(event.action!=KeyEvent.ACTION_DOWN)false
                    else if(key==20&&i>=items.size-(if(live)1 else 5)&&offset+count<currentList.size){offset+=count;nextCardFocus=i%(if(live)1 else 5);browse(mode);true}
                    else if(key==19&&i<(if(live)1 else 5)&&offset>0){offset=maxOf(0,offset-count);nextCardFocus=if(live)count-1 else count-5+i;browse(mode);true}
                    else false
                }
            }
        }
        catRows.forEach{it.nextFocusRightId=cards.firstOrNull()?.id?:it.id}
        cards.forEachIndexed{i,v->
            v.nextFocusLeftId=if(live||i%5==0)catRows.firstOrNull()?.id?:v.id else cards[i-1].id
            v.nextFocusRightId=if(live)v.id else cards.getOrNull(i+1)?.id?:v.id
            v.nextFocusUpId=cards.getOrNull(i-(if(live)1 else 5))?.id?:v.id
            v.nextFocusDownId=cards.getOrNull(i+(if(live)1 else 5))?.id?:v.id
        }
        if(items.isEmpty())text("No items in this category",520,380,1000,100,37f)
        if(live){
            val target=PlayerView(context).apply{useController=false;isFocusable=false}
            stage!!.put(target,990,160,882,496);playback.attach(target)
            val title=selected?.takeIf{it.kind=="live"}?.name?:"Select a channel"
            text(title,990,680,882,90,35f)
            val open=button("Fullscreen",990,800,360,72){if(playback.entry!=null)fullscreen()}
            val tracks=button("Audio / Subtitles",1375,800,480,72){playback.trackMenu()}
            val fav=button("Favorite",990,890,360,64){selected?.let{if(!favorites.add(identity(it)))favorites.remove(identity(it));saveState();toast("Favorites updated")}}
            tracks.nextFocusLeftId=open.id;open.nextFocusRightId=tracks.id
            cards.forEach{it.nextFocusRightId=open.id};open.nextFocusLeftId=cards.firstOrNull()?.id?:open.id
            statusView=text(if(playback.player==null)"Select a channel to start playback" else "",990,968,870,50,24f)
            playback.zap={delta->zap(delta)}
            open.nextFocusDownId=fav.id
        }
        text("${if(currentList.isEmpty())0 else offset+1}–${offset+items.size} / ${currentList.size}     ↑ ↓ More items     BACK Home",490,1015,1380,45,24f)
        focus(if(nextCatFocus!=null)catRows.getOrNull(nextCatFocus!!) else cards.getOrNull((nextCardFocus?:0).coerceAtMost(cards.lastIndex.coerceAtLeast(0)))?:catRows.firstOrNull());nextCatFocus=null;nextCardFocus=null
    }
    private fun zap(delta:Int){
        val list=if(currentList.isEmpty())snapshot?.entries.orEmpty().filter{it.kind=="live"} else currentList
        if(list.isEmpty())return
        val at=list.indexOfFirst{it.id==playback.entry?.id}
        selected=list[Math.floorMod(at+delta,list.size)]
        selected?.let{playback.play(it);recents.add(identity(it));saveState()}
        if(full)fullscreen() else browse("live")
    }
    fun detail(series:Boolean){
        val detailToken=++detailGeneration
        val e=selected?:run{browse(if(series)"series" else "movie");return}
        playback.stop();reset();header(e.name)
        text(e.name,80,200,540,630,55f).apply{maxLines=7;gravity=Gravity.CENTER;background=surface(0x55233149)}
        artwork(e,85,205,530,620)
        if(!series){
            text(e.plot.ifBlank{"Movie from your connected playlist"},720,220,1100,400,33f).apply{maxLines=8;gravity=Gravity.TOP}
            val play=button("Play Movie",720,800,480,90){playback.play(e);fullscreen()}
            val fav=button(if(identity(e) in favorites)"Remove Favorite" else "Add Favorite",1230,800,570,90){if(!favorites.add(identity(e)))favorites.remove(identity(e));saveState();detail(false)}
            play.nextFocusRightId=fav.id;fav.nextFocusLeftId=play.id;focus(play)
        } else {
            text("Loading seasons and episodes…",720,220,1100,100,36f)
            val id=e.id
            worker.execute{
                val cached=runCatching{repository.cachedEpisodes(e)}.getOrDefault(emptyList())
                if(cached.isNotEmpty())main.post{if(!closed&&detailToken==detailGeneration&&selected?.id==id&&screen.page=="series-detail"){episodeList=cached;if(!full)renderEpisodes()}}
                val result=runCatching{repository.episodes(e)}
                main.post{if(!closed&&detailToken==detailGeneration&&selected?.id==id&&screen.page=="series-detail"){
                    result.onSuccess{episodeList=it;season=it.firstOrNull()?.season?:0;seasonOffset=0;episodeOffset=0;if(!full)renderEpisodes()}
                    result.onFailure{if(cached.isEmpty()&&!full)text("Episodes unavailable. Check connection and try again.",720,340,1100,100,30f)}
                }}
            }
        }
    }
    private fun renderEpisodes(){
        if(full)return
        reset();header(selected?.name.orEmpty())
        val seasons=episodeList.map{it.season}.distinct().sorted()
        if(season !in seasons)season=seasons.firstOrNull()?:0
        if(seasons.isEmpty()){text("No episodes returned by this provider",100,320,1700,100,38f);return}
        seasonOffset=seasonOffset.coerceIn(0,maxOf(0,seasons.size-10))
        val rows=seasons.drop(seasonOffset).take(10).mapIndexed{i,n->
            button("Season $n",80,180+i*76,470,70){season=n;episodeOffset=0;renderEpisodes()}.also{v->
                v.setOnKeyListener{_,key,event->if(event.action!=0)false
                else if(key==20&&i==9&&seasonOffset+10<seasons.size){seasonOffset++;nextSeasonFocus=9;renderEpisodes();true}
                else if(key==19&&i==0&&seasonOffset>0){seasonOffset--;nextSeasonFocus=0;renderEpisodes();true}else false}
            }
        }
        val all=episodeList.filter{it.season==season}
        episodeOffset=episodeOffset.coerceIn(0,maxOf(0,all.size-1))
        val eps=all.drop(episodeOffset).take(10).mapIndexed{i,e->
            button(e.name,620,180+i*76,1210,70){recents.add(identity(e));saveState();playback.play(e);fullscreen()}.also{v->
                v.setOnKeyListener{_,key,event->if(event.action!=0)false
                else if(key==20&&i==9&&episodeOffset+10<all.size){episodeOffset+=10;nextEpisodeFocus=0;renderEpisodes();true}
                else if(key==19&&i==0&&episodeOffset>0){episodeOffset=maxOf(0,episodeOffset-10);nextEpisodeFocus=9;renderEpisodes();true}else false}
            }
        }
        rows.forEach{it.nextFocusRightId=eps.firstOrNull()?.id?:it.id}
        eps.forEach{it.nextFocusLeftId=rows.firstOrNull()?.id?:it.id}
        text("${all.size} episodes   ·   ↑ ↓ More episodes",620,985,1200,60,25f)
        focus(if(nextSeasonFocus!=null)rows.getOrNull(nextSeasonFocus!!) else eps.getOrNull((nextEpisodeFocus?:0).coerceAtMost(eps.lastIndex.coerceAtLeast(0)))?:rows.firstOrNull());nextSeasonFocus=null;nextEpisodeFocus=null
    }
    private fun fullscreen(){
        if(playback.player==null)return
        full=true;reset()
        val target=PlayerView(context).apply{useController=true;isFocusable=true;id=View.generateViewId()}
        stage!!.put(target,0,0,1920,1080);playback.attach(target)
        statusView=text("",80,80,1700,60,27f)
        button("Audio / Subtitles",1450,10,430,65){playback.trackMenu()}
        target.requestFocus();target.showController()
    }
    fun key(event:KeyEvent)=full&&playback.key(event)
    fun back():Boolean{
        if(full){full=false;if(mode=="live")browse(mode) else {playback.stop();if(mode=="series")renderEpisodes() else detail(false)};return true}
        return false
    }
    fun stop(){detailGeneration++;playback.stop();if(full){full=false;screen.home()}}
    fun close(){closed=true;generation++;playback.stop();pictures.forEach{com.bumptech.glide.Glide.with(context).clear(it)};pictures.clear();worker.shutdownNow()}
}
