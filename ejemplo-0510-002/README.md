# Ejemplo 0510-002 — `ENTRYPOINT` + `CMD`

Imagen basada en `alpine` que ejecuta `holaMundo.sh` e imprime todos los parámetros recibidos. El script se fija con `ENTRYPOINT` y `CMD` solo aporta **argumentos por defecto**, que se reemplazan con los que se pasen en `docker run`.

## Construir

Desde esta carpeta:

```bash
docker build -t ejemplo-0510-002 .
```

## Correr

```bash
docker run --rm ejemplo-0510-002
```

Salida esperada:

```
Hola mundo
Parametro: Hola Docker
Parametro: --port
Parametro: 8080
```

## Probar

1. Reemplazar solo los argumentos del `CMD` (el `ENTRYPOINT` se conserva):

   ```bash
   docker run --rm ejemplo-0510-002 --port 9090
   ```

   Salida esperada:

   ```
   Hola mundo
   Parametro: Hola Docker
   Parametro: --port
   Parametro: 9090
   ```

2. Sobrescribir el `ENTRYPOINT` para abrir una shell dentro del contenedor:

   ```bash
   docker run --rm -it --entrypoint sh ejemplo-0510-002
   ```

3. Revisar la configuración de la imagen:

   ```bash
   docker image inspect ejemplo-0510-002 --format 'Entrypoint={{.Config.Entrypoint}} Cmd={{.Config.Cmd}}'
   ```

## Limpiar

```bash
docker rmi ejemplo-0510-002
```

