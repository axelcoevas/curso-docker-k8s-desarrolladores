# Ejemplo 0710-003 — API de alumnos con Spring Boot y SQL Server

API REST con Spring Boot 4.1.1 (Java 17), Spring Data JPA y SQL Server. Expone `GET /alumnos` y `POST /alumnos` en el puerto `8081` y guarda los datos en la base `curso` del contenedor `sqlserver`.

## Requisitos

- Java 17 y Maven.
- VS Code con la extensión **Spring Boot Extension Pack** (incluye _Spring Initializr Java Support_).
- El contenedor `sqlserver` del [ejemplo-0710-002](../ejemplo-0710-002/README.md) corriendo y la base `curso` creada con [`migration_001.sql`](../ejemplo-0710-002/migration_001.sql).

## Crear el proyecto con la extensión de VS Code

1. Abrir la paleta de comandos (`Cmd+Shift+P` / `Ctrl+Shift+P`) y ejecutar **Spring Initializr: Create a Maven Project...**.
2. Elegir estos valores:

   | Opción              | Valor                                                     |
   | ------------------- | --------------------------------------------------------- |
   | Spring Boot version | `4.1.1`                                                   |
   | Lenguaje            | Java                                                      |
   | Group Id            | `org.banxico`                                             |
   | Artifact Id         | `curso`                                                   |
   | Packaging           | Jar                                                       |
   | Java version        | `17`                                                      |
   | Dependencias        | Spring Web, Spring Data JPA, MS SQL Server Driver, Lombok |

3. Elegir la carpeta donde se genera el proyecto. Se crea con la clase principal `src/main/java/org/banxico/curso/CursoApplication.java`.

## Configurar la conexión a SQL Server

Dejar `src/main/resources/application.yaml` con este contenido (si el proyecto se generó con `application.properties`, renombrarlo a `application.yaml`):

```yaml
spring:
  application:
    name: curso
  datasource:
    url: ${URL_SQLSERVER}
    username: ${USER_NAME}
    password: ${PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
server:
  port: 8081
```

- `${URL_SQLSERVER}`, `${USER_NAME}` y `${PASSWORD}`: los datos de conexión se leen de variables de entorno, así no quedan escritos dentro de la imagen y la misma imagen sirve para cualquier servidor.
- `trustServerCertificate=true` (va en la URL): el certificado del contenedor es autofirmado. Sin esta opción la aplicación no arranca y muestra el error `PKIX path building failed`.
- `ddl-auto: update`: Hibernate crea la tabla `alumnos` a partir del modelo si todavía no existe.

## Crear el modelo

`src/main/java/org/banxico/curso/entity/Alumno.java`:

```java
package org.banxico.curso.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alumnos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alumno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
}
```

`@Entity` y `@Table` asocian la clase con la tabla `alumnos`, y `@Id` con `@GeneratedValue(IDENTITY)` hacen que SQL Server genere el `id`. Las anotaciones de Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`) generan getters, setters y constructores.

## Crear el repository

`src/main/java/org/banxico/curso/repository/AlumnoRepository.java`:

```java
package org.banxico.curso.repository;

import org.banxico.curso.entity.Alumno;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlumnoRepository extends JpaRepository<Alumno, Long> {
}
```

Al extender `JpaRepository<Alumno, Long>` ya se tienen `findAll()`, `findById()`, `save()`, `deleteById()`, etc., sin escribir SQL.

## Crear el controller

`src/main/java/org/banxico/curso/controller/AlumnoController.java`:

```java
package org.banxico.curso.controller;

import java.util.List;

import org.banxico.curso.entity.Alumno;
import org.banxico.curso.repository.AlumnoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/alumnos")
@AllArgsConstructor
public class AlumnoController {

    private final AlumnoRepository alumnoRepository;

    @GetMapping
    public List<Alumno> obtenerAlumnos() {
        return alumnoRepository.findAll();
    }

