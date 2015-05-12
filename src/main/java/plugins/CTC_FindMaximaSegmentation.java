package plugins;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataReader.RoiReader;
import Database.DataWriter.FileWriter;
import Database.DataWriter.ImageWriter;
import Database.DataWriter.RoiWriter;
import Database.SingleUserDatabase.JEXWriter;
import function.imageUtility.MaximumFinder;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.RankFilters;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import image.roi.IdPoint;
import image.roi.PointList;
import image.roi.ROIPlus;

import java.awt.Shape;
import java.io.File;
import java.util.TreeMap;

import org.scijava.plugin.Plugin;

import jex.statics.JEXStatics;
import logs.Logs;
import miscellaneous.JEXCSVWriter;
import tables.DimTable;
import tables.DimensionMap;
import weka.core.converters.JEXTableWriter;

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
		name="CTC - Find Maxima Segmentation",
		menuPath="CTC Toolbox",
		visible=true,
		description="Find maxima in a grayscale image or one color of a multi-color image."
		)
public class CTC_FindMaximaSegmentation extends JEXPlugin {
	
	public CTC_FindMaximaSegmentation()
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
	JEXData imageData;
	
	@InputMarker(uiOrder=2, name="ROI (optional)", type=MarkerConstants.TYPE_ROI, description="Roi to be processed.", optional=true)
	JEXData roiData;
	
	/////////// Define Parameters ///////////
	
	@ParameterMarker(uiOrder=1, name="Pre-Despeckle Radius", description="Radius of median filter applied before max finding", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0")
	double despeckleR;
	
	@ParameterMarker(uiOrder=2, name="Pre-Smoothing Radius", description="Radius of mean filter applied before max finding", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0")
	double smoothR;
	
	@ParameterMarker(uiOrder=3, name="Color Dim Name", description="Name of the color dimension.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="Color")
	String colorDimName;
	
	@ParameterMarker(uiOrder=4, name="Maxima Color Dim Value", description="Value of the color dimension to analyze for determing maxima. (leave blank to ignore and perform on all images)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="")
	String nuclearDimValue;
	
	@ParameterMarker(uiOrder=5, name="Segmentation Color Dim Value", description="Value of the color dimension to use for segmentation using the found maxima. (leave blank to apply to the same color used to find maxima)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="")
	String segDimValue;
	
	@ParameterMarker(uiOrder=6, name="Tolerance", description="Local intensity increase threshold.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="20")
	double tolerance;
	
	@ParameterMarker(uiOrder=7, name="Threshold", description="Minimum hieght of a maximum.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0")
	double threshold;
	
	@ParameterMarker(uiOrder=8, name="Exclude Maximima on Edges?", description="Exclude particles on the edge of the image?", ui=MarkerConstants.UI_CHECKBOX, defaultBoolean=true)
	boolean excludePtsOnEdges;
	
	@ParameterMarker(uiOrder=9, name="Exclude Segments on Edges?", description="Exclude segements on the edge of the image? (helpful so that half-nuclei aren't counted with the maxima found while excluding maxima on edges)", ui=MarkerConstants.UI_CHECKBOX, defaultBoolean=false)
	boolean excludeSegsOnEdges;
	
	@ParameterMarker(uiOrder=10, name="Is EDM?", description="Is the image being analyzed already a Euclidean Distance Measurement?", ui=MarkerConstants.UI_CHECKBOX, defaultBoolean=false)
	boolean isEDM;
	
	@ParameterMarker(uiOrder=11, name="Particles Are White?", description="Are the particles displayed as white on a black background?", ui=MarkerConstants.UI_CHECKBOX, defaultBoolean=true)
	boolean particlesAreWhite;
	boolean lightBackground;
	
	@ParameterMarker(uiOrder=12, name="Output Maxima Only?", description="Output the maxima only (checked TRUE) or also segmented image, point count, and XY List of points (unchecked FALSE)?", ui=MarkerConstants.UI_CHECKBOX, defaultBoolean=true)
	boolean maximaOnly;
	
	
	/////////// Define Outputs ///////////
	
	@OutputMarker(uiOrder=1, name="Maxima", type=MarkerConstants.TYPE_ROI, flavor="", description="The Roi of maxima", enabled=true)
	JEXData output0;
	
	@OutputMarker(uiOrder=2, name="XY List", type=MarkerConstants.TYPE_FILE, flavor="", description="The coordinate list of maxima", enabled=true)
	JEXData output1;
	
	@OutputMarker(uiOrder=3, name="Counts", type=MarkerConstants.TYPE_FILE, flavor="", description="The total number of maxima", enabled=true)
	JEXData output2;
	
	@OutputMarker(uiOrder=4, name="Segmented Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant segmented image", enabled=true)
	JEXData output3;

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
		lightBackground = !particlesAreWhite;
		try
		{
			/* COLLECT DATA INPUTS */
			
			// if/else to figure out whether or not valid image data has been given;
			// ends run if not
			if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
			{
				return false;
			}
			
			// Check whether Roi available
			boolean roiProvided = false;
			if(roiData != null && roiData.getTypeName().getType().equals(JEXData.ROI))
			{
				roiProvided = true;
			}
				
			
			
			/* RUN THE FUNCTION */
			// validate roiMap (if provided)
			TreeMap<DimensionMap,ROIPlus> roiMap;
			if(roiProvided)	roiMap = RoiReader.readObjectToRoiMap(roiData);
			else roiMap = new TreeMap<DimensionMap,ROIPlus>();
			
			// Read the images in the IMAGE data object into imageMap
			TreeMap<DimensionMap,String> imageMap = ImageReader.readObjectToImagePathTable(imageData);
			
			
			DimTable filteredTable = null;// imageData.getDimTable().copy();
			// if a maxima color dimension is given
			if(!nuclearDimValue.equals(""))
			{
				filteredTable = imageData.getDimTable().getSubTable(new DimensionMap(colorDimName + "=" + nuclearDimValue));
			}
			else {
				// copy the DimTable from imageData
				filteredTable = imageData.getDimTable().copy();
			}
			
			
			// Declare outputs
			TreeMap<DimensionMap,ROIPlus> outputRoiMap = new TreeMap<DimensionMap,ROIPlus>();
			TreeMap<DimensionMap,String> outputImageMap = new TreeMap<DimensionMap,String>();
			TreeMap<DimensionMap,String> outputFileMap = new TreeMap<DimensionMap,String>();
			TreeMap<DimensionMap,Double> outputCountMap = new TreeMap<DimensionMap,Double>();
			
			
			// determine value of total	
			int total;// filteredTable.mapCount() * 4; // if maximaOnly
			if(!maximaOnly & !segDimValue.equals(nuclearDimValue))
			{
				total = filteredTable.mapCount() * 8;
			}
			else if(!maximaOnly)
			{
				total = filteredTable.mapCount() * 5;
			}
			else { // if maximaOnly
				total = filteredTable.mapCount() * 4;
			}
			
			
			Roi roi;
			ROIPlus roip;
			int count = 0, percentage = 0, counter = 0;
			MaximumFinder mf = new MaximumFinder();
			for (DimensionMap map : filteredTable.getMapIterator())
			{
				if(this.isCanceled())
				{
					return false;
				}
				// // Update the display
				count ++;
				percentage = (int) (100 * ((double) (count) / ((double) total)));
				JEXStatics.statusBar.setProgressPercentage(percentage);
				counter ++;
				
				
				ImagePlus im = new ImagePlus(imageMap.get(map));
				FloatProcessor ip = (FloatProcessor) im.getProcessor().convertToFloat();
				im.setProcessor(ip);
				
				if(despeckleR > 0)
				{
					// Smooth the image
					RankFilters rF = new RankFilters();
					rF.rank(ip, despeckleR, RankFilters.MEDIAN);
				}
				if(this.isCanceled())
				{
					return false;
				}
				// // Update the display
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) total)));
				JEXStatics.statusBar.setProgressPercentage(percentage);
				counter = counter + 1;
				
				if(smoothR > 0)
				{
					// Smooth the image
					RankFilters rF = new RankFilters();
					rF.rank(ip, smoothR, RankFilters.MEAN);
				}
				if(this.isCanceled())
				{
					return false;
				}
				// // Update the display
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) total)));
				JEXStatics.statusBar.setProgressPercentage(percentage);
				counter = counter + 1;
				
