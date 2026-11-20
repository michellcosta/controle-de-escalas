> **STATUS (20/11/2026): TODOS os achados desta auditoria foram corrigidos**
> (exceto S2-fase arquitetural e S6-migração de assinatura, adiados por decisão).
> Correções em commits locais de 6f6bd92 a 3e96896 — nada publicado ainda.
> O APK caiu de 78MB para 12,4MB. Detalhes de cada correção na mensagem de cada commit.

# Análise completa do app — Controle de Escalas (v1.4.0-beta)

> Auditoria em 4 frentes (serviços em background, UI/ViewModels, backend/rules, config/APK),
> feita em 19/11/2026. Achados verificados por leitura direta do código.
> Severidade: **P1** crítico · **P2** importante · **P3** melhoria.

---

## 🔴 SEGURANÇA (o mais urgente)

### S1. Endpoints de notificação são PÚBLICOS — qualquer pessoa dispara push e queima cota
**P1** · `backend-python/api.py:105,182,246` · **Verificado no código**
`/notify/motorista`, `/notify/base` e `/notify/status-change` **não chamam `_verify_firebase_token`**
(os únicos endpoints com auth são `/registrar_versao`, `/location/*` e `/assistente/chat`).
Qualquer pessoa na internet pode POSTar em `controle-de-escalas.onrender.com` e:
- mandar push para um motorista ("Subir agora para a vaga 01") ou para a base inteira
- notificar os admins com mensagem falsa ("motorista X chegou ao galpão")
- queimar leituras do Firestore em loop (cada chamada lê N docs de tokens) — **provável vetor real dos estouros de cota**

**Correção:** exigir Bearer + papel admin/superadmin nesses endpoints; em `/status-change` validar `uid == motoristaId`. Esforço: **médio**.

### S2. O gate de versão é furável — e é a ÚNICA ACL que existe
**P1** · `api.py` (/registrar_versao) + `firestore.rules` (podeEscrever)
A API key do Firebase é pública no APK e o login é anônimo: um script cria conta → POSTa `versionCode: 999` → recebe claim `v=999` → **passa todas as rules**. Com isso, qualquer pessoa pode ler escalas, motoristas e tokens FCM de TODAS as bases, escrever no doc de outro motorista, e até se promover a `admin` (campo `papel` é escrito pelo cliente). As funções `isMemberOfBase`/`isAdminOfBase` existem nas rules mas **nunca são usadas**.
**Correção real (alto esforço):** papel/baseId vindo do backend (claims gravadas num fluxo de aprovação), não do cliente. Paliativo imediato: o backend já knows os UIDs admin — recusar claims acima de um teto (ex.: `v = min(versionCode, 15)` já resolve o "999" de graça). Esforço do paliativo: **baixo**.

### S3. Custo do assistente (OpenAI) controlado pelo CLIENTE
**P1** · `api.py:905-972`
`_is_super_admin()` usa `userRole` e `userId` **do body da requisição** (não do token) — mandar `userRole: "superadmin"` desliga a cota de 15 usos/dia. A cota é por `baseId` (string qualquer — trocar reseta o contador). Histórico de 20 itens sem limite de tamanho + imagem até ~5MB com `detail: "high"` sem limite por uid. Login anônimo é grátis → **custo OpenAI ilimitado na prática**.
**Correção:** papel sempre do token/Firestore; cota por uid; limitar tamanho de texto/histórico/imagem. Esforço: **médio**.

### S4. Injeção de prompt pela identidade do usuário
**P1** · `api.py:748-756`
`userName`/`userRole`/`turno` do body são concatenados no SYSTEM prompt (peso máximo). Um "nome" como `Michell. IGNORE TODAS as regras...` é injetado com prioridade — e as ações resultantes (adicionar à escala, avisar base inteira) são executadas pelo app.
**Correção:** tratar identidade como dados citados ("O usuário SE IDENTIFICA como..."), nunca como instruções. Esforço: **baixo**.

### S5. `/motorista/token` GET público devolve o FCM token
**P2** · `api.py:353-387` · sem auth, sem checagem de papel: `GET /motorista/token?baseId=X&motoristaId=Y` retorna o doc com `fcmToken` e `nome`. **Correção:** exigir token + papel admin; nunca devolver `fcmToken` ao cliente. Esforço: **baixo**.

