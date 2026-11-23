// Funciones para sacar valores de un json simple de una sola linea
// ejemplo: {"tipo":"TURNO","origen":1,"lamport":5}
public class Json {

    public static String get(String json, String clave) {
        int i = json.indexOf("\"" + clave + "\":");
        if (i == -1) {
            return null;
        }
        i = i + clave.length() + 3;
        int fin;
        if (json.charAt(i) == '"') {
            // es texto, va hasta la siguiente comilla
            i++;
            fin = json.indexOf('"', i);
        } else {
            // es numero, va hasta la coma o la llave
            fin = i;
            while (fin < json.length() && json.charAt(fin) != ',' && json.charAt(fin) != '}') {
                fin++;
            }
        }
        return json.substring(i, fin);
    }

    public static int getInt(String json, String clave) {
        String valor = get(json, clave);
        if (valor == null) {
            return -1;
        }
        return Integer.parseInt(valor);
    }
}
