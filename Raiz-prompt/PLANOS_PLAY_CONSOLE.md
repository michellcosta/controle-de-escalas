# Configurar produtos de assinatura no Google Play Console

Para o Google Play Billing funcionar, você precisa criar os produtos no Play Console.

## Passos

### 1. Abrir o app no Play Console
1. Acesse [Google Play Console](https://play.google.com/console)
2. Selecione o app **Controle de Escalas**
3. Vá em **Monetizar** → **Produtos** → **Assinaturas**

### 2. Criar assinaturas

Crie assinaturas **mensais** e **anuais** (15% desconto) para cada plano:

#### Planos mensais

| Plano     | ID do produto       | Preço   | Período |
|-----------|---------------------|---------|---------|
| PRO       | `plano_pro_mensal`   | R$ 199  | Mensal  |
| MULTI     | `plano_multi_mensal` | R$ 499  | Mensal  |
| MULTI PRO | `plano_multi_pro_mensal` | R$ 849 | Mensal  |

#### Planos anuais (15% desconto)

| Plano     | ID do produto       | Preço   | Período | Economia |
|-----------|---------------------|---------|---------|----------|
| PRO       | `plano_pro_anual`    | R$ 2.030 | Anual  | R$ 358   |
| MULTI     | `plano_multi_anual`  | R$ 5.090 | Anual  | R$ 898   |
| MULTI PRO | `plano_multi_pro_anual` | R$ 8.660 | Anual | R$ 1.528 |

**Importante:** Os IDs devem ser **exatamente** os acima (sem espaços, minúsculas).

### 3. Configurar cada assinatura

Para cada produto:
1. Clique em **Criar assinatura**
2. Preencha o **ID do produto**
3. Defina o nome (ex: "Plano PRO Mensal" / "Plano PRO Anual")
4. Defina o preço e período (mensal ou anual)
5. Salve e ative

### 4. Testar

1. Adicione testadores em **Configuração** → **Acesso à licença**
2. Use uma conta de teste para comprar no app
3. Compras de teste não são cobradas

### 5. Fluxo após compra

Quando o usuário assina via Play Billing:
- O `BillingManager` recebe `onPurchasesUpdated`
- **TODO:** Atualizar o campo `plano` da base no Firestore (ex: via Cloud Function ou chamada direta)
- O backend deve validar a compra com a API do Google (Server-side verification)

## Referências

- [Documentação Google Play Billing](https://developer.android.com/google/play/billing)
- [Configurar assinaturas](https://support.google.com/googleplay/android-developer/answer/1217309)
