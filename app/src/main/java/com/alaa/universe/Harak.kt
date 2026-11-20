package com.alaa.universe

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import android.text.TextUtils

/** Native counterpart of webOS theme-harak (Noir et Or). No web content is executed. */
object Harak {
    const val GOLD = 0xffd6b254.toInt()
    const val INK = 0xff060608.toInt()
    const val FG = 0xfff5eedc.toInt()
    const val MUTED = 0xffa59c86.toInt()
    private var bodyFont:Typeface?=null
    private var headingFont:Typeface?=null
    fun font(c:Context,heading:Boolean=false):Typeface {
        if(bodyFont==null)bodyFont=Typeface.createFromAsset(c.assets,"harak/plex.ttf")
        if(headingFont==null)headingFont=Typeface.createFromAsset(c.assets,"harak/kufi.ttf")
        return (if(heading)headingFont else bodyFont)!!
    }
    fun box(fill:Int=0xff121116.toInt(),radius:Float=16f,focus:Boolean=false)=GradientDrawable().apply {
        setColor(if(focus)0xff201d26.toInt() else fill);cornerRadius=radius
        setStroke(if(focus)2 else 1,if(focus)GOLD else 0x306c592e)
    }
    fun gold()=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xfffdeeb5.toInt(),0xffe7c46a.toInt(),0xffb98b2d.toInt(),0xfff2d685.toInt())).apply{cornerRadius=18f}
    fun icon(s:Stage,res:Int,x:Int,y:Int,w:Int,h:Int) {
        s.put(ImageView(s.context).apply{setImageResource(res);setColorFilter(GOLD);scaleType=ImageView.ScaleType.FIT_CENTER},x,y,w,h)
    }
    fun label(s:Stage,t:String,x:Int,y:Int,w:Int,h:Int,size:Float=26f,heading:Boolean=false,color:Int=FG):TextView =
        TextView(s.context).apply {
            text=t;setTextColor(color);typeface=font(context,heading);includeFontPadding=false
            gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL;textDirection=View.TEXT_DIRECTION_FIRST_STRONG;maxLines=1;ellipsize=TextUtils.TruncateAt.END
            s.put(this,x,y,w,h,size)
        }
    fun button(s:Stage,t:String,x:Int,y:Int,w:Int,h:Int,size:Float=26f,primary:Boolean=false,action:()->Unit):TextView =
        label(s,t,x,y,w,h,size).apply {
            id=View.generateViewId();contentDescription=t;isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            setPadding(20,0,20,0)
            background=if(primary)gold() else box()
            if(primary)setTextColor(INK)
            setOnClickListener{action()}
            setOnFocusChangeListener{_,f->
                background=if(primary&&!f)gold() else box(focus=f)
                setTextColor(if(primary&&!f)INK else FG)
                // Source theme deliberately does not scale focused controls.
                animate().alpha(1f).setDuration(140).start()
            }
        }
    fun chrome(s:Stage,screen:UniverseScreen,title:String="",active:Int=0):List<TextView> {
        s.put(View(s.context).apply{setBackgroundColor(0xff0d0c10.toInt())},0,0,300,1080)
        s.put(View(s.context).apply{setBackgroundColor(0x345f4c29)},299,0,1,1080)
        label(s,"universe",if(screen.page=="home")28 else 108,28,if(screen.page=="home")252 else 174,42,if(screen.page=="home")32f else 26f,true)
        label(s,"Alaa",if(screen.page=="home")28 else 108,69,174,35,22f,false,GOLD)
        val labels=listOf("الرئيسية","البث المباشر","الأفلام","المسلسلات","المفضلة","المشاهدة الأخيرة","قائمة التشغيل","الإعدادات")
        val icons=listOf(R.drawable.ic_live_tv,R.drawable.ic_live_tv,R.drawable.ic_movie,R.drawable.ic_video_library,R.drawable.ic_subscriptions,R.drawable.ic_refresh,R.drawable.ic_subscriptions,R.drawable.ic_settings)
        val rows=labels.mapIndexed{i,t->button(s,t,20,if(i==7)980 else 126+i*78,260,70,23f){
            when(i){0->screen.home();1->screen.categories();2->screen.catalogue(false);3->screen.catalogue(true);4->screen.provider.openSaved("favorites");5->screen.provider.openSaved("recent");6->screen.provider.loginDialog();7->screen.openThemes()}
        }.also{
            it.setPadding(62,0,10,0)
            icon(s,icons[i],36,if(i==7)999 else 145+i*78,32,32)
            if(i==active)it.background=box(0xff252019.toInt())
        }}
        rows.forEachIndexed{i,v->v.nextFocusUpId=rows.getOrNull(i-1)?.id?:v.id;v.nextFocusDownId=rows.getOrNull(i+1)?.id?:v.id}
        label(s,if(title.isBlank())"ابحث عن قناة، فيلم أو مسلسل ..." else title,348,22,640,64,27f).apply{background=box();setPadding(22,0,22,0)}
        label(s,screen.fixedClock?:java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date()),1665,16,210,58,46f,true,GOLD).gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
        label(s,java.text.SimpleDateFormat("EEEE، d MMMM yyyy",java.util.Locale.forLanguageTag("ar")).format(java.util.Date()),1530,76,345,26,18f,false,MUTED).gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
        return rows
    }
    fun home(screen:UniverseScreen,s:Stage) {
        val nav=chrome(s,screen)
        val entries=screen.provider.homeEntries()
        val hero=entries.firstOrNull{it.kind=="movie"}?:entries.firstOrNull{it.kind=="series"}
        s.put(View(s.context).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xff19161d.toInt(),0xff1e1b1b.toInt(),0xff383027.toInt())).apply{cornerRadius=30f;setStroke(1,0x665f4e2f)}},348,118,1528,430)
        label(s,screen.provider.snapshot?.name?:"اربط قائمة التشغيل لتبدأ",412,151,920,40,22f,false,GOLD)
        label(s,hero?.name?:"أهلاً بك في universe-Alaa",412,213,1060,90,46f,true).apply{maxLines=2}
        label(s,hero?.plot?.ifBlank{"مكتبتك من القنوات والأفلام والمسلسلات في مكان واحد"}?:"مكتبتك من القنوات والأفلام والمسلسلات في مكان واحد",412,325,960,76,22f,false,MUTED).maxLines=2
        val play=button(s,if(hero==null)"قائمة التشغيل" else "شاهد الآن",412,431,220,64,24f,true){
            if(hero!=null)screen.provider.openHomeEntry(hero) else screen.provider.loginDialog()
        }
        button(s,"مزيد من المعلومات",650,431,240,64,24f){if(hero!=null)screen.provider.openHomeEntry(hero) else screen.provider.loginDialog()}
        val poster=ImageView(s.context).apply{background=box(0xff28231f.toInt(),18f);rotation=4f;scaleType=ImageView.ScaleType.CENTER_CROP;clipToOutline=true}
        s.put(poster,1570,158,236,350);hero?.let{screen.provider.homeArtwork(it,poster)}
        val labels=listOf("البث المباشر","الأفلام","المسلسلات","المفضلة","المشاهدة الأخيرة","تحديث")
        val tileIcons=listOf(R.drawable.ic_live_tv,R.drawable.ic_movie,R.drawable.ic_video_library,R.drawable.ic_subscriptions,R.drawable.ic_refresh,R.drawable.ic_refresh)
        val cards=labels.mapIndexed{i,t->
            val x=348+i*258
            val b=button(s,"",x,576,238,124,26f){
                when(i){0->screen.categories();1->screen.catalogue(false);2->screen.catalogue(true);3->screen.provider.openSaved("favorites");4->screen.provider.openSaved("recent");5->screen.provider.refresh()}
            };b.contentDescription=t
            icon(s,tileIcons[i],x+20,598,42,42)
            label(s,t,x+78,591,151,50,21f)
            val count=when(i){0->screen.provider.homeCount("live").toString();1->screen.provider.homeCount("movie").toString();2->screen.provider.homeCount("series").toString();else->""}
            label(s,if(count.isBlank())"" else count+" "+listOf("قناة محمّلة","فيلم","مسلسل")[i],x+80,642,140,32,17f,false,MUTED);b
        }
        s.put(View(s.context).apply{setBackgroundColor(GOLD)},348,748,4,32)
        label(s,"أحدث الأفلام",370,734,1100,56,32f,true)
        button(s,"عرض الكل",1710,740,166,50,21f){screen.catalogue(false)}
        entries.filter{it.kind=="movie"||it.kind=="series"}.take(6).forEachIndexed{i,e->
            val x=348+i*250
            button(s,"",x,808,208,360){screen.provider.openHomeEntry(e)}.contentDescription=e.name
            val image=ImageView(s.context).apply{scaleType=ImageView.ScaleType.CENTER_CROP;clipToOutline=true;background=box()}
            s.put(image,x,808,208,292);screen.provider.homeArtwork(e,image)
            label(s,"HD",x+12,822,50,30,16f,false,GOLD).background=box()
        }
        nav.forEach{it.nextFocusRightId=play.id}
        cards.first().nextFocusLeftId=nav.first().id;play.nextFocusLeftId=nav.first().id
        play.nextFocusDownId=cards.first().id
        play.requestFocus()
        // One short arrival, never a permanent render loop on low-memory TVs.
        s.alpha=0f;s.animate().alpha(1f).setDuration(250).start()
    }
}
