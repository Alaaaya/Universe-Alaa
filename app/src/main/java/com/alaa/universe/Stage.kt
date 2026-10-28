package com.alaa.universe

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.*
import android.widget.TextView

/** Coordinates are a 1920x1080 design surface; children remain native Android focus targets. */
class Stage(context: Context): ViewGroup(context) {
    data class Slot(val x:Int,val y:Int,val w:Int,val h:Int,val font:Float?)
    private val slots = mutableMapOf<View,Slot>()
    init { layoutDirection = LAYOUT_DIRECTION_LTR; setWillNotDraw(false) }
    fun put(v: View,x:Int,y:Int,w:Int,h:Int,font:Float?=null): View {
        slots[v]=Slot(x,y,w,h,font); addView(v); return v
    }
    override fun onMeasure(w:Int,h:Int) {
        val width=MeasureSpec.getSize(w); val height=MeasureSpec.getSize(h)
        val s=minOf(width/1920f,height/1080f)
        slots.forEach { (v,b) ->
            if (v is TextView && b.font!=null) v.setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*s)
            v.measure(MeasureSpec.makeMeasureSpec((b.w*s).toInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((b.h*s).toInt(),MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(width,height)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        val s=minOf(width/1920f,height/1080f); val ox=(width-1920*s)/2; val oy=(height-1080*s)/2
        slots.forEach { (v,p) ->
            val x=(ox+p.x*s).toInt();val y=(oy+p.y*s).toInt()
            v.layout(x,y,x+v.measuredWidth,y+v.measuredHeight)
        }
    }
    override fun onDraw(c:Canvas) {
        c.drawColor(Color.rgb(12,12,12))
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=RadialGradient(width*.5f,height*.35f,width*.55f,intArrayOf(0xff332e17.toInt(),0xff12120f.toInt(),0xff0c0c0c.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
    }
}

fun surface(color:Int,stroke:Int?=null): GradientDrawable = GradientDrawable().apply {
    setColor(color); if(stroke!=null) setStroke(2,stroke)
}

/** Original orbit/play monogram. No Universe/IBO artwork. */
class AlaaMark(context:Context): View(context) {
    override fun onDraw(c:Canvas) {
        val s=minOf(width,height).toFloat();c.save();c.translate((width-s)/2,(height-s)/2);c.scale(s/160,s/160)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0xffd8b602.toInt();style=Paint.Style.STROKE;strokeWidth=3f}
        c.save();c.rotate(-25f,80f,80f);c.drawOval(10f,49f,150f,111f,p);c.restore()
        c.drawCircle(80f,80f,48f,p)
        p.style=Paint.Style.FILL
        val path=Path().apply{moveTo(69f,55f);lineTo(69f,105f);lineTo(105f,80f);close()}
        c.drawPath(path,p);c.drawCircle(139f,51f,6f,p);c.restore()
    }
}
