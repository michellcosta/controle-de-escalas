# 🔐 Guia de Integração - Firebase Cloud Functions + Android App

## 📋 Resumo

Este guia explica como integrar o sistema de autenticação usando Firebase Cloud Functions com bcrypt para hashing seguro de PINs.

## 🏗️ Arquitetura

```
Android App
    ↓
AuthRepository.login()
    ↓
Cloud Function: loginWithPhonePin
    ↓
Firebase Firestore (valida PIN com bcrypt)
    ↓
Firebase Auth (Custom Token)
    ↓
Login bem-sucedido
```

## 🚀 Passo a Passo

### 1. Deploy das Cloud Functions

Siga o guia `FIREBASE_FUNCTIONS_DEPLOY.md`:

```bash
cd functions
npm install
firebase deploy --only functions
```

### 2. Estrutura de Dados no Firestore

Coleção: `/bases/{baseId}/usuarios/`

```json
{
  "nome": "Admin",
  "telefone": "21968686880",
  "pinHash": "$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "papel": "admin",
  "baseId": "base_id",
  "ativo": true
}
```

**Importante**: Use `pinHash` (não `pin`) e armazene o hash bcrypt.

### 3. Uso no App Android

#### Login

```kotlin
val authRepository = AuthRepository()
val result = authRepository.login("21968686880", "123456")

when (result) {
    is LoginResult.Success -> {
        // Login bem-sucedido
        val motoristaId = result.motoristaId
        val baseId = result.baseId
        val papel = result.papel
        val nome = result.nome
        
        // Navegar para tela apropriada
    }
    is LoginResult.Error -> {
        // Erro no login
        val message = result.message
    }
}
```

#### Definir PIN (Admin)

```kotlin
val authRepository = AuthRepository()
val success = authRepository.adminSetPin("user_id", "base_id", "123456")
```

## 🔑 Como Criar Usuários com PIN

### Opção 1: Durante Criação da Base

Ao criar uma base, o admin é criado automaticamente:

```kotlin
val baseId = repository.createBase(CreateBaseData(...))

// O admin é criado com PIN hashado via AuthRepository.createUser()
```

### Opção 2: Admin Define PIN Depois

1. Admin faz login
2. Vai para tela de gerenciamento de usuários
3. Cria novo motorista (sem PIN)
4. Define PIN usando `authRepository.adminSetPin()`

## 🔍 Debug

### Ver logs das Cloud Functions

```bash
firebase functions:log
```

### Ver logs do app

Filtre por `AuthRepository` no Logcat:

```
🔍 AuthRepository: Tentando login com telefone: 21968686880
✅ AuthRepository: Motorista encontrado: Admin em base base_123
🚀 AuthRepository: Chamando Cloud Function loginWithPhonePin...
✅ AuthRepository: Token recebido, fazendo signIn...
✅ AuthRepository: Login bem-sucedido para Admin
```

## ⚠️ Problemas Comuns

### 1. "UNAVAILABLE" ao chamar Cloud Function

**Causa**: Functions não foram deployadas

**Solução**: 
```bash
firebase deploy --only functions
```

### 2. "PERMISSION_DENIED"

**Causa**: Firestore rules não permitem leitura

**Solução**: Ajustar regras no Firebase Console

### 3. "INVALID_ARGUMENT"

**Causa**: Parâmetros faltando

**Solução**: Verificar se `phone`, `baseId` e `pin` estão sendo enviados

### 4. "NOT_FOUND"

**Causa**: Usuário não encontrado no Firestore

**Solução**: Verificar se o usuário existe em `/bases/{baseId}/usuarios/`

### 5. "UNAUTHENTICATED"

**Causa**: PIN incorreto

**Solução**: Verificar se o PIN está correto

## 📝 Notas Importantes

1. **Bcrypt no servidor**: PINs são hashados com bcrypt apenas nas Cloud Functions
2. **Custom Token**: O login usa Firebase Auth Custom Tokens
3. **Segurança**: PINs nunca são enviados em texto plano
4. **Região**: Functions configuradas para `southamerica-east1`
5. **Collection**: Usa `/bases/{baseId}/usuarios/` (não `/bases/{baseId}/motoristas/`)

## 🎯 Próximos Passos

1. Deploy das functions
2. Criar uma base de teste
3. Fazer login com telefone + PIN
4. Verificar se o token foi gerado
5. Testar navegação para telas apropriadas



