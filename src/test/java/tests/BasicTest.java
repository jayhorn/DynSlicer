/**
 * 
 */
package tests;

import org.junit.Assert;
import org.junit.Test;

import dynslicer.Main;

/**
 * @author schaef
 *
 */
public class BasicTest {

	@Test
	public void test() {
		//check without args - should terminate immediately.
		Main.main(new String[]{});

		Main.main(new String[]{".", "/Users/schaef/git/integration-test/corpus/sorting/00_sort/Sort01/classes"});
		
		Assert.assertTrue(true);
	}

}
