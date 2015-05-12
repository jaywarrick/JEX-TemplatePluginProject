package plugins;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.Blitter;
import ij.process.ByteBlitter;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageStatistics;
import image.roi.IdPoint;
import image.roi.ROIPlus;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;

import jex.statics.JEXStatics;
import miscellaneous.Canceler;
import miscellaneous.StatisticsUtility;

import org.scijava.plugin.Plugin;

import tables.DimTable;
import tables.DimensionMap;
import weka.core.converters.JEXTableWriter;
import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataReader.RoiReader;
import Database.DataWriter.FileWriter;
import Database.Definition.Type;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;


@Plugin(
		type = JEXPlugin.class,
		name="CTC - Single Cell Quantification",
		menuPath="CTC Toolbox",
		visible=true,
		description="Quantify single-cell attributes from a point roi, mask overlay, segmentation overlay, and image."
		)
public class CTC_SingleCellQuantification extends JEXPlugin {
	
	public static String[] binaryMeasures = new String[] { "AREA", "PERIMETER", "CIRCULARITY" };
	public static String[] grayscaleMeasures = new String[] { "MEAN", "MEDIAN", "MIN", "MAX", "SUM", "STDDEV", "VARIANCE" };
	
	
	public CTC_SingleCellQuantification()
	{}
	
	/////////// Define Inputs ///////////
	
	@InputMarker(uiOrder=1, name="Maxima", type=MarkerConstants.TYPE_ROI, description="Maxima that define cell locations/id's", optional=false)
	JEXData maximaData;
	
	@InputMarker(uiOrder=2, name="Segmented Image", type=MarkerConstants.TYPE_IMAGE, description="Segmentation image", optional=false)
	JEXData segData;
	
	@InputMarker(uiOrder=3, name="Mask Image", type=MarkerConstants.TYPE_IMAGE, description="Black and white (i.e., binary) image that defines cell regions to be used in conjunction with the maxima defining cell locations.", optional=false)
	JEXData maskData;
	
	@InputMarker(uiOrder=4, name="Image To Quantify", type=MarkerConstants.TYPE_IMAGE, description="Image to be quantified.", optional=false)
	JEXData imageData;
	
	/////////// Define Parameters ///////////
	
	// No parameters necessary
	
	/////////// Define Outputs ///////////
	
	@OutputMarker(uiOrder=1, name="Cell Measurements", type=MarkerConstants.TYPE_FILE, flavor="", description="The single-cell measurements", enabled=true)
	JEXData output;

	public int getMaxThreads()
	{
		return 10;
	}
	
