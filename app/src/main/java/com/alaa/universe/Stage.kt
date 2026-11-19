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
    var theme="red"
    var waves=false
    var transparent=false
    var phoneReflow=true
    private var phoneHeight=0
    private var lastTouchY=0f
    private var touchStartY=0f
    private var dragging=false
    private val phoneBounds=mutableMapOf<View,Rect>()
    private val slots = mutableMapOf<View,Slot>()
    init { layoutDirection = LAYOUT_DIRECTION_LTR; setWillNotDraw(false) }
    fun put(v: View,x:Int,y:Int,w:Int,h:Int,font:Float?=null): View {
        slots[v]=Slot(x,y,w,h,font); addView(v); return v
    }
    fun place(v:View,x:Int,y:Int,w:Int,h:Int,font:Float?=null) {
        slots[v]=Slot(x,y,w,h,font);requestLayout()
    }
    override fun onMeasure(w:Int,h:Int) {
        val width=MeasureSpec.getSize(w); val height=MeasureSpec.getSize(h)
        if(PhoneInput.enabled(context) && phoneReflow){measurePhone(width,height);return}
        val s=minOf(width/1920f,height/1080f)
        slots.forEach { (v,b) ->
            if(PhoneInput.enabled(context) && !phoneReflow && b.w==1920 && b.h==1080){v.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));return@forEach}
            if (v is TextView && b.font!=null) v.setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*s)
            v.measure(MeasureSpec.makeMeasureSpec((b.w*s).toInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((b.h*s).toInt(),MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(width,height)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        if(PhoneInput.enabled(context) && phoneReflow){
            slots.keys.forEach{v->val box=phoneBounds[v];if(box!=null)v.layout(box.left,box.top,box.right,box.bottom) else v.layout(0,0,0,0)}
            return
        }
        val s=minOf(width/1920f,height/1080f); val ox=(width-1920*s)/2; val oy=(height-1080*s)/2
        slots.filterKeys{it.parent===this}.forEach { (v,p) ->
            if(PhoneInput.enabled(context) && !phoneReflow && p.w==1920 && p.h==1080){v.layout(0,0,width,height);return@forEach}
            val x=(ox+p.x*s).toInt();val y=(oy+p.y*s).toInt()
            v.layout(x,y,x+v.measuredWidth,y+v.measuredHeight)
        }
        findViewWithTag<View>("universe-back")?.takeIf{it.parent===this}?.let{back->
            back.bringToFront()
            val rows=slots.keys.filter{it!==back&&it.isFocusable&&it.visibility==View.VISIBLE}
            val top=rows.minOfOrNull{it.top}
            rows.filter{it.top==top}.forEach{it.nextFocusUpId=back.id}
            if(back.nextFocusDownId==View.NO_ID)back.nextFocusDownId=rows.firstOrNull{it.top==top}?.id?:back.id
        }
    }
    // Reuse the real route actions, colors and typefaces, but never shrink TV
    // coordinates into tiny phone targets. TV measurement/focus stays untouched.
    private fun measurePhone(width:Int,height:Int){
        val d=resources.displayMetrics.density
        fun dp(n:Int)=(n*d).toInt()
        phoneBounds.clear()
        val margin=dp(16);var y=dp(12)
        val anchors=slots.keys.filter{it.visibility!=View.GONE && it.isClickable}
        val header=slots.keys.filterIsInstance<TextView>().filter{
            !it.isClickable && it.text.isNotBlank() && ((slots[it]?.y?:9999)<160 || it.text.toString()=="universe-Alaa")
        }.sortedBy{if(it.text.toString()=="universe-Alaa")0 else 1}.take(3)
        fun row(v:View,h:Int){
            phoneBounds[v]=Rect(margin,y,width-margin,y+h)
            v.measure(MeasureSpec.makeMeasureSpec((width-2*margin).coerceAtLeast(1),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY))
            if(v is TextView)v.setTextSize(TypedValue.COMPLEX_UNIT_SP,16f)
            y+=h+dp(10)
        }
        anchors.firstOrNull{it.tag=="universe-back"}?.let{it.isFocusableInTouchMode=false;row(it,dp(48))}
        header.forEach{row(it,dp(32))}
        slots.keys.filterIsInstance<androidx.media3.ui.PlayerView>().forEach{row(it,((width-2*margin)*9/16))}
        anchors.filter{it.tag!="universe-back"}.forEach{v->
            if(v is TextView && v.text.isBlank()){
                val b=slots[v]!!
                val label=slots.entries.filter{(child,p)->child is TextView && child!==v && child.text.isNotBlank() && p.x>=b.x && p.y>=b.y && p.x+p.w<=b.x+b.w+20 && p.y+p.h<=b.y+b.h+20}.maxByOrNull{it.value.y}?.key as? TextView
                v.text=v.contentDescription?.takeIf{it.isNotBlank()}?:label?.text?:"Open"
            }
            v.isFocusableInTouchMode=false
            row(v,dp(56))
        }
        slots.keys.filterIsInstance<TextView>().filter{
            !it.isClickable && it !in header && it.text.length>100 && (slots[it]?.h?:0)>100
        }.take(2).forEach{v->v.maxLines=8;row(v,dp(160))}
        if(anchors.isEmpty())slots.keys.filterIsInstance<TextView>().filter{it !in header && it.text.isNotBlank()}.take(8).forEach{row(it,dp(56))}
        phoneHeight=y+dp(16)
        scrollTo(0,scrollY.coerceIn(0,(phoneHeight-height).coerceAtLeast(0)))
        setMeasuredDimension(width,height)
    }
    override fun onInterceptTouchEvent(e:MotionEvent):Boolean{
        if(!PhoneInput.enabled(context)||!phoneReflow)return super.onInterceptTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{touchStartY=e.y;lastTouchY=e.y;dragging=false}
            MotionEvent.ACTION_MOVE->if(kotlin.math.abs(e.y-touchStartY)>ViewConfiguration.get(context).scaledTouchSlop){dragging=true;return true}
        }
        return dragging
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(!PhoneInput.enabled(context)||!phoneReflow)return super.onTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{lastTouchY=e.y;return true}
            MotionEvent.ACTION_MOVE->{scrollTo(0,(scrollY+(lastTouchY-e.y).toInt()).coerceIn(0,(phoneHeight-height).coerceAtLeast(0)));lastTouchY=e.y}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{dragging=false}
        }
        return true
    }
    override fun onViewRemoved(child:View){super.onViewRemoved(child);slots.remove(child);if(childCount==0)scrollTo(0,0)}
    override fun onDraw(c:Canvas) {
        if(transparent)return
        if(theme=="harak"){c.drawColor(Harak.INK);return}
        if(theme=="red"){c.drawColor(Color.BLACK);return}
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        val base=when(theme){"gold"->0xff100f0a.toInt();"royal"->0xff061d55.toInt();else->0xff020b18.toInt()}
        c.drawColor(base)
        val center=if(theme=="gold")0xff332e17.toInt() else if(theme=="royal")0xff073d85.toInt() else 0xff0a2442.toInt()
        p.shader=RadialGradient(width*.50f,height*.35f,width*.63f,intArrayOf(center,base),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);p.shader=null
        c.save();c.scale(width/1920f,height/1080f)
        if(!waves) {
            p.style=Paint.Style.STROKE;p.strokeWidth=18f
            for(i in 0..12){p.color=if(theme=="gold")0x103f3718 else 0x08477db7;c.drawOval(960f-180-i*90,370f-120-i*63,960f+180+i*90,370f+120+i*63,p)}
        } else {
            p.style=Paint.Style.STROKE
            for(i in 0..40) {
                p.strokeWidth=2f;p.color=if(theme=="gold")0x305b4c18 else 0x254d9edc
                val path=Path().apply{moveTo(-100f,400f+i*8);cubicTo(450f,1350f+i*3,1150f,420f+i*9,2050f,1050f+i*3)}
                c.drawPath(path,p)
            }
        }
        c.restore()
    }
}

fun surface(color:Int,stroke:Int?=null): GradientDrawable = GradientDrawable().apply {
    setColor(color); if(stroke!=null) setStroke(2,stroke)
}

/** Original orbit/play monogram. No Universe/IBO artwork. */
class AlaaMark(context:Context,var tint:Int=0xffb8d9ff.toInt()): View(context) {

    override fun onDraw(c:Canvas) {
        val s=minOf(width,height).toFloat();c.save();c.translate((width-s)/2,(height-s)/2);c.scale(s/160,s/160)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=tint;style=Paint.Style.STROKE;strokeWidth=3f}
        c.save();c.rotate(-25f,80f,80f);c.drawOval(10f,49f,150f,111f,p);c.restore()
        c.drawCircle(80f,80f,48f,p)
        p.style=Paint.Style.FILL
        val path=Path().apply{moveTo(69f,55f);lineTo(69f,105f);lineTo(105f,80f);close()}
        c.drawPath(path,p);c.drawCircle(139f,51f,6f,p);c.restore()
    }
}
