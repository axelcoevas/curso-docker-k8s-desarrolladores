# Ejemplo 1410-002 — Ingress NGINX: una sola entrada para Spring y Express

En `ejemplo-1410-001` cada aplicación tiene su propio Service `NodePort`, y para llegar a cada una hace falta un `port-forward` distinto. Un **Ingress** resuelve eso: es una sola puerta de entrada al clúster que reparte las peticiones a distintos Services según la **ruta** de la URL.

En este ejemplo, una sola entrada atiende las dos aplicaciones:

| Ruta        | Va a                                        |
| ----------- | ------------------------------------------- |
| `/spring/*` | `servidor-spring-service` (API de alumnos)  |
| `/express/*` | `servidor-express-service` (servidor Express) |

> Las salidas son de referencia: IDs, IPs, nombres con hash y tiempos cambian en cada equipo.

## Requisitos

- minikube encendido (`minikube status`).
- Las dos aplicaciones de `ejemplo-1410-001` desplegadas, cada una con su imagen cargada en minikube:

  ```bash
  kubectl get deployment servidor-spring-deployment servidor-express-deployment
  kubectl get svc servidor-spring-service servidor-express-service
  ```

  Si falta alguna, ver `ejemplo-1410-001` (`spring-deployment.yaml` y `express-deployment.yaml`). La de Spring además necesita SQL Server de `ejemplo-1210-005` corriendo.

## El Ingress controller y el namespace `ingress-nginx`

Un recurso `Ingress` **solo son reglas**: por sí solo no hace nada. Quien las ejecuta es un **Ingress controller**, un servidor NGINX que lee las reglas y reparte el tráfico. Kubernetes no lo trae instalado; en minikube se activa como addon:

```bash
minikube addons enable ingress
```

El addon crea su propio **namespace**, `ingress-nginx`, y deja ahí todas sus piezas, separadas de las aplicaciones (que están en `default`). Comprobarlo:

```bash
kubectl get ns
kubectl get pods,svc -n ingress-nginx
kubectl get ingressclass
```

Salida esperada (fragmentos):

```
NAME              STATUS   AGE
default           Active   39d
ingress-nginx     Active   27m
kube-system       Active   39d

NAME                                           READY   STATUS      RESTARTS      AGE
pod/ingress-nginx-admission-create-blj4x       0/1     Completed   0             27m
pod/ingress-nginx-admission-patch-nqh6v        0/1     Completed   1 (27m ago)   27m
pod/ingress-nginx-controller-d7cd8c989-lp8vd   1/1     Running     0             27m

NAME                                         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                      AGE
service/ingress-nginx-controller             NodePort    10.100.130.45   <none>        80:32514/TCP,443:32552/TCP   27m
service/ingress-nginx-controller-admission   ClusterIP   10.111.0.149    <none>        443/TCP                      27m

NAME    CONTROLLER             PARAMETERS   AGE
nginx   k8s.io/ingress-nginx   <none>       27m
```

- `ingress-nginx-controller` (pod): el NGINX que atiende las peticiones. Debe estar `1/1 Running`.
- `ingress-nginx-controller` (service): la entrada al controlador, en los puertos 80 (HTTP) y 443 (HTTPS). **A este Service es al que se le abre el `port-forward`**, no a los de las aplicaciones.
- `ingress-nginx-admission-create` / `-patch`: Jobs que se ejecutan una sola vez al instalar el addon (preparan el certificado del webhook de validación). Quedan en `Completed` y es lo normal.
- `IngressClass` `nginx`: el nombre con el que un Ingress dice **qué controlador** lo debe atender. Los Ingress de este ejemplo lo piden con `ingressClassName: nginx`.

