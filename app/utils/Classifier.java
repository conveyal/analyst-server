package utils;

import java.awt.Color;
import java.util.List;

public abstract class Classifier {
	public Color getColorValue(double value) {
		List<Bin> bins = getBins();
		
		for(Bin b : bins) {
			if(b.lower <= value && b.upper > value)
				return b.color;
		}
		
		return null;
	}
	
	public abstract List<Bin> getBins();
	
	/**
	 * Add percentage values to the bins, as percentages of the total possible.
	 * Note that this does not calculate percent change in a subtraction!
	 */
	protected void addPercentagesToBins(double totalPossible) {
		List<Bin> bins = getBins();
		
		for (Bin bin : bins) {
			bin.upperPercent = 100 * bin.upper / totalPossible;
			bin.lowerPercent = 100 * bin.lower / totalPossible;
		}
	}
	
	// from http://harmoniccode.blogspot.com.au/2011/04/bilinear-color-interpolation.html
	public static java.awt.Color interpolateColor(final Color color1, final Color color2, float fraction) {            
	    final float INT_TO_FLOAT_CONST = 1f / 255f;
	    fraction = Math.min(fraction, 1f);
	    fraction = Math.max(fraction, 0f);
	    
	    final float RED1 = color1.getRed() * INT_TO_FLOAT_CONST;
	    final float GREEN1 = color1.getGreen() * INT_TO_FLOAT_CONST;
	    final float BLUE1 = color1.getBlue() * INT_TO_FLOAT_CONST;
	    final float ALPHA1 = color1.getAlpha() * INT_TO_FLOAT_CONST;
	
	    final float RED2 = color2.getRed() * INT_TO_FLOAT_CONST;
	    final float GREEN2 = color2.getGreen() * INT_TO_FLOAT_CONST;
	    final float BLUE2 = color2.getBlue() * INT_TO_FLOAT_CONST;
	    final float ALPHA2 = color2.getAlpha() * INT_TO_FLOAT_CONST;
	
	    final float DELTA_RED = RED2 - RED1;
	    final float DELTA_GREEN = GREEN2 - GREEN1;
	    final float DELTA_BLUE = BLUE2 - BLUE1;
	    final float DELTA_ALPHA = ALPHA2 - ALPHA1;
	
	    float red = RED1 + (DELTA_RED * fraction);
	    float green = GREEN1 + (DELTA_GREEN * fraction);
	    float blue = BLUE1 + (DELTA_BLUE * fraction);
	    float alpha = ALPHA1 + (DELTA_ALPHA * fraction);
	
	    red = Math.min(red, 1f);
	    red = Math.max(red, 0f);
	    green = Math.min(green, 1f);
	    green = Math.max(green, 0f);
	    blue = Math.min(blue, 1f);
	    blue = Math.max(blue, 0f);
	    alpha = Math.min(alpha, 1f);
	    alpha = Math.max(alpha, 0f);
	
	    return new Color(red, green, blue, alpha);        
	}
}
