# 🚀 GUIA DE DEPLOY - CLOUD FUNCTIONS

## 📋 Pré-requisitos

- ✅ Node.js 20 instalado
- ✅ Firebase CLI instalado (`npm install -g firebase-tools`)
- ✅ Projeto Firebase configurado
- ✅ Autenticado no Firebase (`firebase login`)

---

## 🛠️ PASSO A PASSO

### **1. Instalar Dependências**

```bash
cd Raiz-prompt/functions
npm install
```

**Pacotes incluídos:**
- `firebase-admin` - SDK Admin do Firebase
- `firebase-functions` - Framework para Cloud Functions
- `bcryptjs` - Hash de senhas (PIN)

---

### **2. Compilar TypeScript**

```bash
npm run build
```

**O que faz:**
- Compila `src/index.ts` → `lib/index.js`
- Valida tipos TypeScript
- Gera source maps

---

### **3. Testar Localmente (Opcional)**

```bash
npm run serve
```

**Emulador Firebase:**
- Roda functions localmente
- Firestore emulado
- Ideal para testes antes do deploy

---

### **4. Deploy para Produção**

```bash
npm run deploy
```

**OU diretamente:**

```bash
firebase deploy --only functions
```

---

## 📱 FUNÇÕES QUE SERÃO DEPLOYED

### **1. loginWithPhonePin** (Callable)
- **Região:** southamerica-east1
- **Uso:** Login por telefone + PIN
- **Retorna:** Token customizado

### **2. adminSetPin** (Callable)
- **Região:** southamerica-east1
- **Uso:** Admin altera PIN de usuário
- **Requer:** Autenticação

### **3. onMotoristaAddedToOnda** (Trigger)
- **Trigger:** `bases/{baseId}/escalas/{escalaId}` (onWrite)
- **Ação:** Notifica motorista quando escalado

### **4. onEscalaChanged** (Trigger)
- **Trigger:** `bases/{baseId}/escalas/{escalaId}` (onUpdate)
- **Ação:** Notifica motorista quando escala muda

### **5. onMotoristaStatusChanged** (Trigger)
- **Trigger:** `bases/{baseId}/status_motoristas/{statusId}` (onUpdate)
- **Ação:** Notifica motorista e admin sobre mudanças de status

### **6. onDisponibilidadeResponse** (Trigger)
- **Trigger:** `bases/{baseId}/disponibilidades/{dispId}` (onUpdate)
- **Ação:** Notifica admin quando motorista responde

### **7. chamarMotoristaCarregamento** (Callable)
- **Região:** southamerica-east1
- **Uso:** Admin chama motorista para vaga
- **Ação:** Atualiza status + Envia push

---

## 🔧 CONFIGURAÇÃO ADICIONAL

### **Firestore Indexes**

Algumas queries complexas requerem índices compostos:

```bash
firebase deploy --only firestore:indexes
```

**Índices necessários:**
- `motoristas`: `(telefone, ativo)`
- `escalas`: `(baseId, data, turno)`
- `status_motoristas`: `(baseId, estado)`

---

### **Firestore Rules**

Deploy das regras de segurança:

```bash
firebase deploy --only firestore:rules
```

---

## 📊 MONITORAMENTO

### **Ver Logs em Tempo Real**

```bash
firebase functions:log
```

### **Logs Específicos**

```bash
firebase functions:log --only onMotoristaAddedToOnda
```

### **Dashboard Firebase**
- Acesse: https://console.firebase.google.com
- Navegue: `Functions` → Ver métricas e logs

---

## 🐛 TROUBLESHOOTING

### **Erro: "Billing account not configured"**

**Solução:**
1. Acesse Firebase Console
2. Vá em `Billing` (Faturamento)
3. Configure um método de pagamento
4. Plano Blaze é necessário para Cloud Functions

---

### **Erro: "Region not supported"**

