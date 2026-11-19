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
    private val manualRepository=ProviderRepository(context)
    private val panelRepository=ProviderRepository(CredentialVault.named(context,"panel-provider-v2"))
    private var panelSelected=false
    val repository get()=if(panelSelected)panelRepository else manualRepository
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
    private var categoryOnlyView=false
    private val mediaOrder=MediaOrder(context.getSharedPreferences("media-order-v1",0))
    private data class MoveTarget(val categoryRow:Boolean,val id:String)
    private val moveRows=mutableMapOf<View,MoveTarget>()
    private var moving:MediaMove?=null
    private var movingCategories=false
    private var heldOk:Int?=null
    private var pendingSave=false
    private var pendingMove:MoveTarget?=null
    private var pendingClick:View?=null
    private var longStarted=false
    private val longOk=Runnable {
        val target=pendingMove
        if(heldOk!=null && target!=null && moving==null && pendingClick?.hasFocus()==true){
            longStarted=true;showItemMenu(target)
        }
    }
    private fun orderScope(categories:Boolean):List<String> {
        val login=runCatching{repository.login()}.getOrNull()
        // Credentials are used only as an in-memory scope input, then hashed by MediaOrder.
        val account=org.json.JSONArray(listOf(login?.server,login?.username,login?.m3uUrl,
            if(login==null)snapshot?.name else "")).toString()
        return listOf(if(panelSelected)"panel" else "manual",account,mode,
            if(categories)"categories" else "channels",if(categories)"" else category)
    }
    private fun <T> ordered(values:List<T>,categories:Boolean,id:(T)->String):List<T> {
        val byId=values.associateBy(id)
        val scope=orderScope(categories)
        val ids=if(moving?.scope==scope)moving!!.ids(values.map(id)) else mediaOrder.ids(scope,values.map(id))
        return ids.mapNotNull{byId[it]}
    }
    private fun allCategories()=ordered(listOf(MediaCategory("","All",mode),MediaCategory("favorites","Favorites",mode),MediaCategory("recent","Recently viewed",mode))+kindCategories(),true){it.id}
    private fun moveIds(categories:Boolean)=if(categories)allCategories().map{it.id} else entries().map{it.id}
    private fun bindMove(view:View,categories:Boolean,id:String) {
        val target=MoveTarget(categories,id);moveRows[view]=target
        view.setOnLongClickListener{longStarted=true;showItemMenu(target);true}
        if(moving?.id==id && movingCategories==categories){
            view.contentDescription="Moving "+view.contentDescription+". Up or Down to move, OK to save, Back to cancel."
            (view as? TextView)?.let{it.text="↕ "+it.text}
        }
    }
    private fun showItemMenu(target:MoveTarget){
        val scope=orderScope(target.categoryRow)
        val entry=if(target.categoryRow)null else currentList.firstOrNull{it.id==target.id}
        val labels=mutableListOf("نقل")
        if(entry!=null)labels.add(if(identity(entry) in favorites)"احذف من المفضلة" else "أضف إلى المفضلة")
        val menu=PremiumDialogs.builder(context).setItems(labels.toTypedArray()){_,which->
            clearMoveInput()
            if(scope!=orderScope(target.categoryRow))return@setItems
            if(which==0)beginMove(target)
            else if(entry!=null){
                if(!favorites.add(identity(entry)))favorites.remove(identity(entry))
                saveState();browse(mode,categoryOnlyView)
            }
        }.create()
        // The dialog owns the window as soon as it opens. Consume the initiating
        // hold's remaining repeats/release there too, not just in the screen.
        menu.setOnKeyListener{_,key,event->
            if(heldOk==key){
                if(event.action==KeyEvent.ACTION_UP)clearMoveInput()
                true
            }else false
        }
        menu.show()
        menu.window?.decorView?.layoutDirection=View.LAYOUT_DIRECTION_LTR
    }
    private fun beginMove(target:MoveTarget){
        if(moving!=null)return
        movingCategories=target.categoryRow
        moving=MediaMove(orderScope(target.categoryRow),target.id,moveIds(target.categoryRow))
        renderMove()
    }
    private fun renderMove(){
        val move=moving?:return
        val at=moveIds(movingCategories).indexOf(move.id)
        if(movingCategories){
            if(at>=0){catOffset=catOffset.coerceIn(maxOf(0,at-9),at);nextCatFocus=at-catOffset}
        }else if(at>=0){
            offset=offset.coerceIn(maxOf(0,at-11),at);nextCardFocus=at-offset
        }
        browse(mode,categoryOnlyView)
    }
    private fun finishMove(save:Boolean){
        val move=moving?:return
        val categories=movingCategories
        val focusId=move.id
        if(save && !mediaOrder.save(move.scope,move.ids(moveIds(categories)))){
            toast("Could not save order. Try again.");return
        }
        moving=null
        val at=moveIds(categories).indexOf(focusId)
        if(categories && at>=0){catOffset=catOffset.coerceIn(maxOf(0,at-9),at);nextCatFocus=at-catOffset}
        else if(at>=0){offset=offset.coerceIn(maxOf(0,at-11),at);nextCardFocus=at-offset}
        browse(mode,categoryOnlyView)
    }
    private fun clearMoveInput(){main.removeCallbacks(longOk);pendingMove=null;pendingClick=null;heldOk=null;longStarted=false;pendingSave=false}
    private var selected:MediaEntry?=null
    private var episodeList=emptyList<MediaEntry>()
    private var season=0
    private var seasonOffset=0
    private var nextSeasonFocus:Int?=null
    private var nextEpisodeFocus:Int?=null
    private var detailGeneration=0
    private var episodeOffset=0
    private var full=false
    private var fullscreenControls:FullscreenControls?=null
    private var statusView:TextView?=null
    private var stage:Stage?=null
    private val pictures=mutableListOf<ImageView>()
    var imageHttpAllowed=false; private set
    private var currentList=emptyList<MediaEntry>()
    private var favoriteScope=""
    private var favoriteSnapshot:ProviderSnapshot?=null
    private var favoriteIds=mutableSetOf<String>()
    private fun favoriteKey():String {
        val bytes=java.security.MessageDigest.getInstance("SHA-256").digest(org.json.JSONArray(orderScope(false).take(2)).toString().toByteArray())
        return "favorites-"+bytes.joinToString(""){"%02x".format(it)}
    }
    private val favorites:MutableSet<String> get(){
        val key=favoriteKey()
        if(key!=favoriteScope){favoriteScope=key;favoriteSnapshot=null;favoriteIds=loadSet(key)}
        val current=snapshot
        val state=context.getSharedPreferences("media-state",0)
        if(current!=null && state.contains("favorites")){
            favoriteIds.addAll(loadSet("favorites"))
            val valid=current.entries.mapTo(HashSet()){identity(it)}
            favoriteIds.retainAll(valid)
            state.edit().putStringSet(key,favoriteIds.toSet()).remove("favorites").commit()
        }
        if(current!=null && current!==favoriteSnapshot){
            favoriteSnapshot=current
            val valid=current.entries.mapTo(HashSet()){identity(it)}
            if(favoriteIds.retainAll(valid))context.getSharedPreferences("media-state",0).edit().putStringSet(key,favoriteIds.toSet()).apply()
        }
        return favoriteIds
    }
    private var recents=loadSet("recent")
    val playback=PlaybackSession(context,{repository}){message->statusView?.text=message}
    private val panelNetwork=Executors.newSingleThreadExecutor()
    private val panelLoads=Executors.newSingleThreadExecutor()
    private val panel by lazy { PanelIntegration(
        com.alaa.universe.panel.KeystorePanelStore(context),
        com.alaa.universe.panel.HttpsPanelTransport(),panelRepository,
        java.util.concurrent.Executor { panelLoads.execute(it) },
        java.util.concurrent.Executor { main.post(it) },
        changed={value,_->handlePanelChange(value)},stopped={if(panelSelected){generation++;detailGeneration++;playback.stop();snapshot=null
            pictures.forEach{com.bumptech.glide.Glide.with(context).clear(it)};pictures.clear()
            com.bumptech.glide.Glide.get(context).clearMemory()
        }}
    ) }
    private fun handlePanelChange(value:ProviderSnapshot?){
            run{
                val selectedNow=panel.ownsProvider
                if(selectedNow || panelSelected){
                    moving=null;clearMoveInput();full=false;playback.stop()
                    generation++;detailGeneration++;busy=false
                    panelSelected=selectedNow
                    snapshot=if(selectedNow)value else runCatching{manualRepository.cached()}.getOrNull()
                    if(selectedNow && value!=null){
                        val valid=value.entries.mapTo(HashSet()){identity(it)}
                        recents.retainAll(valid);saveState()
                    }
                    imageHttpAllowed=runCatching{repository.login()?.allowHttp==true}.getOrDefault(false)
                    episodeList=emptyList();selected=null;currentList=emptyList()
                    if(!closed)screen.home()
                }
            }
    }
    private var foreground=false
    private val panelTick=object:Runnable {
        override fun run(){
            if(closed||!foreground||testMode)return
            panel.tick()
            main.postDelayed(this,1000)
        }
    }
    private val panelPoll=object:Runnable {
        override fun run(){
            if(closed||!foreground||testMode)return
            panelNetwork.execute{panel.open()}
            main.postDelayed(this,60000)
        }
    }
    fun panelSummary():String = if(testMode)"Device code unavailable" else
        "Device code  "+panel.code.ifBlank{"Preparing…"}+"\n"+panel.status
    var testMode=false
    private fun loadSet(key:String)=context.getSharedPreferences("media-state",0).getStringSet(key,emptySet())!!.toMutableSet()
    private fun identity(e:MediaEntry)=e.kind+":"+e.id
    private fun saveState(){context.getSharedPreferences("media-state",0).edit().putStringSet(favoriteKey(),favorites.toSet()).putStringSet("recent",recents).apply()}
    fun start(){
        if(testMode||closed)return
        foreground=true
        panel.tick()
        main.removeCallbacks(panelTick);main.post(panelTick)
        main.removeCallbacks(panelPoll);main.post(panelPoll)
        val startToken=generation
        worker.execute {
            val cached=runCatching{manualRepository.cached()}.getOrNull()
            val http=runCatching{repository.login()?.allowHttp==true}.getOrDefault(false)
            main.post{if(!closed&&startToken==generation&&!panelSelected){snapshot=cached;imageHttpAllowed=http;if(screen.page=="home")screen.home();refresh(false)}}
        }
    }
    fun refresh(notify:Boolean=true) {
        if(testMode||busy||closed)return
        if(panelSelected){
            // Refresh secure policy before starting provider work, never bypass panel hides.
            panelNetwork.execute{panel.open()}
            // Initial panel loading owns its own worker and has not committed a login yet.
            // Do not turn "Refresh" into an unrelated manual-provider/no-login failure.
            if(panelRepository.login()==null)return
        }
        val target=repository
        if(target.login()==null) {
            panelNetwork.execute{panel.open()}
            return
        }
        busy=true
        val token=generation
        worker.execute {
            val result=runCatching{target.refresh(loadAllCategories=target!==panelRepository)}
            main.post {
                busy=false
                if(!closed&&token==generation){
                    result.onSuccess{snapshot=it;if(screen.page=="home")screen.home();if(notify)toast("Playlist updated")}
                    result.onFailure{if(notify)toast("Refresh failed. Saved playlist remains available.")}
                }
            }
        }
    }
    private fun toast(value:String){PremiumDialogs.toast(context,value,true)}
    fun loginDialog(){
        if(busy){toast("Playlist is updating");return}
        val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(PremiumDialogs.dp(context,24),0,PremiumDialogs.dp(context,24),PremiumDialogs.dp(context,8))}
        val device=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(12,8,12,8);background=PremiumDialogs.surface(context,0xff1a2433.toInt())}
        device.addView(PremiumDialogs.text(context,panelSummary(),13f),LinearLayout.LayoutParams(0,-2,1f))
        device.addView(PremiumDialogs.button(context,"Copy"){(context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Device code",if(testMode)"" else panel.code));toast("Device code copied")})
        root.addView(device)
        val tabs=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,12,0,12)};root.addView(tabs,LinearLayout.LayoutParams(-1,PremiumDialogs.dp(context,80)))
        val xtream=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
        val m3uGroup=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE}
        fun field(parent:LinearLayout,label:String,hint:String,password:Boolean=false):EditText {
            parent.addView(PremiumDialogs.text(context,label,13f))
            return EditText(context).apply{
                this.hint=hint;setSingleLine(true);textSize=16f;setTextColor(Color.WHITE);setHintTextColor(0xffadbcd1.toInt())
                inputType=if(password)InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                typeface=android.graphics.Typeface.create("sans-serif",0);textDirection=View.TEXT_DIRECTION_LTR
                val icon=context.getDrawable(if(password)android.R.drawable.ic_lock_lock else android.R.drawable.ic_menu_edit)?.mutate()?.apply{setTint(PremiumDialogs.accent(context));setBounds(0,0,PremiumDialogs.dp(context,18),PremiumDialogs.dp(context,18))}
                setCompoundDrawablesRelative(icon,null,null,null);compoundDrawablePadding=PremiumDialogs.dp(context,12)
                background=PremiumDialogs.focus(context);setPadding(16,8,16,8);minimumHeight=PremiumDialogs.dp(context,48)
                importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                parent.addView(this,LinearLayout.LayoutParams(-1,PremiumDialogs.dp(context,48)))
            }
        }
        val name=field(root,"Playlist name","My playlist")
        root.addView(xtream);root.addView(m3uGroup)
        val server=field(xtream,"Server address","https://provider.example")
        val username=field(xtream,"Username","Your username")
        val password=field(xtream,"Password","Your password",true)
        val m3u=field(m3uGroup,"M3U playlist URL","https://provider.example/playlist.m3u")
        val saved=if(!testMode && panel.ownsProvider)panel.configuredLogin else repository.login()
        saved?.let{name.setText(it.name);server.setText(it.server);username.setText(it.username);password.setText(it.password);m3u.setText(it.m3uUrl)}
        var useM3u=false
        lateinit var xTab:Button;lateinit var mTab:Button
        fun select(m:Boolean){useM3u=m;xtream.visibility=if(m)View.GONE else View.VISIBLE;m3uGroup.visibility=if(m)View.VISIBLE else View.GONE
            xTab.text=if(m)"Xtream" else "✓  Xtream";mTab.text=if(m)"✓  M3U URL" else "M3U URL"
            xTab.isSelected=!m;mTab.isSelected=m
            xTab.setTextColor(if(m)Color.WHITE else 0xff090e17.toInt());mTab.setTextColor(if(m)0xff090e17.toInt() else Color.WHITE)
            xTab.background=PremiumDialogs.focus(context,!m);mTab.background=PremiumDialogs.focus(context,m)
        }
        xTab=PremiumDialogs.button(context,"✓  Xtream",true){select(false)}
        mTab=PremiumDialogs.button(context,"M3U URL"){select(true)}
        tabs.addView(xTab,LinearLayout.LayoutParams(0,PremiumDialogs.dp(context,56),1f).apply{marginEnd=8})
        tabs.addView(mTab,LinearLayout.LayoutParams(0,PremiumDialogs.dp(context,56),1f))
        select(saved?.m3uUrl?.isNotBlank()==true)
        val http=CheckBox(context).apply{isChecked=saved?.allowHttp==true;text="Allow insecure HTTP (credentials may be exposed)";textSize=12f;buttonTintList=android.content.res.ColorStateList.valueOf(PremiumDialogs.accent(context));root.addView(this)}
        val help=PremiumDialogs.text(context,"Use HTTPS where supported. Credentials stay encrypted on this device.",12f);root.addView(help)
        root.addView(PremiumDialogs.button(context,"Remove saved playlist",danger=true){confirmRemove()},LinearLayout.LayoutParams(-2,PremiumDialogs.dp(context,48)))
        val scroll=ScrollView(context).apply{isFillViewport=false;addView(root)}
        val dialog=PremiumDialogs.builder(context).setTitle("Connect playlist").setView(scroll).setNegativeButton("Cancel",null)
            .setPositiveButton("Connect",null).create()
        dialog.setOnShowListener{PremiumDialogs.style(dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(busy)return@setOnClickListener
            val login=ProviderLogin(name.text.toString().trim().ifBlank{runCatching{java.net.URI(if(useM3u)m3u.text.toString().trim() else server.text.toString().trim()).host}.getOrNull()?:"Playlist"},if(useM3u)"" else server.text.toString().trim(),if(useM3u)"" else username.text.toString().trim(),if(useM3u)"" else password.text.toString(),if(useM3u)m3u.text.toString().trim() else "",http.isChecked)
            if(panelSelected){playback.stop();panelSelected=false;generation++;detailGeneration++;snapshot=null}
            busy=true;help.text="Connecting…";dialog.getButton(-1).isEnabled=false
            val token=generation
            worker.execute{
                val result=runCatching{repository.connect(login)}
                main.post{
                    busy=false
                    if(!closed&&generation==token){
                        result.onSuccess{
                            snapshot=it;imageHttpAllowed=login.allowHttp;recents.clear();saveState()
                            password.text.clear();m3u.text.clear();dialog.dismiss();screen.home()
                        }
                        result.onFailure{help.text=it.message ?: "Connection failed.";dialog.getButton(-1).isEnabled=true}
                    }
                }
            }
        }}
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun confirmRemove(){
        PremiumDialogs.builder(context).setTitle("Remove this playlist?")
            .setMessage("This deletes saved credentials and private cached lists from this device. You will need to enter the login again.")
            .setNegativeButton("Cancel",null).setPositiveButton("Remove"){_,_->
                generation++;playback.stop();snapshot=null;favorites.clear();recents.clear();saveState()
                worker.execute{repository.clear()}
                screen.home()
            }.show()
    }
    fun requireConnection():Boolean {if(snapshot!=null)return true;loginDialog();return false}
    private val harak get()=screen.theme=="harak"
    private val accent get()=when(screen.theme){"harak"->Harak.GOLD;"red"->0xffe50914.toInt();"gold"->0xffd8b602.toInt();else->0xff518df4.toInt()}
    private fun reset(){
        channelOverlay=null
        fullscreenControls=null
        hiddenRemoteKeys.clear()
        fullscreenClick=null
        moveRows.clear()
        pictures.forEach{com.bumptech.glide.Glide.with(context).clear(it)};pictures.clear()
        screen.removeAllViews()
        stage=Stage(context).also{it.theme=screen.theme;screen.addView(it,FrameLayout.LayoutParams(-1,-1))}
        statusView=null
    }
    private fun text(s:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f):TextView{
        val v=TextView(context).apply{text=s;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;includeFontPadding=false;layoutDirection=View.LAYOUT_DIRECTION_LTR}
        if(harak){v.typeface=Harak.font(context);v.setTextColor(Harak.FG);v.gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL}
        stage!!.put(v,x,y,w,h,size);return v
    }
    private fun artwork(e:MediaEntry,x:Int,y:Int,w:Int,h:Int){
        if(!testMode && !repository.isVisible(e))return
        val uri=runCatching{ProviderRepository.validatedUrl(e.icon,imageHttpAllowed)}.getOrNull()?:return
        val image=ImageView(context).apply{scaleType=ImageView.ScaleType.FIT_CENTER;isFocusable=false}
        stage!!.put(image,x,y,w,h);pictures.add(image)
        com.bumptech.glide.Glide.with(context).asBitmap().load(uri.toASCIIString()).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).skipMemoryCache(true).override(w,h).timeout(15000).into(image)
    }
    private fun button(s:String,x:Int,y:Int,w:Int,h:Int,action:()->Unit):TextView=
        text(s,x,y,w,h).apply{
            id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            background=if(harak)Harak.box() else surface(0x44182438);if(harak)setPadding(18,0,18,0)
            setOnFocusChangeListener{_,f->background=if(harak)Harak.box(focus=f) else surface(if(f)accent else 0x44182438)}
            setOnClickListener{action()};contentDescription=s
        }
    private fun focus(v:View?){v?.requestFocus()}
    private fun header(title:String){
        BackButton.add(stage!!,accent){screen.back()}
        if(harak){Harak.chrome(stage!!,screen,title,when(mode){"live"->1;"movie"->2;else->3});return}
        text("universe-Alaa",120,30,550,75,42f).setTextColor(accent)
        text(title,800,30,1072,75,43f)
    }
    fun homeEntries()=snapshot?.entries.orEmpty().filter{testMode||repository.isVisible(it)}
    fun openSaved(saved:String) {
        if(!requireConnection())return
        mode="live";category=saved;offset=0;catOffset=0;screen.live()
    }
    fun openHomeEntry(e:MediaEntry) {
        if(!testMode&&!repository.isVisible(e))return
        selected=e
        recents.add(identity(e));saveState()
        if(e.kind=="series"){season=0;seasonOffset=0;episodeOffset=0;episodeList=emptyList();screen.seriesDetail()}
        else screen.movieDetail()
    }
    fun homeArtwork(e:MediaEntry,image:ImageView) {
        if(!testMode&&!repository.isVisible(e))return
        val uri=runCatching{ProviderRepository.validatedUrl(e.icon,imageHttpAllowed)}.getOrNull()?:return
        image.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(v:View){}
            override fun onViewDetachedFromWindow(v:View){
                com.bumptech.glide.Glide.with(context).clear(image)
                image.removeOnAttachStateChangeListener(this)
            }
        })
        com.bumptech.glide.Glide.with(context).asBitmap().load(uri.toASCIIString())
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).skipMemoryCache(true)
            .override(400,500).timeout(15000).into(image)
    }
    private fun kindCategories()=snapshot?.categories.orEmpty().filter{it.kind==mode}
    private fun entries():List<MediaEntry> {
        val favoriteEntries=if(category=="favorites")favorites else emptySet<String>()
        return ordered(snapshot?.entries.orEmpty().filter{it.kind==mode&&(testMode||repository.isVisible(it))&&when(category){"favorites"->identity(it) in favoriteEntries;"recent"->identity(it) in recents;""->true;else->it.categoryId==category}},false){it.id}
    }
    fun categories(){
        playback.stop();full=false;mode="live";category="";offset=0;catOffset=0
        browse("live",true)
    }
    private fun loadOpenedCategory(c:MediaCategory){
        if(testMode || c.id.isBlank() || c.id=="favorites" || c.id=="recent" || repository.login()?.m3uUrl?.isNotBlank()==true)return
        val token=generation
        val load=runCatching{repository.prepareCategoryLoad(c.kind,c.id)}.getOrNull()?:return
        worker.execute {
            val result=runCatching{load()}
            main.post {
                if(!closed && token==generation && category==c.id && mode==c.kind){
                    result.onSuccess{snapshot=it;browse(mode)}
                    result.onFailure{toast("Category unavailable. Try again.")}
                }
            }
        }
    }
    fun browse(kind:String,categoryOnly:Boolean=false){
        full=false;detailGeneration++
        if(mode!=kind){category="";offset=0;catOffset=0}
        mode=kind
        categoryOnlyView=categoryOnly
        if(kind!="live")playback.stop()
        reset();header(if(harak)when(kind){"live"->"البث المباشر";"movie"->"الأفلام";else->"المسلسلات"}else if(kind=="live")"Live TV" else if(kind=="movie")"Movies" else "Series")
        val cats=allCategories()
        val categoryPage=if(harak)8 else 10
        catOffset=catOffset.coerceIn(0,maxOf(0,cats.size-categoryPage))
        val catRows=cats.drop(catOffset).take(categoryPage).mapIndexed{i,c->
            button(if(harak)when(c.id){""->"الكل";"favorites"->"المفضلة";"recent"->"المشاهدة الأخيرة";else->c.name}else c.name,if(harak)348 else 30,if(harak)160+i*98 else 160+i*76,if(harak)360 else if(categoryOnly)900 else 430,if(harak)86 else 70){
                category=c.id;offset=0
                if(categoryOnly)screen.live() else browse(mode)
                loadOpenedCategory(c)
            }.also{v->CategoryIcons.decorate(v);bindMove(v,true,c.id);v.setOnKeyListener{_,key,event->
                if(event.action!=KeyEvent.ACTION_DOWN)false
                else if(key==20&&i==categoryPage-1&&catOffset+categoryPage<cats.size){catOffset++;nextCatFocus=categoryPage-1;browse(mode,categoryOnly);true}
                else if(key==19&&i==0&&catOffset>0){catOffset--;nextCatFocus=0;browse(mode,categoryOnly);true}
                else false
            }}
        }
        if(categoryOnly){
            stage!!.swipe={_,_,dx,dy->
                val forward=if(kotlin.math.abs(dy)>=kotlin.math.abs(dx))dy<0 else dx<0
                catOffset=(catOffset+(if(forward)categoryPage else -categoryPage)).coerceIn(0,maxOf(0,cats.size-categoryPage))
                browse(mode,true)
            }
            text(snapshot?.name.orEmpty(),1070,410,760,90,46f)
            text(if(moving!=null)"↑ ↓ Move   OK Save   BACK Cancel" else "Choose a category to watch",1070,520,800,65)
            text("Hold OK for category options",1070,620,760,65)
            focus(catRows.getOrNull(nextCatFocus?:0));nextCatFocus=null;return
        }
        currentList=entries();offset=offset.coerceIn(0,maxOf(0,currentList.size-1))
        val live=kind=="live";val count=if(live){if(harak)8 else 12} else 10
        val items=currentList.drop(offset).take(count)
        val cards=items.mapIndexed{i,e->
            val x=if(harak){if(live)738 else 738+(i%5)*231}else if(live)490 else 500+(i%5)*274
            val y=if(harak){if(live)160+i*100 else 160+(i/5)*344}else if(live)160+i*66 else 160+(i/5)*390
            val w=if(harak){if(live)520 else 205}else if(live)470 else 253;val h=if(harak){if(live)88 else 310}else if(live)60 else 370
            button(if(live)e.name else "",x,y,w,h){
                selected=e;recents.add(identity(e));saveState()
                when(e.kind){
                    "live"->{if(playback.entry?.id==e.id)fullscreen() else {playChecked(e);nextCardFocus=i;browse("live")}}
                    "series"->{season=0;seasonOffset=0;episodeOffset=0;episodeList=emptyList();screen.seriesDetail()}
                    else->screen.movieDetail()
                }
            }.also{v->
                if(live)bindMove(v,false,e.id)
                if(!live){
                    text(e.name.take(1),x+14,y+28,w-28,if(harak)220 else 265,60f).apply{gravity=Gravity.CENTER;setTextColor(accent)}
                    artwork(e,x+5,y+5,w-10,if(harak)240 else 295)
                    text(e.name,x+12,y+if(harak)246 else 310,w-24,52,if(harak)18f else 24f).apply{maxLines=2;gravity=Gravity.CENTER}
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
        stage!!.swipe={x,_,dx,dy->
            val forward=if(kotlin.math.abs(dy)>=kotlin.math.abs(dx))dy<0 else dx<0
            val step=if(forward)1 else -1
            if(x<(if(harak)730 else if(categoryOnly)950 else 480)){
                catOffset=(catOffset+step*categoryPage).coerceIn(0,maxOf(0,cats.size-categoryPage))
            }else offset=(offset+step*count).coerceIn(0,maxOf(0,currentList.size-count))
            browse(mode,categoryOnly)
        }
        if(items.isEmpty())text("No items in this category",520,380,1000,100,37f)
        if(live){
            val target=PlayerView(context).apply{useController=false;isFocusable=false}
            stage!!.put(target,if(harak)1288 else 990,160,if(harak)588 else 882,if(harak)331 else 496);playback.attach(target)
            val title=selected?.takeIf{it.kind=="live"}?.name?:"Select a channel"
            text(title,if(harak)1288 else 990,if(harak)515 else 680,if(harak)588 else 882,90,35f)
            val open=button("Fullscreen",if(harak)1288 else 990,if(harak)620 else 800,if(harak)260 else 360,72){if(playback.entry!=null)fullscreen()}
            val tracks=button("Audio / Subtitles",if(harak)1566 else 1375,if(harak)620 else 800,if(harak)310 else 480,72){playback.trackMenu()}
            val fav=button("Favorite",if(harak)1288 else 990,if(harak)718 else 890,if(harak)588 else 360,64){selected?.let{if(!favorites.add(identity(it)))favorites.remove(identity(it));saveState();toast("Favorites updated")}}
            tracks.nextFocusLeftId=open.id;open.nextFocusRightId=tracks.id
            cards.forEach{it.nextFocusRightId=open.id};open.nextFocusLeftId=cards.firstOrNull()?.id?:open.id
            statusView=text(if(playback.player==null)"Select a channel to start playback" else "",if(harak)1288 else 990,if(harak)820 else 968,if(harak)588 else 870,50,24f)
            playback.zap={delta->zap(delta)}
            open.nextFocusDownId=fav.id
        }
        text(if(moving!=null)"↑ ↓ Move   OK Save   BACK Cancel" else "${if(currentList.isEmpty())0 else offset+1}–${offset+items.size} / ${currentList.size}   Hold OK for options   BACK Home",if(harak)738 else 490,1015,if(harak)1138 else 1380,45,24f)
        focus(if(nextCatFocus!=null)catRows.getOrNull(nextCatFocus!!) else cards.getOrNull((nextCardFocus?:0).coerceAtMost(cards.lastIndex.coerceAtLeast(0)))?:catRows.firstOrNull());nextCatFocus=null;nextCardFocus=null
    }
    private fun zap(delta:Int, keepControlsHidden:Boolean=false){
        val list=(if(currentList.isEmpty())snapshot?.entries.orEmpty() else currentList).filter{it.kind=="live" && repository.isVisible(it)}
        if(list.isEmpty())return
        val at=list.indexOfFirst{it.id==playback.entry?.id}
        selected=list[Math.floorMod(at+delta,list.size)]
        selected?.let{playChecked(it);recents.add(identity(it));saveState()}
        if(full){
            fullscreen()
            if(keepControlsHidden){fullscreenControls?.conceal();selected?.let{PremiumDialogs.toast(context,it.name)}}
        } else browse("live")
    }
    fun detail(series:Boolean){
        val detailToken=++detailGeneration
        val e=selected?:run{browse(if(series)"series" else "movie");return}
        playback.stop();reset();header(e.name)
        text(e.name,if(harak)348 else 80,if(harak)182 else 200,if(harak)246 else 540,if(harak)368 else 630,if(harak)32f else 55f).apply{maxLines=7;gravity=Gravity.CENTER;background=surface(0x55233149)}
        artwork(e,if(harak)350 else 85,if(harak)184 else 205,if(harak)242 else 530,if(harak)364 else 620)
        if(!series){
            if(harak)Harak.label(stage!!,e.name,650,182,1170,90,48f,true)
            text(e.plot.ifBlank{"Movie from your connected playlist"},if(harak)650 else 720,if(harak)310 else 220,1100,if(harak)280 else 400,if(harak)26f else 33f).apply{maxLines=8;gravity=Gravity.TOP or Gravity.LEFT}
            val play=button(if(harak)"شاهد الآن" else "Play Movie",if(harak)650 else 720,if(harak)670 else 800,480,90){playChecked(e);fullscreen()}
            if(harak)play.background=Harak.gold()
            val fav=button(if(harak){if(identity(e) in favorites)"إزالة من المفضلة" else "إضافة إلى المفضلة"}else if(identity(e) in favorites)"Remove Favorite" else "Add Favorite",if(harak)1160 else 1230,if(harak)670 else 800,570,90){if(!favorites.add(identity(e)))favorites.remove(identity(e));saveState();detail(false)}
            play.nextFocusRightId=fav.id;fav.nextFocusLeftId=play.id;focus(play)
        } else {
            text("Loading seasons and episodes…",720,220,1100,100,36f)
            val id=e.id
            val selectedRepository=repository
            val load=runCatching{selectedRepository.prepareEpisodes(e)}.getOrNull()?:return
            worker.execute{
                if(closed || detailToken!=detailGeneration)return@execute
                val cached=runCatching{selectedRepository.cachedEpisodes(e)}.getOrDefault(emptyList())
                if(cached.isNotEmpty())main.post{if(!closed&&detailToken==detailGeneration&&selected?.id==id&&screen.page=="series-detail"){episodeList=cached;if(!full)renderEpisodes()}}
                val result=runCatching{load()}
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
        if(harak){
            val entry=selected
            Harak.label(stage!!,entry?.name.orEmpty(),624,172,1200,80,48f,true)
            Harak.label(stage!!,entry?.plot?.ifBlank{"اختر الموسم والحلقة لبدء المشاهدة"}.orEmpty(),624,270,1200,85,24f,false,Harak.MUTED).maxLines=2
            entry?.let{artwork(it,348,182,246,368)}
        }
        val seasons=episodeList.map{it.season}.distinct().sorted()
        if(season !in seasons)season=seasons.firstOrNull()?:0
        if(seasons.isEmpty()){text("No episodes returned by this provider",100,320,1700,100,38f);return}
        val seasonPage=if(harak)5 else 10
        val episodePage=if(harak)6 else 10
        seasonOffset=seasonOffset.coerceIn(0,maxOf(0,seasons.size-seasonPage))
        val rows=seasons.drop(seasonOffset).take(seasonPage).mapIndexed{i,n->
            button(if(harak)"الموسم $n" else "Season $n",if(harak)348+i*304 else 80,if(harak)568 else 180+i*76,if(harak)286 else 470,70){season=n;episodeOffset=0;renderEpisodes()}.also{v->
                v.setOnKeyListener{_,key,event->if(event.action!=0)false
                else if(key==(if(harak)22 else 20)&&i==seasonPage-1&&seasonOffset+seasonPage<seasons.size){seasonOffset++;nextSeasonFocus=seasonPage-1;renderEpisodes();true}
                else if(key==(if(harak)21 else 19)&&i==0&&seasonOffset>0){seasonOffset--;nextSeasonFocus=0;renderEpisodes();true}else false}
            }
        }
        val all=episodeList.filter{it.season==season}
        episodeOffset=episodeOffset.coerceIn(0,maxOf(0,all.size-1))
        val eps=all.drop(episodeOffset).take(episodePage).mapIndexed{i,e->
            button(e.name,if(harak)348+(i%2)*778 else 620,if(harak)656+(i/2)*112 else 180+i*76,if(harak)750 else 1210,if(harak)100 else 70){recents.add(identity(e));saveState();playChecked(e);fullscreen()}.also{v->
                v.setOnKeyListener{_,key,event->if(event.action!=0)false
                else if(key==20&&i>=episodePage-(if(harak)2 else 1)&&episodeOffset+episodePage<all.size){episodeOffset+=episodePage;nextEpisodeFocus=0;renderEpisodes();true}
                else if(key==19&&i<(if(harak)2 else 1)&&episodeOffset>0){episodeOffset=maxOf(0,episodeOffset-episodePage);nextEpisodeFocus=episodePage-1;renderEpisodes();true}else false}
            }
        }
        stage!!.swipe={x,y,dx,dy->
            val step=if(if(kotlin.math.abs(dy)>=kotlin.math.abs(dx))dy<0 else dx<0)1 else -1
            if(if(harak)y<650 else x<600)seasonOffset=(seasonOffset+step*seasonPage).coerceIn(0,maxOf(0,seasons.size-seasonPage))
            else episodeOffset=(episodeOffset+step*episodePage).coerceIn(0,maxOf(0,all.size-episodePage))
            renderEpisodes()
        }
        if(harak){
            rows.forEachIndexed{i,v->
                v.nextFocusLeftId=rows.getOrNull(i-1)?.id?:v.id;v.nextFocusRightId=rows.getOrNull(i+1)?.id?:v.id
                v.nextFocusDownId=eps.firstOrNull()?.id?:v.id
                v.nextFocusUpId=stage!!.findViewWithTag<View>("universe-back").id
            }
            eps.forEachIndexed{i,v->
                v.nextFocusLeftId=if(i%2==1)eps[i-1].id else rows.firstOrNull()?.id?:v.id
                v.nextFocusRightId=if(i%2==0)eps.getOrNull(i+1)?.id?:v.id else v.id
                v.nextFocusUpId=eps.getOrNull(i-2)?.id?:rows.firstOrNull()?.id?:v.id
                v.nextFocusDownId=eps.getOrNull(i+2)?.id?:v.id
            }
        }else{
            rows.forEach{it.nextFocusRightId=eps.firstOrNull()?.id?:it.id}
            eps.forEach{it.nextFocusLeftId=rows.firstOrNull()?.id?:it.id}
        }
        text("${all.size} episodes   ·   ↑ ↓ More episodes",620,985,1200,60,25f)
        focus(if(nextSeasonFocus!=null)rows.getOrNull(nextSeasonFocus!!) else eps.getOrNull((nextEpisodeFocus?:0).coerceAtMost(eps.lastIndex.coerceAtLeast(0)))?:rows.firstOrNull());nextSeasonFocus=null;nextEpisodeFocus=null
    }
    private fun fullscreen(){
        val item=playback.entry?:return
        if(playback.player==null)return
        full=true;reset()
        val target=PlayerView(context).apply{useController=false;isFocusable=false}
        stage!!.put(target,0,0,1920,1080);playback.attach(target)
        val parent=selected?.takeIf{it.kind=="series" && item.kind=="episode"}
        val categoryName=snapshot?.categories?.firstOrNull{it.kind==item.kind && it.id==item.categoryId}?.name.orEmpty()
        val episodeIndex=episodeList.filter{it.season==item.season}.indexOfFirst{it.id==item.id}
        val subtitle=when(item.kind){
            "live"->categoryName.ifBlank{"Live TV"}
            "episode"->listOfNotNull(parent?.name, "S${item.season}"+if(episodeIndex>=0)"E${episodeIndex+1}" else "", "Episode").joinToString(" · ")
            else->categoryName.ifBlank{"Movie"}
        }
        val favoriteItem=parent?:item
        val controls=FullscreenControls(context,playback,item,accent,subtitle,identity(favoriteItem) in favorites,
            leave={screen.back()},previous={skipFullscreen(-1)},next={skipFullscreen(1)},
            toggleFavorite={
                if(!favorites.add(identity(favoriteItem)))favorites.remove(identity(favoriteItem))
                saveState();identity(favoriteItem) in favorites
            },artwork={image->
                val art=item.icon.ifBlank{parent?.icon.orEmpty()}
                val uri=runCatching{ProviderRepository.validatedUrl(art,imageHttpAllowed)}.getOrNull()
                if(uri!=null && (testMode || repository.isVisible(item))){
                    pictures.add(image)
                    com.bumptech.glide.Glide.with(context).asBitmap().load(uri.toASCIIString())
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).skipMemoryCache(true)
                        .override(96,96).timeout(15000).into(image)
                }
            })
        if(PhoneInput.enabled(context)){
            // Video receives taps even when the overlay has auto-hidden.
            stage!!.setOnTouchListener{_,event->
                if(event.actionMasked==MotionEvent.ACTION_UP){controls.reveal();true}else true
            }
            target.setOnTouchListener{_,event->
                if(event.actionMasked==MotionEvent.ACTION_UP)controls.reveal()
                true
            }
        }
        controls.openChannels={openChannelList()}
        fullscreenControls=controls
        stage!!.put(controls,0,0,1920,1080)
        statusView=TextView(context).apply{text="";setTextColor(Color.WHITE);gravity=Gravity.CENTER}
        controls.stage.put(statusView!!,400,740,1120,60,27f)
        controls.initialFocus()
    }
    private fun openChannelList(){
        if(playback.entry?.kind!="live"||channelOverlay!=null)return
        val channels=(if(currentList.isEmpty())snapshot?.entries.orEmpty() else currentList)
            .filter{it.kind=="live"&&(testMode||repository.isVisible(it))}
        if(channels.isEmpty())return
        fullscreenControls?.conceal()
        val overlay=ChannelOverlay(context,channels,playback.entry?.id.orEmpty(),accent,{entry->
            closeChannelList();selected=entry;playChecked(entry);recents.add(identity(entry));saveState()
            fullscreen();fullscreenControls?.conceal()
        },{closeChannelList()})
        channelOverlay=overlay
        stage!!.put(overlay,0,0,1920,1080)
    }
    private fun closeChannelList(){
        channelOverlay?.let{stage?.removeView(it)};channelOverlay=null
        stage?.apply{isFocusable=true;isFocusableInTouchMode=true;requestFocus()}
    }
    private fun skipFullscreen(delta:Int){
        val item=playback.entry?:return
        if(item.kind=="live"){zap(delta);return}
        if(item.kind=="episode"){
            val at=episodeList.indexOfFirst{it.id==item.id}
            val next=episodeList.getOrNull(at+delta)
            if(next!=null){playChecked(next);recents.add(identity(next));saveState();fullscreen()}
        }else{
            if(delta<0)playback.player?.seekTo(0) else playback.player?.let{if(it.duration>0)it.seekTo(it.duration)}
        }
    }
    private val hiddenRemoteKeys=mutableSetOf<Int>()
    private var fullscreenClick:View?=null
    private var channelOverlay:ChannelOverlay?=null
    /** Activity-level routing works even when no descendant currently owns focus. */
    fun fullscreenKey(event:KeyEvent):Boolean {
        if(!full)return false
        val key=event.keyCode
        if(event.action==KeyEvent.ACTION_UP&&hiddenRemoteKeys.remove(key))return true
        channelOverlay?.let{return it.key(event)}
        val ok=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER
        if(event.action==KeyEvent.ACTION_UP){
            if(hiddenRemoteKeys.remove(key))return true
            if(ok && fullscreenClick!=null){
                val click=fullscreenClick;fullscreenClick=null
                if(!event.isCanceled && click?.hasFocus()==true)click.performClick()
                return true
            }
            return false
        }
        if(event.action!=KeyEvent.ACTION_DOWN)return false
        val dpad=key in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER)
        if(!dpad)return false
        if(panelSelected && !panel.tick())return true
        val controls=fullscreenControls?:return false
        if(controls.visibility!=View.VISIBLE){
            hiddenRemoteKeys.add(key)
            if(controls.live && (key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN))
                zap(if(key==KeyEvent.KEYCODE_DPAD_UP)-1 else 1,keepControlsHidden=true)
            else if(controls.live&&ok)openChannelList()
            else controls.reveal()
            hiddenRemoteKeys.add(key)
            return true
        }
        // A wake-up OK must not click Pause on release, including held-key repeats.
        if(key in hiddenRemoteKeys)return true
        controls.reveal() // Also repairs visible-but-unfocused controls.
        if(ok){
            if(event.repeatCount==0)fullscreenClick=controls.findFocus()
            return true
        }
        return false
    }
    fun key(event:KeyEvent):Boolean {
        if(fullscreenKey(event))return true
        if(panelSelected && !panel.tick())return true
        val key=event.keyCode
        val ok=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER
        if(ok){
            if(event.action==KeyEvent.ACTION_UP && heldOk==key){
                val click=pendingClick
                val save=pendingSave && !event.isCanceled
                val activate=!longStarted && moving==null && !event.isCanceled && click?.hasFocus()==true
                clearMoveInput()
                if(save)finishMove(true)
                if(activate)click?.performClick()
                return true
            }
            if(event.action==KeyEvent.ACTION_DOWN){
                if(heldOk!=null){
                    if(!longStarted && event.isLongPress){main.removeCallbacks(longOk);longOk.run()}
                    return true
                }
                if(moving!=null){
                    heldOk=key;longStarted=true
                    pendingSave=event.repeatCount==0
                    return true
                }
                val view=screen.findFocus()
                val target=moveRows[view]
                if(target!=null){
                    // A repeat without the initial down must not become a fresh click.
                    heldOk=key;longStarted=event.repeatCount>0
                    if(event.repeatCount==0){pendingMove=target;pendingClick=view;main.postDelayed(longOk,ViewConfiguration.getLongPressTimeout().toLong())}
                    return true
                }
            }
        }
        if(moving!=null){
            if(key==KeyEvent.KEYCODE_BACK){
                if(event.action==KeyEvent.ACTION_UP && !event.isCanceled)finishMove(false)
                return true
            }
            if(event.action==KeyEvent.ACTION_DOWN && (key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){
                moving?.move(if(key==KeyEvent.KEYCODE_DPAD_DOWN)1 else -1,moveIds(movingCategories));renderMove()
            }
            return true
        }
        if(full && event.action==KeyEvent.ACTION_DOWN){
            val dpad=key in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER)
            if(dpad)return false
            val handled=playback.key(event)
            if(handled){fullscreenControls?.show();fullscreenControls?.update()}
            return handled
        }
        return false
    }
    fun back():Boolean{
        if(channelOverlay!=null){closeChannelList();return true}
        if(moving!=null){clearMoveInput();finishMove(false);return true}
        if(full){full=false;if(mode=="live")browse(mode) else {playback.stop();if(mode=="series")renderEpisodes() else detail(false)};return true}
        return false
    }
    private fun playChecked(entry:MediaEntry) {
        if(panelSelected && !panel.tick())return
        playback.play(entry)
    }
    fun stop(){moving=null;clearMoveInput();foreground=false;main.removeCallbacks(panelTick);main.removeCallbacks(panelPoll);detailGeneration++;playback.stop();if(full){full=false;screen.home()}}
    fun close(){moving=null;clearMoveInput();closed=true;if(!testMode)panel.close();panelNetwork.shutdownNow();panelLoads.shutdownNow();main.removeCallbacks(panelTick);main.removeCallbacks(panelPoll);generation++;playback.stop();pictures.forEach{com.bumptech.glide.Glide.with(context).clear(it)};pictures.clear();worker.shutdownNow()}
}
