# Ejemplo 1210-004 — Deployment: actualizar y regresar de versión (rollout)

En `ejemplo-1210-003` se vio que un ReplicaSet **no actualiza** los pods que ya existen al cambiar la imagen de su plantilla: hay que borrarlos a mano. Un **Deployment** resuelve eso. Administra ReplicaSets por debajo y, al cambiar la imagen, reemplaza los pods poco a poco sin dejar el servicio caído (*rolling update*). Además guarda un historial de versiones para poder regresar a una anterior (*rollback*).

En este ejemplo se despliega el servidor Express en la versión `v3`, se actualiza a `v4` y se regresa a `v3`.

## Requisitos

- minikube encendido. Comprobar con `minikube status` (ver `ejemplo-0910-001`).
- Las imágenes `servidor-express:v3` y `servidor-express:v4`, construidas a partir del contenido de la carpeta `ejemplo-0510-003`.

## Preparar las imágenes

Cada versión agrega una ruta nueva a `ejemplo-0510-003/servidor.js`, para poder distinguir qué versión responde.

1. **`v3`**: agregar la ruta `/hostname`, que responde con el nombre del pod:

   ```javascript
   app.get("/hostname", (req, res) => {
       res.send(`Nombre del host: ${process.env.HOSTNAME}`);
   })
   ```

   Construir y cargar en minikube:

   ```bash
   cd ../ejemplo-0510-003
   docker build -t servidor-express:v3 .
   minikube image load servidor-express:v3
   ```

2. **`v4`**: agregar además la ruta `/v4`:

   ```javascript
   app.get("/v4", (req, res) => {
       res.json({
           message: "Bienvenido a la version 4 del API, vamos a probar un rollback con el deployment"
       })
   })
   ```

   Construir y cargar en minikube:

   ```bash
   docker build -t servidor-express:v4 .
   minikube image load servidor-express:v4
   cd ../ejemplo-1210-004
   ```

3. Comprobar que las dos versiones están en minikube:

   ```bash
   minikube image ls | grep servidor-express
   ```

   Salida esperada:

   ```
   docker.io/library/servidor-express:v4
   docker.io/library/servidor-express:v3
   docker.io/library/servidor-express:v2
   ```

Cada cambio de código lleva una etiqueta nueva. Si se reconstruye con la misma etiqueta, Kubernetes no detecta ningún cambio en el Deployment y no actualiza los pods.

## El manifiesto: `deployment-and-service.yaml`

El Deployment y el service van en el mismo archivo, separados por `---`. `kubectl apply -f` crea los dos.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: servidor-express-deployment
  annotations:
    kubernetes.io/change-cause: "Se agregó el endpoint /hostname"
  labels:
    app: app-express-deployment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: app-express-deployment
  template:
    metadata:
      labels:
        app: app-express-deployment
    spec:
      restartPolicy: Always
      containers:
        - name: servidor-express-c
          image: servidor-express:v3
          ports:
            - containerPort: 3000
          imagePullPolicy: Never
---
apiVersion: v1
kind: Service
metadata:
  name: servidor-express-deployment-service
spec:
  selector:
    app: app-express-deployment
  type: NodePort
  ports:
    - protocol: TCP
      port: 3000
      targetPort: 3000
