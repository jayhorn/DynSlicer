/**
 * 
 */
package util;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import daikon.FileIO;
import daikon.PptMap;
import daikon.PptTopLevel;
import daikon.ValueTuple;
import daikon.util.Pair;
import dynslicer.Main;

/**
 * @author schaef
 *
 */
public class DaikonRunner extends AbstractRunner {

	public Set<DaikonTrace> run(String classPath, String mainClass, Set<String> classesToInclude) {
		// Run Daikon
		List<String> cmd = new LinkedList<String>();
		cmd.add("java");
		cmd.add("-classpath");
		cmd.add(classPath + File.pathSeparator + Main.basePath+"lib/daikon.jar");
		cmd.add("daikon.Chicory");
		
		StringBuilder sb = new StringBuilder();
		sb.append("--ppt-select-pattern=^(ErrorTestDriver|ErrorTest(\\d)+");
		for (String className : getNamespacesFromClasses(classesToInclude)) {
			sb.append("|");
			sb.append(className);
		}
		sb.append(")\\S*");
//		sb.append("--ppt-select-pattern=\"\\S*\"");
		String inclusionRegex = sb.toString();
		System.err.println(inclusionRegex);
		cmd.add(inclusionRegex);
		
//		cmd.add("--dtrace-file=ErrorTestDriver.dtrace");
		cmd.add(mainClass);
		execute(cmd);
		
		// gunzip the dtrace file
		cmd = new LinkedList<String>();
		cmd.add("gunzip");
		cmd.add("-f");
		cmd.add(mainClass + ".dtrace.gz");
		execute(cmd);

		return parseDTraceFile(mainClass + ".dtrace");
	}

	private Set<String> getNamespacesFromClasses(Set<String> classNames) {
		Set<String> ret = new HashSet<String>();
		for (String s : classNames) {
			if (!s.contains(".")) {
				ret.add(s);
			} else {
				ret.add(s.substring(0, s.lastIndexOf(".")));
			}
		}
		return ret;
	}
	
	public Set<DaikonTrace> parseDTraceFile(String dtraceFileName) {
		CollectDataProcessor processor = new CollectDataProcessor();
		PptMap ppts = new PptMap();
		try {
			FileIO.read_data_trace_files(Arrays.asList(dtraceFileName), ppts, processor, false);
		} catch (Exception e) {
			throw new Error(e);
		}
		return processor.traces;
	}

	/**
	 * Populates the <code>samples</code> map with all the data read from the
	 * file.
	 * This is only reasonable for small trace files, since all the data will
	 * be retained in memory!
	 */
	public static class CollectDataProcessor extends FileIO.Processor {

		private DaikonTrace currentTrace = null;
		public Set<DaikonTrace> traces = new LinkedHashSet<DaikonTrace>(); 
		
		/** Process the sample, by adding it to the <code>samples</code> map. */
		public void process_sample(PptMap all_ppts, PptTopLevel ppt, ValueTuple vt, /* @Nullable */ Integer nonce) {
			FileIO.compute_orig_variables(ppt, vt.vals, vt.mods, nonce);
			FileIO.compute_derived_variables(ppt, vt.vals, vt.mods);
			// Intern the sample, to save space, since we are storing them all.
			vt = new ValueTuple(vt.vals, vt.mods);			
			final String enterTestRegex = "ErrorTest(\\d)+\\.test(\\d)+\\(\\):::ENTER";			
			if (ppt.name().matches(enterTestRegex)) {
				currentTrace = new DaikonTrace();
				currentTrace.addPoint(ppt, vt);
				traces.add(currentTrace);
			} else if (currentTrace!=null) {
				currentTrace.addPoint(ppt, vt);
			}
		}
	}

	public static class DaikonTrace {
		public final List<Pair<PptTopLevel, ValueTuple>> trace = new LinkedList<Pair<PptTopLevel, ValueTuple>>();
		
		public void addPoint(PptTopLevel ppt, ValueTuple val) {
			trace.add(new Pair<PptTopLevel, ValueTuple>(ppt, val));
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (Pair<PptTopLevel, ValueTuple> p : trace) {
				sb.append(p.a.name());
				sb.append("; ");
			}
			sb.append("\n");
			return sb.toString();
		}
	}

}
