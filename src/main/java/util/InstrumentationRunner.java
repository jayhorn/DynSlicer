/**
 * 
 */
package util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import dynslicer.Main;

/**
 * @author schaef
 *
 */
public class InstrumentationRunner extends AbstractRunner {

	public void run(String classDir, String testDir, String classPath) {
		// Run Daikon
		final String instrumenterClassPath = classPath + File.pathSeparator + Main.basePath+"lib/instrumenter.jar";
		List<String> cmd = new LinkedList<String>();
		cmd.add("java");
		cmd.add("-classpath");
		cmd.add(instrumenterClassPath);
		cmd.add("bc_instrumenter.Main");
		cmd.add(classDir);
		cmd.add(testDir);
				execute(cmd);
	}
}
