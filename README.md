# TurnoJusto (ISR-701)

Prototipo de emisión distribuida de turnos: un coordinador central y tres ventanillas que se comunican por TCP, con relojes de Lamport y detección de fallos por latidos.

**Autor:** NOMBRE APELLIDO
**Requisitos:** JDK 21. No se usan librerías externas.

## Compilar

Desde la carpeta raíz del proyecto:

```
javac -d out src/*.java
```

## Ejecutar

Cada proceso va en su propia terminal y siempre se ejecuta desde la carpeta raíz, porque ahí están `config.properties` y `logs/`.

```
java -cp out Coordinador              # modo normal
java -cp out Coordinador -inseguro    # modo con condición de carrera
java -cp out Ventanilla 1             # también 2 y 3
java -cp out PruebaCarga normal       # el argumento solo da nombre al archivo de resultado
java -cp out OrdenTotal
```

Comandos de la ventanilla: `turno`, `llamar`, `estado`, `salir`.

## Estructura

| Archivo | Función |
|---|---|
| `Coordinador.java` | Servidor TCP, estado global, sección crítica y detector de fallos |
| `Ventanilla.java` | Cliente de consola con hilo de latidos y manejo de caídas (E5) |
| `PruebaCarga.java` | 30 peticiones simultáneas con `CountDownLatch` |
| `OrdenTotal.java` | Fusiona los logs por (lamport, id_proceso) y verifica E6 |
| `Lamport.java` | Reloj lógico con métodos `synchronized` |
| `Bitacora.java` | Logs con formato `lamport; id_proceso; evento; detalle` |
| `Json.java` | Lectura de campos de mensajes JSON de una línea |
| `Config.java` | Lectura de `config.properties` |

## Código reutilizado

Si reutilizaste código de prácticas anteriores, indícalo aquí. Si no, borra esta sección.

- Repositorio: https://github.com/USUARIO/REPO
- Commit: `HASH_COMPLETO`
- Archivos reutilizados: ...
