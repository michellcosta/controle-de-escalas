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
import difflib
from datetime import datetime, timezone
import requests as http_requests
import openai
from flask import Flask, request, jsonify
from flask_cors import CORS
from firebase_admin import auth, firestore
from google.cloud.firestore_v1.transforms import Increment
from firestore_reader import FirestoreReader, normalizar_nome
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


VERSAO_MINIMA_ESCRITA = 12  # precisa casar com 'podeEscrever()' no firestore.rules


def _verify_firebase_token(exigir_versao=True):
    """
    Verifica Authorization: Bearer <idToken> e o GATE DE VERSÃO.
    Exige custom claim "v" >= VERSAO_MINIMA_ESCRITA: apps antigos não usam a API
    (economiza OpenAI/push; o Firestore já os barra pelas security rules).
    /registrar_versao chama com exigir_versao=False — é quem GRAVA a claim;
    exigir versão ali criaria um deadlock (claim só existe depois do registro).
    Retorna (uid, None) ou (None, error_tuple).
    """
    auth_header = request.headers.get('Authorization')
    if not auth_header or not auth_header.startswith('Bearer '):
        return None, ({"error": "Token de autenticação obrigatório"}, 401)
    token = auth_header[7:].strip()
    if not token:
        return None, ({"error": "Token inválido"}, 401)
    try:
        decoded = auth.verify_id_token(token)
    except Exception as e:
        print(f"Token verification failed: {e}")
        return None, ({"error": "Token inválido ou expirado"}, 401)
    if exigir_versao:
        versao = decoded.get('v', 0) or 0
        if versao < VERSAO_MINIMA_ESCRITA:
            print(f"⛔ App antigo bloqueado na API: uid={decoded.get('uid')} v={versao}")
            return None, ({"error": "Versão do app muito antiga. Atualize o aplicativo.", "atualizacaoObrigatoria": True}, 403)
    return decoded.get('uid'), None


