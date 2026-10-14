# Ejemplo 1210-005 — SQL Server en minikube con Deployment, PVC y Secret

Se despliega Microsoft SQL Server 2022 en minikube. Es el equivalente en Kubernetes del contenedor `sqlserver` de `ejemplo-0710-002`, y usa tres piezas nuevas:

- Un **Secret** para la contraseña de `sa`, en lugar de escribirla en el manifiesto o en una variable de entorno visible.
- Un **PersistentVolumeClaim (PVC)** para que los datos sobrevivan si el pod se reinicia (equivale al volumen `sqlserver_data` de Docker).
- Un **Service** `ClusterIP` llamado `sqlserver`, para que otras aplicaciones del clúster se conecten a `sqlserver:1433`, igual que por el nombre del contenedor en la red de Docker.

> Las salidas son de referencia: IDs, IPs, nombres con hash y tiempos cambian en cada equipo.

## Requisitos

- minikube encendido. Comprobar con `minikube status` (ver `ejemplo-0910-001`).
- Memoria suficiente: SQL Server necesita **al menos 2 GB** para arrancar. El Deployment pide `2Gi` y tiene un límite de `3Gi`.
- No hay que construir ninguna imagen: `mcr.microsoft.com/mssql/server:2022-latest` se descarga sola la primera vez (pesa más de 1 GB, así que el primer arranque tarda).

## El Secret: `secret-sqlserver.example.yaml`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: sqlserver-secret
type: Opaque
stringData:
  MSSQL_SA_PASSWORD: "<contraseña de sa>"
```

- `stringData`: se escribe la contraseña en texto plano; Kubernetes la guarda codificada en base64 en el campo `data`. Base64 **no es cifrado**: cualquiera con acceso al clúster puede decodificarla.
- La contraseña va **entre comillas dobles**. Las comillas no forman parte del valor; son obligatorias si la contraseña es solo números o empieza con un símbolo especial de YAML.
- La contraseña debe tener al menos 8 caracteres y 3 de estos 4 tipos: mayúsculas, minúsculas, números y símbolos. Si no cumple (por ejemplo `mipassword`), el pod arranca y SQL Server se detiene a los pocos segundos.
- Este archivo es solo una plantilla y **sí se sube a git**. El que lleva la contraseña real es `secret-sqlserver.local.yaml`, que el `.gitignore` ignora (`secret*.local.yaml`).

## El manifiesto: `sql-deployment.yaml`

El PVC, el Deployment y el service van en el mismo archivo, separados por `---`.

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: sqlserver-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 400Mi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sqlserver-deployment
  labels:
    app: sqlserver-deployment
spec:
  replicas: 1
  strategy:
    type: Recreate
  selector:
    matchLabels:
      app: sqlserver-deployment
  template:
    metadata:
      labels:
        app: sqlserver-deployment
    spec:
      restartPolicy: Always
      containers:
        - name: sqlserver-c
          image: mcr.microsoft.com/mssql/server:2022-latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 1433
          env:
            - name: ACCEPT_EULA
              value: "Y"
            - name: MSSQL_SA_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: sqlserver-secret
                  key: MSSQL_SA_PASSWORD
          resources:
            requests:
              memory: 2Gi
            limits:
              memory: 3Gi
          volumeMounts:
            - name: datos
              mountPath: /var/opt/mssql
      volumes:
        - name: datos
          persistentVolumeClaim:
            claimName: sqlserver-data
---
apiVersion: v1
kind: Service
metadata:
  name: sqlserver
spec:
  selector:
    app: sqlserver-deployment
  type: ClusterIP
  ports:
    - protocol: TCP
      port: 1433
      targetPort: 1433
```

