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

class PrayerSymbol(c:Context,val index:Int):View(c){
 override fun onDraw(c:Canvas){
  c.save();c.scale(width/64f,height/40f)
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xffffdc8a.toInt();style=Paint.Style.STROKE;strokeWidth=2f}
  if(index==4){c.drawArc(20f,3f,45f,31f,45f,285f,false,p);c.drawArc(26f,0f,47f,25f,70f,170f,false,p)}
  else{
   c.drawCircle(30f,20f,9f,p)
   for(i in 0..7){val a=i*Math.PI/4; c.drawLine(30f+14*kotlin.math.cos(a).toFloat(),20f+14*kotlin.math.sin(a).toFloat(),30f+18*kotlin.math.cos(a).toFloat(),20f+18*kotlin.math.sin(a).toFloat(),p)}
   if(index==0||index==3){p.color=0xffbcecff.toInt();c.drawLine(7f,27f,55f,27f,p);c.drawLine(15f,33f,49f,33f,p)}
   if(index==2){p.color=0xffd5ecff.toInt();p.style=Paint.Style.FILL;c.drawOval(24f,20f,59f,34f,p);c.drawCircle(37f,22f,9f,p)}
  };c.restore()
 }
}
class MirrorTile(c:Context,val icon:Int,val label:String,val tint:Int):View(c){
 override fun onDraw(c:Canvas){
  val layer=c.saveLayer(0f,0f,width.toFloat(),height.toFloat(),null)
  c.save();c.translate(0f,height.toFloat());c.scale(1f,-.55f)
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=(tint and 0xffffff) or 0x66000000}
  c.drawRoundRect(2f,0f,width-2f,296f,24f,24f,p)
  p.color=tint;p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawRoundRect(2f,0f,width-2f,296f,24f,24f,p)
  val d=context.getDrawable(icon)!!;d.setTint(Color.WHITE);d.setBounds(width/2-54,45,width/2+54,153);d.draw(c)
  p.style=Paint.Style.FILL;p.color=Color.WHITE;p.textSize=27f;p.textAlign=Paint.Align.CENTER;c.drawText(label,width/2f,245f,p)
  c.restore()
  p.shader=LinearGradient(0f,0f,0f,height.toFloat(),intArrayOf(0x00ffffff,0x99ffffff.toInt()),null,Shader.TileMode.CLAMP)
  p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
  c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);c.restoreToCount(layer)
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
