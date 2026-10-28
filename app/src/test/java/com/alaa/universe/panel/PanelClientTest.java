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
            if(configStatus.equals("active")) r.put("expires_at",JSONObject.NULL).put("playlists",
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
        PanelClient c=client();c.open();String old=c.displayCode();configStatus="registration_expired";c.open();
        assertNotEquals(old,c.displayCode());
        now+=10000;configStatus="active";c.open();String active=c.displayCode();
        configStatus="registration_expired";c.open();assertEquals(active,c.displayCode());
        assertFalse(new JSONObject(store.value).has("cache"));
    }
    @Test public void inactiveDoesNotProvePending() throws Exception {
        configStatus="inactive";PanelClient c=client();c.open();String old=c.displayCode();
        configStatus="registration_expired";c.open();assertEquals(old,c.displayCode());
    }
    @Test public void revocationClearsSecureCacheAndStops() throws Exception {
        configStatus="active";PanelClient c=client();c.open();assertEquals(1,configs);
        configStatus="unauthorized";c.open();assertEquals("unauthorized",stopped);
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
}
