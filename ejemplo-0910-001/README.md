# Ejemplo 0910-001 — Instalar minikube y kubectl

Se instala un clúster de Kubernetes local con **minikube** (un solo nodo que corre como contenedor de Docker) y **kubectl**, el cliente para hablar con el clúster. Después se corren los comandos básicos de kubectl y se crea el servicio `hola-minikube` para abrirlo desde el navegador.

## Requisitos

- Docker funcionando (en Mac, OrbStack). Comprobar con `docker ps`.
- Unos 4 GB de RAM libres para el clúster.

## Instalar minikube

1. Descargar el binario para Mac Intel e instalarlo en `/usr/local/bin` (para Mac con Apple Silicon usar `minikube-darwin-arm64`):

   ```bash
   curl -LO https://github.com/kubernetes/minikube/releases/latest/download/minikube-darwin-amd64
   sudo install minikube-darwin-amd64 /usr/local/bin/minikube
   rm minikube-darwin-amd64
   ```

2. Verificar:

   ```bash
   minikube version
   ```

   Salida esperada:

   ```
   minikube version: v1.39.0
   commit: 7a9f6a841470a207de8cf4bafcccee0969d8ba10
   ```

## Instalar kubectl

Se instala la misma versión de Kubernetes que usa minikube (`v1.37.0`). kubectl solo garantiza compatibilidad con **una versión menor de diferencia** respecto al clúster.

1. Descargar kubectl `v1.37.0` (Mac Intel; para Apple Silicon cambiar `amd64` por `arm64`):

   ```bash
   curl -LO https://dl.k8s.io/release/v1.37.0/bin/darwin/amd64/kubectl
   ```

2. Verificar que el archivo no venga dañado. Los dos valores deben coincidir:

   ```bash
   curl -Ls https://dl.k8s.io/release/v1.37.0/bin/darwin/amd64/kubectl.sha256; echo
   shasum -a 256 kubectl
   ```

3. Instalarlo en `/usr/local/bin`:

   ```bash
   sudo install kubectl /usr/local/bin/kubectl
   rm kubectl
   ```

4. Verificar:

   ```bash
   kubectl version --client
   ```

   Salida esperada:

   ```
   Client Version: v1.37.0
   Kustomize Version: v5.8.1
   ```

## Crear y encender el clúster

```bash
minikube start --driver=docker
```

La primera vez descarga la imagen base y Kubernetes (varios cientos de MB). Al final debe aparecer:

```
* Preparando Kubernetes v1.37.0 en containerd 2.3.4...
* Verifying Kubernetes components...
* Complementos habilitados: storage-provisioner, default-storageclass
* Done! kubectl is now configured to use "minikube" cluster and "default" namespace by default
```

Comprobar el estado:

```bash
minikube status
```

Salida esperada:

```
minikube
type: Control Plane
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```

`minikube start` también configura `~/.kube/config` para que kubectl apunte al clúster.

## Comandos básicos de kubectl

1. Ver los nodos del clúster (minikube tiene uno solo, que es a la vez el plano de control):

   ```bash
   kubectl get nodes
   ```

   Salida esperada:

   ```
   NAME       STATUS   ROLES           AGE     VERSION
   minikube   Ready    control-plane   3d21h   v1.37.0
   ```

2. Ver los pods del namespace `default`:

   ```bash
   kubectl get pods
   ```

   Salida esperada (todavía no se ha creado nada):

   ```
   No resources found in default namespace.
   ```

3. Ver los namespaces:

   ```bash
   kubectl get namespaces
   ```

   Salida esperada:

   ```
   NAME              STATUS   AGE
   default           Active   3d21h
   kube-node-lease   Active   3d21h
   kube-public       Active   3d21h
   kube-system       Active   3d21h
   ```

   - `default`: donde se crean los recursos si no se indica otro namespace.
   - `kube-system`: los componentes del propio Kubernetes.
   - `kube-public` y `kube-node-lease`: uso interno del clúster.

4. Ver los services:

   ```bash
   kubectl get services
   ```

   Salida esperada:

   ```
   NAME         TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)   AGE
   kubernetes   ClusterIP   10.96.0.1    <none>        443/TCP   3d21h
   ```

   El service `kubernetes` es el acceso a la API del clúster desde dentro de los pods.

Para ver los pods del sistema, agregar `-A` (todos los namespaces):

```bash
kubectl get pods -A
```

Salida esperada:

