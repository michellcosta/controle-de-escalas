"""
Script auxiliar para preparar o JSON do Service Account
para colar no Railway/Render.

Uso:
    python preparar_json.py service-account-key.json
"""

import sys
import json

def main():
    if len(sys.argv) < 2:
        print("❌ Uso: python preparar_json.py <caminho-do-json>")
        print("\nExemplo:")
        print("  python preparar_json.py service-account-key.json")
        sys.exit(1)
    
    json_path = sys.argv[1]
    
    try:
        # Ler o arquivo JSON
        with open(json_path, 'r', encoding='utf-8') as f:
            json_content = json.load(f)
        
        # Converter de volta para string (formatado)
        json_string = json.dumps(json_content, indent=2)
        
        print("=" * 60)
        print("✅ JSON preparado com sucesso!")
        print("=" * 60)
        print("\n📋 Copie o conteúdo abaixo e cole no Railway/Render:")
        print("=" * 60)
        print(json_string)
        print("=" * 60)
        print("\n💡 Dica: Selecione todo o texto acima (Ctrl+A) e copie (Ctrl+C)")
        print("   Depois cole no campo 'Value' da variável FIREBASE_SERVICE_ACCOUNT_JSON")
        print("=" * 60)
        
    except FileNotFoundError:
        print(f"❌ Arquivo não encontrado: {json_path}")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"❌ Erro ao ler JSON: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Erro: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
