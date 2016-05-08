/**
 * 
 */
package util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import com.google.common.collect.Lists;

import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeStmt;
import soot.toolkits.scalar.UnusedLocalEliminator;

/**
 * @author schaef
 *
 */
public class Slicer {

	public void sliceFromLastAssertion(SootMethod sm) {
		List<Unit> units = new LinkedList<Unit>(sm.getActiveBody().getUnits());
		ListIterator<Unit> listIter = units.listIterator(units.size());

		List<Unit> sliced = new LinkedList<Unit>();
		
		Set<Local> relevantLocals = new HashSet<Local>();
		//first find the last assertion and the variables in this assertion 
		//from which we want to start slicing.
		while (listIter.hasPrevious()) {
			Unit u = listIter.previous();
			if (u instanceof InvokeStmt && ((InvokeStmt) u).getInvokeExpr().getMethod().getName()
					.equals(TraceExtractor.assertionMethodName)) {
				for (ValueBox vb : u.getUseBoxes()) {
					Value v = vb.getValue();
					if (v instanceof Local) {
						relevantLocals.add((Local)v);
					}
				}
				sliced.add(u);
				break;
			}
		}
		//now slice for relevantLocals
		while (listIter.hasPrevious()) {
			Unit u = listIter.previous();
			boolean definesRelevantLocal = false;
			for (ValueBox vb : u.getDefBoxes()) {
				if (relevantLocals.contains(vb.getValue())) {
					definesRelevantLocal = true;
					/*
					 * since we know that this is a trace without branching,
					 * we can remove  the def var from the list of relevant statements.
					 */
					relevantLocals.remove(vb.getValue());
				}
			}
			if (definesRelevantLocal) {
				for (ValueBox vb : u.getUseBoxes()) {
					Value v = vb.getValue();
					if (v instanceof Local) {
						relevantLocals.add((Local)v);
					}
				}
				sliced.add(u);
			}
		}
		List<Unit> slicedBody = new LinkedList<Unit>(Lists.reverse(sliced));
		
		sm.getActiveBody().getUnits().retainAll(slicedBody);
		UnusedLocalEliminator.v().transform(sm.getActiveBody());		
	}

}
