
# Controle de Escalas (MVP)

Este é o esqueleto inicial do aplicativo **Controle de Escalas**, pensado para rodar no Android (Kotlin + Jetpack Compose) com backend Firebase (Auth telefone+PIN manual / Firestore / Storage) e pronto para proteger a sua operação de galpão.

## 📋 Documentos

- [Política de Privacidade](https://michellcosta.github.io/politica-privacidade-app/)

Ele já vem organizado com:
- Tela do MOTORISTA (escala dele + status ao vivo)
- Tela do ADMIN/AJUDANTE (painel da base, chamada pra vaga, etc.)
- Tela de criação/edição de ondas (AM / PM)
- Configuração de localização (pino do galpão e estacionamento)
- Modelos de dados (bases, usuários, ondas, status motorista)
- Regras de acesso pensadas pra não vazar informação entre bases

## Fluxo geral

### 1. Criação de Base
- Alguém instala o app e escolhe **Criar Base**.
- Informa: nome da transportadora, nome da base, telefone, PIN.
- Essa base fica `pendente`.
- O superadmin (você) aprova essa base.
- Ao aprovar, essa pessoa vira `admin` dessa base e ganha acesso ao painel da base.

> Cada base é independente (ex: DELUNA Guarulhos, DELUNA Campinas).  
> O pagamento futuro é por base.  
> Não existe "dono" fixo da base – outros admins podem ser criados e podem até remover quem criou.

### 2. Cadastro de Usuários na Base
Admin/ajudante consegue adicionar:
- Motorista
- Ajudante
- Admin

Campos:
- nome (obrigatório)
- telefone (login)
- PIN de 4 dígitos
- papel (motorista/ajudante/admin)
- ativo: true/false

Motorista não cria conta sozinho.

### 3. Escala do Dia (AM / PM / Ondas)
- O admin monta a escala de HOJE.
- Seleciona turno AM ou PM.
- Cria ondas:
  - A primeira onda criada naquele turno vira automaticamente `PRIMEIRA ONDA`,
  - a segunda vira `SEGUNDA ONDA`,
  - etc.
- Cada onda tem:
  - horário planejado (livre)
  - lista de motoristas dessa onda:
    - motorista
    - vaga planejada (01–50)
    - rota (ex: M07, P03…)
    - PDF da rota (upload)
      - admin consegue visualizar antes de salvar
      - pode trocar se estiver errado

IMPORTANTE:
- AM e PM não têm corte fixo tipo "11h".
- PM começa depois que o AM terminar, mesmo que AM tenha estourado até meio-dia.
- Amanhã começa tudo do zero. Não guardamos histórico de dias anteriores.

### 4. Tela do Motorista
Quando o motorista faz login, ele vê DOIS blocos:

#### Bloco "Sua Escala de Hoje"
- Turno (AM ou PM)
- Onda (PRIMEIRA ONDA, SEGUNDA ONDA…)
- Horário previsto daquela onda
- Vaga prevista
- Rota
- Botão "Ver rota (PDF)"

Ele só vê A ESCALA DELE.  
Ele NÃO vê a escala completa do turno nem os outros motoristas.

#### Bloco "Status agora"
Estados possíveis:
- A CAMINHO
- CHEGUEI (dentro do raio do galpão ~100m)
- ESTACIONAMENTO (dentro do raio do estacionamento ~50m)
- CARREGANDO — com vaga atual
- CONCLUÍDO

Esse bloco diz pro motorista o que fazer AGORA.
Ex: "CARREGANDO — Subir agora para a vaga 02 com rota M12".

O status muda assim:
- GPS define A CAMINHO / CHEGUEI / ESTACIONAMENTO automaticamente.
- ADMIN/AJUDANTE aperta "Chamar / Carregando" -> vira CARREGANDO, e manda push.
- Depois que ele termina e sai -> vira CONCLUÍDO
  - pode ser automático (se passou pelo estacionamento e saiu do raio do galpão) ou manual pelo admin.

### 5. Painel do Admin / Ajudante
O admin vê uma lista de todos os motoristas da base (apenas daquela base) com:

- Nome
- Telefone
- Status atual (A CAMINHO, CHEGUEI, ESTACIONAMENTO, CARREGANDO, CONCLUÍDO)
- De qual onda ele veio (AM / PM, QUAL ONDA, horário previsto)
- Vaga prevista
- Rota prevista
- Botão "Abrir rota (PDF)" pra conferir
- Botão **"Chamar / Carregando"**
- Botão **"Concluído"**

#### Botão "Chamar / Carregando"
Ao tocar:
- Abre pop-up com:
  - "Vaga agora:" (pré-preenchida com a vaga planejada, mas você pode mudar ex: de 01 pra 02)
  - "Rota na mensagem:" (pré-preenchida)
- Confirmar -> manda push "Subir agora para a vaga 02", seta status do motorista pra CARREGANDO,
  e atualiza a tela do motorista.

Isso resolve sua necessidade: "estava vaga 01 mas quero que ele suba na vaga 02 agora".

#### Botão "Concluído"
Se o GPS já marcou saída, beleza.  
Se não, o admin pode tocar manualmente e fechar aquele motorista no dia.

### 6. Localização da Base / GPS
Na parte de configuração da Base:
- Admin/ajudante abre "Localização da Base".
- Arrasta pino do **Galpão/Doca** (raio ~100m).
- Arrasta pino do **Estacionamento** (raio ~50m).
- Salva.

Esses 2 pontos mandam nos status CHEGUEI e ESTACIONAMENTO.

Cada base tem o seu próprio pátio.  
Bases diferentes = pinos diferentes.

### 7. Retenção mínima de dados
- Escala AM/PM, ondas, PDFs, status do dia = só valem "HOJE".
- Depois disso, limpar/substituir.
- Sem histórico pesado.
- Isso reduz custo de banco e protege operação.

---

## Estrutura deste projeto

- `README.md` → você está lendo.
- `ARCHITECTURE.md` → fluxos funcionais e modelo de dados.
- `FIREBASE_SETUP.md` → passos de Firebase, Firestore, Storage, permissões de localização.
- `firestore.rules` → início das regras de segurança (cada base só enxerga a si mesma).
- Diretório `app/` → projeto Android (Kotlin + Jetpack Compose).
  - Telas:
    - LoginScreen
    - DriverHomeScreen (motorista)
    - AdminPanelScreen (admin/ajudante)
    - ScaleScreen (Escala do Dia / ondas AM/PM)
    - LocationConfigScreen (configurar galpão/estacionamento)
  - Navegação Jetpack Compose Navigation
  - Modelos de dados em `data/models/Entities.kt`

Você agora tem:
- UI Compose inicial pronta pra rodar e evoluir.
- Os modelos que representam cada parte do seu fluxo.
- Comentários em cada arquivo indicando a lógica esperada.

Próximo passo na prática:
1. Colocar o `google-services.json` real dentro de `app/`.
2. Conectar Firebase Auth (por telefone/PIN gerenciado pela base).
3. Conectar Firestore e Storage.
4. Trocar a FakeRepository por implementação Firebase.

Esse ZIP é o ponto de partida para isso.

