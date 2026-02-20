"""
firestore_reader.py

Lê tokens FCM do Firestore para uma base específica.
Conecta ao Firestore usando Firebase Admin SDK com Service Account.
"""

import firebase_admin
from firebase_admin import credentials, firestore
from typing import List, Dict, Optional
import os


class FirestoreReader:
    """Classe para ler dados do Firestore"""
    
    def __init__(self, service_account_path: Optional[str] = None):
        """
        Inicializa o Firebase Admin SDK
        
        Args:
            service_account_path: Caminho para o arquivo JSON do Service Account.
                                Se None, tenta usar variável de ambiente ou inicialização padrão.
        """
        # Verificar se já foi inicializado
        if not firebase_admin._apps:
            if service_account_path:
                if not os.path.exists(service_account_path):
                    raise FileNotFoundError(f"Service Account JSON não encontrado: {service_account_path}")
                cred = credentials.Certificate(service_account_path)
            else:
                # Tentar usar variável de ambiente ou credenciais padrão
                service_account_json = os.getenv('FIREBASE_SERVICE_ACCOUNT_JSON')
                if service_account_json:
                    import json
                    cred = credentials.Certificate(json.loads(service_account_json))
                else:
                    # Tentar usar Application Default Credentials (GCP)
                    cred = credentials.ApplicationDefault()
            
            firebase_admin.initialize_app(cred)
        
        self.db = firestore.client()
        print("✅ Firebase Admin SDK inicializado com sucesso")
    
    def get_motoristas_tokens(self, base_id: str) -> List[Dict[str, str]]:
        """
        Busca todos os motoristas de uma base que possuem fcmToken
        
        Args:
            base_id: ID da base no Firestore
            
        Returns:
            Lista de dicionários com motorista_id e fcmToken
            Exemplo: [
                {"motorista_id": "abc123", "fcmToken": "token1"},
                {"motorista_id": "def456", "fcmToken": "token2"}
            ]
        """
        motoristas_ref = self.db.collection('bases').document(base_id).collection('motoristas')
        
        # Buscar todos os documentos
        docs = motoristas_ref.stream()
        
        tokens = []
        for doc in docs:
            data = doc.to_dict()
            motorista_id = doc.id
            fcm_token = data.get('fcmToken')
            
            # Apenas adicionar se tiver token válido
            if fcm_token and isinstance(fcm_token, str) and len(fcm_token) > 0:
                tokens.append({
                    "motorista_id": motorista_id,
                    "fcmToken": fcm_token,
                    "nome": data.get('nome', 'Motorista')
                })
                print(f"  ✅ Token encontrado para motorista {motorista_id} ({data.get('nome', 'N/A')})")
            else:
                print(f"  ⚠️ Motorista {motorista_id} não possui fcmToken válido")
        
        print(f"\n📊 Total de tokens encontrados: {len(tokens)}")
        return tokens
    
    def get_motorista_token(self, base_id: str, motorista_id: str) -> Optional[Dict[str, str]]:
        """
        Busca o token FCM de um motorista específico
        
        Args:
            base_id: ID da base no Firestore
            motorista_id: ID do motorista
            
        Returns:
            Dicionário com motorista_id, fcmToken e nome, ou None se não encontrado
        """
        motorista_ref = self.db.collection('bases').document(base_id).collection('motoristas').document(motorista_id)
        doc = motorista_ref.get()
        
        if not doc.exists:
            return None
        
        data = doc.to_dict()
        fcm_token = data.get('fcmToken')
        
        if fcm_token and isinstance(fcm_token, str) and len(fcm_token) > 0:
            return {
                "motorista_id": motorista_id,
                "fcmToken": fcm_token,
                "nome": data.get('nome', 'Motorista')
            }
        
        return None
    
    def get_all_bases(self) -> List[str]:
        """
        Retorna lista de IDs de todas as bases no Firestore
        
        Returns:
            Lista de IDs de bases
        """
        bases_ref = self.db.collection('bases')
        docs = bases_ref.stream()
        
        base_ids = [doc.id for doc in docs]
        print(f"📋 Bases encontradas: {len(base_ids)}")
        return base_ids

    def get_galpao_coordenadas(self, base_id: str) -> Optional[Dict[str, float]]:
        """
        Busca coordenadas do galpão em configuracao/principal

        Returns:
            {"lat": float, "lng": float} ou None
        """
        config_ref = self.db.collection('bases').document(base_id).collection('configuracao').document('principal')
        doc = config_ref.get()
        if not doc.exists:
            return None
        data = doc.to_dict()
        galpao = data.get('galpao') or {}
        lat = galpao.get('lat')
        lng = galpao.get('lng')
        if lat is not None and lng is not None:
            return {"lat": float(lat), "lng": float(lng)}
        return None

    def get_usuario_papel(self, base_id: str, user_id: str) -> Optional[str]:
        """Retorna o papel do usuário (admin, auxiliar, superadmin, etc) na base ou None.
        Busca por ID do documento e, se não achar, por campo authUid (Firebase Auth UID)."""
        for col in ['usuarios', 'motoristas']:
            ref = self.db.collection('bases').document(base_id).collection(col).document(user_id)
            doc = ref.get()
            if doc.exists:
                return (doc.to_dict() or {}).get('papel')
            # Login anônimo: documento pode ter ID diferente do Auth UID; buscar por authUid
            try:
                q = (
                    self.db.collection('bases')
                    .document(base_id)
                    .collection(col)
                    .where('authUid', '==', user_id)
                    .limit(1)
                )
                for d in q.stream():
                    return (d.to_dict() or {}).get('papel')
            except Exception:
                pass
        return None

    def get_usuario_papel_in_any_base(self, user_id: str) -> Optional[str]:
        """Retorna o papel do usuário em qualquer base (ex.: superadmin que não está na base atual)."""
        for base_id in self.get_all_bases():
            papel = self.get_usuario_papel(base_id, user_id)
            if papel:
                return papel
        return None

    def get_superadmin_uids_from_config(self) -> List[str]:
        """
        Lê UIDs de superadmin do Firestore sistema/config (campo superadminUids, array).
        Permite liberar superadmin sem variável de ambiente.
        """
        try:
            ref = self.db.collection('sistema').document('config')
            doc = ref.get()
            if not doc.exists:
                return []
            data = doc.to_dict() or {}
            uids = data.get('superadminUids') or data.get('superadmin_uids') or []
            if isinstance(uids, list):
                return [str(u).strip() for u in uids if u]
            return []
        except Exception as e:
            print(f"get_superadmin_uids_from_config: {e}")
            return []

    def get_contexto_base_para_assistente(self, base_id: str) -> str:
        """
        Monta um resumo completo da base para o assistente: escala (onda, hora, AM/PM, rota, vaga, sacas),
        tempo estimado (ETA), disponibilidade, quinzena e devoluções (id, quem devolveu).
        """
        from datetime import datetime
        try:
            parts = []
            base_ref = self.db.collection('bases').document(base_id)
            base_doc = base_ref.get()
            nome_base = (base_doc.to_dict() or {}).get('nome', 'Base') if base_doc.exists else 'Base'
            parts.append(f"Base atual: {nome_base} (id: {base_id}).")

            motoristas_ref = base_ref.collection('motoristas')
            motoristas_docs = list(motoristas_ref.stream())
            motoristas_nomes = sorted(
                (d.to_dict() or {}).get('nome', '').strip()
                for d in motoristas_docs
                if (d.to_dict() or {}).get('papel') == 'motorista' and (d.to_dict() or {}).get('ativo', True)
            )
            total_motoristas = len(motoristas_nomes)
            parts.append(f"Total de motoristas na base: {total_motoristas}.")
            if motoristas_nomes:
                parts.append("Motoristas que podem ser escalados (use estes nomes exatos): " + ", ".join(motoristas_nomes) + ".")

            hoje = datetime.utcnow().strftime('%Y-%m-%d')
            escalados_hoje = set()
            # --- ESCALA DETALHADA: turno (AM/PM), onda, hora da onda, vaga, rota, sacas por motorista ---
            escala_detalhes = []
            for turno in ('AM', 'PM'):
                doc_id = f"{hoje}_{turno}"
                escala_ref = base_ref.collection('escalas').document(doc_id)
                escala_doc = escala_ref.get()
                if not escala_doc.exists:
                    continue
                data = escala_doc.to_dict() or {}
                ondas = data.get('ondas') or []
                for idx, onda in enumerate(ondas):
                    nome_onda = onda.get('nome') or f'Onda'
                    horario_onda = (onda.get('horario') or '').strip()
                    itens = onda.get('itens') or []
                    for item in itens:
                        mid = (item.get('motoristaId') or '').strip()
                        nome = (item.get('nome') or '').strip()
                        vaga = (item.get('vaga') or '').strip()
                        rota = (item.get('rota') or '').strip()
                        horario_item = (item.get('horario') or '').strip()
                        sacas = item.get('sacas')
                        sacas_str = str(sacas) if sacas is not None else "-"
                        if mid:
                            escalados_hoje.add((mid, nome))
                        linha = f"  {turno} | {nome_onda} (hora onda: {horario_onda or '-'}) | {nome} | vaga {vaga or '-'} | rota {rota or '-'} | sacas {sacas_str}"
                        if horario_item:
                            linha += f" | horário motorista: {horario_item}"
                        escala_detalhes.append(linha)
            total_escalados = len(escalados_hoje)
            parts.append(f"Escala de hoje ({hoje}): {total_escalados} motoristas escalados.")
            if escala_detalhes:
                parts.append("Detalhe da escala (turno | onda e hora | motorista | vaga | rota | sacas):")
                parts.extend(escala_detalhes[:50])
                if len(escala_detalhes) > 50:
                    parts.append(f"  ... e mais {len(escala_detalhes) - 50} itens.")
            elif total_escalados > 0:
                nomes = sorted(set(n for _, n in escalados_hoje if n))[:20]
                parts.append("Nomes escalados hoje: " + ", ".join(nomes) + ("..." if total_escalados > 20 else "") + ".")

            # --- TEMPO ESTIMADO (ETA): location_responses com status ready ---
            try:
                resp_ref = base_ref.collection('location_responses')
                resp_docs = list(resp_ref.stream())
                etas = []
                for d in resp_docs:
                    data = d.to_dict() or {}
                    if data.get('status') != 'ready':
                        continue
                    nome = (data.get('motoristaNome') or '').strip()
                    dist = data.get('distanceKm')
                    eta_min = data.get('etaMinutes')
                    if nome and eta_min is not None:
                        dist_str = f"{dist:.1f} km" if dist is not None else "?"
                        etas.append(f"{nome}: ~{eta_min} min ({dist_str})")
                if etas:
                    parts.append("Tempo estimado ao galpão (ETA): " + "; ".join(etas) + ".")
            except Exception as e_eta:
                print(f"get_contexto ETA: {e_eta}")

            # --- DISPONIBILIDADE: solicitações recentes (hoje e amanhã) ---
            try:
                from datetime import timedelta
                amanha = (datetime.utcnow() + timedelta(days=1)).strftime('%Y-%m-%d')
                disp_ref = self.db.collection('disponibilidades')
                disp_docs = disp_ref.where('baseId', '==', base_id).limit(30).stream()
                disp_listas = []
                for d in disp_docs:
                    data_disp = d.to_dict() or {}
                    data_str = (data_disp.get('data') or '').strip()
                    if data_str not in (hoje, amanha):
                        continue
                    motoristas = data_disp.get('motoristas') or []
                    resumos = []
                    for m in motoristas:
                        nome_m = (m.get('nome') or '').strip()
                        disp = m.get('disponivel')
                        if disp is True:
                            resumos.append(f"{nome_m}: disponível")
                        elif disp is False:
                            resumos.append(f"{nome_m}: indisponível")
                        else:
                            resumos.append(f"{nome_m}: não respondeu")
                    if resumos:
                        disp_listas.append(f"Data {data_str}: " + "; ".join(resumos[:15]))
                if disp_listas:
                    parts.append("Disponibilidade (hoje/amanhã): " + " | ".join(disp_listas))
            except Exception as e_disp:
                print(f"get_contexto disponibilidade: {e_disp}")

            # --- QUINZENA: dias trabalhados no mês atual (1ª e 2ª quinzena) ---
            try:
                now = datetime.utcnow()
                mes, ano = now.month, now.year
                quinzenas_ref = self.db.collection('quinzenas')
                q_docs = quinzenas_ref.where('baseId', '==', base_id).where('mes', '==', mes).where('ano', '==', ano).limit(50).stream()
                q_resumos = []
                for d in q_docs:
                    data_q = d.to_dict() or {}
                    nome_q = (data_q.get('motoristaNome') or '').strip()
                    p1 = data_q.get('primeiraQuinzena') or {}
                    p2 = data_q.get('segundaQuinzena') or {}
                    d1 = p1.get('diasTrabalhados') if isinstance(p1.get('diasTrabalhados'), (int, float)) else 0
                    d2 = p2.get('diasTrabalhados') if isinstance(p2.get('diasTrabalhados'), (int, float)) else 0
                    if nome_q:
                        q_resumos.append(f"{nome_q}: 1ª quinzena {d1} dia(s), 2ª quinzena {d2} dia(s)")
                if q_resumos:
                    parts.append("Quinzena do mês (dias trabalhados): " + "; ".join(q_resumos[:25]))
            except Exception as e_q:
                print(f"get_contexto quinzena: {e_q}")

            # --- DEVOLUÇÕES: id da devolução, quem devolveu (motoristaNome), data, hora ---
            try:
                dev_ref = base_ref.collection('devolucoes')
                dev_docs = list(dev_ref.order_by('timestamp', direction=firestore.Query.DESCENDING).limit(25).stream())
                dev_listas = []
                for d in dev_docs:
                    data_dev = d.to_dict() or {}
                    dev_id = d.id
                    quem = (data_dev.get('motoristaNome') or '').strip()
                    data_str = data_dev.get('data', '')
                    hora_str = data_dev.get('hora', '')
                    qtd = len(data_dev.get('idsPacotes') or []) or data_dev.get('quantidade') or 0
                    dev_listas.append(f"id={dev_id} | quem devolveu={quem} | data={data_str} | hora={hora_str} | pacotes={qtd}")
                if dev_listas:
                    parts.append("Devoluções recentes (id da devolução, quem devolveu, data, hora, quantidade): " + " | ".join(dev_listas))
            except Exception as e_dev:
                print(f"get_contexto devolucoes: {e_dev}")

            return " ".join(parts)
        except Exception as e:
            print(f"get_contexto_base_para_assistente: {e}")
            return ""

    def write_location_response(self, base_id: str, motorista_id: str, data: dict, merge: bool = True):
        """Grava documento em bases/{baseId}/location_responses/{motoristaId}"""
        ref = self.db.collection('bases').document(base_id).collection('location_responses').document(motorista_id)
        ref.set(data, merge=merge)


if __name__ == "__main__":
    # Teste básico
    import sys
    
    if len(sys.argv) < 2:
        print("Uso: python firestore_reader.py <caminho_service_account.json> [base_id]")
        sys.exit(1)
    
    service_account_path = sys.argv[1]
    base_id = sys.argv[2] if len(sys.argv) > 2 else None
    
    reader = FirestoreReader(service_account_path)
    
    if base_id:
        tokens = reader.get_motoristas_tokens(base_id)
        print(f"\n✅ Encontrados {len(tokens)} tokens para base {base_id}")
    else:
        bases = reader.get_all_bases()
        print(f"\n✅ Encontradas {len(bases)} bases")
        for base in bases:
            print(f"  - {base}")
