package utils;

import java.awt.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class NaturalBreaksClassifier {
	
	public List<Bin> bins = new ArrayList<Bin>();
	
	public NaturalBreaksClassifier(ArrayList<Double> list, int numCategories, Color color1, Color color2) {
		
		Collections.sort(list);
		
		int[] values = buildJenksBreaks(list, numCategories);
		
		if(values.length == 0)
			return;
		
		double min = list.get(0);
        double next;
        double last = min;
		
		int span = (int) (Math.ceil((double) list.size() / (double)numCategories));
		
		Bin bin;	
		
		for(int i = 1; i <= numCategories; i++) {
			int active;
			
			Color c = interpolateColor(color1, color2, (float)((float)i / (float)numCategories));
			
			if(i==numCategories) {
                active = values[numCategories - 1];
                next = ((Double)list.get(active)).doubleValue();
                System.out.println("val "+(i*span)+":"+last+":"+next);

                bin = new Bin(last, next+0.0000001, c);
            }
            else {
                active = values[i - 1];
                next = ((Double)list.get(active)).doubleValue();
                bin = new Bin(last,next, c);
            }
            
            last = next;
            bins.add(bin);
        }
		
		// avoid roundoff errors by expanding lower and upper classes
		bins.get(0).lower -= 100;
		bins.get(bins.size() - 1).upper += 100;
	}
	
	public Color getColorValue(Double v) {
		for(Bin b : bins) {
			if(b.lower <= v && b.upper > v)
				return b.color;
		}
		
		return null;
	}

	private int[] buildJenksBreaks(ArrayList<Double> list, int numclass) {
		try {
			
			//int numclass;
			int numdata = list.size();
				        
			double[][] mat1 = new double[numdata + 1][numclass + 1];
			double[][] mat2 = new double[numdata + 1][numclass + 1];
				        
			for (int i = 1; i <= numclass; i++) {
				mat1[1][i] = 1;
				mat2[1][i] = 0;
				for (int j = 2; j <= numdata; j++)
					mat2[j][i] = Double.MAX_VALUE;
			}
			
			double v = 0;
			
			for (int l = 2; l <= numdata; l++) {
				
				double s1 = 0;
				double s2 = 0;
				double w = 0;
				
				for (int m = 1; m <= l; m++) {
					
					int i3 = l - m + 1;
					double val = ((Double)list.get(i3-1)).doubleValue();
	
					s2 += val * val;
					s1 += val;
					
					w++;
					v = s2 - (s1 * s1) / w;
					
					int i4 = i3 - 1;
					
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
			
			kclass[numclass - 1] = list.size() - 1;
			
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
