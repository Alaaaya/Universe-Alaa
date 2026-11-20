package com.alaa.universe;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.util.*;
@RunWith(AndroidJUnit4.class)
public class DialogDeviceTest {
 private android.app.Instrumentation getInstrumentation(){return InstrumentationRegistry.getInstrumentation();}
 private void runTestOnUiThread(Runnable r){getInstrumentation().runOnMainSync(r);}
 private HomeActivity getActivity(){
  android.content.Intent i=new android.content.Intent(getInstrumentation().getTargetContext(),HomeActivity.class);
  i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
  return (HomeActivity)getInstrumentation().startActivitySync(i);
 }
 private void collect(View v,List<View> out){out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),out);}
 @Test public void testRealDialogPublicPlaylist() throws Throwable {
  HomeActivity a=getActivity();
  runTestOnUiThread(()->a.getScreen().getProvider().loginDialog());
  getInstrumentation().waitForIdleSync();
  final View[] decor={null};
  runTestOnUiThread(()->{
   try{
    Class<?> cl=Class.forName("android.view.WindowManagerGlobal");
    Object wm=cl.getMethod("getInstance").invoke(null);
    java.lang.reflect.Field f=cl.getDeclaredField("mViews");f.setAccessible(true);
    ArrayList<View> vs=(ArrayList<View>)f.get(wm);
    for(View root:vs){List<View> nodes=new ArrayList<>();collect(root,nodes);for(View node:nodes)if(node instanceof TextView && ((TextView)node).getText().toString().contains("M3U URL"))decor[0]=root;}
    if(decor[0]==null)throw new RuntimeException("Connect dialog not found");
   }catch(Exception e){throw new RuntimeException(e);}
  });
  List<View> all=new ArrayList<>();collect(decor[0],all);
  runTestOnUiThread(()->{for(View v:all)if(v instanceof Button && ((Button)v).getText().toString().contains("M3U URL"))v.performClick();});
  runTestOnUiThread(()->{
   for(View v:all)if(v instanceof EditText && ((EditText)v).getHint().toString().contains("playlist.m3u"))((EditText)v).setText("https://iptv-org.github.io/iptv/index.m3u");
   for(View v:all)if(v instanceof Button && ((Button)v).getText().toString().equals("Connect"))v.performClick();
  });
  long start=System.currentTimeMillis();
  while(System.currentTimeMillis()-start<100000){
   getInstrumentation().waitForIdleSync();
   final boolean[] showing={true};
   runTestOnUiThread(()->showing[0]=decor[0].isAttachedToWindow());
   if(!showing[0]){
    assertNotNull("Dialog closed without catalogue",a.getScreen().getProvider().getRepository().cached());
    assertTrue(a.getScreen().getProvider().getRepository().cached().getEntries().size()>8000);
    android.util.Log.i("UniverseQA","dialog channels="+a.getScreen().getProvider().getRepository().cached().getEntries().size()+" elapsedMs="+(System.currentTimeMillis()-start));
    return;
   }
   Thread.sleep(500);
  }
  StringBuilder text=new StringBuilder();for(View v:all)if(v instanceof TextView)text.append(((TextView)v).getText()).append("\n");
  fail("Dialog did not finish in100s "+text);
 }
}
