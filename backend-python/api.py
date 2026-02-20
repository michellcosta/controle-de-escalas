"""
api.py

API Flask simples para receber requisições do app Android
e enviar notificações push via FCM usando o backend Python.

Uso:
    python api.py

Ou com gunicorn (produção):
    gunicorn -w 1 --timeout 120 -b 0.0.0.0:5000 api:app
"""

import os
import json
import requests as http_requests
from flask import Flask, request, jsonify
from flask_cors import CORS
from firebase_admin import auth, firestore
from firestore_reader import FirestoreReader
from fcm_sender import FCMSender
from typing import Optional, Tuple

app = Flask(__name__)
CORS(app)  # Permitir requisições do app Android

# Inicializar serviços (serão inicializados na primeira requisição)
reader: Optional[FirestoreReader] = None
sender: Optional[FCMSender] = None


def get_service_account_path() -> Optional[str]:
    """Obtém o caminho do Service Account"""
    # Tentar variável de ambiente primeiro
    if os.getenv('FIREBASE_SERVICE_ACCOUNT_JSON'):
        return None  # Será usado via variável de ambiente
    
    # Tentar arquivo padrão
    default_paths = [
        'service-account-key.json',
        'service-account.json',
        os.path.join(os.path.dirname(__file__), 'service-account-key.json')
    ]
    
    for path in default_paths:
        if os.path.exists(path):
            return path
    
    return None


def initialize_services():
    """Inicializa os serviços (lazy loading)"""
    global reader, sender
    
    if reader is None or sender is None:
        service_account_path = get_service_account_path()
        
        if service_account_path is None and not os.getenv('FIREBASE_SERVICE_ACCOUNT_JSON'):
            raise ValueError(
                "Service Account não configurado. "
                "Defina FIREBASE_SERVICE_ACCOUNT_JSON ou coloque service-account-key.json na pasta."
            )
        
        reader = FirestoreReader(service_account_path)
        sender = FCMSender(service_account_path)
        
        print("✅ Serviços inicializados")


@app.route('/health', methods=['GET'])
def health():
    """Endpoint de health check (não inicializa FCM; servidor pode estar acordando)."""
    return jsonify({"status": "ok", "message": "API FCM está funcionando"})


@app.route('/health/ready', methods=['GET'])
def health_ready():
    """Verifica se o backend está pronto para enviar notificações (inicializa FCM). Útil para diagnóstico."""
    try:
        initialize_services()
        return jsonify({"status": "ok", "ready": True, "message": "FCM inicializado"}), 200
    except Exception as e:
        return jsonify({"status": "error", "ready": False, "error": str(e)}), 500


@app.route('/notify/motorista', methods=['POST'])
def notify_motorista():
    """
    Endpoint para enviar notificação push para um motorista específico
    
    Body JSON esperado:
    {
        "baseId": "xvtFbdOurhdNKVY08rDw",
        "motoristaId": "abc123",
        "title": "🚚 Chamada para Carregamento",
        "body": "Subir agora para a vaga 01 com rota S-7",
        "data": {
            "tipo": "chamada",
            "vaga": "01",
            "rota": "S-7"
        }
    }
    """
    try:
        initialize_services()
        
        # Validar dados da requisição
        data = request.get_json()
        if not data:
            return jsonify({"error": "Body JSON é obrigatório"}), 400
        
        base_id = data.get('baseId')
        motorista_id = data.get('motoristaId')
        title = data.get('title')
        body = data.get('body')
        data_dict = data.get('data')
        
        if not all([base_id, motorista_id, title, body]):
            return jsonify({
                "error": "Campos obrigatórios: baseId, motoristaId, title, body"
            }), 400
        
        # Buscar token do motorista
        print(f"📖 Buscando token para motorista {motorista_id} na base {base_id}...")
        token_info = reader.get_motorista_token(base_id, motorista_id)
        
        if not token_info:
            print(f"❌ Motorista {motorista_id} não encontrado ou sem FCM token no Firestore")
            return jsonify({
                "error": f"Motorista {motorista_id} não encontrado ou sem FCM token. O motorista precisa fazer login no app para receber notificações."
            }), 404
        
        # Enviar notificação
        success, error = sender.send_to_token(
            token=token_info['fcmToken'],
            title=title,
            body=body,
            data=data_dict
        )
        
        if success:
            print(f"✅ Notificação enviada via FCM para {token_info.get('nome', motorista_id)}")
            return jsonify({
                "success": True,
                "message": f"Notificação enviada para {token_info.get('nome', motorista_id)}",
                "motorista": token_info.get('nome', 'N/A')
            }), 200
        else:
            return jsonify({
                "success": False,
                "error": error or "Erro desconhecido ao enviar notificação"
            }), 500
    
    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        print(f"❌ Erro inesperado: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": f"Erro interno: {str(e)}"}), 500


