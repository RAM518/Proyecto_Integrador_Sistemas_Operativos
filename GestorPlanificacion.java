import java.util.*;

public class GestorPlanificacion {
    String estrategiaPlanificacion;
    private int cuantoTiempo;
    public List<BloqueControlProceso> conjuntoProcesosActivos = new ArrayList<>();

    public GestorPlanificacion(String estrategia, int quantum) {
        this.estrategiaPlanificacion = estrategia;
        this.cuantoTiempo = quantum;
    }

    public void encolarProceso(BloqueControlProceso proceso) {
        conjuntoProcesosActivos.add(proceso);
    }

    public BloqueControlProceso seleccionarSiguienteProceso() {
        // Implementación básica para seleccionar el siguiente proceso
        if (!conjuntoProcesosActivos.isEmpty()) {
            return conjuntoProcesosActivos.get(0);
        }
        return null;
    }

    public void visualizarEstadoProcesos() {
        // Implementación para visualizar el estado de los procesos
    }

    public void visualizarColasPlanificacion() {
        // Implementación para visualizar las colas de planificación
    }

    public void refrescarColasDeProcesos() {
        // Implementación para refrescar las colas de procesos
    }

    public String getEstrategiaPlanificacion() {
        return estrategiaPlanificacion;
    }

    public int getCuantoTiempo() {
        return cuantoTiempo;
    }
}
