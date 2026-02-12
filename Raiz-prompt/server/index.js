/**
 * Servidor Node.js para monitorar Firestore e enviar notificações FCM
 * 
 * Este servidor monitora mudanças de status dos motoristas no Firestore
 * e envia notificações FCM em tempo real, mesmo quando o app está fechado.
 * 
 * Para usar:
 * 1. Configure as variáveis de ambiente (veja .env.example)
 * 2. npm install
 * 3. npm start
 * 
 * Para deploy gratuito:
 * - Railway: https://railway.app
 * - Render: https://render.com
 * - Heroku: https://heroku.com (free tier limitado)
 */

require('dotenv').config();
const admin = require('firebase-admin');

// Inicializar Firebase Admin
if (!admin.apps.length) {
  try {
    // Opção 1: Usar arquivo de credenciais (recomendado para produção)
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
      const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      console.log('✅ Firebase Admin inicializado com service account');
    }
    // Opção 2: Usar variável de ambiente com caminho do arquivo
    else if (process.env.FIREBASE_SERVICE_ACCOUNT_PATH) {
      const serviceAccount = require(process.env.FIREBASE_SERVICE_ACCOUNT_PATH);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      console.log('✅ Firebase Admin inicializado com arquivo de service account');
    }
    // Opção 3: Usar Application Default Credentials (para Google Cloud)
    else {
      admin.initializeApp();
      console.log('✅ Firebase Admin inicializado com Application Default Credentials');
    }
  } catch (error) {
    console.error('❌ Erro ao inicializar Firebase Admin:', error);
    process.exit(1);
  }
}

const db = admin.firestore();

/**
 * Enviar notificação FCM para um motorista
 */
async function sendPushNotification(motoristaId, baseId, title, body, data = {}) {
  try {
    // Buscar dados do motorista
    const motoristaRef = db.doc(`bases/${baseId}/motoristas/${motoristaId}`);
    const motoristaSnap = await motoristaRef.get();
    
    if (!motoristaSnap.exists) {
      console.log(`⚠️ Motorista ${motoristaId} não encontrado na base ${baseId}`);
      return;
    }
    
    const motorista = motoristaSnap.data();
    const fcmToken = motorista.fcmToken;
    
    if (!fcmToken) {
      console.log(`⚠️ Motorista ${motorista.nome || motoristaId} não tem FCM token`);
      return;
    }
    
    const message = {
      notification: {
        title,
        body,
      },
      data: {
        ...data,
        type: data.type || 'status_update',
        click_action: 'FLUTTER_NOTIFICATION_CLICK',
      },
      token: fcmToken,
      android: {
        priority: 'high',
        notification: {
          channelId: 'controle_escalas_channel',
          sound: 'default',
          priority: 'high',
          defaultSound: true,
          defaultVibrateTimings: true,
          defaultLightSettings: true,
          visibility: 'public',
        },
      },
      apns: {
        payload: {
          aps: {
            sound: 'default',
            badge: 1,
            contentAvailable: true,
          },
        },
      },
    };
    
    const response = await admin.messaging().send(message);
    console.log(`✅ Notificação FCM enviada para ${motorista.nome || motoristaId}: ${title} (Message ID: ${response})`);
    return response;
  } catch (error) {
    console.error(`❌ Erro ao enviar notificação FCM para ${motoristaId}:`, error);
    throw error;
  }
}

/**
 * Determinar título e mensagem baseado no status
 */
function getNotificationContent(status, statusData) {
  const statusMessages = {
    'IR_ESTACIONAMENTO': {
      title: '🅿️ Chamada para Estacionamento',
      body: `Olá! Vá para o ESTACIONAMENTO e aguarde`,
      data: { type: 'chamada_motorista', status: 'IR_ESTACIONAMENTO' }
    },
    'CARREGANDO': {
      title: '🚚 Chamada para Carregamento',
      body: `Subir agora para a vaga ${statusData.vagaAtual || ''} com rota ${statusData.rotaAtual || ''}`,
      data: { 
        type: 'chamada_motorista', 
        status: 'CARREGANDO',
        vaga: statusData.vagaAtual || '',
        rota: statusData.rotaAtual || ''
      }
    },
    'CONCLUIDO': {
      title: '✅ Carregamento Concluído',
      body: statusData.mensagem || 'Carregamento finalizado! Ótimo trabalho!',
      data: { type: 'status_update', status: 'CONCLUIDO' }
    },
    'CHEGUEI': {
      title: '📍 Chegou ao Galpão',
      body: statusData.mensagem || 'Você chegou ao galpão',
      data: { type: 'status_update', status: 'CHEGUEI' }
    },
    'ESTACIONAMENTO': {
      title: '🅿️ No Estacionamento',
      body: statusData.mensagem || 'Você está no estacionamento',
      data: { type: 'status_update', status: 'ESTACIONAMENTO' }
    }
  };
  
  return statusMessages[status] || {
    title: '📍 Status Atualizado',
    body: statusData.mensagem || `Status atualizado para ${status}`,
    data: { type: 'status_update', status }
  };
}

/**
 * Monitorar mudanças de status de todos os motoristas
 */
