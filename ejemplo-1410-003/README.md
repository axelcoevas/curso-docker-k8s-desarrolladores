# Ejemplo 1410-003 — Namespaces: separar ambientes en un mismo clúster

Hasta ahora todo se creó en el namespace `default`. Un **namespace** divide un clúster en "carpetas" aisladas: los objetos de un namespace (pods, services, deployments, secrets...) no se mezclan con los de otro, y se puede repetir el mismo nombre en cada uno.

En este ejemplo se crean dos namespaces, `namespace-desarrollo` y `namespace-operaciones`, y en cada uno **un pod de nginx con exactamente el mismo nombre** (`servidor-ngnix`) y su propio Service. Se comprueba que no se estorban.

> Las salidas son de referencia: IDs, IPs, nombres con hash y tiempos cambian en cada equipo.

## Requisitos

- minikube encendido (`minikube status`).

## Los manifiestos

### `namespaces.yaml`

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: namespace-desarrollo
  labels:
    environment: dev
---
apiVersion: v1
kind: Namespace
metadata:
  name: namespace-operaciones
  labels:
    environment: ops
```

- `kind: Namespace`: el namespace es un objeto más de Kubernetes y no pertenece a ningún otro namespace.
- `labels`: etiquetas que describen el ambiente (`dev` y `ops`). Son opcionales y sirven para filtrar, por ejemplo `kubectl get ns -l environment=dev`. Kubernetes agrega solo la etiqueta `kubernetes.io/metadata.name` con el nombre del namespace.

### `services-namespaces.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: desarrollo-nginx-service
  namespace: namespace-desarrollo
spec:
  selector:
    run: servidor-ngnix
  type: NodePort
  ports:
    - protocol: TCP
      port: 80
      targetPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: operaciones-nginx-service
  namespace: namespace-operaciones
spec:
  selector:
    run: servidor-ngnix
  type: NodePort
  ports:
    - protocol: TCP
      port: 80
      targetPort: 80
