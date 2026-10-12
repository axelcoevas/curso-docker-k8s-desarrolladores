# Ejemplo 1210-003 — ReplicaSet con servidor Express

En `ejemplo-1210-002` se creó un pod suelto. Si ese pod se borra o su nodo falla, nadie lo vuelve a crear. Un **ReplicaSet** se encarga de que siempre exista un número fijo de pods iguales (réplicas): si sobra uno, lo borra; si falta uno, crea otro. Aquí se corren 3 réplicas del servidor Express y se exponen con un service que reparte el tráfico entre ellas.

## Requisitos

- minikube encendido. Comprobar con `minikube status` (ver `ejemplo-0910-001`).
- La imagen `servidor-express:v2` construida a partir del contenido de la carpeta `ejemplo-0510-003` y cargada en minikube (ver `ejemplo-1210-002`):

  ```bash
  cd ../ejemplo-0510-003
  docker build -t servidor-express:v2 .
  cd ../ejemplo-1210-003
  minikube image load servidor-express:v2
  ```

## El manifiesto del ReplicaSet: `replica-set.yaml`

```yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: servidor-express-replica-set
  labels:
    app: app-express-replica-set
spec:
  replicas: 3
  selector:
    matchLabels:
      app: app-express-replica-set
  template:
    metadata:
      labels:
        app: app-express-replica-set
    spec:
      restartPolicy: Always
      containers:
        - name: servidor-express-c
          image: servidor-express:v2
          ports:
            - containerPort: 3000
          imagePullPolicy: IfNotPresent
```

- `apiVersion: apps/v1`: el ReplicaSet no está en la API `v1` como el `Pod` y el `Service`, sino en el grupo `apps`.
- `metadata.labels`: etiquetas del **propio ReplicaSet**. Sirven para buscarlo (por ejemplo, `kubectl get rs -l app=app-express-replica-set`), pero no intervienen en qué pods controla.
- `replicas: 3`: el número de pods que debe haber. El ReplicaSet revisa el clúster continuamente y crea o borra pods hasta que haya exactamente 3.
- `selector.matchLabels`: cómo el ReplicaSet **encuentra sus pods**. Cuenta como suyos todos los pods que tengan la etiqueta `app: app-express-replica-set`.
- `template`: la plantilla con la que crea cada pod. Su contenido es el mismo que el de un `pod.yml` (`metadata` y `spec`), pero sin `apiVersion`, `kind` ni `name`: el ReplicaSet le pone a cada pod su propio nombre, `servidor-express-replica-set-<sufijo al azar>`.
- `template.metadata.labels`: las etiquetas que llevan los pods creados. Cumplen dos funciones:
  - Asocian los pods al ReplicaSet. Deben coincidir con `selector.matchLabels`; si no, Kubernetes rechaza el manifiesto.
  - Permiten que el service encuentre los pods con su `selector`.

## El manifiesto del service: `service.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: servidor-express-replica-service
spec:
  selector:
    app: app-express-replica-set
  type: NodePort
  ports:
    - protocol: TCP
      port: 3000
      targetPort: 3000
```

Es igual al de `ejemplo-1210-002`, salvo el `selector`: ahora apunta a la etiqueta de la plantilla del ReplicaSet. Como los 3 pods la tienen, el service **reparte las peticiones entre los 3**.

```
                                                         ┌─► pod ...-jx5xn :3000
navegador ──► nodo :32485 (NodePort) ──► service :3000 ──┼─► pod ...-k9lcd :3000
                                                         └─► pod ...-rrzf6 :3000
```

## Pasos

1. Crear el ReplicaSet:

   ```bash
   kubectl apply -f replica-set.yaml
   ```

   Salida esperada:

   ```
   replicaset.apps/servidor-express-replica-set created
   ```

2. Ver el ReplicaSet:

   ```bash
   kubectl get replicasets servidor-express-replica-set
   ```

   Salida esperada:

   ```
   NAME                           DESIRED   CURRENT   READY   AGE
   servidor-express-replica-set   3         3         3       14m
   ```

   - `DESIRED`: las réplicas pedidas en `replicas`.
   - `CURRENT`: los pods que existen.
   - `READY`: los pods listos para recibir tráfico.

3. Ver los pods que creó:

   ```bash
   kubectl get pods -l app=app-express-replica-set -o wide
   ```

   Salida esperada (los sufijos de los nombres y las IP cambian en cada equipo):

   ```
   NAME                                 READY   STATUS    RESTARTS   AGE   IP           NODE
   servidor-express-replica-set-jx5xn   1/1     Running   0          14m   10.244.0.6   minikube
   servidor-express-replica-set-k9lcd   1/1     Running   0          13m   10.244.0.9   minikube
   servidor-express-replica-set-rrzf6   1/1     Running   0          14m   10.244.0.8   minikube
   ```

   `-l app=app-express-replica-set` filtra por etiqueta, igual que hace el `selector`. Cada pod tiene su propia IP.

4. Crear el service:

   ```bash
   kubectl apply -f service.yaml
   ```

   Salida esperada:

   ```
   service/servidor-express-replica-service created
   ```

5. Ver el service:

   ```bash
   kubectl get service servidor-express-replica-service
   ```

   Salida esperada:

   ```
   NAME                               TYPE       CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
   servidor-express-replica-service   NodePort   10.110.233.148   <none>        3000:32485/TCP   11m
   ```

6. Comprobar que el service encontró los 3 pods:

   ```bash
   kubectl get endpointslices -l kubernetes.io/service-name=servidor-express-replica-service
   ```

   Salida esperada:

   ```
   NAME                                     ADDRESSTYPE   PORTS   ENDPOINTS                          AGE
   servidor-express-replica-service-qfqhd   IPv4          3000    10.244.0.8,10.244.0.6,10.244.0.9   11m
   ```

   `ENDPOINTS` muestra las IP de los 3 pods del paso 3.

## Acceder al servicio

Igual que en `ejemplo-1210-002`, `localhost:3000` y `localhost:32485` no funcionan directamente. Se usa alguna de estas opciones:

- **`kubectl port-forward`**:

  ```bash
  kubectl port-forward service/servidor-express-replica-service 3000:3000
  ```

  Abrir **http://localhost:3000**. Ojo: `port-forward` elige **un solo pod** al arrancar y le manda todas las peticiones; no reparte el tráfico entre las réplicas. Sirve para probar, no para ver el balanceo.

- **`minikube service`**:

  ```bash
  minikube service servidor-express-replica-service --url
  ```

- **IP del nodo + NodePort** (solo con OrbStack o en Linux): `http://<minikube ip>:32485`, en este caso **http://192.168.49.2:32485**. Esta opción sí pasa por el service y reparte las peticiones entre los 3 pods.

En todas, la respuesta esperada es:

```json
{"message":"Hola desde Express"}
```

## Limpiar

```bash
kubectl delete -f service.yaml
kubectl delete -f replica-set.yaml
```

Al borrar el ReplicaSet, Kubernetes borra también sus pods.

Las salidas de `kubectl apply` y de "Limpiar" no se ejecutaron en este equipo; son las que da normalmente `kubectl`. Los nombres de los pods, las IP y el NodePort cambian cada vez que se crean los recursos.
