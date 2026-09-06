# Minikube Deployment Guide — Banking App

Two Spring Boot microservices (`transaction-service`, `user-account-service`) on a local Kubernetes cluster.

---

## TL;DR — Quick start (cluster already provisioned)

Use this when Minikube, the `banking` namespace, the secret and the Postgres/Kafka
manifests already exist and you just want the two apps running with the latest code.

```bash
cd "$(git rev-parse --show-toplevel)"        # spring-app root
minikube start                                # if not already running

# 1. Build BOTH images straight into Minikube's Docker daemon
eval $(minikube docker-env)
docker build -t user-account-service:latest ./user-account-service
docker build -t transaction-service:latest  ./transaction-service
eval $(minikube docker-env -u)               # restore your normal shell

# 2. Roll the deployments (picks up the fresh images)
kubectl apply -f transaction-service/k8s/services/user-account-service.yaml
kubectl apply -f transaction-service/k8s/services/transaction-service.yaml
kubectl rollout restart deploy/user-account-service deploy/transaction-service -n banking

# 3. Wait for green (each app boots in ~30s; readiness probe adds ~40s)
kubectl rollout status deploy/user-account-service -n banking
kubectl rollout status deploy/transaction-service  -n banking
kubectl get pods -n banking

# 4. Reach them from the Mac (cluster net is NOT routable on the docker driver)
kubectl port-forward -n banking svc/user-account-service 8080:8080 &
kubectl port-forward -n banking svc/transaction-service  8081:8081 &
curl -s http://localhost:8080/api/v1/actuator/health
```

If a pod still runs old code after a rebuild, see
**Troubleshooting → Stale image after rebuild**.

## Architecture

```
Kubernetes (Minikube) — namespace: banking
│
├── postgres (single instance)
│   ├── banking_db / user_schema       ← user-account-service (port 8080)
│   └── banking_db / transaction_schema ← transaction-service (port 8081)
│
├── zookeeper
├── kafka-broker
├── user-account-service  (NodePort 30080)
└── transaction-service   (NodePort 30081)
```

## Prerequisites

| Tool | Version tested |
|---|---|
| macOS | 13.x |
| Docker Desktop | 4.33.0 (NOT 4.34.x — has Minikube regression) |
| Minikube | v1.38.1 |
| kubectl | v1.37.0 |
| Docker Desktop RAM | **6 GB minimum** (Settings → Resources → Memory) |

### Install Minikube and kubectl (binary, not Brew)

Brew fails on macOS 13 due to missing Go compiler. Use direct binaries instead:

```bash
# Minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-darwin-amd64
sudo install minikube-darwin-amd64 /usr/local/bin/minikube

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/darwin/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/kubectl
```

> For Apple Silicon (M1/M2/M3) replace `amd64` with `arm64` in the URLs above.

### Docker Desktop version

Docker Desktop 4.34.x has a regression that breaks Minikube. Use 4.33.0:

```
Intel Mac:  https://desktop.docker.com/mac/main/amd64/160616/Docker.dmg
Apple Silicon: https://desktop.docker.com/mac/main/arm64/160616/Docker.dmg
```

If macOS blocks the install with "damaged" or "unverified developer":
```bash
sudo xattr -r -d com.apple.quarantine /Applications/Docker.app
# or: System Settings → Privacy & Security → Open Anyway
```

### Docker Desktop memory & CPU

**Set Docker Desktop RAM to 6 GB before starting.**
Docker Desktop → Settings → Resources → Memory → 6 GB → Apply & Restart.

With only 4 GB, Kafka and both Spring Boot services will OOM-kill each other.

**CPU: give Docker Desktop all available cores (Settings → Resources → CPUs).**
The app Deployments deliberately set **no `resources.limits.cpu`** (only a `250m`
request). A hard CPU limit throttles JVM startup badly — with a `400m` cap the
services took **~17 minutes** to boot and got killed by the startupProbe. With no
limit they burst across the available cores and start in **~30 seconds**, then
settle back to near-idle. Don't add a CPU limit back.

On an 8 GB / 2-core machine, `minikube start --memory=3500 --cpus=2` is enough
*because* there is no per-pod CPU cap.

---

## k8s File Structure

All manifests live in `transaction-service/k8s/`:

