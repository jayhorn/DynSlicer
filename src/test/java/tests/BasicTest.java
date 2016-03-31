/**
 * 
 */
package tests;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.Files;

import dynslicer.Main;

/**
 * @author schaef
 *
 */
public class BasicTest {

	@Test
	public void test() throws IOException {
		//check without args - should terminate immediately.
		Main.main(new String[]{});

		final File srcDir = new File("src/test/resources/test01/");
		File classDir = null;
		File testDir = null;
		try {
			System.out.println("Create temp dir");
			testDir = Files.createTempDir();
			System.out.println("Compile source");
			classDir = Util.compileJavaFiles(srcDir, ".");
			System.out.println("Run Main");
			Main.main(new String[]{".", classDir.getAbsolutePath(), testDir.getAbsolutePath()});
			System.out.println("Clean up");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			FileUtils.deleteDirectory(classDir); 
			FileUtils.deleteDirectory(testDir); 
		}
		Assert.assertTrue(true);
	}
	
}
