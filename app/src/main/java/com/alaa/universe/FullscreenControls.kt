package com.alaa.universe

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.media3.common.C

/** Native TV controls on a transparent design surface. Video remains the only backdrop. */
class FullscreenControls(
    context:Context, val session:PlaybackSession, val item:MediaEntry, val accent:Int,
    subtitle:String, favorite:Boolean, private val leave:()->Unit,
    private val previous:()->Unit, private val next:()->Unit, private val toggleFavorite:()->Boolean,
    artwork:(ImageView)->Unit
):FrameLayout(context) {
    val stage=Stage(context).apply{transparent=true}
    private val main=Handler(Looper.getMainLooper())
    private val hide=Runnable{visibility=View.INVISIBLE;clearFocus()}
    private val tick=object:Runnable{override fun run(){update();main.postDelayed(this,500)}}
    private val buttons=mutableListOf<View>()
    private var attached=false
    val live=item.kind=="live"
    private lateinit var pause:Control
    private lateinit var heart:Control
    private lateinit var elapsed:TextView
    private lateinit var total:TextView
    private lateinit var remaining:TextView
    private lateinit var percent:TextView
    private lateinit var progress:Progress
    private var isFavorite=favorite
    init {
        layoutDirection=View.LAYOUT_DIRECTION_LTR
        addView(stage,LayoutParams(-1,-1))
        stage.put(View(context).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xaa000000.toInt(),0x00000000,0x00000000,0xee000000.toInt()))},0,0,1920,1080)
        val back=control("Back","back",42,43,74){leave()}
        stage.put(View(context).apply{background=rounded(0xad121820.toInt(),22f)},130,28,1120,108)
        stage.put(TextView(context).apply{text=item.name.trim().split(Regex("\\s+")).take(2).joinToString(""){it.take(1)}.uppercase();gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(0x66323b45,14f)},146,42,78,78,28f)
        val image=ImageView(context).apply{scaleType=ImageView.ScaleType.FIT_CENTER;clipToOutline=true;background=rounded(Color.TRANSPARENT,12f)}
        stage.put(image,146,42,78,78);artwork(image)
        label(item.name,244,43,978,43,33f).setTypeface(null,Typeface.BOLD)
        label(subtitle,244,91,978,28,23f).setTextColor(0xffc4cbd3.toInt())
        label(if(live)"LIVE" else if(item.kind=="episode")"EPISODE" else "MOVIE",1725,44,135,42,22f).apply{gravity=Gravity.CENTER;background=rounded(accent,22f);setTypeface(null,Typeface.BOLD)}
        elapsed=label(if(live)"ON AIR" else "Elapsed",90,888,155,28,21f).apply{setTextColor(0xffbcc3cb.toInt())}
        val time=label(if(live)"● LIVE" else "00:00",90,921,170,40,30f)
        time.tag="elapsed-time"
        progress=Progress(context,accent).apply{
            isFocusable=!live;isClickable=!live;contentDescription="Seek position"
            setOnFocusChangeListener{_,_->invalidate();show()}
            setOnKeyListener{_,key,e->
                if(e.action==KeyEvent.ACTION_DOWN && (key==21 || key==22)){if(key==21)session.player?.seekBack() else session.player?.seekForward();show();update();true}else false
            }
        }
        stage.put(progress,278,884,1394,35)
        percent=label("",278,930,200,30,22f)
        total=label("",1370,930,302,30,22f).apply{gravity=Gravity.RIGHT}
        label(if(live)"Broadcast" else "Time left",1730,873,160,30,21f).setTextColor(0xffbcc3cb.toInt())
        remaining=label(if(live)"Real time" else "—",1730,914,170,44,28f)
        val specs=listOf("Previous" to "prev","Rewind 10 seconds" to "rewind","Play or pause" to "pause","Forward 10 seconds" to "forward","Next" to "next","Audio" to "audio","Subtitles" to "subs","Favorite" to "heart")
        specs.forEachIndexed{i,(name,icon)->
            val size=if(i==2)92 else 74
            val x=listOf(608,696,784,890,978,1066,1154,1242)[i]
            val b=control(name,icon,x,if(i==2)969 else 978,size){
                when(i){
                    0->previous()
                    1->session.player?.seekBack()
                    2->session.player?.let{if(it.isPlaying)it.pause() else it.play()}
                    3->session.player?.seekForward()
                    4->next()
                    5->session.tracks(C.TRACK_TYPE_AUDIO)
                    6->session.tracks(C.TRACK_TYPE_TEXT)
                    7->{isFavorite=toggleFavorite();heart.selectedIcon=isFavorite}
                };show();update()
            }
            if(i==2){pause=b;b.primary=true}
            if(i==7){heart=b;b.selectedIcon=isFavorite}
            if(live && (i==1||i==3)){b.isEnabled=false;b.isFocusable=false;b.alpha=.28f}
        }
        val row=buttons.drop(1).filter{it.isFocusable}
        row.forEachIndexed{i,v->
            v.nextFocusLeftId=row.getOrNull(i-1)?.id?:v.id
            v.nextFocusRightId=row.getOrNull(i+1)?.id?:v.id
            v.nextFocusUpId=if(live)back.id else progress.id
            v.nextFocusDownId=v.id
        }
        progress.id=View.generateViewId();row.forEach{it.nextFocusUpId=if(live)back.id else progress.id}
        progress.nextFocusDownId=pause.id;progress.nextFocusUpId=back.id;back.nextFocusDownId=if(live)pause.id else progress.id
        progress.nextFocusLeftId=progress.id;progress.nextFocusRightId=progress.id
        update()
    }
    private fun rounded(color:Int,r:Float)=GradientDrawable().apply{setColor(color);cornerRadius=r}
    private fun label(s:String,x:Int,y:Int,w:Int,h:Int,size:Float)=TextView(context).apply{
        text=s;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;includeFontPadding=false;maxLines=1
        ellipsize=android.text.TextUtils.TruncateAt.END
        stage.put(this,x,y,w,h,size)
    }
    private fun control(name:String,icon:String,x:Int,y:Int,size:Int,action:()->Unit):Control {
        val b=Control(context,icon,accent).apply{
            id=View.generateViewId();contentDescription=name;isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            setOnClickListener{action()};setOnFocusChangeListener{_,_->invalidate();show()}
        };stage.put(b,x,y,size,size);buttons.add(b);return b
    }
    fun show(){visibility=View.VISIBLE;main.removeCallbacks(hide);if(attached)main.postDelayed(hide,4000)}
    fun reveal():Boolean {
        if(visibility==View.VISIBLE){show();return false}
        show();pause.requestFocus();return true
    }
    fun initialFocus(){show();pause.requestFocus()}
    fun update(){
        val p=session.player
        val duration=p?.duration?.takeIf{it>0 && it!=C.TIME_UNSET}?:0L
        val position=(p?.currentPosition?:0L).coerceAtLeast(0)
        val known=duration>0
        progress.fraction=if(known)(position.toFloat()/duration).coerceIn(0f,1f) else 0f
        progress.visibility=if(live)View.INVISIBLE else View.VISIBLE
        progress.isEnabled=known && !live
        progress.isFocusable=known && !live
        (stage.findViewWithTag<TextView>("elapsed-time")).text=if(live)"● LIVE" else clock(position)
        percent.text=if(live)"Live broadcast" else if(known)"${(progress.fraction*100).toInt()}%" else "Duration unavailable"
        total.text=if(!live&&known)"Total  ${clock(duration)}" else ""
        remaining.text=if(live)"Real time" else if(known)"−${clock((duration-position).coerceAtLeast(0))}" else "—"
        pause.icon=if(p?.isPlaying==false)"play" else "pause";pause.invalidate()
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();attached=true;main.post(tick);show()}
    override fun onDetachedFromWindow(){attached=false;main.removeCallbacksAndMessages(null);super.onDetachedFromWindow()}
    companion object { fun clock(ms:Long):String {val s=ms.coerceAtLeast(0)/1000;return if(s>=3600)"%d:%02d:%02d".format(s/3600,(s/60)%60,s%60) else "%02d:%02d".format(s/60,s%60)} }
    class Progress(context:Context,private val accent:Int):View(context){
        var fraction=0f;set(v){field=v;invalidate()}
        override fun onDraw(c:Canvas){val p=Paint(3);val y=height/2f;val pad=height*.3f;val end=pad+(width-pad*2)*fraction
            p.strokeWidth=if(hasFocus())8f else 5f;p.strokeCap=Paint.Cap.ROUND;p.color=0xff69717c.toInt();c.drawLine(pad,y,width-pad,y,p)
            p.color=accent;c.drawLine(pad,y,end,y,p);p.color=Color.WHITE;c.drawCircle(end,y,if(hasFocus())height*.36f else height*.26f,p)
            if(hasFocus()){p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawRoundRect(1f,1f,width-1f,height-1f,12f,12f,p)}
        }
    }
    class Control(context:Context,var icon:String,private val accent:Int):View(context){
        var primary=false
        var selectedIcon=false;set(v){field=v;invalidate()}
        override fun onDraw(c:Canvas){
            val s=minOf(width,height).toFloat();c.save();c.translate((width-s)/2,(height-s)/2);c.scale(s/80,s/80)
            val p=Paint(3).apply{color=if(primary)accent else 0xb5222932.toInt()};c.drawCircle(40f,40f,36f,p)
            if(hasFocus()){p.color=Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=3f;c.drawCircle(40f,40f,39f,p)}
            p.color=if(selectedIcon)accent else Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=2.8f;p.strokeJoin=Paint.Join.ROUND;p.strokeCap=Paint.Cap.ROUND
            fun line(x:Float,y:Float,a:Float,b:Float)=c.drawLine(x,y,a,b,p)
            fun triangle(x:Float,reverse:Boolean=false){val d=if(reverse)-1 else 1;val path=Path().apply{moveTo(x-6*d,29f);lineTo(x+9*d,40f);lineTo(x-6*d,51f);close()};p.style=Paint.Style.FILL;c.drawPath(path,p);p.style=Paint.Style.STROKE}
            when(icon){
                "pause"->{p.style=Paint.Style.FILL;c.drawRoundRect(30f,27f,36f,53f,1f,1f,p);c.drawRoundRect(44f,27f,50f,53f,1f,1f,p)}
                "play"->triangle(40f)
                "prev"->{line(27f,28f,27f,52f);triangle(43f,true)}
                "next"->{triangle(37f);line(53f,28f,53f,52f)}
                "rewind"->{triangle(32f,true);triangle(48f,true)}
                "forward"->{triangle(32f);triangle(48f)}
                "back"->{line(45f,27f,32f,40f);line(32f,40f,45f,53f)}
                "audio"->{val path=Path().apply{moveTo(26f,35f);lineTo(33f,35f);lineTo(42f,27f);lineTo(42f,53f);lineTo(33f,45f);lineTo(26f,45f);close()};c.drawPath(path,p);c.drawArc(39f,28f,57f,52f,-55f,110f,false,p);c.drawArc(43f,22f,65f,58f,-55f,110f,false,p)}
                "subs"->{c.drawRoundRect(23f,28f,57f,52f,4f,4f,p);line(29f,37f,35f,37f);line(42f,37f,51f,37f);line(29f,44f,42f,44f);line(47f,44f,51f,44f)}
                "heart"->{val path=Path().apply{moveTo(40f,54f);cubicTo(12f,35f,29f,18f,40f,31f);cubicTo(51f,18f,68f,35f,40f,54f)};if(selectedIcon)p.style=Paint.Style.FILL;c.drawPath(path,p)}
            };c.restore()
        }
    }
}
