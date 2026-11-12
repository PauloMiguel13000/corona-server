import org.objectweb.asm.*;
import java.nio.file.*;
public class P3 { public static void main(String[] a) throws Exception {
  Path p = Paths.get(a[0]); ClassReader r = new ClassReader(Files.readAllBytes(p)); ClassWriter w = new ClassWriter(r, 0);
  final int[] n = {0, 0};
  r.accept(new ClassVisitor(Opcodes.ASM9, w) {
    public MethodVisitor visitMethod(int acc, String name, String d, String s, String[] e) {
      MethodVisitor mv = super.visitMethod(acc, name, d, s, e);
      if (name.equals("iniciarBiblioteca")) return new MethodVisitor(Opcodes.ASM9, mv) {
        public void visitMethodInsn(int op, String owner, String nm, String ds, boolean itf) {
          if (op == Opcodes.INVOKEINTERFACE && owner.equals("org/bukkit/World") && nm.equals("dropItem") && ds.equals("(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;Ljava/util/function/Consumer;)Lorg/bukkit/entity/Item;")) {
            n[0]++; super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/servidor/hotel/LivroNaEstante", "soltar", "(Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;Ljava/util/function/Consumer;)Lorg/bukkit/entity/Item;", false);
          } else super.visitMethodInsn(op, owner, nm, ds, itf); } };
      if (name.equals("pegarLivro")) return new MethodVisitor(Opcodes.ASM9, mv) {
        public void visitLdcInsn(Object v) { if (v instanceof Double dd && Math.abs(dd - 2.56) < 1e-6) { n[1]++; super.visitLdcInsn(4.0d); } else super.visitLdcInsn(v); } };
      return mv; }
  }, 0);
  if (n[0]!=1 || n[1]!=1) throw new RuntimeException("pontos: " + n[0] + "," + n[1]); Files.write(p, w.toByteArray()); } }
