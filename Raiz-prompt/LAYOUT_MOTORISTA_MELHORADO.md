# 🎨 LAYOUT MELHORADO DO CARD DE MOTORISTA

## 📐 Estrutura em 2 Linhas

Cada motorista agora tem um card limpo e organizado com **duas linhas** de informação:

### **LINHA 1 (Principal)** 
Identificação e Status do Motorista

### **LINHA 2 (Detalhes)** 
Informações Operacionais e Ação

---

## 🖼️ Visualização do Layout

```
┌─────────────────────────────────────────────────────────────┐
│  LINHA 1                                                     │
│  👤 João Silva  [FROTA]  [● Carregando]   ✏️ Editar  ❌    │
│                                                               │
│  LINHA 2                                                     │
│  📍 V02  •  🗺️ Rota M-12  •  📦 3 sacas   [Concluir]       │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 Detalhamento dos Componentes

### **LINHA 1 - Identificação e Status**

#### 1. 👤 **Nome do Motorista**
- Texto em **branco bold**
- Tamanho: `bodyLarge`
- Overflow com elipse se muito longo

#### 2. 🏷️ **Badge de Modalidade**
- **FROTA**: Azul Neon (`#3B82F6`)
- **UTILITÁRIO**: Ciano (`#00BCD4`)
- **PASSEIO**: Laranja Neon (`#FB923C`)
- **DEDICADO**: Roxo Neon (`#A855F7`)
- Fundo com 20% de transparência
- Padding: 6dp horizontal, 2dp vertical

#### 3. 🔴 **Badge de Status** (com indicador visual)
- **Bolinha colorida** (8dp) + Texto
- Cores por status:
  - 🟢 **CARREGANDO**: Verde Neon
  - ✅ **CONCLUÍDO**: Verde Esmeralda (`#10B981`)
  - 🔵 **A CAMINHO**: Azul Neon
  - 🟣 **ESTACIONAMENTO**: Roxo Neon
  - 🟠 **PRÓXIMO**: Laranja (`#FF8C00`)
  - ⚪ **AGUARDANDO**: Cinza
- Fundo com 20% de transparência

#### 4. ✏️ **Botão Editar**
- Ícone azul neon
- Tamanho: 36dp
- Ação: Abre dialog para editar vaga, rota, sacas

#### 5. ❌ **Botão Excluir/Resetar**
- **Vermelho** (`#EF4444`) - Excluir motorista
- **Cinza** - Resetar status (quando concluído)
- Tamanho: 36dp

---

### **LINHA 2 - Detalhes Operacionais**

#### 1. 📍 **Vaga**
- Ícone de localização verde
- Texto: "V02" (sempre 2 dígitos)
- Cor: Branco bold

#### 2. 🗺️ **Rota** (Badge)
- Badge azul com fundo 15% transparente
- Ícone de localização + código da rota
- Ex: "M-12", "P-03", "T-15"
- Se vazio: "Sem rota" em cinza

#### 3. 📦 **Sacas** (Opcional - só aparece se houver)
- Badge laranja com fundo 15% transparente
- Emoji 📦 + quantidade
- Ex: "3 sacas" ou "1 saca"

#### 4. 🔔 **Botão de Ação Contextual**

**Varia conforme o status:**

| Status | Botão | Cor | Ação |
|--------|-------|-----|------|
| ✅ **CONCLUÍDO** | Badge "Concluído" | Verde Esmeralda | Apenas visual |
| 🟢 **CARREGANDO** | "Concluir" | Verde Neon (cheio) | Marca como concluído |
| 🟣 **ESTACIONAMENTO** | "Chamar p/ Vaga" | Verde Neon (cheio) | Chama para carregamento |
| 🔵 **A CAMINHO / PRÓXIMO** | "Chamar p/ Estac." | Roxo Neon (cheio) | Direciona ao estacionamento |
| ⚪ **AGUARDANDO** | "Chamar" | Azul Neon (outline) | Ação genérica |

---

## 🎨 Exemplo Visual Completo

### Motorista Carregando
```
┌─────────────────────────────────────────────────────────────┐
│  João Silva  [FROTA]  [● Carregando]   ✏️  ❌               │
│  📍 V02  •  🗺️ M-12  •  📦 3 sacas    [✓ Concluir]         │
└─────────────────────────────────────────────────────────────┘
```

