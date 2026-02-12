# 🌐 API REST - Backend FCM

API Flask simples para integrar o backend Python com o app Android.

## 🚀 Como Usar

### 1. Instalar Dependências

```bash
cd backend-python
pip install -r requirements.txt
```

### 2. Configurar Service Account

Coloque o arquivo `service-account-key.json` na pasta `backend-python` ou defina a variável de ambiente:

```bash
export FIREBASE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
```

### 3. Iniciar a API

```bash
python api.py
```

A API estará disponível em: `http://localhost:5000`

### 4. Testar a API

```bash
# Health check
curl http://localhost:5000/health

# Notificar motorista específico
curl -X POST http://localhost:5000/notify/motorista \
  -H "Content-Type: application/json" \
  -d '{
    "baseId": "xvtFbdOurhdNKVY08rDw",
    "motoristaId": "abc123",
    "title": "🚚 Chamada para Carregamento",
    "body": "Subir agora para a vaga 01 com rota S-7",
    "data": {
      "tipo": "chamada",
      "vaga": "01",
      "rota": "S-7"
    }
  }'
```

## 📡 Endpoints

### `GET /health`
Health check da API

**Resposta:**
```json
{
  "status": "ok",
  "message": "API FCM está funcionando"
}
```

### `POST /notify/motorista`
Envia notificação push para um motorista específico

**Body:**
```json
{
  "baseId": "xvtFbdOurhdNKVY08rDw",
  "motoristaId": "abc123",
  "title": "🚚 Chamada para Carregamento",
  "body": "Subir agora para a vaga 01 com rota S-7",
  "data": {
    "tipo": "chamada",
    "vaga": "01",
    "rota": "S-7"
  }
}
```

**Resposta (sucesso):**
```json
{
  "success": true,
  "message": "Notificação enviada para João Silva",
  "motorista": "João Silva"
}
```

### `POST /notify/base`
Envia notificação push para todos os motoristas de uma base

**Body:**
```json
{
  "baseId": "xvtFbdOurhdNKVY08rDw",
  "title": "🚛 Você foi escalado!",
  "body": "Você está escalado! Siga para o galpão e aguarde instruções.",
  "data": {
    "tipo": "escalacao"
  }
}
```

**Resposta:**
```json
{
  "success": true,
  "message": "Notificações enviadas para 5 motoristas",
  "resultado": {
    "sucessos": 5,
    "falhas": 0
  }
}
```

### `GET /motorista/token`
Verifica se um motorista tem token FCM

**Query params:**
- `baseId`: ID da base
- `motoristaId`: ID do motorista

**Exemplo:**
```
GET /motorista/token?baseId=xvtFbdOurhdNKVY08rDw&motoristaId=abc123
```

## 🔒 Segurança (Produção)

Para produção, adicione autenticação:

1. **API Key** (simples):
```python
API_KEY = os.getenv('API_KEY', 'sua-chave-secreta')

@app.before_request
def check_api_key():
    if request.endpoint != 'health':
        api_key = request.headers.get('X-API-Key')
        if api_key != API_KEY:
            return jsonify({"error": "Unauthorized"}), 401
```

2. **JWT Token** (mais seguro):
```python
# Adicionar verificação de JWT do Firebase Auth
```

## 🌐 Deploy

### Railway (Gratuito)

1. Crie conta em [Railway](https://railway.app)
2. Conecte seu repositório GitHub
3. Configure variáveis de ambiente:
   - `FIREBASE_SERVICE_ACCOUNT_JSON` (cole o JSON completo)
4. Configure start command: `python api.py`
5. Railway fornecerá uma URL pública (ex: `https://seu-app.railway.app`)

### Render (Gratuito)

1. Crie conta em [Render](https://render.com)
2. Crie novo "Web Service"
3. Configure:
   - Build: `pip install -r requirements.txt`
   - Start: `python api.py`
4. Adicione variável de ambiente: `FIREBASE_SERVICE_ACCOUNT_JSON`

### Servidor Próprio

```bash
# Instalar gunicorn
pip install gunicorn

# Rodar com gunicorn (produção)
gunicorn -w 4 -b 0.0.0.0:5000 api:app
```

## 📱 Integração com App Android

Para usar esta API no app Android, você precisa:

1. **Atualizar o ViewModel** para chamar a API HTTP ao invés da Cloud Function
2. **Adicionar URL da API** nas configurações do app
3. **Fazer requisição HTTP** usando Retrofit ou OkHttp

Exemplo de código Kotlin (usando Retrofit):

```kotlin
interface NotificationApi {
    @POST("/notify/motorista")
    suspend fun notifyMotorista(@Body request: NotifyRequest): Response<NotifyResponse>
}

data class NotifyRequest(
    val baseId: String,
    val motoristaId: String,
    val title: String,
    val body: String,
    val data: Map<String, String>? = null
)
```

## ✅ Vantagens

- ✅ Funciona com app fechado
- ✅ Não usa Cloud Functions
- ✅ Não requer billing
- ✅ Gratuito (pode rodar em Railway/Render)
- ✅ Controle total sobre o backend

## ⚠️ Observações

- A API precisa estar rodando para funcionar
- Para produção, use um serviço de hospedagem (Railway, Render, etc.)
- Adicione autenticação antes de colocar em produção
- O Service Account JSON nunca deve ir para o app Android
