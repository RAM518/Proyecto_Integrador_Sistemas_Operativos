import java.util.*;
import java.util.concurrent.Semaphore;

public class ZonaIntercambioDatos {
    Queue<Integer> almacenamientoIntermedio = new LinkedList<>();
    int limiteCapacidad = 5; // Default, can be set by constructor
    Semaphore controlLleno;
    Semaphore controlVacio;
    Semaphore exclusionMutua = new Semaphore(1);

    public ZonaIntercambioDatos() {
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
