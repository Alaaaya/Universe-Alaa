package com.alaa.universe

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.*
import android.widget.*
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.*

class UniverseScreen(context:Context): FrameLayout(context) {
    private val gold=0xffd8b602.toInt()
    private val white=0xffe0e0e0.toInt()
    private val grey=0xffbdbdbd.toInt()
    private lateinit var stage:Stage
    var page="home"; private set
    var drawer=false; private set
    var fullscreen=false; private set
    var selectedChannel=0; private set
    var selectedCategory=0; private set
    var arabic=false
    var fixedClock:String?=null
    private var homeIndex=0
    private var focusChannel=0
    private var channelOffset=0
    private val favorites=mutableSetOf<Int>()
    private var categoryViews=listOf<View>()
    private var channelViews=listOf<View>()
    private var search:View?=null
    private var favorite:View?=null
    private val names=listOf("Alaa One","Alaa Cinema","Alaa Sports","Alaa Documentary","Alaa Music","Alaa Kids","Alaa World","Alaa Culture","Alaa Nature","Alaa Radio","Alaa Travel","Alaa Classic")
    private val groups=listOf("All channels","Favorites","Recently viewed","Arabic","Germany","Sports","Movies","Documentaries")
    private val arGroups=listOf("كل القنوات","المفضلة","شاهدت مؤخراً","عربي","ألمانيا","رياضة","أفلام","وثائقي")
    private val recents=mutableSetOf<Int>()
    private fun activeIds():List<Int> = when(selectedCategory) {
        1 -> names.indices.filter{it in favorites}
        2 -> names.indices.filter{it in recents}
        0 -> names.indices.toList()
        else -> names.indices.filter{it%5==selectedCategory-3}
    }
    init { layoutDirection=View.LAYOUT_DIRECTION_LTR }
    private fun reset() { removeAllViews();stage=Stage(context);addView(stage,LayoutParams(-1,-1)) }
    private fun text(value:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f,color:Int=white,center:Boolean=false):TextView {
        val v=TextView(context).apply {
            text=value;setTextColor(color);gravity=if(center) Gravity.CENTER else Gravity.CENTER_VERTICAL
            typeface=Typeface.create("sans-serif-light",Typeface.NORMAL);includeFontPadding=false
            textDirection=View.TEXT_DIRECTION_FIRST_STRONG;layoutDirection=View.LAYOUT_DIRECTION_LTR
            maxLines=1;ellipsize=TextUtils.TruncateAt.END
        }
        stage.put(v,x,y,w,h,size);return v
    }
    private fun button(value:String,x:Int,y:Int,w:Int,h:Int,size:Float=28f,action:()->Unit):TextView {
        return text(value,x,y,w,h,size,white,true).apply {
            id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            background=surface(0x55202020)
            setOnFocusChangeListener{_,focused->background=surface(if(focused) 0x99c6a701.toInt() else 0x55202020,if(focused) gold else null)}
            setOnClickListener{action()}
        }
    }
    private fun icon(res:Int,x:Int,y:Int,w:Int,h:Int) {
        stage.put(ImageView(context).apply{setImageResource(res);scaleType=ImageView.ScaleType.FIT_CENTER},x,y,w,h)
    }
    private fun mark(x:Int,y:Int,size:Int) {stage.put(AlaaMark(context),x,y,size,size)}
    private fun clock():String=fixedClock?:SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
    private fun footer(value:String) { text(value,60,996,1800,40,23f,grey) }
    private fun focus(v:View) { v.requestFocus();v.post{if(findFocus()==null && v.isAttachedToWindow) v.requestFocus()} }
    private fun trap(v:View) {v.nextFocusUpId=v.id;v.nextFocusDownId=v.id;v.nextFocusLeftId=v.id;v.nextFocusRightId=v.id}
    fun home() {
        page="home";drawer=false;fullscreen=false;reset()
        text(if(arabic) "أهلاً، علاء" else "Welcome, Alaa",64,50,550,44,32f)
        text(if(arabic) "معاينة التصميم · غير مفعّل" else "Design preview · not activated",64,101,620,38,24f,grey)
        text(clock(),698,48,240,70,54f,white,true)
        text("28 October 2026",680,122,300,32,24f,grey,true).text=if(fixedClock!=null) "28 October 2026" else SimpleDateFormat("dd MMMM yyyy",Locale.getDefault()).format(Date())
        text(if(arabic) "الطقس غير مفعّل" else "Weather disabled",1440,62,416,40,27f,grey)
        text("PHASE 1  /  UI PREVIEW",1440,108,416,30,19f,gold)
        mark(871,226,178)
        text("universe-Alaa",460,413,1000,88,72f,white,true).typeface=Typeface.create("sans-serif-light",Typeface.NORMAL)
        text(if(arabic) "عالمك، على شاشتك" else "Your world. Your screen.",610,508,700,46,29f,grey,true)
        val labels=if(arabic) listOf("بث مباشر","أفلام","مسلسلات","اشتراكات","إعدادات","تحديث") else listOf("Live TV","Movies","Series","Subscriptions","Settings","Reload")
        val icons=listOf(R.drawable.ic_live_tv,R.drawable.ic_movie,R.drawable.ic_video_library,R.drawable.ic_subscriptions,R.drawable.ic_settings,R.drawable.ic_refresh)
        val cards=labels.mapIndexed { i,name ->
            val x=64+i*300
            val card=button("",x,648,280,230) {
                homeIndex=i
                if(i==0) live() else Toast.makeText(context,if(arabic)"هالقسم بالمرحلة الجاية" else "Available in the next phase",Toast.LENGTH_SHORT).show()
            }
            card.contentDescription=name
            icon(icons[i],x+92,688,96,96)
            text(name,x,817,280,58,29f,white,true).apply {background=surface(0x889c8300.toInt());isDuplicateParentStateEnabled=true}
            card
        }
        cards.forEachIndexed { i,v ->
            trap(v);v.nextFocusLeftId=cards[maxOf(0,i-1)].id;v.nextFocusRightId=cards[minOf(5,i+1)].id
        }
        footer(if(arabic) "جهاز تجريبي  02:41:4C:41:41:01       رمز الربط  DEMO ONLY       الربط باللوحة غير مفعّل" else "Demo device  02:41:4C:41:41:01       Pairing  DEMO ONLY       Panel connection not enabled")
        focus(cards[homeIndex])
    }

