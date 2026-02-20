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
    """Endpoint de health check"""
    return jsonify({"status": "ok", "message": "API FCM está funcionando"})


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
        if not papel or papel not in ('admin', 'superadmin', 'auxiliar', 'ajudante'):
            return jsonify({"error": "Apenas admin/auxiliar podem solicitar localização"}), 403
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
        if uid != motorista_id:
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


def _assistente_via_huggingface(text: str, image_b64: Optional[str]) -> Optional[str]:
    """Usa Hugging Face Router API (OpenAI-compatible). Visão + texto ou só texto. Retorna None se falhar."""
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
        if image_b64:
            content = [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}},
            ]
        else:
            content = prompt
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": content}],
            "max_tokens": 1024 if image_b64 else 512,
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

        if not base_id:
            return jsonify({"error": "baseId é obrigatório"}), 400
        if not text and not image_b64:
            return jsonify({"error": "text ou imageBase64 é obrigatório"}), 400

        result_text = _assistente_via_huggingface(text, image_b64)

        if result_text is None or result_text == "":
            return jsonify({
                "error": "Assistente indisponível. Verifique HUGGINGFACE_TOKEN no servidor."
            }), 500

        return jsonify({"text": result_text, "ok": True}), 200

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
