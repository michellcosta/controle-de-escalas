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
        """Retorna o papel do usuário (admin, auxiliar, superadmin, etc) na base ou None"""
        for col in ['usuarios', 'motoristas']:
            ref = self.db.collection('bases').document(base_id).collection(col).document(user_id)
            doc = ref.get()
            if doc.exists:
                return (doc.to_dict() or {}).get('papel')
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