	@Override
	public boolean run(JEXEntry entry)
	{
		try
		{
			// Collect the inputs
			if(maximaData == null && maximaData.getTypeName().getType().matches(JEXData.ROI))
			{
				return false;
			}
			if(segData == null && segData.getTypeName().getType().matches(JEXData.IMAGE))
			{
				return false;
			}
			if(maskData == null && maskData.getTypeName().getType().matches(JEXData.IMAGE))
			{
				return false;
			}
			if(imageData == null && imageData.getTypeName().getType().matches(JEXData.IMAGE))
			{
				return false;
			}
			
			DimTable imageTable = imageData.getDimTable();
			
			TreeMap<DimensionMap,ROIPlus> maximaMap = RoiReader.readObjectToRoiMap(maximaData);
			TreeMap<DimensionMap,String> segMap = ImageReader.readObjectToImagePathTable(segData);
			TreeMap<DimensionMap,String> maskMap = ImageReader.readObjectToImagePathTable(maskData);
			TreeMap<DimensionMap,String> imageMap = ImageReader.readObjectToImagePathTable(imageData);
			TreeMap<DimensionMap,Double> results = new TreeMap<DimensionMap,Double>();
			
			double count = 0;
			double total = imageTable.mapCount();
			
			for (DimensionMap map : imageTable.getMapIterator())
			{
				if(this.isCanceled())
				{
					return false;
				}
				this.runStuff(map, maximaMap, segMap, maskMap, imageMap, results, this);
				count = count + 1;
				JEXStatics.statusBar.setProgressPercentage((int) (100 * count / total));
			}
			
			String resultsFile = JEXTableWriter.writeTable("SingleCellData", results);
			output = FileWriter.makeFileObject("temp", null, resultsFile);
			
			// Return status
			return true;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
		
	}
	
	public void runStuff(DimensionMap map, TreeMap<DimensionMap,ROIPlus> maximaMap, TreeMap<DimensionMap,String> segMap, TreeMap<DimensionMap,String> maskMap, TreeMap<DimensionMap,String> imageMap, TreeMap<DimensionMap,Double> results, Canceler canceler)
	{
		// Get the Maxima
		ROIPlus maxima = maximaMap.get(map);
		
		// Make the mask image impMask
		// ByteProcessor impMask = (ByteProcessor) (new ImagePlus(maskMap.get(map)).getProcessor().convertToByte(false));
		// ByteProcessor impSeg = (ByteProcessor) (new ImagePlus(segMap.get(map)).getProcessor().convertToByte(false));
		ByteProcessor impSeg = (ByteProcessor) (new ImagePlus(segMap.get(map))).getProcessor();
		ByteProcessor impMask = (ByteProcessor) (new ImagePlus(maskMap.get(map))).getProcessor();
		ByteBlitter blit = new ByteBlitter(impSeg);
		blit.copyBits(impMask, 0, 0, Blitter.AND);
		FloatProcessor impImage = (FloatProcessor) (new ImagePlus(imageMap.get(map))).getProcessor().convertToFloat();
		Wand wand = new Wand(impSeg);
		Wand wand2 = new Wand(impMask);
		Vector<Double> measurements;
		for (IdPoint p : maxima.getPointList())
		{
			if(canceler.isCanceled())
			{
				return;
			}
			if(impSeg.getPixel(p.x, p.y) == 255) // if we land on a cell that made it through thresholding
			{
				wand.autoOutline(p.x, p.y); // outline it
				wand2.autoOutline(p.x, p.y);
				boolean partOfCellClump = !this.selectionsAreEqual(wand, wand2); // If the segmented and unsegmented masks do not agree on the roi, then this cell is part of a clump.
				if(wand.npoints > 0)
				{
					Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.POLYGON); // The roi helps for using getLength() (DON'T USE Roi.TRACED_ROI., IT SCREWS UP THE Polygon OBJECTS!!!! Bug emailed to ImageJ folks)
					Polygon poly = new Polygon(wand.xpoints, wand.ypoints, wand.npoints); // The polygon helps for using contains()
					Rectangle r = roi.getBounds();
					measurements = new Vector<Double>();
					for (int i = r.x; i < r.x + r.width; i++)
					{
						for (int j = r.y; j < r.y + r.height; j++)
						{
							// innerBoundary
							if(poly.contains(i, j) && impSeg.getPixelValue(i, j) == 255)
							{
								measurements.add((double) impImage.getPixelValue(i, j));
								// Logs.log("In - " + innerT, this);
							}
						}
					}
					
					impMask.setRoi(roi);
					ImageStatistics stats = ImageStatistics.getStatistics(impMask, ImageStatistics.AREA & ImageStatistics.PERIMETER & ImageStatistics.CIRCULARITY & ImageStatistics.ELLIPSE, null);
					if(measurements.size() > 0)
					{
						DimensionMap resultsMap = map.copy();
						resultsMap.put("Id", "" + p.id);
						
						resultsMap.put("Measurement", "X");
						results.put(resultsMap.copy(), (double) p.x);
						resultsMap.put("Measurement", "Y");
						results.put(resultsMap.copy(), (double) p.y);
						resultsMap.put("Measurement", "AREA");
						results.put(resultsMap.copy(), stats.area);
						resultsMap.put("Measurement", "PERIMETER");
						results.put(resultsMap.copy(), roi.getLength());
						resultsMap.put("Measurement", "CIRCULARITY");
						results.put(resultsMap.copy(), 4.0 * Math.PI * (stats.area / (Math.pow(roi.getLength(), 2))));
						resultsMap.put("Measurement", "ELLIPSE MAJOR");
						results.put(resultsMap.copy(), stats.major);
						resultsMap.put("Measurement", "ELLIPSE MINOR");
						results.put(resultsMap.copy(), stats.minor);
						resultsMap.put("Measurement", "MEAN");
						results.put(resultsMap.copy(), StatisticsUtility.mean(measurements));
						resultsMap.put("Measurement", "MEDIAN");
						results.put(resultsMap.copy(), StatisticsUtility.median(measurements));
						resultsMap.put("Measurement", "SUM");
						results.put(resultsMap.copy(), StatisticsUtility.sum(measurements));
						resultsMap.put("Measurement", "MIN");
						results.put(resultsMap.copy(), StatisticsUtility.min(measurements));
						resultsMap.put("Measurement", "MAX");
						results.put(resultsMap.copy(), StatisticsUtility.max(measurements));
						resultsMap.put("Measurement", "STDDEV");
						results.put(resultsMap.copy(), StatisticsUtility.stdDev(measurements));
						resultsMap.put("Measurement", "VARIANCE");
						results.put(resultsMap.copy(), StatisticsUtility.variance(measurements));
						resultsMap.put("Measurement", "CLUMP");
						results.put(resultsMap.copy(), (double) (partOfCellClump ? 1 : 0));
					}
				}
			}
		}
	}
	
	public boolean selectionsAreEqual(Wand w1, Wand w2)
	{
		if(w1.npoints == w2.npoints)
		{
			for (int i = 0; i < w1.npoints; i++)
			{
				if(w1.xpoints[i] != w2.xpoints[i])
				{
					return false;
				}
			}
			for (int i = 0; i < w1.npoints; i++)
			{
				if(w1.ypoints[i] != w2.ypoints[i])
				{
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	public static JEXData getInputAs(HashMap<String,JEXData> inputs, String name, Type type)
	{
		JEXData data = inputs.get(name);
		if(data == null || !data.getTypeName().getType().equals(type))
		{
			return null;
		}
		return data;
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
}