# Minikube Deployment Guide — Banking App

Two Spring Boot microservices (`transaction-service`, `user-account-service`) on a local Kubernetes cluster.

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

### Docker Desktop memory

**Set Docker Desktop RAM to 6 GB before starting.**
Docker Desktop → Settings → Resources → Memory → 6 GB → Apply & Restart.

With only 4 GB, Kafka and both Spring Boot services will OOM-kill each other.

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

> Both `user-account-service:latest` and `transaction-service:latest` must be built
> from their own directories. Building from the wrong directory will give you the
> wrong service under the wrong tag — causing very confusing runtime errors.

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

**Order matters** — each service waits for its dependencies via initContainers.

```bash
cd transaction-service

# Postgres first — ConfigMap must be applied before the Deployment
kubectl apply -f k8s/postgres/init-configmap.yaml -n banking
kubectl apply -f k8s/postgres/postgres.yaml -n banking
kubectl wait --for=condition=ready pod -l app=postgres -n banking --timeout=90s

# Kafka (Zookeeper starts first, Kafka waits for it via initContainer)
kubectl apply -f k8s/kafka/kafka.yaml -n banking
kubectl wait --for=condition=ready pod -l app=zookeeper -n banking --timeout=90s
kubectl wait --for=condition=ready pod -l app=kafka -n banking --timeout=120s

# user-account-service (waits for postgres + kafka)
kubectl apply -f k8s/services/user-account-service.yaml -n banking
kubectl wait --for=condition=ready pod -l app=user-account-service -n banking --timeout=310s

# transaction-service last (waits for postgres + kafka + user-account-service)
kubectl apply -f k8s/services/transaction-service.yaml -n banking
kubectl wait --for=condition=ready pod -l app=transaction-service -n banking --timeout=310s
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

> Spring Boot services take 2–3 minutes to start. The startupProbe allows up to
> 310 seconds before declaring failure.

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

**Always use scale instead of `rollout restart`** — the services use
`strategy: Recreate` which requires the old pod to die before the new one starts.
`rollout restart` can leave two pods competing for memory.

```bash
# Restart user-account-service
kubectl scale deployment user-account-service -n banking --replicas=0
kubectl scale deployment user-account-service -n banking --replicas=1

# Restart transaction-service
kubectl scale deployment transaction-service -n banking --replicas=0
kubectl scale deployment transaction-service -n banking --replicas=1
```

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

Both services take 2–3 minutes to start on a 6 GB minikube. The manifests use
a `startupProbe` with a 310-second window. If the probe window expires, the pod
is killed.

Signs: pod restarts every ~5 minutes, logs show `Started SpringBootMainApplication`
followed immediately by `GracefulShutdown`.

**Fix:** Check probe path is correct — actuator is under the app context path:
```
/api/v1/actuator/health   ← correct
/actuator/health           ← wrong (returns 404)
```

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
