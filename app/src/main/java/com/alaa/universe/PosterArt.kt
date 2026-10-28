package com.alaa.universe
import android.content.Context
import android.graphics.*
import android.view.View
/** Original procedural poster illustrations, fixture catalogue only. */
class PosterArt(c:Context,val index:Int,val title:String):View(c) {
 override fun onDraw(canvas:Canvas) {
  val p=Paint(Paint.ANTI_ALIAS_FLAG)
  val colors=intArrayOf(0xff263f4b.toInt(),0xff613e2b.toInt(),0xff213c39.toInt(),0xff34324e.toInt(),0xff6a552d.toInt())
  p.shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),colors[index%5],0xff080b11.toInt(),Shader.TileMode.CLAMP)
  canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);p.shader=null
  p.color=0x55e6c88a;p.style=Paint.Style.STROKE;p.strokeWidth=width*.006f
  for(i in 0..6){val r=width*(.2f+i*.15f);canvas.drawCircle(width*.65f,height*.38f,r,p)}
  p.style=Paint.Style.FILL;p.color=0xffd6ba7d.toInt()
  canvas.drawCircle(width*.65f,height*.28f,width*.12f,p)
  p.color=0xff0b1318.toInt()
  val path=Path().apply{moveTo(0f,height*.72f);lineTo(width*.28f,height*.42f);lineTo(width*.55f,height*.66f);lineTo(width*.8f,height*.48f);lineTo(width.toFloat(),height*.65f);lineTo(width.toFloat(),height.toFloat());lineTo(0f,height.toFloat());close()}
  canvas.drawPath(path,p)
  p.color=Color.WHITE;p.textSize=width*.09f;p.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
  var y=height*.72f
  for(word in title.uppercase().split(" ")){canvas.drawText(word,width*.08f,y,p);y+=height*.075f}
 }
}