    fun live(openDrawer:Boolean=false) {
        page="live";drawer=openDrawer;fullscreen=false;reset()
        mark(55,38,88)
        button(if(arabic) "رجوع" else "Back",159,52,130,62,26f){home()}
        text(if(arabic) "بث مباشر" else "Live TV",750,48,420,70,43f,white,true)
        text(clock(),1630,48,225,64,40f,white,true)
        val g=if(arabic) arGroups else groups
        button("‹   "+g[selectedCategory],60,177,650,65,31f){live(true)}
        val searchButton=button(if(arabic) "بحث" else "Search",736,177,178,65,26f){searchDialog()}
        search=searchButton
        val ids=activeIds()
        channelOffset=channelOffset.coerceIn(0,maxOf(0,ids.size-9))
        channelViews=ids.drop(channelOffset).take(9).mapIndexed{row,index->
            val label=if(arabic) "قناة علاء "+(index+1) else names[index]
            val v=button(String.format(Locale.US,"%02d     %s%s",index+1,label,if(index in favorites)"    ♥" else ""),60,265+row*74,854,66,29f){
                if(selectedChannel==index && index in recents) showFullscreen() else {selectedChannel=index;recents.add(index);focusChannel=index;live()}
            }
            v.gravity=Gravity.CENTER_VERTICAL;v.setPadding(25,0,15,0)
            v.setOnKeyListener { _,code,event ->
                if(event.action==KeyEvent.ACTION_DOWN) {
                    focusChannel=index
                    when(code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { live(true);true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { favorite?.nextFocusLeftId=v.id;favorite?.requestFocus();true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> if(row==8 && channelOffset+9<ids.size) {channelOffset++;focusChannel=ids[minOf(ids.lastIndex,channelOffset+8)];live();true} else false
                        KeyEvent.KEYCODE_DPAD_UP -> if(row==0 && channelOffset>0) {channelOffset--;focusChannel=ids[channelOffset];live();true} else false
                        else -> false
                    }
                } else false
            };v
        }
        text(if(ids.isEmpty()) "0 / 0" else "${channelOffset+1}–${minOf(channelOffset+9,ids.size)} / ${ids.size}",60,939,854,34,23f,grey)
        if(ids.isEmpty()) text(if(arabic)"لا توجد قنوات بهذا القسم" else "No channels in this category",60,310,854,80,29f,grey,true)
        val preview=button("",960,177,902,515){showFullscreen()}
        preview.background=surface(Color.BLACK,0xff303030.toInt())
        mark(1315,300,170)
        text(if(arabic)"معاينة البث" else "Live preview",1050,484,722,50,34f,white,true)
        text(if(arabic)"لا يوجد مصدر متصل · بيانات تجريبية" else "No source connected · demo catalogue",1020,546,782,40,24f,grey,true)
        text(if(arabic)"قناة علاء "+(selectedChannel+1) else names[selectedChannel],972,721,702,60,36f)
        val fav=button(if(selectedChannel in favorites)"♥" else "♡",1744,717,118,70,43f) {
            if(!favorites.add(selectedChannel)) favorites.remove(selectedChannel)
            live();favorite?.requestFocus()
        };favorite=fav
        fav.nextFocusLeftId=channelViews.firstOrNull()?.id?:searchButton.id;fav.nextFocusRightId=preview.id;fav.nextFocusUpId=searchButton.id;fav.nextFocusDownId=fav.id
        text(if(arabic)"أضف قائمة التشغيل من لوحتك" else "Add your playlist from your own panel",972,811,870,45,29f,gold)
        text(if(arabic)"المشغّل والاتصال باللوحة بالمرحلة التالية" else "Playback and secure pairing follow in the next phase.",972,867,870,48,25f,grey)
        footer(if(arabic)"يسار  الأقسام      يمين  المفضلة      OK  اختيار / تكبير      رجوع  الرئيسية" else "LEFT  Categories       RIGHT  Favorite       OK  Select / fullscreen       BACK  Home")
        channelViews.forEachIndexed{i,v->
            v.nextFocusUpId=if(i==0)searchButton.id else channelViews[i-1].id
            v.nextFocusDownId=channelViews[minOf(i+1,channelViews.lastIndex)].id
            v.nextFocusRightId=fav.id
        }
        searchButton.nextFocusDownId=channelViews.firstOrNull()?.id?:preview.id
        searchButton.nextFocusRightId=fav.id
        preview.nextFocusLeftId=channelViews.firstOrNull()?.id?:searchButton.id;preview.nextFocusDownId=fav.id
        if(openDrawer) {
            stage.put(View(context).apply{setBackgroundColor(0xff151510.toInt())},0,162,672,804)
            text(if(arabic)"الأقسام" else "Categories",60,179,550,60,35f,gold)
            categoryViews=g.mapIndexed{i,name->
                button(name,48,267+i*78,582,68,30f){
                    selectedCategory=i;channelOffset=0;focusChannel=0;live()
                }.apply {
                    gravity=Gravity.CENTER_VERTICAL;setPadding(25,0,10,0)
                    setOnKeyListener{_,code,event->
                        if(event.action==KeyEvent.ACTION_DOWN && code==KeyEvent.KEYCODE_DPAD_RIGHT){
                            selectedCategory=i;channelOffset=0;live();true
                        }else false
                    }
                }
            }
            categoryViews.forEachIndexed{i,v->trap(v);v.nextFocusUpId=categoryViews[maxOf(0,i-1)].id;v.nextFocusDownId=categoryViews[minOf(i+1,categoryViews.lastIndex)].id}
            // Drawer is modal: underlying controls must not acquire keyboard/accessibility focus.
            for(i in 0 until stage.childCount) {
                val v=stage.getChildAt(i)
                if(v !in categoryViews) {v.isFocusable=false;v.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
            }
            focus(categoryViews[selectedCategory])
        } else {
            val row=ids.indexOf(focusChannel)-channelOffset
            focus(channelViews.getOrNull(row)?:channelViews.firstOrNull()?:searchButton)
        }
    }
    private fun searchDialog() {
        val input=EditText(context).apply{hint=if(arabic)"اسم القناة" else "Channel name";setSingleLine(true)}
        android.app.AlertDialog.Builder(context).setTitle(if(arabic)"بحث ضمن البيانات التجريبية" else "Search demo catalogue").setView(input)
            .setPositiveButton(if(arabic)"بحث" else "Search"){_,_->
                val i=names.indexOfFirst{it.contains(input.text.toString(),true)}
                if(i>=0){selectedCategory=0;selectedChannel=i;focusChannel=i;channelOffset=maxOf(0,i-8);live()}
                else Toast.makeText(context,if(arabic)"لا توجد نتائج" else "No results",Toast.LENGTH_SHORT).show()
            }.setNegativeButton(if(arabic)"إلغاء" else "Cancel",null).show()
    }
    private fun showFullscreen() {
        fullscreen=true;reset()
        stage.setBackgroundColor(Color.BLACK)
        mark(820,284,280)
        text(if(arabic)"لا يوجد بث متصل" else "No stream connected",400,602,1120,70,48f,white,true)
        text(if(arabic)"وضع المعاينة فقط · اضغط رجوع للقنوات" else "Preview only · press Back to return to channels",300,700,1320,60,30f,grey,true)
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if (super.dispatchKeyEvent(event)) return true
        if(event.action!=KeyEvent.ACTION_DOWN) return false
        val direction=when(event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            else -> return false
        }
        val current=findFocus() ?: return false
        return current.focusSearch(direction)?.requestFocus() ?: false
    }
    fun back():Boolean {
        if(fullscreen){live();return true}
        if(drawer){live();return true}
        if(page=="live"){home();return true}
        return false
    }
}
