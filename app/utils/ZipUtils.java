package utils;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utilities for working with zip files.
 * @author mattwigway
 *
 */
public class ZipUtils {
	/**
	 * Unzip the specified file to the specified directory.
	 */
	public static void unzip (ZipFile zip, File dir) throws IOException {
		Enumeration<? extends ZipEntry> entries = zip.entries();

		while (entries.hasMoreElements()) {

			ZipEntry entry = entries.nextElement();
			File entryDestination = new File(dir,  entry.getName());

			entryDestination.getParentFile().mkdirs();

			if (entry.isDirectory())
				entryDestination.mkdirs();
			else {
				InputStream in = zip.getInputStream(entry);
				OutputStream out = new FileOutputStream(entryDestination);
				IOUtils.copy(in, out);
				IOUtils.closeQuietly(in);
				IOUtils.closeQuietly(out);
			}
		}
	}
	
	public static void unzip(ZipFile zip, ZipEntry ze, File output) throws IOException {
		output.getParentFile().mkdirs();
		
		InputStream in = zip.getInputStream(ze);
		OutputStream out = new FileOutputStream(output);
		IOUtils.copy(in, out);
		IOUtils.closeQuietly(in);
		IOUtils.closeQuietly(out);
	}
}
