import java.util.*;

public class BloqueControlProceso {
    public int idUnicoProceso;
    public int valorPrioridad;
    public int tiempoTotalEjecucion;
    public int tiempoRestanteEjecucion;
    public String condicionActual;
    public int acumuladoTiempoRetorno;
    public int marcaTiempoLlegada;
    public int marcaTiempoFinalizacion;
    public MotivoFinalizacion razonFinalizacion;
    public List<String> conjuntoRecursosEnEspera = new ArrayList<>();

    public BloqueControlProceso(int prioridad, int tiempoEjecucion) {
        this.idUnicoProceso = new Random().nextInt(1000); // Generar un ID único
        this.valorPrioridad = prioridad;
        this.tiempoTotalEjecucion = tiempoEjecucion;
        this.tiempoRestanteEjecucion = tiempoEjecucion;
        this.condicionActual = "Listo";
    }

    public void mandarMensaje(int idDestino, String contenido) {
        // Implementación para enviar mensajes
    }

    public void revisarMensajes() {
        // Implementación para revisar mensajes
    }
}
