import org.objectweb.asm.*;
import java.nio.file.*;
public class P2 { public static void main(String[] a) throws Exception {
  Path p = Paths.get(a[0]); ClassReader r = new ClassReader(Files.readAllBytes(p)); ClassWriter w = new ClassWriter(r, ClassWriter.COMPUTE_MAXS);
  final int[] n = {0};
  r.accept(new ClassVisitor(Opcodes.ASM9, w) {
    public MethodVisitor visitMethod(int acc, String name, String d, String s, String[] e) {
      MethodVisitor mv = super.visitMethod(acc, name, d, s, e);
      if (!name.equals("premiar")) return mv;
      return new MethodVisitor(Opcodes.ASM9, mv) {
        boolean viuReturn = false, feito = false;
        public void visitInsn(int op) { super.visitInsn(op); if (op == Opcodes.RETURN && !feito) viuReturn = true; }
        public void visitFrame(int t, int nl, Object[] l, int ns, Object[] st) { super.visitFrame(t, nl, l, ns, st);
          if (viuReturn && !feito) { feito = true; n[0]++;
            super.visitVarInsn(Opcodes.ALOAD, 3); super.visitVarInsn(Opcodes.ALOAD, 0);
            super.visitFieldInsn(Opcodes.GETFIELD, "br/servidor/extras/Votos", "pl", "Lbr/servidor/extras/Extras;");
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/servidor/extras/VotoClaim", "dar", "(Lorg/bukkit/entity/Player;Lorg/bukkit/plugin/java/JavaPlugin;)V", false); } }
      }; }
  }, 0);
  if (n[0]!=1) throw new RuntimeException("ponto de insercao: " + n[0]); Files.write(p, w.toByteArray()); } }
