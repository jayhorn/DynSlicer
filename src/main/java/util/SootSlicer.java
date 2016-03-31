package util;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import daikon.PptTopLevel;
import daikon.ValueTuple;
import daikon.VarInfo;
import daikon.util.Pair;
import dynslicer.InstrumentConditionals;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
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

		Stack<Pair<SootMethod, Unit>> callStack = new Stack<Pair<SootMethod, Unit>>();
		for (Pair<PptTopLevel, ValueTuple> pair : trace.trace) {
			if (pair.a.name.endsWith(":::ENTER")) {
				if (pair.a.name.contains(InstrumentConditionals.conditionalMethodName)) {
					VarInfo vi = pair.a.find_var_by_name(InstrumentConditionals.conditionalMethodArgName);					
					boolean cond = (Long)pair.b.getValueOrNull(vi)!=0L;
					if (cond) {
						System.err.println("\tThen");
					} else {
						System.err.println("\tElse");
					}
				} else {
					SootMethod sm = findMethodForPpt(pair.a);									
					Unit entry = null;					
					Body b = sm.retrieveActiveBody();
					if (b!=null) {
						entry = b.getUnits().getFirst();						
					}
					callStack.push(Pair.of(sm, entry));
					System.err.println(sm.getName());
										
					for (VarInfo vi : pair.a.var_infos) {
						System.err.printf("%s = %s%n", vi.name(), pair.b.getValueOrNull(vi));
					}
				
					
					
				}
			} else if (pair.a.name.endsWith(":::EXIT")) {
				if (pair.a.name.contains(InstrumentConditionals.conditionalMethodName)) {
					// Do nothing
				} else {
					// SootMethod sm = findMethodForPpt(pair.a);
					callStack.pop();
				}
			}

		}
		System.err.println(".......");
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
