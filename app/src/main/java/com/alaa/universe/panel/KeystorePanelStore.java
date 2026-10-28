package com.alaa.universe.panel;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Dedicated no-backup atomic encrypted identity+config envelope. Never delete to recover a key failure. */
public final class KeystorePanelStore implements PanelClient.Store {
    private final AtomicFile file;
    private final String alias;
    public KeystorePanelStore(Context context) {
        file=new AtomicFile(new File(context.getNoBackupFilesDir(),"universe-panel-v2"));
        alias=context.getPackageName()+".panel.v2";
    }
    private SecretKey key(boolean create) throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        SecretKey key=(SecretKey)ks.getKey(alias,null);
        if(key!=null) return key;
        if(!create) throw new IOException("Pairing recovery required");
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return g.generateKey();
    }
    @Override public synchronized String read() throws Exception {
        if(!file.getBaseFile().exists() && !new File(file.getBaseFile()+".bak").exists()) return null;
        try(InputStream in=file.openRead()) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
            while((n=in.read(buffer))!=-1) { if(out.size()+n>1048576) throw new IOException("Invalid envelope"); out.write(buffer,0,n); }
            byte[] b=out.toByteArray();
            if(b.length<29 || b[0]!=1) throw new IOException("Invalid envelope");
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,b,1,12));
            c.updateAAD(alias.getBytes("UTF-8"));
            return new String(c.doFinal(b,13,b.length-13),"UTF-8");
        }
    }
    @Override public synchronized void write(String value) throws Exception {
        byte[] plain=value.getBytes("UTF-8");
        if(plain.length>1048500) throw new IOException("Envelope too large");
        boolean exists=file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key(!exists));
        c.updateAAD(alias.getBytes("UTF-8")); byte[] encrypted=c.doFinal(plain);
        FileOutputStream out=file.startWrite();
        try { out.write(1); out.write(c.getIV()); out.write(encrypted); file.finishWrite(out); }
        catch(Exception e) { file.failWrite(out); throw e; }
    }
}
