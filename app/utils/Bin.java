package utils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.awt.*;

/**
 * Represents a bin for classification.
 */
public class Bin {
	
	public Double lower;
	public Double upper;
	
	/**
	 * The percentage of the total for this bin's lower bound.
	 * This is not a percent change, but is relative to the total.
	 */
	public Double lowerPercent;
	
	/** The percentage of the total for this bin's upper bound. Above comment applies. */
	public Double upperPercent;
	
	@JsonIgnore
	public Color color;
	
	public String hexColor;
	
	public Bin(Double lower, Double upper, Color color) {
		this.lower = lower;
		this.upper = upper;
		this.color = color;
	
		this.hexColor = String.format("#%02x%02x%02x", this.color.getRed(), this.color.getGreen(), this.color.getBlue());
	}
}