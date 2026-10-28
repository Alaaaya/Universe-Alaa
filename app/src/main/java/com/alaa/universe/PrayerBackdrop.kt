package com.alaa.universe
import android.content.Context
import android.graphics.*
import android.view.View
class PrayerBackdrop(c:Context):View(c) {
 private val bitmap=BitmapFactory.decodeResource(resources,R.drawable.prayer_scenery)
 override fun onDraw(canvas:Canvas){
  val photoPaint=Paint(Paint.ANTI_ALIAS_FLAG)
  val saturation=ColorMatrix().apply{setSaturation(1.3f)}
  val contrast=ColorMatrix(floatArrayOf(1.12f,0f,0f,0f,-13f,0f,1.12f,0f,0f,-13f,0f,0f,1.12f,0f,-13f,0f,0f,0f,1f,0f))
  saturation.postConcat(contrast);photoPaint.colorFilter=ColorMatrixColorFilter(saturation)
  canvas.drawBitmap(bitmap,null,Rect(0,0,width,height),photoPaint)
  val p=Paint(Paint.ANTI_ALIAS_FLAG)
  p.shader=LinearGradient(0f,0f,0f,height.toFloat(),intArrayOf(0x5500081a,0x00000000,0x88000616.toInt()),null,Shader.TileMode.CLAMP)
  canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
  // The wallpaper itself contains colored patio lights. Mask the entire floor,
  // not just UI reflection layers, so nothing below the tiles resembles a mirror.
  p.shader=LinearGradient(0f,height*.72f,0f,height*.795f,intArrayOf(0x00000000,Color.BLACK),null,Shader.TileMode.CLAMP)
  canvas.drawRect(0f,height*.72f,width.toFloat(),height.toFloat(),p)
 }
}
class MosqueArt(c:Context):View(c){
 override fun onDraw(c:Canvas){
  c.save();c.scale(width/220f,height/140f)
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xffe8c88a.toInt()}
  p.setShadowLayer(9f,0f,0f,0xffffc75b.toInt())
  p.shader=LinearGradient(0f,20f,0f,140f,intArrayOf(0xffffe8a9.toInt(),0xffb98c44.toInt(),0xffffd997.toInt()),null,Shader.TileMode.CLAMP)
  c.drawRect(51f,78f,171f,130f,p);c.drawArc(61f,35f,161f,115f,180f,180f,true,p)
  c.drawRect(106f,22f,111f,51f,p)
  for(x in listOf(25f,186f)){c.drawRect(x,49f,x+9,130f,p);c.drawOval(x-4,40f,x+13,53f,p);c.drawRect(x+3,16f,x+6,42f,p)}
  p.shader=null;p.clearShadowLayer();p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=0xffffe2a1.toInt()
  for(y in listOf(80f,90f,101f,115f,128f))c.drawLine(48f,y,175f,y,p)
  c.drawCircle(109f,16f,6f,p)
  for(x in listOf(26f,188f)){c.drawCircle(x+3,11f,5f,p);c.drawLine(x-4,62f,x+14,62f,p);c.drawLine(x-4,88f,x+14,88f,p)}
  p.style=Paint.Style.FILL;p.color=0xff18243a.toInt()
  for(x in listOf(62f,78f,136f,153f))c.drawRoundRect(x,94f,x+9,111f,5f,5f,p)
  p.color=0xff18243a.toInt()
  c.drawRoundRect(97f,92f,124f,133f,13f,13f,p)
  c.restore()
 }
}

class PrayerSymbol(c:Context,val index:Int):View(c){
 override fun onDraw(c:Canvas){
  c.save();c.scale(width/64f,height/40f)
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xffffe6a5.toInt();style=Paint.Style.STROKE;strokeWidth=2.4f;setShadowLayer(6f,0f,0f,0xffffbc42.toInt())}
  if(index==4){c.drawArc(20f,3f,45f,31f,45f,285f,false,p);c.drawArc(26f,0f,47f,25f,70f,170f,false,p)}
  else{
   c.drawCircle(30f,20f,9f,p)
   for(i in 0..7){val a=i*Math.PI/4; c.drawLine(30f+14*kotlin.math.cos(a).toFloat(),20f+14*kotlin.math.sin(a).toFloat(),30f+18*kotlin.math.cos(a).toFloat(),20f+18*kotlin.math.sin(a).toFloat(),p)}
   if(index==0||index==3){p.color=0xffbcecff.toInt();c.drawLine(7f,27f,55f,27f,p);c.drawLine(15f,33f,49f,33f,p)}
   if(index==2){p.color=0xffd5ecff.toInt();p.style=Paint.Style.FILL;c.drawOval(24f,20f,59f,34f,p);c.drawCircle(37f,22f,9f,p)}
  };c.restore()
 }
}
class NeonRim(c:Context,val tint:Int):View(c){
 var active=false
 override fun onDraw(c:Canvas){if(!active)return
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE}
  for(i in 12 downTo 1){p.color=(tint and 0xffffff) or ((18-i)*3 shl 24);p.strokeWidth=i*2f;c.drawRoundRect(17f,17f,width-17f,height-17f,27f,27f,p)}
  p.color=tint;p.strokeWidth=3f;c.drawRoundRect(17f,17f,width-17f,height-17f,27f,27f,p)
 }
}

class GlassContour(c:Context,val tint:Int,val radius:Float=32f):View(c){
 override fun onDraw(c:Canvas){
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE}
  for(i in 12 downTo 1){p.color=(tint and 0xffffff) or ((16-i)*4 shl 24);p.strokeWidth=i*1.7f
   c.drawRoundRect(15f,15f,width-15f,height-15f,radius,radius,p)}
  p.color=tint;p.strokeWidth=2f;c.drawRoundRect(15f,15f,width-15f,height-15f,radius,radius,p)
  p.color=0xaae5faff.toInt();p.strokeWidth=1f;c.drawRoundRect(17f,17f,width-17f,height-17f,radius-2,radius-2,p)
 }
}
