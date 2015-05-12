package plugins;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataReader.RoiReader;
import Database.DataWriter.ImageWriter;
import Database.DataWriter.RoiWriter;
import Database.SingleUserDatabase.JEXWriter;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import image.roi.IdPoint;
import image.roi.PointList;
import image.roi.ROIPlus;

import java.awt.Point;
import java.util.TreeMap;

import org.scijava.plugin.Plugin;

import jex.statics.JEXStatics;
import jex.utilities.FunctionUtility;
import logs.Logs;
import tables.DimensionMap;

/**
 * This is a JEXperiment function template To use it follow the following instructions
 * 
 * 1. Fill in all the required methods according to their specific instructions 2. Place the file in the Functions/SingleDataPointFunctions folder 3. Compile and run JEX!
 * 
 * JEX enables the use of several data object types The specific API for these can be found in the main JEXperiment folder. These API provide methods to retrieve data from these objects, create new objects and handle the data they contain.
 * 
 * @author erwinberthier, convert to JEXPlugin by Mengcheng
 * 
 * This method cropped rectangular sub-images around specified maxima locations from the input image
 * 
 */

@Plugin(
		type = JEXPlugin.class,
		name="CTC - Crop Points",
		menuPath="CTC Toolbox",
		visible=true,
		description="Function that allows you to crop a regions specified by a point ROI (e.g., cells identified by find maxima)."
		)
public class CTC_CropPoints extends JEXPlugin {
	
	public CTC_CropPoints()
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
		
	@InputMarker(uiOrder=1, name="Image", type=MarkerConstants.TYPE_IMAGE, description="Image to be processed.", optional=false)
	JEXData imageFiles;
	
	@InputMarker(uiOrder=2, name="Maxima", type=MarkerConstants.TYPE_ROI, description="Maxima Roi to be processed.", optional=false)
	JEXData pointData;
	
	/////////// Define Parameters ///////////
		
	@ParameterMarker(uiOrder=1, name="Width", description="Width of the cropped region surrounding point.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="50")
	int width;
	
	@ParameterMarker(uiOrder=2, name="Height", description="Height of the cropped region surrounding point.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="50")
	int height;

	/////////// Define Outputs ///////////

	@OutputMarker(uiOrder=1, name="Cropped Images", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant cropped images", enabled=true)
	JEXData output1;
	
	@OutputMarker(uiOrder=2, name="Single Points", type=MarkerConstants.TYPE_ROI, flavor="", description="The resultant single points", enabled=true)
	JEXData output2;
	
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
		if(imageFiles == null || !imageFiles.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		
		if(pointData == null || !pointData.getTypeName().getType().equals(JEXData.ROI))
		{
			return false;
		}
		
		// Run the function
		TreeMap<DimensionMap,String> imageList = ImageReader.readObjectToImagePathTable(imageFiles);
		TreeMap<DimensionMap,ROIPlus> pointROIMap = RoiReader.readObjectToRoiMap(pointData);
		TreeMap<DimensionMap,String> outputImageMap = new TreeMap<DimensionMap,String>();
		TreeMap<DimensionMap,ROIPlus> outputRoiMap = new TreeMap<DimensionMap,ROIPlus>();
		
		ROIPlus pointROI;
		ImagePlus im;
		FloatProcessor imp;
		int count = 0;
		String actualPath = "";
		int total = imageList.size();
		
		ROIPlus prototype;
		PointList pl = new PointList();
		pl.add(new Point(-width / 2, -height / 2));
		pl.add(new Point(-width / 2 + width, -height / 2 + height));
		prototype = new ROIPlus(pl.copy(), ROIPlus.ROI_RECT);
		FloatProcessor cell;
		for (DimensionMap map : imageList.keySet())
		{
			if(this.isCanceled())
			{
				return false;
			}
			im = new ImagePlus(imageList.get(map));
			imp = (FloatProcessor) im.getProcessor().convertToFloat();
			int bitDepth = im.getBitDepth();
			pointROI = pointROIMap.get(map);
			if(pointROI != null)
			{
				boolean isLine = pointROI.isLine();
				if(isLine || pointROI.type != ROIPlus.ROI_POINT)
				{
					return false;
				}
				pl = pointROI.getPointList();
				for (IdPoint p : pl)
				{
					if(this.isCanceled())
					{
						return false;
					}
					ROIPlus copy = prototype.copy();
					copy.pointList.translate(p.x, p.y);
					imp.setRoi(copy.getRoi());
					cell = (FloatProcessor) imp.crop().convertToFloat();
					actualPath = this.saveImage(cell, bitDepth);
					DimensionMap newMap = map.copy();
					newMap.put("Id", "" + p.id);
					outputImageMap.put(newMap, actualPath);
					PointList toSave = new PointList();
					toSave.add(p.copy());
					toSave.translate(-p.x, -p.y);
					toSave.translate(width / 2, height / 2);
					outputRoiMap.put(newMap, new ROIPlus(toSave, ROIPlus.ROI_POINT));
					Logs.log("Outputing cell: " + p.id, this);
				}
			}
			count = count + 1;
			JEXStatics.statusBar.setProgressPercentage(count * 100 / total);
		}
		
		output1 = ImageWriter.makeImageStackFromPaths("Cropped Images", outputImageMap);
		output2 = RoiWriter.makeRoiObject("Single Points", outputRoiMap);
		
		// Return status
		return true;
	}
	
	private String saveImage(FloatProcessor imp, int bitDepth)
	{
		ImagePlus toSave = FunctionUtility.makeImageToSave(imp, "false", bitDepth);
		String imPath = JEXWriter.saveImage(toSave);
		return imPath;
	}
}
