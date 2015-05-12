package plugins;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import image.roi.ROIPlus;

import java.util.TreeMap;

import jex.statics.JEXStatics;
import jex.utilities.FunctionUtility;
import jex.utilities.ROIUtility;
import miscellaneous.CSVList;
import miscellaneous.StatisticsUtility;

import org.scijava.plugin.Plugin;

import tables.Dim;
import tables.DimTable;
import tables.DimensionMap;
import weka.core.converters.JEXTableWriter;
import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataReader.RoiReader;
import Database.DataWriter.FileWriter;
import Database.DataWriter.ImageWriter;
import Database.SingleUserDatabase.JEXWriter;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;

@Plugin(
		type = JEXPlugin.class,
		name="CTC - Threshold Background Noise",
		menuPath="CTC Toolbox",
		visible=true,
		description="Determine background noise, calculate threshold based on multiples of sigma, and create thresholded images."
		)
public class CTC_ThresholdBGNoise extends JEXPlugin {
	
	public CTC_ThresholdBGNoise()
	{}
	
	/////////// Define Inputs ///////////
	
	@InputMarker(uiOrder=1, name="Image", type=MarkerConstants.TYPE_IMAGE, description="Image to be adjusted.", optional=false)
	JEXData imageData;
	
	@InputMarker(uiOrder=2, name="Roi (Optional)", type=MarkerConstants.TYPE_ROI, description="Image to be adjusted.", optional=true)
	JEXData roiData;
	
	/////////// Define Parameters ///////////
	
