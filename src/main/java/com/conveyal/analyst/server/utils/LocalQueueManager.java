package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import models.Shapefile;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.SampleSet;
import org.opentripplanner.analyst.cluster.AnalystClusterRequest;
import org.opentripplanner.analyst.cluster.ResultEnvelope;
import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.profile.IsochroneGenerator;
import org.opentripplanner.profile.RepeatedRaptorProfileRouter;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.standalone.CommandLineParameters;
import org.opentripplanner.standalone.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/**
 * Rudimentary queue manager (for testing purposes only) that performs local computation.
 * Does not have any logic for graph affinity or anything of that sort, or even high-priority vs.
 * low priority.
 */
public class LocalQueueManager extends QueueManager {
    private static final Logger LOG = LoggerFactory.getLogger(LocalQueueManager.class);

    /** the queue of requests */
    private Queue<AnalystClusterRequest> queue = new ConcurrentLinkedQueue<>();

    /** the callbacks */
    private Multimap<String, Predicate<ResultEnvelope>> callbacks = HashMultimap.create();

    private Router router;
    private SampleSet sampleSet;

    LocalQueueManager () {
        LOG.warn("Working offline; this is intended for testing only and will not perform well under load");

        // set up queue watching
        new Thread(() -> {
            int previousQueueSize = -1;
            while (true) {
                try {
                    int queueSize = queue.size();

                    if (queueSize != previousQueueSize) {
                        LOG.info("queue size {}", queueSize);
                        previousQueueSize = queueSize;
                    }

                    AnalystClusterRequest req = queue.poll();

                    if (req != null)
                        handle(req);
                    else
                        Thread.sleep(1000);

                } catch (Exception e) {
                    e.printStackTrace();
                    // the loop continues
                }
            }
        }).start();
    }

    @Override public void enqueue(Collection<AnalystClusterRequest> requests) {
        queue.addAll(requests);
    }

    @Override public ResultEnvelope getSinglePoint(AnalystClusterRequest req) throws IOException {
        return compute(req);
    }

    @Override public void addCallback(String jobId, Predicate<ResultEnvelope> callback) {
        callbacks.put(jobId, callback);
    }

    @Override public void cancelJob(String jobId) {
        LOG.warn("Job cancellation is not implemented when working offline. We recommend you use a computation cluster.");
    }

    /** handle a result envelope coming off the queue */
    private void handle (AnalystClusterRequest req) {
        ResultEnvelope re = compute(req);

        for (Predicate<ResultEnvelope> callback : callbacks.get(re.jobId)) {
            if (!callback.test(re))
                callbacks.remove(re.jobId, callback);
        }
    }

    /** compute a result envelope */
    private ResultEnvelope compute (AnalystClusterRequest req) {
        LOG.debug("handling {}", req);

        if (req.profileRequest == null)
            throw new UnsupportedOperationException("Only profile requests are supported locally");


        // check the graph
        if (router == null || !req.graphId.equals(router.id)) {
            buildGraph(req.graphId);
        }

        // get the pointset
        PointSet ps;

        boolean isochrone = req.destinationPointsetId == null;

        if (!isochrone)
            ps = Shapefile.getShapefile(req.destinationPointsetId).getPointSet();
        else
            ps = PointSet.regularGrid(router.graph.getExtent(), IsochroneGenerator.GRID_SIZE_METERS);

        SampleSet ss = new SampleSet(ps, router.graph.getSampleFactory());

        RepeatedRaptorProfileRouter rrpr = new RepeatedRaptorProfileRouter(router.graph, req.profileRequest, ss);
        rrpr.route();

        ResultSet.RangeSet rsrs = rrpr.makeResults(req.includeTimes, !isochrone, isochrone);
        ResultEnvelope re = new ResultEnvelope();
        re.worstCase = rsrs.max;
        re.avgCase = rsrs.avg;
        re.bestCase = rsrs.min;
        re.destinationPointsetId = req.destinationPointsetId;
        re.jobId = req.jobId;
        re.profile = true;

        return re;
    }

    /** build a graph */
    private synchronized void buildGraph (String graphId) {
        if (router != null && graphId.equals(router.id))
            return;

        File graphDir = new File(AnalystMain.config.getProperty("application.data"), "graphs");
        graphDir = new File(graphDir, graphId);
        LOG.info("Building graph {} from files in {}", graphId, graphDir);

        CommandLineParameters params = new CommandLineParameters();
        params.build = graphDir;
        params.inMemory = true;
        GraphBuilder gbt = GraphBuilder.forDirectory(params, params.build);
        gbt.run();
        Graph g = gbt.getGraph();
        g.routerId = graphId;
        g.index(new DefaultStreetVertexIndexFactory());
        g.index.clusterStopsAsNeeded();
        router = new Router(graphId, g);
        LOG.info("Done building graph");
    }
}