```
transaction-service/
└── k8s/
    ├── postgres/
    │   ├── secret.yaml           # DB credentials
    │   ├── init-configmap.yaml   # Creates user_schema + transaction_schema
    │   └── postgres.yaml         # PVC + Deployment + Service
    ├── kafka/
    │   └── kafka.yaml            # Zookeeper + Kafka Deployments + Services
    └── services/
        ├── user-account-service.yaml
        └── transaction-service.yaml
```

---

## Deployment Steps

### 1. Start Minikube

```bash
minikube start --memory=3500 --cpus=2
```

Verify it's running:
```bash
minikube status
kubectl get nodes
```

### 2. Point Docker to Minikube's daemon

**Run this in every new terminal before building images:**

```bash
eval $(minikube docker-env)
```

> If you skip this, images build into Mac's Docker daemon and Minikube can't find them.

### 3. Build both service images

```bash
# inside user-account-service/
cd user-account-service
docker build -t user-account-service:latest .

# inside transaction-service/
cd ../transaction-service
docker build -t transaction-service:latest .
```

Verify both images are in Minikube with matching timestamps:
```bash
docker images | grep -E "user-account|transaction"
```

When done building, restore your normal shell so `docker`/`kubectl` talk to the
Mac again:
```bash
eval $(minikube docker-env -u)
```

> Both `user-account-service:latest` and `transaction-service:latest` must be built
> from their own directories. Building from the wrong directory will give you the
> wrong service under the wrong tag — causing very confusing runtime errors.
>
> Building **into Minikube's daemon** (this step, after `eval $(minikube
> docker-env)`) is the reliable way to update an image. `minikube image load` from
> Mac's daemon is not — it silently skips a tag that already exists. See
> *Troubleshooting → Stale image after rebuild*.

### 4. Create namespace

```bash
kubectl create namespace banking
```

### 5. Create secret

Replace `yourpassword` with your actual Postgres password:

```bash
kubectl create secret generic postgres-secret \
  --namespace=banking \
  --from-literal=POSTGRES_DB=banking_db \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD=yourpassword
```

> Use the same password every time. If you delete and recreate the namespace but
> the PVC still exists with old data, a mismatched password will cause
> `password authentication failed` on startup.

### 6. Apply manifests in order

**Apply each layer, then wait for it to be ready before the next** — the ConfigMap
must exist before Postgres, and each service's initContainers block until their
dependencies answer.

```bash
cd transaction-service

# --- Postgres: ConfigMap + Secret BEFORE the Deployment ---
kubectl apply -f k8s/postgres/init-configmap.yaml -n banking
kubectl apply -f k8s/postgres/postgres.yaml -n banking
kubectl wait --for=condition=ready pod -l app=postgres -n banking --timeout=120s

# --- Kafka: Zookeeper first (Kafka's initContainer waits on it) ---
kubectl apply -f k8s/kafka/kafka.yaml -n banking
kubectl wait --for=condition=ready pod -l app=zookeeper -n banking --timeout=120s
kubectl wait --for=condition=ready pod -l app=kafka     -n banking --timeout=180s

# --- user-account-service (initContainers wait for postgres + kafka) ---
kubectl apply -f k8s/services/user-account-service.yaml -n banking
kubectl wait --for=condition=ready pod -l app=user-account-service -n banking --timeout=240s

# --- transaction-service last (also waits for user-account-service) ---
kubectl apply -f k8s/services/transaction-service.yaml -n banking
kubectl wait --for=condition=ready pod -l app=transaction-service -n banking --timeout=240s
```

### 7. Watch pods come up

```bash
kubectl get pods -n banking -w
```

Expected healthy state (all pods `Running`, `READY 1/1`):
```
NAME                                  READY   STATUS    RESTARTS
postgres-xxx                          1/1     Running   0
zookeeper-xxx                         1/1     Running   0
kafka-xxx                             1/1     Running   0
user-account-service-xxx              1/1     Running   0
transaction-service-xxx               1/1     Running   0
```

> Each Spring Boot service reaches `Started SpringBootMainApplication` in **~30 s**
> (verified: 27 s). It is then marked `READY` once the `readinessProbe` clears —
> `initialDelaySeconds: 150` by default, so allow ~3 min end-to-end. Drop that to
> `40` in the manifests if you want faster rollouts. The `startupProbe` safety
> window is `30 + 60×10 = 630 s`.

---

## Accessing the Services

### Option A — Port forward (recommended, background)

```bash
kubectl port-forward svc/user-account-service 8080:8080 -n banking &
kubectl port-forward svc/transaction-service 8081:8081 -n banking &
```

Then access via localhost:
- user-account-service → `http://localhost:8080/api/v1`
- transaction-service  → `http://localhost:8081/api/v1`

