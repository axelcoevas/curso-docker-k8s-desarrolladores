# Ejemplo 1410-001 — API de alumnos (Spring Boot) en minikube, conectada a SQL Server

Se despliega en minikube la imagen `servidor-spring:v1`, la API de alumnos de `ejemplo-0710-003`, y se conecta al SQL Server de `ejemplo-1210-005`. Es el mismo trabajo que antes se hacía con `--network red-curso` y `--env-file .env` en Docker, ahora con:

- Un **Deployment** que corre la API.
- **Variables de entorno** para la URL y el usuario, y la **contraseña tomada del Secret** `sqlserver-secret` (sin escribirla en el manifiesto).
- Un **Service** `NodePort` para exponer la API.

> Las salidas son de referencia: IDs, IPs, nombres con hash y tiempos cambian en cada equipo.

## Requisitos

- minikube encendido (`minikube status`).
- **SQL Server corriendo en minikube**, según `ejemplo-1210-005`: el Secret `sqlserver-secret` creado y el service `sqlserver` disponible. Comprobar:

  ```bash
  kubectl get secret sqlserver-secret
  kubectl get svc sqlserver
  ```

- **La base `curso` con la tabla `alumnos`**, creada con [`migration_001.sql`](../ejemplo-0710-002/migration_001.sql). La aplicación crea las tablas que le falten (`ddl-auto: update`), pero **no crea la base de datos**.
- **La imagen `servidor-spring:v1` cargada en minikube.** minikube tiene su propio motor de Docker, así que no ve las imágenes de la Mac, y `servidor-spring:v1` no existe en ningún registro de internet. Construir la imagen a partir del `Dockerfile` de `ejemplo-0710-003` y cargarla:

  ```bash
  cd ../ejemplo-0710-003
  docker build -t servidor-spring:v1 .
  minikube image load servidor-spring:v1
  cd ../ejemplo-1410-001
  ```

  Comprobar que está en minikube:

  ```bash
  minikube image ls | grep servidor-spring
  ```

  Salida esperada:

  ```
  docker.io/library/servidor-spring:v1
  ```

## El manifiesto: `spring-deployment.yaml`

El Deployment y el service van en el mismo archivo, separados por `---`.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: servidor-spring-deployment
  labels:
    app: servidor-spring-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: servidor-spring-deployment
  template:
    metadata:
      labels:
        app: servidor-spring-deployment
    spec:
      restartPolicy: Always
      containers:
        - name: servidor-spring-c
          image: servidor-spring:v1
          imagePullPolicy: Never
          ports:
            - containerPort: 8081
          env:
            - name: URL_SQLSERVER
              value: jdbc:sqlserver://sqlserver:1433;databaseName=curso;encrypt=true;trustServerCertificate=true
            - name: USER_NAME
              value: sa
            - name: PASSWORD
              valueFrom:
                secretKeyRef:
                  name: sqlserver-secret
                  key: MSSQL_SA_PASSWORD
---
apiVersion: v1
kind: Service
metadata:
  name: servidor-spring-service
spec:
  selector:
    app: servidor-spring-deployment
  type: NodePort
  ports:
    - protocol: TCP
      port: 8081
      targetPort: 8081
```

- `imagePullPolicy: Never`: usa **solo** la imagen que ya está en minikube y nunca intenta descargarla. Si falta el `minikube image load`, el pod queda en `ErrImageNeverPull`. (`IfNotPresent` también funciona; lo que no funciona es `Always`, ni una etiqueta `:latest`, que lo activa por defecto.)
- `URL_SQLSERVER`: la URL JDBC apunta al host **`sqlserver`**, el nombre del Service de SQL Server. Dentro del clúster `localhost` sería el propio pod de Spring, no la base de datos. `trustServerCertificate=true` es necesario porque el certificado de SQL Server es autofirmado.
- `USER_NAME`: `sa`, en texto plano; no es un dato sensible.
- `PASSWORD` con `secretKeyRef`: la contraseña se lee de la clave `MSSQL_SA_PASSWORD` del Secret `sqlserver-secret`, **el mismo Secret que usa SQL Server**, así que las dos contraseñas siempre coinciden. Los nombres de las variables (`URL_SQLSERVER`, `USER_NAME`, `PASSWORD`) son los que lee `application.yaml` con `${...}`.
- `replicas: 1`: la API no guarda estado, así que se podrían usar más réplicas; se deja una para ahorrar memoria.
- Service `NodePort`: igual que en `ejemplo-1210-004`, con el `selector` apuntando a la etiqueta de la plantilla.

## Pasos

### 1. Desplegar la aplicación

```bash
kubectl apply -f spring-deployment.yaml
```

Salida esperada:

```
deployment.apps/servidor-spring-deployment created
service/servidor-spring-service created
```

Esperar a que termine:

```bash
kubectl rollout status deployment/servidor-spring-deployment
```

Salida esperada:

```
deployment "servidor-spring-deployment" successfully rolled out
```

### 2. Verificar que la aplicación arrancó

```bash
kubectl get pods -l app=servidor-spring-deployment
kubectl get service servidor-spring-service
```

Salida esperada:

```
NAME                                             READY   STATUS    RESTARTS   AGE
servidor-spring-deployment-b9b645895-djsn9       1/1     Running   0          14s

