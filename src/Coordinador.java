import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class Coordinador {
    static Lamport reloj = new Lamport();
    static Bitacora log = new Bitacora("coordinador.log", 0);
    static boolean inseguro = false;

    // estado global del sistema
    static volatile int emitidos = 0;
    static Queue<Integer> cola = new LinkedList<>();
    static String[] estado = {"", "CAIDA", "CAIDA", "CAIDA"};   // se usan las posiciones 1, 2 y 3
    static long[] ultimoLatido = new long[4];
    static Object lock = new Object();

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("-inseguro")) {
            inseguro = true;
        }
        String modo = inseguro ? "INSEGURO" : "NORMAL";
        log.escribir(reloj.tick(), "INICIO", "modo=" + modo + " cupo=" + Config.cupo);
        System.out.println("Coordinador iniciado en el puerto " + Config.puerto + " - modo " + modo);

        // hilo que revisa los latidos de las ventanillas
        new Thread(() -> revisarLatidos()).start();

        ServerSocket servidor = new ServerSocket(Config.puerto, 100);
        while (true) {
            Socket cliente = servidor.accept();
            // cada peticion se atiende en un hilo nuevo
            new Thread(() -> atender(cliente)).start();
        }
    }

    static void atender(Socket s) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);

            String linea = in.readLine();   // un mensaje = una linea
            if (linea == null) {
                s.close();
                return;
            }
            String tipo = Json.get(linea, "tipo");
            int origen = Json.getInt(linea, "origen");
            String msg = Json.get(linea, "msg");
            int l = reloj.recibir(Json.getInt(linea, "lamport"));

            String respuesta;
            if (tipo.equals("LATIDO")) {
                respuesta = latido(origen, l);
            } else {
                log.escribir(l, "RECIBE_" + tipo, "de=" + origen + " msg=" + msg);
                if (tipo.equals("TURNO")) {
                    respuesta = darTurno(origen, msg);
                } else if (tipo.equals("LLAMAR")) {
                    respuesta = llamar(origen, msg);
                } else if (tipo.equals("ESTADO")) {
                    respuesta = estado(msg);
                } else {
                    respuesta = "{\"tipo\":\"ERROR\",\"origen\":0,\"lamport\":" + reloj.tick() + "}";
                }
            }
            out.println(respuesta);   // println pone el \n y hace flush
            s.close();
        } catch (IOException e) {
            log.escribir(reloj.tick(), "ERROR_CONEXION", "" + e.getMessage());
        }
    }

    static String latido(int v, int l) {
        synchronized (lock) {
            ultimoLatido[v] = System.currentTimeMillis();
            if (!estado[v].equals("ACTIVA")) {
                estado[v] = "ACTIVA";
                log.escribir(l, "VENTANILLA_ACTIVA", "ventanilla=" + v);
                System.out.println("Ventanilla " + v + " ACTIVA");
            }
        }
        return "{\"tipo\":\"ACK\",\"origen\":0,\"lamport\":" + reloj.tick() + "}";
    }

    // seccion critica: revisar cupo, sumar y encolar
    static String darTurno(int origen, String msg) {
        int numero = -1;
        if (!inseguro) {
            synchronized (lock) {
                if (emitidos < Config.cupo) {
                    emitidos++;
                    numero = emitidos;
                    cola.add(numero);
                }
            }
        } else {
            // modo inseguro: se lee fuera del lock y se espera 50 ms antes de escribir
            int leido = emitidos;
            if (leido < Config.cupo) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
                emitidos = leido + 1;
                numero = leido + 1;
                synchronized (lock) {
                    cola.add(numero);
                }
            }
        }

        int l = reloj.tick();
        if (numero == -1) {
            log.escribir(l, "ENVIA_RECHAZADA", "a=" + origen + " msg=" + msg);
            return "{\"tipo\":\"RECHAZADA\",\"origen\":0,\"lamport\":" + l + ",\"motivo\":\"cupo agotado\",\"msg\":\"" + msg + "\"}";
        }
        log.escribir(l, "ENVIA_ASIGNADO", "a=" + origen + " numero=" + numero + " msg=" + msg);
        return "{\"tipo\":\"ASIGNADO\",\"origen\":0,\"lamport\":" + l + ",\"numero\":" + numero + ",\"msg\":\"" + msg + "\"}";
    }

    static String llamar(int origen, String msg) {
        Integer numero;
        synchronized (lock) {
            numero = cola.poll();
        }
        int l = reloj.tick();
        if (numero == null) {
            log.escribir(l, "ENVIA_COLA_VACIA", "a=" + origen + " msg=" + msg);
            return "{\"tipo\":\"COLA_VACIA\",\"origen\":0,\"lamport\":" + l + ",\"msg\":\"" + msg + "\"}";
        }
        log.escribir(l, "ENVIA_LLAMADO", "a=" + origen + " numero=" + numero + " msg=" + msg);
        return "{\"tipo\":\"LLAMADO\",\"origen\":0,\"lamport\":" + l + ",\"numero\":" + numero + ",\"msg\":\"" + msg + "\"}";
    }

    static String estado(String msg) {
        int enEspera;
        String ventanillas;
        synchronized (lock) {
            enEspera = cola.size();
            ventanillas = "1=" + estado[1] + ",2=" + estado[2] + ",3=" + estado[3];
        }
        int l = reloj.tick();
        log.escribir(l, "ENVIA_INFO", "emitidos=" + emitidos + " enEspera=" + enEspera + " " + ventanillas);
        return "{\"tipo\":\"INFO\",\"origen\":0,\"lamport\":" + l + ",\"emitidos\":" + emitidos
                + ",\"enEspera\":" + enEspera + ",\"cupo\":" + Config.cupo
                + ",\"ventanillas\":\"" + ventanillas + "\",\"msg\":\"" + msg + "\"}";
    }

    // si una ventanilla pasa mas de 6 s sin latido se marca como CAIDA
    static void revisarLatidos() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            long ahora = System.currentTimeMillis();
            synchronized (lock) {
                for (int v = 1; v <= 3; v++) {
                    long sinLatido = ahora - ultimoLatido[v];
                    if (estado[v].equals("ACTIVA") && sinLatido > Config.timeout) {
                        estado[v] = "CAIDA";
                        log.escribir(reloj.tick(), "VENTANILLA_CAIDA", "ventanilla=" + v + " sin latido hace " + sinLatido + " ms");
                        System.out.println("Ventanilla " + v + " CAIDA");
                    }
                }
            }
        }
    }
}