### Option B — Minikube service tunnel

```bash
# Keep this terminal open — closing it kills the tunnel
minikube service user-account-service -n banking --url
minikube service transaction-service -n banking --url
```

> NodePort URLs (`http://<minikube-ip>:30080`) do NOT work on macOS with the
> Docker driver. Always use port-forward or minikube service tunnel instead.

---

## Validation

### Health checks
```bash
curl http://localhost:8080/api/v1/actuator/health
# Expected: {"status":"UP","groups":["liveness","readiness"]}

curl http://localhost:8081/api/v1/actuator/health
# Expected: {"status":"UP","groups":["liveness","readiness"]}
```

### API endpoints — user-account-service
```bash
# Get all users
curl http://localhost:8080/api/v1/users

# Create a user
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com"}'
```

### API endpoints — transaction-service
```bash
# Get all transactions
curl http://localhost:8081/api/v1/transactions

# Create a transaction
curl -X POST http://localhost:8081/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"amount":100.00,"type":"CREDIT"}'
```

### Check logs for runtime errors
```bash
kubectl logs deployment/user-account-service -n banking | tail -30
kubectl logs deployment/transaction-service -n banking | tail -30
```

---

## Restarting a Service Safely

The services use `strategy: Recreate`, so `kubectl rollout restart` is safe — the
old pod is torn down before the new one starts, they never overlap:

```bash
kubectl rollout restart deploy/user-account-service -n banking
kubectl rollout status  deploy/user-account-service -n banking
```

The explicit scale-down/up does the same thing and is handy when you also want to
free the memory in between (e.g. before a rebuild), or to force a stuck pod away:

```bash
kubectl scale deploy/user-account-service -n banking --replicas=0
kubectl scale deploy/user-account-service -n banking --replicas=1
```

Either way, only touch one service at a time on a small machine so their ~30 s
JVM boots don't overlap.

---

## Troubleshooting

### Kafka CrashLoopBackOff

**Most likely cause:** Kubernetes auto-injects `KAFKA_PORT` env var when the
Kafka Service is named `kafka`. This clashes with Confluent's startup scripts.

**Fix:** The Kafka Service is named `kafka-broker` (not `kafka`) in all manifests.
All references to Kafka use `kafka-broker:9092`.

If Kafka still crashes, get logs immediately while it's in `Running` state:
```bash
kubectl logs -n banking -l app=kafka -f
kubectl describe pod -n banking -l app=kafka | grep -A5 "Last State"
```

Exit code `1` = config error. Exit code `137` = OOM killed (increase Docker Desktop RAM).

### password authentication failed

The Postgres PVC retains data between namespace deletions. If you change the
password in the secret, Postgres rejects it because the data directory was
initialised with the old password.

**Fix:** Delete the PVC before recreating:
```bash
kubectl delete namespace banking
kubectl delete pvc postgres-pvc 2>/dev/null || true
# Then redeploy from Step 4
```

### ConfigMap not found (postgres init-sql)

The `postgres-init-sql` ConfigMap must be applied **before** `postgres.yaml`.
```bash
kubectl apply -f k8s/postgres/init-configmap.yaml -n banking
kubectl apply -f k8s/postgres/postgres.yaml -n banking
```

### Spring Boot killed before startup completes

Healthy startup is ~30 s. If a pod restarts every few minutes and the logs show
`Started SpringBootMainApplication` followed immediately by `GracefulShutdown`
(or the container never gets past Hibernate init), the startupProbe window
expired. Causes, most common first:

1. **A `resources.limits.cpu` was added back.** A `400m` cap pushes startup to
   ~17 min. Remove the CPU limit (keep only the request). See *Docker Desktop
   memory & CPU* above.
2. **Machine is thrashing** — check for `HikariPool-1 - Thread starvation or
   clock leap detected` in the logs. Give Docker Desktop more CPU/RAM, or bring
   the two apps up one at a time.
3. **Wrong probe path** — actuator lives under the app context path:
   ```
   /api/v1/actuator/health   ← correct
   /actuator/health          ← wrong (404)
   ```

