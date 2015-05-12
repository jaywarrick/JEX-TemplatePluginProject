package plugins;

import ij.ImagePlus;
import ij.plugin.filter.RankFilters;
import ij.process.ImageProcessor;

import java.util.TreeMap;

import jex.statics.JEXStatics;

import org.scijava.plugin.Plugin;

import tables.DimensionMap;
import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataWriter.ImageWriter;
import Database.SingleUserDatabase.JEXWriter;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;

/**
 * This is a JEXperiment function template To use it follow the following instructions
 * 
 * 1. Fill in all the required methods according to their specific instructions 2. Place the file in the Functions/SingleDataPointFunctions folder 3. Compile and run JEX!
 * 
 * JEX enables the use of several data object types The specific API for these can be found in the main JEXperiment folder. These API provide methods to retrieve data from these objects, create new objects and handle the data they contain.
 * 
 * @author erwinberthier
 * 
 */

@Plugin(
		type = JEXPlugin.class,
		name="Example - Image Filters",
		menuPath="Template Functions",
		visible=true,
		description="Use a predefined image filter and specify the filter radius."
		)
public class Example_Filters extends JEXPlugin {
	
	public static String MEAN = "mean", MIN = "min", MAX = "max", MEDIAN = "median", VARIANCE = "variance";

	public Example_Filters()
	{}
	
	/////////// Define Inputs ///////////
		
	@InputMarker(uiOrder=1, name="Image", type=MarkerConstants.TYPE_IMAGE, description="Image to be adjusted.", optional=false)
	JEXData imageData;
	
	/////////// Define Parameters ///////////

	@ParameterMarker(uiOrder=1, name="Filter Type", description="Type of filter to apply.", ui=MarkerConstants.UI_DROPDOWN, choices={ "mean", "min", "max", "median", "variance" }, defaultChoice=0)
	String method;
	
	@ParameterMarker(uiOrder=2, name="Radius", description="Radius of filter in pixels.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="2.0")
	double radius;
	
	@ParameterMarker(uiOrder=3, name="Output Bit-Depth", description="Bit-Depth of the output image", ui=MarkerConstants.UI_DROPDOWN, choices={ "8", "16", "32" }, defaultChoice=2)
	int bitDepth;
	
	/////////// Define Outputs ///////////
	
	@OutputMarker(uiOrder=1, name="Filtered Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant filtered image", enabled=true)
	JEXData output;
	
	@Override
	public int getMaxThreads()
	{
		return 10;
	}

	// ----------------------------------------------------
	// --------- THE ACTUAL MEAT OF THIS FUNCTION ---------
	// ----------------------------------------------------
	
	/**
	 * Perform the algorithm here
	 * 
	 */
	@Override
	public boolean run(JEXEntry optionalEntry)
	{
		// check image validation
		if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
			return false;
		
		// Run the function
		TreeMap<DimensionMap,String> imageMap = ImageReader.readObjectToImagePathTable(imageData);
		TreeMap<DimensionMap,String> outputImageMap = new TreeMap<DimensionMap,String>();
		int count = 0, percentage = 0;
		for (DimensionMap map : imageMap.keySet())
		{
			if(this.isCanceled())
			{
				return false;
			}
			ImagePlus im = new ImagePlus(imageMap.get(map));
			ImageProcessor ip = im.getProcessor().convertToFloat();
			
			// //// Begin Actual Function
			RankFilters rF = new RankFilters();
			rF.setup(method, im);
			rF.makeKernel(radius);
			rF.run(ip);
			// //// End Actual Function
			
			ImageProcessor toSave = ip;
			if(bitDepth == 8)
			{
				toSave = ip.convertToByte(false);
			}
			else if(bitDepth == 16)
			{
				toSave = ip.convertToShort(false);
			}
			
			String path = JEXWriter.saveImage(toSave);
			
			if(path != null)
			{
				outputImageMap.put(map, path);
			}
			
			count = count + 1;
			percentage = (int) (100 * ((double) (count) / ((double) imageMap.size())));
			JEXStatics.statusBar.setProgressPercentage(percentage);
		}
		if(outputImageMap.size() == 0)
		{
			return false;
		}
		
		this.output = ImageWriter.makeImageStackFromPaths("temp", outputImageMap);
		
		
		// Return status
		return true;
	}
	
}
