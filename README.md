# 📊 DataWarehouse Analytics - Capa OLAP

Este repositorio contiene la implementación de la capa analítica (Servidor de DataWarehouse) para el sistema de Punto de Venta. Se encarga de aislar el procesamiento gerencial y estadístico para no afectar el rendimiento del servidor transaccional (OLTP).

El sistema consta de tres componentes independientes escritos en Java (JDBC) que simulan un flujo completo de Business Intelligence.

## 🛠️ Requisitos Previos

* **Java JDK** (Recomendado versión 21 o superior).
* **MySQL Server** corriendo localmente en el puerto `3306`.
* Tener creada la base de datos transaccional `arquiproy` con la tabla `TurnoCaja`.
* Tener al menos un registro con `estado = 0` (turno cerrado) para que el cubo analítico tenga datos para sumar.

## 🚀 Instalación y Configuración

1. **Clonar el repositorio:**
```bash
   git clone [https://github.com/LuisBrenis3011/datawarehouse_arqui.git](https://github.com/LuisBrenis3011/datawarehouse_arqui.git)