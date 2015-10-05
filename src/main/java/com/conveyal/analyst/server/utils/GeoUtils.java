package com.conveyal.analyst.server.utils;

import com.sun.tools.javac.jvm.ByteCodes;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.noding.IteratedNoder;
import com.vividsolutions.jts.noding.NodedSegmentString;
import com.vividsolutions.jts.noding.SegmentString;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchIdentifierException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GeoUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GeoUtils.class);

   public static double RADIANS = 2 * Math.PI;
   
   public static MathTransform recentMathTransform = null;
   public static GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(),4326);
   public static GeometryFactory projectedGeometryFactory = new GeometryFactory(new PrecisionModel());

   private static MathTransform aeaTransform = null;
   
   /**
    * From
    * http://gis.stackexchange.com/questions/28986/geotoolkit-conversion-from
    * -lat-long-to-utm
    */
   public static int
       getEPSGCodefromUTS(Coordinate refLonLat) {
     // define base EPSG code value of all UTM zones;
     int epsg_code = 32600;
     // add 100 for all zones in southern hemisphere
     if (refLonLat.y < 0) {
       epsg_code += 100;
     }
     // finally, add zone number to code
     epsg_code += getUTMZoneForLongitude(refLonLat.x);

     return epsg_code;
   }

 

   public static double getMetersInAngleDegrees(
     double distance) {
     return distance / (Math.PI / 180d) / 6378137d;
   }

   public static MathTransform getTransform(
     Coordinate refLatLon) {

     try {
       final CRSAuthorityFactory crsAuthorityFactory =
           CRS.getAuthorityFactory(false);
       
       
       final GeographicCRS geoCRS =
           crsAuthorityFactory.createGeographicCRS("EPSG:4326");

       final CoordinateReferenceSystem dataCRS = 
           crsAuthorityFactory
               .createCoordinateReferenceSystem("EPSG:" 
                   + getEPSGCodefromUTS(refLatLon)); //EPSG:32618

       final MathTransform transform =
           CRS.findMathTransform(geoCRS, dataCRS);
       
       GeoUtils.recentMathTransform = transform;
       
       return transform;
     } catch (NoSuchIdentifierException e) {
         LOG.error("Error retrieving EPSG data", e);
     } catch (final FactoryException e) {
         LOG.error("Error creating MathTransform", e);
     }

     return null;

   }

   /*
    * Taken from OneBusAway's UTMLibrary class
    */
   public static int getUTMZoneForLongitude(double lon) {

     if (lon < -180 || lon > 180)
       throw new IllegalArgumentException(
           "Coordinates not within UTM zone limits");

     int lonZone = (int) ((lon + 180) / 6);

     if (lonZone == 60)
       lonZone--;
     return lonZone + 1;
   }
   
   /**
    * Get the area of a geometry, in undefined units, by transforming to an equal-area projection.
    * 
    * The units are undefined because the scale varies across the map. However, two calls to getArea yield
    * units that are comparable.
    */
   public static double getArea (Geometry geom) {
	   // project the geometry to a cylindrical equal-area projection
	   if (aeaTransform == null) {
		   try {
			   CoordinateReferenceSystem aea = CRS.parseWKT("PROJCS[\"unnamed\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],TOWGS84[0,0,0,0,0,0,0],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9108\"]],AUTHORITY[\"EPSG\",\"4326\"]],PROJECTION[\"Albers_Conic_Equal_Area\"],PARAMETER[\"standard_parallel_1\",0],PARAMETER[\"standard_parallel_2\",30],PARAMETER[\"latitude_of_center\",0],PARAMETER[\"longitude_of_center\",0],PARAMETER[\"false_easting\",0],PARAMETER[\"false_northing\",0],UNIT[\"Meter\",1]]");

			   aeaTransform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, aea);
		   } catch (Exception e) {
			   throw new RuntimeException(e);
		   }
	   }
	   
	   // perform the transformation
	   try {
		   Geometry newGeom = JTS.transform(geom, aeaTransform);
		   return newGeom.getArea();
	   } catch (Exception e) {
		   throw new RuntimeException(e);
	   }
   }

    /**
     * Make a polygonal geometry valid, iff it is invalid. Returns non-polygonal geometries
     * and valid geometries untouched.
     */
    public static Geometry makeValid (Geometry in) {
        if (in instanceof Polygon) {
            return makePolygonValid((Polygon) in);
        }
        else if (in instanceof MultiPolygon) {
            if (in.isValid())
                return in;

            LOG.warn("Cleaning invalid multipolygon {}", in);

            List<Geometry> components = IntStream.range(0, in.getNumGeometries())
                    .mapToObj(i -> (Polygon) in.getGeometryN(i))
                    .map(GeoUtils::makePolygonValid)
                    .collect(Collectors.toList());

            // the components may overlap, etc. handle this.
            return UnaryUnionOp.union(components);
        }
        else {
            return in;
        }
    }

    /** Make a polygon valid */
    public static Geometry makePolygonValid (Polygon p) {
        if (p.isValid())
            return p;

        LOG.info("Cleaning invalid polygon {}", p);

        // Make the outer ring valid
        Geometry ret = makeRingValid(p.getExteriorRing());

        // punch holes. note that this correctly handles the case when a hole overlaps the boundary
        for (int hole = 0; hole < p.getNumInteriorRing(); hole++) {
            LineString ring = p.getInteriorRingN(hole);
            ret = ret.difference(makeRingValid(ring));
        }

        return ret;
    }

    public static Geometry makeRingValid (LineString string) {
        if (string.getNumPoints() < 2)
            // FIXME
            return string;

        // close the ring if it isn't closed
        Coordinate[] coords;
        if (!string.isClosed()) {
            coords = new Coordinate[string.getNumPoints() + 1];

            for (int i = 0; i < string.getNumPoints(); i++) {
                coords[i] = string.getCoordinateN(i);
            }

            // close it
            coords[coords.length - 1] = coords[0];
        }
        else
            coords = string.getCoordinates();

        SegmentString ss = new NodedSegmentString(coords, null);

        IteratedNoder noder = new IteratedNoder(geometryFactory.getPrecisionModel());

        noder.computeNodes(Arrays.asList(ss));

        Polygonizer p = new Polygonizer();

        for (Object noded : noder.getNodedSubstrings()) {
            p.add(geometryFactory.createLineString(((SegmentString) noded).getCoordinates()));
        }

        Collection<Polygon> polys = p.getPolygons();
        Geometry clean = geometryFactory.createMultiPolygon(polys.toArray(new Polygon[polys.size()]));

        // for good measure, handle duplicated edges etc.
        // this should be safe, we have now removed the figure-8 case polygon, which can be problematic
        // because buffer will throw away the negative space.
        return clean.buffer(0);
    }

    public static GeometryFactory getGeometryFactory() {
        return geometryFactory;
    }
}