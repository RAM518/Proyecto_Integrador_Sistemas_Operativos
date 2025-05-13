import java.util.*;
import java.util.concurrent.*;

class BloqueControlProceso {
    static int siguienteIdProceso = 0;
    int idUnicoProceso;
    String condicionActual;
    int valorPrioridad;
    int tiempoRestanteEjecucion;
    int tiempoTotalEjecucion;
    int marcaTiempoLlegada;
    int marcaTiempoFinalizacion;
    int acumuladoTiempoEspera;
    int acumuladoTiempoRetorno;
    List<String> conjuntoRecursosAsignados = new ArrayList<>();
    List<String> conjuntoRecursosEnEspera = new ArrayList<>();
    MotivoFinalizacion razonFinalizacion;
    Map<Integer, List<String>> buzonMensajes = new HashMap<>();

    // Constructor para inicializar un proceso con prioridad y tiempo de ejecución
    public BloqueControlProceso(int valorPrio, int tiempoEjec) {
        this.idUnicoProceso = ++siguienteIdProceso;
        this.condicionActual = "Listo";
        this.valorPrioridad = valorPrio;
        this.tiempoRestanteEjecucion = tiempoEjec;
        this.tiempoTotalEjecucion = tiempoEjec;
        this.marcaTiempoLlegada = Simulador_Procesos.contadorTiempoGlobal++;
    }

    // Método para enviar un mensaje a otro proceso
    public void mandarMensaje(int idDestinatario, String textoMensaje) {
        for (BloqueControlProceso proceso : Simulador_Procesos.gestorPlanificacionGlobal.conjuntoProcesosActivos) {
            if (proceso.idUnicoProceso == idDestinatario) {
                if (!proceso.buzonMensajes.containsKey(this.idUnicoProceso)) {
                    proceso.buzonMensajes.put(this.idUnicoProceso, new ArrayList<>());
                }
                proceso.buzonMensajes.get(this.idUnicoProceso).add(textoMensaje);
                BitacoraEventos.registrarEvento("COMUNICACIÓN", "PID " + this.idUnicoProceso + " → PID " + idDestinatario + ": mensaje enviado");
                return;
            }
        }
        BitacoraEventos.registrarEvento("ERROR", "No se envió mensaje: PID " + idDestinatario + " no encontrado");
    }

    // Método para leer los mensajes recibidos por el proceso
    public void revisarMensajes() {
        if (buzonMensajes.isEmpty()) {
            System.out.println("No hay mensajes para este proceso");
            return;
        }
        
        System.out.println("\n=== MENSAJES PARA PID " + idUnicoProceso + " ===");
        for (Map.Entry<Integer, List<String>> entry : buzonMensajes.entrySet()) {
            System.out.println("De PID " + entry.getKey() + ":");
            for (String msg : entry.getValue()) {
                System.out.println("- " + msg);
            }
        }
        buzonMensajes.clear();
    }
}

enum MotivoFinalizacion {
    EJECUCION_NORMAL("Ejecución completada"),
    ERROR_EN_EJECUCION("Error durante ejecución"),
    DETECCION_INTERBLOQUEO("Interbloqueo detectado"),
    TERMINADO_POR_OPERADOR("Terminado por usuario");
    
    private String detalle;
    
    MotivoFinalizacion(String detalle) {
        this.detalle = detalle;
    }
    
    @Override
    public String toString() {
        return detalle;
    }
}

class BitacoraEventos {
    // Método para registrar eventos en el sistema
    public static void registrarEvento(String categoria, String descripcionEvento) {
        System.out.println("[" + categoria + "] " + descripcionEvento);
    }
}

class AdministradorRecursos {
    int totalMemoriaLibre = 4096;    
    boolean indicadorCpuLibre = true;
    Map<Integer, List<String>> solicitudesRecursosPendientes = new HashMap<>();
    Map<Integer, Integer> asignacionMemoriaPorProceso = new HashMap<>();

