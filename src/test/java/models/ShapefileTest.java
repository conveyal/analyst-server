package models;

import com.conveyal.analyst.server.AnalystMain;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import junit.framework.TestCase;
import models.Shapefile;
import org.junit.Test;
import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.analyst.PointSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Test features of a shapefile.
 */
public class ShapefileTest extends TestCase {
    /**
     * Make sure that pointset order is preserved when regenerating a pointset from a shapefile.
     *
     * Pointsets are created in one of two ways. When a shapefile is first uploaded, a pointset is created directly,
     * without going through MapDB. This prevents deserialization overhead. Pointsets may be recreated as needed later.
     * Make sure that the two methods produce the same pointsets.
     *
     * In theory this shouldn't matter as we shouldn't use pointset position anywhere. But it would be nice if the pointsets
     * were exactly the same.
     */
    @Test
    public void testPointsetOrder () throws Exception {
        AnalystMain.config.setProperty("application.data", Files.createTempDir().getAbsolutePath());
        File shpLoc = File.createTempFile("shp", ".zip");
        InputStream is = getClass().getResourceAsStream("shapefile.zip");
        FileOutputStream fos = new FileOutputStream(shpLoc);
        ByteStreams.copy(is, fos);
        is.close();
        fos.close();

        Shapefile sf = Shapefile.create(shpLoc, "PROJECT", "shapefile");
        // get the pointset that was built by the create function
        PointSet ps1 = sf.getPointSet();

        // null out the pointset reference, force it to be regenerated
        sf.pointSet = null;

        PointSet ps2 = sf.getPointSet();

        // should not be the same object
        assertFalse(ps1 == ps2);

        assertEquals(ps1.capacity, ps2.capacity);
        assertEquals(ps1.featureCount(), ps2.featureCount());

        for (int i = 0; i < ps1.capacity; i++) {
            PointFeature pf1 = ps1.getFeature(i);
            PointFeature pf2 = ps2.getFeature(i);

            assertEquals(pf1.getId(), pf2.getId());
            assertEquals(pf1.getLat(), pf2.getLat(), 1e-6);
            assertEquals(pf1.getLon(), pf2.getLon(), 1e-6);
        }
    }
}
