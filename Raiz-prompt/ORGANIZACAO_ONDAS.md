# 🎯 ORGANIZAÇÃO INTELIGENTE DAS ONDAS

## 📋 Visão Geral

O sistema de ondas foi otimizado para organizar motoristas automaticamente de acordo com suas modalidades, facilitando a visualização e gestão operacional.

## 🔄 Hierarquia de Modalidades

Dentro de cada onda, os motoristas são automaticamente ordenados por prioridade:

1. **🚛 FROTA** (Prioridade 1)
   - Motoristas da frota principal
   - Cor do badge: **Azul Neon**

2. **🔧 UTILITÁRIO** (Prioridade 2)
   - Veículos utilitários e de apoio
   - Cor do badge: **Ciano**

3. **🚗 PASSEIO** (Prioridade 3)
   - Veículos de passeio
   - Cor do badge: **Laranja Neon**

4. **🚌 DEDICADO** (Prioridade 4)
   - Serviços dedicados especiais
   - Cor do badge: **Roxo Neon**

## 📐 Estrutura Visual

### Ondas Regulares
```
🚛 ONDAS REGULARES
├─ PRIMEIRA ONDA - 06:20
│  ├─ Motorista 1 (FROTA)
│  ├─ Motorista 2 (FROTA)
│  ├─ Motorista 3 (UTILITÁRIO)
│  ├─ Motorista 4 (PASSEIO)
│  └─ Motorista 5 (DEDICADO) ← Pode estar aqui!
│
└─ SEGUNDA ONDA - 06:40
   ├─ Motorista 6 (FROTA)
   └─ Motorista 7 (PASSEIO)
```

### Seção Dedicado (Separada)
```
─────────────────────────────────
🚌 DEDICADO
├─ PRIMEIRA ONDA - 06:00
│  ├─ Motorista A (DEDICADO)
│  └─ Motorista B (DEDICADO)
│
└─ SEGUNDA ONDA - 07:00
   └─ Motorista C (DEDICADO)
```

## ⚙️ Funcionamento Automático

### 1. Ao Adicionar Motorista
Quando um motorista é adicionado a uma onda:
- O sistema identifica automaticamente sua modalidade
- Insere o motorista na posição correta (ordenado por modalidade)
- Mantém ordenação secundária por vaga (quando aplicável)

### 2. Ao Visualizar
- Ondas regulares aparecem primeiro
- Depois, uma linha divisória separa a seção DEDICADO
- Cada motorista exibe um badge colorido com sua modalidade

### 3. Tipos de Onda
- **NORMAL**: Ondas regulares (Frota, Utilitário, Passeio)
- **DEDICADO**: Ondas exclusivas do serviço dedicado

## 🎨 Cores e Badges

| Modalidade  | Cor Principal | Uso                          |
|-------------|---------------|------------------------------|
| FROTA       | 🔵 Azul Neon  | Frota principal da operação  |
| UTILITÁRIO  | 🔷 Ciano      | Veículos de apoio            |
| PASSEIO     | 🟠 Laranja    | Veículos de passeio          |
| DEDICADO    | 🟣 Roxo Neon  | Serviços dedicados especiais |

## 💡 Casos de Uso

### Caso 1: Motorista Dedicado na Primeira Onda
✅ **Permitido**: Um motorista dedicado pode estar na primeira onda regular E também ter sua própria seção dedicada.

**Exemplo:**
```
PRIMEIRA ONDA - 06:00
├─ João (FROTA)
├─ Maria (FROTA)
└─ Pedro (DEDICADO) ← Aqui primeiro

🚌 DEDICADO
└─ PRIMEIRA ONDA - 06:00
    └─ Pedro (DEDICADO) ← E aqui também!
```

### Caso 2: Mix de Modalidades
✅ **Automático**: Mesmo adicionando motoristas aleatoriamente, a ordem é mantida.

**Adiciona:**
1. Ana (PASSEIO)
2. Carlos (FROTA)
3. Bruno (UTILITÁRIO)

**Resultado automático:**
1. Carlos (FROTA) ← Reordenado para o topo
2. Bruno (UTILITÁRIO) ← Segunda prioridade
3. Ana (PASSEIO) ← Terceira prioridade

## 🔧 Implementação Técnica

### Modelos de Dados
```kotlin
data class OndaItem(
    val motoristaId: String,
    val nome: String,
    val vaga: String,
    val rota: String,
    val modalidade: String = "FROTA" // Campo adicionado
)

data class Onda(
    val nome: String,
    val horario: String,
    val tipo: String = "NORMAL", // NORMAL ou DEDICADO
    val itens: List<OndaItem>
)
```

### Funções de Ordenação
```kotlin
// Prioridade de modalidade
fun getModalidadePrioridade(modalidade: String): Int {
    return when (modalidade) {
        "FROTA" -> 1
        "UTILITARIO" -> 2
        "PASSEIO" -> 3
        "DEDICADO" -> 4
        else -> 5
    }
}

// Ordenar itens por modalidade
fun List<OndaItem>.sortedByModalidade(): List<OndaItem> {
    return this.sortedWith(compareBy(
        { getModalidadePrioridade(it.modalidade) },
        { it.vaga }
    ))
}
```

## 📱 Interface do Usuário

### Indicadores Visuais
1. **Seções com Títulos**
   - "🚛 ONDAS REGULARES" (verde)
   - "🚌 DEDICADO" (roxo)

2. **Badges de Modalidade**
   - Aparece embaixo do nome do motorista
   - Cor correspondente à modalidade

3. **Linha Divisória**
   - Separa visualmente ondas regulares de dedicadas

### Exemplo Visual
```
┌─────────────────────────────────────┐
│ 🚛 ONDAS REGULARES                  │
│                                     │
│ ┌─ PRIMEIRA ONDA - 06:20 ──────┐   │
│ │ V01  João Silva              │   │
│ │      FROTA                   │   │
│ │ V02  Maria Santos            │   │
│ │      FROTA                   │   │
│ │ V03  Carlos Dias             │   │
│ │      UTILITÁRIO              │   │
│ └─────────────────────────────────┘ │
│                                     │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│                                     │
│ 🚌 DEDICADO                         │
│                                     │
│ ┌─ PRIMEIRA ONDA - 06:00 ──────┐   │
│ │ V47  Pedro Costa             │   │
│ │      DEDICADO                │   │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

## ✅ Benefícios

1. **Organização Clara**: Fácil identificar qual tipo de veículo está em cada onda
2. **Gestão Eficiente**: Prioriza automaticamente frota principal
3. **Flexibilidade**: Permite dedicados em ondas regulares quando necessário
4. **Visual Intuitivo**: Cores e badges facilitam identificação rápida
5. **Automático**: Não precisa ordenar manualmente

## 🔄 Sincronização

Toda a ordenação é:
- ✅ Salva no Firestore
- ✅ Mantida ao recarregar
- ✅ Sincronizada em tempo real
- ✅ Aplicada automaticamente ao adicionar motoristas

---

**Última atualização:** Novembro 2024
**Versão:** 1.0

