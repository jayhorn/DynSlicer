/**
 * 
 */
package dynslicer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.joogie.GlobalsCache;
import org.joogie.report.Report;
import org.joogie.soot.SootBodyTransformer;

import com.google.common.base.Verify;

import boogie.ProgramFactory;
import joogie2.ProgramAnalysis;
import soot.Local;
import soot.RefType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.InstanceFieldRef;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.StaticFieldRef;
import soot.jimple.toolkits.scalar.ConstantPropagatorAndFolder;
import soot.jimple.toolkits.scalar.CopyPropagator;
import util.SootSlicer;

/**
 * @author schaef
 *
 */
public class GroupTraces {

	SootSlicer sootSlicer;
	
	public void groupStuff(SootClass traceClass, SootSlicer ss) {
		sootSlicer = ss;
		Report report = new Report();
		SootBodyTransformer trans = new SootBodyTransformer(report);
		for (SootMethod sm : traceClass.getMethods()) {
			if (!sm.isConstructor() && !sm.getName().contains(SootSlicer.assertionMethodName)) {
				CopyPropagator.v().transform(sm.getActiveBody());
				ConstantPropagatorAndFolder.v().transform(sm.getActiveBody());
				replaceFieldsByLocals(sm);
				trans.transform(sm.getActiveBody());
			}
		}
				
		ProgramFactory pf = GlobalsCache.v().getPf();
		ProgramAnalysis pa = new ProgramAnalysis(pf, report);
		pa.CavModeHack = false;
		org.joogie.Options.v().useOldStyleEncoding(true);
		pa.runFullProgramAnalysis();
	}
	
	
	/**
	 * Note that this only makes sense under the assumption that
	 * this procedure only contains a single trace and that local propagation 
	 * has been applied before.
	 * Otherwise we would have to distinguish possible aliasing for
	 * Instance Fields.
	 * @param sm
	 */
	private void replaceFieldsByLocals(SootMethod sm) {
//		LocalMayAliasAnalysis lmaa = new LocalMayAliasAnalysis(new CompleteUnitGraph(sm.getActiveBody()));
		
		Map<Local, Map<SootField, Local>> repl = new HashMap<Local, Map<SootField, Local>>();
		Map<SootField, Local> static_repl = new HashMap<SootField, Local>();
		
		for (Unit u : new LinkedList<Unit>(sm.getActiveBody().getUnits())) {
//			System.err.println("- "+u);
			for (ValueBox vb : u.getUseAndDefBoxes()) {
				if (vb.getValue() instanceof InstanceFieldRef) {
					InstanceFieldRef r = (InstanceFieldRef)vb.getValue();
					Verify.verify(r.getBase() instanceof Local, "Unexpected type "+r.getBase().getClass().toString());
					Local l = (Local)r.getBase();
					if (!repl.containsKey(l)) {
						repl.put(l, new HashMap<SootField, Local>());
					}
					Map<SootField, Local> lrepl = repl.get(l);
					if (!lrepl.containsKey(r.getField())) {
						Local l2 = Jimple.v().newLocal(l.toString()+"__"+r.getField().getName()+"__", r.getField().getType());
						sm.getActiveBody().getLocals().add(l2);
						lrepl.put(r.getField(), l2);
					}
					//TODO add assertion that base wasnt null.
					soot.Type assertType = l.getType();
					if (assertType instanceof RefType) {
						assertType = RefType.v();
					}
					SootMethod assertMethod = sootSlicer.assertMethods.get(assertType);
					Verify.verifyNotNull(assertMethod,"No method of "+l.getType());
					Unit asrt = Jimple.v().newInvokeStmt(							
							Jimple.v().newStaticInvokeExpr(assertMethod.makeRef(), l, NullConstant.v()));
					asrt.addAllTagsOf(u);
					sm.getActiveBody().getUnits().insertBefore(asrt, u);					
					vb.setValue(lrepl.get(r.getField()));
				} else if (vb.getValue() instanceof StaticFieldRef) {
					StaticFieldRef r = (StaticFieldRef)vb.getValue();
					if (!static_repl.containsKey(r.getField())) {						
						Local l = Jimple.v().newLocal("____static_field_"+static_repl.size(), r.getField().getType());
						sm.getActiveBody().getLocals().add(l);
						static_repl.put(r.getField(), l);
					}
					vb.setValue(static_repl.get(r.getField()));
				}
			}
//			System.err.println("+ "+u);
		}
	
	}
	
}
