# Política de Privacidade - Controle de Escalas

**Última atualização:** 2025

## 1. Introdução

Esta Política de Privacidade descreve como **Michell Oliveira** ("nós", "nosso" ou "aplicativo") coleta, usa, armazena e protege suas informações quando você utiliza o aplicativo **Controle de Escalas**.

Ao usar nosso aplicativo, você concorda com a coleta e uso de informações de acordo com esta política. Esta política está em conformidade com a **Lei Geral de Proteção de Dados (LGPD - Lei nº 13.709/2018)** e os requisitos da **Google Play Store**.

## 2. Informações que Coletamos

Coletamos os seguintes tipos de informações quando você usa nosso aplicativo:

### 2.1. Informações de Identificação

- **Número de telefone:** Usado para autenticação e identificação no sistema
- **PIN de acesso:** Usado para autenticação local (armazenado de forma segura e criptografada)
- **Nome completo:** Nome do motorista ou usuário cadastrado

### 2.2. Dados de Localização

- **Localização GPS em tempo real:** Coletamos sua localização precisamente (coordenadas de latitude e longitude) para permitir funcionalidades de geofencing (detecção de entrada/saída de áreas específicas como galpões e estacionamentos)
- **Quando coletamos:** A coleta de localização **só ocorre quando o motorista possui um status ativo na escala** (por exemplo: a caminho, chegou, em estacionamento, carregando). Quando não há status definido ou quando o status é marcado como **concluído**, o aplicativo **não monitora** a localização
- **Localização em segundo plano:** O aplicativo solicita permissão para acessar sua localização mesmo quando o aplicativo está em segundo plano, necessário para monitoramento contínuo de geofences e atualização automática de status. O uso da localização em segundo plano cessa automaticamente quando o status do motorista é marcado como **concluído** ou quando **não há status definido**, sem necessidade de ação do usuário

### 2.3. Dados de Uso do Aplicativo

- **Status de trabalho:** Informações sobre seu status atual (ex: no galpão, em estacionamento, a caminho)
- **Dados de escala:** Informações sobre suas escalas de trabalho, turnos, ondas, vagas e rotas atribuídas
- **Histórico de atividades:** Registro de ações realizadas no aplicativo

### 2.4. Dados Técnicos do Dispositivo

- **Token FCM (Firebase Cloud Messaging):** Identificador único do dispositivo para envio de notificações push
- **Informações do dispositivo:** Modelo, versão do sistema operacional Android, identificadores únicos do dispositivo

## 3. Como Usamos Suas Informações

Utilizamos as informações coletadas para os seguintes propósitos:

- **Autenticação e Segurança:** Verificar sua identidade através de telefone e PIN para permitir acesso ao sistema
- **Gestão de Escalas:** Gerenciar e exibir suas escalas de trabalho, turnos, vagas e rotas atribuídas
- **Geofencing e Monitoramento de Localização:** Detectar automaticamente quando você entra ou sai de áreas específicas (galpões, estacionamentos) para atualizar seu status de forma automática e fornecer informações contextuais
- **Notificações Push:** Enviar notificações sobre chamadas para vagas, chegada ao galpão, entrada no estacionamento, conclusão da jornada, alterações em escalas e outras informações relevantes relacionadas ao seu trabalho
- **Melhorias do Aplicativo:** Analisar padrões de uso para melhorar a experiência do usuário e desenvolver novas funcionalidades
- **Comunicação:** Permitir comunicação entre administradores e motoristas através do sistema de notificações

## 4. Compartilhamento de Informações

Suas informações são compartilhadas apenas nas seguintes situações:

### 4.1. Prestadores de Serviços de Terceiros

Utilizamos os seguintes serviços de terceiros que podem ter acesso às suas informações:

- **Google Firebase:** Utilizamos Firebase Authentication, Firestore Database e Firebase Cloud Messaging para autenticação, armazenamento de dados e envio de notificações push. Os dados são armazenados em servidores da Google localizados nos Estados Unidos e em outros países. Consulte a Política de Privacidade da Google: https://policies.google.com/privacy
- **Google Maps API:** Utilizamos o serviço de mapas do Google para exibição de mapas e funcionalidades de geolocalização. Consulte a Política de Privacidade do Google Maps: https://policies.google.com/privacy

### 4.2. Administradores da Base

Os administradores da base em que você está cadastrado têm acesso às suas informações de perfil, status de trabalho, escalas e localização atual (quando relevante para a operação), exclusivamente para fins de gestão e coordenação de operações.

### 4.3. Transferências e Requisições Legais

