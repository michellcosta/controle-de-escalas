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
from datetime import datetime, timezone
import requests as http_requests
import openai
from flask import Flask, request, jsonify
from flask_cors import CORS
from firebase_admin import auth, firestore
from google.cloud.firestore_v1.transforms import Increment
from firestore_reader import FirestoreReader
from fcm_sender import FCMSender
from typing import Optional, Tuple

app = Flask(__name__)
CORS(app)  # Permitir requisições do app Android


@app.errorhandler(500)
def handle_500(e):
    """Garante que erros 500 retornem JSON para o app não exibir HTML."""
    return jsonify({"error": "Erro interno no servidor. Tente novamente."}), 500


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
    modelo = os.getenv('OPENAI_MODEL', 'gpt-4o-mini')
    tem_chave = bool(os.getenv('OPENAI_API_KEY'))
    return jsonify({
        "status": "ok",
        "message": "API FCM está funcionando",
        "assistente_modelo": modelo,
        "openai_configurado": tem_chave,
    })


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


@app.route('/notify/status-change', methods=['POST'])
def notify_status_change():
    """
    Notifica motorista e admins quando o status do motorista muda.
    Substitui Cloud Function onMotoristaStatusChanged (sem plano Blaze).

    Body JSON esperado:
    {
        "baseId": "xvtFbdOurhdNKVY08rDw",
        "motoristaId": "abc123",
        "status": "CHEGUEI",
        "motoristaNome": "João Silva"  // opcional; se vazio, busca no Firestore
    }
    """
    try:
        initialize_services()

        data = request.get_json()
        if not data:
            return jsonify({"error": "Body JSON é obrigatório"}), 400

        base_id = data.get('baseId')
        motorista_id = data.get('motoristaId')
        status = (data.get('status') or '').strip()
        motorista_nome = (data.get('motoristaNome') or '').strip()

        if not all([base_id, motorista_id, status]):
            return jsonify({
                "error": "Campos obrigatórios: baseId, motoristaId, status"
            }), 400

        # Buscar nome do motorista se não fornecido
        if not motorista_nome:
            token_info = reader.get_motorista_token(base_id, motorista_id)
            motorista_nome = (token_info or {}).get('nome', 'Motorista')

        # Mensagens para o motorista (como na Cloud Function)
        status_messages = {
            "A_CAMINHO": "Você está a caminho do galpão",
            "CHEGUEI": "Você chegou ao galpão. O admin foi notificado. Aguarde instruções.",
            "PROXIMO": "Você está próximo",
            "IR_ESTACIONAMENTO": "Vá para o ESTACIONAMENTO e aguarde",
            "ESTACIONAMENTO": "Você está no estacionamento",
            "CARREGANDO": "Subir agora para carregar",
            "CONCLUIDO": "Carregamento concluído! Ótimo trabalho!",
        }
        mensagem_motorista = status_messages.get(status) or f"Status atualizado para {status}"

        titulos = {
            "IR_ESTACIONAMENTO": "🅿️ Chamada para Estacionamento",
            "CARREGANDO": "🚚 Chamada para Carregamento",
            "CONCLUIDO": "✅ Carregamento Concluído",
            "CHEGUEI": "📍 Chegou ao Galpão",
            "ESTACIONAMENTO": "🅿️ No Estacionamento",
        }
        titulo_motorista = titulos.get(status) or "📍 Status Atualizado"

        # 1. Notificar motorista
        token_info = reader.get_motorista_token(base_id, motorista_id)
        if token_info:
            success, error = sender.send_to_token(
                token=token_info['fcmToken'],
                title=titulo_motorista,
                body=mensagem_motorista,
                data={"type": "status_update", "status": status}
            )
            if success:
                print(f"✅ Notificação enviada para motorista {motorista_nome} ({status})")
            else:
                print(f"⚠️ Falha ao notificar motorista: {error}")
        else:
            print(f"⚠️ Motorista {motorista_id} sem FCM token, pulando notificação")

        # 2. Notificar admins em CHEGUEI e CONCLUIDO
        status_para_admin = ["CHEGUEI", "CONCLUIDO"]
        if status in status_para_admin:
            admin_msg = (
                f"{motorista_nome} chegou ao galpão" if status == "CHEGUEI"
                else f"{motorista_nome} concluiu o carregamento"
            )
            admin_tokens = reader.get_admins_tokens(base_id)
            if admin_tokens:
                resultado = sender.send_to_multiple_tokens(
                    tokens=admin_tokens,
                    title="📢 Atualização de Motorista",
                    body=admin_msg,
                    data={"type": "motorista_update", "motoristaId": motorista_id, "status": status}
                )
                print(f"✅ Notificação para {resultado['sucessos']} admin(s): {admin_msg}")
            else:
                print(f"⚠️ Nenhum admin com FCM token na base {base_id}")

        return jsonify({
            "success": True,
            "message": f"Notificações de status {status} processadas",
            "motoristaNome": motorista_nome
        }), 200

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        print(f"❌ Erro notify/status-change: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


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


# Prompt de sistema compartilhado pelo assistente (mesmo para texto e visão)
_SYSTEM_PROMPT = (
    "IMPORTANTE: Nunca responda com JSON, códigos ou estruturas técnicas. O usuário deve ver APENAS texto em português. "
    "Quando for aplicar uma alteração (ex.: mudar rota/vaga), escreva primeiro uma frase curta e amigável (ex.: 'Pronto, alterei a rota do Michell para K7.' ou 'Alterado: vaga 10 e rota K7 para o Michell.'). "
    "Em seguida, em uma linha separada, coloque EXATAMENTE: ACTION_JSON:{\"type\":\"...\", ...} (essa linha será removida e não aparece para o usuário). Use o nome exato do campo ondaIndex (não ondalndex). "
    "Você é o assistente do app Controle de Escalas. Responda sobre: escalas de motoristas, "
    "vagas, rotas, ondas, horários, localização/ETA, disponibilidade, quinzena e devoluções. "
    "Você também deve responder sobre a quantidade de motoristas em cada categoria (FROTA, PASSEIO, etc.) e resumos de disponibilidade. "
    "Se o usuário perguntar 'quem sou eu' ou similar, use os dados de IDENTIDADE DO USUÁRIO para responder nome e papel. "
    "Sempre cumprimente o usuário pelo nome se disponível. "
    "\n\nGUIA DE USO DO APP (FAQ): "
    "- Para escanear QR Code: Na tela do motorista, clique no ícone de câmera no menu inferior. "
    "- Para ver devoluções: No painel admin, clique em 'Devoluções'. "
    "- Para gerenciar motoristas: No painel admin, clique em 'Usuários'. "
    "- Para configurar localização: No painel admin, clique em 'Configuração de Localização'. "
    "- Para ver dias trabalhados: Clique em 'Quinzena' no menu admin ou no card da tela do motorista. "
    "\n\nNos DADOS DA BASE você recebe: contagem por modalidade; por turno (AM/PM), cada onda com nome e hora da onda; por motorista escalado: vaga, rota, sacas, horário; "
    "tempo estimado ao galpão (ETA por motorista); disponibilidade detalhada e resumida (quem está disponível/indisponível/não respondeu por data); "
    "quinzena (dias trabalhados); devoluções; histórico de avisos enviados; plano e trial da base; e regras de localização (galpão e raio). "
    "MÁXIMA ATENÇÃO: Se a imagem contiver 6 motoristas, você deve gerar 6 linhas de ACTION_JSON. NUNCA resuma ou ignore motoristas. "
    "Ao falar de DEVOLUÇÕES use SEMPRE este formato: (1) Nome do motorista. (2) 'Total por dia: [data1] X devolução(ões); [data2] Y devolução(ões); ...' (3) Para cada devolução uma linha: 'DD/MM/AAAA HH:MM — N pacote(s). IDs: id1, id2, id3, ...' Não misture 'ID da devolução' no texto; não use lista numerada 1. 2. 3.; use só Total por dia e depois linhas com data hora — pacotes e IDs. "
    "Ao dar INSIGHTS sobre devoluções, você pode dizer: 'O motorista [Nome] teve X devoluções esta semana, um aumento/queda de Y%'. "
    "Use esses dados para responder com números e nomes reais. "
    "Mantenha o contexto da conversa: se o usuário confirmar algo (ex: 'confirmado', 'sim'), interprete com base nas mensagens anteriores. "
    "Se o usuário perguntar sobre outro assunto (hora, notícias, etc.), diga em uma frase que só pode ajudar com escalas, motoristas e localização neste app. "
    "REGRA ESCALA: (1) Quem já está escalado está na lista 'Detalhe da escala' / 'Motoristas já escalados'. NUNCA adicione o mesmo motorista de novo. "
    "(2) Se o usuário pedir para TROCAR/ALTERAR/MUDAR vaga, rota ou sacas de alguém que JÁ ESTÁ escalado: use ACTION_JSON com type \"update_in_scale\" e preencha só o que mudou: {\"type\":\"update_in_scale\",\"motoristaNome\":\"Nome\",\"ondaIndex\":0,\"vaga\":\"02\" (opcional),\"rota\":\"G9\" (opcional),\"sacas\":4 (opcional ou null)}. ondaIndex = onda em que o motorista está (0 = primeira). "
    "(3) Se o usuário pedir para ADICIONAR/COLOCAR um motorista que AINDA NÃO ESTÁ na escala: você PRECISA de rota (e opcionalmente sacas). Se o usuário só disser 'adicionar Brendon na vaga 1' sem rota, NÃO emita ACTION_JSON; pergunte: 'Qual a rota do Brendon? Tem sacas?' e espere a resposta. Quando tiver rota (e sacas se aplicável), aí sim emita: {\"type\":\"add_to_scale\",\"motoristaNome\":\"Nome\",\"ondaIndex\":0,\"vaga\":\"01\",\"rota\":\"G9\",\"sacas\":null ou número}. "
    "(4) Só use add_to_scale para motoristas que NÃO aparecem na escala. Para quem já está escalado, use sempre update_in_scale. vaga sempre 2 dígitos (01, 02). rota em maiúsculas."
    "(5) CRIAÇÃO DO ZERO: Se não existirem ondas criadas (DADOS DA BASE vazios), você ainda deve emitir ACTION_JSON para adicionar os motoristas. O app criará as ondas automaticamente começando da '1ª ONDA' (ondaIndex: 0)."
    "\n\nTURNO (AM/PM) - REGRAS OBRIGATÓRIAS: "
    "(1) Se o usuário pedir 'montar a escala no AM', 'montar escala AM', 'escala do turno AM' ou similar → use SEMPRE o turno AM. "
    "(2) Se o usuário pedir 'montar a escala no PM', 'montar escala PM', 'escala do turno PM' ou similar → use SEMPRE o turno PM. "
    "(3) Se o usuário pedir APENAS 'montar a escala' ou 'montar escala' SEM especificar AM ou PM → NUNCA assuma um turno. PERGUNTE: 'Para qual turno deseja montar a escala? AM ou PM?' e espere a resposta antes de emitir ACTION_JSON. "
    "(4) Em DADOS DA BASE você vê os dados de ambos os turnos. O campo TURNO ATUAL indica qual aba o usuário está no app (contexto). "
    "(5) Se o usuário enviar uma foto sem especificar turno e os dados estiverem ambíguos, PERGUNTE: 'Esta escala é para o turno AM ou PM?' antes de prosseguir."
    "\n\nSINÔNIMOS E VARIAÇÕES DE LINGUAGEM (interprete todas como equivalentes): "
    "• MONTAR ESCALA: 'montar a escala', 'fazer a escala', 'organizar a escala', 'montar escala', 'fazer escala', 'arrumar a escala', 'preparar a escala'. "
    "• STATUS/LOCALIZAÇÃO: 'quem está carregando', 'quem está na vaga', 'quem subiu', 'quem já carregou', 'status do [nome]', 'onde está o [nome]', 'como está o [nome]', 'situação do [nome]'. "
    "• CHAMAR/AVISAR: 'chamar o [nome]', 'avisar o [nome]', 'mandar o [nome] subir', 'notificar o [nome]', 'avisa a [onda]'. "
    "• ADICIONAR NA ESCALA: 'colocar', 'adicionar', 'incluir', 'escalar', 'botar na escala', 'entrar na onda'. "
    "• ALTERAR: 'trocar', 'mudar', 'alterar', 'atualizar' (vaga, rota ou sacas). "
    "• DEVOLUÇÕES: 'devoluções', 'devolução', 'quem devolveu', 'pacotes devolvidos'. "
    "• DISPONIBILIDADE: 'quem está disponível', 'quem pode trabalhar', 'disponibilidade de amanhã', 'quem respondeu'. "
    "• QUINZENA: 'dias trabalhados', 'quinzena', 'quantos dias o [nome] trabalhou'. "
    "• ONDAS/TURNOS: 'primeira onda' = '1ª onda' = 'onda 1'; 'manhã' = 'AM'; 'tarde' = 'PM'. "
    "Quando o usuário usar qualquer uma dessas variações, interprete e responda adequadamente."
    "\n\nQUANDO RECEBER UMA FOTO: Você tem visão total para fotos. "
    "Se a imagem contiver uma escala, identifique não só os motoristas, mas também as ONDAS (ex: 1ª Onda, 2ª Onda, 3ª Onda). "
    "Use o campo ondaIndex (0 para a 1ª onda, 1 para a 2ª, etc.) de acordo com a ordem das ondas identificadas na foto. "
    "Se o usuário pedir para 'organizar como na foto' ou 'separar por ondas', você DEVE re-analisar a imagem do contexto para emitir ACTION_JSON com os ondaIndex corretos. "
    "Extraia TODOS os motoristas (sem limite de quantidade) e emita um ACTION_JSON para cada um, um por linha. NÃO esqueça de nenhum motorista presente na imagem. Confirme o que foi feito em texto amigável."
    "\n\nENVIO DE AVISOS/MENSAGENS: Você pode enviar avisos para motoristas. "
    "(1) Se o usuário pedir para avisar um motorista (ex: 'Avise o David para esperar'): emita ACTION_JSON:{\"type\":\"send_notification\",\"motoristaNome\":\"Nome\",\"body\":\"Mensagem\"}. "
    "(2) Se o usuário pedir para avisar uma ONDA (ex: 'Avisa a 2ª onda que o pátio liberou'): emita ACTION_JSON:{\"type\":\"send_notification\",\"ondaIndex\":1,\"body\":\"Mensagem\"}. "
    "Confirme sempre o envio com uma frase positiva."
)


def _assistente_via_openai(text: str, image_b64: Optional[str], context_base: Optional[str] = None, history: Optional[list] = None, user_name: str = "Usuário", user_role: str = "Membro", turno: Optional[str] = None) -> Optional[str]:
    """Usa OpenAI GPT-4o-mini. Suporta visão (imagem base64) + texto e histórico de conversa."""
    api_key = os.getenv('OPENAI_API_KEY')
    if not api_key:
        print("OPENAI_API_KEY não configurada.")
        return None

    client = openai.OpenAI(api_key=api_key)
    model = os.getenv('OPENAI_MODEL', 'gpt-4o-mini')
    prompt = text or "Descreva o que está nesta imagem. Se for uma escala (lista de nomes com vagas e rotas), extraia cada motorista com vaga e rota, agrupando por ondas se houver."

    system_instruction = _SYSTEM_PROMPT
    if context_base and context_base.strip():
        system_instruction += "\n\nDADOS DA BASE (use para responder): " + context_base.strip()

    # Adiciona contexto de identidade ao prompt do sistema
    identity_context = f"\n\nIDENTIDADE DO USUÁRIO:\nNome: {user_name}\nPapel: {user_role}\n"
    system_instruction += identity_context
    if turno and str(turno).strip().upper() in ("AM", "PM"):
        system_instruction += f"\nTURNO ATUAL (aba selecionada no app): {turno.strip().upper()}\n"

    messages = [{"role": "system", "content": system_instruction}]

    # Adicionar histórico de conversa
    for h in (history or []):
        role = (h.get("role") or "user").strip().lower()
        if role not in ("user", "assistant"):
            continue
        msg_content = (h.get("content") or h.get("text") or "").strip()
        if msg_content:
            messages.append({"role": role, "content": msg_content})

    # Última mensagem do usuário (texto + imagem opcional)
    if image_b64:
        user_content = [
            {"type": "text", "text": prompt},
            {"type": "image_url", "image_url": {
                "url": f"data:image/jpeg;base64,{image_b64}",
                "detail": "high"
            }},
        ]
    else:
        user_content = prompt

    messages.append({"role": "user", "content": user_content})

    try:
        response = client.chat.completions.create(
            model=model,
            messages=messages,
            max_tokens=1500,
            temperature=0.2,  # Mais determinístico para ações estruturadas
        )
        result = (response.choices[0].message.content or "").strip()
        print(f"✅ OpenAI {model} respondeu ({len(result)} chars)")
        return result
    except openai.AuthenticationError:
        print("❌ OpenAI: OPENAI_API_KEY inválida.")
        return None
    except openai.RateLimitError:
        print("❌ OpenAI: Rate limit atingido.")
        return None
    except Exception as e:
        print(f"❌ OpenAI request failed: {e}")
        return None


def _is_super_admin(user_role: str, user_id: str) -> bool:
    """Super admin não tem limite de perguntas."""
    if user_role and str(user_role).strip().lower() == "superadmin":
        return True
    superadmin_uids = (os.getenv("SUPERADMIN_UIDS") or "").strip()
    if not superadmin_uids:
        return False
    uids = [u.strip() for u in superadmin_uids.split(",") if u.strip()]
    return user_id and str(user_id).strip() in uids


LIMITE_PERGUNTAS_POR_BASE = 15


@app.route('/assistente/chat', methods=['POST'])
def assistente_chat():
    """
    Chat com Assistente IA. Aceita texto e/ou imagem.
    Usa apenas Hugging Face (HUGGINGFACE_TOKEN ou HF_TOKEN).
    Body: { "baseId": "...", "text": "...", "imageBase64": "..." (opcional) }
    Header: Authorization: Bearer <Firebase ID Token>
    Limite: 15 perguntas por base por dia (super admin sem limite).
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
        user_name = data.get('userName', 'Usuário')
        user_role = data.get('userRole', 'Membro')
        user_id = data.get('userId') or ""
        turno = (data.get('turno') or "").strip().upper()
        if turno not in ("AM", "PM"):
            turno = None
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
        # Imagem muito grande pode causar timeout ou erro no modelo de visão
        if image_b64 and len(image_b64) > 6_700_000:
            return jsonify({"error": "Imagem muito grande. Use uma foto menor (menos de ~5 MB)."}), 400

        # Super admin: sem limite. Demais usuários: verificar limite diário
        if not _is_super_admin(user_role, user_id):
            hoje = datetime.now(timezone.utc).strftime("%Y-%m-%d")
            uso_ref = reader.db.collection("bases").document(base_id).collection("assistente_uso").document(hoje)
            uso_doc = uso_ref.get()
            contagem = uso_doc.to_dict().get("contagem", 0) if uso_doc.exists else 0
            if contagem >= LIMITE_PERGUNTAS_POR_BASE:
                return jsonify({
                    "error": "Limite diário de 15 perguntas atingido para esta base. Tente novamente amanhã."
                }), 429

        contexto_base = reader.get_contexto_base_para_assistente(base_id) if reader else ""
        result_text = _assistente_via_openai(
            text, image_b64, context_base=contexto_base, history=history,
            user_name=user_name, user_role=user_role, turno=turno
        )

        if result_text is None or result_text == "":
            return jsonify({
                "error": "Assistente indisponível. Verifique OPENAI_API_KEY no servidor."
            }), 500

        # Extrair TODAS as ações ACTION_JSON do texto (pode haver múltiplas quando a imagem tem vários motoristas)
        def _extract_all_action_jsons(text: str):
            """Remove todos os blocos ACTION_JSON: {...} do texto e retorna (texto_limpo, lista_de_acoes)."""
            acts = []
            remaining = text
            while True:
                idx = remaining.find("ACTION_JSON:")
                if idx < 0:
                    break
                start = remaining.find("{", idx)
                if start < 0:
                    break
                depth = 0
                end = start
                for i, c in enumerate(remaining[start:], start):
                    if c == "{":
                        depth += 1
                    elif c == "}":
                        depth -= 1
                    if depth == 0:
                        end = i
                        break
                if end <= start:
                    break
                try:
                    parsed = json.loads(remaining[start:end + 1])
                    acts.append(parsed)
                except Exception:
                    pass
                remaining = (remaining[:idx].rstrip() + remaining[end + 1:].lstrip())
            return remaining.strip(), acts

        result_text, actions = _extract_all_action_jsons(result_text)

        # Fallback: resposta foi só um JSON puro (modelo esqueceu o ACTION_JSON:)
        if not actions:
            trimmed = result_text.strip()
            if trimmed.startswith("{") and "}" in trimmed:
                try:
                    end_brace = trimmed.rfind("}")
                    if end_brace > 0:
                        parsed = json.loads(trimmed[: end_brace + 1])
                        if isinstance(parsed, dict) and parsed.get("type") in ("update_in_scale", "add_to_scale"):
                            actions = [parsed]
                            result_text = "Alteração aplicada."
                except Exception:
                    pass

        # Normalizar todas as ações: garantir "ondaIndex" (modelo às vezes envia "ondalndex")
        for a in actions:
            if isinstance(a, dict):
                for key in list(a.keys()):
                    if key in ("ondalndex", "onda_index"):
                        a["ondaIndex"] = a.pop(key)
                        break

        # Incrementar contador de uso (apenas para não-super-admin)
        if not _is_super_admin(user_role, user_id):
            try:
                hoje = datetime.now(timezone.utc).strftime("%Y-%m-%d")
                uso_ref = reader.db.collection("bases").document(base_id).collection("assistente_uso").document(hoje)
                uso_ref.set({
                    "data": hoje,
                    "contagem": Increment(1),
                    "ultimaAtualizacao": firestore.SERVER_TIMESTAMP
                }, merge=True)
            except Exception as inc_err:
                print(f"⚠️ Erro ao incrementar contador assistente: {inc_err}")

        resp_data = {"text": result_text.strip() or "Feito.", "ok": True}
        if actions:
            resp_data["actions"] = actions       # lista completa (novo)
            resp_data["action"] = actions[0]     # retrocompatibilidade (1ª ação)
        return jsonify(resp_data), 200

    except Exception as e:
        print(f"❌ Erro assistente/chat: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


_PATIO_URL = "https://script.google.com/macros/s/AKfycbxkAXMCEEfd1PHS_KEle8MNeh7iB3nDwfkdrt5qUQHLNck1VB5oEWXBa9y7qqd8sEzi3w/exec"

@app.route('/patio/debug', methods=['GET'])
def patio_debug():
    """Debug: retorna o HTML bruto da página do pátio para inspecionar a estrutura."""
    try:
        headers = {"User-Agent": "Mozilla/5.0 (compatible; ControleEscalas/1.0)"}
        results = {}
        # Tentar URL base e variações comuns de Apps Script com JSON
        urls_to_try = [
            _PATIO_URL,
            _PATIO_URL + "?action=getData",
            _PATIO_URL + "?format=json",
            _PATIO_URL + "?type=json",
        ]
        for url in urls_to_try:
            try:
                resp = http_requests.get(url, timeout=15, headers=headers, allow_redirects=True)
                content_type = resp.headers.get("Content-Type", "")
                user_html = _extrair_user_html(resp.text)
                results[url] = {
                    "status": resp.status_code,
                    "content_type": content_type,
                    "body_preview": resp.text[:1000],
                    "user_html_preview": user_html[:3000] if user_html else None,
                    "is_json": "json" in content_type.lower(),
                }
            except Exception as e:
                results[url] = {"error": str(e)}
        return jsonify(results), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

def _extrair_user_html(html_text: str) -> str:
    """Extrai e decodifica o userHtml embutido na resposta do Google Apps Script."""
    import re, codecs
    # O HTML real está dentro de goog.script.init("{ ... \"userHtml\":\"...\" ... }")
    match = re.search(r'goog\.script\.init\("((?:[^"\\]|\\.)*)"\)', html_text, re.DOTALL)
    if not match:
        return ""
    raw = match.group(1)
    # Decodificar escapes \xNN para bytes e depois UTF-8
    try:
        decoded = codecs.decode(raw.replace('\\x', '%').encode(), 'unicode_escape')
    except Exception:
        decoded = raw
    # Extrair o valor de userHtml do JSON decodificado
    uh_match = re.search(r'"userHtml"\s*:\s*"((?:[^"\\]|\\.)*)"', decoded, re.DOTALL)
    if not uh_match:
        return decoded  # retornar tudo se não encontrar
    user_html_raw = uh_match.group(1)
    # Decodificar escapes duplos
    try:
        user_html = user_html_raw.encode('utf-8').decode('unicode_escape')
    except Exception:
        user_html = user_html_raw
    return user_html


def _parsear_motoristas_do_html(html: str) -> list:
    """Parseia motoristas de um HTML (tabela ou JSON embutido)."""
    import re, json as _json
    from bs4 import BeautifulSoup
    motoristas = []

    soup = BeautifulSoup(html, "lxml")

    # 1. Tentar tabelas HTML
    for table in soup.find_all("table"):
        rows = table.find_all("tr")
        if len(rows) < 2:
            continue
        headers = [th.get_text(strip=True).lower() for th in rows[0].find_all(["th", "td"])]
        for row in rows[1:]:
            cols = [td.get_text(strip=True) for td in row.find_all(["td", "th"])]
            if not any(cols):
                continue
            item = {headers[i]: v for i, v in enumerate(cols) if i < len(headers)}
            motoristas.append({
                "transportadora": item.get("transportadora") or item.get("empresa") or item.get("col0") or cols[0] if cols else "",
                "placa": item.get("placa") or item.get("veículo") or item.get("veiculo") or (cols[1] if len(cols) > 1 else ""),
                "tempo": item.get("tempo") or item.get("tempo na vaga") or item.get("permanência") or item.get("permanencia") or (cols[2] if len(cols) > 2 else ""),
            })

    # 2. Tentar JSON embutido em <script>
    if not motoristas:
        for script in soup.find_all("script"):
            text = script.string or ""
            for pattern in [
                r'(?:dados|data|veiculos|motoristas|registros)\s*=\s*(\[.*?\])',
                r'(\[\s*\{[^;]+\}\s*\])',
            ]:
                m = re.search(pattern, text, re.DOTALL | re.IGNORECASE)
                if m:
                    try:
                        parsed = _json.loads(m.group(1))
                        if isinstance(parsed, list) and parsed:
                            motoristas = parsed
                            break
                    except Exception:
                        pass
            if motoristas:
                break

    return motoristas


@app.route('/patio/motoristas', methods=['GET'])
def patio_motoristas():
    """
    Busca dados do painel de pátio (SRJ8) e retorna lista de motoristas com tempo na vaga.
    Retorna JSON: { "motoristas": [{"transportadora": "...", "placa": "...", "tempo": "..."}] }
    """
    try:
        resp = http_requests.get(
            _PATIO_URL,
            timeout=15,
            headers={"User-Agent": "Mozilla/5.0 (compatible; ControleEscalas/1.0)"},
            allow_redirects=True,
        )
        resp.raise_for_status()

        # O Apps Script embute o HTML real no campo userHtml dentro do wrapper do Google
        user_html = _extrair_user_html(resp.text)

        # Parsear motoristas do HTML real
        motoristas = _parsear_motoristas_do_html(user_html) if user_html else []

        # Fallback: tentar parsear o HTML bruto se não encontrou nada
        if not motoristas:
            motoristas = _parsear_motoristas_do_html(resp.text)

        return jsonify({"motoristas": motoristas, "total": len(motoristas)}), 200

    except http_requests.exceptions.Timeout:
        return jsonify({"error": "Painel de pátio demorou muito para responder"}), 504
    except Exception as e:
        print(f"❌ Erro patio/motoristas: {e}")
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
    print("   POST /notify/status-change      - Notificar mudança de status (motorista + admins)")
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
