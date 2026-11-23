package com.alaa.universe

import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** Atlas: an orbital cinema landscape skin, sharing the provider and playback stack with the other themes. */
internal object Atlas {
    const val ID = "atlas"
    val ink = 0xff0a0d1a.toInt()
    val panel = 0xff171b30.toInt()
    val tile = 0xff21253e.toInt()
    val mint = 0xffd8b9ff.toInt()
    val fg = 0xfff8f3ff.toInt()
    val muted = 0xffb9bdd5.toInt()
    val line = 0xff303554.toInt()
    private val selectedColor = 0xff35264e.toInt()
    private val focused = intArrayOf(android.R.attr.state_focused)
    private val pressed = intArrayOf(android.R.attr.state_pressed)
    private val normal = intArrayOf()
    const val TOP = 215
    const val CATEGORY_X = 1480
    const val CATEGORY_W = 386
    const val LIVE_X = 935
    const val LIVE_W = 522
    const val CAT_PAGE = 9
    const val LIVE_PAGE = 10
    const val GRID_COLUMNS = 5
    const val GRID_PAGE = GRID_COLUMNS * 2

    fun shape(color:Int, radius:Float=18f, stroke:Int?=null, width:Int=1)=GradientDrawable().apply {
        setColor(color);cornerRadius=radius;if(stroke!=null)setStroke(width,stroke)
    }
    fun block(s: Stage, x: Int, y: Int, w: Int, h: Int, color: Int = panel, radius:Float=0f, stroke:Int?=null) {
        s.put(View(s.context).apply { background=shape(color,radius,stroke); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, x,y,w,h)
    }
    fun icon(s:Stage, resource:Int,x:Int,y:Int,size:Int,color:Int=mint) {
        s.put(ImageView(s.context).apply {
            setImageResource(resource);setColorFilter(color);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },x,y,size,size)
    }
    fun chip(s:Stage,title:String,x:Int,y:Int,w:Int,h:Int,color:Int=mint,bg:Int=selectedColor) {
        label(s,title,x,y,w,h,22f,color,true).apply {gravity=Gravity.CENTER;background=shape(bg,16f);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    }
    fun section(s:Stage,title:String,x:Int,y:Int,w:Int,h:Int=37) {
        label(s,title,x,y,w-20,h,28f,fg,true)
        block(s,x+w-5,y+(h-20)/2,5,20,mint,2f)
    }
    fun label(s:Stage, value:String, x:Int,y:Int,w:Int,h:Int,size:Float,
              color:Int=fg, bold:Boolean=false, right:Boolean=true):TextView {
        val v=TextView(s.context).apply {
            text=value; setTextColor(color); includeFontPadding=false; maxLines=1
            ellipsize=TextUtils.TruncateAt.END; textDirection=View.TEXT_DIRECTION_FIRST_STRONG
            gravity=Gravity.CENTER_VERTICAL or if(right) Gravity.RIGHT else Gravity.LEFT
            if(bold) typeface=Typeface.DEFAULT_BOLD
        }
        s.put(v,x,y,w,h,size);return v
    }
    fun row(s:Stage,title:String,x:Int,y:Int,w:Int,h:Int,size:Float=29f,selected:Boolean=false,
            action:()->Unit):TextView = label(s,title,x,y,w,h,size).apply {
        background=StateListDrawable().apply {
            addState(focused,shape(0xffeddaff.toInt(),18f,0xffae82ec.toInt(),2));addState(pressed,shape(mint));addState(normal,shape(if(selected)selectedColor else tile,18f,if(selected)0xff9167bf.toInt() else line))
        }
        setTextColor(ColorStateList(arrayOf(focused,pressed,normal),intArrayOf(ink,ink,fg)))
        // Scale padding with row height rather than display density on small landscape phones.
        addOnLayoutChangeListener { v,_,t,_,b,_,ot,_,ob -> if (b-t != ob-ot) v.setPadding((b-t)/4,0,(b-t)/4,0) }
        id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true
        contentDescription=title;setOnClickListener{action()}
    }
    fun poster(s:Stage,kind:String,index:Int,x:Int,y:Int,w:Int,h:Int){
        s.put(Poster(s.context,index,kind=="series"),x,y,w,h)
    }
    private class Poster(context:Context,index:Int,private val series:Boolean):View(context){
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        private val terrain=Path()
        private val tones=arrayOf(intArrayOf(0xff38306b.toInt(),0xffad7fd2.toInt()),intArrayOf(0xff1a4862.toInt(),0xff88d3eb.toInt()),intArrayOf(0xff5a2a52.toInt(),0xffef9ebe.toInt()),intArrayOf(0xff393368.toInt(),0xffd3b7ff.toInt()),intArrayOf(0xff21454c.toInt(),0xff9ceacb.toInt()))[Math.floorMod(index,5)]
        private val v=Math.floorMod(index,7)
        init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; background=shape(tones[0],16f);clipToOutline=true }
        override fun onDraw(c:Canvas){
            c.save();c.scale(width/240f,height/250f)
            val cx=116f+(v-3)*13;val cy=102f-(v-3)*5
            p.color=tones[1];p.alpha=45;c.drawCircle(cx,cy,65f,p);p.alpha=222;c.drawCircle(cx,cy,37f,p)
            p.alpha=110;p.style=Paint.Style.STROKE;p.strokeWidth=1.5f
            c.save();c.rotate(if(series)-23f else -10f,cx,cy);c.drawOval(cx-97,cy-21,cx+97,cy+21,p);c.restore()
            p.style=Paint.Style.FILL;p.color=0xff11152a.toInt()
            terrain.rewind();terrain.moveTo(0f,182f);terrain.lineTo(55f+v*7,126f);terrain.lineTo(118f,181f);terrain.lineTo(179f-v*3,146f);terrain.lineTo(240f,193f);terrain.lineTo(240f,250f);terrain.lineTo(0f,250f);terrain.close();c.drawPath(terrain,p)
            p.color=tones[1];p.alpha=145;c.drawCircle(23f,28f,2f,p);c.drawCircle(198f,37f,2f,p);c.drawCircle(179f,83f,1.5f,p)
            c.restore()
        }
    }
    fun heading(s:Stage,title:String,subtitle:String) {
        chip(s,"ATLAS",127,47,140,43)
        label(s,"UNIVERSE",291,44,256,49,30f,fg,true,false)
        label(s,title,860,28,1006,86,63f,fg,true)
        label(s,subtitle,730,111,1136,37,26f,muted)
        block(s,54,158,1812,2,line)
    }
    fun vertical(rows:List<View>) = rows.forEachIndexed { i,v ->
        v.nextFocusDownId=rows.getOrNull(i+1)?.id?:v.id
        if(i>0)v.nextFocusUpId=rows[i-1].id
    }
    fun home(screen:UniverseScreen,s:Stage,index:Int,go:(Int)->Unit) {
        heading(s,"الرئيسية", "تجربة أطلس  /  مساحة مشاهدتك")
        s.put(ImageView(s.context).apply {
            setImageResource(R.drawable.atlas_orbit);scaleType=ImageView.ScaleType.FIT_XY
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },54,205,1074,791)
        chip(s,"ATLAS  /  01",101,247,180,43)
        label(s,"أطلس",504,331,556,113,103f,fg,true)
        label(s,"عالمك على شاشة واحدة",446,465,615,68,43f,fg,true)
        label(s,"بث مباشر  ·  أفلام  ·  مسلسلات",438,546,622,48,30f,muted)
        block(s,86,828,1010,128,0xff13182b.toInt(),24f,line)
        icon(s,R.drawable.ic_server,1031,864,36)
        label(s,"مصدر المشاهدة",139,846,861,37,24f,mint,true)
        label(s,screen.provider.snapshot?.name ?: "لا يوجد مصدر متصل",139,890,861,45,31f,fg,true)
        section(s,"استكشف",1170,206,696,56)
        val titles=listOf("البث المباشر","الأفلام","المسلسلات","الإعدادات","تغيير المصدر","تحديث المكتبة")
        val icons=listOf(R.drawable.ic_live_tv,R.drawable.ic_movie,R.drawable.ic_video_library,R.drawable.ic_settings,R.drawable.ic_server,R.drawable.ic_refresh)
        val rows=titles.mapIndexed { i,n ->
            val y=280+i*117
            val v=row(s,n,1170,y,696,97,38f){go(i)}
            val iv=ImageView(s.context).apply {setImageResource(icons[i]);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
            s.put(iv,1203,y+28,40,40)
            iv.setColorFilter(if(v.hasFocus())ink else mint)
            v.setOnFocusChangeListener{_,f->iv.setColorFilter(if(f)ink else mint)}
            v
        }
        vertical(rows);rows[index.coerceIn(rows.indices)].requestFocus()
    }
}