    // Método para solicitar recursos para un proceso
    public synchronized boolean asignarRecursos(BloqueControlProceso procesoActual, int cantidadMemoria) {
        if (cantidadMemoria <= totalMemoriaLibre && indicadorCpuLibre) {
            totalMemoriaLibre -= cantidadMemoria;
            indicadorCpuLibre = false;
            procesoActual.conjuntoRecursosAsignados.add("CPU");
            procesoActual.conjuntoRecursosAsignados.add(cantidadMemoria + "MB RAM");
            asignacionMemoriaPorProceso.put(procesoActual.idUnicoProceso, cantidadMemoria);
            BitacoraEventos.registrarEvento("RECURSO", "PID " + procesoActual.idUnicoProceso + " obtuvo CPU y " + cantidadMemoria + "MB de RAM");
            solicitudesRecursosPendientes.remove(procesoActual.idUnicoProceso);
            procesoActual.conjuntoRecursosEnEspera.clear();
            informarEstadoRecursos("Asignados a PID " + procesoActual.idUnicoProceso);
            return true;
        } else {
            procesoActual.condicionActual = "Bloqueado";
            procesoActual.conjuntoRecursosEnEspera.clear();
            
            List<String> recursosNecesitados = new ArrayList<>();
            if (cantidadMemoria > totalMemoriaLibre) {
                recursosNecesitados.add(cantidadMemoria + "MB RAM");
                procesoActual.conjuntoRecursosEnEspera.add(cantidadMemoria + "MB RAM");
            }
            if (!indicadorCpuLibre) {
                recursosNecesitados.add("CPU");
                procesoActual.conjuntoRecursosEnEspera.add("CPU");
            }
            solicitudesRecursosPendientes.put(procesoActual.idUnicoProceso, recursosNecesitados);
            
            if (detectarInterbloqueo()) {
                gestionarInterbloqueo(procesoActual);
            }
            
            BitacoraEventos.registrarEvento("RECURSO", "PID " + procesoActual.idUnicoProceso + " bloqueado esperando recursos: " + 
                     String.join(", ", recursosNecesitados) + " (RAM disponible: " + 
                     totalMemoriaLibre + "MB, CPU: " + (indicadorCpuLibre ? "disponible" : "no disponible") + ")");
            return false;
        }
    }

    private boolean detectarInterbloqueo() {
        if (solicitudesRecursosPendientes.size() < 2) return false;
        
        int procesosCpuEsperados = 0;
        for (Map.Entry<Integer, List<String>> entry : solicitudesRecursosPendientes.entrySet()) {
            if (entry.getValue().contains("CPU")) {
                procesosCpuEsperados++;
            }
        }
        
        if (procesosCpuEsperados >= 2 && !indicadorCpuLibre) {
            BitacoraEventos.registrarEvento("SISTEMA", "¡INTERBLOQUEO DETECTADO! Múltiples procesos esperando CPU");
            return true;
        }
        
        return false;
    }
      private void gestionarInterbloqueo(BloqueControlProceso procesoEnConflicto) {
        procesoEnConflicto.condicionActual = "Terminado";
        procesoEnConflicto.razonFinalizacion = MotivoFinalizacion.DETECCION_INTERBLOQUEO;
        BitacoraEventos.registrarEvento("INTERBLOQUEO", "PID " + procesoEnConflicto.idUnicoProceso + " terminado para resolver interbloqueo");
        solicitudesRecursosPendientes.remove(procesoEnConflicto.idUnicoProceso);
        procesoEnConflicto.conjuntoRecursosEnEspera.clear();
    }
    
    public synchronized void liberarRecursosDeProceso(BloqueControlProceso procesoFinalizado) {
        boolean cpuLiberada = false;
        
        if (procesoFinalizado.conjuntoRecursosAsignados.isEmpty()) {
            BitacoraEventos.registrarEvento("RECURSO", "PID " + procesoFinalizado.idUnicoProceso + " no tenía recursos asignados");
            return;
        }
        
        if (asignacionMemoriaPorProceso.containsKey(procesoFinalizado.idUnicoProceso)) {
            int memoriaAsignada = asignacionMemoriaPorProceso.get(procesoFinalizado.idUnicoProceso);
            totalMemoriaLibre += memoriaAsignada;
            BitacoraEventos.registrarEvento("RECURSO", "PID " + procesoFinalizado.idUnicoProceso + " liberó " + memoriaAsignada + "MB de RAM");
            asignacionMemoriaPorProceso.remove(procesoFinalizado.idUnicoProceso);
        }
        
        for (String r : procesoFinalizado.conjuntoRecursosAsignados) {
            if (r.equals("CPU")) {
                indicadorCpuLibre = true;
                cpuLiberada = true;
                BitacoraEventos.registrarEvento("RECURSO", "PID " + procesoFinalizado.idUnicoProceso + " liberó CPU");
                break;
            }
        }
        
        if (!cpuLiberada) {
            BitacoraEventos.registrarEvento("RECURSO", "PID " + procesoFinalizado.idUnicoProceso + " no tenía la CPU asignada");
        }
        
        procesoFinalizado.conjuntoRecursosAsignados.clear();
        informarEstadoRecursos("Liberados por PID " + procesoFinalizado.idUnicoProceso);
        
        intentarDesbloquearProcesosEnEspera();
    }
    