```
NAMESPACE     NAME                               READY   STATUS    RESTARTS      AGE
kube-system   coredns-559f6c778d-whhwm           1/1     Running   2 (63m ago)   3d21h
kube-system   etcd-minikube                      1/1     Running   2 (63m ago)   3d21h
kube-system   kindnet-vhtm8                      1/1     Running   2 (63m ago)   3d21h
kube-system   kube-apiserver-minikube            1/1     Running   2 (63m ago)   3d21h
kube-system   kube-controller-manager-minikube   1/1     Running   2 (63m ago)   3d21h
kube-system   kube-proxy-9hk6n                   1/1     Running   2 (63m ago)   3d21h
kube-system   kube-scheduler-minikube            1/1     Running   2 (63m ago)   3d21h
kube-system   storage-provisioner                1/1     Running   4 (63m ago)   3d21h
```

## Crear el servicio hola-minikube

1. Crear un deployment con una aplicación de ejemplo que responde en el puerto `8080`:

   ```bash
   kubectl create deployment hola-minikube --image=gcr.io/google-samples/hello-app:2.0
   ```

   Salida esperada:

   ```
   deployment.apps/hola-minikube created
   ```

2. Exponerlo con un service de tipo `NodePort`:

   ```bash
   kubectl expose deployment hola-minikube --type=NodePort --port=8080
   ```

   Salida esperada:

   ```
   service/hola-minikube exposed
   ```

3. Revisar el pod y el service:

   ```bash
   kubectl get pods,services
   ```

   Salida esperada:

   ```
   NAME                                 READY   STATUS    RESTARTS   AGE
   pod/hola-minikube-5bd6b47f74-x9x7q   1/1     Running   0          13m

   NAME                    TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
   service/hola-minikube   NodePort    10.110.187.68   <none>        8080:31046/TCP   2m18s
   service/kubernetes      ClusterIP   10.96.0.1       <none>        443/TCP          3d22h
   ```

   - `NodePort`: Kubernetes abre un puerto en el **nodo** (aquí `31046`, elegido al azar entre 30000 y 32767) y lo redirige al pod en el puerto `8080`. El número cambia en cada equipo.
   - `CLUSTER-IP` (`10.110.187.68`): solo funciona **dentro** del clúster, no desde el navegador.

## Acceder al servicio desde el navegador

### Opción 1: `minikube service` (recomendada)

```bash
minikube service hola-minikube
```

minikube abre el navegador con la URL del servicio. Con el driver `docker` en Mac o Windows, crea un túnel hacia un puerto local, y la terminal debe quedar abierta mientras se usa (`Ctrl+C` lo cierra). Para solo ver la URL sin abrir el navegador:

```bash
minikube service hola-minikube --url
```

Salida esperada (el puerto cambia cada vez):

```
http://127.0.0.1:52601
! Porque estás usando controlador Docker en darwin, la terminal debe abrirse para ejecutarlo.
```

### Opción 2: `kubectl port-forward`

```bash
kubectl port-forward service/hola-minikube 8080:8080
```

Abrir **http://localhost:8080**. También deja la terminal ocupada hasta `Ctrl+C`, y funciona con cualquier tipo de service, incluso `ClusterIP`.

### Opción 3: IP del nodo (solo con OrbStack o en Linux)

```bash
minikube ip
```

Salida esperada:

```
192.168.49.2
```

Abrir `http://<ip del nodo>:<NodePort>`, en este caso **http://192.168.49.2:31046**. Funciona en Linux (solo desde la misma máquina donde corre minikube) y con OrbStack, que permite llegar a la IP de los contenedores desde la Mac; con Docker Desktop no funciona.

En cualquiera de las tres opciones, el navegador muestra:

```
Hello, world!
Version: 2.0.0
Hostname: hola-minikube-5bd6b47f74-x9x7q
```

`Hostname` es el nombre del pod que respondió.

### Desde otra computadora (minikube en un servidor Rocky Linux)

Las tres opciones anteriores funcionan cuando el navegador y minikube están **en la misma máquina** (por ejemplo, todo en una Mac con OrbStack). Si minikube corre en un servidor Rocky Linux y el navegador está en otra computadora (por ejemplo, Windows conectado por SSH), hay tres obstáculos:

```
Windows (navegador)  ──red──►  Servidor Rocky Linux
                                 ├─ firewalld (bloquea puertos)
                                 └─ minikube (red interna 192.168.49.x)
                                     └─ pod hola-minikube :8080
```

