package com.alaa.universe

import android.content.Context
import android.view.*
import android.widget.*

/** Bounded current-category window. Never rebuilds or downloads the provider list. */
class ChannelOverlay(context:Context,private val channels:List<MediaEntry>,playing:String,
                     private val accent:Int,private val choose:(MediaEntry)->Unit,
                     private val close:()->Unit):FrameLayout(context) {
    private val stage=Stage(context).apply{transparent=true}
    private var at=channels.indexOfFirst{it.id==playing}.coerceAtLeast(0)
    private var releasedKey:Int?=null
    init {addView(stage,LayoutParams(-1,-1));render()}
    private fun render(){
        stage.removeAllViews()
        stage.put(View(context).apply{setBackgroundColor(0xe6101018.toInt())},0,0,660,1080)
        val back=BackButton.add(stage,accent,close)
        Harak.label(stage,"القنوات",120,24,480,72,32f)
        val start=(at-4).coerceIn(0,(channels.size-9).coerceAtLeast(0))
        channels.drop(start).take(9).forEachIndexed{i,e->
            val row=Harak.button(stage,e.name,28,126+i*98,602,86,26f){choose(e)}
            row.contentDescription="Channel ${e.name}"
            if(i+start==at){row.background=Harak.box(focus=true);row.requestFocus();back.nextFocusDownId=row.id;row.nextFocusUpId=back.id}
        }
    }
    fun key(e:KeyEvent):Boolean {
        val ok=e.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER)
        if(e.keyCode==KeyEvent.KEYCODE_BACK){
            if(e.action==KeyEvent.ACTION_UP&&!e.isCanceled)close()
            return true
        }
        if(e.action==KeyEvent.ACTION_UP){
            if(releasedKey==e.keyCode){releasedKey=null;return true}
            if(ok){if(!e.isCanceled){if(findFocus()?.contentDescription=="Back")close() else channels.getOrNull(at)?.let(choose)};return true}
            return true
        }
        if(e.action!=KeyEvent.ACTION_DOWN)return true
        if(e.keyCode==KeyEvent.KEYCODE_DPAD_UP||e.keyCode==KeyEvent.KEYCODE_DPAD_DOWN){
            releasedKey=e.keyCode
            if(e.keyCode==KeyEvent.KEYCODE_DPAD_UP&&at==0){stage.findViewWithTag<View>("universe-back").requestFocus()}
            else if(findFocus()?.contentDescription=="Back"){render()}
            else {at=Math.floorMod(at+if(e.keyCode==KeyEvent.KEYCODE_DPAD_DOWN)1 else -1,channels.size.coerceAtLeast(1));render()}
        }
        return true
    }
}
object BackButton {
    fun add(stage:Stage,accent:Int,action:()->Unit):View {
        val v=FullscreenControls.Control(stage.context,"back",accent).apply{
            id=View.generateViewId();tag="universe-back";contentDescription="Back"
            isFocusable=true;isFocusableInTouchMode=true;isClickable=true
            setOnClickListener{action()}
            setOnFocusChangeListener{_,_->invalidate()}
        }
        stage.put(v,24,24,74,74);return v
    }
}
