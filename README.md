# JEX-TemplatePluginProject
This maven project can be copied, imported into Eclipse as a maven project, and edited to create your own JEX Functions/Plugins. The project depends upon JEX, allowing you to depend on whichever version you choose and avoids you having to create your own version of JEX that contains your particular JEX functions. This also allows you to debug your funciton's run method "on the fly" instead of having to compile and run to test each change.

After importing the project as a Maven project in eclipse, set up a run configuration. Set the main class as "jex.Main" and the program arguments to be "-fromJar" (case dependent) and VM arguments as "-Xmx1024m" (or other amount you choose for your application). Run the application via the "Debug" button of Eclipse and the application will run JEX from the JEX jar that the maven project depends upon but will include your plugins in the toolbox of functions in JEX automatically with live debugging capabilities. If this doesn't work immediately, right-click the project and choose "Maven", then "Update Project...", and select to "Force Update of Snapshots/Releases". Then try to run again.

Be sure to edit the pom.xml file of the projec to enter your project's information such as the project name and author information.

Enjoy.
