# oficina-auth-function

Functions Serverless (Google Cloud Functions Gen2) do Tech Challenge Fase 3
(Pós-Tech SOAT).

Um dos 4 repositórios exigidos pelo desafio. Duas functions independentes,
cada uma em seu próprio módulo Maven:

## `auth/` — autenticação por CPF

Recebe um CPF via HTTP, valida o formato (mesma regra de dígito verificador
usada em `CpfCnpjValidator` na aplicação principal
[`oficina`](https://github.com/robsonago/oficina)), confirma que existe um
cliente ativo com esse documento no Cloud SQL, e devolve um JWT — assinado
com a mesma chave (`JWT_SECRET`) e formato usados pela aplicação principal,
com a claim `role=CLIENTE` — pronto para autenticar chamadas às APIs
protegidas.

Conecta no Cloud SQL via [Cloud SQL Connector (socket
factory)](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory),
autenticando através da service account da function (a mesma usada pela
aplicação no GKE via Workload Identity), sem senha de rede exposta além da
credencial do usuário de aplicação.

**Requisição:** `POST /` com `{"cpf": "52998224725"}`
**Resposta:** `200 {"token": "..."}` | `400` CPF inválido | `404` cliente não
encontrado | `403` cliente inativo.

## `notification/` — envio de notificação

Disparada por mensagem no tópico Pub/Sub (publicado pela aplicação principal
via `PubSubEmailAdapter` quando um orçamento fica disponível). Decodifica o
evento e manda o e-mail de fato via SMTP — a parte síncrona/lenta que antes
rodava dentro da própria aplicação.

## Deploy

Uma implantação por ambiente (homologação/produção), com variáveis de
ambiente próprias:

```bash
# auth/
gcloud functions deploy oficina-auth-<ambiente> \
  --gen2 --region=southamerica-east1 --runtime=java21 \
  --source=auth --entry-point=br.com.fiap.challange.oficina.authfunction.AuthFunction \
  --trigger-http --allow-unauthenticated \
  --set-env-vars="INSTANCE_CONNECTION_NAME=...,DB_NAME=oficina_<ambiente>,DB_USER=oficina,JWT_SECRET=...,JWT_EXPIRATION=86400000" \
  --set-secrets="DB_PASSWORD=oficina-db-password:latest" \
  --service-account=oficina-app-cloudsql@<projeto>.iam.gserviceaccount.com

# notification/
gcloud functions deploy oficina-notification-<ambiente> \
  --gen2 --region=southamerica-east1 --runtime=java21 \
  --source=notification --entry-point=br.com.fiap.challange.oficina.notificationfunction.NotificationFunction \
  --trigger-topic=oficina-notificacoes-<ambiente> \
  --set-env-vars="MAIL_HOST=...,MAIL_PORT=...,MAIL_FROM=..." \
  --set-secrets="MAIL_USERNAME=...:latest,MAIL_PASSWORD=...:latest"
```

O tópico Pub/Sub e a service account são provisionados em
[`oficina-infra-k8s`](https://github.com/robsonago/oficina-infra-k8s) e
[`oficina-infra-db`](https://github.com/robsonago/oficina-infra-db).

Dockerfile não se aplica (Cloud Functions não usa Docker).

> Este README será complementado com o link dos ambientes ativos assim que o
> deploy de cada função em cada ambiente estiver consolidado.
