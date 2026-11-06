package br.servidor.masmorras;

/** Caixa de blocos (inclui as duas pontas). */
record Caixa(int x1, int y1, int z1, int x2, int y2, int z2) {

    static Caixa de(int ax, int ay, int az, int bx, int by, int bz) {
        return new Caixa(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
    }

    boolean contem(int x, int y, int z) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    boolean contem(double x, double y, double z) {
        return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
    }

    Caixa crescer(int n) {
        return new Caixa(x1 - n, y1 - n, z1 - n, x2 + n, y2 + n, z2 + n);
    }

    Caixa juntar(Caixa o) {
        return new Caixa(Math.min(x1, o.x1), Math.min(y1, o.y1), Math.min(z1, o.z1), Math.max(x2, o.x2), Math.max(y2, o.y2), Math.max(z2, o.z2));
    }

    boolean cruza(Caixa o) {
        return x1 <= o.x2 && x2 >= o.x1 && y1 <= o.y2 && y2 >= o.y1 && z1 <= o.z2 && z2 >= o.z1;
    }

    int cx() {
        return (x1 + x2) / 2;
    }

    int cz() {
        return (z1 + z2) / 2;
    }

    long volume() {
        return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
    }

    String texto() {
        return x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;
    }

    static Caixa ler(String s) {
        String[] p = s.split(",");
        return new Caixa(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim()), Integer.parseInt(p[5].trim()));
    }
}