- **PVC**: pide 400 MiB de disco. `ReadWriteOnce` significa que solo un nodo puede montarlo a la vez. minikube crea el volumen solo, con su *StorageClass* `standard`.
- `replicas: 1`: SQL Server **no se escala así**. Cada réplica sería una base distinta y las dos competirían por el mismo volumen.
- `strategy.type: Recreate`: al actualizar, apaga el pod viejo **antes** de crear el nuevo. Con la estrategia por defecto (`RollingUpdate`) habría un momento con dos SQL Server usando los mismos archivos.
- `ACCEPT_EULA: "Y"`: SQL Server no arranca si no se aceptan sus términos de licencia. Va entre comillas para que YAML lo trate como texto y no como un booleano.
- `MSSQL_SA_PASSWORD` con `secretKeyRef`: la contraseña se lee del Secret. En `name` va el nombre del Secret (`sqlserver-secret`) y en `key` la clave dentro de él (`MSSQL_SA_PASSWORD`). El nombre de la **variable** es obligatorio y lo define SQL Server; el nombre de la **clave** del Secret puede ser cualquiera, siempre que coincida.
- `resources.requests.memory: 2Gi` y `limits.memory: 3Gi`: el scheduler solo coloca el pod en un nodo con 2 GB libres, y Kubernetes lo reinicia si pasa de 3 GB.
- `volumeMounts` en `/var/opt/mssql`: donde SQL Server guarda sus bases (el mismo punto de montaje que el volumen de Docker).
- **Service `ClusterIP`**: solo es accesible **dentro** del clúster. Su nombre es el host al que se conectan las aplicaciones. Desde la Mac se entra con `kubectl port-forward` (paso 4).

## Pasos

### 1. Crear el Secret con la contraseña real

```bash
cd ejemplo-1210-005
cp secret-sqlserver.example.yaml secret-sqlserver.local.yaml
```

Editar `secret-sqlserver.local.yaml` y poner la contraseña (por ejemplo `"YourStrong!Passw0rd"`). Luego crear el Secret:

```bash
kubectl apply -f secret-sqlserver.local.yaml
```

Salida esperada:

```
secret/sqlserver-secret created
```

Comprobar que la clave quedó guardada (sin mostrar el valor):

```bash
kubectl describe secret sqlserver-secret
```

Salida esperada (fragmento):

```
Name:         sqlserver-secret
Type:  Opaque

Data
====
MSSQL_SA_PASSWORD:  19 bytes
```

Si el Secret no existe cuando se crea el Deployment, el pod queda en `CreateContainerConfigError`. Después de crear el Secret, el pod arranca solo en su siguiente reintento.

### 2. Desplegar SQL Server

```bash
kubectl apply -f sql-deployment.yaml
```

Salida esperada:

```
persistentvolumeclaim/sqlserver-data created
deployment.apps/sqlserver-deployment created
service/sqlserver created
```

Esperar a que termine (la primera vez tarda por la descarga de la imagen):

```bash
kubectl rollout status deployment/sqlserver-deployment
```

Salida esperada:

```
deployment "sqlserver-deployment" successfully rolled out
```

### 3. Verificar que SQL Server arrancó

```bash
kubectl get pods -l app=sqlserver-deployment
kubectl get pvc sqlserver-data
kubectl get svc sqlserver
```

Salida esperada:

```
NAME                                        READY   STATUS    RESTARTS   AGE
pod/sqlserver-deployment-7ff55c79bf-jffxh   1/1     Running   0          2m

NAME                           STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS
persistentvolumeclaim/sqlserver-data   Bound    pvc-ff33354b-cf32-45d3-aa49-82aa3e189da1   400Mi      RWO            standard

NAME        TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
sqlserver   ClusterIP   10.101.23.145   <none>        1433/TCP   2m
```

`Running` solo indica que el contenedor arrancó, no que SQL Server esté listo. Se confirma en el log:

```bash
kubectl logs deployment/sqlserver-deployment | grep "ready for client connections"
```

Salida esperada:

```
... SQL Server is now ready for client connections. This is an informational message; no user action is required.
```

Si ese mensaje no aparece, ver la sección "Problemas comunes".

### 4. Conectarse desde la Mac con `port-forward`

El service es `ClusterIP`, así que no se puede entrar por la IP de minikube. Se abre un túnel desde el puerto `1433` de la Mac al service:

```bash
kubectl port-forward svc/sqlserver 1433:1433
```

Salida esperada:

```
Forwarding from 127.0.0.1:1433 -> 1433
Forwarding from [::1]:1433 -> 1433
```