				roi = null;
				roip = null;
				roip = roiMap.get(map);
				if(roip != null)
				{
					boolean isLine = roip.isLine();
					if(isLine)
					{
						return false;
					}
					roi = roip.getRoi();
					im.setRoi(roi);
				}
				
				// // Find the Maxima
				ROIPlus points = (ROIPlus) mf.findMaxima(im.getProcessor(), tolerance, threshold, MaximumFinder.ROI, excludePtsOnEdges, isEDM, roi, lightBackground);
				// // Retain maxima within the optional roi
				PointList filteredPoints = new PointList();
				if(roiProvided && roip.getPointList().size() != 0)
				{
					Shape shape = roip.getShape();
					for (IdPoint p : points.getPointList())
					{
						if(shape.contains(p))
						{
							filteredPoints.add(p.x, p.y);
						}
					}
				}
				else
				{
					filteredPoints = points.getPointList();
				}
				
				// // Create the new ROIPlus
				ROIPlus newRoip = new ROIPlus(filteredPoints, ROIPlus.ROI_POINT);
				DimensionMap tempMap = map.copy();
				if(!nuclearDimValue.equals(""))
				{
					tempMap.remove(colorDimName);
				}
				outputRoiMap.put(tempMap, newRoip);
				
				if(!maximaOnly)
				{
					// // Create the segemented image
					DimensionMap segMap = map.copy();
					segMap.put(colorDimName, segDimValue);
					FloatProcessor toSeg = ip;
					if(!segDimValue.equals(nuclearDimValue))
					{
						if(this.isCanceled())
						{
							return false;
						}
						// // Update the display
						count = count + 1;
						percentage = (int) (100 * ((double) (count) / ((double) total)));
						JEXStatics.statusBar.setProgressPercentage(percentage);
						counter = counter + 1;
						
						toSeg = (FloatProcessor) (new ImagePlus(imageMap.get(segMap))).getProcessor().convertToFloat();
						ImagePlus imToSeg = new ImagePlus("toSeg", toSeg);
						if(despeckleR > 0)
						{
							// Smooth the image
							RankFilters rF = new RankFilters();
							rF.setup(CTC_Filters.MEDIAN, imToSeg);
							rF.rank(toSeg, despeckleR, RankFilters.MEDIAN);
							rF.run(toSeg);
						}
						if(this.isCanceled())
						{
							return false;
						}
						// // Update the display
						count = count + 1;
						percentage = (int) (100 * ((double) (count) / ((double) total)));
						JEXStatics.statusBar.setProgressPercentage(percentage);
						counter = counter + 1;
						
						if(smoothR > 0)
						{
							// Smooth the image
							RankFilters rF = new RankFilters();
							rF.setup(CTC_Filters.MEAN, imToSeg);
							rF.rank(toSeg, smoothR, RankFilters.MEAN);
						}
						if(this.isCanceled())
						{
							return false;
						}
						// // Update the display
						count = count + 1;
						percentage = (int) (100 * ((double) (count) / ((double) total)));
						JEXStatics.statusBar.setProgressPercentage(percentage);
						counter = counter + 1;
					}
					
					// Create the Segmented Image
					ByteProcessor segmentedImage = null;
					if(excludeSegsOnEdges != excludePtsOnEdges)
					{
						// // Find the Maxima again with the correct exclusion criteria
						points = (ROIPlus) mf.findMaxima(im.getProcessor(), tolerance, threshold, MaximumFinder.ROI, excludeSegsOnEdges, isEDM, roi, lightBackground);
						segmentedImage = mf.segmentImageUsingMaxima(toSeg, excludeSegsOnEdges);
					}
					else
					{
						// Just use the points we already found
						segmentedImage = mf.segmentImageUsingMaxima(toSeg, excludeSegsOnEdges);
					}
					if(this.isCanceled())
					{
						return false;
					}
					// // Update the display
					count = count + 1;
					percentage = (int) (100 * ((double) (count) / ((double) total)));
					JEXStatics.statusBar.setProgressPercentage(percentage);
					counter = counter + 1;
					
					String segmentedImagePath = JEXWriter.saveImage(segmentedImage);
					if(segmentedImagePath == null)
					{
						Logs.log("Failed to create/write segmented image", Logs.ERROR, this);
					}
					else
					{
						outputImageMap.put(tempMap, segmentedImagePath);
					}
					
					// // Count the maxima
					outputCountMap.put(map, (double) filteredPoints.size());
					
					// // Create the file of XY locations
					String listPath = createXYPointListFile(filteredPoints);
					outputFileMap.put(map, listPath);
					
				}
				
