# Ejemplo 0710-003 — API de alumnos con Spring Boot y SQL Server

API REST con Spring Boot 4.1.1 (Java 17), Spring Data JPA y SQL Server. Expone `GET /alumnos` y `POST /alumnos` en el puerto `8081` y guarda los datos en la base `curso` del contenedor `sqlserver`.

## Requisitos

- Java 17 y Maven.
- VS Code con la extensión **Spring Boot Extension Pack** (incluye *Spring Initializr Java Support*).
- El contenedor `sqlserver` del [ejemplo-0710-002](../ejemplo-0710-002/README.md) corriendo y la base `curso` creada con [`migration_001.sql`](../ejemplo-0710-002/migration_001.sql).

## Crear el proyecto con la extensión de VS Code

1. Abrir la paleta de comandos (`Cmd+Shift+P` / `Ctrl+Shift+P`) y ejecutar **Spring Initializr: Create a Maven Project...**.
2. Elegir estos valores:

   | Opción              | Valor                                                    |
   | ------------------- | -------------------------------------------------------- |
   | Spring Boot version | `4.1.1`                                                  |
   | Lenguaje            | Java                                                     |
   | Group Id            | `org.banxico`                                            |
   | Artifact Id         | `curso`                                                  |
   | Packaging           | Jar                                                      |
   | Java version        | `17`                                                     |
   | Dependencias        | Spring Web, Spring Data JPA, MS SQL Server Driver, Lombok |

3. Elegir la carpeta donde se genera el proyecto. Se crea con la clase principal `src/main/java/org/banxico/curso/CursoApplication.java`.

## Configurar la conexión a SQL Server

Dejar `src/main/resources/application.yaml` con este contenido (si el proyecto se generó con `application.properties`, renombrarlo a `application.yaml`):

```yaml
spring:
  application:
    name: curso
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=curso;encrypt=true;trustServerCertificate=true
    username: sa
    password: YourStrong!Passw0rd
  jpa:
    hibernate:
      ddl-auto: update
server:
  port: 8081
```

- `trustServerCertificate=true`: el certificado del contenedor es autofirmado. Sin esta opción la aplicación no arranca y muestra el error `PKIX path building failed`.
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

Desde esta carpeta (se usa `mvn` porque el proyecto no incluye la carpeta `.mvn`, así que `./mvnw` no funciona):

```bash
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
     -d '{"nombre":"Ana"}'
   ```

   Salida esperada:

   ```json
   {"id":1,"nombre":"Ana"}
   ```

2. Listar los alumnos:

   ```bash
   curl http://localhost:8081/alumnos
   ```

   Salida esperada:

   ```json
   [{"id":1,"nombre":"Ana"}]
   ```

## Detener

En la terminal donde corre la aplicación, presionar `Ctrl+C`.