@app.route('/notify/base', methods=['POST'])
def notify_base():
    """
    Endpoint para enviar notificação push para todos os motoristas de uma base
    
    Body JSON esperado:
    {
        "baseId": "xvtFbdOurhdNKVY08rDw",
        "title": "🚛 Você foi escalado!",
        "body": "Você está escalado! Siga para o galpão e aguarde instruções.",
        "data": {
            "tipo": "escalacao"
        }
    }
    """
    try:
        initialize_services()
        
        # Validar dados da requisição
        data = request.get_json()
        if not data:
            return jsonify({"error": "Body JSON é obrigatório"}), 400
        
        base_id = data.get('baseId')
        title = data.get('title')
        body = data.get('body')
        data_dict = data.get('data')
        
        if not all([base_id, title, body]):
            return jsonify({
                "error": "Campos obrigatórios: baseId, title, body"
            }), 400
        
        # Buscar todos os tokens da base
        tokens = reader.get_motoristas_tokens(base_id)
        
        if not tokens:
            return jsonify({
                "error": f"Nenhum token FCM encontrado para a base {base_id}"
            }), 404
        
        # Enviar notificações
        resultado = sender.send_to_multiple_tokens(
            tokens=tokens,
            title=title,
            body=body,
            data=data_dict
        )
        
        return jsonify({
            "success": True,
            "message": f"Notificações enviadas para {resultado['sucessos']} motoristas",
            "resultado": resultado
        }), 200
    
    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        print(f"❌ Erro inesperado: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": f"Erro interno: {str(e)}"}), 500


@app.route('/motorista/token', methods=['GET'])
def get_motorista_token():
    """
    Endpoint para verificar se um motorista tem token FCM
    
    Query params:
        baseId: ID da base
        motoristaId: ID do motorista
    """
    try:
        initialize_services()
        
        base_id = request.args.get('baseId')
        motorista_id = request.args.get('motoristaId')
        
        if not base_id or not motorista_id:
            return jsonify({
                "error": "Query params obrigatórios: baseId, motoristaId"
            }), 400
        
        token_info = reader.get_motorista_token(base_id, motorista_id)
        
        if token_info:
            return jsonify({
                "exists": True,
                "motorista": token_info
            }), 200
        else:
            return jsonify({
                "exists": False,
                "message": "Motorista não encontrado ou sem FCM token"
            }), 404
    
    except Exception as e:
        return jsonify({"error": f"Erro interno: {str(e)}"}), 500


def _uid_is_superadmin(uid: str) -> bool:
    """True se uid estiver em SUPERADMIN_UIDS (variável de ambiente, UIDs separados por vírgula)."""
    raw = (os.getenv('SUPERADMIN_UIDS') or '').strip()
    if not raw:
        return False
    # Suporta valores com ou sem aspas (ex.: "abc123" ou abc123 no Render)
    allowed = [u.strip().strip('"\'') for u in raw.split(',') if u.strip()]
    return uid in allowed


def _verify_firebase_token():
    """Verifica Authorization: Bearer <idToken>. Retorna (uid, None) ou (None, error_tuple)."""
    auth_header = request.headers.get('Authorization')
    if not auth_header or not auth_header.startswith('Bearer '):
        return None, ({"error": "Token de autenticação obrigatório"}, 401)
    token = auth_header[7:].strip()
    if not token:
        return None, ({"error": "Token inválido"}, 401)
    try:
        decoded = auth.verify_id_token(token)
        return decoded.get('uid'), None
    except Exception as e:
        print(f"Token verification failed: {e}")
        return None, ({"error": "Token inválido ou expirado"}, 401)


