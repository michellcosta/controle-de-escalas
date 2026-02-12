# 🚀 Deploy das Firebase Cloud Functions

## 📋 Pré-requisitos

1. Node.js 20 instalado
2. Firebase CLI instalado globalmente
3. Acesso ao Firebase Console do projeto

## 🛠️ Instalação

### 1. Instalar Firebase Tools

```bash
npm i -g firebase-tools
```

### 2. Login no Firebase

```bash
firebase login
```

### 3. Inicializar Functions (se ainda não fez)

```bash
cd functions
npm install
```

## 📦 Estrutura de Arquivos

```
functions/
├── src/
│   └── index.ts          # Funções Cloud Functions
├── package.json          # Dependências
└── tsconfig.json         # Configuração TypeScript
```

## 🚀 Deploy

### Deploy de todas as functions

```bash
firebase deploy --only functions
```

### Deploy de uma function específica

```bash
firebase deploy --only functions:loginWithPhonePin
firebase deploy --only functions:adminSetPin
```

## 🔐 Funções Implementadas

### 1. `loginWithPhonePin`

**Parâmetros:**
- `phone` (string): Telefone do usuário
- `baseId` (string): ID da base
- `pin` (string): PIN do usuário

**Retorna:**
- `token` (string): Token customizado do Firebase Auth
- `uid` (string): ID do usuário

**Uso:**
```kotlin
val data = mapOf(
    "phone" to "21968686880",
    "baseId" to "base_id",
    "pin" to "123456"
)
val result = functions.getHttpsCallable("loginWithPhonePin").call(data).await()
```

### 2. `adminSetPin`

**Parâmetros:**
- `targetUid` (string): ID do usuário que terá o PIN alterado
- `baseId` (string): ID da base
- `newPin` (string): Novo PIN (será hashado com bcrypt)

**Retorna:**
- `ok` (boolean): Confirmação de sucesso

**Uso:**
```kotlin
val data = mapOf(
    "targetUid" to "user_id",
    "baseId" to "base_id",
    "newPin" to "123456"
)
val result = functions.getHttpsCallable("adminSetPin").call(data).await()
```

## 🔍 Ver Logs

```bash
firebase functions:log
```

## ⚠️ Importante

1. **Região**: As functions estão configuradas para `southamerica-east1`
2. **Bcrypt**: Os PINs são hashados com bcrypt (salt rounds: 10)
3. **Autenticação**: `adminSetPin` requer autenticação Firebase
4. **Permissões**: Apenas admin/ajudante podem usar `adminSetPin`

## 🐛 Testando

### 1. Verificar deploy

Acesse o [Firebase Console](https://console.firebase.google.com) e vá em **Functions**

### 2. Testar no app

1. Criar uma base com PIN conhecido
2. Fazer login com telefone + PIN
3. Verificar se o token foi gerado

### 3. Debug

Ver logs em tempo real:
```bash
firebase functions:log --only loginWithPhonePin
```

## 📝 Notas

- As functions estão configuradas para usar **Firebase Auth Custom Tokens**
- Os PINs são validados com **bcrypt.compare()**
- A collection usada é: `/bases/{baseId}/usuarios`
- Em produção, considere adicionar rate limiting



