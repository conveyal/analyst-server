package utils;

import java.awt.Color;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a bin for classification.
 */
public class Bin {
	
	public Double lower;
	public Double upper;
	
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