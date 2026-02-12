# 🔥 Guia de Implementação Firebase - Controle de Escalas

## 📋 Pré-requisitos

1. **Android Studio** instalado e atualizado
2. **Projeto Android** configurado
3. **Conta Google** para acessar Firebase Console
4. **Java 17** ou superior

## 🚀 Passo 1: Configurar Projeto Firebase

### 1.1 Criar Projeto no Firebase Console
1. Acesse [Firebase Console](https://console.firebase.google.com/)
2. Clique em **"Criar um projeto"**
3. Digite o nome: `controle-escalas`
4. Aceite os termos e clique **"Continuar"**
5. Desabilite **Google Analytics** (opcional para MVP)
6. Clique **"Criar projeto"**

### 1.2 Adicionar App Android
1. No projeto Firebase, clique no ícone **Android**
2. **Nome do pacote**: `com.controleescalas.app`
3. **Apelido do app**: `ControleEscalas`
4. **Certificado de assinatura**: Deixe em branco (para desenvolvimento)
5. Clique **"Registrar app"**

### 1.3 Baixar google-services.json
1. Baixe o arquivo `google-services.json`
2. Coloque em: `raiz-prompt/app/google-services.json`
3. **IMPORTANTE**: Não commite este arquivo no Git!

## 🔧 Passo 2: Configurar Dependências

### 2.1 Atualizar build.gradle.kts (Projeto)
```kotlin
// raiz-prompt/build.gradle.kts
buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.4")
    }
}
```

### 2.2 Atualizar build.gradle.kts (App)
```kotlin
// raiz-prompt/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
}

dependencies {
    // Firebase services (versões específicas para evitar problemas de resolução)
    implementation("com.google.firebase:firebase-firestore-ktx:24.9.1")
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.1")
    
    // Coroutines para Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
```

## 🗄️ Passo 3: Configurar Firestore Database

### 3.1 Criar Database
1. No Firebase Console, vá em **Firestore Database**
2. Clique **"Criar banco de dados"**
3. Escolha **"Começar no modo de teste"**
4. Selecione uma região próxima (ex: `southamerica-east1`)
5. Clique **"Próximo"**

### 3.2 Configurar Regras de Segurança
```javascript
// Firestore Rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Permitir leitura/escrita apenas para usuários autenticados
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## 📱 Passo 4: Implementar Firebase no App

### 4.1 Criar FirebaseManager
```kotlin
// raiz-prompt/app/src/main/java/com/controleescalas/app/data/FirebaseManager.kt
package com.controleescalas.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.FirebaseAuth

object FirebaseManager {
    val firestore = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
}
```

### 4.2 Criar Repository
```kotlin
// raiz-prompt/app/src/main/java/com/controleescalas/app/data/Repository.kt
package com.controleescalas.app.data

import kotlinx.coroutines.tasks.await

class Repository {
    private val firestore = FirebaseManager.firestore
    
    // Exemplo: Buscar motoristas de uma base
    suspend fun getMotoristas(baseId: String): List<AdminMotoristaCardData> {
        return try {
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(AdminMotoristaCardData::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Exemplo: Criar nova base
    suspend fun createBase(baseData: CreateBaseData): String? {
        return try {
            val docRef = firestore.collection("bases").add(baseData).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }
}
```

## 🔐 Passo 5: Implementar Autenticação

### 5.1 Configurar Authentication
1. No Firebase Console, vá em **Authentication**
2. Clique **"Começar"**
3. Vá em **"Sign-in method"**
4. Habilite **"Email/Password"**
5. Habilite **"Phone"** (opcional)

### 5.2 Implementar Login
```kotlin
// raiz-prompt/app/src/main/java/com/controleescalas/app/data/AuthRepository.kt
package com.controleescalas.app.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseManager.auth
    
    suspend fun loginWithPhone(phone: String, pin: String): Boolean {
        return try {
            // Implementar lógica de login com telefone + PIN
            // Buscar usuário no Firestore por telefone
            // Validar PIN
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun createUser(phone: String, pin: String, role: String): Boolean {
        return try {
            // Implementar criação de usuário
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

## 📊 Passo 6: Estrutura de Dados

### 6.1 Collections do Firestore
```
bases/
├── {baseId}/
│   ├── info/
│   │   ├── nome: string
│   │   ├── transportadora: string
│   │   ├── corTema: string
│   │   └── coordenadas: object
│   ├── motoristas/
│   │   ├── {motoristaId}/
│   │   │   ├── nome: string
│   │   │   ├── telefone: string
│   │   │   ├── pin: string (hash)
│   │   │   ├── papel: string
│   │   │   └── ativo: boolean
│   ├── escalas/
│   │   ├── {data}/
│   │   │   ├── turno: string
│   │   │   ├── ondas: array
│   │   │   └── status: object
│   └── configuracao/
│       ├── galpao: object
│       └── estacionamento: object
```

### 6.2 Data Classes
```kotlin
// raiz-prompt/app/src/main/java/com/controleescalas/app/data/models/FirebaseModels.kt
package com.controleescalas.app.data.models

import com.google.firebase.firestore.DocumentId

data class Base(
    @DocumentId val id: String = "",
    val nome: String = "",
    val transportadora: String = "",
    val corTema: String = "#16A34A",
    val coordenadas: Coordenadas = Coordenadas()
)

data class Coordenadas(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val raio: Int = 100
)

data class Motorista(
    @DocumentId val id: String = "",
    val nome: String = "",
    val telefone: String = "",
    val pin: String = "", // Hash do PIN
    val papel: String = "motorista", // motorista, admin, ajudante
    val ativo: Boolean = true
)

data class Escala(
    @DocumentId val id: String = "",
    val data: String = "", // YYYY-MM-DD
    val turno: String = "", // AM, PM
    val ondas: List<Onda> = emptyList()
)

data class Onda(
    val nome: String = "",
    val horario: String = "",
    val itens: List<OndaItem> = emptyList()
)

data class OndaItem(
    val motoristaId: String = "",
    val vaga: String = "",
    val rota: String = "",
    val hasPdf: Boolean = false
)
```

## 🔄 Passo 7: Implementar ViewModels

### 7.1 DriverViewModel
```kotlin
// raiz-prompt/app/src/main/java/com/controleescalas/app/ui/viewmodels/DriverViewModel.kt
package com.controleescalas.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DriverViewModel : ViewModel() {
    private val repository = Repository()
    
    private val _escalaInfo = MutableStateFlow<DriverEscalaInfo?>(null)
    val escalaInfo: StateFlow<DriverEscalaInfo?> = _escalaInfo
    
    private val _statusInfo = MutableStateFlow<DriverStatusInfo?>(null)
    val statusInfo: StateFlow<DriverStatusInfo?> = _statusInfo
    
    fun loadDriverData(motoristaId: String) {
        viewModelScope.launch {
            // Carregar dados do Firebase
            // _escalaInfo.value = repository.getEscalaInfo(motoristaId)
            // _statusInfo.value = repository.getStatusInfo(motoristaId)
        }
    }
}
```

## 📱 Passo 8: Integrar com UI

### 8.1 Atualizar Screens
```kotlin
// Exemplo: DriverHomeScreen
@Composable
fun DriverHomeScreen(
    viewModel: DriverViewModel = hiltViewModel()
) {
    val escalaInfo by viewModel.escalaInfo.collectAsState()
    val statusInfo by viewModel.statusInfo.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadDriverData("motoristaId")
    }
    
    // UI components...
}
```

## 🧪 Passo 9: Testes

### 9.1 Testar Conexão
```kotlin
// Teste simples para verificar se Firebase está funcionando
class FirebaseTest {
    @Test
    fun testFirebaseConnection() {
        val firestore = FirebaseFirestore.getInstance()
        // Teste de conexão
    }
}
```

## 🚀 Passo 10: Deploy e Produção

### 10.1 Configurar Regras de Produção
```javascript
// Firestore Rules para produção
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /bases/{baseId} {
      allow read, write: if request.auth != null && 
        request.auth.uid in resource.data.admins;
    }
  }
}
```

### 10.2 Configurar Storage
1. No Firebase Console, vá em **Storage**
2. Clique **"Começar"**
3. Configure regras de segurança
4. Implemente upload de PDFs

## 📝 Checklist de Implementação

- [ ] Projeto Firebase criado
- [ ] google-services.json adicionado
- [ ] Dependências configuradas
- [ ] Firestore Database criado
- [ ] Regras de segurança configuradas
- [ ] FirebaseManager implementado
- [ ] Repository implementado
- [ ] AuthRepository implementado
- [ ] Data classes criadas
- [ ] ViewModels implementados
- [ ] UI integrada com Firebase
- [ ] Testes básicos funcionando
- [ ] Regras de produção configuradas

## 🔧 Próximos Passos

1. **Implementar cada Repository** conforme necessário
2. **Criar ViewModels** para cada tela
3. **Integrar com UI** usando StateFlow/Flow
4. **Implementar tratamento de erros**
5. **Adicionar loading states**
6. **Configurar notificações push**
7. **Implementar upload de PDFs**

## 📚 Recursos Úteis

- [Documentação Firebase](https://firebase.google.com/docs)
- [Firestore Android](https://firebase.google.com/docs/firestore/quickstart)
- [Firebase Auth](https://firebase.google.com/docs/auth/android/start)
- [Firebase Storage](https://firebase.google.com/docs/storage/android/start)
