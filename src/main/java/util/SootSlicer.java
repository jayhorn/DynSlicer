package util;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.google.common.base.Verify;

import daikon.PptTopLevel;
import daikon.ProglangType;
import daikon.ValueTuple;
import daikon.VarInfo;
import daikon.VarInfo.VarKind;
import daikon.util.Pair;
import soot.Body;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.Modifier;
import soot.PatchingChain;
import soot.PrimType;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.VoidType;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.DefinitionStmt;
import soot.jimple.DoubleConstant;
import soot.jimple.FieldRef;
import soot.jimple.FloatConstant;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LongConstant;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.SwitchStmt;
import soot.jimple.ThisRef;
import soot.jimple.ThrowStmt;
import soot.options.Options;
import soot.tagkit.SourceFileTag;
import soot.tagkit.Tag;
import soot.toolkits.scalar.UnusedLocalEliminator;
import util.DaikonRunner.DaikonTrace;

public class SootSlicer {
	public static final String pcMethodNameSuffix = "__PC__METHOD";
	public static final String pcMethodArgName = "arg";
	public static final String wrapperMethodNameSuffix = "__WRAPPER__METHOD";
	public static final String instanceWrapperSuffix = "__HASBASE__";
	public static final String assertionMethodName = "my_Assert";
	
	private Map<soot.Type, SootMethod> assertMethods = new HashMap<soot.Type, SootMethod>();
	
	public static void main(String[] args) {
		// For testing only!
		SootSlicer sc = new SootSlicer();
		DaikonRunner dr = new DaikonRunner();
		sc.computeErrorSlices(new File(args[0]), args[1], dr.parseDTraceFile("ErrorTestDriver.dtrace"));
	}

	/**
	 * Returns a soot class that contains one method per trace. Each method
	 * contains the sequence of statements executed on that trace.
	 * 
	 * @param classDir
	 * @param classPath
	 * @param traces
	 * @return
	 */	
	public SootClass computeErrorSlices(File classDir, String classPath, Collection<DaikonTrace> traces) {

		System.out.println("Computing slices for input: ");
		System.out.println("ClassDir: " + classDir.getAbsolutePath());
		System.out.println("ClassPath: " + classPath);

		loadSootScene(classDir, classPath);

		SootClass myClass = new SootClass("HelloWorld", Modifier.PUBLIC);
		SootClass objClass = Scene.v().getSootClass("java.lang.Object");
		myClass.setSuperclass(objClass);
		Scene.v().addClass(myClass);

		assertMethods.put(RefType.v(), makeAssertMethod(myClass, RefType.v(objClass)));
		assertMethods.put(IntType.v(), makeAssertMethod(myClass, IntType.v()));
		assertMethods.put(FloatType.v(), makeAssertMethod(myClass, FloatType.v()));
		assertMethods.put(DoubleType.v(), makeAssertMethod(myClass, DoubleType.v()));
		assertMethods.put(LongType.v(), makeAssertMethod(myClass, LongType.v()));		
		
		for (DaikonTrace t : traces) {
			computeErrorSlice(t, myClass);
		}

		return myClass;
	}

	private SootMethod makeAssertMethod(SootClass myClass, soot.Type type) {		
		SootMethod sm = new SootMethod(assertionMethodName,  Arrays.asList(new Type[] {type, type}),
				VoidType.v(), Modifier.PUBLIC | Modifier.STATIC);
		myClass.addMethod(sm);
		JimpleBody body = Jimple.v().newBody(sm);
		sm.setActiveBody(body);
		Local l0 = Jimple.v().newLocal("l0", type);
		Local l1 = Jimple.v().newLocal("l1", type);
		body.getLocals().add(l0);
		body.getLocals().add(l1);
		body.getUnits().add(Jimple.v().newIdentityStmt(l0, Jimple.v().newParameterRef(type, 0)));
		body.getUnits().add(Jimple.v().newIdentityStmt(l1, Jimple.v().newParameterRef(type, 1)));
		// done with assert method.
		return sm;
	}
	
	private DaikonTrace currentTrace;

