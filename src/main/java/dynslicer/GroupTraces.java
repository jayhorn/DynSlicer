/**
 * 
 */
package dynslicer;

import org.joogie.GlobalsCache;
import org.joogie.report.Report;
import org.joogie.soot.SootBodyTransformer;

import boogie.ProgramFactory;
import joogie2.ProgramAnalysis;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.scalar.ConstantPropagatorAndFolder;
import soot.jimple.toolkits.scalar.CopyPropagator;

/**
 * @author schaef
 *
 */
public class GroupTraces {

	public void groupStuff(SootClass traceClass) {
		Report report = new Report();
		SootBodyTransformer trans = new SootBodyTransformer(report);
		for (SootMethod sm : traceClass.getMethods()) {
			if (!sm.isConstructor()) {
				ConstantPropagatorAndFolder.v().transform(sm.getActiveBody());
				CopyPropagator.v().transform(sm.getActiveBody());				
				trans.transform(sm.getActiveBody());
			}
		}
				
		ProgramFactory pf = GlobalsCache.v().getPf();
		ProgramAnalysis pa = new ProgramAnalysis(pf, report);
		pa.CavModeHack = false;
		org.joogie.Options.v().useOldStyleEncoding(true);
		pa.runFullProgramAnalysis();
		

	}
}
