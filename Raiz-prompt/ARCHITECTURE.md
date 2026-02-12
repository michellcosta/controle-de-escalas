
# ARCHITECTURE.md

## Visão Geral

O app tem dois perfis principais:
- Motorista
- Admin / Ajudante

Existe também o Superadmin (só você), mas isso é só uma flag especial no login que permite aprovar novas bases.

## Conceitos principais

### Base
Ex.: "DELUNA - Guarulhos Manhã"
- Campos importantes:
  - nomeTransportadora ("DELUNA")
  - nomeBase ("Guarulhos Manhã")
  - corTema (ex.: "#16A34A")
  - statusAprovacao: "pendente" | "ativa" | "rejeitada"
  - localizacao.galpao = { lat, lng, raioM }
  - localizacao.estacionamento = { lat, lng, raioM }

Cada base tem seus próprios:
- admins
- ajudantes
- motoristas
- turnos AM/PM do dia
- ondas
- status ao vivo
- PDFs de rota

Uma base NÃO enxerga outra base.

---

### Usuário
Representa motorista / ajudante / admin daquela base.

Campos:
- nome
- telefone (login)
- pinHash ou pin4 (no MVP pode ser texto simples, depois hash)
- papel: "motorista" | "ajudante" | "admin"
- ativo: true/false
- baseId (a qual base ele pertence)
- superadmin: true/false (só você teria isso = aprova base nova)

Regra:
- motorista não consegue cadastrar ninguém
- ajudante e admin conseguem cadastrar motorista/ajudante/admin
- só ajudante/admin conseguem redefinir PIN do motorista
- motorista NÃO redefine PIN sozinho no MVP

---

### Escala do Dia
Não tem histórico longo. É só pro dia atual. Contém:
- Turno AM
- Turno PM

Cada turno (AM e PM) tem uma lista ORDENADA de "ondas".
A ordem de criação define o rótulo:
- PRIMEIRA ONDA
- SEGUNDA ONDA
- TERCEIRA ONDA
...

Cada onda tem:
- horarioPlanejado (string tipo "06:00", "12:30"... livre)
- listaDeMotoristas: array de itens:
  - motoristaId
  - nomeMotorista (snapshot do momento)
  - vagaPlanejada (ex. "02")
  - rotaCodigo (ex. "M12", "P03")
  - pdfStoragePath (rota em PDF daquele motorista)
  - dataDoDia (ex.: "2025-10-27")

OBS:
- O admin/ajudante pode abrir uma onda, trocar motorista, trocar vaga, trocar rota, trocar PDF.
- O motorista só vê a dele.

---

### Status Ao Vivo do Motorista
Documento que diz o estado ATUAL do motorista na operação.

Pro motorista:
- A CAMINHO
- CHEGUEI (dentro raio galpão)
- ESTACIONAMENTO (dentro raio estacionamento)
- CARREGANDO (admin chamou)
- CONCLUÍDO (terminou o ciclo hoje)

Também guarda:
- vagaAtualChamado (a vaga real que você mandou ele subir agora, ex.: "02")
- rotaAtualChamado (pra mandar na notificação)
- marcadoParaConclusao (bool)
- passouEstacionamentoDepoisDeCarregar (bool)
- timestamps básicos

Fluxo principal:
1. A CAMINHO  → CHEGUEI (GPS <100m do galpão)
2. CHEGUEI    → ESTACIONAMENTO (GPS <50m estacionamento)
   (opcional; pode pular)
3. (de CHEGUEI ou ESTACIONAMENTO)  
   Admin toca "Chamar / Carregando", define vagaAtualChamado
   → CARREGANDO
4. Depois que ele terminou e saiu:
   → CONCLUÍDO

Esse fluxo pode ser "forçado" manualmente pelo admin pra corrigir.

---

### Notificação (Chamar / Carregando)
Quando admin/ajudante toca "Chamar / Carregando":

- Abre pop-up:
  - Vaga agora (pré-carregada da vagaPlanejada da onda, mas editável)
  - Rota (pré-carregada)
- Ao confirmar:
  - statusMotorista.estado = "CARREGANDO"
  - statusMotorista.vagaAtualChamado = "02" (por ex)
  - statusMotorista.rotaAtualChamado = "M12"
  - Disparar push ao motorista: "Subir agora para a vaga 02"

---

## Telas / Fluxos

### LoginScreen
- Telefone
- PIN (4 dígitos)
- [Entrar]
- Se for superadmin, aparece também um botão para "Gerenciar Bases Pendentes"

### DriverHomeScreen
Bloco "Sua Escala de Hoje":
- turno (AM/PM)
- nome da onda (PRIMEIRA ONDA, etc.)
- horário planejado
- vaga planejada
- rota
- botão "Ver rota (PDF)" (abre PDF no app)

Bloco "Status agora":
- A CAMINHO / CHEGUEI / ESTACIONAMENTO / CARREGANDO / CONCLUÍDO
- Mensagem de instrução
- Se CARREGANDO, mostrar "Subir agora para a vaga 02"

### AdminPanelScreen
Lista de TODOS os motoristas da base, cada card mostra:
- Nome + Telefone
- Status atual
- Onda / horário planejado / vaga planejada / rota planejada
- [Abrir rota (PDF)]
- [Chamar / Carregando]
- [Concluído]

Do topo do painel o admin consegue ir para:
- Escala do Dia / Ondas (ScaleScreen)
- Localização da Base / GPS (LocationConfigScreen)
- Cadastro de Pessoa

### ScaleScreen (Escala do Dia)
Topo: [ AM | PM ]

Mostra, por turno:
- PRIMEIRA ONDA — 06:00
   - Michel — Vaga 01 — Rota M07 — [PDF ✔]
   - Robson — Vaga 02 — Rota M12 — [PDF ✔]
   [Adicionar Motorista nesta Onda]
   [Editar Onda] [Excluir Onda]

- SEGUNDA ONDA — 06:20
   - ...

[+ Adicionar Onda Neste Turno]

IMPORTANTE:
- O nome da onda ("PRIMEIRA ONDA", "SEGUNDA ONDA", ...) NÃO é digitado.
- O sistema gera pela ordem de criação naquele turno.

Cada linha de motorista dentro da onda tem:
- Selecionar motorista
- Vaga planejada
- Rota
- Upload/Troca de PDF
- Visualizar PDF pra conferir antes de salvar

### LocationConfigScreen
Mostra 2 mapas:
- Pino do GALPÃO (raio padrão ~100m)
  - Botão "Usar meu local agora"
  - Campo de raio editável (padrão 100)
  - [Salvar Galpão]

- Pino do ESTACIONAMENTO (raio padrão ~50m)
  - Botão "Usar meu local agora"
  - Campo de raio editável (padrão 50)
  - [Salvar Estacionamento]
  - Botão "Não usar estacionamento separado" (para bases sem pátio externo)

Essas informações alimentam a automação de status GPS.

---

## Armazenamento e Limpeza Diária

- Escala do dia (AM/PM/ondas) e PDFs da rota valem somente no dia corrente.
- "Sua Escala de Hoje" e "Status agora" só refletem HOJE.
- À meia-noite (ou fim do turno), limpar referências de PDFs e escala de hoje para não acumular histórico.

Isso respeita:
- baixo custo de armazenamento
- privacidade de rota
- sem auditoria retroativa desnecessária

---