	/**
	 * For a given trace, create a method that contains the sequqnce of
	 * statements that are executed on that trace.
	 * 
	 * @param trace
	 * @param containingClass
	 */
	public void computeErrorSlice(final DaikonTrace trace, final SootClass containingClass) {
		this.currentTrace = trace;
		// System.err.println("****** All Events");
		// for (Pair<PptTopLevel, ValueTuple> ppt : trace.trace) {
		// System.err.println(ppt.a.name);
		// }
		// System.err.println("****** ");

		Iterator<Pair<PptTopLevel, ValueTuple>> iterator = trace.trace.iterator();
		SootMethod sm = createTraceMethod(iterator, containingClass);
		addFakeReturn(sm);
		UnusedLocalEliminator.v().transform(sm.getActiveBody());
		//
		// for (Unit u : sm.getActiveBody().getUnits()) {
		// System.err.println(" " + u);
		// }
		sm.getActiveBody().validate();
		// System.err.println(".......");
	}

	/**
	 * Add a fake return statement to the end of a method.
	 * 
	 * @param sm
	 */
	private void addFakeReturn(SootMethod sm) {
		if (sm.getReturnType() instanceof VoidType) {
			sm.getActiveBody().getUnits().add(Jimple.v().newReturnVoidStmt());
		} else if (sm.getReturnType() instanceof RefLikeType || sm.getReturnType() instanceof PrimType) {
			sm.getActiveBody().getUnits().add(Jimple.v().newReturnStmt(getDefaultValue(sm.getReturnType())));
		} else {
			throw new RuntimeException("Not implemented for " + sm.getReturnType());
		}
	}

	private Value getDefaultValue(soot.Type t) {
		if (t instanceof RefLikeType) {
			return NullConstant.v();
		} else if (t instanceof IntType) {
			return IntConstant.v(0);
		} else if (t instanceof LongType) {
			return LongConstant.v(0);
		} else if (t instanceof FloatType) {
			return FloatConstant.v(0);
		} else if (t instanceof DoubleType) {
			return DoubleConstant.v(0);
		}
		return IntConstant.v(0);
	}

	private Unit copySootStmt(Unit u, Map<Value, Value> substiutionMap) {
		Unit ret = (Unit) u.clone();
		for (ValueBox vb : ret.getUseAndDefBoxes()) {
			if (substiutionMap.containsKey(vb.getValue())) {
				vb.setValue(substiutionMap.get(vb.getValue()));
			}
		}
		ret.addAllTagsOf(u);
		for (Tag t : this.sm.getDeclaringClass().getTags()) {
			if (t instanceof SourceFileTag) {
				ret.addTag(t);
			}
		}
		return ret;
	}

	private SootMethod createNewMethod(final SootMethod orig, final SootClass containingClass) {
		final String method_prefix = "XY_";
		SootMethod newMethod = new SootMethod(method_prefix + orig.getName(), orig.getParameterTypes(),
				orig.getReturnType(), orig.getModifiers());
		containingClass.addMethod(newMethod);
		JimpleBody newBody = Jimple.v().newBody(newMethod);
		newMethod.setActiveBody(newBody);
		return newMethod;
	}

	private SootMethod newMethod;
	private SootMethod sm;
	private Body body;

	final Stack<String> pcMethodStack = new Stack<String>();

	private boolean haveToPushToPcStack = false;