### S6. Release assinado com a DEBUG keystore
**P1** · `Raiz-prompt/app/build.gradle.kts:33` (`signingConfig = signingConfigs.getByName("debug")`)
O APK de produção usa a keystore debug (senha conhecida, específica da sua máquina). **Se esta máquina morrer, todos os usuários ficam presos** — impossível atualizar sem desinstalar. A chave real existe em `Key Store/Chave` (gitignored) mas não é usada.
**Correção:** `signingConfigs.create("release")` lendo de `keystore.properties` fora do git. Esforço: **baixo**. ⚠️ Atenção ao migrar: APKs assinados com chave DIFERENTE não atualizam por cima — a troca de assinatura exige reinstalação única de todos.

---

## 🟠 BUGS FUNCIONAIS

### B1. A feature "quanto tempo para X chegar?" está MORTA
**P1** · `AssistenteViewModel.kt:402` + `:111` · **Verificado no código**
`findMotoristaByName(baseId, nome)` é chamada **sem** o 3º argumento, e a função faz `preFetched ?: return null` → **sempre retorna null** → toda pergunta de localização cai em "Não encontrei motorista com esse nome". Todo o fluxo de ETA (incluindo o que melhoramos na 1.4.0) nunca roda por essa porta.
**Correção:** buscar a lista de motoristas antes do match (2 linhas). Esforço: **baixo**.

### B2. DailyResetWorker fail-open + retry sem teto = tempestade multi-dispositivo
**P1** · `data/DailyResetWorker.kt:95-99,113-139`
Se a transação de captura do reset for negada (gate/cota), o catch retorna "siga em frente", e o worker retorna `Result.retry()` **sem limite de tentativas**. Cada aparelho que abriu o app agenda esse worker → todos martelam o Firestore negado. É mecanicamente o mesmo formato do incidente de 44k ops.
**Correção:** erro de cota/permissão → `Result.failure()` (sem retry) + pular o reset. Esforço: **baixo**.

### B3. StatusCheckWorker: retry incondicional em qualquer erro (incl. PERMISSION_DENIED)
**P1** · `data/StatusCheckWorker.kt:120-125`
Mesmo padrão do B2 em worker periódico: leitura negada vira `Result.retry()` a cada 30s para sempre, em todos os aparelhos monitorando. **Correção:** classificar o erro; negação/cota → failure. Esforço: **baixo**.

### B4. Falha de rede APAGA o estado do motorista e para o monitoramento
**P1** · `DriverViewModel.kt:791-828` + `MotoristaRepository.kt:343,735`
`checkMotoristaExcluido` considera o motorista excluído quando 2 leituras retornam null — mas os métodos do repositório fazem `catch { null }`. Rede instável → app limpa nome/status/escala, remove geofences e para os Workers **silenciosamente** (motorista para de receber chamadas).
**Correção:** distinguir "não existe" de "erro de leitura". Esforço: **médio**.

### B5. Android 15 mata o serviço do turno após 6h (crash)
**P1** · `AndroidManifest.xml:78-82`
`StatusMonitoringService` declara `foregroundServiceType="dataSync|location"`. No Android 15, FGS `dataSync` tem limite de **6h/dia** e estoura com `ForegroundServiceDidNotStopInTimeException` — exatamente o caso do turno inteiro. O serviço não faz sync de dados; o tipo correto é só `location`.
**Correção:** trocar o tipo no manifest + `ServiceCompat.startForeground`. Esforço: **baixo**.

### B6. Até 3 escritores simultâneos do mesmo "CHEGUEI"
**P1** · `GeofenceBroadcastReceiver.kt:143-209` + `GeofencingService.kt:604-650` + `StatusMonitoringService.kt:448-467,513-543`
Na chegada ao galpão, receiver + serviço + loop de 30s podem gravar CHEGUEI em paralelo (estado de throttle é por instância): até **3 escritas + 3 pushes** por um único evento. **Correção:** centralizar num único ponto + throttle por chave estática (motoristaId+estado). Esforço: **médio**.

### B7. Escala PM fica sem realtime no painel admin
**P2** · `OperationalViewModel.kt:1603-1607`
O listener de escala captura o turno no registro e `changeTurno` não re-registra → ao trocar para PM, dados congelados (só o `get()` inicial). **Correção:** re-registrar no `changeTurno`. Esforço: **baixo**.

