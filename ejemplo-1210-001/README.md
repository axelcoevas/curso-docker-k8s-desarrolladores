# Ejemplo 1210-001 — Primer pod con una imagen local

Se corre en Kubernetes (minikube) la imagen `hola-mundo:v1` que se construyó en el ejemplo `ejemplo-0510-001`. En lugar de `docker run`, el contenedor se describe en un manifiesto (`pod.yml`) y se crea con `kubectl apply`.

## Requisitos

- minikube encendido. Comprobar con `minikube status` (ver `ejemplo-0910-001`).
- La imagen `hola-mundo:v1` construida a partir del contenido de la carpeta `ejemplo-0510-001`:

  ```bash
  cd ../ejemplo-0510-001
  docker build -t hola-mundo:v1 .
  cd ../ejemplo-1210-001
  ```

## El manifiesto `pod.yml`

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: hola-mundo-pod
spec:
  containers:
    - name: hola-mundo-c
      image: hola-mundo:v1
      imagePullPolicy: IfNotPresent
```

- `apiVersion` y `kind`: el tipo de recurso que se crea, un `Pod` de la API `v1`.
- `metadata.name`: el nombre del pod. Solo admite minúsculas, números y `-`. El `_` no es válido, por eso aquí se usa `-pod` y `-c` en lugar del sufijo `_c` de Docker.
- `spec.containers`: es una **lista** (cada contenedor empieza con `-`). Un pod puede tener varios contenedores.
- `image`: la imagen que se usa, con su etiqueta `:v1`.
- `imagePullPolicy: IfNotPresent`: usa la imagen si ya está en el nodo y solo la descarga si no está. Como `hola-mundo:v1` no existe en ningún registro, sin esto Kubernetes podría intentar descargarla y fallar con `ErrImagePull`. El valor distingue mayúsculas: `IfNotPresent`, no `ifNotPresent`.

## Pasos

1. Cargar la imagen en minikube:

   ```bash
   minikube image load hola-mundo:v1
   ```

   No muestra salida si todo sale bien. minikube corre su propio runtime de contenedores (containerd), separado del Docker de la máquina, así que no ve las imágenes construidas con `docker build` hasta que se cargan con este comando.

2. Comprobar que la imagen ya está en minikube:

   ```bash
   minikube image ls
   ```

   Salida esperada (entre otras imágenes del sistema):

   ```
   docker.io/library/hola-mundo:v1
   ```

   minikube le agrega el prefijo `docker.io/library/` porque es el registro por defecto cuando el nombre de la imagen no indica otro.

3. Crear el pod:

   ```bash
   kubectl apply -f pod.yml
   ```

   Salida esperada:

   ```
   pod/hola-mundo-pod created
   ```

4. Ver el estado del pod:

   ```bash
   kubectl get pods
   ```

   Salida esperada (el estado y los reinicios cambian según el momento en que se consulte):

   ```
   NAME             READY   STATUS      RESTARTS        AGE
   hola-mundo-pod   0/1     Completed   14 (6m19s ago)  48m
   ```

   El script `holaMundo.sh` imprime dos líneas y termina. Un pod reinicia por defecto sus contenedores cuando terminan (`restartPolicy: Always`), así que Kubernetes lo vuelve a correr una y otra vez. Por eso `RESTARTS` sube y `STATUS` alterna entre `Completed`, `Running` y `CrashLoopBackOff` (Kubernetes espera cada vez más tiempo entre reinicios). Es lo normal para un contenedor que no se queda corriendo, no un error del manifiesto.

5. Ver los logs del pod:

   ```bash
   kubectl logs -f hola-mundo-pod
   ```

   Salida esperada:

   ```
   Hola mundo
   Parametro: Hola Docker
   ```

   Es la misma salida que da `docker run` en `ejemplo-0510-001`. `-f` sigue los logs en vivo (`Ctrl+C` para salir); como el contenedor termina enseguida, el comando también termina solo.

## Si algo falla

Revisar los eventos del pod (al final de la salida, en la sección **Events**):

```bash
kubectl describe pod hola-mundo-pod
```

Salida esperada (últimas líneas):

```
Events:
  Type     Reason     Age                   From               Message
  ----     ------     ----                  ----               -------
  Normal   Scheduled  48m                   default-scheduler  Successfully assigned default/hola-mundo-pod to minikube
  Normal   Started    32m (x9 over 48m)     kubelet            Container started
  Normal   Created    26m (x10 over 48m)    kubelet            Container created
  Warning  BackOff    2m39s (x42 over 48m)  kubelet            Back-off restarting failed container hola-mundo-c in pod ...
  Normal   Pulled     72s (x15 over 48m)    kubelet            Container image "hola-mundo:v1" already present on machine ...
```

- `already present on machine`: la imagen se tomó de minikube, sin descargarla.
- `Back-off restarting`: los reinicios del paso 4.
- Si aparece `ErrImagePull` o `ImagePullBackOff`, falta el paso 1 (`minikube image load`).

## Limpiar

```bash
kubectl delete -f pod.yml
minikube image rm hola-mundo:v1
```