- La IP del nodo (`192.168.49.2`) es una red interna de Docker que solo existe dentro del servidor; desde Windows no se llega a ella, así que el NodePort no sirve.
- `kubectl port-forward` escucha por defecto solo en `127.0.0.1`, es decir, solo acepta conexiones del propio servidor.
- Rocky Linux trae `firewalld` activo, que bloquea los puertos que no se hayan abierto explícitamente.

En el servidor Rocky:

1. Abrir en el firewall el puerto que se va a usar (aquí `8082`) y recargar para aplicarlo:

   ```bash
   sudo firewall-cmd --permanent --add-port=8082/tcp
   sudo firewall-cmd --reload
   ```

   Salida esperada (una por comando):

   ```
   success
   success
   ```

   `--permanent` guarda la regla para que sobreviva a reinicios, pero no la aplica hasta el `--reload`.

2. Redirigir el puerto escuchando en todas las interfaces del servidor:

   ```bash
   kubectl port-forward --address 0.0.0.0 service/hola-minikube 8082:8080
   ```

   Salida esperada:

   ```
   Forwarding from 0.0.0.0:8082 -> 8080
   ```

   - `--address 0.0.0.0`: escucha en todas las interfaces de red del servidor, no solo en `127.0.0.1`.
   - `8082:8080`: el servidor escucha en el `8082` y lo envía al puerto `8080` del service. Se usa otro puerto si el `8082` ya está ocupado.
   - El redireccionamiento dura mientras la terminal (o la sesión SSH) siga abierta.

3. Obtener la IP del servidor:

   ```bash
   hostname -I
   ```

4. Desde el navegador en la otra computadora, abrir `http://<IP del servidor>:8082`.

> `--address 0.0.0.0` expone el servicio, sin contraseña, a cualquiera que llegue al servidor por la red. Usarlo solo en redes de confianza y cerrar el puerto al terminar:
>
> ```bash
> sudo firewall-cmd --permanent --remove-port=8082/tcp
> sudo firewall-cmd --reload
> ```

Las salidas de esta sección no se ejecutaron en este equipo (Mac); son las que dan normalmente `firewall-cmd` y `kubectl` en Rocky Linux.

### Borrar el servicio

```bash
kubectl delete service hola-minikube
kubectl delete deployment hola-minikube
```

## Encender y apagar el clúster

El contenedor `minikube` se maneja **solo con minikube**, nunca con los botones de OrbStack ni con `docker start/stop/restart minikube`.

| Para                               | Usar                      |
| ---------------------------------- | ------------------------- |
| Encender                           | `minikube start`          |
| Apagar                             | `minikube stop`           |
| Ver estado                         | `minikube status`         |
| Solo corregir el puerto en kubectl | `minikube update-context` |

Si el contenedor se enciende por fuera de minikube:

- Dentro no arrancan `kubelet` ni la API de Kubernetes.
- Docker le asigna **puertos nuevos**, pero `~/.kube/config` sigue apuntando al puerto viejo.

Y kubectl falla con alguno de estos errores:

```
dial tcp 127.0.0.1:32771: connect: connection refused
Get "https://127.0.0.1:32771/api?timeout=32s": EOF
The connection to the server 127.0.0.1:32771 was refused - did you specify the right host or port?
```

`minikube status` lo confirma con `kubelet: Stopped`, `apiserver: Stopped` o `kubeconfig: Misconfigured`. La solución en todos los casos es `minikube start`.

> No ponerle política de reinicio (`docker update --restart ...`) al contenedor `minikube`: Docker lo encendería por fuera de minikube y se repetiría el problema.

## Después de reiniciar la computadora

Al apagar la Mac, OrbStack se detiene y todos los contenedores se apagan. Ninguno vuelve a encenderse solo (OrbStack no arranca al iniciar sesión y los contenedores tienen política de reinicio `no`). No se pierden las imágenes, las redes, los datos ni la configuración del clúster.

1. Abrir OrbStack:

   ```bash
   open -a OrbStack
   ```

2. Encender el clúster:

   ```bash
   minikube start
   ```

3. Comprobar:

   ```bash
   kubectl get nodes
   ```

   Salida esperada:

   ```
   NAME       STATUS   ROLES           AGE     VERSION
   minikube   Ready    control-plane   3d21h   v1.37.0
   ```

## Desinstalar

```bash
minikube delete
sudo rm /usr/local/bin/minikube /usr/local/bin/kubectl
rm -rf ~/.minikube
```
