# [Research 2] RSA+AES, TLS, RBAC және қауіп моделі

Дереккөз: https://trello.com/c/t22wJ2bb

2026-11-20 күнгі Trello материалдарының көшірмесі. Бұл бұрынғы жоспар: ондағы «осы апта» және аяқталу жөніндегі мәтін нақты код күйін растамайды. Іске асырылған мүмкіндіктер README-де көрсетіледі.

## Жауапты және нәтиже
2-адам (Research). Тексеруші: UML 2 орындаушысы.

## Қауіп моделі
Қорғалатын активтер: хабар мазмұны, құпиясөз, session, RSA private key және storage key.
Қауіптер: желіні тыңдау; бөтен чатқа кіру; құпиясөзді болжау; DB көшірмесінің таралуы; өзгертілген ciphertext; сұрауды қайталау.
Сенім шекарасы: клиент → TLS → сенімді сервер → DB. Сервер мазмұнды жадта ашады. Бұл E2EE емес; сервер немесе кілт қоймасы бұзылса, осы модель мазмұнды қорғамайды. Толық E2EE бөлек кілт тарату/тексеру және топ кілттерін ауыстыру дизайнын қажет етеді.

## Ұсынылатын гибридті схема
1. Клиент сервердің RSA public key және keyId мәнін тексерілген HTTPS арқылы алады.
2. Әр жіберу үшін SecureRandom арқылы жаңа AES-256 кілті және 12-byte nonce жасалады.
3. AES/GCM/NoPadding, 128-bit tag; AAD ішінде тұрақты форматпен protocolVersion, chatId, authenticated senderId, clientMessageId байланысады.
4. AES кілті RSA-OAEP арқылы оралады: RSA 3072-bit — жобалық таңдау; SHA-256 және MGF1 SHA-256 параметрлері Java-да анық беріледі. Толық мәтінді RSA-мен шифрламау.
5. Envelope: version, clientMessageId, chatId, serverKeyId, wrappedAesKey, nonce, ciphertextAndTag. Сервер senderId-ді authenticated Principal-дан анықтайды, клиент мәлімдеген тұлғаға сенбейді.
6. Сервер алдымен ACTIVE статус пен мүшелікті тексереді; кейін RSA unwrap және GCM verification орындайды. Қате болса сақтамайды.
7. Базаға жазарда бөлек storage AES key және жаңа nonce қолданылады. Бір кілтпен nonce қайталанбайды; storage keyId, nonce, ciphertext/tag сақталады. Storage AAD: messageId, chatId, senderId.
8. Алушы history/read кезінде session және мүшелік тексеріледі; сервер storage ciphertext-ті ашып, мазмұнды HTTPS арқылы қайтарады. WSS тек хабар идентификаторын жеткізеді.

## Қосымша қорғаныс шешімдері
TLS: HTTPS/WSS; сертификат пен hostname тексеруін өшірмеу. Оқу ортасының CA сертификатын клиент truststore-ына қосу.
Пароль: Argon2id таңдауы ұсынылады; Spring PasswordEncoder арқылы сақтау/тексеру, параметрлерді ресми ұсыныммен және сервер өлшеуімен бекіту. AES пароль сақтау үшін қолданылмайды.
Кілттер: server private key және storage key DB/GitHub-тан бөлек қорғалған key store-да; пароль/кілттер log-қа түспейді; keyId ротацияны қолдайды. Ескі хабарлар үшін ескі storage key қауіпсіз сақталады.
RBAC қана жеткіліксіз: send/read/member-management әр әрекетінде chat membership және owner рұқсаты тексеріледі.
Replay: UNIQUE(senderId, clientMessageId), транзакция; қайталап жібергенде жаңа хабар жасалмайды. ID қайталанып, payload өзгерсе — conflict.
Аудит: login success/failure, account/role/member өзгерісі, access denied, message accepted; actorId, action, targetId, result, timestamp. Мәтін, пароль, session token және кілт жазылмайды.
Brute force: аккаунт/IP бойынша шектеу және уақытша кідіріс; қате login жауабы аккаунттың бар-жоғын ашпайды.

## Тест сценарийлері
Өзгертілген GCM tag қабылданбайды; бөтен чат 403; USER admin әрекеті 403; invalid session 401; BLOCKED аккаунт тоқтайды; duplicate хабар екі рет сақталмайды; дұрыс сертификатсыз TLS қосылмайды; DB-де plaintext жоқ; аудитте құпиялар жоқ. Бұлар келесі спринттегі JUnit/интеграциялық тест жоспарына кіреді, қазір орындалды деп есептелмейді.

## Тексерілген дереккөздер
Oracle JCA (Cipher, SecureRandom, GCM/OAEP параметрлері): https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html
OWASP Cryptographic Storage (AES, authenticated encryption, key separation): https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
OWASP Password Storage (Argon2id және password hashing): https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html

## Аяқтау критерийі
TLS пен payload encryption айырмасы түсіндірілді; E2EE емес екені белгіленді; кілт lifecycle және қауіп шекарасы жазылды; UML sequence осы схемамен сәйкес; бір командалас тексерді.

## Осы аптадағы тәртіп
Бұл — команда тексеріп, өз сөзімен түсіндіріп, бекітетін бастапқы нұсқа. Дайын деп автоматты белгіленбейді.
To Do → In Progress → Code Review → Готово. Осы аптада Code Review бағанында зерттеу мен UML де тексеріледі.
GitHub: https://github.com/ergasaylaubay/SecurityTeam
Код жұмысы басталғанда сәйкес Issue/branch/PR сілтемесін қосу. Құжаттар мен диаграммалар Trello-да сақталады.

