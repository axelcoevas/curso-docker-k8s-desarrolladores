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

3. Cambiar el puerto con una variable de entorno:

   ```bash
   docker run -d --name servidor-express-8080_c -e PORT=8080 -p 8080:8080 servidor-express:v1
   curl http://localhost:8080/
   ```

4. Verificar que el `.dockerignore` funcionó (no deben aparecer `README.md` ni `Dockerfile`):

   ```bash
   docker run --rm servidor-express:v1 ls -a /app
   ```

## Limpiar

```bash
docker rm -f servidor-express_c servidor-express-8080_c
docker rmi servidor-express:v1
```
