package com.conveyal.analyst.server;

import com.conveyal.analyst.server.utils.GeoUtils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import junit.framework.TestCase;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Test;
import org.opengis.referencing.operation.MathTransform;


/**
 * Tests for the geographic math utilities.
 */
public class GeoUtilsTest extends TestCase {
	private GeometryFactory gf;
	
	@Override
	protected void setUp () {
    	gf = new GeometryFactory();
	}
	
    @Test
    public void testGetArea () throws Exception {    	
    	// create two geometries whose ratio of areas should
    	// be very close to 0.1.
    	
    	// this has area 100 m^2
    	Coordinate[] sbCoords = new Coordinate[] {
    		new Coordinate(1842620, 603860),
    		new Coordinate(1842630, 603860),
    		new Coordinate(1842630, 603870),
    		new Coordinate(1842620, 603870),
    		new Coordinate(1842620, 603860)
    	};
    	
    	Geometry sbGeom = gf.createPolygon(sbCoords);
    	
    	// make sure we haven't messed up the coordinates
    	assertTrue(Math.abs(sbGeom.getArea() - 100) < 0.0000000001);
    	
    	// reproject from California State Plane Zone 5 NAD83 to geographic WGS84
    	MathTransform sbTransform = CRS.findMathTransform(CRS.decode("EPSG:26945"), DefaultGeographicCRS.WGS84);
    	sbGeom = JTS.transform(sbGeom, sbTransform);
    	
    	// this should have area 1000 m^2
    	Coordinate[] dcCoords = new Coordinate[] {
    			new Coordinate (397170, 135970),
    			new Coordinate (397270, 135970),
    			new Coordinate (397270, 135980),
    			new Coordinate (397170, 135980),
    			new Coordinate (397170, 135970)
    	};
    	
    	Geometry dcGeom = gf.createPolygon(dcCoords);
    	
    	assertTrue(Math.abs(dcGeom.getArea() - 1000) < 0.0000000001);
    	
    	// Reproject from Maryland State Plane to geographic WGS84
    	MathTransform dcTransform = CRS.findMathTransform(CRS.decode("EPSG:26985"), DefaultGeographicCRS.WGS84);
    	dcGeom = JTS.transform(dcGeom, dcTransform);
    	
    	// check the areas of the reprojected shapes
    	// State Plane has error less than 1%, so we look for an error of less than 10 meters for DC an 1 meter for SB
    	assertTrue(Math.abs(GeoUtils.getArea(dcGeom) - 1000) < 10);
    	assertTrue(Math.abs(GeoUtils.getArea(sbGeom) - 100) < 1);
    	
    	// do the same thing for the southern hemisphere
    	// since the ellipsoid is symmetrical about the equator, we can just flip the latitudes and
    	// expect the same results.
    	assertTrue(Math.abs(GeoUtils.getArea(southern(dcGeom)) - 1000) < 10);
    	assertTrue(Math.abs(GeoUtils.getArea(southern(sbGeom)) - 100) < 1);
    	
    }

    /** mirror a polygonal geometry about the equator */
    private Geometry southern (Geometry northern) {
    	Coordinate[] nCoords = northern.getCoordinates();
    	Coordinate[] sCoords = new Coordinate[nCoords.length];
    	
    	for (int i = 0; i < nCoords.length; i++) {
    		sCoords[i] = new Coordinate(nCoords[i].x, -nCoords[i].y);
    	}
    	
    	return gf.createPolygon(sCoords);
    }
}
