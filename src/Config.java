import java.io.FileInputStream;
import java.util.Properties;

// Lee los valores de config.properties
public class Config {
    static String host = "127.0.0.1";
    static int puerto = 5000;
    static int cupo = 10;
    static String token = "";
    static int latido = 2000;
    static int timeout = 6000;

    static {
        try {
            Properties p = new Properties();
            p.load(new FileInputStream("config.properties"));
            host = p.getProperty("coordinador.host");
            puerto = Integer.parseInt(p.getProperty("coordinador.puerto"));
            cupo = Integer.parseInt(p.getProperty("cupo.maximo"));
            latido = Integer.parseInt(p.getProperty("latido.intervalo.ms"));
            timeout = Integer.parseInt(p.getProperty("latido.timeout.ms"));
            token = p.getProperty("token");
        } catch (Exception e) {
            System.out.println("No se puede leer ");
        }
    }
}
