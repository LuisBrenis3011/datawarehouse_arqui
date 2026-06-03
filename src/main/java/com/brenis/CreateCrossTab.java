package com.brenis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * =========================================================================
 * CLASE 2 DE 3: CreateCrossTab
 * =========================================================================
 * Genera el CUBO analítico a partir del Data Warehouse.
 *
 * PROCESO:
 *   1. Lee `arquiproy_dw`.`TurnoCaja` agrupando por nombre_cajero
 *      y sumando monto_cierre → eso es el "total de ventas" del cajero.
 *   2. Crea (si no existe) la tabla `Cubo_Ventas` en `arquiproy_dw`.
 *   3. Vacía `Cubo_Ventas` y la repuebla con el resultado del GROUP BY.
 *      (TRUNCATE + INSERT garantiza que el cubo refleje el estado actual)
 *
 * Pre-requisito: haber ejecutado GenerarDataWarehouse primero.
 *
 * Dependencia necesaria en build.gradle:
 *   implementation 'com.mysql:mysql-connector-j:8.3.0'
 * =========================================================================
 */
public class CreateCrossTab {

    // ── Credenciales ──────────────────────────────────────────────────────────
    private static final String HOST = "localhost";
    private static final String PORT = "3306";
    private static final String USER = "root";
    private static final String PASS = "tu_contraseña";

    private static final String BD_DW = "arquiproy_dw";

    private static final String URL_DW =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + BD_DW
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";


    public static void main(String[] args) {

        System.out.println("=================================================");
        System.out.println("  CreateCrossTab — Generando Cubo_Ventas");
        System.out.println("  Fuente  : " + BD_DW + ".TurnoCaja");
        System.out.println("  Destino : " + BD_DW + ".Cubo_Ventas");
        System.out.println("=================================================");

        // PASO 1 — Crear la tabla Cubo_Ventas si no existe
        crearTablaCubo();

        // PASO 2 — Repoblar el cubo con el GROUP BY actualizado
        int filas = poblarCubo();

        System.out.println("-------------------------------------------------");
        System.out.println("  Cubo generado. Filas en Cubo_Ventas: " + filas);
        System.out.println("=================================================");
    }

    // =========================================================================
    // PASO 1: Crear tabla Cubo_Ventas
    // =========================================================================
    private static void crearTablaCubo() {
        /*
         * Cubo_Ventas es una tabla de agregación (resumen).
         * En términos de DW es una "tabla de hechos pre-agregada"
         * o, dicho de otro modo, el resultado de una operación ROLLUP.
         *
         * Columnas:
         *   nombre_cajero → dimensión (el "who" del análisis)
         *   total_ventas  → métrica (SUM de monto_cierre)
         */
        String sql = "CREATE TABLE IF NOT EXISTS `Cubo_Ventas` ("
                + "  nombre_cajero VARCHAR(100)   NOT NULL, "
                + "  total_ventas  DECIMAL(14,2)  NOT NULL DEFAULT 0.00, "
                + "  PRIMARY KEY (nombre_cajero)"   // un cajero, un total
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection con = DriverManager.getConnection(URL_DW, USER, PASS);
             Statement  st  = con.createStatement()) {

            st.executeUpdate(sql);
            System.out.println("[OK] Tabla 'Cubo_Ventas' verificada/creada.");

        } catch (SQLException e) {
            System.err.println("[ERROR] No se pudo crear Cubo_Ventas: " + e.getMessage());
            System.exit(1);
        }
    }

    // =========================================================================
    // PASO 2: Poblar Cubo_Ventas con el GROUP BY
    // =========================================================================
    private static int poblarCubo() {
        int filasCargadas = 0;

        /*
         * ESTRATEGIA: INSERT INTO ... SELECT (en una sola sentencia SQL).
         *
         * Primero vaciamos el cubo con TRUNCATE para garantizar que no haya
         * datos viejos. Luego hacemos un INSERT que directamente lee el
         * resultado del GROUP BY sin pasar por Java.
         *
         * Esto es más eficiente que:
         *   - Leer el GROUP BY en Java con un ResultSet
         *   - Hacer un INSERT por cada fila en Java
         *
         * La lógica de agregación:
         *   GROUP BY nombre_cajero → una fila por cajero
         *   SUM(monto_cierre)      → suma de todos sus cierres de caja
         *
         * Solo incluimos los turnos CERRADOS (estado = 0 = false):
         *   WHERE estado = 0
         *   Un turno abierto tiene monto_cierre = 0.0, contaminaría el total.
         */
        String sqlTruncate = "TRUNCATE TABLE `Cubo_Ventas`";

        String sqlInsertSelect =
                "INSERT INTO `Cubo_Ventas` (nombre_cajero, total_ventas) "
                        + "SELECT "
                        + "    nombre_cajero, "
                        + "    SUM(monto_cierre) AS total_ventas "
                        + "FROM `TurnoCaja` "
                        + "WHERE estado = 0 "          // solo turnos cerrados
                        + "GROUP BY nombre_cajero "
                        + "ORDER BY total_ventas DESC"; // mayor vendedor primero

        // Query auxiliar para contar cuántas filas quedaron
        String sqlCount = "SELECT COUNT(*) FROM `Cubo_Ventas`";

        try (Connection con = DriverManager.getConnection(URL_DW, USER, PASS);
             Statement  st  = con.createStatement()) {

            // Transacción manual: TRUNCATE + INSERT como unidad atómica
            con.setAutoCommit(false);

            // Vaciar el cubo
            st.executeUpdate(sqlTruncate);
            System.out.println("[OK] Cubo_Ventas vaciado (TRUNCATE).");

            // Insertar el resultado del GROUP BY directamente desde SQL
            int insertados = st.executeUpdate(sqlInsertSelect);
            System.out.println("[OK] INSERT INTO ... SELECT ejecutado. Filas insertadas: " + insertados);

            // Confirmar
            con.commit();
            System.out.println("[OK] Transacción confirmada (COMMIT).");

            // Mostrar el resultado en consola para verificación rápida
            System.out.println();
            System.out.println("  Resultado del Cubo_Ventas:");
            System.out.printf("  %-30s %15s%n", "CAJERO", "TOTAL VENTAS");
            System.out.println("  " + "-".repeat(47));

            try (ResultSet rs = st.executeQuery(
                    "SELECT nombre_cajero, total_ventas FROM Cubo_Ventas ORDER BY total_ventas DESC")) {

                while (rs.next()) {
                    filasCargadas++;
                    System.out.printf("  %-30s %,15.2f%n",
                            rs.getString("nombre_cajero"),
                            rs.getDouble("total_ventas"));
                }
            }

            System.out.println("  " + "-".repeat(47));

        } catch (SQLException e) {
            System.err.println("[ERROR] Fallo al poblar Cubo_Ventas: " + e.getMessage());
            System.err.println("        SQLState : " + e.getSQLState());
            System.err.println("        ErrorCode: " + e.getErrorCode());
            System.err.println("  ¿Ejecutaste GenerarDataWarehouse primero?");
        }

        return filasCargadas;
    }
}