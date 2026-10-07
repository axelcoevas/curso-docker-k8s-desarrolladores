# Ejemplo 0710-002 — Servidor mssql

Instancia de Microsoft SQL Server 2022 a partir de la imagen oficial de Microsoft (no se construye ninguna imagen). Expone el puerto `1433` y guarda los datos en el volumen `sqlserver_data`.

> Las salidas son de referencia: IDs, digests, fechas e IPs cambian en cada equipo.

## Descargar la imagen

```bash
docker pull mcr.microsoft.com/mssql/server:2022-latest
```

Salida esperada (la imagen pesa más de 1 GB):

```
2022-latest: Pulling from mssql/server
a1b2c3d4e5f6: Pull complete
b2c3d4e5f6a1: Pull complete
c3d4e5f6a1b2: Pull complete
Digest: sha256:<digest>
Status: Downloaded newer image for mcr.microsoft.com/mssql/server:2022-latest
mcr.microsoft.com/mssql/server:2022-latest
```

## Correr instancia de mssql

```bash
docker run -d --name sqlserver \
  -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD='YourStrong!Passw0rd' \
  -p 1433:1433 \
  -v sqlserver_data:/var/opt/mssql \
  mcr.microsoft.com/mssql/server:2022-latest
```

La contraseña de `sa` debe tener al menos 8 caracteres con mayúsculas, minúsculas, números y símbolos. Si no la cumple (por ejemplo `root`), el contenedor se detiene a los pocos segundos de arrancar.

Salida esperada (el ID completo del contenedor):

```
3f9c2a7b1e4d8c6a5b0e9f2d1c7a4b8e6f3d2c1a0b9e8f7d6c5b4a3e2d1c0b9a
```

## Encontrar el contenedor

```bash
docker ps
```

Salida esperada:

```
CONTAINER ID   IMAGE                                        COMMAND                  CREATED         STATUS         PORTS                                         NAMES
f70ab6b9a4d0   mcr.microsoft.com/mssql/server:2022-latest   "/opt/mssql/bin/laun…"   8 seconds ago   Up 7 seconds   0.0.0.0:1433->1433/tcp, [::]:1433->1433/tcp   sqlserver
371b41a8db80   servidor-express:v1                          "docker-entrypoint.s…"   2 hours ago     Up 2 hours     0.0.0.0:3000->3000/tcp
```

## Inspeccionar el contenedor

```bash
docker inspect sqlserver
```

Salida esperada (JSON muy largo; se muestran solo las partes más relevantes):

```
[
    {
        "Id": "3f9c2a7b1e4d8c6a5b0e9f2d1c7a4b8e6f3d2c1a0b9e8f7d6c5b4a3e2d1c0b9a",
        "Created": "2026-10-07T18:00:00.000000000Z",
        "Path": "/opt/mssql/bin/permissions_check.sh",
        "Args": [
            "/opt/mssql/bin/sqlservr"
        ],
        "State": {
            "Status": "running",
            "Running": true,
            ...
        },
        "Name": "/sqlserver",
        ...
        "Mounts": [
            {
                "Type": "volume",
                "Name": "sqlserver_data",
                "Source": "/var/lib/docker/volumes/sqlserver_data/_data",
                "Destination": "/var/opt/mssql",
                "Driver": "local",
                "Mode": "z",
                "RW": true,
                "Propagation": ""
            }
        ],
        "Config": {
            "Hostname": "3f9c2a7b1e4d",
            "User": "mssql",
            "Env": [
                "ACCEPT_EULA=Y",
                "MSSQL_SA_PASSWORD=YourStrong!Passw0rd",
                ...
            ],
            "Image": "mcr.microsoft.com/mssql/server:2022-latest",
            ...
        },
        "NetworkSettings": {
            "Ports": {
                "1433/tcp": [
                    {
                        "HostIp": "0.0.0.0",
                        "HostPort": "1433"
                    }
                ]
            },
            "IPAddress": "172.17.0.2",
            ...
        }
    }
]
```

Las variables de entorno (incluida la contraseña) quedan visibles en `docker inspect`.

## Ver los volúmenes

```bash
docker volume ls
```

Salida esperada:

```
DRIVER    VOLUME NAME
local     sqlserver_data
```

## Inspeccionar el volumen

```bash
docker volume inspect sqlserver_data
```

Salida esperada:

```json
[
  {
    "CreatedAt": "2026-10-07T10:51:13-06:00",
    "Driver": "local",
    "Labels": null,
    "Mountpoint": "/var/lib/docker/volumes/sqlserver_data/_data",
    "Name": "sqlserver_data",
    "Options": null,
    "Scope": "local"
  }
]
```

`Mountpoint` es la ruta dentro de la máquina de Docker (en Mac, dentro de la VM de OrbStack) donde se guardan los datos del volumen.