function startMonitoring() {
  console.log('🚀 Iniciando monitoramento de status dos motoristas...');
  
  // Monitorar todas as bases
  const basesRef = db.collection('bases');
  
  basesRef.get().then((basesSnapshot) => {
    console.log(`📋 Encontradas ${basesSnapshot.size} bases`);
    
    basesSnapshot.forEach((baseDoc) => {
      const baseId = baseDoc.id;
      const statusMotoristasRef = db.collection(`bases/${baseId}/status_motoristas`);
      
      console.log(`👂 Monitorando status_motoristas da base: ${baseId}`);
      
      // Listener em tempo real para mudanças de status
      statusMotoristasRef.onSnapshot(
        (snapshot) => {
          snapshot.docChanges().forEach((change) => {
            if (change.type === 'modified') {
              const statusData = change.doc.data();
              const motoristaId = change.doc.id;
              const novoStatus = statusData.estado;
              
              // Buscar status anterior (se disponível)
              const statusAnterior = change.doc.metadata.hasPendingWrites 
                ? null 
                : (change.doc.metadata.fromCache ? null : statusData.estadoAnterior);
              
              // Só notificar se o status realmente mudou
              if (statusAnterior !== novoStatus && novoStatus) {
                console.log(`🔄 Mudança de status detectada: ${motoristaId} (${baseId}) - ${statusAnterior || 'null'} -> ${novoStatus}`);
                
                // Notificar apenas para estados importantes
                const estadosImportantes = ['IR_ESTACIONAMENTO', 'CARREGANDO', 'CONCLUIDO'];
                
                if (estadosImportantes.includes(novoStatus)) {
                  const notification = getNotificationContent(novoStatus, statusData);
                  
                  sendPushNotification(
                    motoristaId,
                    baseId,
                    notification.title,
                    notification.body,
                    notification.data
                  ).catch((error) => {
                    console.error(`❌ Erro ao enviar notificação:`, error);
                  });
                } else {
                  console.log(`ℹ️ Status ${novoStatus} não requer notificação`);
                }
              }
            }
          });
        },
        (error) => {
          console.error(`❌ Erro no listener de status da base ${baseId}:`, error);
        }
      );
    });
  }).catch((error) => {
    console.error('❌ Erro ao buscar bases:', error);
  });
}

/**
 * Função auxiliar para monitorar uma base específica
 */
function monitorBase(baseId) {
  console.log(`👂 Monitorando base específica: ${baseId}`);
  
  const statusMotoristasRef = db.collection(`bases/${baseId}/status_motoristas`);
  
  // Armazenar último status conhecido para cada motorista
  const lastStatuses = new Map();
  
  statusMotoristasRef.onSnapshot(
    (snapshot) => {
      snapshot.docChanges().forEach((change) => {
        if (change.type === 'modified' || change.type === 'added') {
          const statusData = change.doc.data();
          const motoristaId = change.doc.id;
          const novoStatus = statusData.estado;
          const lastStatus = lastStatuses.get(motoristaId);
          
          // Só notificar se o status realmente mudou
          if (lastStatus !== novoStatus && novoStatus) {
            console.log(`🔄 Mudança de status: ${motoristaId} (${baseId}) - ${lastStatus || 'null'} -> ${novoStatus}`);
            
            // Atualizar último status conhecido
            lastStatuses.set(motoristaId, novoStatus);
            
            // Notificar apenas para estados importantes
            const estadosImportantes = ['IR_ESTACIONAMENTO', 'CARREGANDO', 'CONCLUIDO'];
            
            if (estadosImportantes.includes(novoStatus)) {
              const notification = getNotificationContent(novoStatus, statusData);
              
              sendPushNotification(
                motoristaId,
                baseId,
                notification.title,
                notification.body,
                notification.data
              ).catch((error) => {
                console.error(`❌ Erro ao enviar notificação:`, error);
              });
            }
          } else if (change.type === 'added') {
            // Primeira vez que vemos este motorista, apenas armazenar status
            lastStatuses.set(motoristaId, novoStatus);
          }
        } else if (change.type === 'removed') {
          // Motorista removido, limpar do cache
          lastStatuses.delete(change.doc.id);
        }
      });
    },
    (error) => {
      console.error(`❌ Erro no listener de status da base ${baseId}:`, error);
    }
  );
}

// Iniciar monitoramento
console.log('🚀 Servidor de notificações FCM iniciado');
console.log('📡 Aguardando mudanças de status...\n');

// Se BASE_ID estiver definido, monitorar apenas essa base
// Caso contrário, monitorar todas as bases
if (process.env.BASE_ID) {
  console.log(`🎯 Modo: Monitorando base específica (${process.env.BASE_ID})`);
  monitorBase(process.env.BASE_ID);
} else {
  console.log('🌐 Modo: Monitorando todas as bases');
  startMonitoring();
}

// Manter o processo vivo
process.on('SIGTERM', () => {
  console.log('🛑 Recebido SIGTERM, encerrando servidor...');
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('🛑 Recebido SIGINT, encerrando servidor...');
  process.exit(0);
});