The `startupProbe` window is `initialDelaySeconds 30 + failureThreshold 60 ×
periodSeconds 10 = 630 s`.

### Stale image after rebuild (pod keeps running old code)

Minikube on the Docker driver has its **own** image store, separate from Mac's
Docker daemon, plus a tar cache at `~/.minikube/cache/images/`. With
`imagePullPolicy: Never` the pod only ever uses Minikube's copy, and
`minikube image load <name:tag>` **silently no-ops when that tag already exists**.
So a rebuild can leave the old code running.

Confirm what the pod actually loaded:
```bash
kubectl get pod -n banking -l app=user-account-service \
  -o jsonpath='{.items[0].status.containerStatuses[0].imageID}{"\n"}'
minikube ssh -- "docker images --format '{{.Repository}}:{{.Tag}} {{.ID}} {{.CreatedSince}}' | grep user-account"
```

Force the fresh image in:
```bash
kubectl scale deploy/user-account-service -n banking --replicas=0
minikube ssh -- 'docker container prune -f'          # release the old image ref
minikube ssh -- 'docker rmi -f user-account-service:latest'
rm -rf ~/.minikube/cache/images/*/user-account-service_latest

# rebuild INTO minikube's daemon (not Mac's)
eval $(minikube docker-env)
docker build -t user-account-service:latest ./user-account-service
eval $(minikube docker-env -u)

kubectl scale deploy/user-account-service -n banking --replicas=1
```

Verify the running jar has your change (example — the ModelMapper fix):
```bash
minikube ssh -- 'CID=$(docker create user-account-service:latest); \
  docker cp $CID:/app/app.jar /tmp/a.jar; docker rm $CID >/dev/null'
minikube cp minikube:/tmp/a.jar /tmp/a.jar
unzip -p /tmp/a.jar BOOT-INF/classes/org/example/banking/ApplicationConfig.class \
  | javap -c -p /dev/stdin | grep -i emptyTypeMap
```

> Note: Docker Desktop's containerd image store shows a *different* image ID than
> `docker save` / Minikube report for the same content — don't chase the ID
> mismatch, compare the actual bytecode or a build timestamp instead.

### ModelMapper `ConfigurationException` on user-account-service startup

```
Failed to instantiate [org.modelmapper.ModelMapper]: ... ModelMapper configuration errors:
1) Not able to skip user., because there are already nested properties are mapped: [user.userId.]
```

Cause: `ApplicationConfig.modelMapper()` called `createTypeMap(AccountDTO, Account)`
(which immediately runs implicit mappings, mapping `AccountDTO.userId →
Account.user.userId`) and *then* `skip(Account::setUser)` — ModelMapper refuses to
skip a parent whose child is already mapped.

Fix (already in `ApplicationConfig.java`): start from `emptyTypeMap(...)`, register
`skip` + `setPropertyCondition` first, then call `implicitMappings()` explicitly.
If you see this again, the pod is running a **pre-fix image** — see *Stale image
after rebuild* above.

### Duplicate pods competing for memory

Caused by `kubectl rollout restart` leaving old replicasets active.

**Fix:** Use scale instead (see Restarting a Service Safely above).

Clean up stale replicasets:
```bash
kubectl get rs -n banking | grep "0         0         0" | awk '{print $1}' | \
  xargs kubectl delete rs -n banking 2>/dev/null
```

### Connection refused on NodePort URL

NodePort URLs (`http://192.168.49.2:30080`) don't work on macOS with the Docker
driver. Use port-forward or `minikube service --url` instead.

### Image not found / wrong service running

Always build images **after** running `eval $(minikube docker-env)` and from
**inside each service's own directory**.

```bash
eval $(minikube docker-env)

cd user-account-service && docker build -t user-account-service:latest .
cd ../transaction-service && docker build -t transaction-service:latest .
```

Verify correct image by checking the package name in logs:
- `user-account-service` → `o.e.banking.SpringBootMainApplication` on port `8080`
- `transaction-service`  → `c.e.banking.SpringBootMainApplication` on port `8081`

---

## Teardown

```bash
# Stop services, keep data
kubectl delete namespace banking
minikube stop

# Full reset including PVC data
kubectl delete namespace banking
kubectl delete pvc postgres-pvc 2>/dev/null || true
minikube stop
minikube delete
```
