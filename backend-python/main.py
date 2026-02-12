"""
main.py

Arquivo principal que orquestra a leitura de tokens do Firestore
e o envio de notificações push via FCM.
"""

import argparse
import os
import sys
from firestore_reader import FirestoreReader
from fcm_sender import FCMSender


def main():
    """Função principal"""
    parser = argparse.ArgumentParser(
        description="Envia notificações push via FCM para motoristas de uma base",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemplos de uso:

  # Enviar notificação para todos os motoristas de uma base
  python main.py --base-id xvtFbdOurhdNKVY08rDw \\
                 --title "Você foi escalado!" \\
                 --body "Siga para o galpão e aguarde instruções."

  # Usar Service Account de arquivo
  python main.py --base-id xvtFbdOurhdNKVY08rDw \\
                 --service-account service-account.json \\
                 --title "Teste" \\
                 --body "Mensagem de teste"

  # Adicionar dados customizados
  python main.py --base-id xvtFbdOurhdNKVY08rDw \\
                 --title "Status Atualizado" \\
                 --body "Seu status mudou" \\
                 --data '{"tipo":"escalacao","status":"A_CAMINHO"}'
        """
    )
    
    # Argumentos obrigatórios
    parser.add_argument(
        '--base-id',
        required=True,
        help='ID da base no Firestore (ex: xvtFbdOurhdNKVY08rDw)'
    )
    
    parser.add_argument(
        '--title',
        required=True,
        help='Título da notificação'
    )
    
    parser.add_argument(
        '--body',
        required=True,
        help='Corpo da notificação'
    )
    
    # Argumentos opcionais
    parser.add_argument(
        '--service-account',
        help='Caminho para o arquivo JSON do Service Account (ou use variável de ambiente FIREBASE_SERVICE_ACCOUNT_JSON)'
    )
    
    parser.add_argument(
        '--project-id',
        help='ID do projeto Firebase (se não fornecido, será lido do Service Account)'
    )
    
    parser.add_argument(
        '--data',
        help='Dados adicionais em formato JSON (ex: \'{"tipo":"escalacao","status":"A_CAMINHO"}\')'
    )
    
    parser.add_argument(
        '--dry-run',
        action='store_true',
        help='Apenas listar tokens sem enviar notificações'
    )
    
    args = parser.parse_args()
    
    # Validar e parsear dados se fornecidos
    data_dict = None
    if args.data:
        try:
            import json
            data_dict = json.loads(args.data)
        except json.JSONDecodeError as e:
            print(f"❌ Erro ao parsear JSON dos dados: {e}")
            sys.exit(1)
    
    print("=" * 60)
    print("🚀 Backend FCM - Envio de Notificações Push")
    print("=" * 60)
    print(f"\n📋 Configuração:")
    print(f"   Base ID: {args.base_id}")
    print(f"   Título: {args.title}")
    print(f"   Corpo: {args.body}")
    if data_dict:
        print(f"   Dados: {data_dict}")
    if args.dry_run:
        print(f"   ⚠️  MODO DRY-RUN (não enviará notificações)")
    print()
    
    try:
        # 1. Inicializar Firestore Reader
        print("📖 Passo 1: Conectando ao Firestore...")
        reader = FirestoreReader(args.service_account)
        
        # 2. Buscar tokens dos motoristas
        print(f"\n📖 Passo 2: Buscando tokens FCM da base '{args.base_id}'...")
        tokens = reader.get_motoristas_tokens(args.base_id)
        
        if not tokens:
            print(f"\n⚠️  Nenhum token FCM encontrado para a base '{args.base_id}'")
            print("   Verifique se:")
            print("   - O ID da base está correto")
            print("   - Os motoristas possuem fcmToken no Firestore")
            sys.exit(0)
        
        print(f"\n✅ Encontrados {len(tokens)} tokens válidos")
        
        # 3. Se for dry-run, apenas listar
        if args.dry_run:
            print("\n📋 Tokens encontrados (DRY-RUN):")
            for i, token_info in enumerate(tokens, 1):
                print(f"   {i}. Motorista: {token_info.get('nome', 'N/A')} ({token_info.get('motorista_id', 'N/A')})")
                print(f"      Token: {token_info.get('fcmToken', '')[:50]}...")
            print("\n✅ Dry-run concluído. Use sem --dry-run para enviar notificações.")
            sys.exit(0)
        
        # 4. Inicializar FCM Sender
        print("\n📤 Passo 3: Inicializando FCM Sender...")
        sender = FCMSender(args.service_account, args.project_id)
        
        # 5. Enviar notificações
        print("\n📤 Passo 4: Enviando notificações...")
        resultado = sender.send_to_multiple_tokens(
            tokens=tokens,
            title=args.title,
            body=args.body,
            data=data_dict
        )
        
        # 6. Resumo final
        print("\n" + "=" * 60)
        print("📊 RESUMO FINAL")
        print("=" * 60)
        print(f"   ✅ Sucessos: {resultado['sucessos']}")
        print(f"   ❌ Falhas: {resultado['falhas']}")
        print(f"   📱 Total: {len(tokens)}")
        
        if resultado['falhas'] == 0:
            print("\n🎉 Todas as notificações foram enviadas com sucesso!")
        elif resultado['sucessos'] > 0:
            print(f"\n⚠️  {resultado['sucessos']} notificações enviadas, {resultado['falhas']} falharam")
        else:
            print("\n❌ Nenhuma notificação foi enviada com sucesso")
            sys.exit(1)
    
    except FileNotFoundError as e:
        print(f"\n❌ Erro: {e}")
        print("\n💡 Dica: Verifique o caminho do Service Account JSON")
        sys.exit(1)
    
    except ValueError as e:
        print(f"\n❌ Erro: {e}")
        sys.exit(1)
    
    except Exception as e:
        print(f"\n❌ Erro inesperado: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
