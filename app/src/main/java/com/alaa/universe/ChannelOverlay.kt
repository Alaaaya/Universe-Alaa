package com.alaa.universe

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.view.*
import android.widget.*

/** Bounded current-category window. Never rebuilds or downloads the provider list. */
class ChannelOverlay(context:Context,private val channels:List<MediaEntry>,private val playing:String,
                     private val accent:Int,private val choose:(MediaEntry)->Unit,
                     private val close:()->Unit,
                     private val atlasLogo:((MediaEntry,ImageView)->Unit)?=null):FrameLayout(context) {
    private val stage=Stage(context).apply{transparent=true}
    private var at=channels.indexOfFirst{it.id==playing}.coerceAtLeast(0)
    private var releasedKey:Int?=null
    private val gold=0xffe9bd70.toInt()
    private val white=0xfff7f1e7.toInt()
    private val divider=0xff494137.toInt()
    private val atlasPage=10
    private var atlasStart=(at-4).coerceIn(0,(channels.size-atlasPage).coerceAtLeast(0))
    init {addView(stage,LayoutParams(-1,-1));render()}
    private fun render(){
        stage.removeAllViews()
        val logo=atlasLogo
        if(logo!=null){renderAtlas(logo);return}
        stage.put(View(context).apply{setBackgroundColor(0xe6101018.toInt())},0,0,660,1080)
        val back=BackButton.add(stage,accent,close)
        Harak.label(stage,"القنوات",120,24,480,72,32f)
        val start=(at-4).coerceIn(0,(channels.size-9).coerceAtLeast(0))
        stage.swipe={_,_,_,dy->at=(at+if(dy<0)9 else -9).coerceIn(0,channels.lastIndex.coerceAtLeast(0));render()}
        BrowseIndex.page(channels,start,9).forEachIndexed{i,e->
            val y=126+i*98
            val row=Harak.button(stage,e.name,28,y,602,86,26f){choose(e)}
            row.contentDescription="Channel ${e.name}"
            if(i+start==at){row.background=Harak.box(focus=true);row.requestFocus();back.nextFocusDownId=row.id;row.nextFocusUpId=back.id}
        }
    }
    private fun renderAtlas(bindLogo:(MediaEntry,ImageView)->Unit){
        Atlas.block(stage,30,22,560,798,0xed161416.toInt(),24f,divider)
        val back=BackButton.add(stage,gold,close)
        stage.place(back,44,32,55,55)
        Atlas.label(stage,"جميع القنوات",126,32,370,55,32f,gold,true).gravity=Gravity.CENTER
        Atlas.icon(stage,R.drawable.ic_live_tv,528,42,36,gold)
        Atlas.block(stage,49,101,521,1,divider)
        stage.swipe={x,y,dx,dy->
            if(x in 30f..590f && y in 112f..805f && kotlin.math.abs(dy)>kotlin.math.abs(dx)) {
                val next=(atlasStart+if(dy<0)atlasPage else -atlasPage).coerceIn(0,(channels.size-atlasPage).coerceAtLeast(0))
                if(next!=atlasStart){atlasStart=next;at=next;render()}
            }
        }
        BrowseIndex.page(channels,atlasStart,atlasPage).forEachIndexed{i,entry->
            val index=atlasStart+i
            val number=(index+1).toString().padStart(2,'0')
            val y=112+i*69
            val onAir=entry.id==playing
            val row=Atlas.label(stage,entry.name,49,y,521,63,26f,white,true,right=false).apply {
                id=View.generateViewId();tag="atlas-drawer-row-$index"
                contentDescription="$number، ${entry.name}"
                isFocusable=true;isFocusableInTouchMode=true;isClickable=true
                background=StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused),Atlas.shape(0x7f554021,17f,gold,3))
                    addState(intArrayOf(android.R.attr.state_pressed),Atlas.shape(0x7f554021,17f,gold,3))
                    addState(intArrayOf(),Atlas.shape(if(onAir)0x523c2e20 else 0x00161416,17f))
                }
                addOnLayoutChangeListener { v,_,t,_,b,_,ot,_,ob ->
                    if(b-t!=ob-ot){val scale=(b-t)/63f;v.setPadding((155*scale).toInt(),0,((if(onAir)58 else 14)*scale).toInt(),0)}
                }
                setOnClickListener{choose(entry)}
            }
            Atlas.label(stage,number,57,y,66,63,25f,gold,true,right=false).apply {
                gravity=Gravity.CENTER;tag="atlas-drawer-number-$index";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val logo=ImageView(context).apply {
                tag="atlas-drawer-logo-$index";scaleType=ImageView.ScaleType.FIT_CENTER
                background=Atlas.shape(0xff282427.toInt(),10f,divider)
                addOnLayoutChangeListener { v,_,t,_,b,_,ot,_,ob -> if(b-t!=ob-ot){val pad=(b-t)/9;v.setPadding(pad,pad,pad,pad)} }
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            stage.put(logo,134,y+8,47,47);bindLogo(entry,logo)
            Atlas.block(stage,194,y+13,1,37,divider)
            if(onAir){
                // Indicates the current channel, not measured signal strength.
                listOf(11,17,25,32).forEachIndexed{bar,height->Atlas.block(stage,524+bar*7,y+46-height,4,height,gold,2f)}
            }
            if(i<atlasPage-1)Atlas.block(stage,58,y+66,502,1,divider)
            if(index==at){row.requestFocus();back.nextFocusDownId=row.id;row.nextFocusUpId=back.id}
        }
    }
    private fun selectAtlas(index:Int){
        at=index
        if(at<atlasStart)atlasStart=at
        else if(at>=atlasStart+atlasPage)atlasStart=at-atlasPage+1
        val visible=stage.findViewWithTag<View>("atlas-drawer-row-$at")
        if(visible!=null){visible.requestFocus();stage.findViewWithTag<View>("universe-back").nextFocusDownId=visible.id}
        else render()
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
            else if(findFocus()?.contentDescription=="Back"){if(atlasLogo==null)render() else selectAtlas(at)}
            else if(atlasLogo!=null) selectAtlas(Math.floorMod(at+if(e.keyCode==KeyEvent.KEYCODE_DPAD_DOWN)1 else -1,channels.size.coerceAtLeast(1)))
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
