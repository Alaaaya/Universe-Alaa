package com.alaa.universe

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.*

class UniverseScreen(context:Context): FrameLayout(context) {
    var theme="blue"
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
    private val accent get()=if(theme=="gold")0xffd8b602.toInt() else 0xff518df4.toInt()
    private val textColor=0xffe2eaf6.toInt()
    init {layoutDirection=View.LAYOUT_DIRECTION_LTR}
    private fun reset(wave:Boolean=false) {
        removeAllViews();stage=Stage(context).apply{theme=this@UniverseScreen.theme;waves=wave}
        addView(stage,LayoutParams(-1,-1))
    }
    private fun text(value:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f,color:Int=textColor,center:Boolean=false):TextView {
        val v=TextView(context).apply{
            text=value;setTextColor(color);gravity=if(center)Gravity.CENTER else Gravity.CENTER_VERTICAL
            typeface=Typeface.create("sans-serif",Typeface.NORMAL);includeFontPadding=false
            maxLines=1;ellipsize=TextUtils.TruncateAt.END;layoutDirection=View.LAYOUT_DIRECTION_LTR
            textDirection=View.TEXT_DIRECTION_FIRST_STRONG
        }
        stage.put(v,x,y,w,h,size);return v
    }
    private fun button(value:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f,action:()->Unit):TextView =
        text(value,x,y,w,h,size).apply{
            id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            background=surface(0x22020c1c)
            setOnFocusChangeListener{_,f->background=surface(if(f)accent else 0x22020c1c)}
            setOnClickListener{action()}
        }
    private fun focus(v:View) {v.requestFocus();v.post{if(findFocus()==null&&v.isAttachedToWindow)v.requestFocus()}}
    private fun chain(rows:List<View>) {rows.forEachIndexed{i,v->
        v.nextFocusUpId=rows[maxOf(0,i-1)].id;v.nextFocusDownId=rows[minOf(rows.lastIndex,i+1)].id
        v.nextFocusLeftId=v.id;v.nextFocusRightId=v.id
    }}
    private fun clock()=fixedClock?:SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
    private fun mark(x:Int,y:Int,size:Int) {stage.put(AlaaMark(context,if(theme=="gold")accent else 0xffb8d9ff.toInt()),x,y,size,size)}
    private fun icon(res:Int,x:Int,y:Int,w:Int,h:Int) {stage.put(ImageView(context).apply{setImageResource(res);setColorFilter(if(theme=="gold")accent else 0xffb8d9ff.toInt());scaleType=ImageView.ScaleType.FIT_CENTER},x,y,w,h)}
    private fun ids():List<Int> = order.filter{when(selectedCategory){1->it in favorites;2->it in recents;0->true;else->it%7==selectedCategory-3}}
    fun home() {
        page="home";drawer=false;fullscreen=false;reset()
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
                if(i==0) categories() else if(i==4) themeDialog() else Toast.makeText(context,if(arabic)"غير متصل" else "Not connected",Toast.LENGTH_SHORT).show()
            }.apply{contentDescription=s;gravity=Gravity.CENTER}
        }
        cards.forEachIndexed{i,v->v.nextFocusUpId=v.id;v.nextFocusDownId=v.id;v.nextFocusLeftId=cards[maxOf(0,i-1)].id;v.nextFocusRightId=cards[minOf(5,i+1)].id}
        focus(cards[homeIndex])
    }
    private fun themeDialog() {
        android.app.AlertDialog.Builder(context).setTitle(if(arabic)"المظهر" else "Theme")
            .setItems(arrayOf("Blue","Royal Blue","Gold")){_,i->theme=listOf("blue","royal","gold")[i];home()}.show()
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
            text("A",101,y+7,62,44,26f,0xffdaeaff.toInt(),true).background=surface(0xff234368.toInt())
            text(names[index],190,y,710,58,29f)
            v
        }
        if(list.isEmpty())text(if(arabic)"لا توجد قنوات" else "No channels",70,300,790,80,32f)
        val preview=button("",960,251,935,526){showFullscreen()}
        preview.background=surface(Color.BLACK)
        text(if(arabic)"لا يوجد بث متصل" else "No stream connected",1030,470,790,70,32f,0xff7f91a8.toInt(),true)
        stage.put(View(context).apply{setBackgroundColor(0xff061426.toInt())},960,777,935,80)
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
    private fun showFullscreen(){fullscreen=true;reset();text(if(arabic)"لا يوجد بث متصل" else "No stream connected",400,450,1120,150,45f,textColor,true)}
    fun back():Boolean {
        if(fullscreen){live();return true}
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
