package utils;

import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;

import java.io.Serializable;

public class OneToManyRequest extends AnalystClusterRequest implements Serializable {
	public RoutingRequest options;

	public OneToManyRequest(PointFeature from, String to, RoutingRequest options, String graphId) {
		super(from, to, graphId, false);
		
		this.options = options.clone();
		this.options.batch = true;
		this.options.rctx = null;
		this.options.from = new GenericLocation(from.getLat(), from.getLon());
	}

	/** used for single point requests with from specified by options */
	public OneToManyRequest(String to, RoutingRequest options, String graphId) {
		super(null, to, graphId, false);

		this.options = options.clone();
		this.options.batch = true;
		this.options.rctx = null;
	}
	
	/** used for deserialization from JSON */
	public OneToManyRequest () { /* nothing */ }
}