```

- `kind: Deployment`: el resto de `spec` (`replicas`, `selector`, `template`) es igual que en el ReplicaSet de `ejemplo-1210-003`. El Deployment crea un ReplicaSet con esa plantilla, y ese ReplicaSet crea los pods.
- `annotations.kubernetes.io/change-cause`: una descripción del cambio. Es lo que aparece en la columna `CHANGE-CAUSE` de `kubectl rollout history`. Se actualiza en cada versión; si no se pone, la columna sale como `<none>`.
- `imagePullPolicy: Never`: usa **solo** la imagen que ya está en minikube y nunca intenta descargarla. Si falta el `minikube image load`, el pod queda en `ErrImageNeverPull`.
- El service es igual al de los ejemplos anteriores, con el `selector` apuntando a la etiqueta de la plantilla.

## Pasos

### 1. Desplegar la versión `v3`

1. Con `image: servidor-express:v3` y `change-cause: "Se agregó el endpoint /hostname"` en el manifiesto, crear el Deployment y el service:

   ```bash
   kubectl apply -f deployment-and-service.yaml
   ```

   Salida esperada:

   ```
   deployment.apps/servidor-express-deployment created
   service/servidor-express-deployment-service created
   ```

2. Esperar a que termine el despliegue:

   ```bash
   kubectl rollout status deployment/servidor-express-deployment
   ```

   Salida esperada:

   ```
   deployment "servidor-express-deployment" successfully rolled out
   ```

3. Ver el Deployment, el ReplicaSet que creó y el service:

   ```bash
   kubectl get deployment servidor-express-deployment
   kubectl get replicasets -l app=app-express-deployment
   kubectl get service servidor-express-deployment-service
   ```

   Salida esperada:

   ```
   NAME                          READY   UP-TO-DATE   AVAILABLE   AGE
   servidor-express-deployment   3/3     3            3           55m

   NAME                                     DESIRED   CURRENT   READY   AGE
   servidor-express-deployment-66d6cdcfb5   3         3         3       55m

   NAME                                  TYPE       CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
   servidor-express-deployment-service   NodePort   10.104.139.198   <none>        3000:30805/TCP   55m
   ```

   - `UP-TO-DATE`: los pods que ya tienen la plantilla actual.
   - El nombre del ReplicaSet es el del Deployment más un *hash* de la plantilla (`66d6cdcfb5`). Cada versión de la plantilla genera un ReplicaSet distinto.
   - El NodePort (`30805`) cambia en cada equipo.

4. Probar la ruta `/hostname` por la IP del nodo (ver "Acceder al servicio" en `ejemplo-1210-003`):

   ```bash
   curl http://$(minikube ip):30805/hostname
   ```

   Salida esperada (cambia entre los pods en cada petición):

   ```
   Nombre del host: servidor-express-deployment-66d6cdcfb5-67cgg
   ```

### 2. Actualizar a la versión `v4`

1. Editar `deployment-and-service.yaml` y cambiar la imagen y la causa del cambio:

   ```yaml
     annotations:
       kubernetes.io/change-cause: "Se agregaron nuevos endpoints"
   ```

   ```yaml
             image: servidor-express:v4
   ```

2. Aplicar el cambio y seguir la actualización:

   ```bash
   kubectl apply -f deployment-and-service.yaml
   kubectl rollout status deployment/servidor-express-deployment
   ```

   Salida esperada:

   ```
   deployment.apps/servidor-express-deployment configured
   service/servidor-express-deployment-service unchanged
   Waiting for deployment "servidor-express-deployment" rollout to finish: 1 out of 3 new replicas have been updated...
   Waiting for deployment "servidor-express-deployment" rollout to finish: 2 out of 3 new replicas have been updated...
   Waiting for deployment "servidor-express-deployment" rollout to finish: 1 old replicas are pending termination...
   deployment "servidor-express-deployment" successfully rolled out
   ```

   A diferencia del ReplicaSet, **no hay que borrar los pods**: el Deployment los reemplaza solo.

3. Ver los ReplicaSets:

   ```bash
   kubectl get replicasets -l app=app-express-deployment
   ```

   Salida esperada:

   ```
   NAME                                     DESIRED   CURRENT   READY   AGE
   servidor-express-deployment-66d6cdcfb5   0         0         0       30m
   servidor-express-deployment-67c6b76869   3         3         3       1m
   ```

   El Deployment creó un ReplicaSet nuevo (`67c6b76869`, con `v4`) y bajó el anterior a 0. El ReplicaSet viejo no se borra: se conserva para poder regresar a esa versión.

4. Probar la ruta nueva:

   ```bash
   curl http://$(minikube ip):30805/v4
   ```

   Salida esperada:

   ```json
   {"message":"Bienvenido a la version 4 del API, vamos a probar un rollback con el deployment"}
   ```

#### Cómo reemplaza los pods (rolling update)

```bash
kubectl describe deployment servidor-express-deployment
```

Salida esperada (fragmento):

```
StrategyType:           RollingUpdate
RollingUpdateStrategy:  25% max unavailable, 25% max surge
...
Events:
  Type    Reason             Age   From                   Message
  ----    ------             ----  ----                   -------
  Normal  ScalingReplicaSet  25m   deployment-controller  Scaled up replica set servidor-express-deployment-67c6b76869 from 0 to 1
  Normal  ScalingReplicaSet  25m   deployment-controller  Scaled down replica set servidor-express-deployment-66d6cdcfb5 from 3 to 2
  Normal  ScalingReplicaSet  25m   deployment-controller  Scaled up replica set servidor-express-deployment-67c6b76869 from 1 to 2
  Normal  ScalingReplicaSet  25m   deployment-controller  Scaled down replica set servidor-express-deployment-66d6cdcfb5 from 2 to 1
  Normal  ScalingReplicaSet  25m   deployment-controller  Scaled up replica set servidor-express-deployment-67c6b76869 from 2 to 3
  Normal  ScalingReplicaSet  25m   deployment-controller  Scaled down replica set servidor-express-deployment-66d6cdcfb5 from 1 to 0
