package utils;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;

public class HashUtils {
	
	public static String hashString(String input)  {
		
		try {
		
			byte[] bytesOfMessage = input.getBytes("UTF-8");	
			
			return DigestUtils.md5Hex(bytesOfMessage);
			
		}
		catch(Exception e) {
			
			return "";
		}
	}
	
	public static String hashFile(File file)  {
		
		try {
			
			MessageDigest md = MessageDigest.getInstance("MD5");
			
			FileInputStream fis = new FileInputStream(file);
			DigestInputStream dis = new DigestInputStream(fis, md);
			
			while (dis.read() != -1);
			dis.close();
			
			return new String(Hex.encodeHex(md.digest()));
			
			
		}
		catch(Exception e) {
			
			return "";
		}
	}

}
