import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as bcrypt from "bcryptjs";

admin.initializeApp();
const db = admin.firestore();

/**
 * Login com telefone e PIN
 * 
 * Retorna um token customizado do Firebase Auth para autenticar o usuário
 */
export const loginWithPhonePin = functions.region("southamerica-east1")
  .https.onCall(async (data) => {
    const { phone, baseId, pin } = data || {};
    
    if (!phone || !pin) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "phone e pin são obrigatórios"
      );
    }
    
    console.log(`🔐 Login solicitado: telefone=${phone}`);
    
    let userDoc: FirebaseFirestore.QueryDocumentSnapshot | null = null;
    let foundBaseId = baseId;
    
    // Se baseId não foi fornecido, buscar em todas as bases (Collection Group)
    if (!foundBaseId) {
      console.log("🔍 BaseId não fornecido, buscando globalmente...");
      const query = db.collectionGroup("usuarios")
        .where("telefone", "==", phone)
        .where("ativo", "==", true)
        .limit(1);
        
      const snap = await query.get();
      
      if (snap.empty) {
        console.log(`❌ Usuário não encontrado globalmente: telefone=${phone}`);
        throw new functions.https.HttpsError(
          "not-found",
          "Usuário não encontrado"
        );
      }
      
      userDoc = snap.docs[0];
      // Recuperar o ID da base a partir do caminho do documento: bases/{baseId}/usuarios/{userId}
      // parent = usuarios, parent.parent = bases/{baseId}
      const baseDocRef = userDoc.ref.parent.parent;
      if (baseDocRef) {
        foundBaseId = baseDocRef.id;
        console.log(`✅ Usuário encontrado na base: ${foundBaseId}`);
      } else {
        throw new functions.https.HttpsError(
          "internal",
          "Erro ao identificar base do usuário"
        );
      }
    } else {
      // Busca direta na base específica
      const usuariosRef = db.collection("bases").doc(foundBaseId).collection("usuarios");
      const snap = await usuariosRef
        .where("telefone", "==", phone)
        .where("ativo", "==", true)
        .limit(1)
        .get();
      
      if (snap.empty) {
        console.log(`❌ Usuário não encontrado na base ${foundBaseId}: telefone=${phone}`);
        throw new functions.https.HttpsError(
          "not-found",
          "Usuário não encontrado"
        );
      }
      userDoc = snap.docs[0];
    }
    
    if (!userDoc) {
       throw new functions.https.HttpsError("not-found", "Usuário não encontrado");
    }

    const user: any = userDoc.data();
    
    if (!user.ativo) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Usuário inativo"
      );
    }
    
    // Comparar PIN usando bcrypt
    const ok = await bcrypt.compare(String(pin), String(user.pinHash || ""));
    
    if (!ok) {
      console.log(`❌ PIN inválido para usuário: ${user.nome}`);
      throw new functions.https.HttpsError(
        "unauthenticated",
        "PIN inválido"
      );
    }
    
    // Criar token customizado do Firebase Auth
    const uid = userDoc.id;
    const token = await admin.auth().createCustomToken(uid, {
      baseId: foundBaseId,
      papel: user.papel || "motorista",
      nome: user.nome || "",
      telefone: user.telefone || ""
    });
    
    console.log(`✅ Login bem-sucedido: ${user.nome} (${user.papel})`);
    
    return { token, uid, baseId: foundBaseId };
  });

/**
 * Admin define PIN para usuário
 * 
 * Permite que admin/ajudante defina ou altere o PIN de qualquer usuário
 */
export const adminSetPin = functions.region("southamerica-east1")
  .https.onCall(async (data, context) => {
    const { targetUid, baseId, newPin } = data || {};
    
    // Verificar autenticação
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Login necessário"
      );
    }
    
    // Verificar permissões
    const callerRef = db.doc(`bases/${baseId}/usuarios/${context.auth.uid}`);
    const callerSnap = await callerRef.get();
    
    if (!callerSnap.exists) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Usuário não encontrado"
      );
    }
    
    const caller: any = callerSnap.data();
    
    if (!caller.ativo || !["admin", "superadmin", "ajudante"].includes(caller.papel)) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Apenas admin/ajudante podem alterar PINs"
      );
    }
    
    if (!targetUid || !newPin) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "targetUid e newPin são obrigatórios"
      );
    }
    
    // Hash do novo PIN usando bcrypt
    const pinHash = await bcrypt.hash(String(newPin), 10);
    
    // Atualizar PIN
    await db.doc(`bases/${baseId}/usuarios/${targetUid}`).update({ pinHash });
    
    console.log(`🔑 PIN atualizado para usuário ${targetUid} por ${caller.nome}`);
    
    return { ok: true };
  });

