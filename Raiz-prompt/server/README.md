# Servidor de Notificações FCM

Servidor Node.js que monitora mudanças de status dos motoristas no Firestore e envia notificações FCM em tempo real, mesmo quando o app está fechado.

## 🚀 Como Funciona

1. **Monitora Firestore**: O servidor fica "escutando" mudanças em `bases/{baseId}/status_motoristas/{motoristaId}`
2. **Detecta Mudanças**: Quando o status de um motorista muda (ex: `A_CAMINHO` → `CARREGANDO`)
3. **Envia FCM**: Envia notificação push via Firebase Cloud Messaging
4. **Tempo Real**: Funciona mesmo quando o app está completamente fechado

## 📋 Pré-requisitos

- Node.js 18+ instalado
- Conta Firebase com projeto configurado
- Service Account Key do Firebase (JSON)

## 🔧 Configuração Local

1. **Instalar dependências**:
   ```bash
   cd server
   npm install
   ```

2. **Configurar variáveis de ambiente**:
   ```bash
   cp .env.example .env
   ```
   
   Edite o `.env` e adicione seu `FIREBASE_SERVICE_ACCOUNT` (JSON completo) ou `FIREBASE_SERVICE_ACCOUNT_PATH` (caminho para arquivo JSON).

3. **Obter Service Account Key**:
   - Vá para [Firebase Console](https://console.firebase.google.com)
   - Seu Projeto → Configurações → Contas de Serviço
   - Clique em "Gerar nova chave privada"
   - Baixe o JSON e cole no `.env` como `FIREBASE_SERVICE_ACCOUNT` ou salve como arquivo

4. **Executar**:
   ```bash
   npm start
   ```

## 🌐 Deploy Gratuito

### Railway (Recomendado)

1. Crie uma conta em [Railway](https://railway.app)
2. Crie um novo projeto
3. Adicione um serviço "GitHub Repo" ou "Empty Project"
4. Configure as variáveis de ambiente:
   - `FIREBASE_SERVICE_ACCOUNT`: Cole o JSON completo do service account
   - `BASE_ID`: (Opcional) ID da base específica para monitorar
5. Railway detectará automaticamente o `package.json` e fará o deploy

### Render

1. Crie uma conta em [Render](https://render.com)
2. Crie um novo "Web Service"
3. Conecte seu repositório GitHub
4. Configure:
   - **Build Command**: `cd server && npm install`
   - **Start Command**: `cd server && npm start`
   - **Environment Variables**: Adicione `FIREBASE_SERVICE_ACCOUNT` e `BASE_ID` (opcional)

### Heroku

1. Crie uma conta no [Heroku](https://heroku.com)
2. Instale o Heroku CLI
3. Execute:
   ```bash
   cd server
   heroku create seu-app-nome
   heroku config:set FIREBASE_SERVICE_ACCOUNT='{"type":"service_account",...}'
   git push heroku main
   ```

## 📝 Variáveis de Ambiente

- `FIREBASE_SERVICE_ACCOUNT`: JSON completo do service account (recomendado para produção)
- `FIREBASE_SERVICE_ACCOUNT_PATH`: Caminho para arquivo JSON (apenas desenvolvimento local)
- `BASE_ID`: (Opcional) ID da base específica. Se não definido, monitora todas as bases.

## 🔍 Logs

O servidor exibe logs detalhados:
- `✅` = Sucesso
- `⚠️` = Aviso (ex: motorista sem FCM token)
- `❌` = Erro
- `🔄` = Mudança de status detectada
- `📡` = Notificação enviada

## 🛠️ Desenvolvimento

Para desenvolvimento com auto-reload:
```bash
npm run dev
```

## 📊 Monitoramento

O servidor monitora automaticamente:
- **IR_ESTACIONAMENTO**: Notifica quando motorista é chamado para estacionamento
- **CARREGANDO**: Notifica quando motorista é chamado para vaga
- **CONCLUIDO**: Notifica quando carregamento é concluído

Outros status não geram notificações automáticas.

## 🔒 Segurança

- **Nunca** commite o arquivo `.env` ou `service-account-key.json`
- Use variáveis de ambiente no deploy
- O service account deve ter permissões apenas para Firestore e FCM

## 🐛 Troubleshooting

**Erro: "Firebase Admin não inicializado"**
- Verifique se `FIREBASE_SERVICE_ACCOUNT` está configurado corretamente
- O JSON deve estar completo e válido

**Notificações não chegam**
- Verifique se o motorista tem `fcmToken` salvo no Firestore
- Verifique os logs do servidor para erros
- Teste enviando uma notificação manualmente via Firebase Console

**Servidor para de funcionar**
- Verifique se o processo está rodando (Railway/Render mostram status)
- Verifique os logs para erros
- Reinicie o serviço se necessário

