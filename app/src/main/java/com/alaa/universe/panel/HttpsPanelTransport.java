package com.alaa.universe.panel;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;

/** Fixed origin, no redirects, bounded request/response, no credentials in URLs. */
public final class HttpsPanelTransport implements PanelClient.Transport {
    private static final String ORIGIN="https://jytjcdlmygwqizgydeyp.supabase.co/rest/v1/rpc/";
    private static final String ANON="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp5dGpjZGxteWd3cWl6Z3lkZXlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTEwMzQzODUsImV4cCI6MjEwNjYxMDM4NX0.zcr_TaxhweaScYeHxuSNzwBtHj0cgkp9JSfbsmkIr4Y";
    @Override public JSONObject call(String function, JSONObject request) throws Exception {
        if(!function.equals("register_device_v2")&&!function.equals("get_device_config_v2")) throw new IOException("Invalid operation");
        HttpsURLConnection c=(HttpsURLConnection)new URL(ORIGIN+function).openConnection();
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(15000);
        c.setRequestMethod("POST"); c.setRequestProperty("apikey",ANON); c.setRequestProperty("Content-Type","application/json");
        byte[] body=request.toString().getBytes("UTF-8");
        if(body.length>4096) throw new IOException("Request too large");
        c.setDoOutput(true); c.setFixedLengthStreamingMode(body.length);
        try {
            try(OutputStream out=c.getOutputStream()) { out.write(body); }
            int status=c.getResponseCode();
            if(status==401||status==403) return new JSONObject().put("status","unauthorized");
            if(status==429) {
                long seconds=60;
                try { seconds=Long.parseLong(c.getHeaderField("Retry-After")); } catch(Exception ignored) {}
                return new JSONObject().put("status","rate_limited").put("retry_after",Math.max(1,Math.min(86400,seconds)));
            }
            if(status!=200) throw new IOException("Panel request unavailable");
            try(InputStream in=c.getInputStream()) {
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
                while((n=in.read(b))!=-1) { if(out.size()+n>524288) throw new IOException("Response too large"); out.write(b,0,n); }
                return new JSONObject(new String(out.toByteArray(),"UTF-8"));
            }
        } finally { c.disconnect(); }
    }
}