/**
 * ========================================
 * SISTEMA DE NOTIFICAÇÕES PUSH
 * ========================================
 */

/**
 * Helper: Enviar notificação push para um usuário
 */
async function sendPushNotification(
  userId: string,
  baseId: string,
  title: string,
  body: string,
  data?: { [key: string]: string }
) {
  try {
    // Tentar buscar em motoristas primeiro (sistema atual)
    let userRef = db.doc(`bases/${baseId}/motoristas/${userId}`);
    let userSnap = await userRef.get();
    
    // Se não encontrar em motoristas, tentar em usuarios (legado)
    if (!userSnap.exists) {
      userRef = db.doc(`bases/${baseId}/usuarios/${userId}`);
      userSnap = await userRef.get();
    }
    
    if (!userSnap.exists) {
      console.log(`⚠️ Usuário ${userId} não encontrado em motoristas nem usuarios`);
      return;
    }
    
    const user: any = userSnap.data();
    const fcmToken = user.fcmToken;
    
    if (!fcmToken) {
      console.log(`⚠️ Usuário ${user.nome || userId} não tem FCM token`);
      return;
    }
    
    const message = {
      notification: {
        title,
        body,
      },
      data: {
        ...(data || {}),
        click_action: "FLUTTER_NOTIFICATION_CLICK", // Para abrir o app quando clicar
      },
      token: fcmToken,
      android: {
        priority: "high" as const,
        notification: {
          channelId: "controle_escalas_channel",
          sound: "default",
          priority: "high" as const,
          defaultSound: true,
          defaultVibrateTimings: true,
          defaultLightSettings: true,
          visibility: "public" as const,
        },
      },
      apns: {
        payload: {
          aps: {
            sound: "default",
            badge: 1,
            contentAvailable: true,
          },
        },
      },
    };
    
    const response = await admin.messaging().send(message);
    console.log(`✅ Notificação push enviada para ${user.nome || userId}: ${title} (Message ID: ${response})`);
  } catch (error) {
    console.error(`❌ Erro ao enviar notificação push:`, error);
  }
}

/**
 * Helper: Enviar notificação para múltiplos usuários
 */
async function sendPushToMultiple(
  userIds: string[],
  baseId: string,
  title: string,
  body: string,
  data?: { [key: string]: string }
) {
  const promises = userIds.map((userId) =>
    sendPushNotification(userId, baseId, title, body, data)
  );
  await Promise.all(promises);
}

/**
 * Trigger: Motorista adicionado a uma onda
 * 
 * Notifica o motorista quando ele é adicionado a uma escala/onda
 */
export const onMotoristaAddedToOnda = functions.region("southamerica-east1")
  .firestore.document("bases/{baseId}/escalas/{escalaId}")
  .onWrite(async (change, context) => {
    const { baseId } = context.params;
    
    // Se documento foi deletado, ignorar
    if (!change.after.exists) {
      return null;
    }
    
    const beforeData: any = change.before.exists ? change.before.data() : null;
    const afterData: any = change.after.data();
    
    // Comparar ondas para detectar novos motoristas
    const ondasBefore = beforeData?.ondas || [];
    const ondasAfter = afterData?.ondas || [];
    
    // Coletar todos os motoristas de antes
    const motoristasIdsBefore = new Set<string>();
    ondasBefore.forEach((onda: any) => {
      onda.itens?.forEach((item: any) => {
        motoristasIdsBefore.add(item.motoristaId);
      });
    });
    
    // Detectar novos motoristas
    const novosMotoristas: Array<{ id: string; nome: string; onda: string; horario: string; turno: string }> = [];
    ondasAfter.forEach((onda: any) => {
      onda.itens?.forEach((item: any) => {
        if (!motoristasIdsBefore.has(item.motoristaId)) {
          novosMotoristas.push({
            id: item.motoristaId,
            nome: item.nome,
            onda: onda.nome,
            horario: onda.horario,
            turno: afterData.turno || "AM",
          });
        }
      });
    });
    
    // Enviar notificações para novos motoristas
    for (const motorista of novosMotoristas) {
      await sendPushNotification(
        motorista.id,
        baseId,
        "🚨 Você foi escalado!",
        `Turno ${motorista.turno} - ${motorista.onda} às ${motorista.horario}`,
        {
          type: "escala_update",
          turno: motorista.turno,
          onda: motorista.onda,
          horario: motorista.horario,
        }
      );
    }
    
    return null;
  });