Podemos divulgar suas informações se exigido por lei, ordem judicial, processo legal ou solicitação governamental, ou para proteger nossos direitos, propriedade ou segurança, bem como dos usuários.

## 5. Armazenamento e Segurança dos Dados

- **Armazenamento:** Seus dados são armazenados de forma segura em servidores do Firebase (Google Cloud Platform), utilizando criptografia em trânsito e em repouso
- **Localização dos Dados:** Os dados podem ser armazenados em servidores localizados nos Estados Unidos e em outros países
- **Segurança:** Implementamos medidas de segurança técnicas e organizacionais apropriadas para proteger seus dados contra acesso não autorizado, alteração, divulgação ou destruição
- **Retenção:** Mantemos seus dados enquanto você mantiver uma conta ativa no aplicativo ou conforme necessário para cumprir obrigações legais. Você pode solicitar a exclusão de seus dados através do contato fornecido abaixo

## 6. Seus Direitos (LGPD)

De acordo com a Lei Geral de Proteção de Dados (LGPD), você tem os seguintes direitos:

- **Confirmação e Acesso:** Solicitar confirmação sobre o tratamento de seus dados e acessar seus dados pessoais
- **Correção:** Solicitar a correção de dados incompletos, inexatos ou desatualizados
- **Anonimização, Bloqueio ou Eliminação:** Solicitar a anonimização, bloqueio ou eliminação de dados desnecessários, excessivos ou tratados em desconformidade com a LGPD
- **Portabilidade:** Solicitar a portabilidade de seus dados para outro fornecedor de serviço ou produto
- **Revogação do Consentimento:** Retirar seu consentimento a qualquer momento
- **Informação sobre Compartilhamento:** Obter informações sobre entidades públicas e privadas com as quais compartilhamos dados
- **Informação sobre Possibilidade de Não Consentir:** Ser informado sobre a possibilidade de não fornecer consentimento e sobre as consequências da negativa

Para exercer qualquer um desses direitos, entre em contato conosco através do e-mail: **michell.costa@live.com**

## 7. Permissões do Aplicativo

O aplicativo solicita as seguintes permissões do Android:

- **Localização Precisa (Foreground e Background):** Necessária para funcionalidades de geofencing e detecção automática de entrada/saída de áreas. O uso em segundo plano cessa automaticamente quando o status do motorista é marcado como concluído ou quando não há status definido. Você pode revogar essa permissão a qualquer momento nas configurações do dispositivo, mas isso pode afetar o funcionamento de algumas funcionalidades do aplicativo
- **Notificações:** Para enviar notificações push sobre chamados, alterações de escala e outras informações importantes
- **Internet e Rede:** Para sincronizar dados com os servidores e receber atualizações em tempo real
- **Câmera (opcional):** Para funcionalidades de scanner de QR Code/Código de Barras, se disponível

## 8. Cookies e Tecnologias Similares

O aplicativo utiliza tecnologias similares a cookies (como tokens de autenticação e identificadores de dispositivo) para manter sua sessão ativa e fornecer funcionalidades personalizadas. Essas informações são armazenadas localmente no seu dispositivo e não são compartilhadas com terceiros sem seu consentimento.

## 9. Menores de Idade

Este aplicativo não é destinado a menores de 18 anos. Não coletamos intencionalmente informações pessoais de menores de idade. Se tomarmos conhecimento de que coletamos informações de um menor sem consentimento dos pais ou responsáveis, tomaremos medidas para excluir essas informações de nossos servidores.

## 10. Alterações nesta Política de Privacidade

Podemos atualizar esta Política de Privacidade periodicamente. Notificaremos você sobre quaisquer alterações publicando a nova Política de Privacidade nesta página e atualizando a data de "Última atualização" no topo desta política. Recomendamos que você revise esta Política de Privacidade periodicamente para estar informado sobre como protegemos suas informações.

## 11. Consentimento

Ao usar nosso aplicativo, você consente com nossa Política de Privacidade e concorda com seus termos. Se você não concordar com esta política, por favor, não use nosso aplicativo.

## 12. Contato

Se você tiver dúvidas, preocupações ou solicitações relacionadas a esta Política de Privacidade ou ao tratamento de seus dados pessoais, entre em contato conosco:

- **Responsável pelo Tratamento de Dados:** Michell Oliveira
- **E-mail:** michell.costa@live.com

Faremos o nosso melhor para responder suas solicitações no prazo estabelecido pela LGPD.

---

**Última atualização:** 2025  
**Aplicativo:** Controle de Escalas  
**Desenvolvedor:** Michell Oliveira
