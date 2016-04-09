package dynslicer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.objectweb.asm.ClassReader;

import util.DaikonRunner;
import util.DaikonRunner.DaikonTrace;
import util.InstrumentationRunner;
import util.RandoopRunner;

public class Main {

	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println("use with classpath, classdir, and testDir as arguments.");
			return;
		}
		String classPath = args[0];
		final String classDir = args[1];
		final File testDir = new File(args[2]);

		Set<String> classes = getClasses(classDir);
		// run randoop
		File classListFile = null;
		final String randoopClassPath = classPath + File.pathSeparator + classDir;
		try {
			classListFile = createClassListFile(classes);
			RandoopRunner rr = new RandoopRunner();
			rr.run(randoopClassPath, classListFile, new File(classDir), 1, 2);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (classListFile != null && !classListFile.delete()) {
				throw new RuntimeException("failed to clean up");
			}
		}
		
		System.out.println("Transforming class files");
		InstrumentationRunner ir = new InstrumentationRunner();
		ir.run(classDir, testDir.getAbsolutePath(), randoopClassPath);
		System.out.println("Transformation done.");
		
//		InstrumentConditionals icond = new InstrumentConditionals();
//		icond.transformAllClasses(new File(classDir), testDir);

		DaikonRunner dr = new DaikonRunner();
		List<String> cp = new LinkedList<String>();
		cp.add(classPath);
		cp.add("lib/daikon.jar");
		cp.add(testDir.getAbsolutePath());		
		final String daikonClassPath = StringUtils.join(cp, File.pathSeparator);
		Set<DaikonTrace> traces = dr.run(daikonClassPath, "ErrorTestDriver");

		System.err.println("Number of Traces " + traces.size());
		
		// compute the slices and run the fault localization:
		
//		SootSlicer ss = new SootSlicer();
//		SootClass traceClass = ss.computeErrorSlices(testDir, classPath+File.pathSeparator+"lib/junit.jar", traces);
//		
//		GroupTraces gt = new GroupTraces();
//		gt.groupStuff(traceClass);
	}

	private static File createClassListFile(Set<String> classes) throws IOException {
		File classListFile = File.createTempFile("clist", "txt");
		try (OutputStream streamOut = new FileOutputStream(classListFile);
				PrintWriter writer = new PrintWriter(new OutputStreamWriter(streamOut, "UTF-8"))) {
			for (String className : classes) {
				writer.println(className);
			}
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
		return classListFile;
	}

	private static Set<String> getClasses(final String classDir) {
		Set<String> classes = new HashSet<String>();
		for (Iterator<File> iter = FileUtils.iterateFiles(new File(classDir), new String[] { "class" }, true); iter
				.hasNext();) {
			File classFile = iter.next();
			try (FileInputStream is = new FileInputStream(classFile);) {
				ClassReader cr = new ClassReader(is);
				classes.add(cr.getClassName().replace('/', '.'));
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
		}
		return classes;
	}

}