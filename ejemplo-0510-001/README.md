# Ejemplo 0510-001 — Script con `CMD`

Imagen basada en `alpine` que ejecuta `holaMundo.sh`. El comando por defecto se define con `CMD`, por lo que **se reemplaza completo** si se pasa otro comando en `docker run`.

## Construir

Desde esta carpeta:

```bash
docker build -t ejemplo-0510-001 .
```

## Correr

```bash
docker run --rm ejemplo-0510-001
```

Salida esperada:

```
Hola mundo
Parametro: Hola Docker
```

## Probar

1. Reemplazar el `CMD` con otro parámetro:

   ```bash
   docker run --rm ejemplo-0510-001 /holaMundo.sh "Otro texto"
   ```

   Salida esperada: `Parametro: Otro texto`.

2. Reemplazar el `CMD` con un comando distinto (ya no se ejecuta el script):

   ```bash
   docker run --rm ejemplo-0510-001 echo "Sin script"
   ```

3. Revisar la configuración de la imagen:

   ```bash
   docker image inspect ejemplo-0510-001 --format '{{.Config.Cmd}}'
   ```

## Limpiar

```bash
docker rmi ejemplo-0510-001
```