    @PostMapping
    public Alumno crearAlumno(@RequestBody Alumno alumno) {
        Alumno alumnoCreado = alumnoRepository.save(alumno);

        return alumnoCreado;
    }
}
```

- `GET /alumnos`: regresa todos los alumnos.
- `POST /alumnos`: recibe un alumno en JSON, lo guarda y regresa el registro con su `id`.
- `@AllArgsConstructor` genera el constructor con el que Spring inyecta el `AlumnoRepository`.

## Correr la aplicación

Desde esta carpeta, definir las variables de entorno (fuera de Docker el host es `localhost`) y arrancar con `mvn` (el proyecto no incluye la carpeta `.mvn`, así que `./mvnw` no funciona):

```bash
export URL_SQLSERVER='jdbc:sqlserver://localhost:1433;databaseName=curso;encrypt=true;trustServerCertificate=true'
export USER_NAME=sa
export PASSWORD='YourStrong!Passw0rd'

mvn spring-boot:run
```

Al final de la salida debe aparecer:

```
... o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 8081 (http) with context path '/'
... org.banxico.curso.CursoApplication       : Started CursoApplication in 3.088 seconds (process running for 3.347)
```

## Probar

En otra terminal:

1. Crear un alumno:

   ```bash
   curl -X POST http://localhost:8081/alumnos \
     -H "Content-Type: application/json" \
     -d '{"nombre":"Levi"}'
   ```

   Salida esperada:

   ```json
   { "id": 1, "nombre": "Levi" }
   ```

2. Listar los alumnos:

   ```bash
   curl http://localhost:8081/alumnos
   ```

   Salida esperada:

   ```json
   [{ "id": 1, "nombre": "Levi" }]
   ```

## Detener

En la terminal donde corre la aplicación, presionar `Ctrl+C`.

## Construir la imagen

El `Dockerfile` usa una construcción **multi-stage**:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/target/curso-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
```

- **Etapa `build`** (imagen con Maven y JDK 17): compila el proyecto y genera el jar. Primero se copia solo el `pom.xml` y se descargan las dependencias; como esa capa queda en caché, al cambiar solo el código no se vuelven a descargar.
- **Etapa final** (solo JRE 17, sin Maven ni código fuente): copia el jar de la etapa `build`. La imagen final es mucho más ligera.
- `-DskipTests`: las pruebas se omiten porque necesitan conectarse a SQL Server, que no está disponible durante el build.

Desde esta carpeta:

```bash
docker build -t servidor-spring:v1 .
```

## Definir las variables en el archivo .env

El contenedor necesita las variables `URL_SQLSERVER`, `USER_NAME` y `PASSWORD`. Se toman de un archivo `.env` creado a partir de [`.env.example`](.env.example):

```bash
cp .env.example .env
```

Y poner los valores reales:

```
URL_SQLSERVER=jdbc:sqlserver://sqlserver:1433;databaseName=curso;encrypt=true;trustServerCertificate=true
USER_NAME=sa
PASSWORD=YourStrong!Passw0rd
```

- Dentro de Docker el host es **`sqlserver`** (el nombre del contenedor de SQL Server), no `localhost`: dentro de un contenedor, `localhost` es el propio contenedor. Si se deja `localhost`, la aplicación falla con `Connection refused` y después con `Unable to determine Dialect without JDBC metadata`.
- En el `.env` los valores van sin comillas.
- El `.env` no se sube a git (`.gitignore`) ni entra a la imagen (`.dockerignore`); solo `.env.example` se versiona.

## Crear una red para que los contenedores se vean

Para que `servidor-spring_c` encuentre a `sqlserver` por su nombre, ambos contenedores deben estar en la misma red definida por el usuario (en la red `bridge` por defecto los contenedores no se resuelven por nombre).

1. Crear la red:

   ```bash
   docker network create red-curso
   ```

2. Conectar el contenedor de SQL Server que ya está corriendo (no hace falta recrearlo):

   ```bash
   docker network connect red-curso sqlserver
   ```

> En Linux (por ejemplo, Rocky Linux) también funciona correr el contenedor con `--network host` y `localhost` en la URL, porque el contenedor comparte la red del servidor. La red `red-curso` funciona igual en Mac, Windows y Linux.

## Correr el contenedor

```bash
docker run -d --name servidor-spring_c \
  --network red-curso \
  -p 8081:8081 \
  --env-file .env \
  servidor-spring:v1
```

Verificar que arrancó:

```bash
docker logs servidor-spring_c | grep "Started CursoApplication"
```

Y probar con los mismos `curl` de la sección [Probar](#probar).

## Limpiar

```bash
docker rm -f servidor-spring_c
docker network disconnect red-curso sqlserver
docker network rm red-curso
docker rmi servidor-spring:v1
```