@app.route('/registrar_versao', methods=['POST'])
def registrar_versao():
    """
    Registra a versão do app como custom claim do usuário autenticado.
    O gate de versão nas security rules nega escrita de apps antigos:
    request.auth.token.v precisa ser >= VERSAO_MINIMA_ESCRITA.
    Body: { "versionCode": 12 }
    Header: Authorization: Bearer <Firebase ID Token>
    """
    try:
        initialize_services()
        # exigir_versao=False: este endpoint É quem grava a claim de versão.
        uid, err = _verify_firebase_token(exigir_versao=False)
        if err:
            return jsonify(err[0]), err[1]
        data = request.get_json() or {}
        try:
            version_code = int(data.get('versionCode') or 0)
        except (TypeError, ValueError):
            return jsonify({"error": "versionCode inválido"}), 400
        if version_code <= 0:
            return jsonify({"error": "versionCode é obrigatório"}), 400

        auth.set_custom_user_claims(uid, {"v": version_code})
        print(f"✅ Versão {version_code} registrada para uid {uid}")

        return jsonify({
            "ok": True,
            "versaoRegistrada": version_code,
            "atualizacaoObrigatoria": version_code < VERSAO_MINIMA_ESCRITA,
        }), 200
    except Exception as e:
        print(f"❌ Erro registrar_versao: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


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
    "# IDENTIDADE\n"
    "Você é o assistente do app Controle de Escalas, falando em português. Responda sobre: escalas de motoristas, "
    "vagas, rotas, ondas, horários, localização/ETA, disponibilidade, quinzena, devoluções e avisos. "
    "Cumprimente o usuário pelo nome (IDENTIDADE DO USUÁRIO) quando natural. Responda com números e nomes reais dos DADOS DA BASE. "
    "O usuário só vê TEXTO amigável — nunca escreva JSON, códigos ou estruturas técnicas no texto. "
    "Se perguntarem algo fora de escalas/motoristas/app, recuse com uma frase curta. "
    "\n\n# FERRAMENTAS (AÇÕES)\n"
    "Para QUALQUER alteração ou aviso, use as FERRAMENTAS (tool calls) — nunca escreva ACTION_JSON no texto. "
    "Sempre escreva junto uma frase curta confirmando o que fez (ex.: 'Pronto, alterei a rota do Michell para K7.'). "
    "- add_to_scale: ADICIONAR um motorista que AINDA NÃO está na escala. Exige rota (sacas opcional). "
    "Se faltar a rota, NÃO chame a ferramenta: pergunte 'Qual a rota do [nome]? Tem sacas?' e espere. "
    "- update_in_scale: TROCAR/ALTERAR vaga, rota ou sacas de quem JÁ está escalado (só os campos que mudam). "
    "- remove_from_scale: TIRAR alguém da escala (informe a ondaIndex onde está, se souber). "
    "- send_notification: AVISAR — 1 motorista (motoristaNome), 1 onda (ondaIndex) ou a base inteira (baseInteira=true). "
    "Regras: ondaIndex 0 = 1ª onda; vaga sempre com 2 dígitos (01, 02); rota em MAIÚSCULAS. "
    "NUNCA adicione motorista que já aparece em 'Motoristas já escalados' — nesse caso use update_in_scale. "
    "Vários motoristas (ex.: foto com 6 nomes) = uma chamada para CADA um, sem pular nenhum. "
    "Escala vazia/sem ondas? Adicione mesmo assim com ondaIndex 0 — o app cria a 1ª ONDA automaticamente. "
    "\n\n# DATAS E TURNOS\n"
    "DATAS ATUAIS (no final dos dados) dizem que dia é hoje e amanhã — use-as para 'hoje', 'amanhã', dias da semana. "
    "Os dados trazem a escala de HOJE detalhada e a de AMANHÃ em resumo. "
    "Turno explícito no pedido vence tudo ('montar AM' = AM). Sem turno explícito e ambíguo, PERGUNTE: "
    "'Para qual turno: AM ou PM?' — TURNO ATUAL (aba do app) é contexto, não comando. Foto sem turno e ambígua: pergunte antes. "
    "\n\n# REGRAS DE ESCALA (RESUMO)\n"
    "1. Quem já está escalado: update_in_scale ou remove_from_scale — nunca add_to_scale. "
    "2. Adicionar exige rota; sem rota, pergunte antes. "
    "3. 'Montar a escala' sem mais detalhes + falta de informação essencial → pergunte antes de agir. "
    "\n\n# DEVOLUÇÕES (FORMATO OBRIGATÓRIO)\n"
    "Para cada motorista: primeiro 'Total por dia: [data] X devolução(ões); ...', depois uma linha por devolução: "
    "'DD/MM/AAAA HH:MM — N pacote(s). IDs: id1, id2, ...' Sem lista numerada 1. 2. 3.; não invente IDs. "
    "Pode dar insights simples ('[Nome] teve X devoluções esta semana'). "
    "\n\n# GUIA DO APP (FAQ)\n"
    "QR Code: ícone de câmera no menu inferior da tela do motorista. Devoluções: painel admin > 'Devoluções'. "
    "Motoristas: painel admin > 'Usuários'. Localização: 'Configuração de Localização'. "
    "Dias trabalhados: 'Quinzena' no menu admin ou no card do motorista. "
    "\n\n# VARIAÇÕES DE LINGUAGEM (interprete como equivalentes)\n"
    "MONTAR ESCALA: montar/fazer/organizar/arrumar/preparar a escala. STATUS: 'quem está carregando', 'quem subiu', "
    "'onde está o [nome]', 'situação do [nome]'. AVISAR: chamar/avisar/notificar/mandar subir. ADICIONAR: colocar/incluir/"
    "escalar/botar na escala/entrar na onda. ALTERAR: trocar/mudar/atualizar. ONDAS: 'primeira onda' = 1ª onda = ondaIndex 0; "
    "'manhã' = AM; 'tarde' = PM. Mantenha o contexto: 'sim', 'confirmado' etc. referem-se à mensagem anterior."
)

