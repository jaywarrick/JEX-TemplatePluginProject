package plugins;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.FileReader;
import Database.SingleUserDatabase.JEXWriter;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.ParameterMarker;

import java.io.File;
import java.io.IOException;
import java.util.TreeMap;
import java.util.Vector;

import org.scijava.plugin.Plugin;

import jex.statics.JEXStatics;
import logs.Logs;
import miscellaneous.FileUtility;
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
 */

@Plugin(
		type = JEXPlugin.class,
		name="Example - Export Multiple Table Files",
		menuPath="Template Functions",
		visible=true,
		description="Export multiple files to a folder."
		)
public class Example_ExportMultipleFiles extends JEXPlugin {
	
	public Example_ExportMultipleFiles()
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
	
	@InputMarker(uiOrder=1, name="File 1", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp1;
	
	@InputMarker(uiOrder=2, name="File 2", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp2;
	
	@InputMarker(uiOrder=3, name="File 3", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp3;
	
	@InputMarker(uiOrder=4, name="File 4", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp4;
	
	@InputMarker(uiOrder=5, name="File 5", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp5;
	
	@InputMarker(uiOrder=6, name="File 6", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp6;
	
	@InputMarker(uiOrder=7, name="File 7", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp7;
	
	@InputMarker(uiOrder=8, name="File 8", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp8;
	
	@InputMarker(uiOrder=9, name="File 9", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp9;
	
	@InputMarker(uiOrder=10, name="File 10", type=MarkerConstants.TYPE_FILE, description="Files to be exported", optional=false)
	JEXData temp10;
	
	/////////// Define Parameters ///////////

	@ParameterMarker(uiOrder=1, name="Folder Path", description="Location to which the files will be copied", ui=MarkerConstants.UI_FILECHOOSER)
	String folderPath;
	
	@ParameterMarker(uiOrder=2, name="File Extension", description="Extension to put on the file", ui=MarkerConstants.UI_DROPDOWN, choices={ "csv", "arff", "txt" }, defaultChoice=0)
	String ext;
	
	/////////// Define Outputs ///////////
	// no output
	
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
		Vector<JEXData> datas = new Vector<JEXData>();
		if(temp1 != null)
		{
			if(!temp1.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp1);
		}
		
		if(temp2 != null)
		{
			if(!temp2.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp2);
		}
		
		if(temp3 != null)
		{
			if(!temp3.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp3);
		}
		
		if(temp4 != null)
		{
			if(!temp4.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp4);
		}
		
		if(temp5 != null)
		{
			if(!temp5.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp5);
		}
		
		if(temp6 != null)
		{
			if(!temp6.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp6);
		}
	
		if(temp7 != null)
		{
			if(!temp7.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp7);
		}
		
		if(temp8 != null)
		{
			if(!temp8.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp8);
		}
		
		if(temp9 != null)
		{
			if(!temp9.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp9);
		}
		
		if(temp10 != null)
		{
			if(!temp10.getTypeName().getType().equals(JEXData.FILE))
			{
				return false;
			}
			datas.add(temp10);
		}
		
		File folder = new File(folderPath);
		// Run the function
		int n = 1;
		for (JEXData data : datas)
		{
			TreeMap<DimensionMap,String> filePaths = FileReader.readObjectToFilePathTable(data);
			
			if(!folder.exists())
			{
				folder.mkdirs();
			}
			
			int count = 0;
			int total = filePaths.size();
			JEXStatics.statusBar.setProgressPercentage(0);
			for (DimensionMap dim : filePaths.keySet())
			{
				String path = filePaths.get(dim);
				File f = new File(path);
				String fileName = f.getName();
				String newFilePath = folder.getAbsolutePath() + File.separator + data.name + " - " + FileUtility.getFileNameWithoutExtension(fileName) + "." + ext;
				
				try
				{
					JEXWriter.copy(f, new File(newFilePath));
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				
				Logs.log("File Object " + n + ": Finished processing " + count + " of " + total + ".", 1, this);
				
			}
			// Status bar
			int percentage = (int) (100 * ((double) n / (double) datas.size()));
			JEXStatics.statusBar.setProgressPercentage(percentage);
			n = n + 1;
		}
		
		// Return status
		return true;
	}
}