	private SootMethod createTraceMethod(Iterator<Pair<PptTopLevel, ValueTuple>> iterator,
			final SootClass containingClass) {
		// final List<Unit> sootTrace = new LinkedList<Unit>();
		final Stack<SootMethod> methodStack = new Stack<SootMethod>();
		final Stack<Unit> callStack = new Stack<Unit>();

		pcMethodStack.clear();

		Pair<PptTopLevel, ValueTuple> ppt = iterator.next();
		Verify.verify(ppt.a.name.endsWith(":::ENTER"), "Ppt is not a procedure entry: " + ppt.a.name);

		final Map<Value, Value> substiutionMap = new HashMap<Value, Value>();

		sm = findMethodForPpt(ppt.a);
		// get the active body and start adding to it.
		newMethod = createNewMethod(sm, containingClass);
		final Body newBody = newMethod.getActiveBody();

		enterMethod(ppt, methodStack, callStack, newBody, substiutionMap);

		while (iterator.hasNext()) {
			ppt = iterator.next();

			boolean exceptionalJump = false;

			if (ppt.a.name.contains(pcMethodNameSuffix) && ppt.a.name.endsWith(":::ENTER")) {
				/**
				 * =============================================================
				 * ===
				 * This part handles exceptional back jumps
				 */
				if (haveToPushToPcStack) {
					pcMethodStack.push(ppt.a.name);
					haveToPushToPcStack = false;
				} else {
					if (!pcMethodStack.isEmpty() && !ppt.a.name.equals(pcMethodStack.peek())) {
						// then there was an exception and we have to pop stuff
						// from our stacks until we have the right method again.
						while (!pcMethodStack.isEmpty() && !ppt.a.name.equals(pcMethodStack.peek())) {
							if (!callStack.isEmpty()) {
								callStack.pop();
							}
							SootMethod tmp = methodStack.pop();
							System.out.println("Jumping over " + tmp.getName());
							pcMethodStack.pop();
						}
						if (!pcMethodStack.isEmpty()) {
							sm = methodStack.peek();
							body = sm.retrieveActiveBody();
							System.out.println("Exception to " + sm.getName());
							exceptionalJump = true;
						} else {
							return newMethod;
						}
					}
				}
				/**
				 * =============================================================
				 * ===
				 */

				VarInfo vi = ppt.a.find_var_by_name(pcMethodArgName);
				long arg = (Long) ppt.b.getValueOrNull(vi);

				// skip the exit of this method as well.
				ppt = iterator.next();

				List<Integer> skipList = new LinkedList<Integer>();
				Unit u = findUnitAtPos(body, arg, skipList);
				// Unit u= findUnitAtPos(body, arg, iterator);
//				System.err.println("  " + u + "\t" + sm.getName());

				if (exceptionalJump) {
					// get the caughtexceptionref
					Unit pre = u;
					while (pre != null) {
						if (pre instanceof DefinitionStmt
								&& ((DefinitionStmt) pre).getRightOp() instanceof CaughtExceptionRef) {
							break;
						}
						pre = sm.getActiveBody().getUnits().getPredOf(pre);
					}
					if (pre != null) {
						// check if the last element of the newbody is a throw.
						// if so, remove it and use its op for the assignment
						Unit last = newBody.getUnits().getLast();
						newBody.getUnits().removeLast();
						Unit newasn = null;
						if (last instanceof ThrowStmt) {
							newasn = Jimple.v().newAssignStmt(((DefinitionStmt) pre).getLeftOp(),
									((ThrowStmt) last).getOp());
						}
						newBody.getUnits().add(copySootStmt(newasn, substiutionMap));
					} else {
						// TODO
						System.err.println("No catch found for " + u + ". Guess we are done here.");
						return newMethod;
					}
				}

				if (u == null) {
					// TODO:
					continue;
				}
				for (ValueBox vb : u.getUseAndDefBoxes()) {
					if (vb.getValue() instanceof FieldRef) {
						if (!((FieldRef) vb.getValue()).getField().isPublic()) {
							if (((FieldRef) vb.getValue()).getField().isStatic()) {
								((FieldRef) vb.getValue()).getField().setModifiers(Modifier.PUBLIC | Modifier.STATIC);
							} else {
								((FieldRef) vb.getValue()).getField().setModifiers(Modifier.PUBLIC);
							}
						}
					}

				}
				if (u instanceof IfStmt || u instanceof SwitchStmt || u instanceof GotoStmt) {
					// ignore
				} else if (u instanceof ReturnVoidStmt) {
					// do nothing
					if (callStack.isEmpty()) {
						newBody.getUnits().add(copySootStmt(u, substiutionMap));
					} else {
						callStack.pop();
					}
				} else if (u instanceof ReturnStmt) {
					ReturnStmt rstmt = (ReturnStmt) u;
					if (callStack.isEmpty()) {
						newBody.getUnits().add(copySootStmt(u, substiutionMap));
					} else {
						Unit callee = callStack.pop();
						if (callee instanceof DefinitionStmt) {
							DefinitionStmt call = (DefinitionStmt) callee;
							newBody.getUnits().add(copySootStmt(
									Jimple.v().newAssignStmt(call.getLeftOp(), rstmt.getOp()), substiutionMap));
						} else {
							newBody.getUnits().add(copySootStmt(u, substiutionMap));
						}
					}
				} else if (((Stmt) u).containsInvokeExpr()) {
					InvokeExpr ivk = ((Stmt) u).getInvokeExpr();

					if (ivk.getMethod().getDeclaringClass().isLibraryClass()
							|| ivk.getMethod().getDeclaringClass().isJavaLibraryClass()) {
						// do not try to inline library calls.
						newBody.getUnits().add(copySootStmt(u, substiutionMap));
					} else {
						callStack.push(u);
					}
				} else {
					newBody.getUnits().add(copySootStmt(u, substiutionMap));
				}

				for (int i : skipList) {
					if (!iterator.hasNext()) {
						// Then the trace just threw an exception and we're
						// done.
						return newMethod;
					}
					ppt = iterator.next();
					Verify.verify(ppt.a.name.contains(pcMethodNameSuffix) && ppt.a.name.endsWith(":::ENTER"));
					vi = ppt.a.find_var_by_name(pcMethodArgName);
					arg = (Long) ppt.b.getValueOrNull(vi);
					Verify.verify(arg == (long) i, "Wrong number " + arg + "!=" + i);
					ppt = iterator.next();
					Verify.verify(ppt.a.name.contains(pcMethodNameSuffix) && ppt.a.name.contains(":::EXIT"));
				}
			} else if (ppt.a.name.contains(wrapperMethodNameSuffix) && ppt.a.name.endsWith(":::ENTER")) {
				Pair<PptTopLevel, ValueTuple> next = peekNextPpt(ppt);
				Unit call = callStack.pop();
				SootMethod callee = ((Stmt)call).getInvokeExpr().getMethod();
				if (next.a.name.contains(wrapperMethodNameSuffix) && next.a.name.contains(":::EXIT")) {
					Pair<PptTopLevel, ValueTuple> pre = ppt;
					ppt = iterator.next();
					Set<VarInfo> changedVars = findChangedVariables(pre, ppt);
					for (VarInfo vi : changedVars) {
						throw new RuntimeException("Not implementd ");// TODO side effects.
					}
					//update the return value.
					if (call instanceof DefinitionStmt) {
						VarInfo retVi = null;
						for (VarInfo vi : ppt.a.var_infos) {
							if (vi.var_kind == VarKind.RETURN) {
								retVi = vi;
								break;
							}
						}
						Verify.verifyNotNull(retVi);
						Object retVal = ppt.b.getValueOrNull(retVi);
						Value rhs = daikonValueToSootValue(retVi, retVal);
						Unit asn = Jimple.v().newAssignStmt(((DefinitionStmt) call).getLeftOp(), rhs);
						asn.addAllTagsOf(call);
						newBody.getUnits().add(copySootStmt(asn, substiutionMap));
					}					
				} else {
					int offset = 0;
					if (callee.getName().contains(instanceWrapperSuffix)) {
						offset = 1;
						Value v1 = ((Stmt)call).getInvokeExpr().getArg(0);
						Value v2 = NullConstant.v();
						Unit asrt = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(assertMethods.get(RefType.v()).makeRef(), v1,v2));
						asrt.addAllTagsOf(call);
						asrt = copySootStmt(asrt, substiutionMap);						
						newBody.getUnits().add(asrt);
					} else {
						newBody.getUnits().add(call);	
					}
					//TODO: assert that the current input is illegal.
					// throw new RuntimeException("throws an ex "+ppt.a.name);
					for (int i = offset; i<((Stmt)call).getInvokeExpr().getArgCount();i++) {
						Value v1 = ((Stmt)call).getInvokeExpr().getArg(i);
						VarInfo argVar = ppt.a.find_var_by_name("arg"+i);
						Object argVal = ppt.b.getValueOrNull(argVar);
						Value v2 = daikonValueToSootValue(argVar, argVal);
						Verify.verify(v1.getType().equals(v2.getType()));
						Unit asrt = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(assertMethods.get(v1.getType()).makeRef(), v1,v2));
						asrt.addAllTagsOf(call);
						asrt = copySootStmt(asrt, substiutionMap);						
						newBody.getUnits().add(asrt);
						System.err.println("adding assertion " + asrt);						
					}
					
				}
			} else if (ppt.a.name.endsWith(":::ENTER")) {
				enterMethod(ppt, methodStack, callStack, newBody, substiutionMap);
			} else if (ppt.a.name.contains(":::EXIT")) {
				methodStack.pop();
				sm = methodStack.peek();
				body = sm.retrieveActiveBody();
				pcMethodStack.pop();
			} else {
				System.err.println("Don't know how to handle " + ppt.a.name);
			}
		}

		return newMethod;
	}

	private Value daikonValueToSootValue(VarInfo vi, Object val) {
		if (val==null) {
			return NullConstant.v();
		} else if (vi.type == ProglangType.INT || vi.type == ProglangType.BOOLEAN || vi.type == ProglangType.CHAR) {
			return IntConstant.v( ((Long) val).intValue() );
		} else if (vi.type == ProglangType.LONG_PRIMITIVE || vi.type == ProglangType.LONG_OBJECT) {
			return LongConstant.v((Long) val);
		} else if (vi.type == ProglangType.STRING) {
			return StringConstant.v((String)val);			
		}
		throw new RuntimeException("not implemented for type " + vi.type);
	}

	private Pair<PptTopLevel, ValueTuple> peekNextPpt(Pair<PptTopLevel, ValueTuple> ppt) {
		return peekNextPpt(ppt, 0);
	}

	private Pair<PptTopLevel, ValueTuple> peekNextPpt(Pair<PptTopLevel, ValueTuple> ppt, int offset) {
		int currentPos = this.currentTrace.trace.indexOf(ppt);
		ListIterator<Pair<PptTopLevel, ValueTuple>> lit = this.currentTrace.trace.listIterator(currentPos + 1 + offset);
		if (lit.hasNext()) {
			return lit.next();
		}
		return null;
	}

	private Set<VarInfo> findChangedVariables(Pair<PptTopLevel, ValueTuple> pre, Pair<PptTopLevel, ValueTuple> post) {
		Set<VarInfo> changedVars = new HashSet<VarInfo>();
		for (VarInfo a : pre.a.var_infos) {
			for (VarInfo b : post.a.var_infos) {
				if (a.equals(b)) {
					Object v1 = pre.b.getValueOrNull(a);
					Object v2 = post.b.getValueOrNull(a);
					if (v1 == null && v2 == null) {
						// nothing changed; ignore
					} else if (v1 != null && v1.equals(v2)) {
						// nothing changed; ignore
					} else {
						// value changed. remember update.
						System.err.println("Var " + a.name() + " changed from " + v1 + " to " + v2);
						changedVars.add(b);
					}
				}
			}
		}
		return changedVars;
	}