## Conectarse a sqlserver mediante MSSQL Management Studio o DBeaver para crear la bd

1. Crear una nueva conexión a SQL Server en el cliente con estos datos:

   | Campo         | Valor                             |
   | ------------- | --------------------------------- |
   | Servidor      | `localhost`                       |
   | Puerto        | `1433`                            |
   | Autenticación | SQL Server (usuario y contraseña) |
   | Usuario       | `sa`                              |
   | Contraseña    | `YourStrong!Passw0rd`             |

   El certificado del contenedor es autofirmado, por lo que hay que activar la opción de **confiar en el certificado del servidor** (_Trust server certificate_); si no, la conexión falla con un error de certificado.

2. Abrir un editor de consultas sobre la conexión y ejecutar el script [`migration_001.sql`](migration_001.sql) que está en esta carpeta (se puede abrir el archivo directamente desde el cliente o copiar su contenido):

   ```sql
   CREATE DATABASE curso;
   GO

   USE curso;
   GO
   ```

3. Comprobar desde la terminal con el `sqlcmd` que ya trae la imagen (la opción `-C` confía en el certificado autofirmado):

   ```bash
   docker exec -it sqlserver /opt/mssql-tools18/bin/sqlcmd \
     -S localhost -U sa -P 'YourStrong!Passw0rd' -C \
     -Q "SELECT name FROM sys.databases"
   ```

   Salida esperada:

   ```
   name
   --------------------------------------------------------------------------------------------------------------------------------
   master
   tempdb
   model
   msdb
   curso

   (5 rows affected)
   ```

Como los datos viven en el volumen `sqlserver_data`, la base `curso` sigue existiendo aunque se elimine el contenedor y se vuelva a crear con el mismo `-v sqlserver_data:/var/opt/mssql`.

## Instalar docker CLI en Windows

Solo se instala el **cliente** (`docker.exe`), sin Docker Desktop: los contenedores corren en el servidor Rocky Linux y Windows solo envía los comandos. No requiere permisos de administrador.

1. Descargar con el navegador el archivo `docker-<version>.zip` más reciente (el de número de versión más alto) desde:

   https://download.docker.com/win/static/stable/x86_64/

   Se guarda en la carpeta **Descargas**.

2. Descomprimir el zip (clic derecho → **Extraer todo...**) y mover la carpeta `docker` que contiene a `C:\Users\<usuario>\`. Debe quedar así:

   ```
   C:\Users\<usuario>\docker\docker.exe
   ```

3. Agregar la carpeta al `PATH` del usuario (en PowerShell):

   ```powershell
   $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
   [Environment]::SetEnvironmentVariable("Path", "$userPath;$env:USERPROFILE\docker", "User")
   ```

4. Cerrar y volver a abrir PowerShell, y verificar:

   ```powershell
   docker --version
   ```

   Salida esperada:

   ```
   Docker version <version>, build <commit>
   ```

## Configurar un contexto hacia Rocky Linux vía SSH

Un **contexto** le indica al cliente `docker` a qué motor de Docker enviar los comandos. Con un contexto SSH, el cliente de Windows usa el motor que corre en Rocky Linux.

Crear el contexto en Windows (PowerShell):

```powershell
docker context create rocky --description "Docker en Rocky Linux" --docker "host=ssh://<usuario>@<ip_rocky>"
```

Salida esperada:

```
rocky
Successfully created context "rocky"
```

Al usar el contexto, `docker` se conecta por SSH al servidor, así que pedirá la contraseña de `<usuario>` (y la primera vez, aceptar la huella del servidor con `yes`).

## Verificar el contexto de docker

```bash
docker context ls
```

Salida esperada (el `*` marca el contexto activo):

```
NAME         DESCRIPTION                               DOCKER ENDPOINT                                       ERROR
default      Current DOCKER_HOST based configuration   unix:///var/run/docker.sock
orbstack *   OrbStack                                  unix:///Users/banxicodge/.orbstack/run/docker.sock
```

En Windows, después de crear el contexto, aparece algo como:

```
NAME        DESCRIPTION                               DOCKER ENDPOINT                    ERROR
default *   Current DOCKER_HOST based configuration   npipe:////./pipe/docker_engine
rocky       Docker en Rocky Linux                     ssh://<usuario>@<ip_rocky>
```

## Cambiar el contexto de docker

```bash
docker context use <nombre_contexto>
```

Por ejemplo, para usar el servidor Rocky Linux:

```bash
docker context use rocky
```

Salida esperada:

```
rocky
Current context is now "rocky"
```

A partir de aquí, `docker ps`, `docker run`, etc. se ejecutan en Rocky Linux. Si se corre ahí el contenedor `sqlserver`, el puerto `1433` se publica en el servidor, así que en DBeaver / Management Studio el **Servidor** es `<ip_rocky>` en lugar de `localhost`.