### B8. Edição de escala pode ser sobrescrita pelo listener (lost update)
**P2** · `OperationalViewModel.kt:1609-1644` vs `592,716,832,873,974,1413,1481`
O listener grava sob `escalaMutex`, mas quase todas as edições locais leem/gravam fora do mutex — um snapshot chegando no meio da edição descarta o que o admin digitou (e o auto-save manda a versão errada pro Firestore). **Correção:** centralizar mutações sob o mutex (padrão já existe em `bulkApplyScaleActions`). Esforço: **médio**.

### B9. Trocar de aba salva a escala inteira sem ter mudado nada
**P2** · `OperationalDashboardScreen.kt:238-242`
`LaunchedEffect(turnoAtual) { saveCurrentEscala() }` dispara também na primeira composição → `set()` do documento inteiro a cada entrada/troca de turno (escrita de cota sem motivo). **Correção:** flag `dirty` no VM. Esforço: **baixo**.

### B10. Quinzena: dia trabalhado pode contar EM DOBRO
**P2** · `QuinzenaViewModel.kt:193-257`
Admin E motorista incrementam o mesmo dia sem transação, e o código "permite duplicação" de datas — o dado alimenta pagamento. **Correção:** `runTransaction` + `arrayUnion` + decidir quem incrementa. Esforço: **médio**.

### B11. Reconexão de listener de status é código morto
**P2** · `OperationalViewModel.kt:1527-1533`
O retry só dispara `if (statusListener == null)`, mas o campo nunca é anulado no erro → após uma falha de rede, o painel opera sem realtime para sempre, invisível para o usuário. **Correção:** `statusListener?.remove(); statusListener = null` no handler de erro. Esforço: **baixo**.

### B12. Corrida MainApp gate × FCM ainda existe
**P2** · `MainApp.kt:46-49 vs 150-181`
`registrarVersaoNoStartup` é fire-and-forget e o FCM espera só 1s. Com rede fria (Render acordando), a escrita do token é negada 3x (motorista + usuarios) — push perdida para aquela sessão. A splash agora espera o gate, mas o MainApp não. **Correção:** encadear num único coroutine. Esforço: **baixo**.

### B13. /location/receive rejeita motoristas reais (descoberto ao corrigir S1)
**P1** · `api.py` (`/location/receive`, checagem `if uid != motorista_id`)
O motoristaId no payload é o **ID do documento do Firestore**, e `uid` é o **login anônimo do Firebase Auth** — são sempre diferentes. O backend só aceita quando `uid == motorista_id` ou quando o uid é staff: um motorista de verdade (papel `motorista`) leva **403** ao responder a localização. Junto com B1, **as duas pontas do fluxo de ETA estavam quebradas** — a feature nunca funcionou de ponta a ponta em produção. **Correção:** aceitar também quando `authUid` do doc motorista == uid (mesmo padrão aplicado no S1 ao `/notify/status-change`). Esforço: **baixo**.

---

## 🟡 COTA DO FIRESTORE (risco residual depois dos P1 acima)

| Achado | Local | Efeito | Esforço |
|---|---|---|---|
| Leituras em workers sem proteção quando cota/claim bloqueia | `StatusCheckWorker.kt:53`, `GeofenceBroadcastReceiver.kt:157`, `StatusMonitoringService.kt:400,418` | continuam lendo negado (que consome cota) | baixo |
| Listener órfão: onError não para o serviço (zumbi: 192 leituras/dia negadas) | `StatusMonitoringService.kt:352-368,438` | idem | baixo |
| Auto-reagendamento sem nome único do DailyResetWorker acumula workers órfãos | `DailyResetWorker.kt:183-188` | +1 worker/dia por aparelho | baixo |
| Assistente lê ~150+N+M docs por chamada com cache frio (cold start zera cache) | `firestore_reader.py:264-570` | 15 perguntas/dia ≈ 2,25k leituras | médio |
| Lookups de papel repetidos; 403 para anônimo custa dezenas de leituras | `firestore_reader.py:209-257` | multiplicador em toda chamada | baixo |
| `loadDriverData` recarrega 4-6 leituras em cada evento de status | `DriverViewModel.kt:226,534,542,642` | multiplicador por evento | médio |
| Validar sessões re-lê a mesma base a cada visita (1 leitura/sessão, sem cache) | `AccountSelectionScreen.kt:110-127` | pequeno mas frequente | baixo |

## 🔵 INFRA/TAMANHO (melhorias)

