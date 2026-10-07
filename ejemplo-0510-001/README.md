# Ejemplo 0510-001 — Script con `CMD`

Imagen basada en `alpine` que ejecuta `holaMundo.sh`. El comando por defecto se define con `CMD`, por lo que **se reemplaza completo** si se pasa otro comando en `docker run`.

## Construir

Desde esta carpeta:

```bash
docker build -t hola-mundo:v1 .
```

La etiqueta `:v1` indica la versión de la imagen. Una misma imagen puede tener varias versiones (`:v1`, `:v2`, ...) y si no se pone etiqueta Docker usa `:latest`.

## Correr

```bash
docker run --rm --name hola-mundo_c hola-mundo:v1
```

Salida esperada:

```
Hola mundo
Parametro: Hola Docker
```

## Probar

1. Reemplazar el `CMD` con otro parámetro:

   ```bash
   docker run --rm --name hola-mundo_c hola-mundo:v1 /holaMundo.sh "Otro texto"
   ```

   Salida esperada: `Parametro: Otro texto`.

2. Reemplazar el `CMD` con un comando distinto (ya no se ejecuta el script):

   ```bash
   docker run --rm --name hola-mundo_c hola-mundo:v1 echo "Sin script"
   ```

3. Revisar la configuración de la imagen:

   ```bash
   docker image inspect hola-mundo:v1 --format '{{.Config.Cmd}}'
   ```

## Limpiar

```bash
docker rmi hola-mundo:v1
```

