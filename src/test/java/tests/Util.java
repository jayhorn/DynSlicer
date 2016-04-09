/**
 * 
 */
package tests;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.common.io.Files;

/**
 * @author schaef
 *
 */
public class Util {
	 /**
	   * Compiles a sourceFile into a temp folder and returns this folder or null
	   * if compilation fails.
	   *
	   * @param sourceFile the source file to compile
	   * @param classPath the classPath need to compile the file.
	   * @return the folder that contains the class file(s) or null if compilation
	   *         fails.
	   * @throws IOException
	   */
	  public static File compileJavaFile(File sourceFile, String classPath) throws IOException {
			final File tempDir = Files.createTempDir();
			final String javac_command = String.format("javac -cp %s -g %s -d %s", classPath, sourceFile.getAbsolutePath(),
				tempDir.getAbsolutePath());

			ProcessBuilder pb = new ProcessBuilder(javac_command.split(" "));
			pb.redirectOutput(Redirect.INHERIT);
			pb.redirectError(Redirect.INHERIT);
			Process p = pb.start();

			try {
				p.waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			}

			return tempDir;
	  }

		/**
		 * Compiles a set of sourceFiles into a temp folder and returns this folder
		 * or null if compilation fails.
		 * 
		 * @param sourceFiles an array of files to compile
		 * @param classPath the classPath need to compile the file.
		 * @return the folder that contains the class file(s) or null if compilation
		 *         fails.
		 * @throws IOException
		 */
		public static void compileJavaFiles(File[] sourceFiles, String classPath, File outDir) throws IOException {			
			StringBuilder sb = new StringBuilder();
			for (File f : sourceFiles) {
				sb.append(f.getAbsolutePath());
				sb.append(" ");
			}
			final String javac_command = String.format("javac -g -cp %s -d %s %s", classPath, outDir.getAbsolutePath(), sb.toString());

			System.out.println(javac_command);

			ProcessBuilder pb = new ProcessBuilder(javac_command.split(" "));
			pb.redirectOutput(Redirect.INHERIT);
			pb.redirectError(Redirect.INHERIT);
			Process p = pb.start();

			try {
				p.waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public static File compileJavaFiles(File sourceDir, String classPath) throws IOException {
			final File tempDir = Files.createTempDir();
			compileJavaFiles(sourceDir, classPath, tempDir);
			return tempDir;
		}
		
		public static void compileJavaFiles(File sourceDir, String classPath, File outDir) throws IOException {
			List<File> srcFiles = new LinkedList<File>();
			for (Iterator<File> iter = FileUtils.iterateFiles(sourceDir, new String[] { "java" }, true); iter
			.hasNext();) {
				srcFiles.add(iter.next());
			}			
			compileJavaFiles(srcFiles.toArray(new File[srcFiles.size()]), classPath, outDir);
		}
}