/**
 * Trigger: Escala alterada
 * 
 * Notifica motoristas quando suas informações na escala são alteradas
 */
export const onEscalaChanged = functions.region("southamerica-east1")
  .firestore.document("bases/{baseId}/escalas/{escalaId}")
  .onUpdate(async (change, context) => {
    const { baseId } = context.params;
    
    const beforeData: any = change.before.data();
    const afterData: any = change.after.data();
    
    const ondasBefore = beforeData?.ondas || [];
    const ondasAfter = afterData?.ondas || [];
    
    // Detectar mudanças em motoristas existentes
    const motoristasAlterados: Array<{ id: string; nome: string; mudanca: string }> = [];
    
    // Criar mapa de motoristas antes
    const motoristasMapBefore = new Map<string, any>();
    ondasBefore.forEach((onda: any) => {
      onda.itens?.forEach((item: any) => {
        motoristasMapBefore.set(item.motoristaId, {
          vaga: item.vaga,
          rota: item.rota,
          horario: item.horario,
          onda: onda.nome,
        });
      });
    });
    
    // Comparar com depois
    ondasAfter.forEach((onda: any) => {
      onda.itens?.forEach((item: any) => {
        const antes = motoristasMapBefore.get(item.motoristaId);
        if (antes) {
          // Verificar se houve mudança
          if (antes.vaga !== item.vaga || antes.rota !== item.rota || antes.horario !== item.horario) {
            let mudanca = "Sua escala foi atualizada: ";
            if (antes.vaga !== item.vaga) mudanca += `Vaga ${item.vaga}`;
            if (antes.rota !== item.rota) mudanca += ` • Rota ${item.rota}`;
            if (antes.horario !== item.horario) mudanca += ` • Horário ${item.horario}`;
            
            motoristasAlterados.push({
              id: item.motoristaId,
              nome: item.nome,
              mudanca,
            });
          }
        }
      });
    });
    
    // Enviar notificações
    for (const motorista of motoristasAlterados) {
      await sendPushNotification(
        motorista.id,
        baseId,
        "⚠️ Escala Alterada",
        motorista.mudanca,
        { type: "escala_update" }
      );
    }
    
    return null;
  });

/**
 * Trigger: Status do motorista mudou
 * 
 * Notifica admin quando motorista muda de status importante
 */
export const onMotoristaStatusChanged = functions.region("southamerica-east1")
  .firestore.document("bases/{baseId}/status_motoristas/{statusId}")
  .onUpdate(async (change, context) => {
    const { baseId } = context.params;
    
    const beforeData: any = change.before.data();
    const afterData: any = change.after.data();
    
    // Detectar mudanças importantes de status
    if (beforeData.estado === afterData.estado) {
      return null; // Sem mudança relevante
    }
    
    const motoristaId = afterData.motoristaId;
    const novoEstado = afterData.estado;
    
    // Buscar dados do motorista
    const motoristaRef = db.doc(`bases/${baseId}/motoristas/${motoristaId}`);
    const motoristaSnap = await motoristaRef.get();
    
    if (!motoristaSnap.exists) {
      return null;
    }
    
    const motorista: any = motoristaSnap.data();
    
    // Notificar o próprio motorista sobre mudança de status
    const statusMessages: { [key: string]: string } = {
      "A_CAMINHO": "Você está a caminho do galpão",
      "CHEGUEI": "Você chegou ao galpão",
      "PROXIMO": "Você está próximo",
      "IR_ESTACIONAMENTO": "Vá para o ESTACIONAMENTO e aguarde",
      "ESTACIONAMENTO": "Você está no estacionamento",
      "CARREGANDO": `Subir agora para a vaga ${afterData.vagaAtual || ""} com rota ${afterData.rotaAtual || ""}`,
      "CONCLUIDO": "Carregamento concluído! Ótimo trabalho!",
    };
    
    const mensagem = statusMessages[novoEstado] || `Status atualizado para ${novoEstado}`;
    
    // Título personalizado baseado no status
    const titulos: { [key: string]: string } = {
      "IR_ESTACIONAMENTO": "🅿️ Chamada para Estacionamento",
      "CARREGANDO": "🚚 Chamada para Carregamento",
      "CONCLUIDO": "✅ Carregamento Concluído",
      "CHEGUEI": "📍 Chegou ao Galpão",
      "ESTACIONAMENTO": "🅿️ No Estacionamento",
    };
    
    const titulo = titulos[novoEstado] || "📍 Status Atualizado";
    
    // ✅ Não notificar se a mensagem estiver vazia (indica remoção da onda)
    if (afterData.mensagem && afterData.mensagem.trim() !== "") {
      // Notificar motorista
      await sendPushNotification(
        motoristaId,
        baseId,
        titulo,
        mensagem,
        { type: "status_update", status: novoEstado }
      );
    } else {
      console.log(`⏭️ Notificação ignorada para ${motorista.nome || motoristaId}: mensagem vazia (remoção da onda)`);
    }
    
    // Notificar admins em situações específicas
    const statusParaNotificarAdmin = ["CHEGUEI", "CONCLUIDO"];
    
    if (statusParaNotificarAdmin.includes(novoEstado)) {
      // Buscar todos os admins e auxiliares
      const adminsRef = db.collection(`bases/${baseId}/motoristas`).where("ativo", "==", true);
      const adminsSnap = await adminsRef.get();
      
      const adminIds: string[] = [];
      adminsSnap.forEach((doc) => {
        const userData: any = doc.data();
        if (["admin", "auxiliar", "superadmin"].includes(userData.papel)) {
          adminIds.push(doc.id);
        }
      });
      
      const adminMessage =
        novoEstado === "CHEGUEI"
          ? `${motorista.nome} chegou ao galpão`
          : `${motorista.nome} concluiu o carregamento`;
      
      await sendPushToMultiple(
        adminIds,
        baseId,
        "📢 Atualização de Motorista",
        adminMessage,
        { type: "motorista_update", motoristaId, status: novoEstado }
      );
    }
    
    return null;
  });

