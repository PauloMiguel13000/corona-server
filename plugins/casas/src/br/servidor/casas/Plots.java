package br.servidor.casas;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Le o plots.db do PlotMe (SQLite, so leitura): plot (X,Z) -> dono e cantos. */
final class Plots {

    record Plot(int px, int pz, String mundo, UUID dono, String nomeDono, int x1, int z1, int x2, int z2) {
        String chave() {
            return px + "_" + pz;
        }

        int cx() {
            return (x1 + x2) / 2;
        }

        int cz() {
            return (z1 + z2) / 2;
        }

        int largura() {
            return x2 - x1 + 1;
        }
    }

    private final File arquivo;
    private boolean driverOk = true;
    private String ultimoErro;

    Plots(File arquivo) {
        this.arquivo = arquivo;
    }

    String ultimoErro() {
        return ultimoErro;
    }

    /** Todos os plots do mundo com dono (pode rodar fora da thread principal). */
    List<Plot> ler(String mundo) {
        List<Plot> r = new ArrayList<>();
        if (!arquivo.exists()) {
            ultimoErro = "arquivo nao existe: " + arquivo;
            return r;
        }
        if (!driverOk) return r;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            driverOk = false;
            ultimoErro = "sem driver SQLite (org.sqlite.JDBC)";
            return r;
        }
        String url = "jdbc:sqlite:" + arquivo.getAbsolutePath().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT plotX, plotZ, world, ownerID, owner, bottomX, bottomZ, topX, topZ FROM plotmecore_plots")) {
            while (rs.next()) {
                if (!mundo.equalsIgnoreCase(rs.getString("world"))) continue;
                UUID dono;
                try {
                    dono = UUID.fromString(rs.getString("ownerID"));
                } catch (RuntimeException ex) {
                    continue;
                }
                int bx = rs.getInt("bottomX"), bz = rs.getInt("bottomZ"), tx = rs.getInt("topX"), tz = rs.getInt("topZ");
                r.add(new Plot(rs.getInt("plotX"), rs.getInt("plotZ"), rs.getString("world"), dono, rs.getString("owner"),
                        Math.min(bx, tx), Math.min(bz, tz), Math.max(bx, tx), Math.max(bz, tz)));
            }
            ultimoErro = null;
        } catch (Exception e) {
            ultimoErro = e.toString();
        }
        return r;
    }
}