//	private void findChangedVariables(Pair<PptTopLevel, ValueTuple> ppt, Unit u, int skipListSize) {
//		// TODO: +3 seems to be a correct offset, because there might be one
//		// enter and exit after
//		// the ppt ... however, magic constants are always bad so find a better
//		// way to do this.
//		int currentPos = this.currentTrace.trace.indexOf(ppt) + 3 + skipListSize;
//		if (currentPos == this.currentTrace.trace.size() - 1) {
//			throw new RuntimeException("Not implemented");
//		}
//		ListIterator<Pair<PptTopLevel, ValueTuple>> lit = this.currentTrace.trace.listIterator(currentPos);
//		if (lit.hasNext()) {
//			Pair<PptTopLevel, ValueTuple> next_ppt = lit.next();
//			final String next_label = next_ppt.a.name;
//			// check if the next program point is a statement
//			// in another procedure. In that case, this point threw an
//			// exception.
//
//			if (next_label.endsWith(":::ENTER") && !pcMethodStack.isEmpty()
//					&& !next_label.equals(pcMethodStack.peek())) {
//				// throw new RuntimeException("Lib function throw an exception:
//				// " + u);
//			} else {
//				findChangedVariables(ppt, next_ppt);
//			}
//			// TODO
//		} else {
//			throw new RuntimeException("Not implemented");
//		}
//	}

	private void enterMethod(Pair<PptTopLevel, ValueTuple> ppt, final Stack<SootMethod> methodStack,
			final Stack<Unit> callStack, Body newBody, final Map<Value, Value> substiutionMap) {
		sm = findMethodForPpt(ppt.a);

		body = sm.retrieveActiveBody();
		methodStack.push(sm);
		haveToPushToPcStack = true;
		// Add all the locals from sm to newMethod
		for (Local l : body.getLocals()) {
			if (!substiutionMap.containsKey(l)) {
				Local newLocal = Jimple.v().newLocal(sm.getName() + "_" + l.getName(), l.getType());
				substiutionMap.put(l, newLocal);
				newMethod.getActiveBody().getLocals().add(newLocal);
			}
		}

		// Add the IdentityStmts that assign Parameters to Locals
		// because those do not show up on the trace otherwise.
		for (Unit u : body.getUnits()) {
			if (u instanceof IdentityStmt && ((IdentityStmt) u).getRightOp() instanceof ParameterRef) {
				IdentityStmt idStmt = (IdentityStmt) u;
				ParameterRef pr = (ParameterRef) idStmt.getRightOp();
				if (!callStack.isEmpty()) {
					InvokeExpr ivk = ((Stmt) callStack.peek()).getInvokeExpr();
					// Now we have to add and AssignStmt instead of a
					// DefinitionStmt.
					Stmt s = Jimple.v().newAssignStmt(idStmt.getLeftOp(), ivk.getArg(pr.getIndex()));
					newBody.getUnits().add(copySootStmt(s, substiutionMap));
				} else {
					newBody.getUnits().add(copySootStmt(u, substiutionMap));
				}
			} else if (u instanceof IdentityStmt && ((IdentityStmt) u).getRightOp() instanceof ThisRef) {
				IdentityStmt idStmt = (IdentityStmt) u;
				if (!callStack.isEmpty() && ((Stmt) callStack.peek()).getInvokeExpr() instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr ivk = (InstanceInvokeExpr) ((Stmt) callStack.peek()).getInvokeExpr();
					Stmt s = Jimple.v().newAssignStmt(idStmt.getLeftOp(), ivk.getBase());
					newBody.getUnits().add(copySootStmt(s, substiutionMap));
				} else {
					newBody.getUnits().add(copySootStmt(u, substiutionMap));
				}
			}
		}

		// init all fields if its a constructor
		if (sm.isConstructor()) {
			for (SootField field : sm.getDeclaringClass().getFields()) {
				if (!field.isStatic()) {
					Value rhs = getDefaultValue(field.getType());
					Value lhs = Jimple.v().newInstanceFieldRef(body.getThisLocal(), field.makeRef());
					Unit init = Jimple.v().newAssignStmt(lhs, rhs);
					newBody.getUnits().add(copySootStmt(init, substiutionMap));
				}
			}
		} else if (sm.isStaticInitializer()) {
			for (SootField field : sm.getDeclaringClass().getFields()) {
				if (field.isStatic()) {
					Value rhs = getDefaultValue(field.getType());
					Value lhs = Jimple.v().newStaticFieldRef(field.makeRef());
					Unit init = Jimple.v().newAssignStmt(lhs, rhs);
					newBody.getUnits().add(copySootStmt(init, substiutionMap));
				}
			}
		}

	}

	private Unit findUnitAtPos(Body body, long pos, List<Integer> outSkipList) {
		PatchingChain<Unit> units = body.getUnits();
		Unit ret = null;
		for (Unit u : units) {
			if (u instanceof InvokeStmt) {
				InvokeStmt ivk = (InvokeStmt) u;
				InvokeExpr ie = ivk.getInvokeExpr();
				List<Value> args = ie.getArgs();

				if (isPcMethod(ivk) && (((IntConstant) args.get(0)).value == (int) pos)) {
					Unit next = units.getSuccOf(u);
					while (isPcMethod(next) && next != null) {
						Value v = ((InvokeStmt) next).getInvokeExpr().getArg(0);
						outSkipList.add(((IntConstant) v).value);
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
			InvokeStmt ivk = (InvokeStmt) u;
			return ivk.getInvokeExpr().getMethod().getName().contains(pcMethodNameSuffix);
		}
		return false;
	}

	private SootMethod findMethodForPpt(PptTopLevel ppt) {
		final String qualifiedMethodName = ppt.name.substring(0, ppt.name.indexOf("("));
		final String className = qualifiedMethodName.substring(0, qualifiedMethodName.lastIndexOf('.'));
		String methodName = qualifiedMethodName.substring(qualifiedMethodName.lastIndexOf('.') + 1,
				qualifiedMethodName.length());

		if (!Scene.v().containsClass(className)) {
			throw new RuntimeException("Class not in scene: " + className);
		}
		SootClass sc = Scene.v().getSootClass(className);
		if (className.endsWith(methodName)) {
			// constructor call.
			methodName = "<init>";
		}

		final String paramSig = ppt.name.substring(ppt.name.indexOf("(") + 1, ppt.name.indexOf(":::") - 1).replace(" ",
				"");
		List<soot.Type> paramTypes = new LinkedList<soot.Type>();
		if (paramSig != null && paramSig.length() > 0) {
			for (String paramName : paramSig.split(",")) {
				soot.Type t = stringToType(paramName);
				paramTypes.add(t);
			}
		}
		SootMethod sm = sc.getMethod(methodName, paramTypes);
		return sm;
	}

	private soot.Type stringToType(String s) {
		soot.Type t;
		if (s.endsWith("[]")) {
			return stringToType(s.substring(0, s.length() - 2)).makeArrayType();
		}
		if ("int".equals(s)) {
			t = IntType.v();
		} else if ("float".equals(s)) {
			t = FloatType.v();
		} else if ("double".equals(s)) {
			t = DoubleType.v();
		} else if ("long".equals(s)) {
			t = LongType.v();
		} else if ("char".equals(s)) {
			t = CharType.v();
		} else {
			t = Scene.v().getRefType(s);
		}
		return t;
	}

	private void loadSootScene(File classDir, String classPath) {
		Options sootOpt = Options.v();
		sootOpt.set_keep_line_number(true);
		sootOpt.set_prepend_classpath(true); // -pp
		sootOpt.set_output_format(Options.output_format_class);
		// sootOpt.set_java_version(Options.java_version_1_7);
		sootOpt.set_soot_classpath(classPath);
		sootOpt.set_src_prec(Options.src_prec_class);
		// sootOpt.set_asm_backend(true);
		List<String> processDirs = new LinkedList<String>();
		processDirs.add(classDir.getAbsolutePath());
		sootOpt.set_process_dir(processDirs);
		sootOpt.set_keep_offset(true);
		sootOpt.set_print_tags_in_output(true);
		sootOpt.set_validate(true);

		sootOpt.setPhaseOption("jb.a", "enabled:false");
		sootOpt.setPhaseOption("jop.cpf", "enabled:false");
		sootOpt.setPhaseOption("jop.cfg", "enabled:true");
		sootOpt.setPhaseOption("jb", "use-original-names:true");

		Scene.v().loadClassAndSupport("java.lang.System");
		Scene.v().loadClassAndSupport("java.lang.Thread");
		Scene.v().loadClassAndSupport("java.lang.ThreadGroup");

		Scene.v().loadBasicClasses();
		Scene.v().loadNecessaryClasses();

		for (SootClass sc : Scene.v().getClasses()) {
			if (sc.resolvingLevel() < SootClass.SIGNATURES) {
				sc.setResolvingLevel(SootClass.SIGNATURES);
			}
		}

	}
}
