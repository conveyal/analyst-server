package utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import utils.QueryResults.QueryResultItem;

/**
 * A modified natural breaks classifier for bimodal data. Creates some number of classes about and below zero, with
 * one class that contains zero, and numCategories total classes. Does this by estimating how many classes to make
 * based on counts of values above and below zero, then running natural breaks for the values above and below
 * separately and merging the results.
 */
public class BimodalNaturalBreaksClassifier extends Classifier {
	private List<Bin> bins;
	
	/**
	 * Create a new bimodal natural breaks classifier
	 * @param qr the query results for which to create the classifier.
	 * @param numCategories the number of categories.
	 * @param center the center value (for instance, for an added/remove service scenario, this would be 0).
	 * @param color1 the color for values below the center
	 * @param centerColor the color of the center
	 * @param color2 the color for values above the center
	 */
	public BimodalNaturalBreaksClassifier(QueryResults qr, int numCategories,
			double center, Color color1, Color centerColor, Color color2) {
		// figure out how many values are below and how many are above
		// TODO: don't copy so much
		
		bins = new ArrayList<Bin>(numCategories);
		
		double[] values = new double[qr.items.size()];
		
		// these are where we insert the next values into the array
		// we insert values below middle at the start and values above at the end
		int lower = 0;
		int upper = qr.items.size() - 1;
		
		for (QueryResultItem item : qr.items.values()) {
			if (item.value == null) {
				throw new NullPointerException("Item value should not be null");
			}
			
			if (item.value > center) {
				values[upper] = item.value;
				upper--;
			}
			else {
				values[lower] = item.value;
				lower++;
			}
		}
		
		// we should have filled the entire list, so the pointers should be just past each other
		if (lower != upper + 1)
			throw new IllegalStateException("Array filled incorrectly (internal error).");
		
		if (values.length <= numCategories) {
			// just create a bin for each value
			Arrays.sort(values);
			
			for (double v : values) {
				// linear interpolation
				Color c;
				if (v > center)
					c = interpolateColor(centerColor, color2, (float) (v - center) / (float) (values[values.length - 1] - center));
				else
					c = interpolateColor(color1, centerColor, (float) (v - center) / (float) (values[0] - center));

				bins.add(new Bin(v - 0.00000001, v + 0.00000001, c));
			}
			
			addPercentagesToBins(qr.maxPossible);
			
			return;
		}
		
		// this is the first index of the values above the center 
		int centralIndex = lower;  
		
		// we are going to merge the central categories, so make one extra
		numCategories++;
		
		// figure out how many classes to assign to each
		int lowerClasses = numCategories / 2;		
		int upperClasses = numCategories - lowerClasses;
		
		// split the lists and do jenks twice
		double[] lowerValues = Arrays.copyOfRange(values, 0, lower - 1);
		Arrays.sort(lowerValues);
		
		double[] lowerBreaks;
		if (lowerClasses == 1) 
			lowerBreaks = new double[] { lowerValues[0] - 0.0000001, lowerValues[lowerValues.length - 1] + 0.0000001 };
		else
			lowerBreaks = NaturalBreaksClassifier.buildJenksBreaks(lowerValues, lowerClasses);
		
		double[] upperValues = Arrays.copyOfRange(values, upper + 1, values.length - 1);
		Arrays.sort(upperValues);
		
		double[] upperBreaks;
		if (upperClasses == 1) 
			upperBreaks = new double[] { upperValues[0] - 0.0000001, upperValues[upperValues.length - 1] + 0.0000001 };
		else
			upperBreaks = NaturalBreaksClassifier.buildJenksBreaks(upperValues, upperClasses);
		
		// build the bins
		// handle the bins that are entirely below 0
		// we are looping over the list two at a time, and skipping the last class
		for (int i = 0; i < lowerClasses - 1; i++) {
			Color c = interpolateColor(color1, centerColor, (float)((float)i / (float) (lowerClasses - 1)));
			Bin b = new Bin(lowerBreaks[i], lowerBreaks[i + 1], c);
			bins.add(b);
		}
		
		// build the middle bin, skipping the end of lower breaks and the start of upperBreaks
		// i.e. merge the middle bins
		Bin b = new Bin(lowerBreaks[lowerClasses - 1], upperBreaks[1], centerColor);
		bins.add(b);
		
		// build the upper bins
		// start from 1; the 0th break is contained in the merged middle class
		for (int i = 1; i < upperClasses; i++) {
			Color c = interpolateColor(centerColor, color2, (float)((float)i / (float) (upperClasses - 1)));
			Bin b2 = new Bin(upperBreaks[i], upperBreaks[i + 1], c);
			bins.add(b2);
		}
		
		addPercentagesToBins(qr.maxPossible);
	}

	@Override
	public List<Bin> getBins() {
		return bins;
	}
}
