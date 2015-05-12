package plugins;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataReader.RoiReader;
import Database.DataWriter.ImageWriter;

import Database.SingleUserDatabase.JEXWriter;
import function.imageUtility.jBackgroundSubtracter;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;
import ij.ImagePlus;
import ij.plugin.filter.RankFilters;
import ij.process.FloatBlitter;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import image.roi.ROIPlus;

import java.util.TreeMap;

import org.scijava.plugin.Plugin;

import jex.statics.JEXStatics;
import jex.utilities.FunctionUtility;
import jex.utilities.ImageUtility;
import logs.Logs;
import tables.DimensionMap;

/**
 * Subtract background of image and correct for uneven illumination using
 * calibration images.
 * 
 * @author erwinberthier
 * 
 */
@Plugin(
		type = JEXPlugin.class,
		name="CTC - Background Correct (Calibrated)",
		menuPath="CTC Toolbox",
		visible=true,
		description="Subtract background of image and correct for uneven illumination using calibration images"
		)
public class CTC_SingleCell_BackGroundCorrectCalibrated extends JEXPlugin {

	public CTC_SingleCell_BackGroundCorrectCalibrated() {}

	/////////// Define Inputs ///////////
	
	@InputMarker(uiOrder=0, name="DF Image", type=MarkerConstants.TYPE_IMAGE, description="", optional=false)
	JEXData darkData;
	
	@InputMarker(uiOrder=1, name="IF Image", type=MarkerConstants.TYPE_IMAGE, description="", optional=false)
	JEXData illumData;
	
	@InputMarker(uiOrder=2, name="Images", type=MarkerConstants.TYPE_IMAGE, description="", optional=false)
	JEXData data;
	
	@InputMarker(uiOrder=3, name="Optional Crop ROI", type=MarkerConstants.TYPE_ROI, description="", optional=false)
	JEXData roiData;

	/////////// Define Parameters ///////////

	@ParameterMarker(uiOrder=0, name="IF-DF Radius", description="Radius of mean for smoothing illumination correction image", ui=MarkerConstants.UI_TEXTFIELD, defaultText="5")
	double IFDFRadius;

