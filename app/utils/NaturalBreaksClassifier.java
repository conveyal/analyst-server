package utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import utils.QueryResults.QueryResultItem;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class NaturalBreaksClassifier {
	
	public List<Bin> bins = new ArrayList<Bin>();
	
	public NaturalBreaksClassifier(QueryResults qr, int numCategories, Color color1, Color color2) {
		
		double[] qrVals = new double[qr.items.size()];
		short[] projected = new short[qr.items.size()];
		
		Iterator<QueryResultItem> qrIt = qr.items.values().iterator();
		for (int i = 0; i < qrVals.length; i++) {
			QueryResultItem item = qrIt.next();
			qrVals[i] = item.value;
		}
		
		Arrays.sort(qrVals);
		
		for (int i = 0; i < qrVals.length; i++) {
			projected[i] = project(qrVals[i], qr.maxValue, qr.minValue);
		}
		
		int[] values = buildJenksBreaks(projected, numCategories);
		
		if(values.length == 0)
			return;
		
		double min = qrVals[0];
        double next;
        double last = min;
		
		int span = (int) (Math.ceil((double) qrVals.length / (double)numCategories));
		
		Bin bin;	
		
		for(int i = 1; i <= numCategories; i++) {
			int active;
			
			Color c = interpolateColor(color1, color2, (float)((float)i / (float)numCategories));
			
			if(i==numCategories) {
                active = values[numCategories - 1];
                next = qrVals[active];
                System.out.println("val "+(i*span)+":"+last+":"+next);

                bin = new Bin(last, next+0.0000001, c);
            }
            else {
                active = values[i - 1];
                next = qrVals[active];
                bin = new Bin(last,next, c);
            }
            
            last = next;
            bins.add(bin);
        }
	}
	
	/**
	 * Project value to a short, using the full range of short, for a scale between minValue and maxValue.
	 */
	private short project(double value, double maxValue, double minValue) {
		double frac = (value - minValue) / (maxValue - minValue);
		return (short) Math.round(frac * ((int) Short.MAX_VALUE - (int) Short.MIN_VALUE) + Short.MIN_VALUE); 
	}

	public Color getColorValue(Double v) {
		for(Bin b : bins) {
			if(b.lower <= v && b.upper > v)
				return b.color;
		}
		
		return null;
	}

	/**
	 * Calculate Jenks breaks for a list of shorts. We use integers to make math fast; see the
	 * project() and unproject() functions
	 * @returns indices of the breaks in the data 
	 */
	private int[] buildJenksBreaks(short[] list, int numclass) {
		try {
			
			//int numclass;
			int numdata = list.length;

			long[][] mat1 = new long[numdata + 1][numclass + 1];
			long[][] mat2 = new long[numdata + 1][numclass + 1];
				        
			for (int i = 1; i <= numclass; i++) {
				mat1[1][i] = 1;
				mat2[1][i] = 0;
				for (int j = 2; j <= numdata; j++)
					mat2[j][i] = Long.MAX_VALUE;
			}
			
			long v = 0;
			
			long s1, s2, w, val;
			int i3, i4;
			for (int l = 2; l <= numdata; l++) {
				
				s1 = 0;
				s2 = 0;
				w = 0;
				
				for (int m = 1; m <= l; m++) {
					
					i3 = l - m + 1;
					val = list[i3-1];
	
					s2 += val * val;
					s1 += val;
					
					w++;
					
					// there is a divide sign here and all of the variables involved are integers.
					// this should cause good programmers everywhere to shudder, however it is fine
					// because s1^2 has to be a multiple of w, because s1 has to be a multiple of w,
					// because an integer has been added to s1 each time w has been incremented  
					v = s2 - (s1 * s1) / w;
					
					i4 = i3 - 1;
					
					if (i4 != 0) {
						for (int j = 2; j <= numclass; j++) {
							if (mat2[l][j] >= (v + mat2[i4][j - 1])) {
								mat1[l][j] = i3;
								mat2[l][j] = v + mat2[i4][j - 1];
							}
						}
					}
				}
				
				mat1[l][1] = 1;
				mat2[l][1] = v;
			}
	
			int k = numdata;
			int[] kclass = new int[numclass];
			
			// set the highest break to the maximum data value
			kclass[numclass - 1] = list.length - 1;
			
			for (int j = numclass; j >= 2; j--) {
			
					//System.out.println("rank = " + mat1[k][j]);
					int id =  (int) (mat1[k][j]) - 2;
					//System.out.println("val = " + list.get(id));
					
					//System.out.println(mat2[k][j]);
					
					kclass[j - 2] = id;
	
					k = (int) mat1[k][j] - 1;
				
				
			}
			
			return kclass;
		}
		catch(Exception e) {
		 return new int[0];	
		}
	}

	public class DoubleComp implements Comparator<Double> {
		public int compare(Double a, Double b) {
			if (((Double) a).doubleValue() < ((Double)b).doubleValue())
				return -1;
			if (((Double) a).doubleValue() > ((Double)b).doubleValue())
				return 1;
			
			return 0;
		}
	}
	
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
	
	// from http://harmoniccode.blogspot.com.au/2011/04/bilinear-color-interpolation.html
	public static java.awt.Color interpolateColor(final Color color1, final Color color2, float fraction)
    {            
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
