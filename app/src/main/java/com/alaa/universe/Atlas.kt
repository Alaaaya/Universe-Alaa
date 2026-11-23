package com.alaa.universe

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** Atlas: a flat landscape skin, sharing the provider and playback stack with the other themes. */
internal object Atlas {
    const val ID = "atlas"
    val ink = 0xff101915.toInt()
    val panel = 0xff192820.toInt()
    val tile = 0xff22342a.toInt()
    val mint = 0xffc8f280.toInt()
    val fg = 0xfff5f7ef.toInt()
    val muted = 0xffb0c1b4.toInt()
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

    fun block(s: Stage, x: Int, y: Int, w: Int, h: Int, color: Int = panel) {
        s.put(View(s.context).apply { setBackgroundColor(color); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, x,y,w,h)
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
            addState(focused,surface(mint));addState(pressed,surface(mint));addState(normal,surface(if(selected)0xff35543b.toInt() else tile))
        }
        setTextColor(ColorStateList(arrayOf(focused,pressed,normal),intArrayOf(ink,ink,fg)))
        // Scale padding with row height rather than display density on small landscape phones.
        addOnLayoutChangeListener { v,_,t,_,b,_,ot,_,ob -> if (b-t != ob-ot) v.setPadding((b-t)/4,0,(b-t)/4,0) }
        id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true
        contentDescription=title;setOnClickListener{action()}
    }
    fun heading(s:Stage,title:String,subtitle:String) {
        label(s,"UNIVERSE / ATLAS",115,35,540,60,30f,mint,true,false)
        label(s,title,860,34,1006,77,59f,fg,true)
        label(s,subtitle,730,110,1136,43,25f,muted)
        block(s,54,163,1812,2,tile)
    }
    fun vertical(rows:List<View>) = rows.forEachIndexed { i,v ->
        v.nextFocusDownId=rows.getOrNull(i+1)?.id?:v.id
        if(i>0)v.nextFocusUpId=rows[i-1].id
    }
    fun home(screen:UniverseScreen,s:Stage,index:Int,go:(Int)->Unit) {
        heading(s,"الرئيسية", "أطلس  /  مساحة مشاهدة واحدة")
        block(s,54,205,1074,791)
        block(s,54,205,1074,7,mint)
        s.put(AlaaMark(s.context,mint),136,325,242,242)
        label(s,"UNIVERSE",450,347,604,126,101f,fg,true)
        label(s,"بوابتك للبث والأفلام والمسلسلات",405,491,650,55,36f,muted)
        block(s,107,690,947,2,tile)
        label(s,screen.provider.snapshot?.name ?: "لا يوجد مزوّد متصل",103,721,951,64,38f,mint,true)
        label(s,"أدر مصدر المشاهدة من الإعدادات",103,795,951,48,29f,muted)
        label(s,"انتقل إلى",1170,204,696,59,35f,fg,true)
        val rows=listOf("البث المباشر","الأفلام","المسلسلات","الإعدادات","تغيير المصدر","تحديث المكتبة").mapIndexed { i,n ->
            row(s,n,1170,280+i*117,696,97,38f){go(i)}
        }
        vertical(rows);rows[index.coerceIn(rows.indices)].requestFocus()
    }
}
