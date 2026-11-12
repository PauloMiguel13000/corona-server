import org.objectweb.asm.*;
import java.nio.file.*;
public class P4 { public static void main(String[] a) throws Exception {
  int[] n = {0};
  for (int k = 0; k < 2; k++) {
    final boolean fig = k == 0;
    Path p = Paths.get(a[k]); ClassReader r = new ClassReader(Files.readAllBytes(p)); ClassWriter w = new ClassWriter(r, ClassWriter.COMPUTE_MAXS);
    r.accept(new ClassVisitor(Opcodes.ASM9, w) {
      public MethodVisitor visitMethod(int acc, String name, String d, String s, String[] e) {
        MethodVisitor mv = super.visitMethod(acc, name, d, s, e);
        if (fig && name.equals("ouvir")) return new MethodVisitor(Opcodes.ASM9, mv) { public void visitCode() { super.visitCode(); n[0]++;
          super.visitVarInsn(Opcodes.ALOAD,0); super.visitVarInsn(Opcodes.ALOAD,1); super.visitVarInsn(Opcodes.ALOAD,2);
          super.visitMethodInsn(Opcodes.INVOKESTATIC,"br/servidor/hotel/Coracao","escondido","(Lbr/servidor/hotel/Partida$Figura;Lorg/bukkit/entity/Player;Lbr/servidor/hotel/Partida$Jogador;)V",false); } };
        if (!fig && name.equals("biblioteca")) return new MethodVisitor(Opcodes.ASM9, mv) { public void visitCode() { super.visitCode(); n[0]++;
          super.visitVarInsn(Opcodes.ALOAD,0); super.visitVarInsn(Opcodes.ALOAD,1); super.visitVarInsn(Opcodes.ALOAD,2);
          super.visitMethodInsn(Opcodes.INVOKESTATIC,"br/servidor/hotel/Coracao","armarios","(Lbr/servidor/hotel/Planta;Lbr/servidor/hotel/Planta$Comodo;Ljava/util/Map;)V",false); } };
        return mv; } }, 0);
    Files.write(p, w.toByteArray()); }
  if (n[0]!=2) throw new RuntimeException("pontos: " + n[0]); } }