/**
 * Trigger: Motorista respondeu disponibilidade
 * 
 * Notifica admin quando motorista responde pesquisa de disponibilidade
 */
export const onDisponibilidadeResponse = functions.region("southamerica-east1")
  .firestore.document("disponibilidades/{dispId}")
  .onUpdate(async (change, context) => {
    const { dispId } = context.params;
    // Extrair baseId do documentId que tem formato: baseId_YYYY-MM-DD
    const baseId = dispId.split('_')[0];
    
    const beforeData: any = change.before.data();
    const afterData: any = change.after.data();
    
    // Detectar novas respostas
    const motoristasAntes = beforeData.motoristas || [];
    const motoristasDepois = afterData.motoristas || [];
    
    // Identificar quem respondeu agora
    const novasRespostas = motoristasDepois.filter((m: any) => {
      const antes = motoristasAntes.find((ma: any) => ma.motoristaId === m.motoristaId);
      return m.respondidoEm && (!antes || !antes.respondidoEm);
    });
    
    if (novasRespostas.length === 0) {
      return null;
    }
    
    // Buscar admins
    const adminsRef = db.collection(`bases/${baseId}/motoristas`).where("ativo", "==", true);
    const adminsSnap = await adminsRef.get();
    
    const adminIds: string[] = [];
    adminsSnap.forEach((doc) => {
      const userData: any = doc.data();
      if (["admin", "auxiliar", "superadmin"].includes(userData.papel)) {
        adminIds.push(doc.id);
      }
    });
    
    // Notificar cada nova resposta
    for (const resposta of novasRespostas) {
      const disponivel = resposta.disponivel ? "disponível" : "indisponível";
      await sendPushToMultiple(
        adminIds,
        baseId,
        "✅ Nova Resposta de Disponibilidade",
        `${resposta.nome} está ${disponivel} para a próxima escala`,
        { type: "disponibilidade_update", motoristaId: resposta.motoristaId }
      );
    }
    
    return null;
  });

/**
 * Callable: Chamar motorista para carregamento
 * 
 * Admin/Auxiliar chama motorista e envia notificação push
 */
