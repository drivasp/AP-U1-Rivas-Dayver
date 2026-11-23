import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;

public class Ventanilla {
    static int id;
    static Lamport reloj = new Lamport();
    static Bitacora log;
    static int contador = 0;
    static volatile boolean sinConexion = false;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Falta el id. Ejemplo: java Ventanilla 1");
            return;
        }
        id = Integer.parseInt(args[0]);
        log = new Bitacora("ventanilla" + id + ".log", id);
        log.escribir(reloj.tick(), "INICIO", "ventanilla=" + id);

        // hilo de latidos cada 2 segundos
        Thread hilo = new Thread(() -> latidos());
        hilo.setDaemon(true);
        hilo.start();

        System.out.println("Ventanilla " + id + " - comandos: turno, llamar, estado, turno-token-malo, salir");
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String cmd = sc.nextLine().trim();
            if (cmd.equals("turno")) {
                pedir("TURNO", Config.token);
            } else if (cmd.equals("turno-token-malo")) {
                pedir("TURNO", "token-incorrecto");
            } else if (cmd.equals("llamar")) {
                pedir("LLAMAR", Config.token);
            } else if (cmd.equals("estado")) {
                pedir("ESTADO", Config.token);
            } else if (cmd.equals("salir")) {
                break;
            } else if (!cmd.isEmpty()) {
                System.out.println("Comando no valido");
            }
        }
        log.escribir(reloj.tick(), "FIN", "ventanilla=" + id);
    }

    static void pedir(String tipo, String token) {
        contador++;
        String msg = "V" + id + "-" + contador;
        int l = reloj.tick();
        log.escribir(l, "ENVIA_" + tipo, "a=0 msg=" + msg);
        String json = "{\"tipo\":\"" + tipo + "\",\"token\":\"" + token + "\",\"origen\":" + id + ",\"lamport\":" + l + ",\"msg\":\"" + msg + "\"}";

        try {
            String resp = enviar(json);
            String tipoResp = Json.get(resp, "tipo");
            int numero = Json.getInt(resp, "numero");
            int lr = reloj.recibir(Json.getInt(resp, "lamport"));
            String detalle = "de=0 msg=" + msg;
            if (numero > 0) {
                detalle = "de=0 numero=" + numero + " msg=" + msg;
            }
            log.escribir(lr, "RECIBE_" + tipoResp, detalle);

            if (tipoResp.equals("ASIGNADO")) {
                System.out.println("Turno asignado: " + numero);
            } else if (tipoResp.equals("RECHAZADA")) {
                System.out.println("Rechazado: " + Json.get(resp, "motivo"));
            } else if (tipoResp.equals("LLAMADO")) {
                System.out.println("Llamando al turno " + numero);
            } else if (tipoResp.equals("INFO")) {
                System.out.println("Emitidos: " + Json.getInt(resp, "emitidos") + "/" + Json.getInt(resp, "cupo")
                        + " | En espera: " + Json.getInt(resp, "enEspera")
                        + " | Ventanillas: " + Json.get(resp, "ventanillas"));
            }
        } catch (ConnectException | SocketTimeoutException e) {
            // E5: el coordinador no esta, se avisa pero el programa sigue
            log.escribir(reloj.tick(), "FALLO_COORDINADOR", e.getClass().getSimpleName());
            System.out.println("ERROR: no hay conexion con el coordinador, intente mas tarde");
        } catch (IOException e) {
            log.escribir(reloj.tick(), "FALLO_COORDINADOR", "" + e.getMessage());
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    // abre la conexion, manda una linea y lee una linea de respuesta
    static String enviar(String json) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(Config.host, Config.puerto), 3000);
            s.setSoTimeout(5000);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out.println(json);
            String resp = in.readLine();
            if (resp == null) {
                throw new IOException("el coordinador no respondio");
            }
            return resp;
        }
    }

    static void latidos() {
        while (true) {
            String json = "{\"tipo\":\"LATIDO\",\"token\":\"" + Config.token + "\",\"origen\":" + id + ",\"lamport\":" + reloj.tick() + "}";
            try {
                String resp = enviar(json);
                reloj.recibir(Json.getInt(resp, "lamport"));
                if (sinConexion) {
                    sinConexion = false;
                    log.escribir(reloj.tick(), "COORDINADOR_RECUPERADO", "latidos ok");
                    System.out.println("Conexion recuperada con el coordinador");
                }
            } catch (IOException e) {
                // solo se registra la primera vez que falla
                if (!sinConexion) {
                    sinConexion = true;
                    log.escribir(reloj.tick(), "FALLO_COORDINADOR", "latido fallido " + e.getClass().getSimpleName());
                    System.out.println("Se perdio la conexion con el coordinador");
                }
            }
            try {
                Thread.sleep(Config.latido);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
