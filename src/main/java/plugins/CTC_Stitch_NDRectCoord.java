package plugins;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ValueReader;
import Database.DataWriter.ImageWriter;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;
import image.roi.PointList;

import java.awt.Rectangle;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.scijava.plugin.Plugin;

import miscellaneous.CSVList;
import tables.Dim;
import tables.DimTable;
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
		name="CTC - Stitch 2 Dims Using Alignments",
		menuPath="CTC Toolbox",
		visible=true,
		description="Function that allows you to stitch an image ARRAY into a single image using two image alignment objects."
		)
public class CTC_Stitch_NDRectCoord extends JEXPlugin {
	
	public static final int LtoR = 0, TtoB = 1;
	
	public CTC_Stitch_NDRectCoord()
	{}
	
	// ----------------------------------------------------
	// --------- INFORMATION ABOUT THE FUNCTION -----------
	// ----------------------------------------------------
	
	@Override
	public int getMaxThreads()
	{
		return 10;
	}
	
	// ----------------------------------------------------
	// --------- INPUT OUTPUT DEFINITIONS -----------------
	// ----------------------------------------------------
	
	/////////// Define Inputs ///////////
	
	@InputMarker(uiOrder=0, name="Vertical Image Alignment", type=MarkerConstants.TYPE_VALUE, description="Vertical Image Aligment in pixels", optional=false)
	JEXData verAlign;
		
	@InputMarker(uiOrder=1, name="Image Set", type=MarkerConstants.TYPE_IMAGE, description="Image to be stitched up.", optional=false)
	JEXData imageData;
	
	@InputMarker(uiOrder=2, name="Horizontal Image Alignment", type=MarkerConstants.TYPE_VALUE, description="Horizontal Image Aligment in pixels", optional=false)
	JEXData horAlign;
		
	/////////// Define Parameters ///////////
	
	@ParameterMarker(uiOrder=0, name="Image Row Dim Name", description="Number of rows in the stitched image", ui=MarkerConstants.UI_TEXTFIELD, defaultText="ImRow")
	String rowDimName;
	
	@ParameterMarker(uiOrder=1, name="Image Col Dim Name", description="Number of columns in the stitched image", ui=MarkerConstants.UI_TEXTFIELD, defaultText="ImCol")
	String colDimName;
	
	@ParameterMarker(uiOrder=2, name="Scale", description="The stitched image size will be scaled by this factor.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="1.0")
	double scale;
	
	@ParameterMarker(uiOrder=3, name="Output Bit Depth", description="Bit depth to save the image as.", ui=MarkerConstants.UI_DROPDOWN, choices={ "8", "16" }, defaultChoice=1)
	int bitDepth;
	
	@ParameterMarker(uiOrder=4, name="Normalize Intensities Fit Bit Depth", description="Scale intensities to go from 0 to max value determined by new bit depth " +
			"(\'true\' overrides intensity multiplier).", ui=MarkerConstants.UI_CHECKBOX,  defaultBoolean=true)
	boolean normalize;
	
	@ParameterMarker(uiOrder=5, name="Intensity Multiplier", description="Number to multiply all intensities by before converting to new bitDepth.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="1")
	double multiplier;
		
	/////////// Define Outputs ///////////
	
	@OutputMarker(uiOrder=1, name="Stitched Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant stitched image", enabled=true)
	JEXData output1;

	
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
		// Collect the inputs
		if(horAlign == null || !horAlign.getTypeName().getType().equals(JEXData.VALUE))
		{
			return false;
		}
		CSVList alignmentInfoHor = new CSVList(ValueReader.readValueObject(horAlign));
		int horDxImage = Integer.parseInt(alignmentInfoHor.get(0));
		int horDyImage = Integer.parseInt(alignmentInfoHor.get(1));
		
		// Collect the inputs
		if(verAlign == null || !verAlign.getTypeName().getType().equals(JEXData.VALUE))
		{
			return false;
		}
		CSVList alignmentInfoVer = new CSVList(ValueReader.readValueObject(verAlign));
		int verDxImage = Integer.parseInt(alignmentInfoVer.get(0));
		int verDyImage = Integer.parseInt(alignmentInfoVer.get(1));
		
		
		// Check the inputs
		if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		DimTable table = imageData.getDimTable();
		Dim rowDim = table.getDimWithName(rowDimName);
		Dim colDim = table.getDimWithName(colDimName);
		if(rowDim == null || colDim == null)
		{
			return false;
		}
		int rows = rowDim.size();
		int cols = colDim.size();
		
		// Run the function
		PointList imageCoords = this.getMovements(horDxImage, horDyImage, verDxImage, verDyImage, rows, cols, TtoB, scale);
		
		// Remove the row and col Dim's from the DimTable and iterate through it
		// and stitch.
		table.remove(rowDim);
		table.remove(colDim);
		Map<DimensionMap,String> stitchedImageFilePaths = new HashMap<DimensionMap,String>();
		for (DimensionMap partialMap : table.getDimensionMaps())
		{
			if(this.isCanceled())
			{
				return false;
			}
			try
			{
				List<DimensionMap> mapsToGet = getMapsForStitching(rowDim, colDim, partialMap);
				File stitchedFile = CTC_JEX_ImageTools_Stitch_Coord.stitch(optionalEntry, imageData, mapsToGet, imageCoords, scale, normalize, multiplier, bitDepth);
				stitchedImageFilePaths.put(partialMap, stitchedFile.getAbsolutePath());
			}
			catch (Exception e)
			{
				e.printStackTrace();
				return false;
			}
			
		}
		
		output1 = ImageWriter.makeImageStackFromPaths("Stitched Image", stitchedImageFilePaths);
		
		// Return status
		return true;
	}
	
	private List<DimensionMap> getMapsForStitching(Dim rowDim, Dim colDim, DimensionMap partialMap)
	{
		List<DimensionMap> ret = new Vector<DimensionMap>();
		for (int c = 0; c < colDim.size(); c++)
		{
			for (int r = 0; r < rowDim.size(); r++)
			{
				DimensionMap imageMap = partialMap.copy();
				imageMap.put(rowDim.name(), rowDim.valueAt(r));
				imageMap.put(colDim.name(), colDim.valueAt(c));
				ret.add(imageMap);
			}
		}
		return ret;
	}
	
	private PointList getMovements(int horDxImage, int horDyImage, int verDxImage, int verDyImage, int rows, int cols, int order, double scale)
	{
		PointList ret = new PointList();
		if(order == TtoB) // Go through col first (i.e. index row first)
		{
			for (int c = 0; c < cols; c++)
			{
				for (int r = 0; r < rows; r++)
				{
					ret.add(r * verDxImage + c * horDxImage, r * verDyImage + c * horDyImage);
				}
			}
		}
		else
		// Go through row first (i.e. index col first)
		{
			for (int r = 0; r < rows; r++)
			{
				for (int c = 0; c < cols; c++)
				{
					ret.add(r * verDxImage + c * horDxImage, r * verDyImage + c * horDyImage);
				}
			}
		}
		
		// Scale and put bounding rectangle at 0,0
		ret.scale(scale);
		Rectangle rect = ret.getBounds();
		ret.translate(-1 * rect.x, -1 * rect.y);
		System.out.println(ret.getBounds());
		return ret;
	}
}
