# 🚀 Quick Start - Backend FCM

Guia rápido para começar a usar o backend de notificações push.

## ⚡ 3 Passos Rápidos

### 1. Instalar Dependências

```bash
cd backend-python
pip install -r requirements.txt
```

### 2. Obter Service Account JSON

1. Acesse [Firebase Console](https://console.firebase.google.com)
2. Seu Projeto → ⚙️ Configurações → Contas de Serviço
3. Clique em **"Gerar nova chave privada"**
4. Salve como `service-account-key.json` na pasta `backend-python`

### 3. Enviar Primeira Notificação

```bash
python main.py \
  --base-id SEU_BASE_ID_AQUI \
  --service-account service-account-key.json \
  --title "🚛 Você foi escalado!" \
  --body "Você está escalado! Siga para o galpão e aguarde instruções."
```

## 📝 Exemplo Completo

```bash
# 1. Navegar para a pasta
cd backend-python

# 2. Instalar dependências
pip install -r requirements.txt

# 3. Testar (dry-run - não envia, apenas lista tokens)
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "Teste" \
  --body "Teste" \
  --dry-run

# 4. Enviar notificação real
python main.py \
  --base-id xvtFbdOurhdNKVY08rDw \
  --service-account service-account-key.json \
  --title "🚛 Você foi escalado!" \
  --body "Você está escalado! Siga para o galpão e aguarde instruções."
```

## 🔍 Verificar se Funcionou

1. **No terminal**: Você verá estatísticas de sucesso/falha
2. **No dispositivo**: A notificação aparecerá mesmo com o app fechado
3. **No Firebase Console**: Verifique em Cloud Messaging → Estatísticas

## ❓ Problemas Comuns

### "Service Account JSON não encontrado"
- Verifique se o arquivo está na pasta `backend-python`
- Use caminho absoluto se necessário: `--service-account /caminho/completo/arquivo.json`

### "Nenhum token FCM encontrado"
- Verifique se o `base-id` está correto
- Verifique se os motoristas têm `fcmToken` no Firestore
- Use `--dry-run` para listar tokens sem enviar

### "Erro de autenticação"
- Verifique se o Service Account JSON está correto
- Verifique se o projeto Firebase está ativo
- Verifique se as permissões do Service Account estão corretas

## 📚 Próximos Passos

- Leia o [README.md](README.md) completo para mais detalhes
- Veja [example_automation.py](example_automation.py) para automação
- Configure deploy em produção (Railway, Render, etc.)

## ✅ Checklist

- [ ] Python 3.8+ instalado
- [ ] Dependências instaladas (`pip install -r requirements.txt`)
- [ ] Service Account JSON baixado
- [ ] Base ID conhecido
- [ ] Teste dry-run executado com sucesso
- [ ] Primeira notificação enviada