@app.route('/location/request', methods=['POST'])
def location_request():
    """
    Admin/Assistente solicita localização e ETA de um motorista.
    Envia push silenciosa para o app do motorista.
    """
    try:
        initialize_services()
        uid, err = _verify_firebase_token()
        if err:
            return jsonify(err[0]), err[1]
        data = request.get_json()
        if not data:
            return jsonify({"error": "Body JSON é obrigatório"}), 400
        base_id = data.get('baseId')
        motorista_id = data.get('motoristaId')
        if not base_id or not motorista_id:
            return jsonify({"error": "baseId e motoristaId são obrigatórios"}), 400
        papel = reader.get_usuario_papel(base_id, uid)
        if not papel:
            papel = reader.get_usuario_papel_in_any_base(uid)
        if not papel and _uid_is_superadmin(uid):
            papel = 'superadmin'
        if not papel and uid in (reader.get_superadmin_uids_from_config() or []):
            papel = 'superadmin'
        if not papel or papel not in ('admin', 'superadmin', 'auxiliar', 'ajudante'):
            uids_env = (os.getenv('SUPERADMIN_UIDS') or '').strip()
            uid_suffix = uid[-6:] if len(uid) >= 6 else uid
            print(f"location/request 403: uid_fim={uid_suffix} papel={papel} SUPERADMIN_UIDS_definido={bool(uids_env)}")
            return jsonify({
                "error": "Apenas admin, superadmin ou auxiliar podem solicitar localização",
                "uid_suffix": uid_suffix,
                "hint": "Adicione seu UID completo em SUPERADMIN_UIDS no Render (variável de ambiente). O UID desta sessão termina em: " + uid_suffix,
            }), 403
        token_info = reader.get_motorista_token(base_id, motorista_id)
        if not token_info:
            return jsonify({"error": "Motorista não encontrado ou sem FCM token"}), 404
        motorista_nome = token_info.get('nome', 'Motorista')
        reader.write_location_response(base_id, motorista_id, {
            "status": "pending", "motoristaId": motorista_id, "motoristaNome": motorista_nome,
            "solicitadoEm": firestore.SERVER_TIMESTAMP,
        })
        success, error = sender.send_silent_data_only(
            token=token_info['fcmToken'],
            data={"type": "request_location", "baseId": base_id, "motoristaId": motorista_id}
        )
        if not success:
            return jsonify({"error": error or "Falha ao enviar push"}), 500
        print(f"✅ Pedido de localização enviado para {motorista_nome}")
        return jsonify({"ok": True, "status": "pending"}), 200
    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        print(f"❌ Erro location/request: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route('/location/receive', methods=['POST'])
def location_receive():
    """
    App do motorista envia coordenadas. Calcula rota via OpenRouteService.
    """
    try:
        initialize_services()
        uid, err = _verify_firebase_token()
        if err:
            return jsonify(err[0]), err[1]
        data = request.get_json()
        if not data:
            return jsonify({"error": "Body JSON é obrigatório"}), 400
        base_id = data.get('baseId')
        motorista_id = data.get('motoristaId')
        lat = data.get('lat')
        lng = data.get('lng')
        if not base_id or not motorista_id:
            return jsonify({"error": "baseId e motoristaId são obrigatórios"}), 400
        if lat is None or lng is None:
            return jsonify({"error": "lat e lng são obrigatórios"}), 400
        try:
            lat, lng = float(lat), float(lng)
        except (TypeError, ValueError):
            return jsonify({"error": "lat e lng devem ser números"}), 400
        # Motorista envia sua própria localização; admin/superadmin pode enviar em nome do motorista (mesmo aparelho/teste)
        if uid != motorista_id:
            papel = reader.get_usuario_papel(base_id, uid) or reader.get_usuario_papel_in_any_base(uid)
            if not papel and _uid_is_superadmin(uid):
                papel = 'superadmin'
            if not papel and uid in (reader.get_superadmin_uids_from_config() or []):
                papel = 'superadmin'
            if not papel or papel not in ('admin', 'superadmin', 'auxiliar', 'ajudante'):
                return jsonify({"error": "Apenas o motorista pode enviar sua localização"}), 403
        galpao = reader.get_galpao_coordenadas(base_id)
        if not galpao:
            reader.write_location_response(base_id, motorista_id, {
                "status": "error", "error": "Galpão não configurado",
                "atualizadoEm": firestore.SERVER_TIMESTAMP,
            })
            return jsonify({"ok": False, "error": "Galpão não configurado"}), 200
        ors_key = os.getenv('ORS_API_KEY') or os.getenv('OPENROUTESERVICE_API_KEY')
        if not ors_key:
            reader.write_location_response(base_id, motorista_id, {
                "status": "error", "error": "Serviço indisponível",
                "atualizadoEm": firestore.SERVER_TIMESTAMP,
            })
            return jsonify({"ok": False, "error": "Serviço indisponível"}), 500
        url = "https://api.openrouteservice.org/v2/directions/driving-car"
        payload = {"coordinates": [[lng, lat], [galpao["lng"], galpao["lat"]]]}
        resp = http_requests.post(url, json=payload, headers={"Authorization": ors_key, "Content-Type": "application/json"}, timeout=15)
        if resp.status_code != 200:
            reader.write_location_response(base_id, motorista_id, {
                "status": "error", "error": "Erro ao calcular rota",
                "atualizadoEm": firestore.SERVER_TIMESTAMP,
            })
            return jsonify({"ok": False, "error": "Erro ao calcular rota"}), 200
        ors_data = resp.json()
        route = (ors_data.get('routes') or [{}])[0]
        summary = route.get('summary') or {}
        distance_m = summary.get('distance', 0)
        duration_s = summary.get('duration', 0)
        eta_min = round(duration_s / 60)
        distance_km = round((distance_m / 1000) * 10) / 10
        motorista_doc = reader.db.collection('bases').document(base_id).collection('motoristas').document(motorista_id).get()
        motorista_nome = (motorista_doc.to_dict() or {}).get('nome', 'Motorista')
        reader.write_location_response(base_id, motorista_id, {
            "status": "ready", "motoristaNome": motorista_nome,
            "distanceKm": distance_km, "etaMinutes": eta_min,
            "atualizadoEm": firestore.SERVER_TIMESTAMP,
        })
        print(f"✅ Localização: {motorista_nome} - {distance_km} km, ~{eta_min} min")
        return jsonify({"ok": True, "distanceKm": distance_km, "etaMinutes": eta_min}), 200
    except Exception as e:
        print(f"❌ Erro location/receive: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


def _assistente_via_huggingface(text: str, image_b64: Optional[str], context_base: Optional[str] = None, history: Optional[list] = None) -> Optional[str]:
    """Usa Hugging Face Router API (OpenAI-compatible). Visão + texto ou só texto. context_base = dados reais da base. history = lista de {role, content} para manter contexto."""
    hf_token = os.getenv('HUGGINGFACE_TOKEN') or os.getenv('HF_TOKEN')
    if not hf_token:
        return None
    url = "https://router.huggingface.co/v1/chat/completions"
    headers = {"Authorization": f"Bearer {hf_token}", "Content-Type": "application/json"}
    model_vision = os.getenv('HF_VISION_MODEL', 'zai-org/GLM-4.5V')
    model_text = os.getenv('HF_TEXT_MODEL', 'Qwen/Qwen2.5-7B-Instruct')
    model = model_vision if image_b64 else model_text
    prompt = text or "Descreva o que está nesta imagem. Se for uma escala (lista de nomes com vagas e rotas), extraia cada linha no formato: Nome / Vaga / Rota, agrupando por ondas (1a onda, 2a onda, etc.) se houver."
    try:
        system_instruction = (
            "IMPORTANTE: Nunca responda com JSON, códigos ou estruturas técnicas. O usuário deve ver APENAS texto em português. "
            "Quando for aplicar uma alteração (ex.: mudar rota/vaga), escreva primeiro uma frase curta e amigável (ex.: 'Pronto, alterei a rota do Michell para K7.' ou 'Alterado: vaga 10 e rota K7 para o Michell.'). "
            "Em seguida, em uma linha separada, coloque EXATAMENTE: ACTION_JSON:{\"type\":\"...\", ...} (essa linha será removida e não aparece para o usuário). Use o nome exato do campo ondaIndex (não ondalndex). "
            "Você é o assistente do app Controle de Escalas. Responda APENAS sobre: escalas de motoristas, "
            "vagas, rotas, ondas, horários, localização/ETA, disponibilidade, quinzena e devoluções. "
            "Nos DADOS DA BASE você recebe: por turno (AM/PM), cada onda com nome e hora da onda; por motorista escalado: vaga, rota, sacas, horário; "
            "tempo estimado ao galpão (ETA por motorista); disponibilidade (quem está disponível/indisponível/não respondeu por data); "
            "quinzena (dias trabalhados na 1ª e 2ª quinzena por motorista); devoluções por motorista com Total por dia e cada devolução com data, hora, N pacotes e IDs. "
            "Ao falar de DEVOLUÇÕES use SEMPRE este formato: (1) Nome do motorista. (2) 'Total por dia: [data1] X devolução(ões); [data2] Y devolução(ões); ...' (3) Para cada devolução uma linha: 'DD/MM/AAAA HH:MM — N pacote(s). IDs: id1, id2, id3, ...' Não misture 'ID da devolução' no texto; não use lista numerada 1. 2. 3.; use só Total por dia e depois linhas com data hora — pacotes e IDs. "
            "Use esses dados para responder com números e nomes reais. "
            "Mantenha o contexto da conversa: se o usuário confirmar algo (ex: 'confirmado', 'sim'), interprete com base nas mensagens anteriores. "
            "Se o usuário perguntar sobre outro assunto (hora, notícias, etc.), diga em uma frase que só pode ajudar com escalas, motoristas e localização neste app. "
            "REGRA ESCALA: (1) Quem já está escalado está na lista 'Detalhe da escala' / 'Motoristas já escalados'. NUNCA adicione o mesmo motorista de novo. "
            "(2) Se o usuário pedir para TROCAR/ALTERAR/MUDAR vaga, rota ou sacas de alguém que JÁ ESTÁ escalado: use ACTION_JSON com type \"update_in_scale\" e preencha só o que mudou: {\"type\":\"update_in_scale\",\"motoristaNome\":\"Nome\",\"ondaIndex\":0,\"vaga\":\"02\" (opcional),\"rota\":\"G9\" (opcional),\"sacas\":4 (opcional ou null)}. ondaIndex = onda em que o motorista está (0 = primeira). "
            "(3) Se o usuário pedir para ADICIONAR/COLOCAR um motorista que AINDA NÃO ESTÁ na escala: você PRECISA de rota (e opcionalmente sacas). Se o usuário só disser 'adicionar Brendon na vaga 1' sem rota, NÃO emita ACTION_JSON; pergunte: 'Qual a rota do Brendon? Tem sacas?' e espere a resposta. Quando tiver rota (e sacas se aplicável), aí sim emita: {\"type\":\"add_to_scale\",\"motoristaNome\":\"Nome\",\"ondaIndex\":0,\"vaga\":\"01\",\"rota\":\"G9\",\"sacas\":null ou número}. "
            "(4) Só use add_to_scale para motoristas que NÃO aparecem na escala. Para quem já está escalado, use sempre update_in_scale. vaga sempre 2 dígitos (01, 02). rota em maiúsculas."
        )
        if context_base and context_base.strip():
            system_instruction += "\n\nDADOS DA BASE (use para responder): " + context_base.strip()
        if image_b64:
            content = [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}},
            ]
        else:
            content = prompt
        messages = [{"role": "system", "content": system_instruction}]
        for h in (history or []):
            role = (h.get("role") or "user").strip().lower()
            if role not in ("user", "assistant"):
                continue
            msg_content = (h.get("content") or h.get("text") or "").strip()
            if msg_content:
                messages.append({"role": role, "content": msg_content})
        messages.append({"role": "user", "content": content})
        payload = {
            "model": model,
            "messages": messages,
            "max_tokens": 1024,
        }
        resp = http_requests.post(url, json=payload, headers=headers, timeout=90)
        if not resp.ok:
            err = resp.text
            try:
                j = resp.json()
                err = j.get("error", {}).get("message") or j.get("error") or err
            except Exception:
                pass
            print(f"HF API error {resp.status_code}: {err}")
            return None
        out = resp.json()
        choice = (out.get("choices") or [None])[0]
        if not choice:
            return None
        msg = choice.get("message") or {}
        return (msg.get("content") or "").strip()
    except Exception as e:
        print(f"HF request failed: {e}")
        return None


