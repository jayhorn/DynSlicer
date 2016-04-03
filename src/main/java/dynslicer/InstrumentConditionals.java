/**
 * 
 */
package dynslicer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @author schaef
 *
 */
public class InstrumentConditionals {

	public static final String conditionalMethodName = "__CONDITION__METHOD";
	public static final String conditionalMethodArgName = "arg";

	public static final String pcMethodName = "__PC__METHOD";
	public static final String pcMethodArgName = "arg";

	
	public void transformAllClasses(File classDir, File outDir) {				
		for (Iterator<File> iter = FileUtils.iterateFiles(classDir, new String[] { "class" }, true); iter
				.hasNext();) {
			File classFile = iter.next();
			File transformedClass = new File(classFile.getAbsolutePath().replace(classDir.getAbsolutePath(), outDir.getAbsolutePath()));
			final String tClassName = transformedClass.getAbsolutePath();
			if (tClassName.contains(File.separator)) {
				File tClassDir = new File(tClassName.substring(0, tClassName.lastIndexOf(File.separator)));
				if (tClassDir.mkdirs()) {
					System.out.println("Wrote transformed classes to "+tClassDir.getAbsolutePath());
				}
			}
			instrumentClass(classFile.getAbsolutePath(), transformedClass.getAbsolutePath());
		}
		System.out.println("Done.");
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
			fos.write(cw.toByteArray());
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

	static class ClassRewriter extends ClassVisitor implements Opcodes {

		protected String className;
		
		public ClassRewriter(final ClassVisitor cv) {
			super(ASM5, cv);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {			
			className = name;
			cv.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {			
			MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
			return new MethodAdapter(mv, className);
		}

		@Override
		public void visitEnd() {
			/*
			 * create a method
			 * public static boolean conditionalMethodName(boolean arg) {
			 * return arg;
			 * }
			 * which is in bytecode:
			 * public boolean conditionalMethodName(boolean);
			 * descriptor: (Z)Z
			 * flags: ACC_PUBLIC, ACC_STATIC
			 * Code:
			 * stack=1, locals=1, args_size=1
			 * 0: iload_0
			 * 1: ireturn
			 * LineNumberTable:
			 * line 50: 0
			 * LocalVariableTable:
			 * Start Length Slot Name Signature
			 * 0 2 0 arg Z
			 */
			Label endLabel = new Label();
			MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, conditionalMethodName, "(Z)Z", null, null);
			// create method body
			mv.visitVarInsn(Opcodes.ILOAD, 0);
			mv.visitInsn(IRETURN);
			mv.visitMaxs(1, 1);
			mv.visitLabel(endLabel);
			mv.visitLocalVariable(conditionalMethodArgName, "Z", null, new Label(), endLabel, 0);
			mv.visitEnd();
			
			//create empty method to sample instruction counter.
			endLabel = new Label();
			mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, pcMethodName, "(I)V", null, null);
			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 1);
			mv.visitLabel(endLabel);
			mv.visitLocalVariable(pcMethodArgName, "I", null, new Label(), endLabel, 0);
			mv.visitEnd();
			super.visitEnd();
		}
	}

	static class MethodAdapter extends MethodVisitor implements Opcodes {

		private int instCounter = 0;
		
		protected final String className;
		
		public MethodAdapter(MethodVisitor mv, String className) {
			super(ASM5, mv);			
			this.className = className;
		}

		private void sampleInstCounter() {
			super.visitIntInsn(BIPUSH, instCounter);
			super.visitMethodInsn(INVOKESTATIC, className, pcMethodName, "(I)V", false);
			instCounter++;
		}
		
		@Override
		public void visitIntInsn(int opcode, int operand) {
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitIntInsn(opcode, operand);
		}

		@Override
		public void visitVarInsn(int opcode, int var) {
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitVarInsn(opcode, var);
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitTypeInsn(opcode, type);
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String desc) {
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitFieldInsn(opcode, owner, name, desc);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitMethodInsn(opcode, owner, name, desc, itf);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
		}

		@Override
		public void visitLdcInsn(Object cst) {			
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitLdcInsn(cst);
		}

		@Override
		public void visitIincInsn(int var, int increment) {
			// TODO Auto-generated method stub
			sampleInstCounter();
			super.visitIincInsn(var, increment);
		}

		@Override
		public void visitMultiANewArrayInsn(String desc, int dims) {
			// TODO Auto-generated method stub
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
//			Preconditions.checkNotNull(this.className);
//			if (opcode==Opcodes.GOTO) {
//				return; //Don't do goto's
//			}
//			final Label thenLabel = new Label();
//			final Label joinLabel = new Label();
//			// visit the old jump instruction
//			super.visitJumpInsn(opcode, thenLabel);
//			// else block (note that in bytecode the else comes first)
//			super.visitInsn(Opcodes.ICONST_1);
//			super.visitJumpInsn(Opcodes.GOTO, joinLabel);
//			super.visitLabel(thenLabel);
//			//then block
//			super.visitInsn(Opcodes.ICONST_0);
//			super.visitLabel(joinLabel);
//			super.visitMethodInsn(INVOKESTATIC, className, conditionalMethodName, "(Z)Z", false);
//			super.visitJumpInsn(Opcodes.IFEQ, label);
		}

		
		
	}

}