    public void intentarDesbloquearProcesosEnEspera() {
        List<Integer> idsProcesosDesbloqueados = new ArrayList<>();
        
        for (Map.Entry<Integer, List<String>> entry : new HashMap<>(solicitudesRecursosPendientes).entrySet()) { // Iterate over a copy
            int idProceso = entry.getKey();
            List<String> recursosNecesitados = entry.getValue();
            
            boolean puedeDesbloquear = true;
            int memoriaRequerida = 0;
            boolean requiereCPU = false;
            
            for (String recurso : recursosNecesitados) {
                if (recurso.contains("MB RAM")) {
                    memoriaRequerida = Integer.parseInt(recurso.split("MB")[0]);
                }
                if (recurso.equals("CPU")) {
                    requiereCPU = true;
                }
            }
            
            if (requiereCPU && !indicadorCpuLibre) {
                puedeDesbloquear = false;
            }
            if (memoriaRequerida > totalMemoriaLibre) {
                puedeDesbloquear = false;
            }
            
            if (puedeDesbloquear) {
                idsProcesosDesbloqueados.add(idProceso);
                for (BloqueControlProceso proceso : Simulador_Procesos.gestorPlanificacionGlobal.conjuntoProcesosActivos) {
                    if (proceso.idUnicoProceso == idProceso && proceso.condicionActual.equals("Bloqueado")) {
                        proceso.condicionActual = "Listo";
                        proceso.conjuntoRecursosEnEspera.clear();
                        BitacoraEventos.registrarEvento("RECURSO", "PID " + idProceso + " desbloqueado, recursos disponibles");
                        // Attempt to assign resources immediately if possible
                        // This part might need careful handling to avoid re-locking or race conditions in a more complex sim
                        // For now, just setting to Listo and clearing expected. The scheduler will pick it up.
                        break; 
                    }
                }
            }
        }
        
        for (Integer idProceso : idsProcesosDesbloqueados) {
            solicitudesRecursosPendientes.remove(idProceso);
        }
    }
    
    private void informarEstadoRecursos(String causaCambio) {
        System.out.println("\n--- ACTUALIZACIÓN DE RECURSOS (" + causaCambio + ") ---");
        System.out.println("Memoria disponible: " + totalMemoriaLibre + "MB");
        System.out.println("CPU disponible: " + (indicadorCpuLibre ? "Sí" : "No"));
        System.out.println("---------------------------------------");
    }
    
    @Override
    public String toString() {
        return "Memoria disponible: " + totalMemoriaLibre + "MB, CPU disponible: " + indicadorCpuLibre;
    }
}

class ZonaIntercambioDatos {
    Queue<Integer> almacenamientoIntermedio = new LinkedList<>();
    int limiteCapacidad = 5; // Default, can be set by constructor
    Semaphore controlLleno;
    Semaphore controlVacio;
    Semaphore exclusionMutua = new Semaphore(1);

    public ZonaIntercambioDatos() { // Constructor to initialize semaphores with capacity
        this.controlLleno = new Semaphore(0);
        this.controlVacio = new Semaphore(this.limiteCapacidad);
    }
    
    public ZonaIntercambioDatos(int capacidadDefinida) {
        this.limiteCapacidad = capacidadDefinida;
        this.controlLleno = new Semaphore(0);
        this.controlVacio = new Semaphore(this.limiteCapacidad);
        this.exclusionMutua = new Semaphore(1);
    }


    public void agregarDato(int dato) throws InterruptedException {
        controlVacio.acquire();
        exclusionMutua.acquire();
        almacenamientoIntermedio.offer(dato);
        BitacoraEventos.registrarEvento("PRODUCTOR", "Producido: " + dato + " (buffer: " + almacenamientoIntermedio.size() + "/" + limiteCapacidad + ")");
        exclusionMutua.release();
        controlLleno.release();
    }

    public void retirarDato() throws InterruptedException {
        controlLleno.acquire();
        exclusionMutua.acquire();
        int elementoConsumido = almacenamientoIntermedio.poll();
        BitacoraEventos.registrarEvento("CONSUMIDOR", "Consumido: " + elementoConsumido + " (buffer: " + almacenamientoIntermedio.size() + "/" + limiteCapacidad + ")");
        exclusionMutua.release();
        controlVacio.release();
    }
}

public class Simulador_Procesos {
    static Scanner entradaUsuario = new Scanner(System.in);
    static AdministradorRecursos adminRecursosGlobal = new AdministradorRecursos();
    static GestorPlanificacion gestorPlanificacionGlobal;
    static int contadorTiempoGlobal = 0;
    static final String[] TEXTOS_MENSAJE_FIJOS = {
        "Solicitar recurso",
        "Liberar recurso",
        "Prioridad aumentada",
        "Prioridad disminuida",
        "Ejecutar tarea de I/O",
        "Terminar ejecución"
    };