### APK de 78MB — pode cair para ~20-30MB
Cada versão OTA baixa **78MB no 4G** do motorista. Composição medida no APK:
| Item | Hoje | Ação | Depois |
|---|---|---|---|
| ML Kit OCR (.so ×4 ABIs) | 38,4MB | variante Play Services (`play-services-mlkit-text-recognition`) | ~0 |
| ML Kit barcode (.so ×4 ABIs) | 18,2MB | consolidar com ZXing (já incluído) / filtrar ABIs | ~0-4MB |
| dex sem R8 (`isMinifyEnabled=false`, inclui material-icons-extended inteiro) | 18,3MB | R8 + shrinkResources + `resConfigs("pt","en")` | ~7-9MB |
| **Total** | **78MB** | **AAB na Play + APK arm64-only para OTA** | **~20-30MB** |

Passo mínimo (metade do ganho, esforço baixo): publicar o APK **sem ABIs x86/x86_64** → ~44MB.

### Outras melhorias
| Achado | Local | Esforço |
|---|---|---|
| Gunicorn 1 worker síncrono: notificação para base com 20 motoristas bloqueia o serviço TODO (inclusive /health) | `render.yaml:10`, `fcm_sender.py:158-200` | médio |
| `usesCleartextTraffic="true"` global (herança de dev) | `AndroidManifest.xml:41` | baixo |
| `WRITE/READ_EXTERNAL_STORAGE` sem `maxSdkVersion` | `AndroidManifest.xml:20-21` | baixo |
| versao.json sem sha256 do APK (integridade do OTA) | `versao.json`, `AppUpdateChecker.kt` | baixo |
| Dependências com CVE: gunicorn<22 (CVE-2024-1135), flask-cors<5.0.1, requests<2.32.4 | `requirements.txt` | baixo |
| `render.yaml` sem `healthCheckPath: /health` | `render.yaml` | baixo |
| `zxing-android-embedded` morto (só zxing:core é usado) | `build.gradle.kts:124` | baixo |
| versionCode e versao.json mantidos à mão em 2 arquivos (gate quebra se esquecer um) | build.gradle.kts + versao.json | baixo |
| Timestamp vs Long inconsistentes entre módulos (leitura tolerante espalhada) | FirebaseModels.kt etc. | médio |
| Firestore cache ilimitado no aparelho do motorista | `FirebaseManager.kt:26` | baixo |
| Gradle wrapper apontando para milestone (pré-release) | gradle-wrapper.properties | baixo |
| NotifyStatusChangeWorker retry eterno sem teto (HTTP, não cota) | NotifyStatusChangeWorker.kt:95-105 | baixo |
| `runBlocking` em DataStore na main thread a cada notificação | NotificationService.kt:96-107 | médio |
| OkHttp client novo por mensagem do assistente | NotificationApiService.kt:18-22 | baixo |
| Código morto: LocationService.kt inteiro, BackgroundNotificationService.kt inteiro, `escalasCache` escrito e nunca lido | vários | baixo |

## ✅ Verificado e OK
- Listeners do StatusMonitoringService são removidos corretamente no stop/onDestroy
- google-services.json sem service account/privada (normal e público para apps Firebase)
- Keystore real em `Key Store/Chave` não vazou no repo (gitignored)
- Modelos Firestore com defaults em todos os campos (sem crash de deserialização por construtor)
- Receivers do Android 14+ corretos (explícitos, FLAG_IMMUTABLE)
- Componentes do manifest: só MainActivity exported (correto)

---

## Sugestão de fases (você decide a viabilidade)

**Fase 1 — segurança e o que recria o incidente de cota** (todos esforço baixo, ~1 sessão):
S1 (auth nos /notify/*), S5, B1, B2, B3, B5, S6 (assinatura — com plano de migração).

**Fase 2 — robustez de dados e cota residual** (1-2 sessões):
B4, B6, B7, B8, B9, B10, B11, B12, S4, tabela de cota residual (itens de esforço baixo).

**Fase 3 — tamanho e infra** (1 sessão + testes):
APK (ABIs → R8 → OCR GMS), gunicorn gthread, CVEs, manifest, sha256 no OTA.

**Fase 4 — arquitetura (decisão grande):** ACL real com claims de papel/baseId no backend (S2/S3 de verdade). É o único que exige modelagem de um fluxo de aprovação — vale planejar com calma.