NAME                      TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
servidor-spring-service   NodePort   10.108.37.191   <none>        8081:30101/TCP   14s
```

El NodePort (`30101`) cambia en cada equipo.

`Running` y `1/1` aparecen casi de inmediato, porque el Deployment no tiene un *readiness probe*: no significa que Spring ya haya terminado de arrancar (tarda unos 10 segundos). Se confirma en el log:

```bash
kubectl logs deployment/servidor-spring-deployment | grep "Started CursoApplication"
```

Salida esperada:

```
... org.banxico.curso.CursoApplication       : Started CursoApplication in 10.105 seconds (process running for 11.052)
```

### 3. Acceder a la API con `port-forward`

Con el driver de Docker en Mac, la IP de `minikube ip` no es alcanzable desde la Mac, así que el NodePort no sirve directamente. Se abre un túnel al service:

```bash
kubectl port-forward svc/servidor-spring-service 8081:8081
```

Salida esperada:

```
Forwarding from 127.0.0.1:8081 -> 8081
Forwarding from [::1]:8081 -> 8081
```

Esa terminal debe quedarse abierta. (Otra opción: `minikube service servidor-spring-service --url`, que imprime una URL con un puerto local; usar esa URL en lugar de `localhost:8081`.)

### 4. Probar la API

En otra terminal:

1. Listar los alumnos que ya están en la base:

   ```bash
   curl http://localhost:8081/alumnos
   ```

   Salida esperada (si se corrió `migration_001.sql`):

   ```json
   [{"id":1,"nombre":"Andrea"},{"id":2,"nombre":"Alexis"},{"id":3,"nombre":"Asher"},{"id":4,"nombre":"Axel"},{"id":5,"nombre":"Fernando"},{"id":6,"nombre":"Jorge"},{"id":7,"nombre":"Levi"},{"id":8,"nombre":"Humberto"}]
   ```

2. Crear un alumno:

   ```bash
   curl -X POST http://localhost:8081/alumnos \
     -H "Content-Type: application/json" \
     -d '{"nombre":"Nuevo"}'
   ```

   Salida esperada:

   ```json
   {"id":9,"nombre":"Nuevo"}
   ```

3. Volver a listar con el primer `curl`: ahora debe aparecer también el alumno con `id` 9. El registro queda en SQL Server, en el volumen del PVC, así que sobrevive aunque se borre el pod de la API.

## Problemas comunes

| Síntoma                                                                      | Causa                                                                   | Solución                                                                                                                  |
| ---------------------------------------------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Pod en `ErrImageNeverPull` o `ImagePullBackOff`                              | La imagen no está cargada en minikube                                   | `minikube image load servidor-spring:v1` y borrar el pod (`kubectl delete pod -l app=servidor-spring-deployment`).        |
| Pod en `CreateContainerConfigError`                                          | No existe el Secret `sqlserver-secret`                                  | Crearlo como en `ejemplo-1210-005`.                                                                                       |
| El pod reinicia (`CrashLoopBackOff`) y el log dice `Connection refused`      | El host de la URL es `localhost` o SQL Server no está corriendo         | Usar el host `sqlserver` en `URL_SQLSERVER` y comprobar `kubectl get pods` de SQL Server.                                 |
| El log dice `Unable to determine Dialect without JDBC metadata`              | Es el síntoma de arriba: no se pudo abrir ninguna conexión              | Revisar el host, el puerto y la contraseña de la conexión.                                                                |
| El log dice `Cannot open database "curso"`                                   | La base `curso` no existe en esta instancia de SQL Server               | Ejecutar `migration_001.sql` (paso 5 de `ejemplo-1210-005`).                                                              |
| El log dice `Login failed for user 'sa'`                                     | La contraseña del Secret no es la que guardó SQL Server en su volumen   | Ver "Problemas comunes" de `ejemplo-1210-005`.                                                                            |
| `curl` responde `Connection refused` aunque el pod está `Running`            | Spring aún está arrancando, o el `port-forward` no está abierto         | Esperar el mensaje `Started CursoApplication` en el log y revisar la terminal del `port-forward`.                         |

Para ver el motivo de un fallo, siempre empezar por:

```bash
kubectl describe pod -l app=servidor-spring-deployment    # sección Events
kubectl logs deployment/servidor-spring-deployment
```

## Limpiar

```bash
kubectl delete -f spring-deployment.yaml
```

Borra el Deployment (con su ReplicaSet y su pod) y el service. SQL Server y sus datos siguen intactos. Para quitar también la imagen de minikube: `minikube image rm servidor-spring:v1`.

Las salidas de `rollout status`, de `curl -X POST` y del `port-forward` son las que da normalmente `kubectl` y la API; las demás se tomaron del clúster de este equipo.