@app.route('/assistente/chat', methods=['POST'])
def assistente_chat():
    """
    Chat com Assistente IA. Aceita texto e/ou imagem.
    Usa apenas Hugging Face (HUGGINGFACE_TOKEN ou HF_TOKEN).
    Body: { "baseId": "...", "text": "...", "imageBase64": "..." (opcional) }
    Header: Authorization: Bearer <Firebase ID Token>
    """
    try:
        initialize_services()
        uid, err = _verify_firebase_token()
        if err:
            return jsonify(err[0]), err[1]

        data = request.get_json()
        if not data:
            return jsonify({"error": "Body JSON é obrigatório"}), 400

        base_id = data.get('baseId')
        text = (data.get('text') or "").strip()
        image_b64 = data.get('imageBase64')
        history = data.get('history')
        if history is not None and not isinstance(history, list):
            history = None
        if history and len(history) > 20:
            history = history[-20:]

        if not base_id:
            return jsonify({"error": "baseId é obrigatório"}), 400
        if not text and not image_b64:
            return jsonify({"error": "text ou imageBase64 é obrigatório"}), 400

        contexto_base = reader.get_contexto_base_para_assistente(base_id) if reader else ""
        result_text = _assistente_via_huggingface(text, image_b64, context_base=contexto_base, history=history)

        if result_text is None or result_text == "":
            return jsonify({
                "error": "Assistente indisponível. Verifique HUGGINGFACE_TOKEN no servidor."
            }), 500

        # Extrair ação estruturada se o modelo retornou ACTION_JSON (ex.: adicionar/atualizar na escala)
        action = None
        idx = result_text.find("ACTION_JSON:")
        if idx >= 0:
            start = result_text.find("{", idx)
            if start >= 0:
                depth = 0
                end = start
                for i, c in enumerate(result_text[start:], start):
                    if c == "{": depth += 1
                    elif c == "}": depth -= 1
                    if depth == 0: end = i; break
                if end >= start:
                    try:
                        action = json.loads(result_text[start:end + 1])
                        result_text = (result_text[:idx].rstrip() + "\n" + result_text[end + 1:].lstrip()).strip()
                    except Exception:
                        pass

        # Fallback: se a resposta for só um JSON (modelo esqueceu de usar ACTION_JSON:), extrair ação e não mostrar código
        if action is None:
            trimmed = result_text.strip()
            if trimmed.startswith("{") and "}" in trimmed:
                try:
                    end_brace = trimmed.rfind("}")
                    if end_brace > 0:
                        candidate = trimmed[: end_brace + 1]
                        parsed = json.loads(candidate)
                        if isinstance(parsed, dict) and parsed.get("type") in ("update_in_scale", "add_to_scale"):
                            action = parsed
                            result_text = "Alteração aplicada."
                except Exception:
                    pass

        # Normalizar ação: garantir "ondaIndex" (modelo às vezes envia "ondalndex")
        if action and isinstance(action, dict):
            for key in list(action.keys()):
                if key == "ondalndex" or key == "onda_index":
                    action["ondaIndex"] = action.pop(key)
                    break

        resp_data = {"text": result_text.strip() or "Feito.", "ok": True}
        if action:
            resp_data["action"] = action
        return jsonify(resp_data), 200

    except Exception as e:
        print(f"❌ Erro assistente/chat: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    print("=" * 60)
    print("🚀 API FCM - Backend Python")
    print("=" * 60)
    print("\n📡 Endpoints disponíveis:")
    print("   GET  /health                    - Health check")
    print("   POST /notify/motorista          - Notificar motorista específico")
    print("   POST /notify/base               - Notificar todos da base")
    print("   GET  /motorista/token           - Verificar token de motorista")
    print("   POST /location/request          - Pedir localização/ETA (admin)")
    print("   POST /location/receive          - Receber coordenadas (motorista)")
    print("   POST /assistente/chat           - Chat com IA (texto + imagem)")
    
    # Usar PORT da variável de ambiente (produção) ou 5000 (desenvolvimento)
    port = int(os.getenv('PORT', 5000))
    
    print(f"\n🌐 Iniciando servidor na porta {port}...")
    print(f"   Acesse: http://localhost:{port}/health")
    print("\n⚠️  Para produção, use gunicorn:")
    print("   gunicorn -w 1 --timeout 120 -b 0.0.0.0:$PORT api:app")
    print("=" * 60)
    
    app.run(host='0.0.0.0', port=port, debug=False)  # debug=False em produção
