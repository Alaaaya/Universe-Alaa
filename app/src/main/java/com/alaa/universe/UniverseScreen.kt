package com.alaa.universe

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.*

class UniverseScreen(context:Context): FrameLayout(context) {
    var theme="red"
    var homeTheme=context.getSharedPreferences("appearance",0).getString("home","classic")!!
    var arabic=false
    var fixedClock:String?=null
    var page="home";private set
    var drawer=false;private set
    var fullscreen=false;private set
    var selectedCategory=0;private set
    var selectedChannel=0;private set
    private var homeIndex=0
    private var channelOffset=0
    private var channelFocus=0
    private var categoryFocus=0
    private var moving=false
    private val favorites=mutableSetOf<Int>()
    private val recents=mutableSetOf<Int>()
    private val order=(0..15).toMutableList()
    private val groups=mutableListOf("All Channels","Favorites","Recently Viewed","AR | Alaa","AR | Cinema","AR | Sports","AR | Kids","DE | Germany","World","Documentaries")
    private val names=listOf("ALAA - ONE","ALAA - CINEMA","ALAA - WORLD","ALAA - TWO","ALAA - KIDS","ALAA - FIVE","ALAA - MAX","ALAA - CULTURE","ALAA - DRAMA","ALAA - ACTION","ALAA - MUSIC","ALAA - TRAVEL","ALAA - NATURE","ALAA - CLASSIC","ALAA - NEWS","ALAA - FAMILY")
    private lateinit var stage:Stage
    private val accent get()=when(theme){"red"->0xffe50914.toInt();"gold"->0xffd8b602.toInt();else->0xff518df4.toInt()}
    private val textColor get()=if(theme=="red")Color.WHITE else 0xffe2eaf6.toInt()
    init {layoutDirection=View.LAYOUT_DIRECTION_LTR}
    private fun reset(wave:Boolean=false) {
        removeAllViews();stage=Stage(context).apply{theme=this@UniverseScreen.theme;waves=wave}
        addView(stage,LayoutParams(-1,-1))
    }
    private fun text(value:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f,color:Int=textColor,center:Boolean=false):TextView {
        val v=TextView(context).apply{
            text=value;setTextColor(if(theme=="red" && !(page=="home"&&homeTheme=="prayer") && color!=accent)Color.WHITE else color);gravity=if(center)Gravity.CENTER else Gravity.CENTER_VERTICAL
            typeface=Typeface.create("sans-serif",Typeface.NORMAL);includeFontPadding=false
            maxLines=1;ellipsize=TextUtils.TruncateAt.END;layoutDirection=View.LAYOUT_DIRECTION_LTR
            textDirection=View.TEXT_DIRECTION_FIRST_STRONG
        }
        stage.put(v,x,y,w,h,size);return v
    }
    private fun button(value:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f,action:()->Unit):TextView =
        text(value,x,y,w,h,size).apply{
            id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            background=surface(if(theme=="red")Color.BLACK else 0x22020c1c)
            setOnFocusChangeListener{_,f->background=surface(if(f)accent else if(theme=="red")Color.BLACK else 0x22020c1c)}
            setOnClickListener{action()}
        }
    private fun focus(v:View) {v.requestFocus();v.post{if(findFocus()==null&&v.isAttachedToWindow)v.requestFocus()}}
    private fun chain(rows:List<View>) {rows.forEachIndexed{i,v->
        v.nextFocusUpId=rows[maxOf(0,i-1)].id;v.nextFocusDownId=rows[minOf(rows.lastIndex,i+1)].id
        v.nextFocusLeftId=v.id;v.nextFocusRightId=v.id
    }}
    private fun clock()=fixedClock?:SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
    private fun mark(x:Int,y:Int,size:Int) {stage.put(AlaaMark(context,if(theme=="gold"||theme=="red")accent else 0xffb8d9ff.toInt()),x,y,size,size)}
    private fun icon(res:Int,x:Int,y:Int,w:Int,h:Int) {stage.put(ImageView(context).apply{setImageResource(res);setColorFilter(if(theme=="gold"||theme=="red")accent else 0xffb8d9ff.toInt());scaleType=ImageView.ScaleType.FIT_CENTER},x,y,w,h)}
    private fun ids():List<Int> = order.filter{when(selectedCategory){1->it in favorites;2->it in recents;0->true;else->it%7==selectedCategory-3}}
    fun home() {
        page="home";drawer=false;fullscreen=false;reset()
        if(homeTheme=="prayer"){prayerHome();return}
        text("Alaa",54,43,250,38,25f,0xff9aaec8.toInt())
        text(clock(),338,43,180,38,25f,0xff9aaec8.toInt())
        val date=if(fixedClock!=null)"28 October 2026" else SimpleDateFormat("dd MMMM yyyy",Locale.getDefault()).format(Date())
        text(date,54,85,270,34,22f,0xff8096b4.toInt())
        text("—",338,85,160,34,22f,0xff8096b4.toInt())
        text(if(arabic)"الأيام المتبقية  —" else "(Days Left: —)",54,137,440,35,22f,0xff8096b4.toInt())
        stage.put(View(context).apply{setBackgroundColor(0x40738ba8)},314,47,1,72)
        text(if(arabic)"الموقع غير محدد" else "Location unavailable",1480,43,385,40,25f,0xff9aaec8.toInt()).gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
        text("—°",1570,93,295,36,25f,0xff9aaec8.toInt()).gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
        text(if(arabic)"الطقس غير متصل" else "Weather unavailable",1480,135,385,35,21f,0xff8096b4.toInt()).gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
        mark(738,315,90)
        text("universe-Alaa",835,326,490,72,52f)
        val labels=if(arabic)listOf("القنوات","الأفلام","المسلسلات","قائمة التشغيل","الإعدادات","تحديث قائمة التشغيل")
                   else listOf("Live TV","Movies","Series","Change Server","Settings","Refresh Playlist")
        val icons=listOf(R.drawable.ic_live_tv,R.drawable.ic_movie,R.drawable.ic_video_library,R.drawable.ic_subscriptions,R.drawable.ic_settings,R.drawable.ic_refresh)
        val cards=labels.mapIndexed{i,s->
            val x=50+i*310
            icon(icons[i],x+65,650,140,140)
            button(s,x,827,270,70,29f){
                homeIndex=i
                if(i==0) categories() else if(i==1) catalogue(false) else if(i==2) catalogue(true) else if(i==4) themeDialog() else Toast.makeText(context,if(arabic)"غير متصل" else "Not connected",Toast.LENGTH_SHORT).show()
            }.apply{contentDescription=s;gravity=Gravity.CENTER}
        }
        cards.forEachIndexed{i,v->v.nextFocusUpId=v.id;v.nextFocusDownId=v.id;v.nextFocusLeftId=cards[maxOf(0,i-1)].id;v.nextFocusRightId=cards[minOf(5,i+1)].id}
        focus(cards[homeIndex])
    }

