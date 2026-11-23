import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

// Guarda los eventos en logs/ con el formato: lamport; id_proceso; evento; detalle
public class Bitacora {
    private PrintWriter pw;
    private int id;

    public Bitacora(String archivo, int id) {
        this.id = id;
        try {
            new File("logs").mkdirs();
            pw = new PrintWriter(new FileWriter("logs/" + archivo, true), true);
        } catch (IOException e) {
            System.out.println("No se pudo crear el log " + archivo);
        }
    }

    public synchronized void escribir(int lamport, String evento, String detalle) {
        pw.println(lamport + "; " + id + "; " + evento + "; " + detalle);
    }
}
