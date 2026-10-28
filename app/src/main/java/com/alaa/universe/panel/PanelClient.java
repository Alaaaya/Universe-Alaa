package com.alaa.universe.panel;

import org.json.JSONObject;
import java.security.SecureRandom;

/** Blocking client: call off the UI thread. A single instance must own a store. */
public final class PanelClient {
    public interface Store { String read() throws Exception; void write(String value) throws Exception; }
    public interface Transport { JSONObject call(String function, JSONObject request) throws Exception; }
    public interface Clock { long now(); }
    public interface Listener {
        void onConfig(JSONObject config);
        void onStopped(String reason);
    }
    private final Store store;
    private final Transport transport;
    private final Clock clock;
    private final SecureRandom random;
    private final Listener listener;
    private JSONObject state;
    public PanelClient(Store store, Transport transport, Clock clock, SecureRandom random, Listener listener) {
        this.store=store; this.transport=transport; this.clock=clock; this.random=random; this.listener=listener;
    }
    private static String hex(byte[] bytes, boolean upper) {
        StringBuilder s=new StringBuilder();
        for(byte b:bytes) s.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
        return upper?s.toString().toUpperCase(java.util.Locale.ROOT):s.toString();
    }
    private JSONObject fresh() throws Exception {
        byte[] code=new byte[6],token=new byte[32]; random.nextBytes(code); random.nextBytes(token);
        return new JSONObject().put("code",hex(code,true)).put("token",hex(token,false))
            .put("registered",false).put("everActive",false).put("pendingProof",false)
            .put("attempts",0).put("next",0);
    }
    private void save() throws Exception { store.write(state.toString()); }
    private void load() throws Exception {
        if(state!=null) return;
        String raw=store.read();
        state=raw==null?fresh():new JSONObject(raw);
        if(!state.getString("code").matches("[0-9A-F]{12}") ||
           !state.getString("token").matches("[0-9a-f]{64}") ||
           !state.has("everActive") || !state.has("pendingProof")) { state=null; throw new Exception("identity"); }
        if(raw==null) save(); // Persist complete identity BEFORE any network.
    }
    /** Display code only. Never expose token through UI/logging. */
    public synchronized String displayCode() throws Exception { load(); return state.getString("code"); }
    private JSONObject request() throws Exception {
        return new JSONObject().put("p_device_code",state.getString("code"))
            .put("p_device_token",state.getString("token"));
    }
    private void stop(String reason) throws Exception {
        state.remove("cache"); state.put("blocked",reason); save(); listener.onStopped(reason);
    }
    private void defer(JSONObject response) throws Exception {
        int attempts=Math.min(16,state.optInt("attempts")+1);
        long base=Math.min(3600000L,1000L*(1L<<Math.min(attempts,11)));
        long seconds=response==null?0:response.optLong("retry_after",0);
        long wait=Math.max(base,Math.min(86400L,Math.max(0,seconds))*1000L);
        state.put("attempts",attempts).put("next",clock.now()+wait+random.nextInt(1001)); save();
    }
    private void resetBackoff() throws Exception { state.put("attempts",0).put("next",clock.now()+60000L); save(); }
    private void validateResponse(JSONObject r) throws Exception {
        if(r.has("device_code")&&!state.getString("code").equals(r.getString("device_code"))) throw new Exception("mismatch");
        if(r.has("app_type")&&!"universe".equals(r.getString("app_type"))) throw new Exception("mismatch");
    }
    /** Cache-first, one refresh per call. Offline cache allowed for at most 24h and never past server expiry.
     * Denial is authoritative. Exceptions and secrets are deliberately never forwarded/logged.
     */
    public synchronized void open() {
        try {
            load(); save(); // Reassert durability even after a previous write failure.
            JSONObject cached=state.optJSONObject("cache");
            long now=clock.now(), age=now-state.optLong("cachedAt");
            if(cached!=null && !state.has("blocked") && age>=0 && age<86400000L && notExpired(cached,now))
                listener.onConfig(new JSONObject(cached.toString()));
            else if(cached!=null) { state.remove("cache"); save(); listener.onStopped("cache_expired"); }
            if(now<state.optLong("next")) return;
            if(!state.optBoolean("registered")) {
                JSONObject r=transport.call("register_device_v2",request().put("p_app_type","universe").put("p_model","Android TV"));
                validateResponse(r);
                String status=r.getString("status");
                if("rate_limited".equals(status)) { defer(r); return; }
                if(!"registered".equals(status)) { stop("pairing_required"); defer(null); return; }
                state.put("registered",true); save();
            }
            JSONObject r=transport.call("get_device_config_v2",request());
            // Any active observation conservatively burns renewal permission before validation.
            if("active".equals(r.optString("status"))) {
                state.put("everActive",true).put("pendingProof",false); save();
                if(!r.has("device_code") || !r.has("app_type")) {
                    stop("invalid_config"); defer(null); return;
                }
            }
            validateResponse(r);
            String status=r.getString("status");
            switch(status) {
                case "active":
                    if(!r.has("playlists") || r.getJSONArray("playlists").length()==0 || !notExpired(r,now)) {
                        stop("invalid_config"); defer(null); return;
                    }
                    state.put("everActive",true).put("pendingProof",false).put("cache",r).put("cachedAt",now);
                    state.remove("blocked"); resetBackoff();
                    listener.onConfig(new JSONObject(r.toString())); return;
                case "pending":
                    if(state.optBoolean("everActive")) { stop("pairing_required"); return; }
                    state.put("pendingProof",true); stop("pending"); resetBackoff(); return;
                case "registration_expired":
                    stop(status);
                    // Server guarantees only never-activated, playlist-free registrations expire.
                    if(!state.optBoolean("everActive") && state.optBoolean("pendingProof")) {
                        state=fresh(); save(); defer(null); // next open registers the NEW durable identity.
                    }
                    return;
                case "rate_limited": defer(r); return;
                case "inactive": case "expired": case "unauthorized":
                    // Inactive is NOT proof of never activated.
                    state.put("pendingProof",false); stop(status); resetBackoff(); return;
                default: stop("pairing_required"); defer(null);
            }
        } catch(Exception ignored) {
            try { if(state!=null) defer(null); } catch(Exception alsoIgnored) {}
            // A temporary network failure does not revoke a valid offline lease.
            JSONObject offline=state==null?null:state.optJSONObject("cache");
            long age=state==null?-1:clock.now()-state.optLong("cachedAt");
            if(offline==null || state.has("blocked") || age<0 || age>=86400000L || !notExpired(offline,clock.now()))
                listener.onStopped("refresh_unavailable");
        }
    }
    private static boolean notExpired(JSONObject r,long now) {
        if(r.isNull("expires_at")) return true;
        try {
            // API23-safe ISO parser (no java.time desugaring required).
            String text=r.getString("expires_at").replace("Z","+00:00");
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?([+-]\\d{2}):(\\d{2})$").matcher(text);
            if(!m.matches()) return false;
            java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",java.util.Locale.US);
            f.setLenient(false);
            return f.parse(m.group(1)+m.group(2)+m.group(3)).getTime()>now;
        } catch(Exception ignored) { return false; }
    }
}
