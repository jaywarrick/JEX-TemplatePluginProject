package plugins;

import java.util.TreeMap;

import jex.statics.JEXStatics;
import ij.ImagePlus;
import ij.process.Blitter;
import ij.process.FloatBlitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

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

@Plugin(
		type = JEXPlugin.class,
		name="CTC - Image Calculator",
		menuPath="CTC Toolbox",
		visible=true,
		description="Perform pixel-to-pixel math on two images.")
public class CTC_ImageCalculator extends JEXPlugin {

	public CTC_ImageCalculator() {}

	/////////// Define Inputs ///////////

	@InputMarker(uiOrder=1, name="Image A", type=MarkerConstants.TYPE_IMAGE, description="", optional=false)
	JEXData imageAData;

	@InputMarker(uiOrder=2, name="Image B", type=MarkerConstants.TYPE_IMAGE, description="", optional=false)
	JEXData imageBData;

	/////////// Define Parameters ///////////

	@ParameterMarker(uiOrder=0, name="Math Operation", description="Math operation to perform using the pixel information from Image A and B.", ui=MarkerConstants.UI_DROPDOWN, choices={ "A+B", "A-B", "A*B", "A/B", "|A-B|", "MAX", "MIN", "AVERAGE", "AND", "OR", "XOR", "COPY", "COPY Transparent 0" }, defaultChoice=0)
	String method;

	@ParameterMarker(uiOrder=1, name="Output Bit-Depth", description="Bit-Depth of the output image", ui=MarkerConstants.UI_DROPDOWN, choices={"8","16","32"}, defaultChoice=2)
	int bitDepth;

	/////////// Define Outputs ///////////

	@OutputMarker(uiOrder=1, name="Calculated Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="", enabled=true)
	JEXData output;


	public boolean allowMultithreading()
	{
		return true;
	}

	@Override
	public boolean run(JEXEntry arg0) {
		// Collect the inputs
		if(imageAData == null || !imageAData.getTypeName().getType().equals(JEXData.IMAGE))
			return false;
		if(imageBData == null || !imageBData.getTypeName().getType().equals(JEXData.IMAGE))
			return false;

		// Gather parameters
		int methodInt = Blitter.DIVIDE; // initialize
		if(method.equals("A+B"))
			methodInt = Blitter.ADD;
		else if(method.equals("AND"))
			methodInt = Blitter.AND;
		else if(method.equals("AVERAGE"))
			methodInt = Blitter.AVERAGE;
		else if(method.equals("COPY"))
			methodInt = Blitter.COPY;
		else if(method.equals("COPY_ZERO_TRANSPARENT"))
			methodInt = Blitter.COPY_ZERO_TRANSPARENT;
		else if(method.equals("|A-B|"))
			methodInt = Blitter.DIFFERENCE;
		else if(method.equals("A/B"))
			methodInt = Blitter.DIVIDE;
		else if(method.equals("MAX"))
			methodInt = Blitter.MAX;
		else if(method.equals("MIN"))
			methodInt = Blitter.MIN;
		else if(method.equals("A*B"))
			methodInt = Blitter.MULTIPLY;
		else if(method.equals("OR"))
			methodInt = Blitter.OR;
		else if(method.equals("A-B"))
			methodInt = Blitter.SUBTRACT;
		else if(method.equals("XOR"))
			methodInt = Blitter.XOR;

		// Run the function
		TreeMap<DimensionMap,String> imageAMap = ImageReader.readObjectToImagePathTable(imageAData);
		TreeMap<DimensionMap,String> imageBMap = ImageReader.readObjectToImagePathTable(imageBData);
		TreeMap<DimensionMap,String> outputImageMap = new TreeMap<DimensionMap,String>();
		int count = 0, percentage = 0;
		if(imageAMap.size() == 1)
		{
			if(this.isCanceled())
			{
				return false;
			}
			ImagePlus savedImA = new ImagePlus(imageAMap.firstEntry().getValue());
			FloatProcessor savedImpA = (FloatProcessor) savedImA.getProcessor().convertToFloat();
			FloatProcessor impA;
			for (DimensionMap map : imageBMap.keySet())
			{
				impA = new FloatProcessor(savedImpA.getWidth(), savedImpA.getHeight(), (float[]) savedImpA.getPixelsCopy(), null);
				String pathB = imageBMap.get(map);
				if(pathB == null)
					continue;
				ImagePlus imB = new ImagePlus(pathB);

				FloatProcessor ipB = (FloatProcessor) imB.getProcessor().convertToFloat();

				FloatBlitter blit = new FloatBlitter(impA);
				blit.copyBits(ipB, 0, 0, methodInt);
				ImageProcessor toSave = impA;
				if(bitDepth == 8)
				{
					toSave = impA.convertToByte(false);
				}
				else if(bitDepth == 16)
				{
					toSave = impA.convertToShort(false);
				}

				String path = JEXWriter.saveImage(toSave);

				if(path != null)
				{
					outputImageMap.put(map, path);
				}
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) imageAMap.size())));
				JEXStatics.statusBar.setProgressPercentage(percentage);
			}
		}
		else if(imageBMap.size() == 1)
		{
			ImagePlus savedImB = new ImagePlus(imageBMap.firstEntry().getValue());
			FloatProcessor savedImpB = (FloatProcessor) savedImB.getProcessor().convertToFloat();
			FloatProcessor impB;
			for (DimensionMap map : imageAMap.keySet())
			{
				if(this.isCanceled())
				{
					return false;
				}
				impB = new FloatProcessor(savedImpB.getWidth(), savedImpB.getHeight(), (float[]) savedImpB.getPixelsCopy(), null);
				String pathA = imageAMap.get(map);
				if(pathA == null)
					continue;
				ImagePlus imA = new ImagePlus(pathA);

				FloatProcessor impA = (FloatProcessor) imA.getProcessor().convertToFloat();

				FloatBlitter blit = new FloatBlitter(impA);
				blit.copyBits(impB, 0, 0, methodInt);
				ImageProcessor toSave = impA;
				if(bitDepth == 8)
				{
					toSave = impA.convertToByte(false);
				}
				else if(bitDepth == 16)
				{
					toSave = impA.convertToShort(false);
				}

				String path = JEXWriter.saveImage(toSave);

				if(path != null)
				{
					outputImageMap.put(map, path);
				}
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) imageAMap.size())));
				JEXStatics.statusBar.setProgressPercentage(percentage);
			}
		}
		else
		{
			for (DimensionMap map : imageAMap.keySet())
			{
				if(this.isCanceled())
				{
					return false;
				}
				ImagePlus imA = new ImagePlus(imageAMap.get(map));
				String pathB = imageBMap.get(map);
				if(pathB == null)
					continue;
				ImagePlus imB = new ImagePlus(pathB);
				FloatProcessor ipA = (FloatProcessor) imA.getProcessor().convertToFloat();
				FloatProcessor ipB = (FloatProcessor) imB.getProcessor().convertToFloat();

				FloatBlitter blit = new FloatBlitter(ipA);
				blit.copyBits(ipB, 0, 0, methodInt);
				ImageProcessor toSave = ipA;
				if(bitDepth == 8)
				{
					toSave = ipA.convertToByte(false);
				}
				else if(bitDepth == 16)
				{
					toSave = ipA.convertToShort(false);
				}

				String path = JEXWriter.saveImage(toSave);

				if(path != null)
				{
					outputImageMap.put(map, path);
				}
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) imageAMap.size())));
				JEXStatics.statusBar.setProgressPercentage(percentage);
			}
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
