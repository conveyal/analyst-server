package otp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Graph.LoadLevel;
import org.opentripplanner.routing.services.GraphSource;
import org.opentripplanner.routing.services.GraphSource;
import org.opentripplanner.routing.services.GraphSource.Factory;

import controllers.Application;
import org.opentripplanner.standalone.Router;


public class AnalystGraphService {
	AnalystGraphCache graphCache = new AnalystGraphCache();
	
	public Graph getGraph(String graphId) {
		return graphCache.get(graphId);
	}
	
	public String getGraphStatus(String graphId) {
		return graphCache.getStatus(graphId);
	}
	
	public synchronized File getZippedGraph(String graphId) throws IOException {
		
		File graphDataDir = new File(new File(Application.dataPath,"graphs"), graphId);
		
		File graphZipFile = new File(new File(Application.dataPath,"graphs"), graphId + ".zip");
		
		if(graphDataDir.exists() && graphDataDir.isDirectory()) {
			
			FileOutputStream fileOutputStream = new FileOutputStream(graphZipFile);
			ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
			
			byte[] buffer = new byte[1024];
			
			for(File f : graphDataDir.listFiles()) {
				ZipEntry zipEntry = new ZipEntry(f.getName());
				zipOutputStream.putNextEntry(zipEntry);
	    		FileInputStream fileInput = new FileInputStream(f);

	    		int len;
	    		while ((len = fileInput.read(buffer)) > 0) {
	    			zipOutputStream.write(buffer, 0, len);
	    		}
	 
	    		fileInput.close();
	    		zipOutputStream.closeEntry();
			}
			
			zipOutputStream.close();
			return graphZipFile;		
		}	
		return null;
	}

}
