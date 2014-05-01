package model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.toRadians;

import org.apache.commons.math3.util.FastMath;
import org.opentripplanner.analyst.core.Sample;
import org.opentripplanner.common.geometry.AccumulativeGridSampler;
import org.opentripplanner.common.geometry.AccumulativeGridSampler.AccumulativeMetric;
import org.opentripplanner.common.geometry.DelaunayIsolineBuilder;
import org.opentripplanner.common.geometry.IsolineBuilder.ZMetric;
import org.opentripplanner.common.geometry.RecursiveGridIsolineBuilder;
import org.opentripplanner.common.geometry.RecursiveGridIsolineBuilder.ZFunc;
import org.opentripplanner.common.geometry.SparseMatrixZSampleGrid;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.common.geometry.ZSampleGrid;
import org.opentripplanner.common.geometry.DistanceLibrary;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.spt.SPTWalker;
import org.opentripplanner.routing.spt.SPTWalker.SPTVisitor;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.StreetVertex;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

import controllers.Application;
import play.Logger;


public class SptResponse {

	private DistanceLibrary distanceLibrary = SphericalDistanceLibrary.getInstance();
	
	public String sptId;
	
	private HashMap<Integer,Geometry> isolineCache = new HashMap<Integer,Geometry>();
	
	AnalystRequest req;
	ShortestPathTree spt;
	
	@JsonIgnore
	public HashMap<String,Long> destinationTimes = new HashMap<String,Long>();
	
	public SptResponse(AnalystRequest r, ShortestPathTree s) {
		
		sptId = r.sptId;
		
		req = r;
		spt = s;
	}
	
	public long evaluateSample(Sample s) {
		return s.eval(spt);
	}
	
	
	public void calcDestinations(HashMap<String, Sample> destinations) {
		
		for(String id : destinations.keySet()) {
			if(destinations.get(id) != null) {
				long time = evaluateSample(destinations.get(id));
				if(time <= Application.maxTimeLimit) {
					destinationTimes.put(id, time);
				}
			}
		}
		// zapping spt so its GC'd 
		this.spt = null;
		
	}
	
	public Geometry getIsoline(Integer seconds) {
		/*
		// hit cache to see if we've already computed this isoline
		if(isolineCache.containsKey(seconds))
			return isolineCache.get(seconds);

        // TODO Snap the center as XYZ tile grid for better sample-reuse (if using sample cache).
        Coordinate center = req.getFrom().getCoordinate();
        double gridSizeMeters = 200;
      
       
       // Off-road max distance MUST be APPROX EQUALS to the grid precision
       final double D0 = gridSizeMeters * 0.8;
       final double V0 = 1.00; // m/s, off-road walk speed

       // 3. Create a sample grid based on the SPT.
       long t1 = System.currentTimeMillis();
       final double cosLat = FastMath.cos(toRadians(center.y));
       double dY = Math.toDegrees(gridSizeMeters / SphericalDistanceLibrary.RADIUS_OF_EARTH_IN_M);
       double dX = dY / cosLat;

       SparseMatrixZSampleGrid<WTWD> sampleGrid = new SparseMatrixZSampleGrid<WTWD>(16,
               spt.getVertexCount(), dX, dY, center);
       
       sampleSPT(spt, sampleGrid, gridSizeMeters * 0.7, gridSizeMeters, V0,
               req.getMaxWalkDistance(), cosLat);

       // 4. Compute isolines
       ZMetric<WTWD> zMetric = new ZMetric<WTWD>() {
           @Override
           public int cut(WTWD zA, WTWD zB, WTWD z0) {
               double t0 = z0.tw / z0.w;
               double tA = zA.d > z0.d ? Double.POSITIVE_INFINITY : zA.tw / zA.w;
               double tB = zB.d > z0.d ? Double.POSITIVE_INFINITY : zB.tw / zB.w;
               if (tA < t0 && t0 <= tB)
                   return 1;
               if (tB < t0 && t0 <= tA)
                   return -1;
               return 0;
           }

           @Override
           public double interpolate(WTWD zA, WTWD zB, WTWD z0) {
               if (zA.d > z0.d || zB.d > z0.d) {
                   if (zA.d > z0.d && zB.d > z0.d)
                       throw new AssertionError("dA > d0 && dB > d0");
                   // Interpolate on d
                   double k = zA.d == zB.d ? 0.5 : (z0.d - zA.d) / (zB.d - zA.d);
                   return k;
               } else {
                   // Interpolate on t
                   double tA = zA.tw / zA.w;
                   double tB = zB.tw / zB.w;
                   double t0 = z0.tw / z0.w;
                   double k = tA == tB ? 0.5 : (t0 - tA) / (tB - tA);
                   return k;
               }
           }
       };
       
       DelaunayIsolineBuilder<WTWD> isolineBuilder = new DelaunayIsolineBuilder<WTWD>(sampleGrid,
               zMetric);
       isolineBuilder.setDebug(false);

       WTWD z0 = new WTWD();
       z0.w = 1.0;
       z0.tw = seconds;
       z0.d = D0;
       
       Geometry isoline = isolineBuilder.computeIsoline(z0);
       
       // cache isolines for later use -- TODO need to make this cache limited size 
       isolineCache.put(seconds, isoline);
       */
       return null; 
	}
	