    public static void main(String[] argumentos) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  BIENVENIDO AL SIMULADOR DE PROCESOS              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println("\nSeleccione el algoritmo de planificación que desea utilizar:");
        System.out.println("  1. Round Robin (Turnos equitativos con quantum)");
        System.out.println("  2. Prioridad (Basado en la prioridad del proceso)");
        System.out.println("  3. SJF (Trabajo más corto primero)");
        System.out.println("  4. FCFS (Primero en llegar, primero en ser atendido)");
        
        int opcion = solicitarEnteroEnRango("Ingrese el número de su elección: ", 1, 4);
        String algoritmoSeleccionado = "";
        int quantumVal = 2; 
        
        switch (opcion) {
            case 1: 
                algoritmoSeleccionado = "RoundRobin"; 
                quantumVal = solicitarEnteroEnRango("Defina el quantum para Round Robin (en unidades): ", 1, 10);
                break;
            case 2: algoritmoSeleccionado = "Prioridad"; break;
            case 3: algoritmoSeleccionado = "SJF"; break;
            case 4: algoritmoSeleccionado = "FCFS"; break;
        }
        
        gestorPlanificacionGlobal = new GestorPlanificacion(algoritmoSeleccionado, quantumVal);
        BitacoraEventos.registrarEvento("SISTEMA", "Simulador iniciado con el algoritmo " + algoritmoSeleccionado);
        
        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  MODO DE EJECUCIÓN DEL SIMULADOR                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println("  1. Modo Manual (Control total del usuario)");
        System.out.println("  2. Modo Automático (Simulación con datos aleatorios)");
        
        int modoEjecucion = solicitarEnteroEnRango("Seleccione el modo de ejecución: ", 1, 2);
        
