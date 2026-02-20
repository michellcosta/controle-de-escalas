# 🔑 Configurar SHA-1 no Firebase (Corrigir crash DEVELOPER_ERROR)

O app está crashando com `DEVELOPER_ERROR` porque o SHA-1 do seu certificado não está cadastrado no Firebase.

## 📋 Seu SHA-1 (Debug)

```
B9:B0:DE:C2:7A:D2:87:87:B8:42:47:57:C0:07:9A:EC:38:09:FD:9C
```

## 🚀 Passo a Passo

### 1. Acessar Firebase Console

1. Abra: **https://console.firebase.google.com**
2. Selecione o projeto: **controle-de-escalas-739cf**

### 2. Configurações do Projeto

1. Clique no **ícone de engrenagem** (⚙️) ao lado de "Visão geral do projeto"
2. Selecione **"Configurações do projeto"**

### 3. Adicionar o SHA-1

1. Role até a seção **"Seus apps"**
2. Localize o app Android: **com.controleescalas.app**
3. Clique em **"Adicionar impressão digital"** (ou "Add fingerprint")
4. Cole o SHA-1:
   ```
   B9:B0:DE:C2:7A:D2:87:87:B8:42:47:57:C0:07:9A:EC:38:09:FD:9C
   ```
5. Clique em **"Salvar"**

### 4. Baixar novo google-services.json (Opcional)

O Firebase pode pedir para baixar um novo `google-services.json` após adicionar o SHA-1. Se pedir:
1. Clique em **"Fazer download do google-services.json"**
2. Substitua o arquivo em: `Raiz-prompt/app/google-services.json`

### 5. Aguardar Propagação

- A alteração pode levar **alguns minutos** para propagar
- Feche o app completamente e abra novamente
- Se estiver no emulador, considere testar em **dispositivo físico** também

## ✅ Verificação

Após configurar, o app não deve mais crashar com `DEVELOPER_ERROR` na inicialização.

## 📱 Para Play Store (Release)

Quando for publicar na Play Store, você precisará adicionar também o **SHA-1 de Release**:
- Obtenha no Google Play Console → Configuração do app → Integridade do app
- Ou configure uma keystore de release e rode: `./gradlew signingReport` com a configuração de release

## ⚠️ Importante

- **Debug:** Use o SHA-1 acima (desenvolvimento/testes)
- **Release:** Adicione o SHA-1 da keystore que assina o APK de produção
