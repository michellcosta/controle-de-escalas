# 🚀 Backend FCM - Envio de Notificações Push

Backend Python simples e gratuito para enviar notificações push via Firebase Cloud Messaging (FCM) HTTP v1, **sem usar Cloud Functions** e **sem ativar billing**.

## 📋 Pré-requisitos

- Python 3.8 ou superior
- Conta Firebase com projeto configurado
- Service Account JSON do Firebase

## 🔧 Instalação

### 1. Instalar dependências

```bash
cd backend-python
pip install -r requirements.txt
```

### 2. Obter Service Account JSON

1. Acesse [Firebase Console](https://console.firebase.google.com)
2. Seu Projeto → ⚙️ Configurações → Contas de Serviço
3. Clique em **"Gerar nova chave privada"**
4. Baixe o arquivo JSON (ex: `service-account-key.json`)
5. **⚠️ IMPORTANTE**: Mantenha este arquivo seguro e **NUNCA** o compartilhe ou faça commit no Git

## 🚀 Como Usar

### Uso Básico

```bash
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "🚛 Você foi escalado!" \
  --body "Você está escalado! Siga para o galpão e aguarde instruções."
```

### Com Dados Customizados

```bash
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "Status Atualizado" \
  --body "Seu status mudou para A_CAMINHO" \
  --data '{"tipo":"escalacao","status":"A_CAMINHO","baseId":"xvtFbdOurhdNKVY08rDw"}'
```

### Modo Dry-Run (Teste)

Para apenas listar os tokens sem enviar notificações:

```bash
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "Teste" \
  --body "Teste" \
  --dry-run
```

### Usando Variável de Ambiente

Ao invés de passar `--service-account`, você pode usar variável de ambiente:

```bash
export FIREBASE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
python main.py --base-id xvtFbdOurhdNKVY08rDw --title "Teste" --body "Teste"
```

## 📁 Estrutura dos Arquivos

```
backend-python/
├── firestore_reader.py    # Lê tokens FCM do Firestore
├── fcm_sender.py          # Envia notificações via FCM HTTP v1
├── main.py                # Arquivo principal (orquestra tudo)
├── requirements.txt       # Dependências Python
└── README.md             # Este arquivo
```

## 🔍 Como Funciona

1. **firestore_reader.py**: 
   - Conecta ao Firestore usando Firebase Admin SDK
   - Busca todos os motoristas de uma base
   - Filtra apenas os que possuem `fcmToken` válido
   - Retorna lista de tokens

2. **fcm_sender.py**:
   - Autentica no Firebase usando Service Account
   - Obtém token OAuth2
   - Envia notificação via FCM HTTP v1 API
   - Suporta Android e iOS

3. **main.py**:
   - Orquestra todo o processo
   - Lê tokens do Firestore
   - Envia notificações para todos os tokens
   - Mostra estatísticas de sucesso/falha

## 📊 Estrutura do Firestore

O backend espera a seguinte estrutura:

```
bases/
└── {baseId}/
    └── motoristas/
        └── {motoristaId}/
            ├── nome: string
            └── fcmToken: string  ← Token FCM do dispositivo
```

## 🔐 Segurança

- **Service Account JSON**: Contém credenciais sensíveis. **NUNCA**:
  - Faça commit no Git
  - Compartilhe publicamente
  - Envie por email não criptografado
  - Coloque no código do app Android

- **Variável de Ambiente**: Para produção, use variáveis de ambiente ao invés de arquivos JSON

## 🌐 Deploy (Opcional)

### Railway (Gratuito)

1. Crie conta em [Railway](https://railway.app)
2. Crie novo projeto
3. Adicione variável de ambiente: `FIREBASE_SERVICE_ACCOUNT_JSON` (cole o JSON completo)
4. Configure build: `pip install -r requirements.txt`
5. Configure start: `python main.py --base-id ... --title ... --body ...`

### Render (Gratuito)

1. Crie conta em [Render](https://render.com)
2. Crie novo "Background Worker"
3. Configure variáveis de ambiente
4. Deploy automático via GitHub

### Servidor Próprio

```bash
# No servidor
git clone <seu-repo>
cd backend-python
pip install -r requirements.txt
python main.py --base-id ... --title ... --body ...
```

## 🔄 Automação (Opcional)

Para monitorar mudanças no Firestore e enviar automaticamente:

### Opção 1: Script com Loop

```python
# monitor.py (exemplo)
import time
from firestore_reader import FirestoreReader
from fcm_sender import FCMSender

reader = FirestoreReader("service-account.json")
sender = FCMSender("service-account.json")

while True:
    # Verificar mudanças (implementar lógica)
    # Enviar notificações se necessário
    time.sleep(60)  # Verificar a cada 60 segundos
```

### Opção 2: Webhook/API

Crie um servidor Flask/FastAPI simples que recebe requisições HTTP e envia notificações.

## 📝 Exemplos de Uso

### Enviar para Todos os Motoristas de uma Base

```bash
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "🚛 Você foi escalado!" \
  --body "Você está escalado! Siga para o galpão e aguarde instruções."
```

### Notificação de Chamada para Vaga

```bash
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "🚚 Suba para a vaga 01" \
  --body "Rota: S-7" \
  --data '{"tipo":"chamada","vaga":"01","rota":"S-7"}'
```

### Notificação de Status Atualizado

```bash
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "Status Atualizado" \
  --body "Seu status mudou para CARREGANDO" \
  --data '{"tipo":"status","status":"CARREGANDO"}'
```

## ❓ Troubleshooting

### Erro: "Service Account JSON não encontrado"
- Verifique o caminho do arquivo
- Use caminho absoluto se necessário

### Erro: "project_id não encontrado"
- O project_id deve estar no Service Account JSON
- Ou forneça explicitamente com `--project-id`

### Erro: "Nenhum token FCM encontrado"
- Verifique se o `base-id` está correto
- Verifique se os motoristas possuem `fcmToken` no Firestore
- Use `--dry-run` para listar tokens

### Notificações não chegam
- Verifique se o app Android está configurado corretamente
- Verifique se o `FirebaseMessagingService` está implementado
- Verifique logs do Firebase Console

## 📚 Referências

- [FCM HTTP v1 API](https://firebase.google.com/docs/cloud-messaging/migrate-v1)
- [Firebase Admin SDK Python](https://firebase.google.com/docs/admin/setup)
- [Service Accounts](https://cloud.google.com/iam/docs/service-accounts)

## ✅ Compatibilidade

- ✅ Funciona com app Android fechado
- ✅ Funciona com app Android em segundo plano
- ✅ Funciona com app Android em primeiro plano
- ✅ Não requer Cloud Functions
- ✅ Não requer billing ativado
- ✅ Não altera o app Android existente
