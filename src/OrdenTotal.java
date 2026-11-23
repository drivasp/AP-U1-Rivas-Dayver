import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrdenTotal {

    static class Evento {
        int lamport;
        int proceso;
        String linea;

        Evento(String linea) {
            String[] partes = linea.split(";");
            this.lamport = Integer.parseInt(partes[0].trim());
            this.proceso = Integer.parseInt(partes[1].trim());
            this.linea = linea;
        }
    }

    public static void main(String[] args) throws IOException {
        String[] archivos = {"coordinador.log", "ventanilla1.log", "ventanilla2.log", "ventanilla3.log"};
        List<Evento> eventos = new ArrayList<>();

        for (String nombre : archivos) {
            File f = new File("logs/" + nombre);
            if (!f.exists()) {
                System.out.println("No existe " + f + ", se salta");
                continue;
            }
            BufferedReader br = new BufferedReader(new FileReader(f));
            String linea;
            while ((linea = br.readLine()) != null) {
                if (!linea.isBlank()) {
                    eventos.add(new Evento(linea));
                }
            }
            br.close();
        }

        eventos.sort(Comparator.comparingInt((Evento e) -> e.lamport).thenComparingInt(e -> e.proceso));

        PrintWriter pw = new PrintWriter("logs/orden_total.log");
        for (Evento e : eventos) {
            pw.println(e.linea);
        }
        pw.close();
        System.out.println("Se ordenaron " + eventos.size() + " eventos en logs/orden_total.log");

        Map<String, Integer> envios = new HashMap<>();
        for (Evento e : eventos) {
            if (e.linea.contains("ENVIA_TURNO")) {
                envios.put(msgDe(e.linea), e.lamport);
            }
        }
        int errores = 0;
        for (Evento e : eventos) {
            if (e.linea.contains("RECIBE_TURNO")) {
                Integer envio = envios.get(msgDe(e.linea));
                if (envio == null) {
                    continue;
                }
                String ok = e.lamport > envio ? "OK" : "ERROR";
                System.out.println(msgDe(e.linea) + ": envio L=" + envio + " -> recepcion L=" + e.lamport + "  " + ok);
                if (e.lamport <= envio) {
                    errores++;
                }
            }
        }
        System.out.println("Errores de causalidad: " + errores);
    }

    static String msgDe(String linea) {
        int i = linea.indexOf("msg=");
        if (i == -1) {
            return "";
        }
        return linea.substring(i + 4).trim();
    }
}
