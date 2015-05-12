package plugins;

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
import ij.ImagePlus;
import ij.process.Blitter;
import ij.process.ByteProcessor;

import java.util.Map.Entry;
import java.util.TreeMap;

import org.scijava.plugin.Plugin;

import tables.DimensionMap;

/**
 * This is a JEXperiment function template To use it follow the following instructions
 * 
 * 1. Fill in all the required methods according to their specific instructions 2. Place the file in the Functions/SingleDataPointFunctions folder 3. Compile and run JEX!
 * 
 * JEX enables the use of several data object types The specific API for these can be found in the main JEXperiment folder. These API provide methods to retrieve data from these objects, create new objects and handle the data they contain.
 * 
 * @author erwinberthier, convert to JEXPlugin by Mengcheng Qi
 * 
 */

@Plugin(
		type = JEXPlugin.class,
		name="CTC - Segmented Mask Overlay",
		menuPath="CTC Toolbox",
		visible=true,
		description="Overlay color images with the segmentation and boundary masks."
		)
public class CTC_SegmentedMaskOverlay extends JEXPlugin {
	
	// ----------------------------------------------------
	// --------- INFORMATION ABOUT THE FUNCTION -----------
	// ----------------------------------------------------
	
	@Override
	public int getMaxThreads()
	{
		return 1;
	}

	// ----------------------------------------------------
	// --------- INPUT OUTPUT DEFINITIONS -----------------
	// ----------------------------------------------------

	/////////// Define Inputs ///////////
	
	@InputMarker(name="Image", type=MarkerConstants.TYPE_IMAGE, description="Image which Segmented Mask apply to", optional=false)
	JEXData imageData;
	
	@InputMarker(name="Segemented Image", type=MarkerConstants.TYPE_IMAGE, description="Segemented Image", optional=false)
	JEXData segData;
	
	@InputMarker(name="Mask Image", type=MarkerConstants.TYPE_IMAGE, description="Mask Image", optional=false)
	JEXData innerData;
	
	/////////// Define Parameters ///////////
		
	@ParameterMarker(uiOrder=1, name="DEFAULT Color", description="What should the color be for images without a color dimension", ui=MarkerConstants.UI_DROPDOWN, choices = { "R", "G", "B" }, defaultChoice=0)
	String defaultColor;
	
	@ParameterMarker(uiOrder=2, name="DEFAULT Min", description="Value in the DEFAULT image to map to 0 intensity. (blank assumes image min)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0")
	double defaultMin;
	
	@ParameterMarker(uiOrder=3, name="DEFAULT Max", description="Value in the DEFAULT image to map to 0 intensity. (blank assumes image max)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="65535")
	double defaultMax;

	@ParameterMarker(uiOrder=4, name="Color Dim Name", description="Name of the color dim for multicolor image sets", ui=MarkerConstants.UI_TEXTFIELD, defaultText="Color")
	String dimName;
	
	@ParameterMarker(uiOrder=5, name="RED Dim Value", description="Value of dim containing the RED image.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="")
	String rDim;
	
	@ParameterMarker(uiOrder=6, name="RED Min", description="Value in the RED image to map to 0 intensity. (blank assumes image min)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0")
	double rMin;
	
	@ParameterMarker(uiOrder=7, name="RED Max", description="alue in the RED image to map to 255 intensity. (blank assumes image max)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="65535")
	double rMax;
	
	@ParameterMarker(uiOrder=8, name="GREEN Dim Value", description="Value of dim containing the GREEN image.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="")
	String gDim;
	
	@ParameterMarker(uiOrder=9, name="GREEN Min", description="Value in the GREEN image to map to 0 intensity. (blank assumes image min)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0")
	double gMin;
	
	@ParameterMarker(uiOrder=10, name="GREEN Max", description="alue in the GREEN image to map to 255 intensity. (blank assumes image max)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="65535")
	double gMax;
	
	@ParameterMarker(uiOrder=11, name="BLUE Dim Value", description="Value of dim containing the BLUE image.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="")
	String bDim;
	
	@ParameterMarker(uiOrder=12, name="BLUE Min", description="Value in the BLUE image to map to 0 intensity. (blank assumes image min)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0")
	double bMin;
	
	@ParameterMarker(uiOrder=13, name="BLUE Max", description="alue in the BLUE image to map to 255 intensity. (blank assumes image max)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="65535")
	double bMax;
	
	@ParameterMarker(uiOrder=14, name="RGB Scale", description="Linear or log scaling of R, G, and B channels", ui=MarkerConstants.UI_DROPDOWN, choices = { "Linear", "Log" }, defaultChoice=1)
	String rgbScale;
	
	/////////// Define Outputs ///////////
		
	@OutputMarker(name="Overlay", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant overlay image", enabled=true)
	JEXData output;

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
		// Check the inputs
		if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		
		if(segData == null || !segData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		
		if(innerData == null || !innerData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		
		// Run the function
		
		TreeMap<DimensionMap,String> images = ImageReader.readObjectToImagePathTable(imageData);
		TreeMap<DimensionMap,String> segs = ImageReader.readObjectToImagePathTable(segData);
		TreeMap<DimensionMap,String> masks = ImageReader.readObjectToImagePathTable(innerData);
		TreeMap<DimensionMap,String> overlayers = new TreeMap<DimensionMap,String>();
		
		if(rDim.equals("") && bDim.equals("") && gDim.equals(""))
		{
			dimName = "Color";
			if(defaultColor.equals("R"))
			{
				rDim = defaultColor;
				rMin = defaultMin;
				rMax = defaultMax;
			}
			if(defaultColor.equals("G"))
			{
				gDim = defaultColor;
				gMin = defaultMin;
				gMax = defaultMax;
			}
			if(defaultColor.equals("B"))
			{
				bDim = defaultColor;
				bMin = defaultMin;
				bMax = defaultMax;
			}
			for (Entry<DimensionMap,String> e : images.entrySet())
			{
				DimensionMap newMap = e.getKey().copy();
				newMap.put(dimName, defaultColor);
				overlayers.put(newMap.copy(), e.getValue());
			}
		}
		else
		{
			overlayers.putAll(images);
		}
		
		for (DimensionMap map : segData.getDimTable().getMapIterator())
		{
			DimensionMap newMap = map.copy();
			newMap.put(dimName, "MASK");
			
			ByteProcessor seg = (ByteProcessor) (new ImagePlus(segs.get(map))).getProcessor();
			ByteProcessor mask = (ByteProcessor) (new ImagePlus(masks.get(map))).getProcessor();
			
			seg.invert();
			mask.multiply(0.50);
			seg.copyBits(mask, 0, 0, Blitter.ADD);
			String path = JEXWriter.saveImage(seg);
			
			overlayers.put(newMap, path);
		}
		
		TreeMap<DimensionMap,String> outputMap = CTC_JEX_OverlayStack.overlayStack(overlayers, dimName, rDim, gDim, bDim, "MASK", rMin, rMax, gMin, gMax, bMin, bMax, new Double(0), new Double(255), rgbScale, CTC_JEX_OverlayStack.LINEAR, this);
		
		// Set the outputs
		output = ImageWriter.makeImageStackFromPaths("Overlay", outputMap);
		
		// Return status
		return true;
	}
	
}