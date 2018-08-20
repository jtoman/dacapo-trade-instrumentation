package edu.washington.cse.instrumentation.dacapo;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.SerialVersionUIDAdder;

public class PreMainBroken {
	private static enum InstrumentationMode {
		STATIC_INITIALIZER,
		PRIVATE_METHOD,
		PUBLIC_METHOD
	}
	public static void premain(final String args, final Instrumentation inst) throws URISyntaxException, IOException {
		boolean applyFix = false;
		InstrumentationMode im = InstrumentationMode.STATIC_INITIALIZER;
		if(args != null) {
			for(final String token : args.split(",")) {
				if(token.equalsIgnoreCase("fix")) {
					applyFix = true;
				} else if(token.equals("clinit")) {
					im = InstrumentationMode.STATIC_INITIALIZER;
				} else if(token.equals("pub-method")) {
					im = InstrumentationMode.PUBLIC_METHOD;
				} else if(token.equals("priv-method")) {
					im = InstrumentationMode.PRIVATE_METHOD;
				}
			}
		}
		final boolean applyFixCapture = applyFix;
		final InstrumentationMode imCapture = im;
		inst.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, 
					final ProtectionDomain protectionDomain, final byte[] classfileBuffer)
					throws IllegalClassFormatException {
				/*
				 * Completely avoid instrumenting the JDK, it's messy and not necessary for what we're doing
				 */
				if(className.startsWith("java/") || className.startsWith("sun/")) {
					return null;
				}
				final ClassReader crPre = new ClassReader(classfileBuffer);
				if((crPre.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
					return null;
				}
				final ClassReader cr;
				if(applyFixCapture) {
					/*
					 * To completely fix this problem, precompute the serial UID based on old class members,
					 * sidestepping the default serial UID computation which gets thrown off by new public members. 
					 */
					final ClassWriter cw = new ClassWriter(crPre, 0);
					final SerialVersionUIDAdder adder = new SerialVersionUIDAdder(cw);
					crPre.accept(adder, 0);
					cr = new ClassReader(cw.toByteArray());
				} else {
					cr = crPre;
				}
				final ClassWriter cw = new ClassWriter(0);
				final ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
					private boolean foundStaticInit = false;
					@Override
					public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
						if(name.equals("<clinit>")) {
							foundStaticInit = true;
						}
						return super.visitMethod(access, name, desc, signature, exceptions);
					}
					
					@Override
					public void visitEnd() {
						if(!foundStaticInit && imCapture == InstrumentationMode.STATIC_INITIALIZER) {
							final MethodVisitor mv = super.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "<clinit>", "()V", null, null);
							generateEmptyMethod(mv, false);
						} else if(imCapture == InstrumentationMode.PRIVATE_METHOD) {
							final MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE, "dummy$$method", "()V", null, null);
							generateEmptyMethod(mv, true);
						} else if(imCapture == InstrumentationMode.PUBLIC_METHOD) {
							assert imCapture == InstrumentationMode.PUBLIC_METHOD;
							final MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "dummy$$method", "()V", null, null);
							generateEmptyMethod(mv, true);
						}
						super.visitEnd();
					}

					public void generateEmptyMethod(final MethodVisitor mv, final boolean isInstance) {
						mv.visitCode();
						mv.visitInsn(Opcodes.RETURN);
						mv.visitMaxs(0, isInstance ? 1 : 0);
						mv.visitEnd();
					}
				};
				cr.accept(cv, 0);
				return cw.toByteArray();
			}
		}, false);
	}
}
