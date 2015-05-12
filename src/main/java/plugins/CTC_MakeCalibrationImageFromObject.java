package plugins;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataWriter.ImageWriter;
import Database.SingleUserDatabase.JEXWriter;
import cruncher.Ticket;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;
import ij.ImagePlus;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.FloatBlitter;
import ij.process.FloatProcessor;

import java.io.File;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.scijava.plugin.Plugin;

import jex.statics.JEXStatics;

/**
 * Make Calibration Image (Object)
 * 
 * @author erwinberthier
 * 
 */

@Plugin(
		type = JEXPlugin.class,
		name="CTC - Make Calibration Image (Object)",
		menuPath="CTC Toolbox",
		visible=true,
		description="Treat an image object as a stack and calculate the mean "
				+ "stack intensity."
		)
public class CTC_MakeCalibrationImageFromObject extends JEXPlugin {

	public static ImagePlus calibrationImage = null;

	public CTC_MakeCalibrationImageFromObject()
	{}

	/////////// Define Inputs ///////////

	@InputMarker(uiOrder=0, name="Source Images", type=MarkerConstants.TYPE_IMAGE, description="", optional=false)
	JEXData imageData;

	/////////// Define Parameters ///////////

	@ParameterMarker(uiOrder=0, name="Stack Projection Method", description="Calculation method for projecting the stack to a single image (pseudo median = The median of subgroups will be averaged)", ui=MarkerConstants.UI_DROPDOWN, choices={ "Mean", "Pseudo Median" }, defaultChoice=1)
	String method;

	@ParameterMarker(uiOrder=1, name="Pseudo Median Subgroup Size", description="The number of images in each subgroup. Used for pseudo median option. Each median operation only produces integer value increments. Mean produces decimal increments", ui=MarkerConstants.UI_TEXTFIELD, defaultText="10")
	int groupSize;

	@ParameterMarker(uiOrder=2, name="Final Smoothing Method", description="Smoothing function to apply at the end", ui=MarkerConstants.UI_DROPDOWN, choices={ "none", CTC_JEX_StackProjection.METHOD_MEAN, CTC_JEX_StackProjection.METHOD_MEDIAN}, defaultChoice=2)
	String smooth;

	@ParameterMarker(uiOrder=3, name="Smoothing Filter Radius", description="Radius of the smoothing filter", ui=MarkerConstants.UI_TEXTFIELD, defaultText="2")
	double radius;

	/////////// Define Outputs ///////////

	@OutputMarker(uiOrder=0, name="Calibration Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="", enabled=true)
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
		// validate inputs
		if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			// Return status
			return true;
		}

		// validate parameters
		if(calibrationImage == null)
		{
			String[] filePaths = ImageReader.readObjectToImagePathStack(imageData);

			FloatProcessor imp = null;
			if(method.equals("Mean"))
			{
				imp = getMeanProjection(filePaths);
			}
			else
			{
				imp = getPseudoMedianProjection(filePaths, groupSize);
			}

			if(!smooth.equals("none"))
			{
				ImagePlus temp = new ImagePlus("temp", imp);
				RankFilters rF = new RankFilters();
				rF.setup(method, temp);
				rF.makeKernel(radius);
				rF.run(imp);
				temp.flush();
				temp = null;
			}

			// //// End Actual Function
			calibrationImage = new ImagePlus("temp", imp);
		}

		this.output = ImageWriter.makeImageObject(output.name, "Placeholder");

		// Return status
		return true;
	}

	public FloatProcessor getPseudoMedianProjection(String[] fileList, int groupSize)
	{
		int i = 0, k = 0;
		FloatProcessor ret = null, imp = null;
		FloatBlitter blit = null;
		while (i < fileList.length)
		{
			File[] files = new File[groupSize];
			for (int j = 0; j < groupSize && i < fileList.length; j++)
			{
				files[j] = new File(fileList[i]);
				i++;
			}
			// Get the median of the group
			ImagePlus stack = ImageReader.readFileListToVirtualStack(files);
			stack.setProcessor((FloatProcessor) stack.getProcessor().convertToFloat());
			imp = CTC_JEX_StackProjection.evaluate(stack, CTC_JEX_StackProjection.METHOD_MEDIAN, groupSize);

			// Add it to the total for taking the mean of the groups
			if(k == 0)
			{
				ret = imp;
				blit = new FloatBlitter(ret);
			}
			else
			{
				blit.copyBits(imp, 0, 0, Blitter.ADD);
			}
			JEXStatics.statusBar.setProgressPercentage((int) (100 * (double) i / fileList.length));
			k++;
		}
		// Divide the total by the number of groups to get the final mean of the
		// groups
		ret.multiply((double) 1 / k);
		return ret;
	}

	public FloatProcessor getMeanProjection(String[] fileList)
	{
		int i = 0;
		FloatProcessor imp1 = null, imp2 = null;
		FloatBlitter blit = null;
		for (String f : fileList)
		{
			if(i == 0)
			{
				imp1 = (FloatProcessor) (new ImagePlus(f)).getProcessor().convertToFloat();
				blit = new FloatBlitter(imp1);
			}
			else
			{
				imp2 = (FloatProcessor) (new ImagePlus(f)).getProcessor().convertToFloat();
				blit.copyBits(imp2, 0, 0, Blitter.ADD);
			}
			JEXStatics.statusBar.setProgressPercentage((int) (100 * (double) i / fileList.length));
			i++;
		}
		imp1.multiply((double) 1 / fileList.length);
		return imp1;
	}

	public void finalizeTicket(Ticket ticket)
	{
		if(calibrationImage != null)
		{
			// Copy the calibration image to all the locations
			TreeMap<JEXEntry,Set<JEXData>> outputs = ticket.getOutputList();
			for (Entry<JEXEntry,Set<JEXData>> e : outputs.entrySet())
			{
				String finalPath = JEXWriter.saveImage(calibrationImage);
				JEXData temp = ImageWriter.makeImageObject(output.name, finalPath);
				Set<JEXData> data = e.getValue();
				data.add(temp);
			}
			calibrationImage.flush();
			calibrationImage = null;
		}
	}


}
