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
    }    public BloqueControlProceso seleccionarSiguienteProceso() {
        refrescarColasDeProcesos(); // Asegurar que los procesos estén ordenados según el algoritmo
        
        // Buscar el primer proceso en estado "Listo"
        for (BloqueControlProceso proceso : conjuntoProcesosActivos) {
            if (proceso.condicionActual.equals("Listo")) {
                return proceso;
            }
        }
        
        return null; // No hay procesos listos para ejecutar
    }    public void visualizarEstadoProcesos() {
        System.out.println("\n--- ESTADO ACTUAL DE PROCESOS ---");
        System.out.println("Algoritmo: " + estrategiaPlanificacion + (estrategiaPlanificacion.equals("RoundRobin") ? " (Quantum: " + cuantoTiempo + ")" : ""));
        
        if (conjuntoProcesosActivos.isEmpty()) {
            System.out.println("No hay procesos en el sistema.");
            return;
        }
        
        System.out.printf("%-5s %-12s %-10s %-12s %-12s %-12s %-12s %-15s%n", 
                         "PID", "Estado", "Prioridad", "T. Restante", "T. Total", "T. Llegada", "T. Final", "Razón Final");
        System.out.println("-----------------------------------------------------------------------------------------------------");
        
        for (BloqueControlProceso proceso : conjuntoProcesosActivos) {
            System.out.printf("%-5d %-12s %-10d %-12d %-12d %-12d %-12s %-15s%n", 
                             proceso.idUnicoProceso, 
                             proceso.condicionActual, 
                             proceso.valorPrioridad,
                             proceso.tiempoRestanteEjecucion,
                             proceso.tiempoTotalEjecucion,
                             proceso.marcaTiempoLlegada,
                             (proceso.condicionActual.equals("Terminado") ? proceso.marcaTiempoFinalizacion : "-"),
                             (proceso.condicionActual.equals("Terminado") ? proceso.razonFinalizacion : "-"));
        }
    }

    public void visualizarColasPlanificacion() {
        System.out.println("\n--- COLAS DE PLANIFICACIÓN ---");
        System.out.println("Algoritmo: " + estrategiaPlanificacion);
        
        // Contadores para cada estado
        Map<String, List<Integer>> procesosPorEstado = new HashMap<>();
        procesosPorEstado.put("Listo", new ArrayList<>());
        procesosPorEstado.put("Ejecutando", new ArrayList<>());
        procesosPorEstado.put("Bloqueado", new ArrayList<>());
        procesosPorEstado.put("Terminado", new ArrayList<>());
        procesosPorEstado.put("Suspendido Listo", new ArrayList<>());
        procesosPorEstado.put("Suspendido Bloqueado", new ArrayList<>());
        
        // Clasificar los procesos por estado
        for (BloqueControlProceso proceso : conjuntoProcesosActivos) {
            if (procesosPorEstado.containsKey(proceso.condicionActual)) {
                procesosPorEstado.get(proceso.condicionActual).add(proceso.idUnicoProceso);
            }
        }
        
        // Mostrar los procesos por estado
        for (Map.Entry<String, List<Integer>> entry : procesosPorEstado.entrySet()) {
            String estado = entry.getKey();
            List<Integer> pids = entry.getValue();
            
            System.out.print("Cola " + estado + ": ");
            if (pids.isEmpty()) {
                System.out.println("(vacía)");
            } else {
                System.out.println(pids.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse(""));
            }
        }
        
        // Si es Round Robin, mostrar información adicional sobre el quantum
        if (estrategiaPlanificacion.equals("RoundRobin")) {
            System.out.println("\nQuantum configurado: " + cuantoTiempo + " unidades de tiempo");
        }
    }public void refrescarColasDeProcesos() {
        List<BloqueControlProceso> procesosActualizados = new ArrayList<>();
        List<BloqueControlProceso> procesosListos = new ArrayList<>();
        
        // Filtrar los procesos por estado
        for (BloqueControlProceso proceso : conjuntoProcesosActivos) {
            if (proceso.condicionActual.equals("Listo")) {
                procesosListos.add(proceso);
            } else {
                procesosActualizados.add(proceso);
            }
        }
        
        // Ordenar según el algoritmo de planificación
        if (!procesosListos.isEmpty()) {
            switch (estrategiaPlanificacion) {
                case "RoundRobin":
                    // Round Robin no requiere ordenamiento específico, mantiene el orden FIFO
                    break;
                case "Prioridad":
                    // Ordenar por prioridad (menor valor = mayor prioridad)
                    Collections.sort(procesosListos, Comparator.comparingInt(p -> p.valorPrioridad));
                    break;
                case "SJF":
                    // Ordenar por tiempo restante de ejecución (menor tiempo primero)
                    Collections.sort(procesosListos, Comparator.comparingInt(p -> p.tiempoRestanteEjecucion));
                    break;
                case "FCFS":
                    // Ordenar por tiempo de llegada (mantener orden FIFO)
                    Collections.sort(procesosListos, Comparator.comparingInt(p -> p.marcaTiempoLlegada));
                    break;
                default:
                    // Si no se reconoce el algoritmo, mantener el orden actual
                    break;
            }
            
            // Añadir los procesos listos ordenados a la lista actualizada
            procesosActualizados.addAll(procesosListos);
        }
        
        // Actualizar la lista de procesos activos
        conjuntoProcesosActivos = procesosActualizados;
    }

    public String getEstrategiaPlanificacion() {
        return estrategiaPlanificacion;
    }

    public int getCuantoTiempo() {
        return cuantoTiempo;
    }
}
