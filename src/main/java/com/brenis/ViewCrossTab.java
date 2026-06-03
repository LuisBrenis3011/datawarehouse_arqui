package com.brenis;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * =========================================================================
 * CLASE 3 DE 3: ViewCrossTab
 * =========================================================================
 * Interfaz gráfica Swing que visualiza el cubo analítico `Cubo_Ventas`.
 *
 * TECNOLOGÍA: Java Swing puro (javax.swing).
 *   - Sin JavaFX, sin dependencias externas de UI.
 *   - Swing viene incluido en el JDK, no requiere ninguna dependencia Gradle.
 *
 * COMPONENTES PRINCIPALES:
 *   JFrame          → ventana principal
 *   JTable          → grilla de datos
 *   DefaultTableModel → modelo de datos de la JTable
 *   JScrollPane     → scroll si hay muchas filas
 *   JButton         → botón para recargar datos
 *   JLabel          → totalizador al pie
 *
 * Pre-requisito: haber ejecutado GenerarDataWarehouse y CreateCrossTab.
 *
 * Dependencia necesaria en build.gradle:
 *   implementation 'com.mysql:mysql-connector-j:8.3.0'
 *   (Swing NO requiere dependencia, está en el JDK)
 * =========================================================================
 */
public class ViewCrossTab extends JFrame {

    // ── Credenciales ──────────────────────────────────────────────────────────
    private static final String HOST  = "localhost";
    private static final String PORT  = "3306";
    private static final String USER  = "root";
    private static final String PASS  = "tu_contraseña";
    private static final String BD_DW = "arquiproy_dw";

    private static final String URL_DW =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + BD_DW
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // ── Componentes Swing (campos de instancia para acceso desde múltiples métodos)
    private final DefaultTableModel modeloTabla;
    private final JTable            tabla;
    private final JLabel            lblTotal;
    private final JLabel            lblEstado;

    // Formateador de moneda peruana para la columna total_ventas
    private static final NumberFormat FORMATO_MONEDA =
            NumberFormat.getCurrencyInstance(new Locale("es", "PE"));

    // =========================================================================
    // MAIN — punto de entrada
    // =========================================================================
    public static void main(String[] args) {
        /*
         * SwingUtilities.invokeLater() garantiza que la ventana se cree
         * en el Event Dispatch Thread (EDT) de Swing.
         * Nunca crear componentes Swing fuera del EDT.
         */
        SwingUtilities.invokeLater(() -> {
            ViewCrossTab ventana = new ViewCrossTab();
            ventana.setVisible(true);
        });
    }

    // =========================================================================
    // CONSTRUCTOR — construye y configura toda la UI
    // =========================================================================
    public ViewCrossTab() {
        // ── Configuración básica del JFrame ───────────────────────────────────
        super("Data Warehouse — Cubo de Ventas por Cajero");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(620, 480);
        setLocationRelativeTo(null); // centrar en pantalla
        setResizable(true);

        // ── Look and Feel del sistema operativo (más profesional que el default)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Si falla, Swing usa su look por defecto — no es crítico
        }

        // ── PANEL NORTE: Encabezado ───────────────────────────────────────────
        JPanel panelNorte = new JPanel(new BorderLayout(5, 5));
        panelNorte.setBackground(new Color(33, 64, 95));
        panelNorte.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel lblTitulo = new JLabel("Cubo de Ventas por Cajero");
        lblTitulo.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblTitulo.setForeground(Color.WHITE);

        JLabel lblSubtitulo = new JLabel(BD_DW + ".Cubo_Ventas");
        lblSubtitulo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblSubtitulo.setForeground(new Color(180, 200, 220));

        JPanel panelTextos = new JPanel(new GridLayout(2, 1));
        panelTextos.setOpaque(false);
        panelTextos.add(lblTitulo);
        panelTextos.add(lblSubtitulo);
        panelNorte.add(panelTextos, BorderLayout.CENTER);

