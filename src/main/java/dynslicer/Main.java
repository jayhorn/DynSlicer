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

import com.google.common.io.Files;

import soot.SootClass;
import util.DaikonRunner;
import util.DaikonRunner.DaikonTrace;
import util.InstrumentationRunner;
import util.RandoopRunner;
import util.SootSlicer;
import util.Util;

public class Main {

	public static String basePath = "./";
	
	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			System.err.println("use with classpath, classdir, and testDir as arguments.");
			return;
		}
		String classPath = args[0];
		final File classDir = new File(args[1]);
		final File testDir = new File("./PlayItSafeFolderName/");
		final File testSrcDir = new File("./testSources/");
		
		if (testDir.exists()) {
			FileUtils.deleteDirectory(testDir);
		}
		
		final String junit = basePath+"lib/junit.jar";
		
		if (args.length == 4) {
			basePath = args[3];
		}
		
		System.out.println("Run Randoop");
		Set<String> classes = getClasses(classDir);
		// run randoop
		File classListFile = null;
		final String randoopClassPath = classPath + File.pathSeparator + classDir.getAbsolutePath();
		try {
			classListFile = createClassListFile(classes);
			RandoopRunner rr = new RandoopRunner();
			rr.run(randoopClassPath, classListFile, testSrcDir, 5, 5);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (classListFile != null && !classListFile.delete()) {
				throw new RuntimeException("failed to clean up");
			}
		}
		System.out.println("Completed: Run Randoop");
				
		System.out.println("Compile generated tests");
		//first copy over the classes over to a temp folder:
		File tempDir = Files.createTempDir();
		FileUtils.copyDirectory(classDir, tempDir);
		//compile test cases:
		final String classPathAndJUnit = classPath + File.pathSeparator + junit;
		Util.compileJavaFiles(testSrcDir, classPathAndJUnit+ File.pathSeparator + classDir.getAbsolutePath(), tempDir);		
		System.out.println("Completed: Compile generated tests");
		
		System.out.println("Transforming class files");
		InstrumentationRunner ir = new InstrumentationRunner();
		final String instrumentClassPath = classPathAndJUnit + File.pathSeparator + tempDir.getAbsolutePath();
		ir.run(tempDir, testDir, instrumentClassPath);
		//now the instrumented classes and tests are in testDir and we can delete tempDir
		FileUtils.deleteDirectory(tempDir);
		System.out.println("Transformation done.");
		
//		InstrumentConditionals icond = new InstrumentConditionals();
//		icond.transformAllClasses(new File(classDir), testDir);

		System.out.println("Running Daikon on transformed classes");
		DaikonRunner dr = new DaikonRunner();
		List<String> cp = new LinkedList<String>();
		cp.add(classPathAndJUnit);
		cp.add(basePath+"lib/daikon.jar");
		cp.add(testDir.getAbsolutePath());		
		final String daikonClassPath = StringUtils.join(cp, File.pathSeparator);
		Set<DaikonTrace> traces = dr.run(daikonClassPath, "ErrorTestDriver", classes);
		System.out.println("Completed: Running Daikon on transformed classes");
		System.err.println("Number of Traces " + traces.size());
		
		// compute the slices and run the fault localization:
		
		System.out.println("Computing the slices");
		SootSlicer ss = new SootSlicer();
		SootClass traceClass = ss.computeErrorSlices(testDir, daikonClassPath+File.pathSeparator+junit, traces);
		System.out.println("Completed: Computing the slices");
		
		System.out.println("Run the fault localization.");
		GroupTraces gt = new GroupTraces();
		gt.groupStuff(traceClass, ss);
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

	public static Set<String> getClasses(final File classDir) {
		Set<String> classes = new HashSet<String>();
		for (Iterator<File> iter = FileUtils.iterateFiles(classDir, new String[] { "class" }, true); iter
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