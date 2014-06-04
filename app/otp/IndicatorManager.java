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

import org.opengis.referencing.FactoryException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.MultiPoint;

public class IndicatorManager {
	
	private  ConcurrentHashMap<String, Indicator> indicators = new  ConcurrentHashMap<String, Indicator>();
	
	private  ConcurrentHashMap<String,  ConcurrentHashMap<String, HaltonPoints>> haltonCache = new  ConcurrentHashMap<String,  ConcurrentHashMap<String, HaltonPoints>>();
	
	private  ConcurrentHashMap<String, Collection<AttributeGroup>> indicatorMetadata = new  ConcurrentHashMap<String, Collection<AttributeGroup>>();
	
	private SampleFactory sampleSource;
	

	
	public IndicatorManager(SampleFactory sampleSource) {
		this.sampleSource = sampleSource;
	}
	
	public void loadJson(File jsonFile, Blocks blocks) throws JsonParseException, JsonMappingException, IOException {
	
		ObjectMapper mapper = new ObjectMapper();
		Indicator inc = mapper.readValue(jsonFile, Indicator.class);
		System.out.println("Loaded " + inc.id + " with " + inc.data.size() + " items.");
	
		inc.index(sampleSource, blocks);
		
		indicatorMetadata.put(inc.id, inc.attributes);
		
		indicators.put(inc.id, inc);
		
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