        if (modoEjecucion == 1) {
            // Mostrar estado inicial en modo manual
            System.out.println("\n=== Estado inicial de procesos ===");
            gestorPlanificacionGlobal.visualizarEstadoProcesos();
            System.out.println("\n=== Estado inicial de recursos ===");
            verificarEstadoRecursos();
            System.out.println("\n=== Estado inicial de colas ===");
            gestorPlanificacionGlobal.visualizarColasPlanificacion();
            desplegarMenuPrincipal();
        } else {
            iniciarModoAutomatico();
        }
        entradaUsuario.close();
    }

    static void desplegarMenuPrincipal() {
        while (true) {
            System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                      GESTOR DE PROCESOS - MENÚ                    ║");
            System.out.println("╠════════════════════════════════════════════════════════════════════╣");
            System.out.println("║ 1. Mostrar la lista de procesos                                   ║");
            System.out.println("║ 2. Crear un nuevo proceso                                         ║");
            System.out.println("║ 3. Ver las colas de planificación                                 ║");
            System.out.println("║ 4. Consultar el estado de los recursos                            ║");
            System.out.println("║ 5. Suspender o reanudar un proceso                                ║");
            System.out.println("║ 6. Ejecutar el siguiente proceso                                  ║");
            System.out.println("║ 7. Leer los mensajes de un proceso                                ║");
            System.out.println("║ 8. Enviar un mensaje entre procesos                               ║");
            System.out.println("║ 9. Mostrar los procesos bloqueados                               ║");
            System.out.println("║ 10. Terminar un proceso                                           ║");
            System.out.println("║ 11. Demostración del modelo Productor-Consumidor                  ║");
            System.out.println("║ 12. Salir del simulador                                           ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            
            int opcion = solicitarEnteroEnRango("Seleccione una opción del menú: ", 1, 12);
            switch (opcion) {
                case 1: registrarNuevoProceso(); break;
                case 2: gestorPlanificacionGlobal.visualizarEstadoProcesos(); break;
                case 3: gestorPlanificacionGlobal.visualizarColasPlanificacion(); break; // Corregido
                case 4: verificarEstadoRecursos(); break; // Corregido
                case 5: procesarSiguienteTarea(); break;
                case 6: alternarEstadoProceso(); break;
                case 7: finalizarProcesoSeleccionado(); break;
                case 8: transmitirMensajeEntreProcesos(); break;
                case 9: consultarMensajesRecibidos(); break;
                case 10: demostrarProductorConsumidor(); break;
                case 11: listarProcesosBloqueados(); break;
                case 12: 
                    System.out.println("\nGracias por usar el Gestor de Procesos. ¡Hasta pronto!");
                    BitacoraEventos.registrarEvento("SISTEMA", "Simulador finalizado por el usuario.");
                    return;
            }
            gestorPlanificacionGlobal.refrescarColasDeProcesos();
        }
    }

    static void iniciarModoAutomatico() {
        System.out.println("\n====== SIMULACIÓN AUTOMÁTICA ======");
        BitacoraEventos.registrarEvento("SISTEMA", "Iniciando simulación automática con algoritmo " + gestorPlanificacionGlobal.estrategiaPlanificacion);
        
        Random random = new Random();
        int numProcesos = random.nextInt(6) + 5; // 5 to 10 processes
        
        System.out.println("\n=== Generando " + numProcesos + " procesos aleatorios ===");
        
        for (int i = 0; i < numProcesos; i++) {
            int prioridad = random.nextInt(10) + 1;
            int tiempoEjecucion = random.nextInt(15) + 5; // 5 to 20
            BloqueControlProceso p = new BloqueControlProceso(prioridad, tiempoEjecucion);
            gestorPlanificacionGlobal.encolarProceso(p);            // Simulate some processes requiring memory immediately
            if (random.nextDouble() < 0.7) { // 70% chance
                 // Removed unused memory requirement calculation
                 // Memory will be assigned during execution step
            }
            BitacoraEventos.registrarEvento("PROCESO", "Proceso creado: PID " + p.idUnicoProceso + 
                         ", Prioridad " + p.valorPrioridad + 
                         ", Tiempo " + p.tiempoTotalEjecucion);
        }
        gestorPlanificacionGlobal.refrescarColasDeProcesos();
        
        System.out.println("\n=== Estado inicial de procesos ===");
        gestorPlanificacionGlobal.visualizarEstadoProcesos();
        
        System.out.println("\n=== Estado inicial de recursos ===");
        verificarEstadoRecursos();
        
        System.out.println("\n=== Estado inicial de colas ===");
        gestorPlanificacionGlobal.visualizarColasPlanificacion();
        
        System.out.println("\n=== Enviando mensajes aleatorios entre procesos ===");
        if (numProcesos > 1) {
            int numMensajes = random.nextInt(numProcesos) + 1;
            for (int i = 0; i < numMensajes; i++) {
                if (gestorPlanificacionGlobal.conjuntoProcesosActivos.size() < 2) continue;
                BloqueControlProceso remitente = gestorPlanificacionGlobal.conjuntoProcesosActivos.get(random.nextInt(gestorPlanificacionGlobal.conjuntoProcesosActivos.size()));
                BloqueControlProceso destinatario;
                do {
                    destinatario = gestorPlanificacionGlobal.conjuntoProcesosActivos.get(random.nextInt(gestorPlanificacionGlobal.conjuntoProcesosActivos.size()));
                } while (destinatario.idUnicoProceso == remitente.idUnicoProceso && gestorPlanificacionGlobal.conjuntoProcesosActivos.size() > 1);

                if (remitente.idUnicoProceso != destinatario.idUnicoProceso) {
                     String contenido = TEXTOS_MENSAJE_FIJOS[random.nextInt(TEXTOS_MENSAJE_FIJOS.length)];
                     remitente.mandarMensaje(destinatario.idUnicoProceso, contenido + " (auto)");
                }
            }
        }
        
        System.out.println("\n=== Ejecutando procesos con algoritmo " + gestorPlanificacionGlobal.estrategiaPlanificacion + " ===");
        int ciclosMaximos = numProcesos * 5; // Limit execution cycles for auto mode
        int ciclos = 0;
        while(gestorPlanificacionGlobal.conjuntoProcesosActivos.stream().anyMatch(p -> !p.condicionActual.equals("Terminado")) && ciclos < ciclosMaximos) {
            procesarSiguienteTarea();
            gestorPlanificacionGlobal.refrescarColasDeProcesos(); // Refresh queues
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // Small delay
            ciclos++;
             if (ciclos % 10 == 0) { // Display status periodically
                gestorPlanificacionGlobal.visualizarEstadoProcesos();
                gestorPlanificacionGlobal.visualizarColasPlanificacion();
            }
        }
         BitacoraEventos.registrarEvento("SISTEMA", "Simulación automática completada o ciclos máximos alcanzados.");

        System.out.println("\n=== Estado final de procesos ===");
        gestorPlanificacionGlobal.visualizarEstadoProcesos();
        
        System.out.println("\n=== Estado final de recursos ===");
        verificarEstadoRecursos();
        
        System.out.println("\n====== FIN DE LA SIMULACIÓN AUTOMÁTICA ======");
    }

    static int solicitarEnteroEnRango(String textoSolicitud, int minimo, int maximo) {
        int valor;
        while (true) {
            System.out.print(textoSolicitud);
            if (entradaUsuario.hasNextInt()) {
                valor = entradaUsuario.nextInt();
                if (valor >= minimo && valor <= maximo) {
                    break;
                } else {
                    System.out.println("Valor fuera de rango. Debe estar entre " + minimo + " y " + maximo + ".");
                }
            } else {
                System.out.println("Entrada inválida. Por favor ingrese un número entero.");
                entradaUsuario.next(); // Limpiar buffer
            }
        }
        return valor;
    }

    static void registrarNuevoProceso() {
        System.out.println("\n--- Crear Nuevo Proceso ---");
        int prioridad = solicitarEnteroEnRango("Prioridad del proceso (1-10, menor es más prioritario): ", 1, 10);
        int tiempoEjec = solicitarEnteroEnRango("Tiempo de ejecución del proceso (1-100): ", 1, 100);
        
        BloqueControlProceso nuevoProceso = new BloqueControlProceso(prioridad, tiempoEjec);
        gestorPlanificacionGlobal.encolarProceso(nuevoProceso);
        gestorPlanificacionGlobal.refrescarColasDeProcesos();
        BitacoraEventos.registrarEvento("PROCESO", "Creado PID " + nuevoProceso.idUnicoProceso + 
                                     " Prioridad: " + nuevoProceso.valorPrioridad + 
                                     " Tiempo: " + nuevoProceso.tiempoTotalEjecucion);
    }

    static void verificarEstadoRecursos() {
        System.out.println("\n--- Estado Actual de Recursos ---");
        System.out.println(adminRecursosGlobal.toString());
    }
    
    static BloqueControlProceso buscarProcesoPorId(int id) {
        for (BloqueControlProceso p : gestorPlanificacionGlobal.conjuntoProcesosActivos) {
            if (p.idUnicoProceso == id) {
                return p;
            }
        }
        return null;
    }

    static void procesarSiguienteTarea() {
        BloqueControlProceso procesoActual = gestorPlanificacionGlobal.seleccionarSiguienteProceso();

        if (procesoActual == null) {
            BitacoraEventos.registrarEvento("EJECUCIÓN", "No hay procesos listos en la cola activa.");
            // Check for blocked processes that might become ready if resources are now free
            adminRecursosGlobal.intentarDesbloquearProcesosEnEspera();
            gestorPlanificacionGlobal.refrescarColasDeProcesos();
            return;
        }
        
        if (!procesoActual.condicionActual.equals("Listo")) {
             BitacoraEventos.registrarEvento("EJECUCIÓN", "Proceso PID " + procesoActual.idUnicoProceso + " no está Listo (estado: " + procesoActual.condicionActual + "). Volviendo a encolar si es RR.");
             if (gestorPlanificacionGlobal.estrategiaPlanificacion.equals("RoundRobin") && (procesoActual.condicionActual.equals("Ejecutando") || procesoActual.condicionActual.equals("Suspendido Listo"))) {
                // If it was "Ejecutando" it means its quantum might have just finished, or it was suspended.
                // If "Suspendido Listo", it should be handled by alternarEstadoProceso to become "Listo".
                // For RR, if it was running and not finished, it should be re-added to the queue.
                // This logic is tricky here, better handled after execution slice.
                // For now, if not "Listo", we might just re-add it to its queue if it's eligible.
                // gestorPlanificacionGlobal.encolarProceso(procesoActual); // This might create duplicates or incorrect order
             }
             // Attempt to unblock any waiting processes as resources might have been freed by another process
             adminRecursosGlobal.intentarDesbloquearProcesosEnEspera();
             gestorPlanificacionGlobal.refrescarColasDeProcesos(); // Refresh queues
             return;
        }

        BitacoraEventos.registrarEvento("EJECUCIÓN", "Intentando ejecutar PID " + procesoActual.idUnicoProceso);
        Random rand = new Random();
        int memoriaRequerida = (rand.nextInt(5) + 1) * 64; // Requiere 64-320MB

        if (!adminRecursosGlobal.asignarRecursos(procesoActual, memoriaRequerida)) {
            BitacoraEventos.registrarEvento("EJECUCIÓN", "PID " + procesoActual.idUnicoProceso + " no pudo obtener recursos, permanece Bloqueado.");
            // Proceso ya está bloqueado por asignarRecursos, no es necesario re-encolarlo en lista de listos.
            adminRecursosGlobal.intentarDesbloquearProcesosEnEspera(); // Check if others can run
            gestorPlanificacionGlobal.refrescarColasDeProcesos();
            return;
        }

        procesoActual.condicionActual = "Ejecutando";
        BitacoraEventos.registrarEvento("EJECUCIÓN", "Iniciando PID " + procesoActual.idUnicoProceso + 
                                     " (Tiempo restante: " + procesoActual.tiempoRestanteEjecucion + 
                                     ", Prioridad: " + procesoActual.valorPrioridad + ")");
        
        int tiempoEjecutarEsteCiclo = procesoActual.tiempoRestanteEjecucion;
        if (gestorPlanificacionGlobal.estrategiaPlanificacion.equals("RoundRobin")) {
            tiempoEjecutarEsteCiclo = Math.min(procesoActual.tiempoRestanteEjecucion, gestorPlanificacionGlobal.getCuantoTiempo());
        }
        
        // Simular paso del tiempo
        try { Thread.sleep(tiempoEjecutarEsteCiclo * 50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // Simula trabajo

        procesoActual.tiempoRestanteEjecucion -= tiempoEjecutarEsteCiclo;
        contadorTiempoGlobal += tiempoEjecutarEsteCiclo;
        procesoActual.acumuladoTiempoRetorno = contadorTiempoGlobal - procesoActual.marcaTiempoLlegada;


        if (procesoActual.tiempoRestanteEjecucion <= 0) {
            procesoActual.condicionActual = "Terminado";
            procesoActual.razonFinalizacion = MotivoFinalizacion.EJECUCION_NORMAL;
            procesoActual.marcaTiempoFinalizacion = contadorTiempoGlobal;
            BitacoraEventos.registrarEvento("EJECUCIÓN", "PID " + procesoActual.idUnicoProceso + " terminado normalmente.");
            adminRecursosGlobal.liberarRecursosDeProceso(procesoActual);
        } else if (gestorPlanificacionGlobal.estrategiaPlanificacion.equals("RoundRobin")) {
            procesoActual.condicionActual = "Listo"; // Vuelve a listo
            BitacoraEventos.registrarEvento("EJECUCIÓN", "PID " + procesoActual.idUnicoProceso + " quantum finalizado, vuelve a cola de listos.");
            adminRecursosGlobal.liberarRecursosDeProceso(procesoActual); // Libera CPU
            gestorPlanificacionGlobal.encolarProceso(procesoActual); // Re-encolar
        } else {
            // Para algoritmos no-RR (FCFS, SJF, Prioridad no preemptive), si no terminó, debería seguir "Ejecutando"
            // o pasar a "Listo" si la simulación es por pasos y este fue un paso.
            // En este modelo simple, si no es RR y no terminó, se asume que completó su lógica para este "paso"
            // y liberará recursos para que el planificador decida.
            procesoActual.condicionActual = "Listo"; // Vuelve a listo para ser re-evaluado
            BitacoraEventos.registrarEvento("EJECUCIÓN", "PID " + procesoActual.idUnicoProceso + " paso de ejecución completado, vuelve a cola de listos.");
            adminRecursosGlobal.liberarRecursosDeProceso(procesoActual); // Libera CPU
            gestorPlanificacionGlobal.encolarProceso(procesoActual); // Re-encolar
        }
        adminRecursosGlobal.intentarDesbloquearProcesosEnEspera(); // Check if others can run now
        gestorPlanificacionGlobal.refrescarColasDeProcesos(); // Actualizar colas
    }

    static void alternarEstadoProceso() {
        System.out.print("Ingrese PID del proceso a suspender/continuar: ");
        if (!entradaUsuario.hasNextInt()) { System.out.println("PID inválido."); entradaUsuario.next(); return; }
        int pid = entradaUsuario.nextInt();
        BloqueControlProceso p = buscarProcesoPorId(pid);

        if (p == null) {
            System.out.println("Proceso con PID " + pid + " no encontrado.");
            return;
        }

        if (p.condicionActual.equals("Listo")) {
            p.condicionActual = "Suspendido Listo";
            BitacoraEventos.registrarEvento("SISTEMA", "PID " + p.idUnicoProceso + " ahora está " + p.condicionActual);
        } else if (p.condicionActual.equals("Suspendido Listo")) {
            p.condicionActual = "Listo";
            BitacoraEventos.registrarEvento("SISTEMA", "PID " + p.idUnicoProceso + " ahora está " + p.condicionActual);
            // gestorPlanificacionGlobal.encolarProceso(p); // Re-add to ready queue
        } else if (p.condicionActual.equals("Bloqueado")) {
             p.condicionActual = "Suspendido Bloqueado";
             BitacoraEventos.registrarEvento("SISTEMA", "PID " + p.idUnicoProceso + " ahora está " + p.condicionActual);
        } else if (p.condicionActual.equals("Suspendido Bloqueado")) {
             p.condicionActual = "Bloqueado"; // Remains blocked, but no longer suspended
             BitacoraEventos.registrarEvento("SISTEMA", "PID " + p.idUnicoProceso + " ahora está " + p.condicionActual);
        } else {
            System.out.println("Proceso PID " + p.idUnicoProceso + " está en estado '" + p.condicionActual + "' y no puede ser suspendido/continuado de esta forma.");
        }
        gestorPlanificacionGlobal.refrescarColasDeProcesos();
    }

    static void finalizarProcesoSeleccionado() {
        System.out.print("Ingrese PID del proceso a terminar: ");
         if (!entradaUsuario.hasNextInt()) { System.out.println("PID inválido."); entradaUsuario.next(); return; }
        int pid = entradaUsuario.nextInt();
        BloqueControlProceso p = buscarProcesoPorId(pid);

        if (p == null) {
            System.out.println("Proceso con PID " + pid + " no encontrado.");
            return;
        }
        
        if (p.condicionActual.equals("Terminado")) {
            System.out.println("Proceso PID " + p.idUnicoProceso + " ya está terminado.");
            return;
        }

        p.condicionActual = "Terminado";
        p.razonFinalizacion = MotivoFinalizacion.TERMINADO_POR_OPERADOR;
        p.marcaTiempoFinalizacion = contadorTiempoGlobal;
        adminRecursosGlobal.liberarRecursosDeProceso(p);
        BitacoraEventos.registrarEvento("SISTEMA", "PID " + p.idUnicoProceso + " terminado por usuario.");
        gestorPlanificacionGlobal.refrescarColasDeProcesos();
    }

    static void transmitirMensajeEntreProcesos() {
        System.out.print("Ingrese PID del proceso origen: ");
        if (!entradaUsuario.hasNextInt()) { System.out.println("PID origen inválido."); entradaUsuario.next(); return; }
        int pidOrigen = entradaUsuario.nextInt();
        BloqueControlProceso origen = buscarProcesoPorId(pidOrigen);

        if (origen == null) {
            System.out.println("Proceso origen con PID " + pidOrigen + " no encontrado.");
            return;
        }

        System.out.print("Ingrese PID del proceso destino: ");
        if (!entradaUsuario.hasNextInt()) { System.out.println("PID destino inválido."); entradaUsuario.next(); return; }
        int pidDestino = entradaUsuario.nextInt();
        BloqueControlProceso destino = buscarProcesoPorId(pidDestino);
         if (destino == null) {
            System.out.println("Proceso destino con PID " + pidDestino + " no encontrado.");
            return;
        }
        if (pidOrigen == pidDestino) {
            System.out.println("Un proceso no puede enviarse mensajes a sí mismo de esta forma.");
            return;
        }

        System.out.print("Ingrese el contenido del mensaje: ");
        entradaUsuario.nextLine(); // Consumir newline
        String contenido = entradaUsuario.nextLine();
        
        origen.mandarMensaje(pidDestino, contenido);
    }

    static void consultarMensajesRecibidos() {
        System.out.print("Ingrese PID del proceso para leer sus mensajes: ");
        if (!entradaUsuario.hasNextInt()) { System.out.println("PID inválido."); entradaUsuario.next(); return; }
        int pid = entradaUsuario.nextInt();
        BloqueControlProceso p = buscarProcesoPorId(pid);

        if (p == null) {
            System.out.println("Proceso con PID " + pid + " no encontrado.");
            return;
        }
        p.revisarMensajes();
    }

    static void demostrarProductorConsumidor() {
        System.out.println("\n--- Demostración Productor-Consumidor ---");
        ZonaIntercambioDatos zonaCompartida = new ZonaIntercambioDatos(5); // Buffer de tamaño 5

        Thread productor = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    zonaCompartida.agregarDato(i);
                    Thread.sleep(new Random().nextInt(500) + 100); // Tiempo aleatorio
                }
            } catch (InterruptedException e) {
                BitacoraEventos.registrarEvento("PRODUCTOR", "Productor interrumpido.");
                Thread.currentThread().interrupt();
            }
        });

        Thread consumidor = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    zonaCompartida.retirarDato();
                    Thread.sleep(new Random().nextInt(1000) + 200); // Tiempo aleatorio
                }
            } catch (InterruptedException e) {
                BitacoraEventos.registrarEvento("CONSUMIDOR", "Consumidor interrumpido.");
                Thread.currentThread().interrupt();
            }
        });

        productor.start();
        consumidor.start();

        try {
            productor.join();
            consumidor.join();
        } catch (InterruptedException e) {
            BitacoraEventos.registrarEvento("SISTEMA", "Hilos de productor/consumidor interrumpidos.");
            Thread.currentThread().interrupt();
        }
        System.out.println("--- Fin Demostración Productor-Consumidor ---");
    }

    static void listarProcesosBloqueados() {
        System.out.println("\n--- Procesos Bloqueados ---");
        boolean encontrados = false;
        for (BloqueControlProceso p : gestorPlanificacionGlobal.conjuntoProcesosActivos) {
            if (p.condicionActual.equals("Bloqueado")) {
                System.out.println("PID: " + p.idUnicoProceso + ", Esperando: " + 
                                   (p.conjuntoRecursosEnEspera.isEmpty() ? "Recursos no especificados" : String.join(", ", p.conjuntoRecursosEnEspera)));
                encontrados = true;
            }
        }
        if (!encontrados) {
            System.out.println("No hay procesos bloqueados actualmente.");
        }
        System.out.println("--------------------------");
    }
}
