package com.alaa.universe.panel;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.security.SecureRandom;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class PanelClientTest {
    static class Memory implements PanelClient.Store {
        String value; boolean fail;
        public String read(){return value;}
        public void write(String s) throws Exception {if(fail)throw new Exception();value=s;}
    }
    Memory store; long now; int calls,configs; String stopped; String configStatus;
    PanelClient.Transport wire;
    @Before public void setup() {
        store=new Memory(); now=1000000; calls=0; configs=0; configStatus="pending";
        wire=(fn,req)->{
            assertNotNull(store.value);
            JSONObject saved=new JSONObject(store.value);
            assertEquals(saved.getString("token"),req.getString("p_device_token"));
            calls++;
            if(fn.equals("register_device_v2")) {
                assertEquals("universe",req.getString("p_app_type"));
                return new JSONObject().put("status","registered");
            }
            JSONObject r=new JSONObject().put("status",configStatus);
            if(configStatus.equals("active")) r.put("device_code",req.getString("p_device_code")).put("app_type","universe").put("expires_at",JSONObject.NULL).put("playlists",
                new org.json.JSONArray("[{\"id\":\"test\"}]"));
            return r;
        };
    }
    PanelClient client() { return new PanelClient(store,wire,()->now,new SecureRandom(),new PanelClient.Listener(){
        public void onConfig(JSONObject c){configs++;}
        public void onStopped(String r){stopped=r;}
    }); }
    @Test public void persistsBeforeRegistrationAndPendingProof() throws Exception {
        PanelClient c=client(); assertTrue(c.displayCode().matches("[A-F0-9]{12}"));
        c.open(); assertEquals(2,calls); JSONObject s=new JSONObject(store.value);
        assertTrue(s.getBoolean("pendingProof")); assertTrue(s.getString("token").matches("[a-f0-9]{64}"));
    }
    @Test public void writeFailureNeverSends() { store.fail=true; PanelClient c=client(); c.open();c.open(); assertEquals(0,calls); }
    @Test public void retrySameIdentityAfterRestart() throws Exception {
        wire=(f,r)->{calls++;throw new java.io.IOException();};
        PanelClient c=client();String code=c.displayCode();c.open();client().open();assertEquals(1,calls);
        now+=10000;client().open();assertEquals(2,calls);assertEquals(code,client().displayCode());
    }
    @Test public void pendingExpiredRenewsButActiveNeverDoes() throws Exception {
        PanelClient c=client();c.open();String old=c.displayCode();configStatus="registration_expired";now+=60000;c.open();
        assertNotEquals(old,c.displayCode());
        now+=10000;configStatus="active";c.open();String active=c.displayCode();
        configStatus="registration_expired";now+=60000;c.open();assertEquals(active,c.displayCode());
        assertFalse(new JSONObject(store.value).has("cache"));
    }
    @Test public void inactiveDoesNotProvePending() throws Exception {
        configStatus="inactive";PanelClient c=client();c.open();String old=c.displayCode();
        configStatus="registration_expired";now+=60000;c.open();assertEquals(old,c.displayCode());
    }
    @Test public void revocationClearsSecureCacheAndStops() throws Exception {
        configStatus="active";PanelClient c=client();c.open();assertEquals(1,configs);
        configStatus="unauthorized";now+=60000;c.open();assertEquals("unauthorized",stopped);
        assertFalse(new JSONObject(store.value).has("cache"));
        int count=configs; c.open(); assertEquals(count,configs);
    }
    @Test public void rateLimitSurvivesRestart() throws Exception {
        wire=(f,r)->{calls++;return new JSONObject().put("status","rate_limited").put("retry_after",3600);};
        client().open();now+=60000;client().open();assertEquals(1,calls);
        assertTrue(new JSONObject(store.value).getLong("next")>=4600000);
    }
    @Test public void collisionDoesNotRotate() throws Exception {
        wire=(f,r)->new JSONObject().put("status","unavailable");
        PanelClient c=client();String code=c.displayCode();c.open();assertEquals(code,c.displayCode());
        assertEquals("pairing_required",stopped);
    }
    @Test public void corruptIdentityFailsClosed() {
        store.value="{broken";client().open();assertEquals(0,calls);assertEquals("{broken",store.value);
    }

    @Test public void successCooldownSurvivesRestart() throws Exception {
        client().open(); int count=calls;
        assertTrue(new JSONObject(store.value).getLong("next")>=now+60000);
        client().open(); now+=59999; client().open(); assertEquals(count,calls);
        now++; client().open(); assertEquals(count+1,calls);
    }
    @Test public void invalidActiveCannotRenewPendingIdentity() throws Exception {
        PanelClient c=client();c.open();String code=c.displayCode();now+=60000;
        wire=(f,r)->new JSONObject().put("status","active");c=client();c.open();
        JSONObject saved=new JSONObject(store.value);
        assertTrue(saved.getBoolean("everActive"));assertFalse(saved.getBoolean("pendingProof"));
        assertEquals("invalid_config",stopped);
        now+=60000;wire=(f,r)->new JSONObject().put("status","registration_expired");
        client().open();assertEquals(code,client().displayCode());
    }
    @Test public void mismatchedDeviceAndAppNeverCached() throws Exception {
        PanelClient c=client();c.open();now+=60000;
        wire=(f,r)->new JSONObject().put("status","active").put("device_code","FFFFFFFFFFFF").put("app_type","universe");
        client().open();assertEquals(0,configs);
        assertTrue(new JSONObject(store.value).getBoolean("everActive"));
        assertFalse(new JSONObject(store.value).has("cache"));
        now+=60000;
        wire=(f,r)->new JSONObject().put("status","active").put("device_code",r.getString("p_device_code")).put("app_type","alaa_player");
        client().open();assertEquals(0,configs);
    }
    @Test public void offlineLeaseExpiresAfter24Hours() throws Exception {
        configStatus="active";client().open(); assertEquals(1,configs);
        wire=(f,r)->{throw new java.io.IOException();};
        now+=60000;stopped=null;client().open();assertEquals(2,configs);assertNull(stopped);
        now+=86400000;client().open();assertEquals(2,configs);
        assertFalse(new JSONObject(store.value).has("cache"));assertNotNull(stopped);
    }
    @Test public void serverExpiryEndsOfflineLease() throws Exception {
        configStatus="active";client().open();
        JSONObject saved=new JSONObject(store.value);
        saved.getJSONObject("cache").put("expires_at","1970-01-01T00:20:00+00:00");
        store.value=saved.toString();now=1200001;
        wire=(f,r)->{throw new java.io.IOException();};
        client().open();assertEquals(1,configs);assertFalse(new JSONObject(store.value).has("cache"));
    }
    @Test public void expiredActivePermanentlyBurnsPendingProof() throws Exception {
        client().open();now+=60000;
        wire=(f,r)->new JSONObject().put("status","active").put("device_code",r.getString("p_device_code"))
            .put("app_type","universe").put("expires_at","1970-01-01T00:00:00Z").put("playlists",new org.json.JSONArray("[{}]"));
        client().open();JSONObject saved=new JSONObject(store.value);
        assertTrue(saved.getBoolean("everActive"));assertFalse(saved.getBoolean("pendingProof"));assertEquals("invalid_config",stopped);
    }
}