### Motorista no Estacionamento
```
┌─────────────────────────────────────────────────────────────┐
│  Maria Santos  [UTILITÁRIO]  [● Estacionado]   ✏️  ❌       │
│  📍 V15  •  🗺️ C-08                [📞 Chamar p/ Vaga]     │
└─────────────────────────────────────────────────────────────┘
```

### Motorista A Caminho
```
┌─────────────────────────────────────────────────────────────┐
│  Carlos Dias  [PASSEIO]  [● A Caminho]   ✏️  ❌             │
│  📍 V07  •  🗺️ P-03                [📞 Chamar p/ Estac.]   │
└─────────────────────────────────────────────────────────────┘
```

### Motorista Dedicado Concluído
```
┌─────────────────────────────────────────────────────────────┐
│  Pedro Costa  [DEDICADO]  [● Concluído]   ✏️  🔄            │
│  📍 V47  •  🗺️ R-05                    [✓ Concluído]       │
└─────────────────────────────────────────────────────────────┘
```

---

## 💡 Vantagens do Novo Layout

### ✅ **Organização Visual**
- Informações agrupadas logicamente
- Hierarquia clara (nome > status > ação)
- Fácil escaneamento visual

### ✅ **Espaço Otimizado**
- 2 linhas em vez de 1 linha comprimida
- Cada elemento tem espaço adequado
- Não parece apertado

### ✅ **Ações Contextuais**
- Botões mudam conforme o status
- Texto claro da ação ("Chamar p/ Vaga" vs "Chamar p/ Estac.")
- Cores indicam urgência/tipo de ação

### ✅ **Feedback Visual Imediato**
- Bolinha de status colorida
- Badges bem definidos
- Ícones descritivos

### ✅ **Mobile-Friendly**
- Botões com tamanho adequado (38dp altura)
- Espaçamento confortável
- Touch targets suficientes

---

## 🔧 Responsividade

O layout se adapta automaticamente:

1. **Nome longo**: Trunca com "..." para não quebrar layout
2. **Sem rota**: Mostra "Sem rota" em cinza
3. **Sem sacas**: O elemento não aparece (economiza espaço)
4. **Status concluído**: Botão muda para badge informativo

---

## 🎯 Fluxo de Ação Típico

```
AGUARDANDO
    ↓ [Chamar]
A CAMINHO
    ↓ [Chamar p/ Estac.]
ESTACIONAMENTO
    ↓ [Chamar p/ Vaga]
CARREGANDO
    ↓ [Concluir]
CONCLUÍDO
    ↓ [Resetar] (se necessário)
```

---

## 📱 Preview em Diferentes Estados

### Estado Normal (com todos os elementos)
```
┌───────────────────────────────────────────────────┐
│ 👤 Nome    [MOD]  [● Status]   ✏️  ❌            │
│ 📍 Vaga • 🗺️ Rota • 📦 Sacas    [Ação]          │
└───────────────────────────────────────────────────┘
```

### Estado Mínimo (sem sacas)
```
┌───────────────────────────────────────────────────┐
│ 👤 Nome    [MOD]  [● Status]   ✏️  ❌            │
│ 📍 Vaga • 🗺️ Rota              [Ação]            │
└───────────────────────────────────────────────────┘
```

### Estado Concluído
```
┌───────────────────────────────────────────────────┐
│ 👤 Nome    [MOD]  [● Concluído]   ✏️  🔄         │
│ 📍 Vaga • 🗺️ Rota         [✓ Concluído]          │
└───────────────────────────────────────────────────┘
```

---

## 🚀 Implementação Técnica

O layout utiliza:
- `Column` principal com 2 linhas
- `Row` para elementos horizontais
- `Surface` para badges com bordas arredondadas
- `Box` com `CircleShape` para bolinha de status
- Espaçamento de 12dp entre linhas
- Padding de 12dp no card

**Cores dinâmicas** baseadas em:
- Modalidade do motorista
- Estado atual do status
- Tipo de ação disponível

---

**Última atualização:** Novembro 2024  
**Versão:** 2.0 - Layout em 2 Linhas

