package utils;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.NoSuchIdentifierException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateFilter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchIdentifierException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.NoninvertibleTransformException;
import org.opengis.referencing.operation.TransformException;

import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateFilter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;

public class GeoUtils {
   public static double RADIANS = 2 * Math.PI;
   
   public static MathTransform recentMathTransform = null;
   public static GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(),4326);
   public static GeometryFactory projectedGeometryFactory = new GeometryFactory(new PrecisionModel());

   private static MathTransform ceaTransform = null;
   
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
     } catch (final NoSuchIdentifierException e) {
       // TODO Auto-generated catch block
       e.printStackTrace();
     } catch (final FactoryException e) {
       // TODO Auto-generated catch block
       e.printStackTrace();
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
    * Get the area of a geometry, in square meters, by transforming to an equal-area projection.
    * 
    * http://epsg.io/3975 has a comment that equal-area properties are not maintained
	* due to issues with cylindrical/ellipsoidal math, but I don't think that is
	* correct. It's not the only place we use cylindrical math anyhow, and since we're using this
	* to create ratios of small areas at similar latitudes it should be fine.			   
    */
   public static double getArea (Geometry geom) {
	   // project the geometry to a cylindrical equal-area projection
	   if (ceaTransform == null) {
		   try {
			   CoordinateReferenceSystem cea = CRS.decode("epsg:3975");
			   ceaTransform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, cea);
		   } catch (Exception e) {
			   throw new RuntimeException(e);
		   }
	   }
	   
	   // perform the transformation
	   try {
		   Geometry newGeom = JTS.transform(geom, ceaTransform);
		   return newGeom.getArea();
	   } catch (Exception e) {
		   throw new RuntimeException(e);
	   }
   }
 }