## El manifiesto: `ingress.yaml`

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: servidor-ingress
  namespace: default
  annotations:
    nginx.ingress.kubernetes.io/use-regex: "true"
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /spring(/|$)(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: servidor-spring-service
                port:
                  number: 8081
          - path: /express(/|$)(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: servidor-express-service
                port:
                  number: 3000
```

- `ingressClassName: nginx`: este Ingress lo atiende el controlador NGINX del namespace `ingress-nginx`. Sin esta línea, o con otro nombre, ningún controlador lo toma y no pasa nada.
- `namespace: default`: el Ingress debe estar en el **mismo namespace que los Services** a los que apunta. Aunque el controlador viva en `ingress-nginx`, las reglas se escriben junto a las aplicaciones, y un Ingress no puede apuntar a Services de otro namespace.
- `rules.http.paths`: una entrada por ruta. Cada una manda a un `backend.service` con su `name` y el `port.number` del **Service** (no del contenedor ni del Deployment): `8081` para Spring y `3000` para Express, los mismos puertos de sus Services.
- `rewrite-target: /$2` y `use-regex: "true"`: **quitan el prefijo** antes de llegar a la aplicación. Las aplicaciones no saben que existen `/spring` ni `/express`: Express responde en `/`, `/hostname` y `/v4`, y Spring en `/alumnos`. `$2` es el segundo grupo entre paréntesis del `path` (`(.*)`, lo que sigue al prefijo).
- `path: /express(/|$)(.*)`: el grupo `(/|$)` acepta una `/` o el fin de la ruta, para que `/express` y `/express/` funcionen igual. `pathType: ImplementationSpecific` es el que permite usar expresiones regulares.

La traducción que hace NGINX:

| Petición                    | Servicio                   | Lo que recibe la aplicación |
| --------------------------- | -------------------------- | --------------------------- |
| `/express/`                 | `servidor-express-service` | `/`                         |
| `/express/hostname`         | `servidor-express-service` | `/hostname`                 |
| `/express/v4`               | `servidor-express-service` | `/v4`                       |
| `/spring/alumnos`           | `servidor-spring-service`  | `/alumnos`                  |
| `/cualquier-otra-ruta`      | ninguno                    | NGINX responde 404          |

## Pasos

### 1. Crear el Ingress

```bash
kubectl apply -f ingress.yaml
```

Salida esperada:

```
ingress.networking.k8s.io/servidor-ingress created
```

Ver que el controlador lo tomó:

```bash
kubectl get ingress
kubectl describe ingress servidor-ingress
```

Salida esperada (fragmento):

```
NAME               CLASS   HOSTS   ADDRESS        PORTS   AGE
servidor-ingress   nginx   *       192.168.49.2   80      23m

Rules:
  Host        Path  Backends
  ----        ----  --------
  *
              /spring(/|$)(.*)    servidor-spring-service:8081 (10.244.0.5:8081)
              /express(/|$)(.*)   servidor-express-service:3000 (10.244.0.6:3000)
```

- `ADDRESS`: lo llena el controlador cuando toma el Ingress. Si queda vacío después de un minuto, el addon no está listo (ver "Problemas comunes").
- Entre paréntesis aparece la IP del **pod** al que apunta cada Service. Si en lugar de una IP dice `<error: endpoints ... not found>` o `<none>`, el Service no tiene pods detrás.
- `HOSTS` con `*` significa que las reglas aplican a cualquier nombre de host.

### 2. Abrir el túnel hacia el controlador

Con el driver de Docker en Mac, la IP de `minikube ip` no es alcanzable desde la Mac, así que el NodePort del controlador no sirve directamente. Se abre un túnel al Service **del controlador**, que está en el namespace `ingress-nginx`:

```bash
kubectl port-forward service/ingress-nginx-controller -n ingress-nginx 7777:80
```

Salida esperada:

```
Forwarding from 127.0.0.1:7777 -> 80
Forwarding from [::1]:7777 -> 80
```

- `-n ingress-nginx`: el namespace donde vive ese Service. Sin este parámetro, `kubectl` lo busca en `default` y responde `services "ingress-nginx-controller" not found`.
- `7777:80`: el puerto `7777` de la Mac (arbitrario; sirve cualquiera libre) se conecta al puerto `80` del controlador.
- **Un solo túnel sirve para las dos aplicaciones**, porque todo entra por el controlador y es él quien reparte según la ruta. Ya no hace falta un `port-forward` por cada Service.

Esa terminal debe quedarse abierta. (Otra opción, según la documentación de minikube para el addon `ingress` con el driver Docker: `minikube tunnel` en una terminal aparte, que deja el Ingress en `http://127.0.0.1` sin indicar puerto. No se probó en este ejemplo.)

### 3. Probar las rutas

En otra terminal:

1. Express:

   ```bash
   curl http://localhost:7777/express/
   curl http://localhost:7777/express/hostname
   curl http://localhost:7777/express/v4
   ```

   Salida esperada:

   ```json
   {"message":"Hola desde Express"}
   ```

   ```
   Nombre del host: servidor-express-deployment-99867c65b-x8xgs
   ```

   ```json
   {"message":"Bienvenido a la version 4 del API, vamos a probar un rollback con el deployment"}
   ```

   `/express/hostname` responde con el nombre del pod, así que cambia en cada equipo. Con `replicas: 1` siempre responde el mismo; si se escala el Deployment (`kubectl scale deployment servidor-express-deployment --replicas=3`), el nombre va cambiando entre peticiones porque el Service reparte la carga entre los pods.

2. Spring (lee de SQL Server):

   ```bash
   curl http://localhost:7777/spring/alumnos
   ```

   Salida esperada (si se corrió `migration_001.sql`):

   ```json
   [{"id":1,"nombre":"Andrea"},{"id":2,"nombre":"Alexis"},{"id":3,"nombre":"Asher"},{"id":4,"nombre":"Axel"},{"id":5,"nombre":"Fernando"},{"id":6,"nombre":"Jorge"},{"id":7,"nombre":"Levi"},{"id":8,"nombre":"Humberto"}]
   ```

3. Una ruta sin regla:

   ```bash
   curl -i http://localhost:7777/otra
   ```

   Salida esperada (fragmento):

   ```
   HTTP/1.1 404 Not Found
   ...
   <html>
   <head><title>404 Not Found</title></head>
   <body>
   <center><h1>404 Not Found</h1></center>
   <hr><center>nginx</center>
   </body>
   ```

   El `404` lo responde el propio NGINX del controlador, no ninguna de las aplicaciones: ninguna regla del Ingress coincide con esa ruta.

## Problemas comunes

| Síntoma                                                                          | Causa                                                                          | Solución                                                                                                                       |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| `services "ingress-nginx-controller" not found` al abrir el `port-forward`        | Falta `-n ingress-nginx`, o el addon no está habilitado                        | Agregar `-n ingress-nginx` y revisar `minikube addons list \| grep ingress`.                                                    |
| `kubectl get ingress` muestra `ADDRESS` vacío                                     | El controlador aún no está listo, o falta `ingressClassName: nginx`            | `kubectl get pods -n ingress-nginx` (el controlador debe estar `1/1 Running`) y revisar `ingressClassName`.                    |
| `404 Not Found` de nginx en una ruta que sí debería existir                       | La ruta no coincide con ningún `path`, o falta `/` (por ejemplo `/express`)    | Usar el prefijo exacto: `/spring/...` o `/express/...`. Revisar con `kubectl describe ingress servidor-ingress`.               |
| La aplicación responde su propio 404 (`Cannot GET /express/hostname`)             | El `rewrite-target` no se aplicó y el prefijo llegó a la app                   | Revisar que `use-regex: "true"` y `rewrite-target: /$2` estén en `annotations`, y que el `path` tenga los dos grupos `(...)`.   |
| `503 Service Temporarily Unavailable`                                             | El Service existe pero no tiene pods listos (el `selector` no coincide o el pod no está `Running`) | `kubectl get endpoints servidor-express-service`. Si `ENDPOINTS` dice `<none>`, revisar el `selector` y el estado de los pods. |
| `describe ingress` muestra `<error: endpoints "..." not found>`                   | El Service no existe en el namespace del Ingress                               | El Ingress y los Services deben estar en el mismo namespace (`default`). Crear el Service o corregir el `name`.                |
| `curl` responde `Connection refused` o `Empty reply`                              | El `port-forward` no está abierto o se cerró                                   | Volver a lanzarlo y dejar la terminal abierta.                                                                                  |
| `Unable to listen on port 7777 ... address already in use`                        | Otro programa usa ese puerto                                                   | Usar otro puerto local: `kubectl port-forward ... 8888:80` y `curl http://localhost:8888/...`.                                  |

## Limpiar

```bash
kubectl delete -f ingress.yaml
```

Borra solo las reglas. El Ingress controller y el namespace `ingress-nginx` se quedan, y las aplicaciones de `ejemplo-1410-001` siguen corriendo con sus Services.

Para quitar también el controlador: `minikube addons disable ingress`. Eso elimina el namespace `ingress-nginx` con todo lo que tiene.

Las salidas de `kubectl get ns`, `get pods,svc -n ingress-nginx`, `get ingress`, `describe ingress` y las respuestas de `/express/...`, `/spring/alumnos` y del `404` se tomaron del clúster de este equipo (las respuestas, ejecutando `curl` dentro del pod del controlador). Las de `minikube addons enable`, `kubectl apply`, el `port-forward` y `delete` son las que da normalmente `kubectl`.
