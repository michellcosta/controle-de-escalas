
# FIREBASE_SETUP.md

Este arquivo descreve o que precisa ser configurado no Firebase para o MVP.

## 1. Projetos Firebase
- Criar um projeto Firebase.
- Ativar Firestore.
- Ativar Storage.
- (Futuro) Ativar Cloud Messaging para push de "Chamar / Carregando".

## 2. google-services.json
Baixar o `google-services.json` do Firebase Console (Android app) e colocar em:
`app/google-services.json`

No ZIP temos um arquivo placeholder só pra lembrar.

## 3. Auth / Login
Neste MVP estamos usando:
- telefone + PIN de 4 dígitos, onde o PIN é controlado dentro da nossa própria coleção (não precisa usar SMS OTP ainda).

Fluxo sugerido:
- Tela de Login pede telefone e PIN.
- Busca no Firestore `usuarios` daquela base.
- Se bater, loga localmente e mantém em memória quem é você:
  - motorista / ajudante / admin / superadmin
  - baseId
  - userId

Depois dá pra migrar isso pra Firebase Auth oficial com custom claims.

## 4. Firestore coleções sugeridas

### bases/{baseId}
```json
{
  "nomeTransportadora": "DELUNA",
  "nomeBase": "Guarulhos Manhã",
  "corTema": "#16A34A",
  "statusAprovacao": "pendente | ativa | rejeitada",
  "localizacao": {
    "galpao": { "lat": -23.4, "lng": -46.5, "raioM": 100 },
    "estacionamento": { "lat": -23.41, "lng": -46.51, "raioM": 50, "ativo": true }
  }
}
```

Quando o superadmin aprovar, `statusAprovacao` vira `"ativa"`.

### bases/{baseId}/usuarios/{usuarioId}
```json
{
  "nome": "Michel",
  "telefone": "11988880000",
  "pin4": "1234",              // depois virar hash
  "papel": "motorista | ajudante | admin",
  "ativo": true,
  "superadmin": false,
  "baseId": "<id da base>"
}
```

Motorista não edita isso.  
Admin e ajudante podem cadastar/editar.

### bases/{baseId}/escalaHoje/{turno}  // turno = "AM" ou "PM"
```json
{
  "ondas": [
    {
      "ordem": 1,                         // 1 = PRIMEIRA ONDA, 2 = SEGUNDA ONDA...
      "nomeOnda": "PRIMEIRA ONDA",
      "horarioPlanejado": "06:00",
      "itens": [
        {
          "motoristaId": "abc123",
          "nomeMotorista": "Michel",
          "vagaPlanejada": "02",
          "rotaCodigo": "M12",
          "pdfStoragePath": "bases/<baseId>/motoristas/<motoristaId>/2025-10-27.pdf",
          "dataDoDia": "2025-10-27"
        }
      ]
    }
  ],
  "dataDoDia": "2025-10-27"
}
```

OBS:
- O array `ondas` é uma lista ORDENADA.
- O primeiro item tem `ordem = 1`, o segundo `ordem = 2`, etc.
- O nomeOnda ("PRIMEIRA ONDA", "SEGUNDA ONDA") é gerado a partir desse `ordem`.

### bases/{baseId}/statusMotoristas/{motoristaId}
```json
{
  "estado": "A_CAMINHO | CHEGUEI | ESTACIONAMENTO | CARREGANDO | CONCLUIDO",
  "vagaAtualChamado": "02",
  "rotaAtualChamado": "M12",

  "marcadoParaConclusao": true,
  "passouEstacionamentoDepoisDeCarregar": false,

  "ultimaAtualizacao": 1234567890  // timestamp
}
```

- GPS atualiza: A_CAMINHO, CHEGUEI, ESTACIONAMENTO
- Admin chama: vira CARREGANDO e seta vagaAtualChamado
- Quando ele termina e sai do galpão:
  - automático ou pelo admin → CONCLUIDO

### Storage
PDFs:
- `bases/<baseId>/motoristas/<motoristaId>/<dataDoDia>.pdf`

IMPORTANTE:
- apagar PDFs antigos na virada do dia para não consumir espaço.

---

## 5. Regras de Segurança (Rascunho)

Ver `firestore.rules` no ZIP:
- Motorista só lê os dados dele e o status dele.
- Motorista NÃO lê todos os outros motoristas nem toda escala completa.
- Admin/Ajudante de uma base só vê/edita dados daquela base.
- Ninguém de uma base vê a outra base.
- Só superadmin pode aprovar base nova (mudar `statusAprovacao` de pendente -> ativa).

---

## 6. GPS

No app Android:
- Pedir permissão de localização (foreground).
- Quando tiver localização:
  - calcular distância até `localizacao.galpao` e `localizacao.estacionamento`.
- Atualizar `estado` em `statusMotoristas/{motoristaId}`:
  - CHEGUEI se <= raioM do galpao
  - ESTACIONAMENTO se <= raioM do estacionamento
- Dar prioridade: CARREGANDO > ESTACIONAMENTO > CHEGUEI > A_CAMINHO
  (ou seja, se já está CARREGANDO, não derrubar pra CHEGUEI automaticamente)

---

## 7. Notificação "Chamar"
Quando admin toca "Chamar / Carregando" em um motorista:
- Abre pop-up pra escolher/ajustar a vaga real AGORA.
- Salva estado = "CARREGANDO" + vagaAtualChamado + rotaAtualChamado.
- Envia notificação push para o motorista.
- Atualiza imediatamente a tela do motorista com "CARREGANDO — Subir agora para a vaga X".

Push pode ser FCM (Firebase Cloud Messaging) depois.
No MVP dá pra simular com um campo reativo no Firestore e a tela do motorista re-renderizando.

---
