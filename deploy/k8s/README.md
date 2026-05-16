# ChipIn Service — Kubernetes manifests

Minimal, hardened manifests. None of these contain secrets.

Apply in order (after creating the `chipin-service-secrets` secret from a real
secret manager):

```bash
kubectl apply -f namespace.yaml
kubectl apply -f configmap.yaml
# secrets are NOT applied from secret.example.yaml — that file is a template.
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f networkpolicy.yaml
```

Image tag in `deployment.yaml` is set to `:latest` for the example. In production
pin to an immutable digest:

```yaml
image: ghcr.io/your-org/chipin-service@sha256:<digest>
```

Notes
-----

- Pod-Security `restricted` profile is enforced at the namespace level.
- Container runs as non-root (UID 1001), with a read-only root filesystem and
  `ALL` capabilities dropped. The JVM still needs a writable `/tmp` — provided
  via an `emptyDir` volume.
- Readiness/liveness probes hit Spring Boot's actuator. Make sure
  `management.endpoints.web.exposure.include` includes `health` (it does, by
  default, in `application.properties`).
- Prometheus annotations on the Pod template work with the
  `kubernetes_sd_configs` scrape config that ships with most clusters; switch
  to a `PodMonitor` CRD if you use the Prometheus operator.
- The `NetworkPolicy` only allows ingress from a namespace labeled
  `app.kubernetes.io/name=ingress`. Adjust to match your real gateway/sidecar
  namespace.
