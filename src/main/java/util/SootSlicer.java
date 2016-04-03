package util;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import com.google.common.base.Verify;

import daikon.PptTopLevel;
import daikon.ValueTuple;
import daikon.VarInfo;
import daikon.util.Pair;
import dynslicer.InstrumentConditionals;
import soot.Body;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.options.Options;
import util.DaikonRunner.DaikonTrace;

public class SootSlicer {

	public void computeErrorSlices(File classDir, String classPath, Collection<DaikonTrace> traces) {
		loadSootScene(classDir, classPath);
		for (DaikonTrace t : traces) {
			computeErrorSlice(t);
		}
	}

	public void computeErrorSlice(DaikonTrace trace) {

		Iterator<Pair<PptTopLevel, ValueTuple>> iterator = trace.trace.iterator();
		List<Unit> sootTrace = new LinkedList<Unit>();
		while (iterator.hasNext()) {
			sootTrace.addAll(bar(iterator));
		}

		for (Unit u : sootTrace) {
			System.err.println("   " + u);
		}

		System.err.println(".......");
	}
	
	private List<Unit> bar(Iterator<Pair<PptTopLevel, ValueTuple>> iterator) {
		final List<Unit> sootTrace = new LinkedList<Unit>();
		Pair<PptTopLevel, ValueTuple> ppt = iterator.next();
		Verify.verify(ppt.a.name.endsWith(":::ENTER"), "Ppt is not a procedure entry: " + ppt.a.name);
		
		SootMethod sm = findMethodForPpt(ppt.a);
		Body body = sm.retrieveActiveBody();
		
		Stack<SootMethod> methodStack = new Stack<SootMethod>(); 
		methodStack.push(sm);		
		
		while (iterator.hasNext()) {
			ppt = iterator.next();
			if (ppt.a.name.contains(InstrumentConditionals.pcMethodName) && ppt.a.name.endsWith(":::ENTER")) {
				VarInfo vi = ppt.a.find_var_by_name(InstrumentConditionals.pcMethodArgName);
				//skip the exit of this method as well.
				ppt = iterator.next();
				long arg = (Long)ppt.b.getValueOrNull(vi);
				List<Integer> skipList = new LinkedList<Integer>();
				sootTrace.add(findUnitAtPos(body, arg, skipList));
				for (int i : skipList) {
					ppt = iterator.next();
					Verify.verify(ppt.a.name.contains(InstrumentConditionals.pcMethodName) && ppt.a.name.endsWith(":::ENTER"));
					vi = ppt.a.find_var_by_name(InstrumentConditionals.pcMethodArgName);
					arg = (Long)ppt.b.getValueOrNull(vi);
					Verify.verify(arg==(long)i, "Wrong number "+arg+"!="+i);
					ppt = iterator.next();
					Verify.verify(ppt.a.name.contains(InstrumentConditionals.pcMethodName) && ppt.a.name.contains(":::EXIT"));
				}
			} else if (ppt.a.name.contains(InstrumentConditionals.conditionalMethodName)&& ppt.a.name.endsWith(":::ENTER")) {
				//skip the exit of this method as well.
				ppt = iterator.next();				
			} else if (ppt.a.name.endsWith(":::ENTER")) {
				sm = findMethodForPpt(ppt.a);
				body = sm.retrieveActiveBody();
				methodStack.push(sm);								
			} else if (ppt.a.name.contains(":::EXIT")) {
				methodStack.pop();
				sm = methodStack.peek();
				body = sm.retrieveActiveBody();
			} else {
				System.err.println("Don't know how to handle " + ppt.a.name);
			}
		}	
		return sootTrace;
	}
	
	private Unit findUnitAtPos(Body body, long pos, List<Integer> outSkipList) {
		PatchingChain<Unit> units = body.getUnits();
		Unit ret = null;
		for (Unit u : units) {
			if (u instanceof InvokeStmt) {
				InvokeStmt ivk = (InvokeStmt)u;
				InvokeExpr ie = ivk.getInvokeExpr();
				List<Value> args = ie.getArgs();

				if (isPcMethod(ivk)
						&& (((IntConstant)args.get(0)).value == (int)pos)) {
					Unit next = units.getSuccOf(u);
					while (isPcMethod(next) && next!=null) {
						Value v = ((InvokeStmt)next).getInvokeExpr().getArg(0);
						outSkipList.add(((IntConstant)v).value );								
						next = units.getSuccOf(next);
					}
					return next;
				}
			}
		}
		return ret;
	}
	
	private boolean isPcMethod(Unit u) {
		if (u instanceof InvokeStmt) {
			InvokeStmt ivk = (InvokeStmt)u;
			return ivk.getInvokeExpr().getMethod().getName().equals(InstrumentConditionals.pcMethodName);
		}
		return false;
	}
	



	private SootMethod findMethodForPpt(PptTopLevel ppt) {
		String qualifiedMethodName = ppt.name.substring(0, ppt.name.indexOf("("));
		String className = qualifiedMethodName.substring(0, qualifiedMethodName.lastIndexOf('.'));
		String methodName = qualifiedMethodName.substring(qualifiedMethodName.lastIndexOf('.') + 1,
				qualifiedMethodName.length());

		SootClass sc = Scene.v().getSootClass(className);
		if (className.endsWith(methodName)) {
			// constructor call.
			methodName = "<init>";
		}

		SootMethod sm = sc.getMethodByName(methodName);

		return sm;
	}

	private void loadSootScene(File classDir, String classPath) {
		Options sootOpt = Options.v();
		sootOpt.set_keep_line_number(true);
		sootOpt.set_prepend_classpath(true); // -pp
		sootOpt.set_output_format(Options.output_format_class);
		sootOpt.set_java_version(Options.java_version_1_7);
		sootOpt.set_soot_classpath(classPath);
		sootOpt.set_src_prec(Options.src_prec_class);
		sootOpt.set_asm_backend(true);
		List<String> processDirs = new LinkedList<String>();
		processDirs.add(classDir.getAbsolutePath());
		sootOpt.set_process_dir(processDirs);
		sootOpt.set_keep_offset(true);
		sootOpt.set_print_tags_in_output(true);
		sootOpt.set_validate(true);

		sootOpt.setPhaseOption("jb.a", "enabled:false");
		sootOpt.setPhaseOption("jop.cpf", "enabled:false");
		sootOpt.setPhaseOption("jb", "use-original-names:true");

		Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
		Scene.v().addBasicClass("java.lang.Thread", SootClass.SIGNATURES);
		Scene.v().addBasicClass("java.lang.ThreadGroup", SootClass.SIGNATURES);

		Scene.v().loadBasicClasses();
		Scene.v().loadNecessaryClasses();

		/*
		 * TODO: apply some preprocessing stuff like:
		 * soot.jimple.toolkits.base or maybe the optimize option from soot.
		 */
		for (SootClass sc : Scene.v().getClasses()) {
			if (sc.resolvingLevel() < SootClass.SIGNATURES) {
				sc.setResolvingLevel(SootClass.SIGNATURES);
			}
		}

	}
}