    private var prayerTickCount=0
    private val prayerTick=object:Runnable{
        override fun run(){if(homeTheme=="prayer"&&page=="home")home();if(++prayerTickCount%15==0)PrayerTimes.refresh(context){};postDelayed(this,60000)}
    }
    override fun onAttachedToWindow(){
        super.onAttachedToWindow();postDelayed(prayerTick,60000)
        PrayerTimes.refresh(context){post{if(isAttachedToWindow&&homeTheme=="prayer"&&page=="home")home()}}
    }
    override fun onDetachedFromWindow(){removeCallbacks(prayerTick);super.onDetachedFromWindow()}
    private fun glass(color:Int,stroke:Int,radius:Float=24f)=surface(color,stroke).apply{cornerRadius=radius}
    private fun prayerHome(){
        stage.put(PrayerBackdrop(context),0,0,1920,1080)
        stage.put(GlassContour(context,0xff329fff.toInt(),35f),9,7,1902,206)
        stage.put(View(context).apply{background=glass(0xd00b1429.toInt(),0xff59bfff.toInt(),35f)},24,22,1872,176)
        text("AP",49,52,100,105,48f,Color.WHITE,true).apply{background=glass(0x99465478.toInt(),0x6673a6c6);typeface=Typeface.DEFAULT_BOLD}
        text("Alaa",174,46,340,39,29f,Color.WHITE)
        val df=SimpleDateFormat("dd MMMM yyyy",Locale.ENGLISH).apply{timeZone=TimeZone.getTimeZone("Europe/Berlin")}
        text(df.format(Date()),174,91,350,30,22f,Color.WHITE)
        stage.put(View(context).apply{background=surface(0x8887cfff.toInt())},532,54,2,100)
        text("(Days Left: —)",174,135,340,30,21f,Color.WHITE)
        text(fixedClock?:SimpleDateFormat("HH:mm",Locale.US).apply{timeZone=TimeZone.getTimeZone("Europe/Berlin")}.format(Date()),560,58,250,75,56f,Color.WHITE,true).typeface=Typeface.DEFAULT_BOLD
        text("—",560,133,250,28,25f,Color.WHITE,true)
        stage.put(MosqueArt(context),797,27,238,158)
        stage.put(View(context).apply{background=glass(0x440b1931,0xaa58c6ee.toInt(),20f)},1029,26,850,161)
        text("أوقات الصلاة",1045,27,800,28,24f,Color.WHITE,true)
        text("Mülheim - مسجد الرحمة",1045,56,800,25,20f,Color.WHITE,true)
        val state=PrayerTimes.snapshot(context)
        val names=listOf("الفجر","الظهر","العصر","المغرب","العشاء")
        for(i in 0..4){
            val x=1045+i*166
            if(state?.next==i)stage.put(GlassContour(context,0xff23ddff.toInt(),23f),x-13,69,182,125)
            stage.put(View(context).apply{background=glass(if(state?.next==i)0xb00d6995.toInt() else 0x33090f24,if(state?.next==i)0xff67ecff.toInt() else 0x226a87a7,23f)},x,82,156,99)
            stage.put(PrayerSymbol(context,i),x+49,84,58,33)
            text(names[i],x,115,156,28,20f,Color.WHITE,true)
            text(state?.values?.get(i)?:"--:--",x,146,156,30,26f,Color.WHITE,true)
        }
        if(state==null||state.stale)text(if(state==null)"Prayer times unavailable" else "Offline · last saved times",1110,202,700,30,20f,Color.WHITE,true)
        stage.put(AlaaMark(context,0xffd96af6.toInt()),633,326,115,115)
        val logo=android.text.SpannableString("universe-Alaa")
        logo.setSpan(android.text.style.ForegroundColorSpan(0xff37c5ff.toInt()),9,logo.length,0)
        text("",770,341,620,84,58f,Color.WHITE).apply{text=logo;typeface=Typeface.DEFAULT_BOLD;setShadowLayer(12f,0f,3f,Color.BLACK)}
        val colors=listOf(0xff169bff.toInt(),0xffff832a.toInt(),0xffb34ef0.toInt(),0xff22d5dc.toInt(),0xffee65bf.toInt(),0xff46dd93.toInt())
        val labels=listOf("Live TV","Movies","Series","Change Server","Settings","Refresh Playlist")
        val icons=listOf(R.drawable.ic_live_tv,R.drawable.ic_movie,R.drawable.ic_video_library,R.drawable.ic_server,R.drawable.ic_settings,R.drawable.ic_refresh)
        val cards=labels.mapIndexed{i,label->
            val x=44+i*309;val color=colors[i]
            val rim=NeonRim(context,color);stage.put(rim,x-17,543,321,330)
            val v=button("",x,560,287,296){
                homeIndex=i;when(i){0->categories();1->catalogue(false);2->catalogue(true);4->themeDialog();else->Toast.makeText(context,"Not connected",Toast.LENGTH_SHORT).show()}
            }
            v.contentDescription=label
            fun paint(f:Boolean){
                val bright=if(f)0xff007aff.toInt() else color
                val dark=android.graphics.Color.rgb((Color.red(bright)*.26f).toInt(),(Color.green(bright)*.26f).toInt(),(Color.blue(bright)*.26f).toInt())
                v.background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,intArrayOf(bright,dark)).apply{cornerRadius=26f;setStroke(if(f)5 else 3,if(f)0xff70e5ff.toInt() else color)}
            }
            paint(false);v.setOnFocusChangeListener{_,f->paint(f);rim.active=f;rim.invalidate()}
            stage.put(ImageView(context).apply{setImageResource(icons[i]);setColorFilter(Color.WHITE);alpha=.95f},x+82,604,123,123)
            text(label,x+7,762,273,62,29f,Color.WHITE,true).typeface=Typeface.DEFAULT_BOLD
            stage.put(GlassContour(context,color,5f),x+68,821,150,43)
            stage.put(View(context).apply{background=glass(color,color,5f)},x+88,840,110,7)

            v
        }
        cards.forEachIndexed{i,v->v.nextFocusLeftId=cards[maxOf(0,i-1)].id;v.nextFocusRightId=cards[minOf(5,i+1)].id;v.nextFocusUpId=v.id;v.nextFocusDownId=v.id}
        stage.put(GlassContour(context,0xff53bcff.toInt(),32f),40,980,510,94)
        text("Playlist: "+context.getSharedPreferences("playlist",0).getString("name","Not connected"),105,996,430,64,26f,Color.WHITE,true).background=glass(0xd0091429.toInt(),0xff73ceff.toInt(),32f)
        for(j in 0..2){
            stage.put(View(context).apply{background=surface(Color.WHITE)},69,1011+j*13,5,3)
            stage.put(View(context).apply{background=surface(Color.WHITE)},80,1011+j*13,17,3)
        }
        focus(cards[homeIndex])
    }
    private fun themeDialog() {
        page="themes";reset()
        text(if(arabic)"المظهر" else "Theme",100,80,1300,100,56f)
        val labels=listOf("Red","Gold","Blue","Royal Blue","Sunset Prayer")
        val rows=labels.mapIndexed{i,label->button(label,100,250+i*125,900,100,40f){
            if(i==4)homeTheme="prayer" else {homeTheme="classic";theme=listOf("red","gold","blue","royal")[i]}
            context.getSharedPreferences("appearance",0).edit().putString("home",homeTheme).apply();home()
        }}
        chain(rows);focus(rows[listOf("red","gold","blue","royal").indexOf(theme).coerceAtLeast(0)])

    }
    fun categories() {
        page="categories";drawer=true;fullscreen=false;reset(true)
        text(if(arabic)"القنوات" else "Live TV",52,49,800,105,65f)
        icon(R.drawable.ic_search,1450,64,55,55);mark(1560,38,86)
        text("universe-Alaa",1650,60,240,62,30f)
        val rows=groups.mapIndexed{i,s->
            val count=when(i){0->order.size;1->favorites.size;2->recents.size;else->order.count{it%7==i-3}}
            val label=if(arabic&&i<3)listOf("كل القنوات","المفضلة","شاهدت مؤخراً")[i] else s
            button("",0,231+i*74,942,69,30f){selectedCategory=i;categoryFocus=i;channelOffset=0;channelFocus=0;live()}.apply{
                contentDescription=label
                setOnLongClickListener{moving=!moving;true}
                setOnKeyListener{_,code,event->
                    if(moving&&event.action==KeyEvent.ACTION_DOWN&&(code==19||code==20)&&i>2){
                        val to=(i+if(code==20)1 else -1).coerceIn(3,groups.lastIndex)
                        Collections.swap(groups,i,to);categoryFocus=to;categories();true
                    }else false
                }
            }.also {
                text((i+1).toString(),28,231+i*74,55,69,28f)
                text(label,109,231+i*74,635,69,30f)
                text(count.toString(),758,231+i*74,100,69,28f).gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
                text("›",884,231+i*74,45,69,44f,trueColor(),true)
            }
        }
        chain(rows)
        text(clock(),1150,421,650,150,122f,textColor,true).typeface=Typeface.create("sans-serif",Typeface.BOLD)
        text(clock(),1240,580,470,60,43f,textColor,true)
        text(if(arabic)"ⓘ اضغط مطوّلاً OK لتحريك الأقسام" else "ⓘ Long press the \"OK\" button to move Categories",1020,903,835,70,27f)
        focus(rows[categoryFocus.coerceIn(rows.indices)])
    }
    private fun trueColor()=textColor
    fun live(openDrawer:Boolean=false) {
        if(openDrawer){categories();return}
        page="live";drawer=false;fullscreen=false;reset(true)
        button("‹",40,55,80,80,65f){categories()}.gravity=Gravity.CENTER
        text(groups[selectedCategory],145,55,1120,90,46f)
        icon(R.drawable.ic_search,1520,72,50,50)
        text(if(arabic)"القنوات" else "Channels",1600,49,280,100,61f)
        val list=ids();channelOffset=channelOffset.coerceIn(0,maxOf(0,list.size-13))
        lateinit var favorite:View
        val rows=list.drop(channelOffset).take(13).mapIndexed{row,index->
            val y=251+row*60
            val v=button("",0,y,936,58){if(selectedChannel==index&&index in recents)showFullscreen() else {selectedChannel=index;recents.add(index);channelFocus=index;live()}}
            v.contentDescription=names[index]
            v.setOnLongClickListener{moving=!moving;true}
            v.setOnKeyListener{_,code,event->
                if(event.action!=KeyEvent.ACTION_DOWN)false else when(code){
                    21->{categories();true}
                    22->{favorite.nextFocusLeftId=v.id;favorite.requestFocus();true}
                    20,19->{
                        if(moving){val from=order.indexOf(index);val to=(from+if(code==20)1 else -1).coerceIn(order.indices);Collections.swap(order,from,to);channelFocus=index;live();true}
                        else if(code==20&&row==12&&channelOffset+13<list.size){channelOffset++;channelFocus=list[channelOffset+12];live();true}
                        else if(code==19&&row==0&&channelOffset>0){channelOffset--;channelFocus=list[channelOffset];live();true}
                        else false
                    }
                    else->false
                }
            }
            text((channelOffset+row+1).toString(),28,y,55,58,27f)
            text("A",101,y+7,62,44,26f,0xffdaeaff.toInt(),true).background=surface(if(theme=="red")accent else 0xff234368.toInt())
            text(names[index],190,y,710,58,29f)
            v
        }
        if(list.isEmpty())text(if(arabic)"لا توجد قنوات" else "No channels",70,300,790,80,32f)
        val preview=button("",960,251,935,526){showFullscreen()}
        preview.background=surface(Color.BLACK)
        text(if(arabic)"لا يوجد بث متصل" else "No stream connected",1030,470,790,70,32f,0xff7f91a8.toInt(),true)
        stage.put(View(context).apply{setBackgroundColor(if(theme=="red")Color.BLACK else if(theme=="gold")0xff161309.toInt() else 0xff061426.toInt())},960,777,935,80)
        text(names[selectedChannel],978,787,750,70,31f).typeface=Typeface.DEFAULT_BOLD
        favorite=button(if(selectedChannel in favorites)"♥" else "♡",1775,787,110,70,47f){
            if(!favorites.add(selectedChannel))favorites.remove(selectedChannel);live()
        }.apply{gravity=Gravity.CENTER}
        favorite.nextFocusLeftId=rows.firstOrNull()?.id?:preview.id
        favorite.nextFocusRightId=preview.id
        favorite.nextFocusUpId=preview.id;favorite.nextFocusDownId=favorite.id
        preview.nextFocusLeftId=rows.firstOrNull()?.id?:favorite.id;preview.nextFocusDownId=favorite.id
        chain(rows)
        text(if(arabic)"ⓘ اضغط مطوّلاً OK لتحريك القنوات" else "ⓘ Long press the \"OK\" button to move Channels",1020,930,850,66,27f)
        focus(rows.getOrNull(list.indexOf(channelFocus)-channelOffset)?:rows.firstOrNull()?:favorite)
    }

    private var seriesMode=false
    private var chosenTitle=0
    private var chosenSeason=0
    private val titles=listOf("The Last Horizon","Silent City","Northbound","Beyond the Tide","Golden Hour","The Archive","Night Train","Open Sky","The Crossing","Another Summer")
    private fun poster(index:Int,x:Int,y:Int,w:Int,h:Int) {
        stage.put(PosterArt(context,index,titles[index%titles.size]),x,y,w,h)
    }
    fun catalogue(series:Boolean) {
        seriesMode=series;page=if(series)"series" else "movies";fullscreen=false;drawer=false;reset()
        mark(35,35,90)
        text("universe-Alaa",135,48,510,65,37f,accent)
        text(if(series) {if(arabic)"المسلسلات" else "Series"} else {if(arabic)"الأفلام" else "Movies"},1430,42,440,84,58f)
        val labels=if(arabic)listOf("الكل","المفضلة","شاهدت مؤخراً","عربي","ألماني","عالمي","دراما","عائلي") else listOf("All","Favorites","Recently Viewed","Arabic","German","International","Drama","Family")
        val categories=labels.mapIndexed{i,label->button("  "+label,0,190+i*83,470,77,29f){catalogue(series)}}
        chain(categories)
        val cards=titles.mapIndexed{i,title->
            val col=i%5;val row=i/5;val x=510+col*277;val y=183+row*423
            val card=button("",x-5,y-5,253,404){chosenTitle=i;if(series)seriesDetail() else movieDetail()}
            poster(i,x+5,y+5,233,333)
            text(title,x,y+345,245,52,26f,textColor,true)
            card
        }
        cards.forEachIndexed{i,v->
            v.nextFocusLeftId=if(i%5==0)categories[0].id else cards[i-1].id
            v.nextFocusRightId=if(i%5==4)v.id else cards[i+1].id
            v.nextFocusUpId=if(i<5)v.id else cards[i-5].id
            v.nextFocusDownId=if(i>=5)v.id else cards[i+5].id
        }
        categories.forEach{it.nextFocusRightId=cards.first().id}
        text(if(arabic)"OK  التفاصيل     رجوع  الرئيسية" else "OK  Details       BACK  Home",520,1010,1250,42,24f)
        focus(cards[chosenTitle])
    }
    fun movieDetail() {
        page="movie-detail";reset()
        text(titles[chosenTitle].uppercase(),288,65,1536,110,49f,accent,true).typeface=Typeface.DEFAULT_BOLD
        poster(chosenTitle,288,216,480,702)
        text(if(arabic)"حكاية رحلة غير متوقعة" else "A journey beyond the familiar",864,240,950,90,39f)
        text(if(arabic)"فيلم من قائمة العرض التجريبية. لم يتم ربط مصدر للمشاهدة." else "An unexpected invitation brings two strangers together.\nA quiet journey becomes a search for home,\nand a chance to begin again.",864,355,930,220,30f).apply{maxLines=5;gravity=Gravity.TOP}
        text("2026    •    Drama    •    1h 48m\nDirector  —\nCast  —",864,600,940,180,28f).apply{maxLines=4;gravity=Gravity.TOP}
        val play=button(if(arabic)"تشغيل" else "Play Movie",864,833,300,77,30f){showFullscreen()}.apply{gravity=Gravity.CENTER}
        val fav=button(if(arabic)"إضافة للمفضلة" else "Add to Favorites",1190,833,420,77,30f){Toast.makeText(context,"Added to Favorites",Toast.LENGTH_SHORT).show()}.apply{gravity=Gravity.CENTER}
        play.nextFocusRightId=fav.id;fav.nextFocusLeftId=play.id;focus(play)
    }
    fun seriesDetail() {
        page="series-detail";reset()
        text(titles[chosenTitle],96,28,1728,70,45f,accent)
        poster(chosenTitle,150,108,400,432)
        text("2026   •   Drama   •   3 Seasons",96,570,540,70,27f)
        text(if(arabic)"حكايات مترابطة ورحلة عبر ثلاث مواسم." else "Three seasons. One unfolding story.\nA collection of unexpected encounters,\nnew beginnings and distant horizons.",96,666,540,190,27f).apply{maxLines=5;gravity=Gravity.TOP}
        val seasons=(0..2).map{i->button(if(arabic)"الموسم "+(i+1) else "Season "+(i+1),680,130+i*86,365,76,32f){chosenSeason=i;seriesDetail()}}
        val eps=(0..8).map{i->button(if(arabic)"الحلقة "+(i+1) else String.format(Locale.US,"%02d   Episode %d",i+1,i+1),1070,130+i*86,750,76,30f){showFullscreen()}}
        chain(seasons);chain(eps)
        seasons.forEach{it.nextFocusRightId=eps[0].id}
        eps.forEach{it.nextFocusLeftId=seasons[chosenSeason].id}
        text(if(arabic)"OK  تشغيل الحلقة    رجوع  المسلسلات" else "OK  Play episode       BACK  Series",680,1000,1140,50,24f)
        focus(seasons[chosenSeason])
    }
    private fun showFullscreen(){fullscreen=true;reset();text(if(arabic)"لا يوجد بث متصل" else "No stream connected",400,450,1120,150,45f,textColor,true)}
    fun back():Boolean {
        if(fullscreen){fullscreen=false;if(page=="series-detail")seriesDetail() else if(page=="movie-detail")movieDetail() else live();return true}
        if(page=="movie-detail"||page=="series-detail"){catalogue(seriesMode);return true}
        if(page=="movies"||page=="series"){home();return true}
        if(page=="themes"){home();return true}
        if(page=="live"){categories();return true}
        if(page=="categories"){home();return true}
        return false
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(super.dispatchKeyEvent(event))return true
        if(event.action!=KeyEvent.ACTION_DOWN)return false
        val d=when(event.keyCode){21->View.FOCUS_LEFT;22->View.FOCUS_RIGHT;19->View.FOCUS_UP;20->View.FOCUS_DOWN;else->return false}
        return findFocus()?.focusSearch(d)?.requestFocus()?:false
    }
}