# Ferramentas (function calling nativo) do assistente — substituem o ACTION_JSON em texto.
_ASSISTENTE_TOOLS = [
    {"type": "function", "function": {
        "name": "add_to_scale",
        "description": "Adiciona um motorista que AINDA NÃO está na escala. Exige rota; se não souber a rota, pergunte ao usuário antes de chamar.",
        "parameters": {"type": "object", "properties": {
            "motoristaNome": {"type": "string", "description": "Nome exato do motorista conforme DADOS DA BASE"},
            "ondaIndex": {"type": "integer", "minimum": 0, "description": "0 = 1ª onda, 1 = 2ª onda..."},
            "vaga": {"type": "string", "description": "Vaga com 2 dígitos (ex.: 01)"},
            "rota": {"type": "string", "description": "Rota em maiúsculas (ex.: G9)"},
            "sacas": {"type": "integer", "description": "Quantidade de sacas (opcional)"}
        }, "required": ["motoristaNome", "ondaIndex", "vaga", "rota"]}}},
    {"type": "function", "function": {
        "name": "update_in_scale",
        "description": "Altera vaga, rota e/ou sacas de um motorista JÁ escalado. Preencha só o que muda.",
        "parameters": {"type": "object", "properties": {
            "motoristaNome": {"type": "string", "description": "Nome exato do motorista"},
            "ondaIndex": {"type": "integer", "minimum": 0, "description": "Onda em que o motorista está (0 = 1ª)"},
            "vaga": {"type": "string", "description": "Nova vaga com 2 dígitos (opcional)"},
            "rota": {"type": "string", "description": "Nova rota em maiúsculas (opcional)"},
            "sacas": {"type": "integer", "description": "Nova quantidade de sacas (opcional)"}
        }, "required": ["motoristaNome", "ondaIndex"]}}},
    {"type": "function", "function": {
        "name": "remove_from_scale",
        "description": "Remove um motorista da escala de hoje (do turno atual).",
        "parameters": {"type": "object", "properties": {
            "motoristaNome": {"type": "string", "description": "Nome exato do motorista"},
            "ondaIndex": {"type": "integer", "minimum": 0, "description": "Onda em que o motorista está (0 = 1ª); omita se não souber"}
        }, "required": ["motoristaNome"]}}},
    {"type": "function", "function": {
        "name": "send_notification",
        "description": "Envia aviso push. Alvo: um motorista (motoristaNome), uma onda (ondaIndex) ou a base inteira (baseInteira=true).",
        "parameters": {"type": "object", "properties": {
            "body": {"type": "string", "description": "Texto do aviso para o motorista"},
            "motoristaNome": {"type": "string", "description": "Nome do motorista (avisar só ele)"},
            "ondaIndex": {"type": "integer", "minimum": 0, "description": "Índice da onda (avisar a onda toda)"},
            "baseInteira": {"type": "boolean", "description": "true = avisar todos os motoristas da base"}
        }, "required": ["body"]}}},
]


def _assistente_via_openai(text: str, image_b64: Optional[str], context_base: Optional[str] = None, history: Optional[list] = None, user_name: str = "Usuário", user_role: str = "Membro", turno: Optional[str] = None) -> Tuple[Optional[str], list]:
    """
    Usa OpenAI (gpt-4o-mini por padrão) com function calling nativo.
    Retorna (texto_para_o_usuario, acoes_extraidas_das_tool_calls).
    """
    api_key = os.getenv('OPENAI_API_KEY')
    if not api_key:
        print("OPENAI_API_KEY não configurada.")
        return None, []

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
            tools=_ASSISTENTE_TOOLS,
            tool_choice="auto",
        )
        message = response.choices[0].message
        result = (message.content or "").strip()

        # Converter tool calls em ações para o app
        acoes = []
        for tc in (message.tool_calls or []):
            try:
                args = json.loads(tc.function.arguments or "{}")
            except Exception:
                print(f"⚠️ Tool call com argumentos inválidos: {tc.function.name}")
                continue
            if not isinstance(args, dict):
                continue
            acoes.append({"type": tc.function.name, **args})
        if acoes and not result:
            result = _texto_confirmacao_acoes(acoes)

        if acoes:
            print(f"✅ OpenAI {model} respondeu ({len(result)} chars) com {len(acoes)} tool call(s)")
        else:
            print(f"✅ OpenAI {model} respondeu ({len(result)} chars)")
        return result, acoes
    except openai.AuthenticationError:
        print("❌ OpenAI: OPENAI_API_KEY inválida.")
        return None, []
    except openai.RateLimitError:
        print("❌ OpenAI: Rate limit atingido.")
        return None, []
    except Exception as e:
        print(f"❌ OpenAI request failed: {e}")
        return None, []


def _texto_confirmacao_acoes(acoes: list) -> str:
    """Texto amigável padrão quando o modelo só devolve tool calls sem texto."""
    if not acoes:
        return "Feito."
    nomes = [str(a.get("motoristaNome") or "").strip() for a in acoes]
    nomes = [n for n in nomes if n]
    if len(acoes) == 1:
        tipo = acoes[0].get("type")
        if tipo == "add_to_scale":
            return f"Pronto, adicionei {nomes[0] if nomes else 'o motorista'} na escala."
        if tipo == "update_in_scale":
            return f"Pronto, atualizei {nomes[0] if nomes else 'os dados do motorista'}."
        if tipo == "remove_from_scale":
            return f"Pronto, removi {nomes[0] if nomes else 'o motorista'} da escala."
        if tipo == "send_notification":
            return "Aviso enviado!"
    if nomes:
        return f"Pronto! Apliquei as alterações para {len(nomes)} motorista(s): " + ", ".join(nomes[:6]) + (".")
    return "Pronto, alterações aplicadas."


