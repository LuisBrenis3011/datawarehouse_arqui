package com.brenis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * =========================================================================
 * CLASE 1 DE 3: GenerarDataWarehouse
 * =========================================================================
 * Proceso ETL (Extract → Transform → Load).
 *
 * EXTRACT : Lee todos los registros de `arquiproy`.`TurnoCaja`
 * TRANSFORM: (sin transformación en este caso, carga 1:1)
 * LOAD    : Inserta los registros en `arquiproy_dw`.`TurnoCaja`
 *
 * Orden de ejecución recomendado:
 *   1. GenerarDataWarehouse  (este)
 *   2. CreateCrossTab
 *   3. ViewCrossTab
 *
 * Dependencia necesaria en build.gradle:
 *   implementation 'com.mysql:mysql-connector-j:8.3.0'
 * =========================================================================
 */
public class GenerarDataWarehouse {

    // ── Credenciales de conexión ──────────────────────────────────────────────
    private static final String HOST   = "localhost";
    private static final String PORT   = "3306";
    private static final String USER   = "root";
    private static final String PASS   = "tu_contraseña";

    // ── Base de datos ORIGEN (OLTP) ──────────────────────────────────────────
    private static final String BD_ORIGEN = "arquiproy";

    // ── Base de datos DESTINO (Data Warehouse) ───────────────────────────────
    private static final String BD_DW     = "arquiproy_dw";

