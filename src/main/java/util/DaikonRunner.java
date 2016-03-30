/**
 * 
 */
package util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import daikon.FileIO;
import daikon.PptMap;
import daikon.PptTopLevel;
import daikon.ValueTuple;
import daikon.VarInfo;

/**
 * @author schaef
 *
 */
public class DaikonRunner extends AbstractRunner {
	
	public void run(String classPath, String mainClass) {
		//Run Daikon
		List<String> cmd = new LinkedList<String>();
		cmd.add("java");
		cmd.add("-classpath");
		cmd.add(classPath+File.pathSeparator+"lib/daikon.jar");
		cmd.add("daikon.Chicory");
		cmd.add(mainClass);		
		execute(cmd);
		
		//gunzip the dtrace file
		cmd = new LinkedList<String>();
		cmd.add("gunzip");
		cmd.add("-f");
		cmd.add(mainClass+".dtrace.gz");
		execute(cmd);
		
		parseDTraceFile(mainClass+".dtrace");
	}
	
	protected void parseDTraceFile(String dtraceFileName) {
	    CollectDataProcessor processor = new CollectDataProcessor();
	    PptMap ppts = new PptMap();
	    
	    try {
	      FileIO.read_data_trace_files (Arrays.asList(dtraceFileName), ppts, processor, false);
	    } catch (Exception e) {
	      throw new Error(e);
	    }
	   
	    for ( Iterator<PptTopLevel> iter = ppts.ppt_all_iterator(); iter.hasNext(); ) {
	    	PptTopLevel ppt = iter.next();
	    	
	    	System.err.println(ppt.name);
	    }
	    
	    // At this point, the processor has been called on each sample in turn.
	    // Now, we can do whatever we like with the data.
	    for (PptTopLevel ppt : processor.samples.keySet()) {	    	
	      for (ValueTuple vt : processor.samples.get(ppt)) {
	        System.out.printf("%nSample for %s :%n", ppt.name());
	        for (VarInfo vi : ppt.var_infos) {
	          System.out.printf("%s = %s%n", vi.name(), vt.getValueOrNull(vi));
	        }
	      }
	    }
	}
	
	  /**
	   * Populates the <code>samples</code> map with all the data read from the file.
	   * This is only reasonable for small trace files, since all the data will
	   * be retained in memory!
	   */
	  public static class CollectDataProcessor extends FileIO.Processor {

	    public Map<PptTopLevel,List<ValueTuple>> samples = new LinkedHashMap<PptTopLevel,List<ValueTuple>>();

	    /** Process the sample, by adding it to the <code>samples</code> map. */
	    /*@RequiresNonNull("FileIO.data_trace_state")*/
	    public void process_sample (PptMap all_ppts, PptTopLevel ppt,
	                                ValueTuple vt, /*@Nullable*/ Integer nonce) {

	      // Add orig and derived variables to the ValueTuple
	      assert vt.vals != null : "@AssumeAssertion(nullness): bug: Checker Framework bug:  vals is a non-null array, but is reported as nullable";
	      FileIO.compute_orig_variables(ppt, vt.vals, vt.mods, nonce);
	      FileIO.compute_derived_variables(ppt, vt.vals, vt.mods);

	      // Intern the sample, to save space, since we are storing them all.
	      vt = new ValueTuple(vt.vals, vt.mods);

	      // Add the sample to the map
	      if (! samples.containsKey(ppt)) {
	        samples.put(ppt, new ArrayList<ValueTuple>());
	      }
	      samples.get(ppt).add(vt);
	    }
	  }
	
}