```

El Deployment sube un pod de la versión nueva, baja uno de la vieja, y repite hasta terminar. Siempre hay pods respondiendo, así que el servicio no se cae durante la actualización.

- `max surge 25%`: cuántos pods **de más** puede haber durante la actualización (25% de 3, redondeado hacia arriba = 1, o sea hasta 4 pods).
- `max unavailable 25%`: cuántos pods pueden faltar (25% de 3, redondeado hacia abajo = 0, o sea nunca menos de 3 listos).

### 3. Ver el historial y regresar a `v3` (rollback)

1. Ver el historial de versiones:

   ```bash
   kubectl rollout history deployment/servidor-express-deployment
   ```

   Salida esperada:

   ```
   deployment.apps/servidor-express-deployment
   REVISION  CHANGE-CAUSE
   1         Se agregó el endpoint /hostname
   2         Se agregaron nuevos endpoints
   ```

   Cada revisión corresponde a una versión de la plantilla (un ReplicaSet). `CHANGE-CAUSE` es la anotación `kubernetes.io/change-cause`.

2. Ver el detalle de una revisión:

   ```bash
   kubectl rollout history deployment/servidor-express-deployment --revision=2
   ```

   Muestra la plantilla de esa revisión, incluyendo `Image: servidor-express:v4`.

3. Regresar a la revisión anterior:

   ```bash
   kubectl rollout undo deployment/servidor-express-deployment
   ```

   Salida esperada:

   ```
   deployment.apps/servidor-express-deployment rolled back
   ```

   Para regresar a una revisión específica: `kubectl rollout undo deployment/servidor-express-deployment --to-revision=1`.

   El rollback también es un rolling update: el Deployment vuelve a escalar el ReplicaSet de `v3` (`66d6cdcfb5`) de 0 a 3 y baja el de `v4` a 0. No crea un ReplicaSet nuevo.

4. Volver a ver el historial:

   ```bash
   kubectl rollout history deployment/servidor-express-deployment
   ```

   Salida esperada:

   ```
   deployment.apps/servidor-express-deployment
   REVISION  CHANGE-CAUSE
   2         Se agregaron nuevos endpoints
   3         Se agregó el endpoint /hostname
   ```

   La revisión 1 desaparece y su plantilla pasa a ser la revisión 3: un rollback no regresa el número, crea una revisión nueva con el contenido de la anterior.

5. Comprobar que responde `v3`:

   ```bash
   kubectl get deployment servidor-express-deployment -o wide
   curl http://$(minikube ip):30805/hostname
   curl http://$(minikube ip):30805/v4
   ```

   Salida esperada:

   ```
   NAME                          READY   UP-TO-DATE   AVAILABLE   AGE   CONTAINERS           IMAGES                SELECTOR
   servidor-express-deployment   3/3     3            3           55m   servidor-express-c   servidor-express:v3   app=app-express-deployment

   Nombre del host: servidor-express-deployment-66d6cdcfb5-67cgg

   <pre>Cannot GET /v4</pre>
   ```

   `/hostname` responde, pero `/v4` ya no existe: los pods vuelven a correr `v3`.

> `kubectl rollout undo` cambia el clúster, **no el archivo**. `deployment-and-service.yaml` sigue diciendo `servidor-express:v4`, y un nuevo `kubectl apply -f` volvería a desplegar `v4`. Para quedarse en `v3`, regresar también la imagen y el `change-cause` en el archivo.

## Limpiar

```bash
kubectl delete -f deployment-and-service.yaml
```

Borra el Deployment (con sus ReplicaSets y pods) y el service.

Las salidas de `kubectl apply`, de `rollout status` durante la actualización, de los ReplicaSets y `/v4` con `v4` activa, del historial antes del rollback, de `rollout undo` y de "Limpiar" no se ejecutaron en este equipo; son las que da normalmente `kubectl`. Las demás se tomaron del clúster después del rollback.