	@ParameterMarker(uiOrder=1, name="Image-DF Radius", description="Radius of median filter for smoothing dark field corrected experimental image (if no DF provided, it WILL smooth the image but not subtract any DF image from the image)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="3")
	double imageDFRadius;

	@ParameterMarker(uiOrder=2, name="Est. BG sigma", description="Estimated noise in the background signal (i.e., mu +/- sigma)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="100")
	double sigma;

	@ParameterMarker(uiOrder=3, name="BG Sub. Presmooth Radius", description="Radius of mean filter to apply temporarily for background subtraction (only affects rolling ball subtraction, not applied directly to original image)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="5")
	double bgPresmoothRadius;

	@ParameterMarker(uiOrder=4, name="BG Sub. Radius", description="Kernal radius in pixels (e.g. 3.8)", ui=MarkerConstants.UI_TEXTFIELD, defaultText="150")
	double bgRadius;

	@ParameterMarker(uiOrder=5, name="BG Sub. Paraboloid", description="A sliding paraboloid is recommended", ui=MarkerConstants.UI_DROPDOWN, choices={ "true", "false" }, defaultChoice=0)
	boolean bgParaboloid;

	@ParameterMarker(uiOrder=6, name="Output Bit Depth", description="Depth of the outputted image", ui=MarkerConstants.UI_DROPDOWN, choices={ "8", "16", "32" }, defaultChoice=1)
	int outputDepth;
	
	@ParameterMarker(uiOrder=7, name="Nominal Value to Add Back", description="Nominal value to add back to the image so that we don't clip values below zero", ui=MarkerConstants.UI_TEXTFIELD, defaultText="100")
	double nominal;
	
	/////////// Define Outputs ///////////

	@OutputMarker(uiOrder=0, name="Adjusted Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="Background subtracted using background subtraction function", enabled=true)
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
		// collect the inputs and validate as necessary
		if(data == null || !data.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}

		FloatProcessor darkImp = null;
		if(darkData != null && darkData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			ImagePlus darkIm = ImageReader.readObjectToImagePlus(darkData);
			if(darkIm == null)
			{
				return false;
			}
			darkImp = (FloatProcessor) darkIm.getProcessor().convertToFloat();
		}

		FloatProcessor illumImp = null;
		if(illumData != null && illumData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			ImagePlus illumIm = ImageReader.readObjectToImagePlus(illumData);
			if(illumIm == null)
			{
				return false;
			}
			illumImp = (FloatProcessor) illumIm.getProcessor().convertToFloat();
		}

		TreeMap<DimensionMap,ROIPlus> roiMap = new TreeMap<DimensionMap,ROIPlus>();
		if(roiData != null && roiData.getDataObjectType().equals(JEXData.ROI))
		{
			roiMap = RoiReader.readObjectToRoiMap(roiData);
		}

		// Run the function
		TreeMap<DimensionMap,String> images = ImageReader.readObjectToImagePathTable(data);
		TreeMap<DimensionMap,String> outputMap = new TreeMap<DimensionMap,String>();

		// //// Create a reference to a Blitter for float operations
		FloatBlitter blit = null;

		if(illumImp != null)
		{
			// //// Prepare the illumination Field Image

			// //// Subtract dark field from illumination field
			blit = new FloatBlitter(illumImp);
			if(darkImp != null)
			{
				blit.copyBits(darkImp, 0, 0, FloatBlitter.SUBTRACT);
			}

			if(IFDFRadius > 0)
			{
				// //// Smooth the result
				ImagePlus IC = new ImagePlus("IC", illumImp);
				RankFilters rF = new RankFilters();
				rF.rank(illumImp, IFDFRadius, RankFilters.MEAN);
				IC.flush();
				IC = null;
			}

			// Calculate the mean of the illumination field correction for back
			// multiplication
			FloatStatistics illumStats = new FloatStatistics(illumImp, FloatStatistics.MEAN, null);
			double illumMean = illumStats.mean;
			illumStats = null;
			illumImp.multiply(1 / illumMean); // Normalized IllumImp so we don't
			// have to multiply back up all
			// other images
		}

		int count = 0;
		int total = images.size();
		JEXStatics.statusBar.setProgressPercentage(0);
		for (DimensionMap dim : images.keySet())
		{
			if(this.isCanceled())
			{
				return false;
			}
			String path = images.get(dim);

			Logs.log("Calibrating image for " + dim.toString(), this);

			// /// Get the image
			ImagePlus im = new ImagePlus(path);
			FloatProcessor imp = (FloatProcessor) im.getProcessor().convertToFloat(); // should
			// be
			// a
			// float
			// processor

			// //// First calculate (Image-DF)
			blit = new FloatBlitter(imp);
			if(darkImp != null)
			{
				blit.copyBits(darkImp, 0, 0, FloatBlitter.SUBTRACT);
			}

			if(imageDFRadius > 0)
			{
				// //// Smooth (Image-DF)
				RankFilters rF = new RankFilters();
				rF.rank(imp, imageDFRadius, RankFilters.MEDIAN);
			}

			// //// Subtract the background from the filtered (Image-DF)
			FloatProcessor impTemp = new FloatProcessor(imp.getFloatArray());
			RankFilters rF = new RankFilters();
			rF.rank(impTemp, bgPresmoothRadius, RankFilters.MEAN);
			ImagePlus imTemp = new ImagePlus("temp", impTemp);
			jBackgroundSubtracter bS = new jBackgroundSubtracter();
			bS.setup("", imTemp);
			jBackgroundSubtracter.radius = bgRadius; // default rolling ball radius
			// boolean bgInverse = false; // Boolean.parseBoolean(parameters.getValueOfParameter("Inverted"));
			jBackgroundSubtracter.lightBackground = false;
			jBackgroundSubtracter.createBackground = true;
			// boolean bgPresmooth = false; // Boolean.parseBoolean(parameters.getValueOfParameter("Presmoothing"));
			jBackgroundSubtracter.useParaboloid = bgParaboloid; // use "Sliding Paraboloid" instead of rolling ball algorithm
			jBackgroundSubtracter.doPresmooth = false;
			bS.run(impTemp);

			// subtract the calculated background from the image
			blit.copyBits(impTemp, 0, 0, FloatBlitter.SUBTRACT);

			// //// Subtract off remaining background because subtraction method
			// //subtracts off the MINIMUM of the background, we want the mean of
			// //the background to be zero
			// FloatProcessor bg = new FloatProcessor(imp.getWidth(),
			// imp.getHeight(), (float[])imp.getPixelsCopy(), null);
			// double remainderMean = 0;
			//
			// ////// Apply mean filter on BG subtracted (Image-DF)
			// RankFilters rF = new RankFilters();
			// rF.rank(bg, 5, RankFilters.MEAN);
			// remainderMean = ImageStatistics.getStatistics(bg, ImageStatistics.MIN_MAX, null).min; // Get the "min" of the "mean"

			double remainderMean = ImageUtility.getHistogramPeakBin(imp, -sigma, sigma, -1, false);

			// try
			// {
			// FileUtility.openFileDefaultApplication(JEXWriter.saveImage(imp));
			// }
			// catch (Exception e)
			// {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }

			// Subtract off the remainder of the background before division with
			// the illumination field
			imp.add(-1 * remainderMean);

			if(illumImp != null)
			{
				// //// Then divide by IllumImp
				blit.copyBits(illumImp, 0, 0, FloatBlitter.DIVIDE);
			}

			// //// Add back a nominal amount to avoid clipping negative values
			// upon conversion to 16-bit
			imp.add(nominal);

			// //// crop if desired
			ROIPlus cropRoi = roiMap.get(dim);
			if(cropRoi != null)
			{
				imp.setRoi(cropRoi.getRoi());
				imp = (FloatProcessor) imp.crop();
			}

			// //// reset the display min and max
			imp.resetMinAndMax();

			ImagePlus toSave = FunctionUtility.makeImageToSave(imp, "false", outputDepth);
			String finalPath1 = JEXWriter.saveImage(toSave);

			outputMap.put(dim.copy(), finalPath1);
			Logs.log("Finished processing " + count + " of " + total + ".", 1, this);
			count++;

			// Status bar
			int percentage = (int) (100 * ((double) count / (double) images.size()));
			JEXStatics.statusBar.setProgressPercentage(percentage);
		}

		// Set the outputs
		this.output = ImageWriter.makeImageStackFromPaths("temp", outputMap);

		// Return status
		return true;
	}
}
