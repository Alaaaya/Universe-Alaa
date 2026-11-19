package com.alaa.universe

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.content.res.ColorStateList
import android.view.*
import android.widget.*

/** A shared native dialog skin. Platform dialog behavior, our surfaces and focus system. */
object PremiumDialogs {
    fun accent(c:Context):Int=when(c.getSharedPreferences("appearance",0).getString("theme","red")){
        "harak"->Harak.GOLD;"gold"->0xffd8b602.toInt();"blue","royal"->0xff518df4.toInt();else->0xffed3446.toInt()
    }
    fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
    fun surface(c:Context,color:Int,stroke:Int=0x334f6077):GradientDrawable=GradientDrawable().apply{
        cornerRadius=dp(c,18).toFloat();setColor(color);setStroke(dp(c,1),stroke)
    }
    fun focus(c:Context,primary:Boolean=false,danger:Boolean=false)=StateListDrawable().apply{
        val a=if(danger)0xffff747e.toInt() else accent(c)
        addState(intArrayOf(android.R.attr.state_focused),surface(c,if(primary)a else 0xff29364a.toInt(),Color.WHITE))
        addState(intArrayOf(android.R.attr.state_pressed),surface(c,0xff34435a.toInt(),a))
        addState(intArrayOf(),surface(c,if(primary)(a and 0x00ffffff or 0xff000000.toInt()) else 0xff202c3d.toInt(),if(primary||danger)a else 0x334f6077))
    }
    fun text(c:Context,value:String,size:Float=14f)=TextView(c).apply{
        text=value;textSize=size;setTextColor(0xffc4cedf.toInt());textDirection=View.TEXT_DIRECTION_FIRST_STRONG
        setPadding(0,dp(c,8),0,dp(c,8))
    }
    fun button(c:Context,label:String,primary:Boolean=false,danger:Boolean=false,action:()->Unit)=Button(c).apply{
        text=label;isAllCaps=false;textSize=14f;setTextColor(if(danger)0xffff929b.toInt() else if(primary)0xff090e17.toInt() else Color.WHITE)
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);background=focus(c,primary,danger)
        minimumHeight=dp(c,48);minHeight=dp(c,48);isFocusable=true;setPadding(dp(c,16),0,dp(c,16),0)
        setOnClickListener{action()}
    }
    class Builder(private val c:Context):AlertDialog.Builder(c,android.R.style.Theme_Material_NoActionBar){
        override fun create():AlertDialog=super.create().also{d->d.setOnShowListener{style(d)}}
        override fun show():AlertDialog=create().also{it.show()}
    }
    fun builder(c:Context)=Builder(c)
    fun style(d:AlertDialog){
        val c=d.context
        d.window?.apply{
            setBackgroundDrawable(surface(c,0xff101722.toInt(),accent(c)))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);attributes=attributes.apply{dimAmount=.72f}
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(minOf(dp(c,660),(c.resources.displayMetrics.widthPixels*.94f).toInt()),WindowManager.LayoutParams.WRAP_CONTENT)
        }
        fun walk(v:View){
            if(v is TextView){
                if(v !is EditText && v !is Button)v.setTextColor(0xffe9edf6.toInt());v.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
                v.textDirection=View.TEXT_DIRECTION_FIRST_STRONG
                if(v !is EditText && v !is Button && v !is CheckBox)v.setLineSpacing(dp(c,2).toFloat(),1f)
            }
            if(v is ListView){v.selector=focus(c);v.divider=null;v.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8))}
            if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i))
        }
        walk(d.window!!.decorView)
        listOf(-1,-2,-3).forEach{which->d.getButton(which)?.let{b->
            val danger=b.text.toString().contains("Remove",true)
            b.isAllCaps=false;b.textSize=14f;b.background=focus(c,which == -1,danger)
            b.setTextColor(if(danger)0xffff929b.toInt() else if(which == -1)0xff090e17.toInt() else Color.WHITE)
            b.minHeight=dp(c,48);b.minimumHeight=dp(c,48);b.setPadding(dp(c,16),0,dp(c,16),0)
            (b.layoutParams as? ViewGroup.MarginLayoutParams)?.let{it.setMargins(dp(c,4),dp(c,8),dp(c,4),dp(c,8));b.layoutParams=it}
        }}
    }
    @Suppress("DEPRECATION")
    fun toast(c:Context,value:String,long:Boolean=false){
        val label=text(c,value,15f).apply{setTextColor(Color.WHITE);setPadding(dp(c,20),dp(c,12),dp(c,20),dp(c,12));background=surface(c,0xff182332.toInt(),accent(c))}
        Toast(c).apply{view=label;duration=if(long)Toast.LENGTH_LONG else Toast.LENGTH_SHORT;setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,0,dp(c,32))}.show()
    }
}
