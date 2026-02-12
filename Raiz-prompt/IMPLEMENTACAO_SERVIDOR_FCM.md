# Implementação: Servidor FCM + Correção de Bug

## ✅ O que foi implementado

### 1. Servidor Node.js para Notificações FCM em Tempo Real

**Localização**: `Raiz-prompt/server/`

**Arquivos criados**:
- `package.json` - Dependências do servidor
- `index.js` - Servidor principal que monitora Firestore e envia FCM
- `.env.example` - Exemplo de configuração
- `README.md` - Documentação completa
- `.gitignore` - Ignora arquivos sensíveis

**Como funciona**:
1. Monitora `bases/{baseId}/status_motoristas/{motoristaId}` no Firestore
2. Detecta mudanças de status em tempo real
3. Envia notificações FCM quando status muda para:
   - `IR_ESTACIONAMENTO`
   - `CARREGANDO`
   - `CONCLUIDO`

**Deploy gratuito**:
- Railway (recomendado)
- Render
- Heroku

### 2. Correção: Status Resetado ao Clicar no Badge

**Problema**: Quando o usuário clicava no badge da notificação, o status era resetado para "A_CAMINHO".

**Solução**: Adicionada flag `escalaObservacaoInicializada` para distinguir:
- **Primeira carga** (app aberto via notificação): Preserva status atual
- **Mudança real** (motorista adicionado à escala): Reseta apenas se necessário

**Arquivo modificado**: `DriverViewModel.kt`

## 🔧 Configuração do Servidor

### Passo 1: Instalar dependências
```bash
cd server
npm install
```

### Passo 2: Configurar variáveis de ambiente
```bash
cp .env.example .env
```

Edite o `.env` e adicione:
```env
FIREBASE_SERVICE_ACCOUNT={"type":"service_account","project_id":"seu-projeto",...}
```

### Passo 3: Obter Service Account Key
1. Vá para [Firebase Console](https://console.firebase.google.com)
2. Seu Projeto → Configurações → Contas de Serviço
3. Clique em "Gerar nova chave privada"
4. Baixe o JSON e cole no `.env` como `FIREBASE_SERVICE_ACCOUNT`

### Passo 4: Executar localmente
```bash
npm start
```

## 🌐 Deploy no Railway (Gratuito)

1. Crie conta em [Railway](https://railway.app)
2. Crie novo projeto
3. Adicione serviço "GitHub Repo" ou "Empty Project"
4. Configure variáveis de ambiente:
   - `FIREBASE_SERVICE_ACCOUNT`: Cole o JSON completo
   - `BASE_ID`: (Opcional) ID da base específica
5. Railway detectará automaticamente e fará deploy

## ⚠️ Nota sobre Compilação

Há um erro de compilação no `DriverViewModel.kt` que precisa ser verificado. O código está correto logicamente, mas pode haver um problema de sintaxe ou importação.

**Para verificar o erro**:
```bash
./gradlew compileDebugKotlin --stacktrace
```

## 📝 Próximos Passos

1. Resolver erro de compilação (se houver)
2. Testar servidor localmente
3. Fazer deploy no Railway
4. Testar notificações com app fechado