export const chamarMotoristaCarregamento = functions.region("southamerica-east1")
  .https.onCall(async (data, context) => {
    const { baseId, motoristaId, vaga, rota } = data || {};
    
    // Verificar autenticação
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Login necessário");
    }
    
    // Verificar permissões
    const callerRef = db.doc(`bases/${baseId}/usuarios/${context.auth.uid}`);
    const callerSnap = await callerRef.get();
    
    if (!callerSnap.exists) {
      throw new functions.https.HttpsError("permission-denied", "Usuário não encontrado");
    }
    
    const caller: any = callerSnap.data();
    
    if (!["admin", "superadmin", "auxiliar"].includes(caller.papel)) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Apenas admin/auxiliar podem chamar motoristas"
      );
    }
    
    // Buscar motorista
    const motoristaRef = db.doc(`bases/${baseId}/motoristas/${motoristaId}`);
    const motoristaSnap = await motoristaRef.get();
    
    if (!motoristaSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Motorista não encontrado");
    }
    
    const motorista: any = motoristaSnap.data();
    
    // Atualizar status do motorista
    const statusRef = db.doc(`bases/${baseId}/status_motoristas/${motoristaId}`);
    await statusRef.set(
      {
        motoristaId,
        baseId,
        estado: "CARREGANDO",
        vagaAtual: vaga,
        rotaAtual: rota,
        mensagem: `Subir agora para a vaga ${vaga}`,
        atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    
    // Enviar notificação
    await sendPushNotification(
      motoristaId,
      baseId,
      "🚚 CHAMADA PARA CARREGAMENTO",
      `Subir agora para a vaga ${vaga} com rota ${rota}`,
      {
        type: "chamada_carregamento",
        vaga,
        rota,
      }
    );
    
    console.log(`📢 ${motorista.nome} chamado para vaga ${vaga} por ${caller.nome}`);
    
    return { ok: true };
  });

/**
 * Callable: Notificar todos os motoristas escalados
 * 
 * Admin notifica todos os motoristas da escala atual
 */
export const notificarTodosMotoristasEscalados = functions.region("southamerica-east1")
  .https.onCall(async (data, context) => {
    const { baseId, motoristaIds } = data || {};
    
    // Verificar autenticação
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Login necessário");
    }
    
    if (!baseId || !motoristaIds || !Array.isArray(motoristaIds) || motoristaIds.length === 0) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "baseId e motoristaIds são obrigatórios"
      );
    }
    
    const title = "🚛 Você foi escalado!";
    const body = "Você está escalado! Siga para o galpão e aguarde instruções.";
    
    // Enviar notificações para todos os motoristas
    await sendPushToMultiple(
      motoristaIds,
      baseId,
      title,
      body,
      { type: "escala_update" }
    );
    
    console.log(`✅ ${motoristaIds.length} motoristas notificados via push`);
    
    return { ok: true, notificados: motoristaIds.length };
  });

/**
 * ========================================
 * PEDIDO DE LOCALIZAÇÃO / ETA DO MOTORISTA
 * ========================================
 * Admin pergunta ao assistente: "Quanto tempo para o X chegar?"
 * Fluxo: requestDriverLocation -> FCM silenciosa -> app obtém GPS ->
 * receiveDriverLocation (callable pelo app) -> OpenRouteService -> grava resultado
 */

/**
 * Enviar push SILENCIOSA (data-only) para o motorista pedir localização
 * Não mostra notificação - motorista não percebe
 */
async function sendSilentLocationRequest(motoristaId: string, baseId: string) {
  const motoristaRef = db.doc(`bases/${baseId}/motoristas/${motoristaId}`);
  const motoristaSnap = await motoristaRef.get();

  if (!motoristaSnap.exists) {
    throw new Error(`Motorista ${motoristaId} não encontrado`);
  }

  const motorista: any = motoristaSnap.data();
  const fcmToken = motorista.fcmToken;

  if (!fcmToken) {
    throw new Error(`Motorista ${motorista.nome || motoristaId} não tem FCM token`);
  }

  // Mensagem APENAS com data - SEM notification (silenciosa)
  const message = {
    data: {
      type: "request_location",
      baseId,
      motoristaId,
    },
    token: fcmToken,
    android: {
      priority: "high" as const,
      // Sem notification = não mostra nada ao usuário
    },
    apns: {
      headers: { "apns-priority": "10" },
      payload: {
        aps: {
          contentAvailable: true,
          // Sem alert = silenciosa
        },
      },
    },
  };

  await admin.messaging().send(message);
  console.log(`📤 Push silenciosa de localização enviada para ${motorista.nome || motoristaId}`);
}

/**
 * Callable: Admin/Assistente solicita localização e ETA de um motorista
 */
