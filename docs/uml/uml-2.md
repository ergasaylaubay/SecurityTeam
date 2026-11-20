# [UML 2] Sequence және Component диаграммалары

Дереккөз: https://trello.com/c/d6GFCmWh

2026-11-20 күнгі Trello материалдарының көшірмесі. Бұл бұрынғы жоспар: ондағы «осы апта» және аяқталу жөніндегі мәтін нақты код күйін растамайды. Іске асырылған мүмкіндіктер README-де көрсетіледі.

## Sequence түсіндірмесі

Алдын ала шарт: A және B тіркелген, ACTIVE, чат мүшелері; A сервердің public key мәнін алған. Сценарий жаңа хабарды шифрлаудан бастап алушының оқуына дейін барады. 401/403, duplicate және GCM failure тармақтары бар. Алушы offline болса, хабар DB-де қалады, кейін history арқылы оқиды. Қайталанған ID мен өзгертілген payload үшін 409 conflict ережесін іске асыруда қосу қажет.

```plantuml
@startuml
actor "Жіберуші" as User
participant "JavaFX клиент A" as A
participant "Spring Security" as Security
participant "MessageService" as Service
participant "CryptoService" as Crypto
database "PostgreSQL" as DB
participant "Алушының клиенті B" as B
User -> A : Хабар енгізу
A -> A : Жаңа AES кілті + nonce\nAES-GCM(text, AAD)\nRSA-OAEP(serverPublicKey, AES key)
A -> Security : HTTPS POST /messages\nSession + CSRF + TransportEnvelope
Security -> Security : Session, ACTIVE статус, CSRF тексеру
alt Аутентификация жарамсыз
 Security --> A : 401 / 403; хабар сақталмайды
else Аутентификация дұрыс
 Security -> Service : send(envelope, authenticatedPrincipal)
 Service -> DB : Чат мүшелігі, өлшем, duplicate тексеру
 alt Рұқсат жоқ
  Service --> A : 403; мазмұн өңделмейді
 else Қайталанған clientMessageId
  Service --> A : Бұрын сақталған messageId
 else Жаңа және рұқсат етілген хабар
  Service -> Crypto : RSA unwrap + AES-GCM verify/decrypt
  alt OAEP немесе GCM тексеруі өтпеді
   Crypto --> Service : Reject
   Service --> A : Жалпы қате; сақтау жоқ
  else Тексеру сәтті
   Crypto --> Service : plaintext (тек жадта)
   Service -> Crypto : Бөлек storage key + жаңа nonce\nAES-GCM шифрлау
   Crypto --> Service : storageCiphertext + nonce + keyId
   Service -> DB : Message + мазмұнсыз AuditEvent сақтау\nБір транзакция, UNIQUE sender/clientMessageId
   DB --> Service : commit OK
   Service --> A : messageId, accepted
   Service -> Security : commit-тен кейін B мүшелігін тексеру
   Security -> B : WSS жеке арна: messageId хабарламасы
   B -> Security : HTTPS GET /messages/{id}
   Security -> Service : Session + ағымдағы мүшелік тексеру
   Service -> DB : ciphertext оқу
   Service -> Crypto : Storage decrypt
   Service --> B : HTTPS арқылы хабар мазмұны
  end
 end
end
note over Service,B
Сервер сенімді: мазмұн сервер жадында ашылады.
Бұл E2EE емес. Offline клиент тарихты кейін HTTPS арқылы алады.
end note
@enduml
```

## Component түсіндірмесі

Клиент JavaFX UI, JCA және HTTP/WSS байланысынан тұрады. Сервер Security, бизнес логика, криптография, аудит және repository модульдеріне бөлінген. Key store DB/GitHub-тан бөлек. Notification тек DB commit-тен кейін жіберіледі. History endpoint әр оқуда рұқсатты қайта тексереді.

```plantuml
@startuml
skinparam componentStyle rectangle
node "Қызметкер құрылғысы" {
 component "JavaFX UI" as UI
 component "Client Crypto (JCA)" as CC
 component "HTTPS / WSS клиент" as HTTP
 UI --> CC
 UI --> HTTP
}
node "Қолданба сервері" {
 component "REST API" as API
 component "Spring Security\nSession + RBAC + membership" as Security
 component "Chat / Message Service" as Service
 component "Server Crypto (JCA)" as SC
 component "WebSocket notifications" as WS
 component "Audit Service" as Audit
 component "Repository" as Repo
 API --> Security
 Security --> Service
 Service --> SC
 Service --> Repo
 Service --> Audit
 Service --> WS : after commit
 Audit --> Repo
}
database "PostgreSQL\nUsers, Chats, Members,\nEncrypted Messages, Audit" as DB
artifact "Қорғалған key store\nRSA private key + storage AES key\nGitHub пен DB-ден бөлек" as KS
HTTP --> API : HTTPS
HTTP --> WS : WSS; own user queue
SC --> KS
Repo --> DB : шектеулі DB account
note bottom of Security
HTTP және WebSocket аутентификациясы байланысқан.
SEND/SUBSCRIBE және history үшін рұқсат тексеріледі.
Бұғатталғанда session/socket жабылады.
end note
@enduml
```

## Қамту

FR-04: клиент AES-GCM + RSA-OAEP; сервер unwrap/decrypt және storage encryption.
FR-05: HTTPS/WSS; FR-06: session/role/membership; FR-07: AuditService.
Бұл сенімді сервер моделі; E2EE ретінде таныстырылмайды.

## Аяқтау критерийі

Екі диаграмма экспортталып тіркелді; success/error жолдары түсіндірілді; кілт пен plaintext қай жерде болатыны анық; Research 2 және UML 1 кластарымен сәйкестік тексерілді.

## Осы аптадағы тәртіп

Бұл — команда тексеріп, өз сөзімен түсіндіріп, бекітетін бастапқы нұсқа. Дайын деп автоматты белгіленбейді.
To Do → In Progress → Code Review → Готово. Осы аптада Code Review бағанында зерттеу мен UML де тексеріледі.
GitHub: [https://github.com/ergasaylaubay/SecurityTeam](https://github.com/ergasaylaubay/SecurityTeam "smartCard-inline")
Код жұмысы басталғанда сәйкес Issue/branch/PR сілтемесін қосу. Құжаттар мен диаграммалар Trello-да сақталады.

