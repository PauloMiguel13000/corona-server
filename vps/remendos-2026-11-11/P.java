import org.objectweb.asm.*;
import java.nio.file.*;
public class P { public static void main(String[] a) throws Exception {
  Path p = Paths.get(a[0]); ClassReader r = new ClassReader(Files.readAllBytes(p)); ClassWriter w = new ClassWriter(0);
  final int[] n = {0};
  r.accept(new ClassVisitor(Opcodes.ASM9, w) {
    public MethodVisitor visitMethod(int acc, String name, String d, String s, String[] e) {
      if (name.equals("feito") && d.equals("()Z")) { n[0]++;
        MethodVisitor m = super.visitMethod(acc, name, d, s, e); String C = "br/servidor/extras/Vazio"; Label l = new Label();
        m.visitCode(); m.visitVarInsn(Opcodes.ALOAD,0); m.visitFieldInsn(Opcodes.GETFIELD,C,"feitoVisto","Z"); m.visitJumpInsn(Opcodes.IFEQ,l);
        m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.IRETURN); m.visitLabel(l); m.visitFrame(Opcodes.F_SAME,0,null,0,null);
        m.visitVarInsn(Opcodes.ALOAD,0); m.visitVarInsn(Opcodes.ALOAD,0); m.visitFieldInsn(Opcodes.GETFIELD,C,"feito","Ljava/io/File;");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/io/File","exists","()Z",false); m.visitInsn(Opcodes.DUP_X1);
        m.visitFieldInsn(Opcodes.PUTFIELD,C,"feitoVisto","Z"); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(3,1); m.visitEnd(); return null; }
      return super.visitMethod(acc, name, d, s, e); }
    public void visitEnd() { super.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_VOLATILE,"feitoVisto","Z",null,null).visitEnd(); super.visitEnd(); }
  }, 0);
  if (n[0]!=1) throw new RuntimeException("feito nao achado"); Files.write(p, w.toByteArray()); } }
