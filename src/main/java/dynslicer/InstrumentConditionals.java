/**
 * 
 */
package dynslicer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.common.base.Preconditions;

/**
 * @author schaef
 *
 */
public class InstrumentConditionals {

	public static final String conditionalMethodName = "__CONDITION__METHOD";

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
			System.err.println("XX " + name);
			className = name;
			cv.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			System.err.println(name);			
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
			mv.visitLocalVariable("arg", "Z", null, new Label(), endLabel, 0);
			mv.visitEnd();
			super.visitEnd();
		}
	}

	static class MethodAdapter extends MethodVisitor implements Opcodes {

		protected final String className;
		
		public MethodAdapter(MethodVisitor mv, String className) {
			super(ASM5, mv);			
			this.className = className;
		}
		
		@Override
		public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void visitJumpInsn(int opcode, Label label) {			
			Preconditions.checkNotNull(this.className);
			if (opcode==Opcodes.GOTO) {
				return; //Don't do goto's
			}
//			Printer printer = new Textifier();
//			printer.visitJumpInsn(opcode, label);
//			StringWriter writer = new StringWriter();
//			printer.print(new PrintWriter(writer));
//			System.err.println(writer.toString());
			super.visitMethodInsn(INVOKESTATIC, className, conditionalMethodName, "(Z)Z", false);
			super.visitJumpInsn(Opcodes.IFEQ, label);
		}


		
	}

}
