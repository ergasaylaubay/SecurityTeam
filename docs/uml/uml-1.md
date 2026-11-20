# [UML 1] Use Case және Class — 3-адам

Дереккөз: https://trello.com/c/SjTGbOde

2026-11-20 күнгі Trello материалдарының көшірмесі. Бұл бұрынғы жоспар: ондағы «осы апта» және аяқталу жөніндегі мәтін нақты код күйін растамайды. Іске асырылған мүмкіндіктер README-де көрсетіледі.

## Жауапты: 3-адам — UML
Use Case пайдаланушылардың әрекеттерін, Class жүйенің деректерін көрсетеді. Тексеруші: 1-адам.
GitHub: https://github.com/ergasaylaubay/SecurityTeam

## Use Case
Қонақ тіркеледі және кіреді. Қызметкер жеке/топтық чатпен жұмыс істейді. ADMIN аккаунт, рөл және аудитті басқарады; бұл бөтен чаттарды оқуға автоматты рұқсат бермейді. Топ мүшелерін тек OWNER басқарады.

```plantuml
@startuml
left to right direction
actor "Қонақ" as Guest
actor "Қызметкер" as Employee
actor "Администратор" as Admin
Admin --|> Employee
rectangle "Қауіпсіз корпоративтік мессенджер" {
 usecase "Тіркелу" as Register
 usecase "Кіру / шығу" as Login
 usecase "Жеке чат ашу" as Direct
 usecase "Топтық чат құру" as Group
 usecase "Хабар жіберу" as Send
 usecase "Хабар тарихын оқу" as History
 usecase "Чат мүшелігін тексеру" as Membership
 usecase "Топ мүшелерін басқару" as Manage
 usecase "Аккаунтты белсендіру / бұғаттау" as Block
 usecase "Рөл тағайындау" as Role
 usecase "Аудит журналын қарау" as Audit
}
Guest -- Register
Guest -- Login
Employee -- Login
Employee -- Direct
Employee -- Group
Employee -- Send
Employee -- History
Employee -- Manage
Send ..> Membership : <<include>>
History ..> Membership : <<include>>
Manage ..> Membership : <<include>>
Admin -- Block
Admin -- Role
Admin -- Audit
note bottom of Manage
Тек топ OWNER-і мүшелерді басқарады.
ADMIN рөлі чат мүшелігін алмастырмайды.
end note
note bottom of Register
Жаңа аккаунт PENDING күйінде.
ADMIN белсендіргеннен кейін чатқа кіреді.
end note
@enduml
```

## Class
User мен Chat арасындағы көп-көп қатынас ChatMember арқылы беріледі. TransportEnvelope — тасымалдау объектісі; Message базада шифрланған мазмұнды сақтайды. Бұл сенімді сервер моделі, E2EE емес.

```plantuml
@startuml
hide circle
skinparam classAttributeIconSize 0
class User {
 +id: UUID
 +username: String
 -passwordHash: String
 +role: Role
 +status: UserStatus
}
enum Role {
 USER
 ADMIN
}
enum UserStatus {
 PENDING
 ACTIVE
 BLOCKED
}
class Chat {
 +id: UUID
 +type: ChatType
 +title: String
 +createdAt: Instant
}
enum ChatType {
 DIRECT
 GROUP
}
class ChatMember {
 +chatId: UUID
 +userId: UUID
 +memberRole: MemberRole
 +joinedAt: Instant
}
enum MemberRole {
 OWNER
 MEMBER
}
class Message {
 +id: UUID
 +clientMessageId: UUID
 +chatId: UUID
 +senderId: UUID
 +ciphertextAndTag: byte[]
 +storageNonce: byte[]
 +storageKeyId: String
 +createdAt: Instant
}
class AuditEvent {
 +id: UUID
 +actorId: UUID
 +action: String
 +targetId: String
 +result: String
 +createdAt: Instant
}
class TransportEnvelope <<DTO>> {
 +version: String
 +clientMessageId: UUID
 +chatId: UUID
 +serverKeyId: String
 +wrappedAesKey: byte[]
 +nonce: byte[]
 +ciphertextAndTag: byte[]
}
class AuthService {
 +register()
 +login()
 +logout()
}
class ChatService {
 +createChat()
 +checkMembership()
 +manageMembers()
}
class MessageService {
 +send(envelope, principal)
 +history(chatId, principal)
}
class CryptoService {
 +encryptTransport()
 +decryptTransport()
 +encryptAtRest()
 +decryptAtRest()
}
class AuditService {
 +record()
}
User "1" -- "0..*" ChatMember
Chat "1" -- "2..*" ChatMember
Chat "1" -- "0..*" Message
User "1" -- "0..*" Message : sends
User "0..1" -- "0..*" AuditEvent : actor
User --> Role
User --> UserStatus
Chat --> ChatType
ChatMember --> MemberRole
MessageService ..> TransportEnvelope
MessageService --> ChatService
MessageService --> CryptoService
MessageService --> AuditService
AuthService --> User
AuthService --> AuditService
note bottom of Message
UNIQUE(senderId, clientMessageId)
Базада тек шифрланған мазмұн.
Storage AAD: messageId, chatId, senderId.
end note
note bottom of Chat
DIRECT: дәл 2 мүше.
GROUP: кемінде 2 мүше, 1 OWNER.
end note
@enduml
```

## Тапсыру реті
1. Әр кодты @startuml-дан @enduml-ға дейін бөлек көшіріп, PlantUML редакторында ашу.
2. Use Case және Class диаграммаларын PNG/SVG түрінде экспорттау.
3. Суреттер мен өңделетін кодты осы карточкаға тіркеу.
4. 1-адаммен талаптар, рөлдер және байланыстарды тексеру.
5. Түзетілген нәтижені Code Review-дан Готово-ға өткізу.

Диаграмма редакторы: https://www.plantuml.com/plantuml/uml/umls
Бұл бастапқы нұсқа: диаграммалар суретке экспортталып, командамен тексерілгеннен кейін ғана тапсырма аяқталды деп белгіленеді.

