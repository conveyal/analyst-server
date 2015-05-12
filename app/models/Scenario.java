package models;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

import utils.DataStore;

/**
 * Scenarios are lightweight modifications on top of graphs. For example, you might have a single San Francisco
 * graph with transit data for the entire Bay Area. This is a several-GB object. Paul might be analyzing the
 * effects of a new bus line, which is represented as a scenario. Leila is analyzing the effects of a construction
 * project that will temporarily suspend streetcar service. Sarah is analyzing the effects of a new Transbay tube.
 * All use the same base graph but with different lightweight lists of modifications.
 */
public class Scenario implements Serializable {
	public static final long serialVersionUID = 1L;
	
	// called transport_scenario not scenario because scenario is what bundles used to be called.
	private static final DataStore<Scenario> scenarioStore = new DataStore<Scenario>("transport_scenario", true);
	
	/** The bundle that this scenario is based on */
	public String bundleId;
	
	/** The project this scenario is associated with */
	public String projectId;
	
	/** The ID of this scenario */
	public String id;
	
	/** The project that this scenario is associated with */
	
	/** A list of banned routes */
	public List<Bundle.RouteSummary> bannedRoutes;
	
	// TODO additional types of modifications
	
	/** Create and save a new scenario based on the named bundle */
	public static Scenario create (Bundle bundle) {
		Scenario s = new Scenario();
		s.bundleId = bundle.id;
		s.projectId = bundle.projectId;
		s.generateId();
		s.save();
		return s;
	}
	
	public void save () {
		scenarioStore.save(id, this);
	}
	
	public static Scenario getScenario (String id) {
		return scenarioStore.getById(id);
	}
	
	public static Collection<Scenario> getAll () {
		return scenarioStore.getAll();
	}
	
	public static Collection<Scenario> getByProject (final String projectId) {
		return Collections2.filter(getAll(), new Predicate<Scenario>() {

			@Override
			public boolean apply(Scenario arg0) {
				return projectId.equals(arg0.projectId);
			}
		});
	}

	public void generateId() {
		this.id = UUID.randomUUID().toString();
	}

	/** has this scenario been saved previously? */
	public boolean exists() {
		return scenarioStore.contains(id);
	}

	public void delete() {
		scenarioStore.delete(id);
	}
}
