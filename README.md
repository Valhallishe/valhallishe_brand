# ValhallisheBrand

Плагин Velocity для сети Valhallishe. Задаёт название сервера в F3 в зависимости
от текущего backend и показывает уведомление о перезагрузке при переходе с backend
на сервер `limbo`.

Импортирована версия **1.1.0**, установленная на прокси Velocity **3.5.1**.

## Сборка

Нужны JDK 21 или новее и Maven 3.9 или новее.

```sh
mvn package
```

Результат: `target/ValhallisheBrand.jar`. Velocity API предоставляется самим прокси
и не включается в JAR. Дескриптор плагина хранится в
`src/main/resources/velocity-plugin.json`.

## Установка

1. Поместите JAR в каталог `plugins` прокси Velocity.
2. Перезапустите прокси.
3. Настройте `plugins/valhallishebrand/config.properties` и перезапустите прокси.

Файл создаётся автоматически. Строки читаются в UTF-8. Отсутствующие параметры
добавляются с настройками по умолчанию.

```properties
default=Valhallishe Survival
auth=Valhallishe Auth
survival=Valhallishe Survival
limbo=Valhallishe Limbo
creative=Valhallishe Survival
restart-title-enabled=true
restart-title-main=<red><bold>СЕРВЕР ПЕРЕЗАГРУЖАЕТСЯ
restart-title-sub=<gray>Пожалуйста, подождите...
restart-title-seconds=3600
```

Ключ с именем backend задаёт его F3 brand; `default` используется для неизвестного
backend. `auth` применяется во время входа, если у игрока ещё нет подключённого
backend. Сообщения заголовка поддерживают MiniMessage.

## Поведение

- При подключении к backend отправляет F3 brand сразу и повторно через секунду.
- При переходе с backend на `limbo` показывает заголовок о перезагрузке.
- При выходе из `limbo` на другой backend очищает заголовок.
- Сам не выполняет авторизацию, не запускает перезагрузки и не переводит игроков
  между серверами.

Уведомление определяется переходом на `limbo`, а не фактическим состоянием
перезапуска backend.
