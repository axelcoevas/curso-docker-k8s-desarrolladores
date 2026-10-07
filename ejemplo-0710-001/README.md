# Ejemplo 0710-001 — Servidor nginx

Servidor web `nginx` a partir de la imagen oficial de Docker Hub (no se construye ninguna imagen). Expone el puerto `80` del contenedor en el puerto `80` del host.

## Correr el servidor nginx

```bash
docker run -d -p 80:80 --name servidor-nginx_c nginx
```

## Verificar que está corriendo

1. Listar los contenedores en ejecución (debe aparecer `servidor-nginx_c`):

   ```bash
   docker ps
   ```

2. Llamar al servidor (o abrir http://localhost en el navegador):

   ```bash
   curl http://localhost
   ```

   Debe responder con la página de bienvenida de nginx (`Welcome to nginx!`).

## Entrar al contenedor

1. Abrir una shell dentro del contenedor en ejecución:

   ```bash
   docker exec -it servidor-nginx_c sh
   ```

2. Ver el usuario actual:

   ```bash
   whoami
   ```

   Salida esperada:

   ```
   root
   ```

3. Ver la distribución del sistema operativo de la imagen:

   ```bash
   cat /etc/os-release
   ```

   Salida esperada:

   ```
   PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
   NAME="Debian GNU/Linux"
   VERSION_ID="13"
   VERSION="13 (trixie)"
   VERSION_CODENAME=trixie
   DEBIAN_VERSION_FULL=13.7
   ID=debian
   HOME_URL="https://www.debian.org/"
   SUPPORT_URL="https://www.debian.org/support"
   BUG_REPORT_URL="https://bugs.debian.org/"
   ```

4. Ver la información del kernel:

   ```bash
   cat /proc/version
   ```

   Salida esperada (varía según el host):

   ```
   Linux version 7.0.14-orbstack-00380-ga7e0a2dc9535 (orbstack@builder) (ClangBuiltLinux clang version 22.1.3 (https://github.com/llvm/llvm-project.git e9846648fd6183ee6d8cbdb4502213fcf902a211), ClangBuiltLinux LLD 22.1.3 (https://github.com/llvm/llvm-project.git e9846648fd6183ee6d8cbdb4502213fcf902a211)) #1 SMP PREEMPT Fri Aug 7 03:48:40 UTC 2026
   ```

   El sistema de archivos es Debian (viene de la imagen `nginx`), pero el kernel es el del host (aquí, la VM de OrbStack): los contenedores **comparten el kernel** del host.

5. Ver los sistemas de archivos montados:

   ```bash
   df -h
   ```

   Salida esperada (varía según el host):

   ```
   Filesystem      Size  Used Avail Use% Mounted on
   overlay          41G  3.5G   37G   9% /
   tmpfs            64M     0   64M   0% /dev
   shm             4.0G     0  4.0G   0% /dev/shm
   /dev/vdb1        41G  3.5G   37G   9% /etc/hosts
   tmpfs           4.0K     0  4.0K   0% /proc/asound
   ```

   La raíz `/` es un sistema de archivos `overlay`: las capas de la imagen (solo lectura) más una capa de escritura propia del contenedor.

6. Salir del contenedor (sigue corriendo):

   ```bash
   exit
   ```

## Limpiar

1. Detener y eliminar el contenedor:

   ```bash
   docker rm -f servidor-nginx_c
   ```

2. Eliminar la imagen descargada:

   ```bash
   docker rmi nginx
   ```

3. Verificar que ya no existen:

   ```bash
   docker ps -a --filter name=servidor-nginx_c
   docker images nginx
   ```
