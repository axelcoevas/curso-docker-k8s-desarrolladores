# Ejemplo 0510-003 — Servidor Express (Node.js)

API mínima con Express sobre `node:20-slim`. Escucha en el puerto `3000` (configurable con la variable `PORT`) y responde en `GET /`.

Las dependencias se instalan **dentro de la imagen** con `npm ci`; la carpeta local `node_modules` se excluye con `.dockerignore`.

## Construir

Desde esta carpeta:

```bash
docker build -t servidor-express:v1 .
```

La etiqueta `:v1` indica la versión de la imagen. Una misma imagen puede tener varias versiones (`:v1`, `:v2`, ...) y si no se pone etiqueta Docker usa `:latest`.

## Correr

```bash
docker run -d --name servidor-express_c -p 3000:3000 servidor-express:v1
```

## Probar

1. Llamar al endpoint:

   ```bash
   curl http://localhost:3000/
   ```

   Respuesta esperada:

   ```json
   { "message": "Hola desde Express" }
   ```

2. Ver los logs del contenedor:

   ```bash
   docker logs servidor-express_c
   ```

   Debe mostrar: `Servidor corriendo en http://localhost:3000`.

## Entrar al contenedor

1. Abrir una shell dentro del contenedor en ejecución:

   ```bash
   docker exec -it servidor-express_c sh
   ```

2. Listar los archivos de la aplicación:

   ```bash
   ls
   ```

   Salida esperada:

   ```
   Dockerfile  node_modules  package-lock.json  package.json  servidor.js
   ```

3. Ver la versión del kernel:

   ```bash
   uname -r
   ```

   Salida esperada (varía según el host):

   ```
   7.0.14-orbstack-00380-ga7e0a2dc9535
   ```

4. Ver la distribución del sistema operativo de la imagen:

   ```bash
   cat /etc/os-release
   ```

   Salida esperada:

   ```
   PRETTY_NAME="Debian GNU/Linux 12 (bookworm)"
   NAME="Debian GNU/Linux"
   VERSION_ID="12"
   VERSION="12 (bookworm)"
   VERSION_CODENAME=bookworm
   ID=debian
   HOME_URL="https://www.debian.org/"
   SUPPORT_URL="https://www.debian.org/support"
   BUG_REPORT_URL="https://bugs.debian.org/"
   ```

5. Ver la información completa del kernel:

   ```bash
   cat /proc/version
   ```

   Salida esperada (varía según el host):

   ```
   Linux version 7.0.14-orbstack-00380-ga7e0a2dc9535 (orbstack@builder) (ClangBuiltLinux clang version 22.1.3 (https://github.com/llvm/llvm-project.git e9846648fd6183ee6d8cbdb4502213fcf902a211), ClangBuiltLinux LLD 22.1.3 (https://github.com/llvm/llvm-project.git e9846648fd6183ee6d8cbdb4502213fcf902a211)) #1 SMP PREEMPT Fri Aug 7 03:48:40 UTC 2026
   ```

   El sistema de archivos es Debian (viene de la imagen `node:20-slim`), pero el kernel es el del host (aquí, la VM de OrbStack): los contenedores **comparten el kernel** del host.

6. Salir del contenedor (sigue corriendo):

   ```bash
   exit
   ```

## Limpiar

```bash
docker rm -f servidor-express_c servidor-express-8080_c
docker rmi servidor-express:v1
```
