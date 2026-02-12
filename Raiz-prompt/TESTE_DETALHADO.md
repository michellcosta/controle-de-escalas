# 🔧 Guia de Teste Detalhado - Problema de Login

## **🎯 Objetivo**
Identificar exatamente onde está o problema do "telefone não encontrado" após criar uma base.

## **📱 Passo a Passo para Teste**

### **1. Executar o App**
- Abra o Android Studio
- Execute o app no emulador ou dispositivo
- Abra o **Logcat** para ver os logs

### **2. Criar uma Nova Base**
```
Nome da Transportadora: Transportadora Teste
Nome da Base: Base Teste  
Telefone do Admin: 11999999999
PIN do Admin: 123456
```

### **3. Observar Logs de Criação**
Procure por estes logs no Logcat:

```
🏗️ Repository: Criando base: Base Teste
✅ Repository: Base criada com ID: [ID_DA_BASE]
👤 Repository: Criando motorista: Admin (admin) - Telefone: 11999999999
📱 Repository: Telefone original: '11999999999' -> Normalizado: '11999999999'
✅ Repository: Motorista criado com ID: [ID_DO_MOTORISTA]
```

### **4. Tentar Fazer Login**
```
Telefone: 11999999999
PIN: 123456
```

### **5. Observar Logs de Login**
Procure por estes logs no Logcat:

```
🔍 AuthRepository: Tentando login com telefone: 11999999999
🔍 DEBUG: Listando todos os motoristas...
📊 DEBUG: Encontradas X bases
🏢 DEBUG: Base: Base Teste (ID: [ID_DA_BASE])
👥 DEBUG: 1 motoristas nesta base:
  - Admin | 11999999999 | admin | Ativo: true
🔍 Repository: Buscando telefone original: '11999999999' -> Normalizado: '11999999999'
```

## **🔍 Análise dos Logs**

### **✅ Se tudo estiver funcionando:**
```
✅ Repository: Motorista encontrado via collectionGroup: Admin
✅ AuthRepository: Motorista encontrado: Admin (admin)
🔐 AuthRepository: PIN fornecido hash: [HASH]
🔐 AuthRepository: PIN armazenado: [HASH]
✅ AuthRepository: Login bem-sucedido para Admin
```

### **❌ Se houver problemas:**

#### **Problema 1: Base não foi criada**
```
❌ Repository: Erro ao criar base: [ERRO]
```
**Solução**: Verificar conexão com Firebase

#### **Problema 2: Motorista não foi criado**
```
✅ Repository: Base criada com ID: [ID]
❌ Repository: Erro ao criar motorista: [ERRO]
```
**Solução**: Verificar estrutura de dados do Firestore

#### **Problema 3: Motorista não encontrado**
```
🔍 DEBUG: Listando todos os motoristas...
📊 DEBUG: Encontradas 0 bases
```
**Solução**: Verificar se os dados foram salvos no Firestore

#### **Problema 4: Telefone não normalizado**
```
📱 Repository: Telefone original: '(11) 99999-9999' -> Normalizado: '11999999999'
```
**Solução**: Verificar se está usando o telefone normalizado

## **🚨 Problemas Comuns e Soluções**

### **1. Firebase não conectado**
**Sintomas**: Erros de conexão nos logs
**Solução**: 
- Verificar arquivo `google-services.json`
- Verificar regras do Firestore
- Verificar conexão com internet

### **2. Índice não configurado**
**Sintomas**: 
```
⚠️ Repository: CollectionGroup falhou, usando busca manual
```
**Solução**: 
- Configurar índice no Firebase Console
- Aguardar criação do índice

### **3. Formatação de telefone**
**Sintomas**: Telefone salvo diferente do buscado
**Solução**: 
- Usar apenas números no telefone
- Verificar normalização nos logs

### **4. Dados não sincronizados**
**Sintomas**: Base criada mas motorista não encontrado
**Solução**: 
- Aguardar sincronização do Firestore
- Verificar regras de segurança

## **📋 Checklist de Verificação**

- [ ] **Firebase conectado**: Logs mostram conexão
- [ ] **Base criada**: Log mostra ID da base
- [ ] **Motorista criado**: Log mostra ID do motorista
- [ ] **Telefone normalizado**: Logs mostram normalização
- [ ] **Busca funcionando**: Logs mostram busca
- [ ] **PIN correto**: Logs mostram comparação de hash

## **🔧 Comandos de Debug**

### **Para verificar dados no Firebase Console:**
1. Acesse [console.firebase.google.com](https://console.firebase.google.com)
2. Vá em **Firestore Database**
3. Verifique se há dados em:
   - `bases` (coleção principal)
   - `bases/{baseId}/motoristas` (subcoleção)

### **Para limpar dados de teste:**
1. No Firebase Console
2. Delete as bases de teste
3. Crie uma nova base para teste

## **📞 Teste com Diferentes Formatos**

Teste com estes formatos de telefone:
- `11999999999` (apenas números)
- `(11) 99999-9999` (com parênteses e hífen)
- `+55 11 99999-9999` (com código do país)

**Todos devem ser normalizados para**: `11999999999`

## **🎯 Resultado Esperado**

Após seguir este guia, você deve conseguir:

1. ✅ **Criar base** com sucesso
2. ✅ **Ver logs detalhados** de criação
3. ✅ **Fazer login** com sucesso
4. ✅ **Identificar problemas** específicos nos logs

**Se ainda houver problemas, os logs detalhados mostrarão exatamente onde está o erro!** 🔍



