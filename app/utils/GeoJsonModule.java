//Copyright Alex Bertram
//https://github.com/bedatadriven/jackson-geojson
//	
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/


package utils;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.vividsolutions.jts.geom.Geometry;

public class GeoJsonModule extends SimpleModule {

	public GeoJsonModule() {
		super("GeoJson", new Version(1, 0, 0, null, null, null));

		//addSerializer(Geometry.class, new GeometrySerializer());
		//addDeserializer(Geometry.class, new GeometryDeserializer());
	}
}