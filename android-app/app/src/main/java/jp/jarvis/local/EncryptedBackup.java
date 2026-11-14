package jp.jarvis.local;

import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.JSONObject;

final class EncryptedBackup {
    private static final byte[] MAGIC="JARVIS-BACKUP-V2".getBytes(StandardCharsets.UTF_8);
    private static SecretKey key(String password,byte[] salt)throws Exception{SecretKeyFactory f=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");KeySpec s=new PBEKeySpec(password.toCharArray(),salt,120000,256);return new SecretKeySpec(f.generateSecret(s).getEncoded(),"AES");}
    static void save(File out,JSONObject payload,String password)throws Exception{byte[] salt=new byte[16],iv=new byte[12];SecureRandom r=new SecureRandom();r.nextBytes(salt);r.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));byte[] enc=c.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));out.getParentFile().mkdirs();DataOutputStream d=new DataOutputStream(new FileOutputStream(out));d.write(MAGIC);d.write(salt);d.write(iv);d.writeInt(enc.length);d.write(enc);d.close();}
    static JSONObject load(File in,String password)throws Exception{DataInputStream d=new DataInputStream(new FileInputStream(in));byte[] m=new byte[MAGIC.length];d.readFully(m);if(!java.util.Arrays.equals(m,MAGIC))throw new SecurityException("バックアップ形式が違います");byte[] salt=new byte[16],iv=new byte[12];d.readFully(salt);d.readFully(iv);byte[] enc=new byte[d.readInt()];d.readFully(enc);d.close();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));return new JSONObject(new String(c.doFinal(enc),StandardCharsets.UTF_8));}
}
