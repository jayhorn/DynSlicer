package bc_instrumenter;
/**
 * 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import com.google.common.base.Verify;

/**
 * @author schaef
 *
 */
public class Main {

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("Usage: classDir outDir");
			return;
		}
		File classDir = new File(args[0]);
		File outDir = new File(args[1]);

		Main m = new Main();
		m.transformAllClasses(classDir, outDir);
	}

	public static final String pcMethodNameSuffix = "__PC__METHOD";
	public static final String pcMethodArgName = "arg";

	public static final String wrapperMethodNameSuffix = "__WRAPPER__METHOD";

	public static final String instanceWrapperSuffix = "__HASBASE__";

	protected static Set<String> applicationClassNames;

	public void transformAllClasses(File classDir, File outDir) {
		applicationClassNames = new LinkedHashSet<String>();
		boolean failed = false;
		// Load all classes in the classDir and remember their name.
		try (URLClassLoader cl = new URLClassLoader(new URL[] { classDir.toURI().toURL() });) {
			for (Iterator<File> iter = FileUtils.iterateFiles(classDir, new String[] { "class" }, true); iter
					.hasNext();) {
				File classFile = iter.next();
				try (FileInputStream is = new FileInputStream(classFile);) {
					ClassReader cr = new ClassReader(is);
					applicationClassNames.add(cr.getClassName());
					final String className = cr.getClassName().replace('/', '.');
					cl.loadClass(className);
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}

			for (Iterator<File> iter = FileUtils.iterateFiles(classDir, new String[] { "class" }, true); iter
					.hasNext();) {
				File classFile = iter.next();
				File transformedClass = new File(
						classFile.getAbsolutePath().replace(classDir.getAbsolutePath(), outDir.getAbsolutePath()));
				final String tClassName = transformedClass.getAbsolutePath();
				if (tClassName.contains(File.separator)) {
					File tClassDir = new File(tClassName.substring(0, tClassName.lastIndexOf(File.separator)));
					if (tClassDir.mkdirs()) {
						System.out.println("Writing transformed classes to " + tClassDir.getAbsolutePath());
					}
				}
				try {
					instrumentClass(classFile.getAbsolutePath(), transformedClass.getAbsolutePath());
					System.out.println("Transformed " + classFile);
				} catch (Exception e) {
					System.err
							.println("Failed to transform " + classFile.getAbsolutePath() + " :\n\t" + e.getMessage());
					e.printStackTrace(System.err);
					failed = true;
				}
			}
			if (failed) {
				// throw new RuntimeException("FAILED");
			}
			System.out.println("Done.");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e.getMessage());
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}

	}

	/**
	 * Reads class file 'inFile' and looks for any conditional
	 * if (e) ...
	 * and replaces it by
	 * e = conditionalMethodName(e);
	 * if (e) ...
	 * This way, Daikon.Chicory will sample the value of e during a run
	 * which allows us to reconstruct the execution trace.
	 * The instrumented class file is written to 'outFile'.
	 * 
	 * @param inFile
	 * @param outFile
	 */
	public void instrumentClass(final String inFile, final String outFile) {
		try (FileInputStream is = new FileInputStream(inFile); FileOutputStream fos = new FileOutputStream(outFile);) {
			ClassReader cr = new ClassReader(is);
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			cr.accept(new ClassRewriter(cw), 0);

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			CheckClassAdapter.verify(new ClassReader(cw.toByteArray()), false, pw);
			Verify.verify(sw.toString().length() == 0, sw.toString());
			fos.write(cw.toByteArray());
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

	static class ClassRewriter extends ClassVisitor implements Opcodes {

		protected String className, superName;

		public final String getSuperName() {
			return this.superName;
		}

		public ClassRewriter(final ClassVisitor cv) {
			super(ASM5, cv);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
			className = name;
			this.superName = superName;
			cv.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
			final String pcMethodName = createProgramCounterMethod(name, desc);
			return new MethodAdapter(mv, className, name, pcMethodName, this);
		}

		// @Override
		// public void visitEnd() {
		//
		// //create empty method to sample instruction counter.
		// Label endLabel = new Label();
		// MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC,
		// pcMethodName, "(I)V", null, null);
		// mv.visitInsn(RETURN);
		// mv.visitMaxs(0, 1);
		// mv.visitLabel(endLabel);
		// mv.visitLocalVariable(pcMethodArgName, "I", null, new Label(),
		// endLabel, 0);
		// mv.visitEnd();
		// super.visitEnd();
		// }

		private String createProgramCounterMethod(String name, String desc) {
			final String methodName = composePcMethodName(name, desc);
			Label endLabel = new Label();
			MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, methodName, "(I)V", null, null);
			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 1);
			mv.visitLabel(endLabel);
			mv.visitLocalVariable(pcMethodArgName, "I", null, new Label(), endLabel, 0);
			mv.visitEnd();
			return methodName;
		}

		Map<String, String> foobar = new LinkedHashMap<String, String>();

		private List<String> splitDescription(String desc) {
			List<String> ret = new LinkedList<String>();
			String s = "";
			for (int i = 0; i < desc.length(); i++) {
				char c = desc.charAt(i);
				if (Character.isLetter(c) && c != 'L') {
					// fall through.
				} else if (c == 'L') {
					while (c != ';') { // collect class name
						s += c;
						i++;
						c = desc.charAt(i);
					}
				} else if (c == '[') {
					s += c; // do not add to ret yet.
					continue;
				} else {
					System.err.println("Desc: " + desc);
					throw new RuntimeException(
							"Don't know type " + c + " between " + desc.substring(0, i) + " and " + desc.substring(i));
				}
				ret.add(s + c);
				s = "";
			}
			return ret;
		}

		public String lookupWrapperMethod(int opcode, String owner, String name, String desc, boolean itf,
				String wrapperDesc) {
			Verify.verify(!applicationClassNames.contains(owner), "Application method must not be wrapped!");
			final String signature = owner + "." + name + desc;

			if (!foobar.containsKey(signature)) {
				int accessModifier = ACC_PRIVATE | ACC_STATIC;
				String firstPart = owner.replace("/", "_") + name.replace("<", "_").replace(">", "_");
				if (!desc.equals(wrapperDesc)) {
					firstPart += instanceWrapperSuffix;
				}
				final String wrapperName = firstPart + wrapperMethodNameSuffix;
				MethodVisitor mv = cv.visitMethod(accessModifier, wrapperName, wrapperDesc, null, null);
				// Load all parameters

				final String retString = wrapperDesc.substring(wrapperDesc.lastIndexOf(')') + 1);

//				if (opcode == Opcodes.INVOKESPECIAL) {
//					// if this is a constructor, add the new statement as well
//					// to keep the bytecode verifier happy.
//					Verify.verify(retString.startsWith("L") && retString.endsWith(";"), "Can't handle " + retString);
//					final String tname = retString.substring(1, retString.length() - 1);
//					System.out.println("Hello " + tname);
//					mv.visitTypeInsn(Opcodes.NEW, tname);
//					mv.visitInsn(Opcodes.DUP);
//				}

				List<String> argStrings = splitDescription(wrapperDesc.substring(1, wrapperDesc.lastIndexOf(')')));
				for (int i = 0; i < argStrings.size(); i++) {
					String s = argStrings.get(i);
					if ("B".equals(s)) { // signed byte
						mv.visitVarInsn(Opcodes.ILOAD, i);
					} else if ("C".equals(s)) { // char
						mv.visitVarInsn(Opcodes.ILOAD, i);
					} else if ("D".equals(s)) { // double
						mv.visitVarInsn(Opcodes.DLOAD, i);
					} else if ("F".equals(s)) { // float
						mv.visitVarInsn(Opcodes.FLOAD, i);
					} else if ("I".equals(s)) { // int
						mv.visitVarInsn(Opcodes.ILOAD, i);
					} else if ("J".equals(s)) { // long
						mv.visitVarInsn(Opcodes.LLOAD, i);
					} else if (s.startsWith("L")) { // class
						mv.visitVarInsn(Opcodes.ALOAD, i);
					} else if ("S".equals(s)) { // short
						mv.visitVarInsn(Opcodes.ILOAD, i);
					} else if ("Z".equals(s)) { // bool
						mv.visitVarInsn(Opcodes.ILOAD, i);
					} else if (s.startsWith("[")) { // array
						mv.visitVarInsn(Opcodes.ALOAD, i);
					} else {
						throw new RuntimeException("Unknown type signature " + s);
					}
				}

				// call the original method.
				mv.visitMethodInsn(opcode, owner, name, desc, itf);

				if ("B".equals(retString)) { // signed byte
					mv.visitInsn(Opcodes.IRETURN); // TODO is that true?
				} else if ("C".equals(retString)) { // char
					mv.visitInsn(Opcodes.IRETURN); // TODO is that true?
				} else if ("D".equals(retString)) { // double
					mv.visitInsn(Opcodes.DRETURN);
				} else if ("F".equals(retString)) { // float
					mv.visitInsn(Opcodes.FRETURN);
				} else if ("I".equals(retString)) { // int
					mv.visitInsn(Opcodes.IRETURN);
				} else if ("J".equals(retString)) { // long
					mv.visitInsn(Opcodes.LRETURN);
				} else if (retString.startsWith("L")) { // class
					mv.visitInsn(Opcodes.ARETURN);
				} else if ("S".equals(retString)) { // short
					mv.visitInsn(Opcodes.IRETURN); // TODO is that true?
				} else if ("Z".equals(retString)) { // bool
					mv.visitInsn(Opcodes.IRETURN); // TODO is that true?
				} else if ("V".equals(retString)) { // void
					mv.visitInsn(Opcodes.RETURN);
				} else if (retString.startsWith("[")) { // array
					mv.visitInsn(Opcodes.ARETURN);
				} else {
					throw new RuntimeException("Unknown type signature " + retString);
				}
				mv.visitMaxs(1, argStrings.size());
				mv.visitEnd();
				foobar.put(signature, wrapperName);
			}
			return foobar.get(signature);
		}
	}

	public static String composePcMethodName(String name, String desc) {
		String cleanDesc = desc.replace("(", "_LP_").replace(")", "_RP_");
		cleanDesc = cleanDesc.replace(";", "_sc_");
		cleanDesc = cleanDesc.replace("[", "_lb_");
		cleanDesc = cleanDesc.replace("/", "_sl_");
		String cleanName = name.replace("<", "_la_");
		cleanName = cleanName.replace(">", "_ra_");
		return cleanName + "_SIG_" + cleanDesc + pcMethodNameSuffix;
	}

	static class MethodAdapter extends MethodVisitor implements Opcodes {

		private int instCounter = 0;

		protected final String className, pcMethodName, methodName;
		protected final ClassRewriter containClassVisitor;

		public MethodAdapter(MethodVisitor mv, String className, String methodName, String pcMethodName,
				ClassRewriter cv) {
			super(ASM5, mv);
			this.className = className;
			this.pcMethodName = pcMethodName;
			this.methodName = methodName;
			this.containClassVisitor = cv;
		}

		private void sampleInstCounter() {
			super.visitIntInsn(BIPUSH, instCounter);
			super.visitMethodInsn(INVOKESTATIC, className, pcMethodName, "(I)V", false);
			instCounter++;
		}

		@Override
		public void visitInsn(int opcode) {
			sampleInstCounter();
			super.visitInsn(opcode);
		}

		@Override
		public void visitIntInsn(int opcode, int operand) {
			sampleInstCounter();
			super.visitIntInsn(opcode, operand);
		}

		@Override
		public void visitVarInsn(int opcode, int var) {
			sampleInstCounter();
			super.visitVarInsn(opcode, var);
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			sampleInstCounter();
			super.visitTypeInsn(opcode, type);
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String desc) {
			sampleInstCounter();
			super.visitFieldInsn(opcode, owner, name, desc);
		}

		private boolean mustNotBeWrapped(String owner, String name) {
//			if (!this.methodName.equals("<init>")) {
//				System.err.println("Only wrapping in constructor " + owner + "." + name);
//				return false;
//			}
			return "<init>".equals(name);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
			sampleInstCounter();
			if (applicationClassNames.contains(owner)) {
				super.visitMethodInsn(opcode, owner, name, desc, itf);
				return;
			} else if (mustNotBeWrapped(owner, name)) {
				// don't wrap constructors because of this
				// "Type uninitialized 0 (current frame, stack[1]) is not
				// assignable to ..."
				// exception
				super.visitMethodInsn(opcode, owner, name, desc, itf);
				return;
			} else {

				String wrapperDesc = desc;
				if (opcode == Opcodes.INVOKESTATIC) {
					// leave desc as is
				} else {
					
					if (owner.endsWith(";") && (owner.startsWith("L") || owner.startsWith("["))) {
						//don't touch it.
						System.err.println("Skipping call to " + owner+"."+name+desc);
						super.visitMethodInsn(opcode, owner, name, desc, itf);
						return;
//					} else if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
						//DO NOTHING FOR NOW.
//						Verify.verify(wrapperDesc.endsWith(")V"));
//						StringBuilder sb = new StringBuilder();
//						sb.append(wrapperDesc.substring(0, wrapperDesc.length()-1));
//						sb.append("L");
//						sb.append(owner);
//						sb.append(";");
//						wrapperDesc = sb.toString();
//						System.err.println("New wrapper desc: " + wrapperDesc);
					} else {
						StringBuilder sb = new StringBuilder();	
						sb.append("(L");
						sb.append(owner);
						sb.append(";");
						sb.append(desc.substring(1));
						wrapperDesc = sb.toString();						
					}
				}
				final String wrappedMethodName = this.containClassVisitor.lookupWrapperMethod(opcode, owner, name, desc,
						itf, wrapperDesc);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, className, wrappedMethodName, wrapperDesc, false);
				return;
			}
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
		// TODO Auto-generated method stub
		sampleInstCounter();
		super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
	}

	@Override
	public void visitLdcInsn(Object cst) {
		sampleInstCounter();
		super.visitLdcInsn(cst);
	}

	@Override
	public void visitIincInsn(int var, int increment) {
		sampleInstCounter();
		super.visitIincInsn(var, increment);
	}

	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		sampleInstCounter();
		super.visitMultiANewArrayInsn(desc, dims);
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		sampleInstCounter();
		super.visitTableSwitchInsn(min, max, dflt, labels);
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		sampleInstCounter();
		super.visitLookupSwitchInsn(dflt, keys, labels);
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		sampleInstCounter();
		super.visitJumpInsn(opcode, label);
	}

}

}
