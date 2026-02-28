# Limite de Perguntas do Assistente - Implementação no Backend

O app Android já envia `userId` e `userRole` nas requisições para `/assistente/chat`. O backend Python precisa implementar o limite de perguntas por base.

## Regra

- **Super Admin** (`userRole == "superadmin"` ou `userId` em `SUPERADMIN_UIDS`): **sem limite**
- **Demais usuários** (admin, etc.): **15 perguntas por base por dia**

## Dados recebidos do app

O body JSON da requisição inclui:

- `baseId` (string)
- `text` (string)
- `userName` (string, opcional)
- `userRole` (string, opcional): `"superadmin"`, `"admin"`, etc.
- `userId` (string, opcional): ID do documento do motorista/admin no Firestore

## Implementação sugerida (Python/Flask)

### 1. Verificar se é super admin

```python
def is_super_admin(user_role: str, user_id: str) -> bool:
    if user_role == "superadmin":
        return True
    superadmin_uids = os.environ.get("SUPERADMIN_UIDS", "").split(",")
    superadmin_uids = [u.strip() for u in superadmin_uids if u.strip()]
    return user_id in superadmin_uids
```

### 2. Contador no Firestore

Criar documento: `bases/{baseId}/assistente_uso/{data}`

Campos:
- `data` (string): "YYYY-MM-DD"
- `contagem` (number): quantidade de perguntas no dia
- `ultimaAtualizacao` (timestamp)

### 3. Lógica antes de processar o chat

```python
LIMITE_PERGUNTAS_POR_BASE = 15

@app.route("/assistente/chat", methods=["POST"])
def assistente_chat():
    data = request.get_json()
    base_id = data.get("baseId")
    user_role = data.get("userRole")
    user_id = data.get("userId")
    
    # Super admin: sem limite
    if is_super_admin(user_role, user_id or ""):
        return processar_chat(data)  # processar normalmente
    
    # Verificar limite
    hoje = datetime.utcnow().strftime("%Y-%m-%d")
    doc_ref = db.collection("bases").document(base_id).collection("assistente_uso").document(hoje)
    doc = doc_ref.get()
    
    contagem = 0
    if doc.exists:
        contagem = doc.to_dict().get("contagem", 0)
    
    if contagem >= LIMITE_PERGUNTAS_POR_BASE:
        return jsonify({
            "success": False,
            "error": "Limite diário de 15 perguntas atingido para esta base. Tente novamente amanhã."
        }), 429
    
    # Processar chat
    resultado = processar_chat(data)
    
    # Incrementar contador (após sucesso)
    doc_ref.set({
        "data": hoje,
        "contagem": contagem + 1,
        "ultimaAtualizacao": firestore.SERVER_TIMESTAMP
    }, merge=True)
    
    return resultado
```

### 4. Resposta de erro

Quando o limite for atingido, retornar status **429** (Too Many Requests) e o app exibirá a mensagem de erro no chat.

## Variável de ambiente

No Render (ou seu provedor), configure:

- `SUPERADMIN_UIDS`: IDs separados por vírgula dos super admins (ex.: `"abc123,def456"`)

O super admin pode obter seu ID na tela **Configurações do Sistema** do app.