Esa terminal debe quedarse abierta mientras se use la conexión. Si el puerto `1433` ya lo ocupa otro programa (por ejemplo el contenedor `sqlserver` de Docker), usar otro puerto local: `kubectl port-forward svc/sqlserver 1434:1433` y conectarse a `localhost,1434`.

En la extensión de SQL Server de VS Code (o en DBeaver / Management Studio), crear la conexión:

| Campo                    | Valor                                          |
| ------------------------ | ---------------------------------------------- |
| Servidor                 | `localhost` (o `localhost,1434` si se cambió)  |
| Autenticación            | SQL Login                                      |
| Usuario                  | `sa`                                           |
| Contraseña               | la que se puso en `secret-sqlserver.local.yaml` |
| Trust server certificate | Sí (el certificado es autofirmado)             |

### 5. Crear la base `curso` y los alumnos

Esta instancia está **vacía**: no tiene los datos del contenedor de Docker. Ejecutar sobre la conexión el script [`migration_001.sql`](../ejemplo-0710-002/migration_001.sql) de `ejemplo-0710-002`. Crea la base `curso`, la tabla `alumnos` y 8 registros, y se puede correr más de una vez sin duplicar datos.

Comprobarlo sin salir de la terminal, con el `sqlcmd` que trae la imagen (toma la contraseña de la variable de entorno que viene del Secret):

```bash
kubectl exec deployment/sqlserver-deployment -- sh -c \
  '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d curso -Q "SELECT id, nombre FROM alumnos"'
```

Salida esperada:

```
id                   nombre
-------------------- ----------
                   1 Andrea
                   2 Alexis
                   3 Asher
                   4 Axel
                   5 Fernando
                   6 Jorge
                   7 Levi
                   8 Humberto

(8 rows affected)
```

La aplicación de `ejemplo-1410-001` se conecta a esta base.

## Problemas comunes

| Síntoma                                                          | Causa                                                              | Solución                                                                                                                                          |
| ---------------------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Pod en `CreateContainerConfigError`                              | El Secret no existe, o `name` / `key` no coinciden con él          | `kubectl describe pod` (sección `Events`). Crear el Secret o corregir `secretKeyRef`.                                                              |
| Pod `Running` pero con `RESTARTS` en aumento (`CrashLoopBackOff`) | La contraseña no cumple la política de SQL Server                   | `kubectl logs deployment/sqlserver-deployment`. Cambiar la contraseña, y borrar el Deployment y el PVC (ver abajo) para empezar con un volumen limpio. |
| `port-forward` se cae al intentar conectar, o `connection refused` | SQL Server no está escuchando (se cayó o aún arranca)              | Esperar el mensaje `ready for client connections` en el log antes de conectar.                                                                      |
| `Login failed for user 'sa'`                                     | SQL Server guarda la contraseña de `sa` en el volumen la primera vez; cambiar el Secret después no la cambia | Borrar Deployment y PVC y volver a crearlos, o usar la contraseña original.                                                                        |
| Pod `Pending` o reiniciándose por memoria                        | A minikube le faltan 2 GB libres                                   | `minikube delete` y `minikube start --memory=4096` (borra todo el clúster).                                                                        |
| `Connection refused` pero el pod está sano                       | El `selector` del service no coincide con la etiqueta del pod      | `kubectl get endpoints sqlserver`. Si `ENDPOINTS` dice `<none>`, corregir el `selector`.                                                           |

Para empezar con una base nueva (**esto borra los datos**):

```bash
kubectl delete deployment sqlserver-deployment
kubectl delete pvc sqlserver-data
kubectl apply -f sql-deployment.yaml
```

## Limpiar

```bash
kubectl delete -f sql-deployment.yaml
kubectl delete -f secret-sqlserver.local.yaml
```

> `kubectl delete -f sql-deployment.yaml` borra también el **PVC**, y con él los datos de la base. Para apagar SQL Server sin perder los datos, basta con `kubectl scale deployment sqlserver-deployment --replicas=0` (y `--replicas=1` para volver a levantarlo).

Las salidas de `describe secret` y de la verificación del log son las que da normalmente `kubectl`; las demás se tomaron del clúster de este equipo.