```

- `metadata.namespace`: en qué namespace se crea el Service. Si se omite, se crea en `default`.
- `selector: run: servidor-ngnix`: el Service manda el tráfico a los pods **de su propio namespace** que tengan esa etiqueta. Es la etiqueta que `kubectl run` le pone automáticamente al pod (`run=<nombre>`), no `app=...`. Si el selector no coincide con la etiqueta del pod, el Service no tiene a quién mandar el tráfico (ver "Problemas comunes").
- Los dos Services apuntan al puerto `80`, el de nginx, y no se estorban porque cada uno vive en su namespace.

## Pasos

### 1. Crear los namespaces

```bash
kubectl apply -f namespaces.yaml
```

Salida esperada:

```
namespace/namespace-desarrollo created
namespace/namespace-operaciones created
```

Ver los namespaces con sus etiquetas:

```bash
kubectl get ns --show-labels
```

Salida esperada (fragmento):

```
NAME                    STATUS   AGE   LABELS
default                 Active   39d   kubernetes.io/metadata.name=default
kube-system             Active   39d   kubernetes.io/metadata.name=kube-system
namespace-desarrollo    Active   23m   environment=dev,kubernetes.io/metadata.name=namespace-desarrollo
namespace-operaciones   Active   23m   environment=ops,kubernetes.io/metadata.name=namespace-operaciones
```

`default`, `kube-system`, `kube-public` y `kube-node-lease` existen siempre; `kube-system` contiene los componentes internos de Kubernetes.

### 2. Crear un pod de nginx en cada namespace

Con `-n` se indica en qué namespace se crea. El **mismo nombre** se usa en los dos:

```bash
kubectl run servidor-ngnix --image=nginx -n namespace-desarrollo
kubectl run servidor-ngnix --image=nginx -n namespace-operaciones
```

Salida esperada:

```
pod/servidor-ngnix created
pod/servidor-ngnix created
```

Funciona sin conflicto porque el nombre de un objeto solo tiene que ser único **dentro de su namespace**. Para verlos todos juntos:

```bash
kubectl get pods -A | grep servidor-ngnix
```

Salida esperada:

```
namespace-desarrollo    servidor-ngnix    1/1     Running     0     41m
namespace-operaciones   servidor-ngnix    1/1     Running     0     40m
```

`-A` (`--all-namespaces`) lista los objetos de todos los namespaces. Sin `-n` ni `-A`, `kubectl get pods` solo muestra los de `default`.

Los dos pods tienen la etiqueta `run=servidor-ngnix`:

```bash
kubectl get pods -n namespace-desarrollo --show-labels
```

Salida esperada:

```
NAME             READY   STATUS    RESTARTS   AGE   LABELS
servidor-ngnix   1/1     Running   0          22m   run=servidor-ngnix
```

> Para que un pod creado con `kubectl run` lleve otra etiqueta (por ejemplo `app=servidor-ngnix`), se usa `--labels="app=servidor-ngnix"`. Cuando se indica `--labels`, la etiqueta `run=` por defecto **ya no se agrega**, y el `selector` del Service debe usar la misma clave.

### 3. Crear los Services

```bash
kubectl apply -f services-namespaces.yaml
```

Salida esperada:

```
service/desarrollo-nginx-service created
service/operaciones-nginx-service created
```

Comprobar que cada Service encontró su pod:

```bash
kubectl get endpoints -A | grep namespace-
```

Salida esperada:

```
namespace-desarrollo    desarrollo-nginx-service    10.244.0.10:80    16m
namespace-operaciones   operaciones-nginx-service   10.244.0.11:80    16m
```

Cada Service muestra la IP del pod de **su** namespace. Si en `ENDPOINTS` aparece `<none>`, el selector no coincide con la etiqueta del pod.

### 4. Acceder a cada servicio con `port-forward`

Con el driver de Docker en Mac, la IP de `minikube ip` no es alcanzable desde la Mac, así que el NodePort no sirve directamente. Se usa un túnel, indicando el namespace del Service con `-n`:

```bash
kubectl port-forward service/desarrollo-nginx-service -n namespace-desarrollo 7777:80
```

Salida esperada:

```
Forwarding from 127.0.0.1:7777 -> 80
Forwarding from [::1]:7777 -> 80
```

En otra terminal:

```bash
curl http://localhost:7777
```

Salida esperada (fragmento):

```
<!DOCTYPE html>
<html>
<head>
<title>Welcome to nginx!</title>
```

Para el otro namespace, cerrar el túnel (`Ctrl+C`) y repetir con `service/operaciones-nginx-service -n namespace-operaciones`. Sin el `-n`, `kubectl` busca el Service en `default` y responde `services "..." not found`.

### 5. Comunicación entre namespaces (DNS)

Dentro del clúster, cada Service tiene un nombre DNS con la forma:

```
<service>.<namespace>.svc.cluster.local
```

Desde el pod de `namespace-desarrollo` se prueban distintos nombres:

```bash
kubectl exec -n namespace-desarrollo servidor-ngnix -- curl -s -o /dev/null -w '%{http_code}\n' http://desarrollo-nginx-service
kubectl exec -n namespace-desarrollo servidor-ngnix -- curl -s -o /dev/null -w '%{http_code}\n' http://operaciones-nginx-service
kubectl exec -n namespace-desarrollo servidor-ngnix -- curl -s -o /dev/null -w '%{http_code}\n' http://operaciones-nginx-service.namespace-operaciones
kubectl exec -n namespace-desarrollo servidor-ngnix -- curl -s -o /dev/null -w '%{http_code}\n' http://operaciones-nginx-service.namespace-operaciones.svc.cluster.local
```

Salida esperada, en ese orden:

```
200
000
command terminated with exit code 6
200
200
```

| Nombre usado desde `namespace-desarrollo`                          | Resultado                                    |
| ------------------------------------------------------------------ | -------------------------------------------- |
| `desarrollo-nginx-service`                                          | `200`: nombre corto, mismo namespace          |
| `operaciones-nginx-service`                                         | **Falla** (`exit code 6`, no resuelve el nombre) |
| `operaciones-nginx-service.namespace-operaciones`                   | `200`: servicio + namespace                   |
| `operaciones-nginx-service.namespace-operaciones.svc.cluster.local` | `200`: nombre completo                        |

El nombre corto solo funciona dentro del **mismo** namespace. Para llamar a un Service de otro namespace hay que agregar el nombre del namespace. **Los namespaces separan nombres, pero no bloquean el tráfico**: cualquier pod puede llamar a cualquier Service si conoce su nombre completo. Para restringirlo se necesitan `NetworkPolicy`, que no se ven en este ejemplo.

Esto es lo mismo que pasó con SQL Server: la aplicación Spring usaba el host `sqlserver` porque ambos estaban en `default`. Si SQL Server estuviera en otro namespace, el host sería `sqlserver.<namespace>`.

### 6. Cambiar el namespace por defecto (opcional)

Escribir `-n` en cada comando es repetitivo. Se puede fijar el namespace del contexto actual:

```bash
kubectl config set-context --current --namespace=namespace-desarrollo
kubectl get pods
```

A partir de ahí, `kubectl get pods` muestra los de `namespace-desarrollo`. Para volver a `default`:

```bash
kubectl config set-context --current --namespace=default
```

## Problemas comunes

| Síntoma                                                                   | Causa                                                                                  | Solución                                                                                                                      |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `kubectl port-forward` falla aunque el Service existe; `ENDPOINTS` en `<none>` | El `selector` del Service no coincide con las etiquetas del pod (por ejemplo, `app:` contra `run=`) | `kubectl get pods -n <ns> --show-labels` y `kubectl get svc <svc> -n <ns> -o jsonpath='{.spec.selector}'`. Hacer que coincidan. |
| `services "..." not found`                                                | Falta `-n <namespace>` y el Service no está en `default`                               | Agregar `-n namespace-desarrollo` (o el que corresponda).                                                                     |
| `namespaces "..." not found` al crear un objeto con `-n`                   | El namespace no existe todavía                                                         | Crearlo antes con `kubectl apply -f namespaces.yaml`.                                                                         |
| `kubectl get pods` no muestra los pods que se acaban de crear              | Se están listando los de `default`                                                     | Usar `-n <namespace>` o `-A`.                                                                                                 |
| `curl` entre namespaces no resuelve el nombre                              | Se usó el nombre corto del Service                                                     | Usar `<service>.<namespace>` (ver paso 5).                                                                                    |
| `Unable to listen on port 7777 ... address already in use`                 | Otro programa usa ese puerto                                                           | Usar otro puerto local, por ejemplo `8888:80`.                                                                                |

## Limpiar

```bash
kubectl delete -f services-namespaces.yaml
kubectl delete -f namespaces.yaml
```

Borrar un namespace **elimina todo lo que contiene**: los pods `servidor-ngnix`, los Services y cualquier otro objeto que se hubiera creado dentro. Por eso no es necesario borrar los pods por separado. El namespace puede tardar unos segundos en pasar de `Terminating` a desaparecer.

Las salidas de `get ns`, `get pods -A`, `get endpoints`, `port-forward` + `curl` y las cuatro pruebas de DNS se tomaron del clúster de este equipo. Las de `kubectl apply`, `kubectl run`, `config set-context` y `delete` son las que da normalmente `kubectl`.