        // ── MODELO DE LA TABLA ────────────────────────────────────────────────
        /*
         * DefaultTableModel con columnas fijas.
         * El segundo parámetro `true` en isCellEditable() → false
         * hace la tabla de solo lectura (override más abajo).
         */
        String[] columnas = {"#", "Nombre del Cajero", "Total Ventas (S/.)"};
        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Tabla de solo lectura
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                // Declarar el tipo de cada columna para ordenamiento correcto
                return switch (columnIndex) {
                    case 0 -> Integer.class;  // # (número de fila)
                    case 2 -> Double.class;   // total_ventas (para ordenar numéricamente)
                    default -> String.class;
                };
            }
        };

        // ── JTABLE ───────────────────────────────────────────────────────────
        tabla = new JTable(modeloTabla);
        tabla.setRowHeight(28);
        tabla.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tabla.setSelectionBackground(new Color(173, 214, 255));
        tabla.setGridColor(new Color(220, 220, 220));
        tabla.setShowVerticalLines(true);
        tabla.setAutoCreateRowSorter(true); // permite ordenar columnas con click

        // Ancho de columnas
        tabla.getColumnModel().getColumn(0).setPreferredWidth(35);  // #
        tabla.getColumnModel().getColumn(0).setMaxWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(300); // Cajero
        tabla.getColumnModel().getColumn(2).setPreferredWidth(180); // Total

        // Header de la tabla
        JTableHeader header = tabla.getTableHeader();
        header.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.setBackground(new Color(52, 100, 145));
        header.setForeground(Color.BLACK);
        header.setReorderingAllowed(false);

        // Alineación de columnas numéricas a la derecha
        DefaultTableCellRenderer renderDerecha = new DefaultTableCellRenderer();
        renderDerecha.setHorizontalAlignment(SwingConstants.RIGHT);
        tabla.getColumnModel().getColumn(0).setCellRenderer(renderDerecha);
        tabla.getColumnModel().getColumn(2).setCellRenderer(renderDerecha);

        // Alternancia de color de filas (zebra stripes)
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    setBackground(row % 2 == 0
                            ? Color.WHITE
                            : new Color(240, 245, 250));
                }
                // Alinear la columna de totales a la derecha
                setHorizontalAlignment(column == 2 || column == 0
                        ? SwingConstants.RIGHT
                        : SwingConstants.LEFT);
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        // ── PANEL SUR: Total general + botones ───────────────────────────────
        JPanel panelSur = new JPanel(new BorderLayout(10, 5));
        panelSur.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        panelSur.setBackground(new Color(245, 245, 245));

        // Etiqueta de total general
        lblTotal = new JLabel("Total general: S/. 0.00");
        lblTotal.setFont(new Font("SansSerif", Font.BOLD, 14));
        lblTotal.setForeground(new Color(33, 64, 95));

        // Etiqueta de estado de conexión
        lblEstado = new JLabel("Conectando...");
        lblEstado.setFont(new Font("SansSerif", Font.ITALIC, 11));
        lblEstado.setForeground(Color.GRAY);

        // Panel izquierdo del sur (total + estado)
        JPanel panelSurIzq = new JPanel(new GridLayout(2, 1));
        panelSurIzq.setOpaque(false);
        panelSurIzq.add(lblTotal);
        panelSurIzq.add(lblEstado);

        // Botón de recarga
        JButton btnRecargar = new JButton("↺  Recargar datos");
        btnRecargar.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnRecargar.setBackground(new Color(52, 100, 145));
        btnRecargar.setForeground(Color.BLACK);
        btnRecargar.setFocusPainted(false);
        btnRecargar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRecargar.setPreferredSize(new Dimension(160, 36));
        /*
         * ActionListener: se llama cargarDatos() al hacer click.
         * Lambda como alternativa a new ActionListener() { ... }
         */
        btnRecargar.addActionListener(e -> cargarDatos());

        panelSur.add(panelSurIzq,  BorderLayout.CENTER);
        panelSur.add(btnRecargar,  BorderLayout.EAST);

        // ── ENSAMBLADO FINAL DEL FRAME ────────────────────────────────────────
        setLayout(new BorderLayout(0, 5));
        add(panelNorte, BorderLayout.NORTH);
        add(scroll,     BorderLayout.CENTER);
        add(panelSur,   BorderLayout.SOUTH);

        // ── Cargar datos al abrir la ventana ──────────────────────────────────
        cargarDatos();
    }

    // =========================================================================
    // CARGA DE DATOS DESDE LA BD
    // =========================================================================
    /**
     * Consulta `arquiproy_dw`.`Cubo_Ventas` y llena el DefaultTableModel.
     *
     * Se puede llamar varias veces (botón Recargar) sin efectos secundarios:
     * primero limpia el modelo y luego lo repuebla.
     */
    private void cargarDatos() {
        // Limpiar la tabla antes de repoblar
        modeloTabla.setRowCount(0);
        lblEstado.setText("Consultando " + BD_DW + ".Cubo_Ventas...");
        lblTotal.setText("Total general: calculando...");

        String sql = "SELECT nombre_cajero, total_ventas "
                + "FROM Cubo_Ventas "
                + "ORDER BY total_ventas DESC";

        try (Connection con = DriverManager.getConnection(URL_DW, USER, PASS);
             Statement  st  = con.createStatement();
             ResultSet  rs  = st.executeQuery(sql)) {

            int    numeroFila   = 1;
            double totalGeneral = 0.0;

            while (rs.next()) {
                String nombre = rs.getString("nombre_cajero");
                double total  = rs.getDouble("total_ventas");
                totalGeneral += total;

                /*
                 * addRow() agrega una fila al DefaultTableModel.
                 * Los valores del array deben coincidir en orden con
                 * las columnas definidas en el constructor: #, Cajero, Total.
                 */
                modeloTabla.addRow(new Object[]{
                        numeroFila++,
                        nombre,
                        total          // Double — el renderer lo formatea
                });
            }

            // Actualizar etiquetas de resumen
            String textoTotal = "Total general: " + FORMATO_MONEDA.format(totalGeneral);
            lblTotal.setText(textoTotal);

            int filas = modeloTabla.getRowCount();
            lblEstado.setText(filas == 0
                    ? "Sin datos. ¿Ejecutaste CreateCrossTab?"
                    : filas + " cajero(s) encontrado(s). Conexión OK.");

        } catch (SQLException e) {
            lblEstado.setText("ERROR de conexión: " + e.getMessage());
            lblTotal.setText("Total general: —");

            /*
             * JOptionPane.showMessageDialog() muestra un diálogo de error nativo.
             * Es el equivalente Swing a Alert.AlertType.ERROR de JavaFX.
             */
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo conectar a '" + BD_DW + "'.\n\n"
                            + "Detalle: " + e.getMessage() + "\n\n"
                            + "Verifique:\n"
                            + "  1. Que MySQL esté corriendo en " + HOST + ":" + PORT + "\n"
                            + "  2. Que exista la base de datos '" + BD_DW + "'\n"
                            + "  3. Que exista la tabla 'Cubo_Ventas'\n"
                            + "  4. Haber ejecutado GenerarDataWarehouse y CreateCrossTab primero.",
                    "Error de conexión",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
