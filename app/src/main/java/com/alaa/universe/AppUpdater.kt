package com.alaa.universe

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.ProgressBar
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AppUpdater(private val activity:Activity) {
    companion object {
        const val MANIFEST_URL="https://salaciously-comparably-discreet-hairtail.kitten.space/version.json"
        fun newer(json:String,current:Long):Boolean=JSONObject(json).getLong("versionCode")>current
    }
    private val worker=Executors.newSingleThreadExecutor{r->Thread(r,"universe-updater").apply{isDaemon=true}}
    @Volatile private var busy=false
    @Volatile private var closed=false
    private var pending:File?=null
    private fun ui(run:()->Unit){activity.runOnUiThread{if(!closed&&!activity.isFinishing)run()}}
    private fun connection(url:String):HttpURLConnection {
        require(URL(url).protocol=="https"){"HTTPS required"}
        return (URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=15000;readTimeout=30000;instanceFollowRedirects=false}
    }
    private fun error(message:String){ui{PremiumDialogs.builder(activity).setMessage(message).setPositiveButton("OK",null).show()}}
    fun check(quiet:Boolean=false){
        if(closed)return
        if(busy){if(!quiet)error("An update check or download is already running.");return}
        busy=true
        worker.execute{
            try{
                val c=connection(MANIFEST_URL)
                val text=try{require(c.responseCode==200);c.inputStream.use{input->val bytes=input.readNBytesCompat(131073);require(bytes.size<=131072);bytes.toString(Charsets.UTF_8)}}finally{c.disconnect()}
                val json=JSONObject(text)
                @Suppress("DEPRECATION")
                val current=activity.packageManager.getPackageInfo(activity.packageName,0).versionCode.toLong()
                busy=false
                if(newer(text,current))ui{
                    PremiumDialogs.builder(activity).setTitle("Update "+json.getString("versionName"))
                        .setMessage(json.optString("notes")).setNegativeButton("Later",null)
                        .setPositiveButton("Download"){_,_->download(json)}.show()
                }else if(!quiet)error("You're up to date.")
            }catch(e:Exception){if(!quiet)error("Could not check for updates. Please try again.")}
            finally{busy=false}
        }
    }
    private fun download(json:JSONObject){
        if(busy||closed)return
        busy=true
        val bar=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply{isIndeterminate=true;max=100}
        val dialog=PremiumDialogs.builder(activity).setTitle("Downloading update").setView(bar).setCancelable(false).create()
        dialog.show()
        worker.execute{
            val folder=File(activity.cacheDir,"updates").apply{mkdirs()}
            val part=File(folder,"update.part")
            val apk=File(folder,"universe.apk")
            try{
                val c=connection(json.getString("apkUrl"))
                try{
                    require(c.responseCode==200)
                    val total=c.getHeaderField("Content-Length")?.toLongOrNull()?:-1L
                    require(total<512L*1024*1024)
                    var count=0L
                    c.inputStream.use{input->part.outputStream().use{out->
                        val buffer=ByteArray(65536)
                        while(true){
                            if(closed)throw java.io.IOException("Closed")
                            val n=input.read(buffer);if(n<0)break
                            count+=n;require(count<=512L*1024*1024)
                            out.write(buffer,0,n)
                            if(total>0){val percent=(count*100/total).toInt();ui{bar.isIndeterminate=false;bar.progress=percent}}
                        }
                    }}
                    require(count>0 && (total<=0 || count==total))
                }finally{c.disconnect()}
                @Suppress("DEPRECATION")
                val flags=android.content.pm.PackageManager.GET_SIGNATURES
                @Suppress("DEPRECATION")
                val archive=activity.packageManager.getPackageArchiveInfo(part.path,flags)?:throw java.io.IOException("Invalid APK")
                @Suppress("DEPRECATION")
                val installed=activity.packageManager.getPackageInfo(activity.packageName,flags)
                @Suppress("DEPRECATION")
                require(archive.packageName==activity.packageName && archive.versionCode.toLong()==json.getLong("versionCode") &&
                    archive.versionCode>installed.versionCode && archive.signatures!!.map{it.toCharsString()}.toSet()==installed.signatures!!.map{it.toCharsString()}.toSet())
                require(part.renameTo(apk))
                pending=apk
                ui{dialog.dismiss();install()}
            }catch(e:Exception){part.delete();ui{dialog.dismiss()};error("Update download failed or could not be verified. Try again.")}
            finally{busy=false}
        }
    }
    private fun install(){
        val apk=pending?:return
        if(Build.VERSION.SDK_INT>=26&&!activity.packageManager.canRequestPackageInstalls()){
            PremiumDialogs.builder(activity).setMessage("Allow universe-Alaa to install updates on the next screen, then return here.")
                .setNegativeButton("Later",null).setPositiveButton("Open settings"){_,_->
                    try{activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.packageName)))}catch(e:Exception){error("Open Android Settings and allow unknown app installs for universe-Alaa.")}
                }.show()
            return
        }
        try{
            val uri=FileProvider.getUriForFile(activity,activity.packageName+".updates",apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            pending=null
        }catch(e:Exception){error("No package installer available on this device.")}
    }
    fun resume(){if(pending!=null&&(Build.VERSION.SDK_INT<26||activity.packageManager.canRequestPackageInstalls()))install()}
    fun close(){closed=true;worker.shutdownNow()}
}

private fun java.io.InputStream.readNBytesCompat(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
    while(out.size()<limit){val n=read(buffer,0,minOf(buffer.size,limit-out.size()));if(n<0)break;out.write(buffer,0,n)}
    return out.toByteArray()
}
