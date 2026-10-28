package com.alaa.universe
import android.content.Context
import android.graphics.*
import android.view.View
class PrayerBackdrop(c:Context):View(c) {
 private val bitmap=BitmapFactory.decodeResource(resources,R.drawable.prayer_scenery)
 override fun onDraw(canvas:Canvas){
  canvas.drawBitmap(bitmap,null,Rect(0,0,width,height),null)
  val p=Paint(Paint.ANTI_ALIAS_FLAG)
  p.shader=LinearGradient(0f,0f,0f,height.toFloat(),intArrayOf(0x5500081a,0x00000000,0x88000616.toInt()),null,Shader.TileMode.CLAMP)
  canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
 }
}
class MosqueArt(c:Context):View(c){
 override fun onDraw(c:Canvas){
  c.save();c.scale(width/220f,height/140f)
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xffe8c88a.toInt()}
  c.drawRect(51f,78f,171f,130f,p);c.drawArc(61f,35f,161f,115f,180f,180f,true,p)
  c.drawRect(106f,22f,111f,51f,p)
  for(x in listOf(25f,186f)){c.drawRect(x,49f,x+9,130f,p);c.drawOval(x-4,40f,x+13,53f,p);c.drawRect(x+3,16f,x+6,42f,p)}
  p.color=0xff18243a.toInt()
  c.drawRoundRect(97f,92f,124f,133f,13f,13f,p)
  c.restore()
 }
}

/** Decorative light only; never consumes D-pad focus. */
class TileLight(c:Context,val tint:Int):View(c){
 override fun onDraw(c:Canvas){
  val p=Paint(Paint.ANTI_ALIAS_FLAG)
  p.shader=RadialGradient(width*.5f,height*.5f,width*.5f,intArrayOf((tint and 0xffffff) or 0x88000000.toInt(),tint and 0xffffff),null,Shader.TileMode.CLAMP)
  c.drawOval(0f,0f,width.toFloat(),height.toFloat(),p)
  p.shader=LinearGradient(0f,0f,0f,height.toFloat(),intArrayOf((tint and 0xffffff) or 0x44000000,0),null,Shader.TileMode.CLAMP)
  c.drawRoundRect(width*.16f,0f,width*.84f,height.toFloat(),15f,15f,p)
 }
}