export const requestDriverLocation = functions.region("southamerica-east1")
  .https.onCall(async (data, context) => {
    const { baseId, motoristaId } = data || {};

    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Login necessário");
    }

    if (!baseId || !motoristaId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "baseId e motoristaId são obrigatórios"
      );
    }

    // Verificar permissão (admin/auxiliar)
    const callerRef = db.doc(`bases/${baseId}/usuarios/${context.auth.uid}`);
    let callerSnap = await callerRef.get();
    if (!callerSnap.exists) {
      callerSnap = await db.doc(`bases/${baseId}/motoristas/${context.auth.uid}`).get();
    }
    if (!callerSnap.exists) {
      throw new functions.https.HttpsError("permission-denied", "Usuário não autorizado");
    }
    const caller: any = callerSnap.data();
    const papel = caller.papel || "";
    if (!["admin", "superadmin", "auxiliar", "ajudante"].includes(papel)) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Apenas admin/auxiliar podem solicitar localização"
      );
    }

    // Criar doc de resposta como "pendente" - assistente escuta e exibe quando atualizar
    const responseRef = db.doc(`bases/${baseId}/location_responses/${motoristaId}`);
    await responseRef.set({
      status: "pending",
      motoristaId,
      motoristaNome: (await db.doc(`bases/${baseId}/motoristas/${motoristaId}`).get()).data()?.nome || "",
      solicitadoEm: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    await sendSilentLocationRequest(motoristaId, baseId);

    return { ok: true, status: "pending" };
  });

/**
 * Callable: App do motorista envia coordenadas (chamado após receber push)
 * Calcula rota via OpenRouteService e grava apenas distância e ETA
 */
