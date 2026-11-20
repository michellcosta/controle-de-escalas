"""
fcm_sender.py

Envia notificações push via Firebase Cloud Messaging (FCM) HTTP v1 API.
Usa Service Account para autenticação.
"""

import requests
import json
import os
from concurrent.futures import ThreadPoolExecutor
from typing import Dict, List, Optional, Tuple
from google.oauth2 import service_account
from google.auth.transport.requests import Request


class FCMSender:
    """Classe para enviar notificações push via FCM HTTP v1"""
    
    # URL da API FCM HTTP v1
    FCM_ENDPOINT = "https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    
    def __init__(self, service_account_path: Optional[str] = None, project_id: Optional[str] = None):
        """
        Inicializa o FCM Sender
        
        Args:
            service_account_path: Caminho para o arquivo JSON do Service Account
            project_id: ID do projeto Firebase (se None, será lido do Service Account)
        """
        if service_account_path:
            if not os.path.exists(service_account_path):
                raise FileNotFoundError(f"Service Account JSON não encontrado: {service_account_path}")
            
            # Carregar credenciais do Service Account
            self.credentials = service_account.Credentials.from_service_account_file(
                service_account_path,
                scopes=['https://www.googleapis.com/auth/firebase.messaging']
            )
            
            # Obter project_id do Service Account se não fornecido
            if not project_id:
                with open(service_account_path, 'r') as f:
                    sa_data = json.load(f)
                    project_id = sa_data.get('project_id')
        else:
            # Tentar usar variável de ambiente
            service_account_json = os.getenv('FIREBASE_SERVICE_ACCOUNT_JSON')
            if service_account_json:
                sa_data = json.loads(service_account_json)
                self.credentials = service_account.Credentials.from_service_account_info(
                    sa_data,
                    scopes=['https://www.googleapis.com/auth/firebase.messaging']
                )
                if not project_id:
                    project_id = sa_data.get('project_id')
            else:
                raise ValueError("Service Account não fornecido. Use service_account_path ou variável de ambiente FIREBASE_SERVICE_ACCOUNT_JSON")
        
        if not project_id:
            raise ValueError("project_id não encontrado. Forneça explicitamente ou use Service Account válido.")
        
        self.project_id = project_id
        self.endpoint = self.FCM_ENDPOINT.format(project_id=project_id)
        
        print(f"✅ FCM Sender inicializado para projeto: {project_id}")
    
    def _get_access_token(self) -> str:
        """
        Obtém token de acesso OAuth2 para autenticação na API FCM
        
        Returns:
            Token de acesso
        """
        # Atualizar credenciais se necessário
        if not self.credentials.valid:
            self.credentials.refresh(Request())
        
        return self.credentials.token
    
    def _build_message_data_only(self, token: str, title: str, body: str, data: Optional[Dict] = None) -> Dict:
        """
        Constrói mensagem FCM data-only (sem notification payload).
        Evita duplicação: o sistema NÃO exibe automaticamente; o app recebe e exibe UMA notificação.
        Inclui title e body no data para o app exibir via handleDataMessage.
        """
        data_merged = dict(data) if data else {}
        data_merged["title"] = title
        data_merged["message"] = body  # app usa data["message"]
        if "tipo" not in data_merged:
            data_merged["tipo"] = data_merged.get("type", "notification")  # app usa tipo ou type
        payload = {str(k): str(v) for k, v in data_merged.items()}
        return {
            "message": {
                "token": token,
                "data": payload,
                "android": {"priority": "high"},
                "apns": {
                    "headers": {"apns-priority": "10"},
                    "payload": {"aps": {"contentAvailable": True}}
                }
            }
        }

    def _build_message(self, token: str, title: str, body: str, data: Optional[Dict] = None) -> Dict:
        """
        Constrói a mensagem FCM data-only (evita duplicação no app).
        Usa _build_message_data_only para enviar apenas via data.
        """
        return self._build_message_data_only(token, title, body, data)
    
    def send_to_token(self, token: str, title: str, body: str, data: Optional[Dict] = None) -> Tuple[bool, Optional[str]]:
        """
        Envia notificação push para um único token
        
        Args:
            token: Token FCM do dispositivo
            title: Título da notificação
            body: Corpo da notificação
            data: Dados adicionais (opcional)
        
        Returns:
            Tupla (sucesso: bool, mensagem_erro: Optional[str])
        """
        try:
            # Obter token de acesso
            access_token = self._get_access_token()
            
            # Construir mensagem
            message = self._build_message(token, title, body, data)
            
            # Fazer requisição HTTP
            headers = {
                "Authorization": f"Bearer {access_token}",
                "Content-Type": "application/json"
            }
            
            response = requests.post(
                self.endpoint,
                headers=headers,
                json=message,
                timeout=10
            )
            
            if response.status_code == 200:
                result = response.json()
                print(f"  ✅ Notificação enviada com sucesso: {result.get('name', 'N/A')}")
                return True, None
            else:
                error_msg = f"Erro {response.status_code}: {response.text}"
                print(f"  ❌ Falha ao enviar: {error_msg}")
                return False, error_msg
        
        except Exception as e:
            error_msg = f"Exceção ao enviar notificação: {str(e)}"
            print(f"  ❌ Erro: {error_msg}")
            return False, error_msg
    
    def send_to_multiple_tokens(
        self,
        tokens: List[Dict[str, str]],
        title: str,
        body: str,
        data: Optional[Dict] = None
    ) -> Dict[str, int]:
        """
        Envia notificação push para múltiplos tokens
        
        Args:
            tokens: Lista de dicionários com 'fcmToken' e 'motorista_id'
            title: Título da notificação
            body: Corpo da notificação
            data: Dados adicionais (opcional)
        
        Returns:
            Dicionário com estatísticas: {"sucessos": int, "falhas": int}
        """
        print(f"\n📤 Enviando notificações para {len(tokens)} dispositivos...")

        # Achado 9 da auditoria: envio sequencial (até ~10s por token) somava
        # minutos em bases grandes e bloqueava o worker do gunicorn. Envio em
        # paralelo (I/O bound — threads resolvem).
        def _enviar(token_info: Dict[str, str]) -> bool:
            token = token_info.get('fcmToken')
            motorista_id = token_info.get('motorista_id', 'N/A')
            if not token:
                print(f"  ⚠️ Token vazio para motorista {motorista_id}, pulando...")
                return False
            success, _ = self.send_to_token(token, title, body, data)
            return success

        with ThreadPoolExecutor(max_workers=8) as executor:
            resultados = list(executor.map(_enviar, tokens))

        sucessos = sum(1 for r in resultados if r)
        falhas = sum(1 for r in resultados if not r)

        print(f"\n📊 Resultado: {sucessos} sucessos, {falhas} falhas")

        return {"sucessos": sucessos, "falhas": falhas}

    def send_silent_data_only(self, token: str, data: Dict[str, str]) -> Tuple[bool, Optional[str]]:
        """
        Envia mensagem FCM APENAS com data (silenciosa) - sem notification.
        Usado para pedido de localização: motorista não percebe, app processa em background.

        Args:
            token: Token FCM do dispositivo
            data: Dados no formato {"chave": "valor"} - todos strings

        Returns:
            Tupla (sucesso: bool, mensagem_erro: Optional[str])
        """
        try:
            access_token = self._get_access_token()

            # FCM HTTP v1: mensagem data-only (sem notification)
            message = {
                "message": {
                    "token": token,
                    "data": {str(k): str(v) for k, v in data.items()},
                    "android": {
                        "priority": "high",
                    },
                    "apns": {
                        "headers": {"apns-priority": "10"},
                        "payload": {
                            "aps": {
                                "contentAvailable": True,
                            }
                        }
                    }
                }
            }

            headers = {
                "Authorization": f"Bearer {access_token}",
                "Content-Type": "application/json"
            }

            response = requests.post(
                self.endpoint,
                headers=headers,
                json=message,
                timeout=10
            )

            if response.status_code == 200:
                print(f"  ✅ Push silenciosa enviada com sucesso")
                return True, None
            else:
                error_msg = f"Erro {response.status_code}: {response.text}"
                print(f"  ❌ Falha ao enviar push silenciosa: {error_msg}")
                return False, error_msg

        except Exception as e:
            error_msg = f"Exceção: {str(e)}"
            print(f"  ❌ Erro: {error_msg}")
            return False, error_msg


if __name__ == "__main__":
    # Teste básico
    import sys
    
    if len(sys.argv) < 5:
        print("Uso: python fcm_sender.py <service_account.json> <project_id> <token> <title> <body>")
        sys.exit(1)
    
    service_account_path = sys.argv[1]
    project_id = sys.argv[2]
    token = sys.argv[3]
    title = sys.argv[4]
    body = sys.argv[5] if len(sys.argv) > 5 else "Teste de notificação"
    
    sender = FCMSender(service_account_path, project_id)
    success, error = sender.send_to_token(token, title, body)
    
    if success:
        print("\n✅ Teste concluído com sucesso!")
    else:
        print(f"\n❌ Teste falhou: {error}")