	public void sampleSPT(ShortestPathTree spt, ZSampleGrid<WTWD> sampleGrid, final double d0,
            final double gridSizeMeters, final double v0, final double maxWalkDistance,
            final double cosLat) {

        final DistanceLibrary distanceLibrary = SphericalDistanceLibrary.getInstance();

        /**
         * Any given sample is weighted according to the inverse of the squared normalized distance
         * + 1 to the grid sample. We add to the sampling time a default off-road walk distance to
         * account for off-road sampling.
         */
        AccumulativeMetric<WTWD> accMetric = new AccumulativeMetric<WTWD>() {
            @Override
            public WTWD cumulateSample(Coordinate C0, Coordinate Cs, WTWD z, WTWD zS) {
                double t = z.wTime / z.w;
                double b = z.wBoardings / z.w;
                double wd = z.wWalkDist / z.w;
                double d = distanceLibrary.fastDistance(C0, Cs, cosLat);
                // additionnal time
                double dt = d / v0;
                // t weight
                double w = 1 / ((d + d0) * (d + d0));
                if (zS == null) {
                    zS = new WTWD();
                    zS.d = Double.MAX_VALUE;
                }
                zS.w = zS.w + w;
                zS.wTime = zS.wTime + w * (t + dt);
                zS.wBoardings = zS.wBoardings + w * b;
                zS.wWalkDist = zS.wWalkDist + w * (wd + d);
                if (d < zS.d)
                    zS.d = d;
                return zS;
            }

            /**
             * A Generated closing sample take 1) as off-road distance, the minimum of the off-road
             * distance of all enclosing samples, plus the grid size, and 2) as time the minimum
             * time of all enclosing samples plus the grid size * off-road walk speed as additional
             * time. All this are approximations.
             * 
             * TODO Is there a better way of computing this? Here the computation will be different
             * based on the order where we close the samples.
             */
            @Override
            public WTWD closeSample(WTWD zUp, WTWD zDown, WTWD zRight, WTWD zLeft) {
                double dMin = Double.MAX_VALUE;
                double tMin = Double.MAX_VALUE;
                double bMin = Double.MAX_VALUE;
                double wdMin = Double.MAX_VALUE;
                for (WTWD z : new WTWD[] { zUp, zDown, zRight, zLeft }) {
                    if (z == null)
                        continue;
                    if (z.d < dMin)
                        dMin = z.d;
                    double t = z.wTime / z.w;
                    if (t < tMin)
                        tMin = t;
                    double b = z.wBoardings / z.w;
                    if (b < bMin)
                        bMin = b;
                    double wd = z.wWalkDist / z.w;
                    if (wd < wdMin)
                        wdMin = wd;
                }
                WTWD z = new WTWD();
                z.w = 1.0;
                /*
                 * The computations below are approximation, but we are on the edge anyway and the
                 * current sample does not correspond to any computed value.
                 */
                z.wTime = tMin + gridSizeMeters / v0;
                z.wBoardings = bMin;
                z.wWalkDist = wdMin + gridSizeMeters;
                z.d = dMin + gridSizeMeters;
                return z;
            }
        };
        final AccumulativeGridSampler<WTWD> gridSampler = new AccumulativeGridSampler<WTWD>(
                sampleGrid, accMetric);

        SPTWalker johnny = new SPTWalker(spt);
        johnny.walk(new SPTVisitor() {
            @Override
            public final boolean accept(Edge e) {
                return e instanceof StreetEdge;
            }

            @Override
            public final void visit(Coordinate c, State s0, State s1, double d0, double d1) {
                double wd0 = s0.getWalkDistance() + d0;
                double wd1 = s0.getWalkDistance() + d1;
                double t0 = wd0 > maxWalkDistance ? Double.POSITIVE_INFINITY : s0.getActiveTime()
                        + d0 / v0;
                double t1 = wd1 > maxWalkDistance ? Double.POSITIVE_INFINITY : s1.getActiveTime()
                        + d1 / v0;
                if (!Double.isInfinite(t0) || !Double.isInfinite(t1)) {
                    WTWD z = new WTWD();
                    z.w = 1.0;
                    z.d = 0.0;
                    if (t0 < t1) {
                        z.wTime = t0;
                        z.wBoardings = s0.getNumBoardings();
                        z.wWalkDist = s0.getWalkDistance();
                    } else {
                        z.wTime = t1;
                        z.wBoardings = s1.getNumBoardings();
                        z.wWalkDist = s1.getWalkDistance();
                    }
                    gridSampler.addSamplingPoint(c, z);
                }
            }
        }, d0);
        gridSampler.close();
    }
	
	public static class WTWD {
        /* Total weight */
        public double w;

        // TODO Add generalized cost

        /* Weighted sum of time in seconds */
        public double wTime;

        /* Weighted sum of number of boardings (no units) */
        public double wBoardings;

        /* Weighted sum of walk distance in meters */
        public double wWalkDist;

        /* Minimum off-road distance to any sample */
        public double d;

        @Override
        public String toString() {
            return String.format("[t/w=%f,w=%f,d=%f]", wTime / w, w, d);
        }
    }
}