export const receiveDriverLocation = functions.region("southamerica-east1")
  .https.onCall(async (data, context) => {
    const { baseId, motoristaId, lat, lng } = data || {};

    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Login necessário");
    }

    // Só o próprio motorista pode enviar sua localização
    if (context.auth.uid !== motoristaId) {
      throw new functions.https.HttpsError("permission-denied", "Apenas o motorista pode enviar sua localização");
    }

    if (!baseId || !motoristaId || typeof lat !== "number" || typeof lng !== "number") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "baseId, motoristaId, lat e lng são obrigatórios"
      );
    }

    const configRef = db.doc(`bases/${baseId}/configuracao/principal`);
    const configSnap = await configRef.get();
    if (!configSnap.exists) {
      await db.doc(`bases/${baseId}/location_responses/${motoristaId}`).set({
        status: "error",
        error: "Galpão não configurado",
        atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      return { ok: false, error: "Galpão não configurado" };
    }

    const config: any = configSnap.data();
    const galpao = config.galpao || {};
    const galpaoLat = galpao.lat;
    const galpaoLng = galpao.lng;

    if (!galpaoLat || !galpaoLng) {
      await db.doc(`bases/${baseId}/location_responses/${motoristaId}`).set({
        status: "error",
        error: "Coordenadas do galpão não configuradas",
        atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      return { ok: false, error: "Coordenadas do galpão não configuradas" };
    }

    const orsApiKey = process.env.ORS_API_KEY || functions.config().openrouteservice?.key;
    if (!orsApiKey) {
      console.error("ORS_API_KEY não configurada");
      await db.doc(`bases/${baseId}/location_responses/${motoristaId}`).set({
        status: "error",
        error: "Serviço indisponível",
        atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      return { ok: false, error: "Serviço indisponível" };
    }

    try {
      // OpenRouteService: coordinates são [lng, lat]
      const url = `https://api.openrouteservice.org/v2/directions/driving-car`;
      const body = {
        coordinates: [
          [lng, lat],
          [galpaoLng, galpaoLat],
        ],
      };

      const res = await fetch(url, {
        method: "POST",
        headers: {
          "Authorization": orsApiKey,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      });

      if (!res.ok) {
        const errText = await res.text();
        console.error("OpenRouteService erro:", res.status, errText);
        throw new Error(`ORS: ${res.status}`);
      }

      const orsResult: any = await res.json();
      const route = orsResult.routes?.[0];
      if (!route?.summary) {
        throw new Error("Rota não encontrada");
      }

      const distanceMeters = route.summary.distance || 0;
      const durationSeconds = route.summary.duration || 0;
      const etaMinutes = Math.round(durationSeconds / 60);
      const distanceKm = Math.round((distanceMeters / 1000) * 10) / 10;

      const motoristaSnap = await db.doc(`bases/${baseId}/motoristas/${motoristaId}`).get();
      const motoristaNome = motoristaSnap.data()?.nome || "Motorista";

      await db.doc(`bases/${baseId}/location_responses/${motoristaId}`).set({
        status: "ready",
        motoristaId,
        motoristaNome,
        distanceMeters,
        distanceKm,
        etaMinutes,
        atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });

      console.log(`✅ Localização recebida: ${motoristaNome} - ${distanceKm} km, ~${etaMinutes} min`);

      return { ok: true, distanceKm, etaMinutes };
    } catch (e: any) {
      console.error("Erro ao calcular rota:", e);
      await db.doc(`bases/${baseId}/location_responses/${motoristaId}`).set({
        status: "error",
        error: "Erro ao calcular rota",
        atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      return { ok: false, error: e.message };
    }
  });

/**
 * ========================================
 * DISPONIBILIDADE AUTOMÁTICA DIÁRIA
 * ========================================
 */

/**
 * CRIA DISPONIBILIDADE AUTOMATICAMENTE TODOS OS DIAS
 * 
 * Roda todos os dias às 00:00 (meia-noite) e cria disponibilidade
 * para o dia seguinte para todas as bases ativas
 * Motoristas sempre respondem para o dia seguinte
 */
export const criarDisponibilidadeDiaria = functions.region("southamerica-east1")
  .pubsub.schedule("0 0 * * *") // Todos os dias às 00:00
  .timeZone("America/Sao_Paulo")
  .onRun(async (context) => {
    console.log("🌅 Iniciando criação automática de disponibilidades...");
    
    try {
      // Calcular data de amanhã
      const amanha = new Date();
      amanha.setDate(amanha.getDate() + 1);
      const dataAmanha = amanha.toISOString().split('T')[0]; // YYYY-MM-DD
      
      console.log(`📅 Criando disponibilidades para: ${dataAmanha}`);
      
      // Buscar todas as bases
      const basesSnapshot = await db.collection("bases").get();
      
      let basesProcessadas = 0;
      let disponibilidadesCriadas = 0;
      
      for (const baseDoc of basesSnapshot.docs) {
        const baseId = baseDoc.id;
        const docId = `${baseId}_${dataAmanha}`;
        
        // Verificar se já existe
        const dispRef = db.collection("disponibilidades").doc(docId);
        const dispSnapshot = await dispRef.get();
        
        if (dispSnapshot.exists) {
          console.log(`⏭️ Disponibilidade já existe para base ${baseId}`);
          continue;
        }
        
        // Buscar todos os motoristas ativos da base
        const motoristasSnapshot = await db
          .collection(`bases/${baseId}/motoristas`)
          .where("ativo", "==", true)
          .where("papel", "==", "motorista")
          .get();
        
        if (motoristasSnapshot.empty) {
          console.log(`⚠️ Nenhum motorista encontrado na base ${baseId}`);
          continue;
        }
        
        const motoristas = motoristasSnapshot.docs.map(doc => ({
          motoristaId: doc.id,
          nome: doc.data().nome || "",
          telefone: doc.data().telefone || "",
          disponivel: null,
          respondidoEm: null,
        }));
        
        // Criar disponibilidade
        await dispRef.set({
          baseId,
          data: dataAmanha,
          motoristas,
          notificacaoEnviada: false,
          criadoEm: admin.firestore.FieldValue.serverTimestamp(),
          criadoPor: "system", // Sistema automático
        });
        
        console.log(`✅ Disponibilidade criada para base ${baseId} com ${motoristas.length} motoristas`);
        disponibilidadesCriadas++;
        
        // Enviar notificações push para motoristas
        const motoristaIds = motoristas.map((m: any) => m.motoristaId);
        await sendPushToMultiple(
          motoristaIds,
          baseId,
          "📋 Disponibilidade Solicitada",
          "Você tem uma nova solicitação de disponibilidade para responder",
          { type: "disponibilidade_solicitada", data: dataAmanha }
        );
        
        basesProcessadas++;
      }
      
      console.log(`✅ Concluído: ${disponibilidadesCriadas} disponibilidades criadas em ${basesProcessadas} bases`);
      
      return null;
    } catch (error: any) {
      console.error("❌ Erro ao criar disponibilidades:", error);
      throw error;
    }
  });

/**
 * Trigger: Base aprovada/rejeitada
 * 
 * Notifica o admin quando sua transportadora é aprovada ou rejeitada
 */
export const onBaseStatusChanged = functions.region("southamerica-east1")
  .firestore.document("bases/{baseId}")
  .onUpdate(async (change, context) => {
    const { baseId } = context.params;
    
    const beforeData: any = change.before.data();
    const afterData: any = change.after.data();
    
    // Verificar se o status mudou
    if (beforeData.statusAprovacao === afterData.statusAprovacao) {
      return null; // Sem mudança
    }
    
    const statusAntigo = beforeData.statusAprovacao;
    const novoStatus = afterData.statusAprovacao;
    
    console.log(`🔄 Base ${baseId}: Status mudou de '${statusAntigo}' para '${novoStatus}'`);
    
    // Buscar o admin da base (primeiro admin criado)
    try {
      const motoristasRef = db.collection(`bases/${baseId}/motoristas`)
        .where("papel", "==", "admin")
        .where("ativo", "==", true)
        .limit(1);
      
      const motoristasSnap = await motoristasRef.get();
      
      if (motoristasSnap.empty) {
        console.log(`⚠️ Nenhum admin encontrado para base ${baseId}`);
        return null;
      }
      
      const adminDoc = motoristasSnap.docs[0];
      const adminId = adminDoc.id;
      const adminData: any = adminDoc.data();
      
      console.log(`👤 Admin encontrado: ${adminData.nome || adminId} (${adminId})`);
      
      // Enviar notificação baseada no novo status
      if (novoStatus === "ativa") {
        await sendPushNotification(
          adminId,
          baseId,
          "✅ Transportadora Aprovada!",
          "Sua transportadora foi aprovada e está pronta para uso. Você já pode fazer login e começar a usar o app!",
          {
            type: "base_aprovada",
            baseId: baseId
          }
        );
        console.log(`✅ Notificação de aprovação enviada para admin ${adminId} da base ${baseId}`);
      } else if (novoStatus === "rejeitada") {
        await sendPushNotification(
          adminId,
          baseId,
          "❌ Transportadora Rejeitada",
          "Sua solicitação de transportadora foi rejeitada.",
          {
            type: "base_rejeitada",
            baseId: baseId
          }
        );
        console.log(`✅ Notificação de rejeição enviada para admin ${adminId} da base ${baseId}`);
      }
      
      return null;
    } catch (error: any) {
      console.error(`❌ Erro ao processar mudança de status da base ${baseId}:`, error);
      return null;
    }
  });

/**
 * ========================================
 * SISTEMA DE MONETIZAÇÃO
 * ========================================
 */

/**
 * Verificar e ativar monetização automaticamente
 * 
 * Roda a cada hora e verifica se há agendamentos de ativação
 * que já passaram da data definida
 */
export const verificarAtivacaoAutomatica = functions.region("southamerica-east1")
  .pubsub.schedule("every 1 hours") // A cada hora
  .onRun(async (context) => {
    console.log("🔄 Verificando ativação automática de monetização...");
    
    try {
      const sistemaRef = db.collection("sistema").doc("config");
      const sistemaSnap = await sistemaRef.get();
      
      if (!sistemaSnap.exists) {
        console.log("ℹ️ Configuração do sistema não existe ainda");
        return null;
      }
      
      const data: any = sistemaSnap.data();
      
      // Se já está ativo, não fazer nada
      if (data.monetizacaoAtiva) {
        console.log("✅ Monetização já está ativa");
        return null;
      }
      
      // Se não tem data agendada, não fazer nada
      if (!data.dataAtivacaoAutomatica) {
        console.log("ℹ️ Nenhuma data de ativação agendada");
        return null;
      }
      
      const agora = new Date();
      const dataAgendada = data.dataAtivacaoAutomatica.toDate();
      
      console.log(`📅 Data agendada: ${dataAgendada.toISOString()}`);
      console.log(`🕐 Agora: ${agora.toISOString()}`);
      
      // Se a data chegou, ativar
      if (agora >= dataAgendada) {
        console.log("✅ Data de ativação chegou! Ativando monetização...");
        
        await sistemaRef.update({
          monetizacaoAtiva: true,
          modoAtivacao: "AUTOMATICA",
          dataAtivacao: admin.firestore.FieldValue.serverTimestamp(),
          ultimaModificacao: admin.firestore.FieldValue.serverTimestamp()
        });
        
        console.log("✅ Monetização ativada automaticamente com sucesso!");
        
        // TODO: Enviar notificação para super admin (se necessário)
        // await sendPushNotification(...);
        
        return null;
      } else {
        const diffMs = dataAgendada.getTime() - agora.getTime();
        const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
        console.log(`⏰ Ainda faltam ${diffHours} horas para ativação`);
        return null;
      }
    } catch (error: any) {
      console.error("❌ Erro ao verificar ativação automática:", error);
      return null;
    }
  });



