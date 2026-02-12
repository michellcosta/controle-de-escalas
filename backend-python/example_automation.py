"""
example_automation.py

Exemplo de como automatizar o envio de notificações.
Este script monitora mudanças no Firestore e envia notificações automaticamente.

⚠️ NOTA: Este é apenas um exemplo. Para produção, considere usar:
- Cloud Functions (se billing estiver ativado)
- Webhooks
- API REST simples
"""

import time
import json
from firestore_reader import FirestoreReader
from fcm_sender import FCMSender
from google.cloud import firestore


class NotificationAutomation:
    """Classe para automatizar envio de notificações baseado em mudanças no Firestore"""
    
    def __init__(self, service_account_path: str, base_id: str):
        """
        Inicializa a automação
        
        Args:
            service_account_path: Caminho para Service Account JSON
            base_id: ID da base para monitorar
        """
        self.reader = FirestoreReader(service_account_path)
        self.sender = FCMSender(service_account_path)
        self.base_id = base_id
        self.db = self.reader.db
        
        print(f"✅ Automação inicializada para base: {base_id}")
    
    def monitor_status_changes(self, callback_interval: int = 30):
        """
        Monitora mudanças no status dos motoristas e envia notificações
        
        Args:
            callback_interval: Intervalo em segundos para verificar mudanças
        """
        print(f"\n🔍 Iniciando monitoramento (verificando a cada {callback_interval}s)...")
        print("   Pressione Ctrl+C para parar\n")
        
        # Últimos status conhecidos (para detectar mudanças)
        last_statuses = {}
        
        try:
            while True:
                # Buscar status atual de todos os motoristas
                status_ref = self.db.collection('bases').document(self.base_id).collection('status_motoristas')
                current_statuses = {}
                
                for doc in status_ref.stream():
                    data = doc.to_dict()
                    motorista_id = doc.id
                    estado = data.get('estado', '')
                    mensagem = data.get('mensagem', '')
                    
                    current_statuses[motorista_id] = {
                        'estado': estado,
                        'mensagem': mensagem
                    }
                    
                    # Verificar se houve mudança
                    if motorista_id in last_statuses:
                        last_status = last_statuses[motorista_id]
                        
                        # Detectar mudança de status ou mensagem de escalação
                        status_mudou = last_status['estado'] != estado
                        mensagem_escalacao = 'escalado' in mensagem.lower() or 'siga para o galpão' in mensagem.lower()
                        
                        if status_mudou or (estado == 'A_CAMINHO' and mensagem_escalacao):
                            print(f"\n🔄 Mudança detectada para motorista {motorista_id}:")
                            print(f"   Status: {last_status['estado']} → {estado}")
                            
                            # Buscar token FCM do motorista
                            motorista_ref = self.db.collection('bases').document(self.base_id)\
                                .collection('motoristas').document(motorista_id)
                            motorista_doc = motorista_ref.get()
                            
                            if motorista_doc.exists:
                                motorista_data = motorista_doc.to_dict()
                                fcm_token = motorista_data.get('fcmToken')
                                
                                if fcm_token:
                                    # Determinar título e corpo da notificação
                                    if mensagem_escalacao:
                                        title = "🚛 Você foi escalado!"
                                        body = mensagem if mensagem else "Você está escalado! Siga para o galpão."
                                    elif estado == 'CARREGANDO':
                                        vaga = data.get('vagaAtual', 'N/A')
                                        rota = data.get('rotaAtual', 'N/A')
                                        title = f"🚚 Suba para a vaga {vaga}"
                                        body = f"Rota: {rota}" if rota else mensagem
                                    else:
                                        title = "Status Atualizado"
                                        body = mensagem if mensagem else f"Status: {estado}"
                                    
                                    # Enviar notificação
                                    success, error = self.sender.send_to_token(
                                        token=fcm_token,
                                        title=title,
                                        body=body,
                                        data={
                                            'tipo': 'status_update',
                                            'status': estado,
                                            'motorista_id': motorista_id
                                        }
                                    )
                                    
                                    if success:
                                        print(f"   ✅ Notificação enviada")
                                    else:
                                        print(f"   ❌ Falha: {error}")
                                else:
                                    print(f"   ⚠️ Motorista não possui fcmToken")
                    
                    # Atualizar último status conhecido
                    last_statuses[motorista_id] = {
                        'estado': estado,
                        'mensagem': mensagem
                    }
                
                # Aguardar antes da próxima verificação
                time.sleep(callback_interval)
        
        except KeyboardInterrupt:
            print("\n\n⏹️  Monitoramento interrompido pelo usuário")
        except Exception as e:
            print(f"\n❌ Erro no monitoramento: {e}")
            import traceback
            traceback.print_exc()
    
    def send_notification_to_all(self, title: str, body: str, data: dict = None):
        """
        Envia notificação para todos os motoristas da base
        
        Args:
            title: Título da notificação
            body: Corpo da notificação
            data: Dados adicionais (opcional)
        """
        print(f"\n📤 Enviando notificação para todos os motoristas da base {self.base_id}...")
        
        tokens = self.reader.get_motoristas_tokens(self.base_id)
        
        if not tokens:
            print("⚠️  Nenhum token encontrado")
            return
        
        resultado = self.sender.send_to_multiple_tokens(
            tokens=tokens,
            title=title,
            body=body,
            data=data
        )
        
        print(f"\n📊 Resultado: {resultado['sucessos']} sucessos, {resultado['falhas']} falhas")


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) < 3:
        print("Uso: python example_automation.py <service_account.json> <base_id> [--monitor]")
        print("\nExemplos:")
        print("  # Enviar notificação manual")
        print("  python example_automation.py service-account.json xvtFbdOurhdNKVY08rDw")
        print("\n  # Monitorar mudanças automaticamente")
        print("  python example_automation.py service-account.json xvtFbdOurhdNKVY08rDw --monitor")
        sys.exit(1)
    
    service_account_path = sys.argv[1]
    base_id = sys.argv[2]
    monitor_mode = '--monitor' in sys.argv
    
    automation = NotificationAutomation(service_account_path, base_id)
    
    if monitor_mode:
        # Modo monitoramento contínuo
        automation.monitor_status_changes(callback_interval=30)
    else:
        # Modo envio manual (exemplo)
        automation.send_notification_to_all(
            title="🚛 Você foi escalado!",
            body="Você está escalado! Siga para o galpão e aguarde instruções.",
            data={"tipo": "escalacao"}
        )
