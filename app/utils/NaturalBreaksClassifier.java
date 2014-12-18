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

public class NaturalBreaksClassifier extends Classifier {
	
	public List<Bin> bins = new ArrayList<Bin>();
	
	public NaturalBreaksClassifier(QueryResults qr, int numCategories, Color color1, Color color2) {
		double[] list = new double[qr.items.size()];
		
		Iterator<QueryResultItem> qrIt = qr.items.values().iterator();
		
		for (int i = 0; i < list.length; i++) {
			list[i] = qrIt.next().value;
		}
		
		Arrays.sort(list);
		
		// we can't classify into more bins than we have values
		if (numCategories > list.length)
			numCategories = list.length;
		
		double[] breaks = buildJenksBreaks(list, numCategories);
		
		if(breaks.length == 0)
			return;
		
		for (int i = 0; i < numCategories; i++) {
			// numcategories - 1: fencepost problem. The highest value should get color2
			Color c;
			if (numCategories > 1)
				c = interpolateColor(color1, color2, (float)((float)i / (float) (numCategories - 1)));
			else
				c = interpolateColor(color1, color2, 0.5f);
			
			bins.add(new Bin(breaks[i], breaks[i + 1], c));
		}
		
		addPercentagesToBins(qr.maxPossible);
		
		bins.get(0).lower -= 0.00000001;
		bins.get(bins.size() - 1).upper += 0.00000001;
	}
	
	@Override
	public List<Bin> getBins() {
		return bins;
	}

	/**
	 * Build Jenks breaks for the given list, which is assumed to be already sorted.
	 */
	public static double[] buildJenksBreaks(double[] list, int numclass) {
		try {
			// If there are more than 2000 values, take a systematic sample
			// This is what is done by QGIS:
			// https://github.com/qgis/QGIS/blob/d4f64d9bde43c05458e867d04e73bc804435e7b6/src/core/symbology-ng/qgsgraduatedsymbolrendererv2.cpp#L832
			// QGIS also takes a 10% sample if that is larger, but that's not really necessary because
			// the central limit theorem states that confidence intervals around statistics are based
			// on the sample size, not the population size.
			
			// We use a systematic sample so that the renderer is deterministic. We take it after sorting
			// so that it is not influenced by the order of the input. The systematic sample should
			// approximate a random sample because it is taken over the entire range of input data
			if (list.length > 2000) {
				// figure out the increment to get ~2000 values
				int increment = (int) Math.floor(list.length / 2000D);
				
				// and how many values will that generate?
				// we add two because we also use the minimum and the maximum
				int sampleSize = (int) Math.floor(list.length / (double) increment) + 2;
				
				double[] sample = new double[sampleSize];
				
				// Make sure the min and the max values get in there.
				// maintain the array in sorted order though.
				sample[0] = list[0];
				sample[sampleSize - 1] = list[list.length - 1];
				
				for (int i = 0; i < sampleSize - 2; i++) {
					sample[i + 1] = list[i * increment];
				}
				
				list = sample;
			}
			//int numclass;
			int numdata = list.length;
				        
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
					double val = ((Double)list[i3-1]).doubleValue();
	
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
			double[] breaks = new double[numclass + 1];
			
			// list is sorted, first and last breaks are min and max
			breaks[numclass] = list[numdata - 1] + 0.0000001;
			breaks[0] = list[0] - 0.0000001;
			
			for (int j = numclass; j >= 2; j--) {
			
					//System.out.println("rank = " + mat1[k][j]);
					int id =  (int) (mat1[k][j]) - 2;
					//System.out.println("val = " + list.get(id));
					
					//System.out.println(mat2[k][j]);
					
					breaks[j - 1] = list[id];
	
					k = (int) mat1[k][j] - 1;
				
				
			}
			
			return breaks;
		}
		catch(Exception e) {
			return new double[0];
		}
	}

	public static class DoubleComp implements Comparator<Double> {
		public int compare(Double a, Double b) {
			if (((Double) a).doubleValue() < ((Double)b).doubleValue())
				return -1;
			if (((Double) a).doubleValue() > ((Double)b).doubleValue())
				return 1;
			
			return 0;
		}
	}
}
