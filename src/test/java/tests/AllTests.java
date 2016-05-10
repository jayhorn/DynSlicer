/**
 * 
 */
package tests;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.joogie.GlobalsCache;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dynslicer.Main;
import util.Util;

/**
 * @author schaef
 *
 */
@RunWith(Parameterized.class)
public class AllTests {

	private static final String userDir = System.getProperty("user.dir") + "/";
	private static final String testRoot = userDir + "src/test/resources/";

	@Parameterized.Parameters(name = "{index}: check ({1})")
	public static Collection<Object[]> data() {
		List<Object[]> srcDirs = new LinkedList<Object[]>();
		final File root_dir = new File(testRoot);
		
		File[] directoryListing = root_dir.listFiles();
		if (directoryListing != null) {
			for (File child : directoryListing) {
				if (child.isDirectory()) {
					srcDirs.add(new Object[]{child.getAbsoluteFile()});
				}
			}
		}
		if (srcDirs.isEmpty()) {
			throw new RuntimeException("Test data not found!");
		}
		return srcDirs;
	}
	
	private final File srcDir;
	
	public AllTests(File testDir) {
		this.srcDir = testDir;
		GlobalsCache.restInstance();		
//		soot.G.reset();

	}

	@Test
	public void test() throws IOException {
		//Reset everything
		File classDir = new File("classes_and_tests");
		if (classDir.exists()) {
			FileUtils.deleteDirectory(classDir); 
		}
		Assert.assertTrue(classDir.mkdir());
		
		try {
			System.out.println("Create temp dir");
			File testDir = new File("slicerData");
			if (testDir.exists()) {
				FileUtils.deleteDirectory(testDir); 
			}
			Assert.assertTrue(testDir.mkdir());
			System.out.println("Compile source");
			Util.compileJavaFiles(srcDir, ".", classDir);
			System.out.println("Run Main");
			Main.main(new String[]{".", classDir.getAbsolutePath(), testDir.getAbsolutePath()});
			System.out.println("Clean up");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			FileUtils.deleteDirectory(classDir); 			
		}
		Assert.assertTrue(true);
	}
		
}