	@ParameterMarker(uiOrder=1, name="Color Dim Name", description="Name of the color dimension if any. If not found, it is assumed there is no color dimension.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="Color")
	String colorDimName;
	
	@ParameterMarker(uiOrder=2, name="Number of Sigma", description="How many multiples of of sigma above background should the threshold be set?", ui=MarkerConstants.UI_TEXTFIELD, defaultText="5")
	double nSigma;
	
	@ParameterMarker(uiOrder=3, name="Single Threshold per Color?", description="Calculate a single threhsold for each color or a threshold for each image in data set. The combined thresh is calcualted as the median of the individual thresholds.", ui=MarkerConstants.UI_CHECKBOX, defaultBoolean=false)
	boolean threshPerColor;
	
	@ParameterMarker(uiOrder=4, name="Mean, median, or mode?", description="Should the threshold be measured relative to the image mean, median, or mode?", ui=MarkerConstants.UI_DROPDOWN, choices={"Mean", "Median", "Mode"}, defaultChoice=2)
	String method;
	
	/////////// Define Outputs ///////////
	
	@OutputMarker(uiOrder=1, name="Thresholded Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant thresholded image", enabled=true)
	JEXData outputImage;
	
	@OutputMarker(uiOrder=2, name="Stats", type=MarkerConstants.TYPE_FILE, flavor="", description="The measures and stats used to determine the thresholds", enabled=true)
	JEXData outputStats;

	@OutputMarker(uiOrder=3, name="Thresholds", type=MarkerConstants.TYPE_FILE, flavor="", description="The resultant calculated thresholds based on the number of sigma set by the user.", enabled=true)
	JEXData outputThresholds;
	
	@Override
	public int getMaxThreads()
	{
		return 5;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean run(JEXEntry entry)
	{
		// Collect the inputs
		if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		TreeMap<DimensionMap,ROIPlus> rois = new TreeMap<DimensionMap,ROIPlus>();
		if(roiData != null && roiData.getTypeName().getType().equals(JEXData.ROI))
		{
			rois = RoiReader.readObjectToRoiMap(roiData);
		}
		
		// Run the function
		TreeMap<DimensionMap,String> imageMap = ImageReader.readObjectToImagePathTable(imageData);
		
		TreeMap<String,Object> output = null;
		if(threshPerColor)
		{
			output = this.calcPerColor(colorDimName, imageMap, imageData.getDimTable(), rois, nSigma);
		}
		else
		{
			output = this.calcIndividual(true, imageMap, imageData.getDimTable(), rois, nSigma);
		}
		
		if(output == null || !(Boolean) output.get("Success"))
		{
			return false;
		}
		
		outputImage = ImageWriter.makeImageStackFromPaths("temp", (TreeMap<DimensionMap,String>) output.get("outputMap"));
		String valuePath2 = JEXTableWriter.writeTable("temp", (TreeMap<DimensionMap,Double>) output.get("statsMap"));
		outputStats = FileWriter.makeFileObject("temp", null, valuePath2);
		String valuePath3 = JEXTableWriter.writeTable("temp", (TreeMap<DimensionMap,Double>) output.get("outputThreshMap"));
		outputThresholds = FileWriter.makeFileObject("temp", null, valuePath3);
		
		// Return status
		return true;
	}
	
	@SuppressWarnings("unchecked")
	public TreeMap<String,Object> calcPerColor(String colorDimName, TreeMap<DimensionMap,String> imageMap, DimTable table, TreeMap<DimensionMap,ROIPlus> rois, double nSigma)
	{
		Dim colorDim = table.getDimWithName(colorDimName);
		if(colorDim == null)
		{
			return this.calcIndividual(true, imageMap, table, rois, nSigma);
		}
		
		TreeMap<DimensionMap,String> outputMap = new TreeMap<DimensionMap,String>();
		TreeMap<DimensionMap,Double> statsMap = new TreeMap<DimensionMap,Double>();
		TreeMap<DimensionMap,Double> outputThreshMap = new TreeMap<DimensionMap,Double>();
		boolean success = true;
		TreeMap<String,Object> temp = null;
		for (DimTable subTable : table.getSubTableIterator(colorDimName))
		{
			temp = this.calcIndividual(false, imageMap, subTable, rois, nSigma);
			Dim colorSubDim = subTable.getDimWithName(colorDimName);
			
			// Get the median threshold
			TreeMap<DimensionMap,Double> thresholds = (TreeMap<DimensionMap,Double>) temp.get("outputThreshMap");
			Double thresh = StatisticsUtility.median(thresholds.values());
			
			// Threshold all images for this subtable using the threshold
			for (DimensionMap map : subTable.getMapIterator())
			{
				// Get the image
				ImagePlus im = new ImagePlus(imageMap.get(map));
				FloatProcessor ip = (FloatProcessor) im.getProcessor().convertToFloat();
				
				// Threshold it
				FunctionUtility.imThresh(ip, thresh, false);
				if(this.isCanceled())
				{
					success = false;
					return this.makeTreeMap("Success", false);
				}
				String path = JEXWriter.saveImage(FunctionUtility.makeImageToSave(ip, "false", 8)); // Creating black and white image
				outputMap.put(map.copy(), path);
			}
			
			// Combine rest of new data with current output data
			statsMap.putAll((TreeMap<DimensionMap,Double>) temp.get("statsMap"));
			outputThreshMap.put(new DimensionMap(colorSubDim.dimName + "=" + colorSubDim.valueAt(0)), thresh);
		}
		
		return this.makeTreeMap("Success,outputMap,statsMap,outputThreshMap", success, outputMap, statsMap, outputThreshMap);
	}
	
	public TreeMap<String,Object> calcIndividual(boolean threshImages, TreeMap<DimensionMap,String> imageMap, DimTable dimsToProcess, TreeMap<DimensionMap,ROIPlus> rois, double nSigma)
	{
		int percentage = 0;
		double count = 0, total = dimsToProcess.mapCount();
		TreeMap<DimensionMap,String> outputMap = new TreeMap<DimensionMap,String>();
		TreeMap<DimensionMap,Double> statsMap = new TreeMap<DimensionMap,Double>();
		TreeMap<DimensionMap,Double> outputThreshMap = new TreeMap<DimensionMap,Double>();
		boolean success = true;
		for (DimensionMap map : dimsToProcess.getMapIterator())
		{
			if(this.isCanceled())
			{
				return this.makeTreeMap("Success", false);
			}
			
			// Get the image
			ImagePlus im = new ImagePlus(imageMap.get(map));
			FloatProcessor ip = (FloatProcessor) im.getProcessor().convertToFloat();
			
			// Do threshold
			ROIPlus roi = rois.get(map);
			float[] tempPixels = null;
			if(roi != null)
			{
				tempPixels = ROIUtility.getPixelsInRoi(ip, roi);
			}
			if(tempPixels == null)
			{
				tempPixels = (float[]) ip.getPixels();
			}
			double[] pixels = new double[tempPixels.length];
			int i = 0;
			for (float f : tempPixels)
			{
				pixels[i] = f;
				i++;
			}
			tempPixels = null;
			double center = -1;
			if(method.equals("Mean"))
			{
				center = StatisticsUtility.mean(pixels);
			}
			else if(method.equals("Median"))
			{
				center = StatisticsUtility.median(pixels);
			}
			else
			{
				double[] modes = StatisticsUtility.modes(pixels);
				center = modes[0];
			}
			if(this.isCanceled())
			{
				return this.makeTreeMap("Success", false);
			}
			double mad = StatisticsUtility.mad(center, pixels); // Multiplier converts the mad to an approximation of the standard deviation without the effects of outliers
			double threshold = center + nSigma * mad;
			String path = null;
			if(threshImages)
			{
				FunctionUtility.imThresh(ip, threshold, false);
				if(this.isCanceled())
				{
					success = false;
					return this.makeTreeMap("Success", false);
				}
				path = JEXWriter.saveImage(FunctionUtility.makeImageToSave(ip, "false", 8)); // Creating black and white image
			}
			
			if(path != null)
			{
				outputMap.put(map, path);
			}
			DimensionMap map2 = map.copy();
			
			map2.put("Measurement", method);
			statsMap.put(map2.copy(), center);
			map2.put("Measurement", "MAD");
			statsMap.put(map2.copy(), mad);
			outputThreshMap.put(map, threshold);
			
			// Update progress
			count = count + 1;
			percentage = (int) (100 * (count / total));
			JEXStatics.statusBar.setProgressPercentage(percentage);
		}
		
		TreeMap<String,Object> ret = this.makeTreeMap("Success,outputMap,statsMap,outputThreshMap", success, outputMap, statsMap, outputThreshMap);
		return ret;
	}
	
	public TreeMap<String,Object> makeTreeMap(String csvString, Object... items)
	{
		CSVList names = new CSVList(csvString);
		if(names.size() != items.length)
		{
			return null;
		}
		TreeMap<String,Object> ret = new TreeMap<String,Object>();
		for (int i = 0; i < names.size(); i++)
		{
			ret.put(names.get(i), items[i]);
		}
		return ret;
	}
}
