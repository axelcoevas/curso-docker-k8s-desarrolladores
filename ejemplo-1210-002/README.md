# Ejemplo 1210-002 — Pod con servidor Express expuesto con un service

Se corre en Kubernetes (minikube) el servidor Express del ejemplo `ejemplo-0510-003`. A diferencia de `ejemplo-1210-001`, este contenedor se queda escuchando en el puerto `3000`. Para poder llegar a él se crea un **service** de tipo `NodePort`.

## Requisitos

- minikube encendido. Comprobar con `minikube status` (ver `ejemplo-0910-001`).
- La imagen `servidor-express:v2` construida a partir del contenido de la carpeta `ejemplo-0510-003`:

  ```bash
  cd ../ejemplo-0510-003
  docker build -t servidor-express:v2 .
  cd ../ejemplo-1210-002
  ```

## El manifiesto del pod: `pod.yml`

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: servidor-express-pod
  labels:
    app: app-express-pod
spec:
  restartPolicy: Always
  containers:
    - name: servidor-express-c
      image: servidor-express:v2
      ports:
        - containerPort: 3000
      imagePullPolicy: IfNotPresent
```

- `metadata.labels`: etiquetas clave-valor del pod. El service usa `app: app-express-pod` para encontrarlo.
- `restartPolicy: Always`: si el contenedor termina, Kubernetes lo vuelve a arrancar (es el valor por defecto).
- `ports.containerPort: 3000`: el puerto en el que escucha Express dentro del contenedor. Es informativo: documenta el puerto, pero no lo publica hacia fuera como `-p` en `docker run`.
- `imagePullPolicy: IfNotPresent`: usa la imagen cargada en minikube en lugar de intentar descargarla.

## El manifiesto del service: `service.yml`

Un pod no es accesible desde fuera del clúster por sí solo. Además, su IP cambia cada vez que se vuelve a crear. Un service le da un punto de acceso estable.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: servidor-express-service
spec:
  selector:
    app: app-express-pod
  type: NodePort
  ports:
    - protocol: TCP
      port: 3000
      targetPort: 3000
```

- `selector`: el service envía el tráfico a los pods que tengan la etiqueta `app: app-express-pod`. Debe coincidir **exactamente** con `metadata.labels` del pod; si no, el service no tiene a dónde mandar el tráfico.
- `type: NodePort`: además de la IP interna del service, Kubernetes abre un puerto en el **nodo** (elegido al azar entre 30000 y 32767).
- `port: 3000`: el puerto del service dentro del clúster.
- `targetPort: 3000`: el puerto del contenedor al que se reenvía el tráfico (el `containerPort` del pod).

```
navegador ──► nodo :32444 (NodePort) ──► service :3000 (port) ──► pod :3000 (targetPort)
```

## Pasos

1. Cargar la imagen en minikube:

   ```bash
   minikube image load servidor-express:v2
   ```

   No muestra salida si todo sale bien. minikube usa su propio runtime de contenedores (containerd) y no ve las imágenes del Docker de la máquina hasta que se cargan.

2. Comprobar que la imagen ya está en minikube:

   ```bash
   minikube image ls
   ```

   Salida esperada (entre otras imágenes del sistema):

   ```
   docker.io/library/servidor-express:v2
   ```

3. Crear el pod:

   ```bash
   kubectl apply -f pod.yml
   ```

   Salida esperada:

   ```
   pod/servidor-express-pod created
   ```

4. Ver que el pod esté corriendo:

   ```bash
   kubectl get pods servidor-express-pod
   ```

   Salida esperada:

   ```
   NAME                   READY   STATUS    RESTARTS   AGE
   servidor-express-pod   1/1     Running   0          53m
   ```

5. Ver los logs del pod:

   ```bash
   kubectl logs servidor-express-pod
   ```

   Salida esperada:

   ```
   Servidor corriendo en http://localhost:3000
   ```

   Ese `localhost` es el **del contenedor**, no el de la máquina. Todavía no se puede entrar desde el navegador.

6. Crear el service:

   ```bash
   kubectl apply -f service.yml
   ```

   Salida esperada:

   ```
   service/servidor-express-service created
   ```

7. Ver el service:

   ```bash
   kubectl get service servidor-express-service
   ```

   Salida esperada:

   ```
   NAME                       TYPE       CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
   servidor-express-service   NodePort   10.100.239.245   <none>        3000:32444/TCP   32m
   ```

   En `3000:32444/TCP`, `3000` es el `port` del service y `32444` el NodePort. El NodePort cambia en cada equipo.

8. Comprobar que el service encontró al pod:

   ```bash
   kubectl get endpointslices -l kubernetes.io/service-name=servidor-express-service
   ```

   Salida esperada:

   ```
   NAME                             ADDRESSTYPE   PORTS   ENDPOINTS    AGE
   servidor-express-service-c27wn   IPv4          3000    10.244.0.5   32m
   ```

   `ENDPOINTS` es la IP del pod. Si aparece `<unset>`, el `selector` del service no coincide con las etiquetas del pod.

## Acceder al servicio

`http://localhost:3000` y `http://localhost:32444` **no funcionan** directamente:

- El NodePort (`32444`) se abre en el **nodo de minikube**, que es un contenedor con su propia IP (`192.168.49.2`), no en la máquina.
- El puerto `3000` del service solo existe dentro del clúster.

Hay que usar alguna de estas opciones. En todas, la respuesta esperada es:

```json
{"message":"Hola desde Express"}
```

### Opción 1: `kubectl port-forward`

```bash
kubectl port-forward service/servidor-express-service 3000:3000
```

Salida esperada:

```
Forwarding from 127.0.0.1:3000 -> 3000
Forwarding from [::1]:3000 -> 3000
```

Abrir **http://localhost:3000** o probar con `curl http://localhost:3000`.

- `3000:3000` es `<puerto local>:<puerto del service>`. Si el `3000` local está ocupado, se usa otro, por ejemplo `8080:3000`, y se entra por `http://localhost:8080`.
- La terminal queda ocupada mientras dura el redireccionamiento. `Ctrl+C` lo cierra.

### Opción 2: `minikube service`

```bash
minikube service servidor-express-service --url
```

Salida esperada (el puerto cambia cada vez):

```
http://127.0.0.1:51110
! Porque estás usando controlador Docker en darwin, la terminal debe abrirse para ejecutarlo.
```

Abrir la URL que aparece. Sin `--url`, minikube abre el navegador directamente. La terminal también queda ocupada hasta `Ctrl+C`.

### Opción 3: IP del nodo + NodePort (solo con OrbStack o en Linux)

```bash
minikube ip
```

Salida esperada:

```
192.168.49.2
```

Abrir `http://<ip del nodo>:<NodePort>`, en este caso **http://192.168.49.2:32444**. Con Docker Desktop no funciona.

### Desde otra computadora (minikube en un servidor Rocky Linux)

La IP `192.168.49.x` solo existe dentro del servidor. Para entrar desde otra computadora hay que abrir el puerto en el firewall y hacer el port-forward escuchando en todas las interfaces:

```bash
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
kubectl port-forward --address 0.0.0.0 service/servidor-express-service 3000:3000
```

Luego, desde la otra computadora, abrir `http://<IP del servidor>:3000`. El detalle y las advertencias están en `ejemplo-0910-001`, sección "Desde otra computadora".

## Limpiar

```bash
kubectl delete -f service.yml
kubectl delete -f pod.yml
minikube image rm servidor-express:v2
```
