# 📱 Configuração do App Android para API Python

## ✅ O que foi feito

O app Android foi atualizado para usar a API Python ao invés de Cloud Functions.

## 🔧 Arquivos Modificados

1. **`NotificationApiConfig.kt`** - Configuração da URL da API
2. **`NotificationApiService.kt`** - Serviço para chamar a API Python
3. **`OperationalViewModel.kt`** - Atualizado para usar API Python
4. **`AdminViewModel.kt`** - Atualizado para usar API Python
5. **`build.gradle.kts`** - Adicionado OkHttp

## ⚙️ Configuração Necessária

### 1. Alterar URL da API

Abra o arquivo:
```
Raiz-prompt/app/src/main/java/com/controleescalas/app/data/NotificationApiConfig.kt
```

Altere a constante `BASE_URL`:

```kotlin
const val BASE_URL = "https://seu-backend.railway.app"  // ⬅️ ALTERE AQUI
```

**Opções de URL:**

- **Produção (Railway/Render):** `https://seu-app.railway.app`
- **Desenvolvimento local (Emulador):** `http://10.0.2.2:5000`
- **Desenvolvimento local (Dispositivo físico):** `http://SEU_IP_LOCAL:5000`

### 2. Rodar a API Python

```bash
cd backend-python
pip install -r requirements.txt
python api.py
```

A API estará rodando em `http://localhost:5000`

### 3. Testar

1. Compile o app Android
2. Quando o admin chamar um motorista, o app fará uma requisição HTTP para a API Python
3. A API Python enviará a notificação push via FCM
4. O motorista receberá a notificação mesmo com o app fechado

## ⚡ Tempo Real

**SIM, as notificações são em tempo real!**

- **Latência típica:** 100-500ms
- **FCM HTTP v1:** Instantâneo
- **API Python:** Processa em milissegundos

O fluxo completo:
```
App Android → API Python (100-200ms) → FCM (100-300ms) → Dispositivo Motorista
```

**Total: ~200-500ms** (menos de meio segundo!)

## 🔍 Verificar se está funcionando

### Logs no Android Studio

Procure por:
```
📤 Enviando notificação via API Python
📥 Resposta da API: 200
✅ Notificação enviada com sucesso via API Python
```

### Logs na API Python

Você verá:
```
📤 Enviando notificações para 1 dispositivos...
  ✅ Notificação enviada com sucesso: projects/...
📊 Resultado: 1 sucessos, 0 falhas
```

## 🚨 Troubleshooting

### Erro: "Connection refused"

- Verifique se a API Python está rodando
- Verifique se a URL está correta no `NotificationApiConfig.kt`
- Para emulador, use `http://10.0.2.2:5000`
- Para dispositivo físico, use o IP local da sua máquina

### Erro: "Timeout"

- Verifique sua conexão de internet
- Aumente o timeout em `NotificationApiConfig.TIMEOUT_SECONDS`

### Notificação não chega

- Verifique se o motorista tem `fcmToken` no Firestore
- Verifique os logs da API Python
- Verifique se o Service Account está configurado corretamente

## 📝 Próximos Passos

1. **Deploy da API Python:**
   - Railway: https://railway.app
   - Render: https://render.com
   - Fly.io: https://fly.io

2. **Atualizar URL no app:**
   - Altere `BASE_URL` em `NotificationApiConfig.kt`
   - Recompile o app

3. **Testar em produção:**
   - Faça um teste completo
   - Verifique logs

## ✅ Vantagens

- ✅ **Gratuito** - Não precisa de billing do Firebase
- ✅ **Tempo real** - Latência de 200-500ms
- ✅ **Funciona com app fechado** - FCM push notifications
- ✅ **Controle total** - Você controla o backend