**Solução:**
- Certifique-se que `southamerica-east1` está disponível
- OU mude para `us-central1` no código

```typescript
.region("us-central1") // Região alternativa
```

---

### **Erro: "Firebase token is invalid"**

**Solução:**
```bash
firebase logout
firebase login
firebase use --add  # Selecione seu projeto
```

---

### **Erro de Permissões**

**Solução:**
```bash
# Garantir que Service Account tem permissões
gcloud projects add-iam-policy-binding [PROJECT_ID] \
  --member="serviceAccount:[SERVICE_ACCOUNT]" \
  --role="roles/firebase.admin"
```

---

## ✅ VERIFICAÇÃO PÓS-DEPLOY

### **1. Verificar Functions Ativas**

```bash
firebase functions:list
```

**Deve listar:**
- ✅ loginWithPhonePin
- ✅ adminSetPin
- ✅ onMotoristaAddedToOnda
- ✅ onEscalaChanged
- ✅ onMotoristaStatusChanged
- ✅ onDisponibilidadeResponse
- ✅ chamarMotoristaCarregamento

---

### **2. Testar Login**

No app Android, tente fazer login:
- Se funcionar: ✅ Functions OK
- Se falhar: Verifique logs

---

### **3. Testar Notificações**

1. Adicione um motorista a uma onda
2. Verifique se a notificação chegou
3. Confira nos logs:

```bash
firebase functions:log --only onMotoristaAddedToOnda
```

---

## 💰 CUSTOS ESTIMADOS

### **Free Tier (Spark Plan)**
- ❌ Cloud Functions **NÃO disponível**

### **Blaze Plan (Pay-as-you-go)**

**Incluído gratuitamente:**
- 2M invocações/mês
- 400.000 GB-segundos/mês
- 200.000 CPU-segundos/mês
- 5GB de saída de rede/mês

**Custos Típicos (Brasil):**
- Até 10.000 usuários: **R$ 0-50/mês**
- Até 50.000 usuários: **R$ 50-200/mês**

**Monitorar gastos:**
https://console.firebase.google.com/project/_/usage

---

## 🔐 SEGURANÇA

### **Variáveis de Ambiente**

Para chaves sensíveis:

```bash
firebase functions:config:set someservice.key="THE_KEY"
firebase deploy --only functions
```

**Acessar no código:**
```typescript
const key = functions.config().someservice.key;
```

---

### **Regras de Segurança**

Certifique-se que as Cloud Functions têm acesso adequado ao Firestore:

```javascript
// firestore.rules
service cloud.firestore {
  match /databases/{database}/documents {
    match /bases/{baseId}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

---

## 📱 INTEGRAÇÃO NO APP

### **Chamar Function Callable**

```kotlin
// No app Android
val functions = Firebase.functions("southamerica-east1")

val data = hashMapOf(
    "phone" to telefone,
    "pin" to pin
)

functions
    .getHttpsCallable("loginWithPhonePin")
    .call(data)
    .addOnSuccessListener { result ->
        val token = result.data["token"]
        // Use o token
    }
    .addOnFailureListener { e ->
        Log.e("Functions", "Erro: ${e.message}")
    }
```

---

## 🎯 CHECKLIST FINAL

Antes de considerar deploy completo:

- [ ] Functions compilando sem erros
- [ ] Todas as 7 functions deployed
- [ ] Logs sem erros no Console
- [ ] Login funcionando no app
- [ ] Notificação de teste recebida
- [ ] Firestore rules atualizadas
- [ ] Billing configurado (Blaze Plan)
- [ ] Monitoramento ativo

---

## 📞 SUPORTE

**Firebase Console:**
https://console.firebase.google.com

**Documentação:**
https://firebase.google.com/docs/functions

**Status Firebase:**
https://status.firebase.google.com

---

**Última atualização:** Novembro 2024  
**Versão:** 1.0  
**Status:** ✅ Pronto para Deploy