def _resolver_acoes(acoes: list, estrutura: Optional[dict]) -> list:
    """
    Normaliza campos das ações e resolve motoristaNome -> motoristaId
    (comparação sem acentos/caixa + fuzzy) contra os motoristas reais da base.
    """
    if not acoes:
        return []
    motoristas = (estrutura or {}).get("motoristas") or {}
    nomes_normalizados = list(motoristas.keys())

    resolvidas = []
    for a in acoes:
        if not isinstance(a, dict):
            continue

        # Normalizações de formato
        if "ondaIndex" in a:
            try:
                a["ondaIndex"] = max(0, int(a["ondaIndex"]))
            except (TypeError, ValueError):
                a["ondaIndex"] = 0
        if a.get("vaga"):
            v = str(a["vaga"]).strip()
            a["vaga"] = v.zfill(2) if v.isdigit() else v.upper()
        if a.get("rota"):
            a["rota"] = str(a["rota"]).strip().upper()
        if a.get("sacas") is not None:
            try:
                a["sacas"] = int(a["sacas"])
            except (TypeError, ValueError):
                a["sacas"] = None

        # send_notification sem alvo específico = base inteira
        if a.get("type") == "send_notification" \
                and not (a.get("motoristaNome") or "").strip() \
                and a.get("ondaIndex") is None:
            a["baseInteira"] = True

        # Resolver motoristaNome -> motoristaId
        nome = (a.get("motoristaNome") or "").strip()
        if nome and motoristas:
            chave = normalizar_nome(nome)
            alvo = motoristas.get(chave)
            if alvo is None and chave:
                contendo = [k for k in nomes_normalizados if k.startswith(chave) or chave in k]
                if len(contendo) == 1:
                    alvo = motoristas[contendo[0]]
            if alvo is None:
                proximos = difflib.get_close_matches(chave, nomes_normalizados, n=1, cutoff=0.55)
                if proximos:
                    alvo = motoristas[proximos[0]]
            if alvo:
                a["motoristaId"] = alvo["id"]
                a["motoristaNome"] = alvo["nome"]  # nome canônico da base

        resolvidas.append(a)
    return resolvidas


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

        perguntas_restantes = None
        # Super admin: sem limite. Demais usuários: verificar limite diário
        if not _is_super_admin(user_role, user_id):
            hoje = datetime.now(timezone.utc).strftime("%Y-%m-%d")
            uso_ref = reader.db.collection("bases").document(base_id).collection("assistente_uso").document(hoje)
            uso_doc = uso_ref.get()
            contagem = uso_doc.to_dict().get("contagem", 0) if uso_doc.exists else 0
            perguntas_restantes = max(0, LIMITE_PERGUNTAS_POR_BASE - (contagem + 1))
            if contagem >= LIMITE_PERGUNTAS_POR_BASE:
                return jsonify({
                    "error": "Limite diário de 15 perguntas atingido para esta base. Tente novamente amanhã."
                }), 429

        contexto_base = reader.get_contexto_base_para_assistente(base_id) if reader else ""
        estrutura_base = reader.get_estrutura_base_para_assistente(base_id) if reader else {}
        result_text, acoes_tools = _assistente_via_openai(
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

        # 1) Ações vindas das tool calls nativas (caminho principal)
        actions = list(acoes_tools or [])

        # 2) Fallback: ACTION_JSON em texto (legado) — mantido por robustez
        result_text, actions_texto = _extract_all_action_jsons(result_text)
        if actions_texto:
            actions.extend(actions_texto)

        # Fallback: resposta foi só um JSON puro (modelo esqueceu o ACTION_JSON:)
        if not actions:
            trimmed = result_text.strip()
            if trimmed.startswith("{") and "}" in trimmed:
                try:
                    end_brace = trimmed.rfind("}")
                    if end_brace > 0:
                        parsed = json.loads(trimmed[: end_brace + 1])
                        if isinstance(parsed, dict) and parsed.get("type") in ("update_in_scale", "add_to_scale", "remove_from_scale", "send_notification"):
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

        # 3) Resolver motoristas no servidor: nome -> id (fuzzy) + normalização de campos
        actions = _resolver_acoes(actions, estrutura_base)

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
        if perguntas_restantes is not None:
            resp_data["perguntasRestantes"] = perguntas_restantes
        if actions:
            resp_data["actions"] = actions       # lista completa (novo)
            resp_data["action"] = actions[0]     # retrocompatibilidade (1ª ação)
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
