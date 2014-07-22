package utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.core.SlippyTile;
import org.opentripplanner.analyst.request.TileRequest;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

public class Tile {
	
	final public String tileId;
	final public Integer x, y, z;
	final public Integer scaleFactor;
	final private MathTransform tr;
	
	public BufferedImage buffer;	
	public Graphics2D gr;
	
	final public Envelope envelope;
	
	public Tile(String tileIdPrefix, int x, int y, int z) {
		
		this.x = x;
		this.y = y;
		this.z = z;
		
		if(14 - z <= 0)
			this.scaleFactor = 1;
		else
			this.scaleFactor = 14 - z;
		
		tileId = tileIdPrefix + "_" + x + "_" + "_" + y + "_" + "_" + z;
		
		double maxLat = SlippyTile.tile2lat(y, z);
        double minLat = SlippyTile.tile2lat(y + 1, z);
        double minLon = SlippyTile.tile2lon(x, z);
        double maxLon = SlippyTile.tile2lon(x + 1, z);
    	
        // annoyingly need both jts and opengis envelopes -- there's probably a smarter way to get them
    	envelope = new Envelope(maxLon, minLon, maxLat, minLat);
         
    	Envelope2D env = JTS.getEnvelope2D(envelope, DefaultGeographicCRS.WGS84);
    	
    	TileRequest tileRequest = new TileRequest("", env, 256 * this.scaleFactor, 256 * this.scaleFactor);
    	GridEnvelope2D gridEnv = new GridEnvelope2D(0, 0, tileRequest.width, tileRequest.height);
    	GridGeometry2D gg = new GridGeometry2D(gridEnv, (org.opengis.geometry.Envelope)(tileRequest.bbox));
    	
      	tr = gg.getCRSToGrid2D();
      	
      	buffer = new BufferedImage(tileRequest.width, tileRequest.height, BufferedImage.TYPE_4BYTE_ABGR);
      
	}
	
	public void renderHaltonPoints(HaltonPoints hp, Color c) {
		
		double[] coords = hp.transformPoints(tr);
    	int i = 0;
    	for(i = 0; i < hp.getNumPoints() * 2; i += 2){
    		
    		if(coords[i] > 0 && coords[i] < buffer.getWidth() &&  coords[i+1] > 0 && coords[i+1] < buffer.getHeight())
    			buffer.setRGB((int)coords[i], (int)coords[i+1], c.getRGB());
			
    		if(z > 14) {
				
				if(x+1 < buffer.getWidth() && y+1 < buffer.getHeight())
					buffer.setRGB((int)coords[i]+1, (int)coords[i+1]+1, c.getRGB());
				
				if(y+1 < buffer.getHeight())
					buffer.setRGB((int)coords[i], (int)coords[i+1]+1, c.getRGB());
				
				if(x+1 < buffer.getWidth())
					buffer.setRGB((int)coords[i]+1, (int)coords[i+1], c.getRGB());
    			
			}	
    	}	
		
	}
	
	public void renderPolygon(Geometry g, Color c) throws MismatchedDimensionException, TransformException {
		
		if(gr == null)
			gr = buffer.createGraphics();
		
		gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
		
		Geometry gTr  = JTS.transform(g, tr);
        
		gr.setColor(c);
		
    	Polygon p = new Polygon();
    	for(Coordinate coord : gTr.getCoordinates())
    		p.addPoint((int)coord.x, (int)coord.y);
    	
    	gr.fillPolygon(p);       
	}
	
	public void renderLineString(Geometry g, Color c, Integer strokeWidth) throws MismatchedDimensionException, TransformException {
		
		if(gr == null)
			gr = buffer.createGraphics();
		
		gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
		
		Geometry gTr  = JTS.transform(g, tr);
        
		gr.setColor(c);
		
		if(strokeWidth != null)
			gr.setStroke(new BasicStroke(5));
		
		Path2D path = new Path2D.Double();
		
		boolean firstPoint = true;
    	for(Coordinate coord : gTr.getCoordinates()) {
    		if(firstPoint)
    			path.moveTo(coord.x, coord.y);
    		else
    			path.lineTo(coord.x, coord.y);
    		
    		firstPoint = false;
    	}
    	
    	gr.draw(path);    
	}
	
	public byte[] generateImage() throws IOException {
		
		if(this.scaleFactor > 1) {
			
			int w = buffer.getWidth();
            int h = buffer.getHeight();
			
			do {
				w /= 2;
                if (w < 256) {
                    w = 256;
                }
                
                h /= 2;
                if (h < 256) {
                    h = 256;
                }
				
				BufferedImage original = buffer;
				

				BufferedImage resized = new BufferedImage(w, h, original.getType());
			    Graphics2D g = resized.createGraphics();
			    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			    g.drawImage(original, 0, 0, w, h, null);
			    g.dispose();
			    g = null;
			    
			    buffer = resized;

	        } while (w != 256 || h != 256);
		}
		
		if(gr != null)
			gr.dispose();
		gr = null;
		
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(buffer, "png", baos);
        
        return baos.toByteArray(); 
	}
}
