import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class PruebaCarga {

    public static void main(String[] args) throws Exception {
        int total = 30;
        Lamport reloj = new Lamport();
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(total);
        List<String> respuestas = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < total; i++) {
            int ventanilla = i % 3 + 1;
            String msg = "PC" + ventanilla + "-" + (i / 3 + 1);
            new Thread(() -> {
                try {
                    inicio.await();
                    String json = "{\"tipo\":\"TURNO\",\"token\":\"" + Config.token + "\",\"origen\":" + ventanilla + ",\"lamport\":" + reloj.tick() + ",\"msg\":\"" + msg + "\"}";
                    respuestas.add(enviar(json));
                } catch (Exception e) {
                    respuestas.add("{\"tipo\":\"ERROR\"}");
                }
                fin.countDown();
            }).start();
        }

        System.out.println("Enviando " + total + " peticiones al mismo tiempo...");
        inicio.countDown();
        fin.await();

        int asignados = 0;
        int rechazados = 0;
        List<Integer> numeros = new ArrayList<>();
        for (String r : respuestas) {
            String tipo = Json.get(r, "tipo");
            if (tipo.equals("ASIGNADO")) {
                asignados++;
                numeros.add(Json.getInt(r, "numero"));
            } else if (tipo.equals("RECHAZADA")) {
                rechazados++;
            }
        }
        Set<Integer> unicos = new HashSet<>(numeros);

        String info = enviar("{\"tipo\":\"ESTADO\",\"token\":\"" + Config.token + "\",\"origen\":9,\"lamport\":" + reloj.tick() + ",\"msg\":\"PC-estado\"}");
        int emitidos = Json.getInt(info, "emitidos");

        String resultado = "Peticiones enviadas: " + total + "\n"
                + "ASIGNADO: " + asignados + "\n"
                + "RECHAZADA: " + rechazados + "\n"
                + "Numeros unicos (Set): " + unicos.size() + "\n"
                + "Numeros repetidos: " + (asignados - unicos.size()) + "\n"
                + "Emitidos segun coordinador: " + emitidos + " (cupo " + Config.cupo + ")\n";
        if (asignados == unicos.size() && asignados == emitidos && asignados <= Config.cupo) {
            resultado += "Resultado: CONSISTENTE\n";
        } else {
            resultado += "Resultado: INCONSISTENTE (condicion de carrera)\n";
        }
        System.out.print(resultado);

        String nombre = "resultado";
        if (args.length > 0) {
            nombre = args[0];
        }
        new File("logs").mkdirs();
        PrintWriter pw = new PrintWriter("logs/prueba_carga_" + nombre + ".txt");
        pw.print(resultado);
        pw.close();
        System.out.println("Guardado en logs/prueba_carga_" + nombre + ".txt");
    }

    static String enviar(String json) throws IOException {
        try (Socket s = new Socket(Config.host, Config.puerto)) {
            s.setSoTimeout(10000);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out.println(json);
            return in.readLine();
        }
    }
}
