package models;

import java.io.Serializable;

import utils.Bounds;


/**
 * We used to call bundles scenarios, when we didn't have separate concepts of data and modifications.
 * 
 * We keep this class around so that we can read old data directories. All of the meat 
 */
@Deprecated
public class Scenario implements Serializable {

	private static final long serialVersionUID = 1L;

	public String id;
	public String projectId;
	public String name;
	public String description;
	
	public String timeZone;
	
	public Boolean processingGtfs = false;
	public Boolean processingOsm = false;
	public Boolean failed = false;
	
	public Bounds bounds;
}
