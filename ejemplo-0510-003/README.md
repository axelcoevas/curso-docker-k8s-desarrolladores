# Ejemplo 0510-003 — Servidor Express (Node.js)

API mínima con Express sobre `node:20-slim`. Escucha en el puerto `3000` (configurable con la variable `PORT`) y responde en `GET /`.

Las dependencias se instalan **dentro de la imagen** con `npm ci`; la carpeta local `node_modules` se excluye con `.dockerignore`.

## Construir

Desde esta carpeta:

```bash
docker build -t ejemplo-0510-003 .
```

## Correr

```bash
docker run -d --name ejemplo-0510-003 -p 3000:3000 ejemplo-0510-003
```

## Probar

1. Llamar al endpoint:

   ```bash
   curl http://localhost:3000/
   ```

   Respuesta esperada:

   ```json
   {"message":"Hola desde Express"}
   ```

2. Ver los logs del contenedor:

   ```bash
   docker logs ejemplo-0510-003
   ```

   Debe mostrar: `Servidor corriendo en http://localhost:3000`.

3. Cambiar el puerto con una variable de entorno:

   ```bash
   docker run -d --name ejemplo-0510-003-8080 -e PORT=8080 -p 8080:8080 ejemplo-0510-003
   curl http://localhost:8080/
   ```

4. Verificar que el `.dockerignore` funcionó (no deben aparecer `README.md` ni `Dockerfile`):

   ```bash
   docker run --rm ejemplo-0510-003 ls -a /app
   ```

## Limpiar

```bash
docker rm -f ejemplo-0510-003 ejemplo-0510-003-8080
docker rmi ejemplo-0510-003
```
