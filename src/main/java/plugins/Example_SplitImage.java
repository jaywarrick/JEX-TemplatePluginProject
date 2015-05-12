package plugins;

import java.io.File;
import java.util.TreeMap;
import java.util.Vector;

import jex.statics.JEXStatics;

import org.scijava.plugin.Plugin;

import tables.DimTable;
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
 * @author erwinberthier, convert to JEXPlugin by Mengcheng Qi
 * 
 */

@Plugin(
		type = JEXPlugin.class,
		name="Example - Split Image",
		menuPath="Template Functions",
		visible=true,
		description="Split and image set along a dimension. (e.g., split multicolor image set into image sets with an individual color)"
		)
public class Example_SplitImage extends JEXPlugin {
	
	public Example_SplitImage()
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
		
	@InputMarker(uiOrder=1, name="Image", type=MarkerConstants.TYPE_IMAGE, description="Image to be splited up.", optional=false)
	JEXData imageData;
	
	/////////// Define Parameters ///////////
	
	@ParameterMarker(uiOrder=1, name="Dim to Split", description="Name of the dimension to split", ui=MarkerConstants.UI_TEXTFIELD, defaultText="Color")
	String dim;
	
	@ParameterMarker(uiOrder=2, name="Keep Dim?", description="Keep the dimension name in the resultant images " +
			"(i.e., the new objects with have a dimension matching the name of the dimension that was split, " +
			"have a size of one, and a value matching the original value)", ui=MarkerConstants.UI_CHECKBOX, defaultBoolean=false)
	Boolean keep;
	
	/////////// Define Outputs ///////////
	
	@OutputMarker(uiOrder=1, name="Split Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant split image stack", enabled=true)
	Vector<JEXData> output = new Vector<JEXData>();

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
		// imageData.getDataMap();
		if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		
		// Run the function
		TreeMap<DimensionMap,String> imageMap = ImageReader.readObjectToImagePathTable(imageData);
		int count = 0, percentage = 0;
		for (DimTable subTable : imageData.getDimTable().getSubTableIterator(dim))
		{
			TreeMap<DimensionMap,String> splitImageMap = new TreeMap<DimensionMap,String>();
			for (DimensionMap map : subTable.getMapIterator())
			{
				String copiedFile = JEXWriter.saveFile(new File(imageMap.get(map)));
				if(keep)
				{
					splitImageMap.put(map.copy(), copiedFile);
				}
				else
				{
					DimensionMap newMap = map.copy();
					newMap.remove(dim);
					splitImageMap.put(newMap.copy(), copiedFile);
				}
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) imageMap.size())));
				JEXStatics.statusBar.setProgressPercentage(percentage);
			}
			output.add(ImageWriter.makeImageStackFromPaths(imageData.name + " " + dim + " " + subTable.getDimWithName(dim).min(), splitImageMap));
			
		}
		if(output.size() == 0)
		{
			return false;
		}
		
		// Return status
		return true;
	}
}
