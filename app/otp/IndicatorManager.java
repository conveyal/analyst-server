package otp;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import model.AttributeGroup;
import model.HaltonPoints;
import model.Indicator;
import model.IndicatorItem;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.opengis.referencing.FactoryException;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.MultiPoint;

public class IndicatorManager {
	
	private  ConcurrentHashMap<String, Indicator> indicators = new  ConcurrentHashMap<String, Indicator>();
	
	private  ConcurrentHashMap<String,  ConcurrentHashMap<String, HaltonPoints>> haltonCache = new  ConcurrentHashMap<String,  ConcurrentHashMap<String, HaltonPoints>>();
	
	private  ConcurrentHashMap<String, Collection<AttributeGroup>> indicatorMetadata = new  ConcurrentHashMap<String, Collection<AttributeGroup>>();
	
	private MutligraphSampleFactory mutligraphSampleFactory = new MutligraphSampleFactory();

	public IndicatorManager(MutligraphSampleFactory msf) {
		this.mutligraphSampleFactory = msf;
	}
	
	public void loadJson(File jsonFile, Blocks blocks) throws JsonParseException, JsonMappingException, IOException {
	
		ObjectMapper mapper = new ObjectMapper();
		Indicator indicator = mapper.readValue(jsonFile, Indicator.class);
		System.out.println("Loaded " + indicator.id + " with " + indicator.data.size() + " items.");
	
		indicator.index(mutligraphSampleFactory, blocks);
		
		indicatorMetadata.put(indicator.id, indicator.attributes);
		
		indicators.put(indicator.id, indicator);
		
	}
	
	public List<IndicatorItem> query(String indicatorId, Envelope env) {
		
		if(!indicators.containsKey(indicatorId))
			return null;
		
		return indicators.get(indicatorId).query(env);
	}
	
	public List<IndicatorItem> queryAll(String indicatorId) {
		
		if(!indicators.containsKey(indicatorId))
			return null;
		
		return indicators.get(indicatorId).queryAll();
	}
	
	public  ConcurrentHashMap<String, Collection<AttributeGroup>> indicatorMetadata() {
		return indicatorMetadata;
	}
	
	public HaltonPoints getHaltonPoints(String blockId, String indicatorId, String attribute) {
		String pointTypeId = indicatorId + "." + attribute;
		
		if(haltonCache.containsKey(blockId)){
			if(haltonCache.get(blockId).containsKey(pointTypeId)) {
				return haltonCache.get(blockId).get(pointTypeId);
			}
		}
		else {
			haltonCache.put(blockId, new  ConcurrentHashMap<String, HaltonPoints>());
		}
		
		// create and cache halton points
		
		HaltonPoints haltonPoints = indicators.get(indicatorId).idIndex.get(blockId).getHaltonPoints(attribute);
		
		haltonCache.get(blockId).put(pointTypeId, haltonPoints);
		
		return haltonPoints;
	}
}
