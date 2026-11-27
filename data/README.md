# Demo data / Демонстрациялық деректер

`demo-users.csv` және `demo-chats.csv` — тек оқу және файлмен жұмыс істеу мысалдары үшін жасалған **ойдан шығарылған** деректер. Нақты тіркелгілер, парольдер немесе шифрланбаған жеке хабарламалар жоқ.

**Маңызды:** бұл CSV файлдары сервердің PostgreSQL/H2 базасына автоматты түрде импортталмайды. Нақты тіркелу және чат құру жүйенің API-і арқылы орындалады.

Java CSV оқу үлгісін орындау (жоба түбірінен):

```bat
javac -encoding UTF-8 -d server\target\demo-classes server\src\main\java\kz\securityteam\server\DemoDataReader.java
java -cp server\target\demo-classes kz.securityteam.server.DemoDataReader data\demo-users.csv data\demo-chats.csv
```

Бұл мысал CSV файлдарын оқиды, тақырыптарын тексереді, бос жолдарды өткізеді және жол санын шығарады. Қауіпсіздік үшін CSV мазмұнын дерекқорға автоматты түрде жүктемейді.
