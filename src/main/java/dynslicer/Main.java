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
import util.RandoopRunner;

public class Main {

	public static void main(String[] args) {
//		final String inFile = "/Users/schaef/git/integration-test/corpus/sorting/00_sort/Sort01/classes/Sort01.class";
//		final String outFile = "./Sort01.class";
		final String classListFile = "classes.txt";
		final String testDir = "rd_tests";

		String classPath = ".";
		final String classDir = "/Users/schaef/git/integration-test/corpus/sorting/00_sort/Sort01/classes";

		Set<String> classes = getClasses(classDir);
		createClassListFile(classes, classListFile);

		if (!new File(testDir).exists()) {
			RandoopRunner rr = new RandoopRunner();
			rr.run(classPath + File.pathSeparator + classDir, classListFile, testDir, 5);
		} else {
			System.out.println("Tests already exist. Delete " + testDir + " to re-run randoop.");
		}
		
		final String transformedDir = "trans_classes";
		transformAllClasses(new File(classDir), new File(transformedDir));

		DaikonRunner dr = new DaikonRunner();
		List<String> cp = new LinkedList<String>();
		cp.add(classPath);
		cp.add("lib/daikon.jar");
		cp.add(testDir);
		cp.add(transformedDir);
		final String daikonClassPath = StringUtils.join(cp, File.pathSeparator);
		dr.run(daikonClassPath, "ErrorTestDriver");
	}

	private static void transformAllClasses(File classDir, File outDir) {
		System.out.println("Transforming classes");
		InstrumentConditionals icond = new InstrumentConditionals();
				
		for (Iterator<File> iter = FileUtils.iterateFiles(classDir, new String[] { "class" }, true); iter
				.hasNext();) {
			File classFile = iter.next();
			File transformedClass = new File(classFile.getAbsolutePath().replace(classDir.getAbsolutePath(), outDir.getAbsolutePath()));
			final String tClassName = transformedClass.getAbsolutePath();
			if (tClassName.contains(File.separator)) {
				File tClassDir = new File(tClassName.substring(0, tClassName.lastIndexOf(File.separator)));
				tClassDir.mkdirs();	
			}
			icond.instrumentClass(classFile.getAbsolutePath(), transformedClass.getAbsolutePath());
		}
		System.out.println("Done.");
	}

	private static void createClassListFile(Set<String> classes, final String fileName) {
		try (OutputStream streamOut = new FileOutputStream(fileName);
				PrintWriter writer = new PrintWriter(new OutputStreamWriter(streamOut, "UTF-8"))) {
			for (String className : classes) {
				writer.println(className);
			}
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
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