				// // Update the display
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) total)));
				JEXStatics.statusBar.setProgressPercentage(percentage);
				counter = counter + 1;
				im.flush();
				im = null;
				ip = null;
			}
			if(outputRoiMap.size() == 0)
			{
				return false;
			}
			
			// roi, file file(value), image
			output0 = RoiWriter.makeRoiObject("Maxima", outputRoiMap);
			
			if(!maximaOnly)
			{
				output1 = FileWriter.makeFileObject("XY List", null, outputFileMap);
				String countsFile = JEXTableWriter.writeTable("Counts", outputCountMap, "arff");
				output2 = FileWriter.makeFileObject("Counts", null, countsFile);
				output3 = ImageWriter.makeImageStackFromPaths("Segmented Image", outputImageMap);

			}
			
			// Return status
			return true;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}
	
	// private String saveAdjustedImage(String imagePath, double oldMin, double
	// oldMax, double newMin, double newMax, double gamma, int bitDepth)
	// {
	// // Get image data
	// File f = new File(imagePath);
	// if(!f.exists()) return null;
	// ImagePlus im = new ImagePlus(imagePath);
	// FloatProcessor imp = (FloatProcessor) im.getProcessor().convertToFloat();
	// // should be a float processor
	//
	// // Adjust the image
	// FunctionUtility.imAdjust(imp, oldMin, oldMax, newMin, newMax, gamma);
	//
	// // Save the results
	// ImagePlus toSave = FunctionUtility.makeImageToSave(imp, "false",
	// bitDepth);
	// String imPath = JEXWriter.saveImage(toSave);
	// im.flush();
	//
	// // return temp filePath
	// return imPath;
	// }
	
	public static String createXYPointListFile(PointList pl)
	{
		JEXCSVWriter w = new JEXCSVWriter(JEXWriter.getDatabaseFolder() + File.separator + JEXWriter.getUniqueRelativeTempPath("csv"));
		w.write(new String[] { "ID", "X", "Y" });
		for (IdPoint p : pl)
		{
			w.write(new String[] { "" + p.id, "" + p.x, "" + p.y });
		}
		w.close();
		return w.path;
	}
}