    // ── URLs de conexión JDBC ────────────────────────────────────────────────
    // useSSL=false         → evita advertencias en MySQL 8 con SSL desactivado
    // allowPublicKeyRetrieval=true → necesario en MySQL 8 con autenticación RSA
    // serverTimezone=UTC   → evita error de zona horaria en MySQL 8
    private static final String URL_ORIGEN =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + BD_ORIGEN
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // Para crear la BD destino, primero conectamos SIN especificar base de datos
    private static final String URL_SIN_BD =
            "jdbc:mysql://" + HOST + ":" + PORT
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String URL_DW =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + BD_DW
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) {

        System.out.println("=================================================");
        System.out.println("  ETL — GenerarDataWarehouse");
        System.out.println("  Origen  : " + BD_ORIGEN + ".TurnoCaja");
        System.out.println("  Destino : " + BD_DW     + ".TurnoCaja");
        System.out.println("=================================================");

        // PASO 1 — Crear la base de datos DW si no existe
        crearBaseDeDatosDW();

        // PASO 2 — Crear la tabla destino si no existe
        crearTablaDW();

        // PASO 3 — Ejecutar el ETL (leer origen → insertar destino)
        int registrosCargados = ejecutarETL();

        System.out.println("-------------------------------------------------");
        System.out.println("  ETL completado. Registros cargados: " + registrosCargados);
        System.out.println("=================================================");
    }

    // =========================================================================
    // PASO 1: Crear base de datos arquiproy_dw si no existe
    // =========================================================================
    private static void crearBaseDeDatosDW() {
        // Conectamos al servidor MySQL SIN seleccionar ninguna BD,
        // porque la BD destino puede no existir todavía.
        String sql = "CREATE DATABASE IF NOT EXISTS `" + BD_DW + "` "
                + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";

        try (Connection con = DriverManager.getConnection(URL_SIN_BD, USER, PASS);
             Statement  st  = con.createStatement()) {

            st.executeUpdate(sql);
            System.out.println("[OK] Base de datos '" + BD_DW + "' verificada/creada.");

        } catch (SQLException e) {
            System.err.println("[ERROR] No se pudo crear la base de datos DW.");
            System.err.println("        " + e.getMessage());
            System.exit(1);
        }
    }

    // =========================================================================
    // PASO 2: Crear tabla TurnoCaja en arquiproy_dw (espejo de la OLTP)
    // =========================================================================
    private static void crearTablaDW() {
        /*
         * La estructura de la tabla DW replica exactamente la tabla OLTP.
         * En un DW real habría dimensiones y hechos separados, pero para
         * este proyecto académico hacemos una copia plana (staging table).
         *
         * CREATE TABLE IF NOT EXISTS → idempotente, se puede ejecutar N veces.
         * ON DUPLICATE KEY UPDATE    → lo usaremos en el INSERT para upsert.
         */
        String sql = "CREATE TABLE IF NOT EXISTS `" + BD_DW + "`.`TurnoCaja` ("
                + "  id             INT           NOT NULL, "
                + "  nombre_cajero  VARCHAR(100)  NOT NULL, "
                + "  monto_apertura DECIMAL(10,2) NOT NULL DEFAULT 0.00, "
                + "  monto_cierre   DECIMAL(10,2) NOT NULL DEFAULT 0.00, "
                + "  fecha          DATETIME        NOT NULL, " // timestamp en ms (igual que el .dat)
                + "  estado         TINYINT(1)    NOT NULL DEFAULT 1, "
                + "  PRIMARY KEY (id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection con = DriverManager.getConnection(URL_DW, USER, PASS);
             Statement  st  = con.createStatement()) {

            st.executeUpdate(sql);
            System.out.println("[OK] Tabla '" + BD_DW + ".TurnoCaja' verificada/creada.");

        } catch (SQLException e) {
            System.err.println("[ERROR] No se pudo crear la tabla TurnoCaja en el DW.");
            System.err.println("        " + e.getMessage());
            System.exit(1);
        }
    }

    // =========================================================================
    // PASO 3: ETL — Extract de OLTP, Load en DW
    // =========================================================================
    private static int ejecutarETL() {
        int contador = 0;

        /*
         * Query de extracción: leemos TODOS los registros de la OLTP.
         * En un DW real habría una columna de auditoría (ej: updated_at)
         * para hacer carga incremental. Aquí hacemos carga completa.
         */
        String sqlSelect = "SELECT id, nombre_cajero, monto_apertura, "
                + "monto_cierre, fecha, estado FROM TurnoCaja";

        /*
         * INSERT con ON DUPLICATE KEY UPDATE (UPSERT):
         * Si el id ya existe en el DW, actualiza los valores en lugar de fallar.
         * Esto hace la operación idempotente: se puede ejecutar N veces sin duplicar.
         *
         * VALUES (?,?,?,?,?,?) → PreparedStatement con parámetros posicionales:
         *   ?1 = id
         *   ?2 = nombre_cajero
         *   ?3 = monto_apertura
         *   ?4 = monto_cierre
         *   ?5 = fecha
         *   ?6 = estado
         */
        String sqlInsert =
                "INSERT INTO TurnoCaja (id, nombre_cajero, monto_apertura, monto_cierre, fecha, estado) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "  nombre_cajero  = VALUES(nombre_cajero), "
                        + "  monto_apertura = VALUES(monto_apertura), "
                        + "  monto_cierre   = VALUES(monto_cierre), "
                        + "  fecha          = VALUES(fecha), "
                        + "  estado         = VALUES(estado)";

        /*
         * Abrimos DOS conexiones en el mismo try-with-resources:
         * una al ORIGEN (OLTP) y otra al DESTINO (DW).
         * Ambas se cierran automáticamente al salir del bloque.
         */
        try (Connection conOrigen  = DriverManager.getConnection(URL_ORIGEN, USER, PASS);
             Connection conDW      = DriverManager.getConnection(URL_DW,     USER, PASS);
             Statement  stSelect   = conOrigen.createStatement();
             ResultSet  rs         = stSelect.executeQuery(sqlSelect);
             PreparedStatement pst = conDW.prepareStatement(sqlInsert)) {

            // Desactivar autocommit para hacer la carga en una sola transacción
            // (más eficiente: un solo COMMIT al final en lugar de uno por INSERT)
            conDW.setAutoCommit(false);

            System.out.println("[ETL] Leyendo registros de " + BD_ORIGEN + ".TurnoCaja...");

            while (rs.next()) {
                // Extraer columnas del ResultSet por nombre (más legible que por índice)
                int     id            = rs.getInt("id");
                String  nombreCajero  = rs.getString("nombre_cajero");
                double  montoApertura = rs.getDouble("monto_apertura");
                double  montoCierre   = rs.getDouble("monto_cierre");
                String fecha          = rs.getString("fecha");
                boolean estado        = rs.getBoolean("estado");

                // Cargar parámetros en el PreparedStatement
                pst.setInt    (1, id);
                pst.setString (2, nombreCajero);
                pst.setDouble (3, montoApertura);
                pst.setDouble (4, montoCierre);
                pst.setString(5, fecha);
                pst.setBoolean(6, estado);

                pst.addBatch(); // Acumular en lote (batch) para mejor rendimiento
                contador++;

                // Ejecutar en lotes de 50 para no saturar la memoria
                if (contador % 50 == 0) {
                    pst.executeBatch();
                    System.out.println("  [BATCH] " + contador + " registros procesados...");
                }
            }

            // Ejecutar el lote restante (los que no completaron un lote de 50)
            pst.executeBatch();

            // Confirmar la transacción: todos los INSERTs se persisten juntos
            conDW.commit();

            System.out.println("[OK] Transacción confirmada (COMMIT).");

        } catch (SQLException e) {
            System.err.println("[ERROR] Fallo durante el ETL: " + e.getMessage());
            System.err.println("        SQLState : " + e.getSQLState());
            System.err.println("        ErrorCode: " + e.getErrorCode());
        }

        return contador;
    }
}