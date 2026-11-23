// Reloj logico de Lamport. Los metodos son synchronized para que
// dos hilos no lo modifiquen al mismo tiempo.
public class Lamport {
    private int reloj = 0;

    // evento interno o envio de mensaje
    public synchronized int tick() {
        reloj++;
        return reloj;
    }

    // al recibir un mensaje: max(local, recibido) + 1
    public synchronized int recibir(int recibido) {
        reloj = Math.max(reloj, recibido) + 1;
        return reloj;
    }
}
