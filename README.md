# Better Launcher

Нативный Android-лаунчер на Kotlin для **Android 12+ (API 31)**. Направление проекта — воспроизвести интерфейс и сценарии Niagara под собственным именем Better Launcher. Главный экран работает с настоящими установленными приложениями и может быть назначен системным HOME-лаунчером.

Версия **0.1.9** реализует вертикальное избранное, алфавитную «волну», поиск, меню приложений, уведомления, папки, один виджет на главном экране и отдельную страницу виджетов слева; при выборе виджета доступны превью и алфавит справа. Полный паритет Niagara и Niagara Pro ещё не достигнут; реализованные возможности и пробелы перечислены в [docs/features.md](docs/features.md).

[Репозиторий](https://github.com/better-launcher/better-launcher) · [Релиз 0.1.9 и APK](https://github.com/better-launcher/better-launcher/releases/tag/v0.1.9) · [Релиз-кандидат 0.2.0-rc.1](https://github.com/better-launcher/better-launcher/releases/tag/v0.2.0-rc.1). Репозиторий приватный: для скачивания нужен доступ к организации.

## Документация проекта

| Документ | Когда читать |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Перед работой ИИ-агента: порядок действий, инварианты и выбор проверок |
| [PRODUCT.md](PRODUCT.md) | Требования продукта, выбранная платформа и направление развития |
| [DESIGN.md](DESIGN.md) | Изменения интерфейса, размеров, цветов, типографики и поведения при масштабировании |
| [docs/home-surface.md](docs/home-surface.md) | Компоновка и основные взаимодействия главного экрана |
| [docs/features.md](docs/features.md) | Текущее покрытие возможностей и оставшаяся работа |
| [docs/releases/0.1.8.md](docs/releases/0.1.8.md) | Исправление нижней границы алфавитного ползунка |
| [docs/releases/0.1.9.md](docs/releases/0.1.9.md) | Превью виджетов, алфавит выбора и результаты проверки |
| [docs/releases/0.2.0-rc.4.md](docs/releases/0.2.0-rc.4.md) | Компактный алфавит, release APK и проверки нового релиз-кандидата |
| [docs/releases/0.1.7.md](docs/releases/0.1.7.md) | Уменьшенный release APK и результаты проверки |
| [docs/verification-0.1.3.md](docs/verification-0.1.3.md) | Проверки латинского алфавита и транслитерации |
| [docs/releases/0.1.3.md](docs/releases/0.1.3.md) | Латинский алфавит, транслитерация и установка |
| [docs/verification.md](docs/verification.md) | Проверки версии 0.1.0 и границы подтверждённой совместимости |
| [docs/app-navigation-verification.md](docs/app-navigation-verification.md) | Диагностика задержки списка и проверки оптимизации |
| [docs/releases/0.1.5.md](docs/releases/0.1.5.md) | Меню удержания по образцу Niagara |
| [docs/app-menu-verification.md](docs/app-menu-verification.md) | Проверки меню и пакета 0.1.5 |
| [docs/releases/0.1.4.md](docs/releases/0.1.4.md) | Регулировка затемнения обоев и проверки патча |
| [docs/widgets-verification.md](docs/widgets-verification.md) | Проверки экрана виджетов |
| [docs/verification-0.2.0-rc.1.md](docs/verification-0.2.0-rc.1.md) | Проверки итогового APK релиз-кандидата |
| [docs/releases/0.2.0-rc.1.md](docs/releases/0.2.0-rc.1.md) | Экран виджетов в минорном релиз-кандидате |
| [docs/releases/0.1.2.md](docs/releases/0.1.2.md) | Патч плавности списка приложений и алфавита |
| [docs/releases/0.1.1.md](docs/releases/0.1.1.md) | Изменения и проверки патч-релиза |
| [docs/releases/0.1.0.md](docs/releases/0.1.0.md) | Состав первого выпуска и инструкция установки |

## Стек и окружение

Проект состоит из одного Android-модуля `app`. Интерфейс создаётся программно из нативных Views; настройки используют Material 3. Основная логика находится в пакете `app.better.launcher`. Отдельного сервера, учётных записей и инфраструктуры развёртывания нет.

| Параметр | Текущее значение | Источник |
| --- | --- | --- |
| Минимальная версия Android | API 31 / Android 12 | [app/build.gradle.kts](app/build.gradle.kts) |
| Compile SDK / Target SDK | 36 / 36 | [app/build.gradle.kts](app/build.gradle.kts) |
| Application ID / namespace | `app.better.launcher` | [app/build.gradle.kts](app/build.gradle.kts) |
| Kotlin | 2.2.21 | [build.gradle.kts](build.gradle.kts) |
| Android Gradle Plugin | 8.13.2 | [build.gradle.kts](build.gradle.kts) |
| Gradle | 8.13, через wrapper | [gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties) |
| JVM target | 17; для сборки подходят JDK 17 и 21 | [app/build.gradle.kts](app/build.gradle.kts) |
| Android ICU | Системная библиотека Android; Russian-Latin/BGN, без отдельного JAR в APK | [AppListRules.kt](app/src/main/java/app/better/launcher/AppListRules.kt) |
| Material Components | 1.13.0 | [app/build.gradle.kts](app/build.gradle.kts) |

Нужны Android SDK Platform 36, Build Tools 35.0.0 и Platform Tools с `adb`. Для интеграционных проверок нужен телефон или Android Emulator; для UI-скрипта также Python 3. Android Studio можно использовать для открытия корня проекта, настройки Gradle JDK и установки SDK через SDK Manager. Версии инструментов задаются Gradle-файлами; при их обновлении обновляйте эту таблицу.

## Получение кода и первая сборка

С авторизованным GitHub CLI:

```sh
gh repo clone better-launcher/better-launcher
cd better-launcher
```

Создайте в корне `local.properties` с абсолютным путём к своему Android SDK. Android Studio также может создать этот файл:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

Проверьте JDK и соберите APK:

```sh
java -version
./gradlew :app:assembleDebug
```

Результат: `app/build/outputs/apk/debug/app-debug.apk`. Первый запуск Gradle загружает wrapper и зависимости. Для Windows используйте `gradlew.bat`; остальные примеры команд ниже рассчитаны на POSIX-оболочку.

`local.properties`, каталоги сборки и локальные APK исключены из Git. Пути SDK, ключи подписи, токены и пользовательские резервные копии храните вне отслеживаемых файлов проекта.

## Установка и использование

Можно установить APK из релиза вручную либо через `adb`. Если подключено несколько устройств, указывайте serial явно:

```sh
adb devices
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 shell am start -n app.better.launcher/.MainActivity
```

`emulator-5580` — пример; замените его на serial своего устройства. После запуска откройте настройки Better Launcher → «Домашний экран» → выберите Better Launcher в системном диалоге Android. Для возврата к другому лаунчеру используйте системные настройки приложения по умолчанию для домашнего экрана.

| Действие | Результат |
| --- | --- |
| Нажать на приложение | Открыть приложение |
| Удерживать приложение | Избранное, название, скрытие с отменой, сведения, удаление |
| Провести по приложению в сторону | Уведомления, быстрые действия и прикреплённые приложения |
| Провести по алфавиту | Перейти к разделу списка всех приложений |
| Нажать на звёздочку в алфавите | Вернуться к избранному |
| «Все приложения» | Поиск; Enter открывает первый результат |
| Настройки → «Календарь» | Ближайшие события календаря |
| Провести вправо по свободному месту | Экран виджетов слева |
| Провести влево на экране виджетов | Вернуться на главный экран |
| Кнопка виджетов внизу | Открыть экран виджетов без жеста |
| Удерживать свободное место | Настройки |

Главный экран всегда использует системные обои Android. Пункт «Обои» в настройках сразу открывает системный выбор обоев; ползунок «Затемнение обоев» регулирует его от 0 до 100% с шагом 1%. Значение по умолчанию — 20%, настройка сохраняется сразу. На светлых обоях можно увеличить затемнение для читаемости текста. Прежний выбор встроенных обоев, в том числе из резервных копий формата 1, больше не влияет на фон.

Меню долгого удержания показывает значок и название приложения, контурные значки команд и переход в настройки Better Launcher под разделителем. Нажатие на название открывает переименование; прикреплённые приложения и скрытие находятся в раскрывающемся разделе «Дополнительно». Меню появляется снизу за 300 мс и закрывается за 180 мс; системное отключение анимаций учитывается. Меню закрывается кнопкой «Назад», нажатием снаружи или HOME.

На главном экране нет отдельных часов и даты: время отображается в статус-баре Android.

В настройках редактируются избранное, порядок и папки. На главном экране можно разместить один виджет; остальные находятся в вертикальном списке на отдельном экране слева. Добавляйте их кнопкой «+» этого экрана, а главный — через «Виджет на главном экране» в настройках. Меню каждого виджета позволяет перенести его между экранами или удалить; новый главный виджет переносит прежний на левый экран. Системная привязка и внешний экран настройки провайдера сохраняются. При обновлении прежней стопки первый виджет остаётся на главном, остальные переходят налево.

## Архитектура и жизненный цикл

Исходники находятся в [app/src/main/java/app/better/launcher](app/src/main/java/app/better/launcher).

| Файл | Ответственность |
| --- | --- |
| [MainActivity.kt](app/src/main/java/app/better/launcher/MainActivity.kt) | Главный экран, экран виджетов, список приложений, настройки, диалоги, разрешения, календарь и MediaSession; координация фоновых задач |
| [AppCatalog.kt](app/src/main/java/app/better/launcher/AppCatalog.kt) | Каталог через LauncherApps по доступным профилям, реальные значки, запуск, shortcuts, первичное избранное |
| [AppSearchIndex.kt](app/src/main/java/app/better/launcher/AppSearchIndex.kt) | Подготовленные названия и порядок каталога; фильтрация без повторной сортировки |
| [AppListAdapter.kt](app/src/main/java/app/better/launcher/AppListAdapter.kt) | Видимые строки списка приложений, переиспользование Views и позиции алфавитных разделов |
| [AppListRules.kt](app/src/main/java/app/better/launcher/AppListRules.kt) | Правила поиска, нормализации и избранного; алфавитные разделы через системную Android ICU |
| [LauncherStore.kt](app/src/main/java/app/better/launcher/LauncherStore.kt) | SharedPreferences, папки, aliases, скрытые приложения, JSON-экспорт и проверяемый импорт |
| [PageSwipeLayout.kt](app/src/main/java/app/better/launcher/PageSwipeLayout.kt) | Свайпы между главным экраном и виджетами без перехвата жестов приложений, виджетов и системного Back |
| [LauncherViews.kt](app/src/main/java/app/better/launcher/LauncherViews.kt) | Затемнение системных обоев, GlyphView, AlphabetRail, вспомогательные функции размеров и фона |
| [LauncherNotificationService.kt](app/src/main/java/app/better/launcher/LauncherNotificationService.kt) | NotificationListenerService и общий NotificationHub в памяти |
| [WidgetController.kt](app/src/main/java/app/better/launcher/WidgetController.kt) | AppWidgetHost, привязка, настройка, размещение и удаление виджетов |
| [WeatherRepository.kt](app/src/main/java/app/better/launcher/WeatherRepository.kt) | Поиск города и текущая погода через Open-Meteo |

Тема и цветовые роли заданы в [styles.xml](app/src/main/res/values/styles.xml), компоненты и разрешения — в [AndroidManifest.xml](app/src/main/AndroidManifest.xml). [Иконка приложения](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) адаптивная. `.impeccable/design.json` содержит вспомогательное представление дизайн-системы; его HTML/CSS-примеры иллюстрируют нативные компоненты, а Android-интерфейс определяется Kotlin и ресурсами.

`MainActivity` имеет `singleTask`. Новый HOME-intent закрывает диалог и клавиатуру и возвращает главный экран. Каталог обновляется по callbacks LauncherApps и при возобновлении Activity. Загрузка каталога, правил транслитерации Android ICU, календаря, погоды и файлов выполняется в executor; изменения Views возвращаются через main-thread Handler. Каталог заранее подготавливает индекс поиска в фоновой загрузке; переименование, скрытие и импорт настроек обновляют индекс при следующем обращении. Список приложений использует `ListView`: создаются только видимые строки, а переход по алфавиту выбирает позицию раздела без измерения всего каталога. Движение внутри одной буквы обновляет волну без повторных команд навигации. Отложенный запрос клавиатуры отменяется при выходе из поиска или выборе буквы. Подписки, таймер и executor освобождаются в соответствующих lifecycle callbacks.

AppWidgetHost слушает обновления между `onStart` и `onStop`. Обновление уведомлений при открытом диалоге откладывает перестроение главного экрана до закрытия диалога, сохраняя прокрутку. Эти сценарии важны при изменениях навигации и фонового обновления.

Алфавит содержит ★ и только непустые разделы приложений: латинские буквы A–Z и заключительный «#», если для него есть приложения. Кириллические названия группируются по первой латинской букве транслитерации Android ICU Russian-Latin/BGN: Яндекс → Yandeks → Y, Банк → Bank → B. Цифры, символы и начальные буквы без латинской транслитерации (например, Ь и Ъ) относятся к «#». Ряд прижат к нижней части экрана с отступом 8 dp над местом нижней панели; шаг букв — 20 dp при обычном масштабе шрифта, растёт с системным шрифтом и уменьшается при нехватке высоты. Положение сохраняется при переходе между избранным и списком приложений; system/IME insets и край жеста «Назад» учитываются.

## Хранение данных и интеграции

Настройки находятся в приватном SharedPreferences-файле `launcher`. `AppEntry.id` имеет формат `<serial Android-профиля>:<ComponentName>`: один package name не идентифицирует приложение между профилями или несколькими launcher activities.

| Данные | Представление в LauncherStore |
| --- | --- |
| Избранное | Упорядоченный список app/folder IDs в `favorites` |
| Скрытые приложения | Набор IDs в `hidden` |
| Названия | Ключи `alias:<app ID>` |
| Папки | Ключи `folder:<UUID>`, JSON с `name` и `apps` |
| Прикреплённые приложения | Ключи `popup:<app ID>` |
| Виджеты | Локальные IDs в `widgets`, единственный главный — `homeWidgetId`; незавершённая привязка — `pendingWidgetId` и `pendingWidgetDestination` |
| Оформление и погода | Типизированные параметры, выбранный город и его координаты; `wallpaperDimming` — целое число 0–100 |

Прежний boolean `dimWallpaper` принимается при импорте: `true` соответствует 20%, `false` — 0%; явный `wallpaperDimming` имеет приоритет. Некорректные типы и значения процента вне 0–100 отклоняются до изменения настроек.

Экспорт содержит `version: 1` и объект `preferences`. Это **версия формата резервной копии**, независимая от версии приложения. Старые параметры `clock24` и `smallClock` по-прежнему принимаются при импорте резервных копий 0.1.0, но больше не влияют на интерфейс. Импорт полностью проверяет допустимые ключи, типы и вложенный JSON до изменения настроек; повреждённый файл должен оставить предыдущие данные целыми. Лимит чтения — 1 000 000 символов. Экспорт исключает локальные IDs, выбор главного виджета и незавершённую привязку; импорт сохраняет эти данные устройства. Резервные копии прежней версии остаются совместимыми. Системное резервное копирование отключено в manifest и [data_extraction_rules.xml](app/src/main/res/xml/data_extraction_rules.xml); пользовательский экспорт/импорт работает через системный выбор файла.

| Интеграция | Доступ и поведение |
| --- | --- |
| HOME | ROLE_HOME выбирается пользователем в системном диалоге |
| Уведомления | Отдельный доступ к NotificationListenerService в настройках Android; фильтрация по package и UserHandle |
| Музыка | Активные MediaSession доступны через включённый доступ к уведомлениям |
| Календарь | READ_CALENDAR запрашивается при открытии событий; до 12 событий за ближайшие 7 дней |
| Виджеты | Системное разрешение привязки и настройка конкретного провайдера |
| Удаление приложения | REQUEST_DELETE_PACKAGES открывает системное подтверждение удаления |
| Погода | INTERNET; пользователь выбирает город, геолокация не запрашивается |

Содержимое уведомлений находится только в NotificationHub в памяти, не сохраняется и не передаётся по сети. События календаря читаются на устройстве. Сеть в текущем приложении используется для запросов города и прогноза на `geocoding-api.open-meteo.com` и `api.open-meteo.com`. Погодные запросы имеют connect/read timeouts по 8 секунд; прогноз обновляется с интервалом 30 минут или по явному запросу. Для работы с Open-Meteo в текущей реализации API-ключ не нужен.

Выбор виджета показывает название, приложение и статичное превью провайдера: `previewLayout` имеет приоритет, затем используется `previewImage`. Если оба недоступны, строка явно сообщает «Превью недоступно». Нажатие на строку запускает прежнюю системную привязку/конфигурацию. Алфавит справа использует тот же контроль, что список приложений, включая кириллическую транслитерацию, заключительный «#», клавиатуру и accessibility; звёздочка возвращает к началу списка. Каталог и ресурсы превью загружаются в фоне, строки переиспользуются, закрытие отменяет загрузки. Для списка виджетов не выделяются widget IDs. За превью отвечает [WidgetPickerView](app/src/main/java/app/better/launcher/WidgetPickerView.kt), за размещение — [WidgetController](app/src/main/java/app/better/launcher/WidgetController.kt).

## Проверки

Для изменений Kotlin, ресурсов или конфигурации Android выполняйте:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

| Проверка | Покрытие и отчёт |
| --- | --- |
| [AppListRulesTest](app/src/test/java/app/better/launcher/AppListRulesTest.kt) | Нормализация латиницы/кириллицы, поиск, порядок разделов и перемещение избранного; `app/build/reports/tests/testDebugUnitTest/index.html` |
| [LauncherIntegrationTest](app/src/androidTest/java/app/better/launcher/LauncherIntegrationTest.kt) | Сохранение, папки, backup, атомарный отказ импорта, реальный каталог и aliases/hidden; `app/build/reports/androidTests/connected/debug/index.html` |
| [SystemTransliterationTest](app/src/androidTest/java/app/better/launcher/SystemTransliterationTest.kt) | Системные правила Russian-Latin/BGN, Яндекс/Y, Й, порядок разделов и поиск по aliases |
| [AppSearchIndexTest](app/src/test/java/app/better/launcher/AppSearchIndexTest.kt) | Подготовленный поиск по aliases/исходным названиям и отсутствие повторного чтения названий |
| [AppNavigationTest](app/src/androidTest/java/app/better/launcher/AppNavigationTest.kt) | Непустые разделы, компактный алфавит с доступным нижним разделом, свободным краем Back и управлением с клавиатуры; отсутствие повторных переходов внутри буквы, ограничение создаваемых строк, переиспользование и переход к последнему выбранному разделу |
| [WidgetPickerTest](app/src/androidTest/java/app/better/launcher/WidgetPickerTest.kt) | Превью layout/image, отсутствие и повреждение превью, алфавит, выбор провайдера и отсутствие привязки при просмотре |
| [LauncherNavigationTest](app/src/androidTest/java/app/better/launcher/LauncherNavigationTest.kt) | Кнопки экрана виджетов, отмена выбора, Back и HOME; стабильное положение алфавита между избранным и списком приложений |
| Android lint | `app/build/reports/lint-results-debug.html` |
| [smoke-test.py](scripts/smoke-test.py) | Отсутствие встроенных часов и даты, UI-сценарии, включая экран виджетов, через adb и отсутствие аварий процесса лаунчера |

### Интеграционные тесты

Запускайте на выделенном тестовом устройстве. Gradle устанавливает тестируемый APK и test APK, а по завершении удаляет их; для продолжения UI-проверок установите основной APK заново. Тесты временно заменяют SharedPreferences и восстанавливают их в `finally`, но выделенный эмулятор всё равно нужен из-за установки/удаления приложений.

```sh
./gradlew :app:connectedDebugAndroidTest
```

### Проверка интерфейса

Используйте эмулятор Google APIs с английским системным языком, установленными Camera и Phone, исходным избранным и алфавитом справа. На одном устройстве запускайте один UI-скрипт за раз. Лог AndroidRuntime проверяется целиком; старую аварию сначала разберите, затем начните отдельный запуск с очищенным логом.

```sh
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 shell cmd package set-home-activity app.better.launcher/.MainActivity
adb -s emulator-5580 logcat -c
python3 scripts/smoke-test.py --serial emulator-5580
```

Команда назначения HOME выше предназначена для тестового эмулятора. Скрипт проверяет отсутствие встроенных часов/даты, настройки/Back, поиск, запуск Camera/HOME, свайп меню, долгое нажатие, алфавит и переходы на экран виджетов; он не проверяет полный набор интеграций. Для UI-изменений отдельно проверяйте портрет, узкий экран с font scale 1.3, горизонтальную ориентацию, клавиатуру и системный Back. Снимки текущей проверки храните в локальной `.impeccable/review/`.

Проверки рабочей правки компактного алфавита и ограничения UI-проверки записаны в [отчёте](docs/compact-alphabet-verification.md).

Результаты проверок 0.1.9 записаны в [release notes](docs/releases/0.1.9.md). Результаты проверок 0.1.8 записаны в [release notes](docs/releases/0.1.8.md). Результаты проверок 0.1.7 записаны в [release notes](docs/releases/0.1.7.md). Результаты проверок 0.1.5 записаны в [проверке меню](docs/app-menu-verification.md) и [release notes](docs/releases/0.1.5.md). Результаты проверок 0.1.4 записаны в [release notes](docs/releases/0.1.4.md). Результаты проверок 0.1.3 записаны в [отчёте](docs/verification-0.1.3.md). Результаты текущей проверки экрана виджетов записаны в [отчёте](docs/widgets-verification.md). Результаты проверок 0.1.2 записаны в [проверке плавности](docs/app-navigation-verification.md) и [release notes](docs/releases/0.1.2.md). Результаты проверок 0.1.1 записаны в [release notes](docs/releases/0.1.1.md).

Для 0.1.0 подтверждены 3 unit-теста, 3 интеграционных теста, UI-сценарии на Android 12 и Android 16 и отдельные сценарии уведомлений/виджета Clock на Android 16. Lint того выпуска: 0 ошибок, 35 предупреждений. Проверки физических устройств, Android 13–15, рабочих профилей, TalkBack и остальных провайдеров ещё не завершены. Полный список границ — в [отчёте](docs/verification.md). CI workflow в репозитории пока отсутствует.

### Если что-то не работает

| Симптом | Что проверить |
| --- | --- |
| SDK location not found / отсутствующая платформа | `local.properties`, Platform 36 и Build Tools в SDK Manager |
| Ошибка версии Java | `java -version`, Gradle JDK в Android Studio; JDK 17 или 21 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Сертификаты двух APK различаются; соберите обновление тем же ключом. Удаление приложения сбрасывает его данные, поэтому сначала экспортируйте настройки |
| HOME открывает другой лаунчер | Системное приложение по умолчанию для домашнего экрана; на тестовом эмуляторе — команда назначения HOME выше |
| Нет уведомлений или управления музыкой | Включён ли доступ к уведомлениям, есть ли активное уведомление/MediaSession |
| Нет погоды или событий | Выбран ли город, доступен ли интернет; выдан ли READ_CALENDAR и есть ли события в интервале 7 дней |
| UI-скрипт не находит Camera/Phone | Язык и состав эмулятора, начальное избранное, сторона алфавита, установка APK после connected-тестов |

Для диагностики процесса:

```sh
adb -s emulator-5580 logcat -d -s AndroidRuntime:E
```

## Git и выпуски

Основная ветка — `main`, основной remote — `origin` (`https://github.com/better-launcher/better-launcher.git`). В исходной рабочей копии также есть `personal`, указывающий на стартовый репозиторий; он не является целью публикации Better Launcher. Перед коммитом и пушем сверяйте ветку, remote и состав изменений.

Версия Android задаётся `versionName` и возрастающим `versionCode` в `app/build.gradle.kts`. Стабильный выпуск 0.1.9 соответствует `v0.1.9`; main может содержать следующую RC-версию. В GitHub Release публикуются `better-launcher-0.1.9-release.apk` и `SHA256SUMS.txt`. Debug APK используются только локально. Release включает R8 и удаление неиспользуемых ресурсов; manifest должен иметь `debuggable=false`.

Подпись настраивается через `BETTER_LAUNCHER_SIGNING_PROPERTIES`: абсолютный путь к приватному Java properties-файлу вне Git. Поля: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. `storeFile` задаёт абсолютный путь к keystore. Файл свойств и keystore храните с доступом только владельцу; значения паролей не выводите в логи. Без этих данных release-сборка не подписывается и не проходит проверку подписи Gradle.

Для совместимости обновлений 0.1.9 использует прежний сертификат первых APK. Это подписанная release-сборка; прежний сертификат Android Debug пока сохранён. Переход на отдельный production-сертификат и выпуск в магазине не настроены.

Для нового выпуска обновите версию, выполните базовые проверки, connected-тесты системной транслитерации и проверьте подписанный release APK на устройстве. Сравните размер и содержимое с предыдущим APK. Подготовьте `docs/releases/X.Y.Z.md`, указав фактические результаты и ограничения. Используйте путь к своему properties-файлу:

```sh
export BETTER_LAUNCHER_SIGNING_PROPERTIES=/absolute/private/path/release-signing.properties
./gradlew :app:assembleRelease :app:lintRelease
mkdir -p artifacts
cp app/build/outputs/apk/release/app-release.apk artifacts/better-launcher-X.Y.Z-release.apk
(cd artifacts && shasum -a 256 better-launcher-X.Y.Z-release.apk > SHA256SUMS.txt)
# Перед тегом: apksigner verify, aapt dump badging/xmltree, установка и UI-проверка.
git tag -a vX.Y.Z -m "Better Launcher X.Y.Z"
git push origin main
git push origin vX.Y.Z
gh release create vX.Y.Z artifacts/better-launcher-X.Y.Z-release.apk artifacts/SHA256SUMS.txt \
  --repo better-launcher/better-launcher --title "Better Launcher X.Y.Z" \
  --notes-file docs/releases/X.Y.Z.md --verify-tag
```

Проверьте соответствие удалённого commit/tag локальному исходнику, скачайте опубликованные APK и SHA-256 и сравните их с локальными файлами. Выпуски 0.1.0–0.1.5 сохраняются как история; для обновлений публикуйте новый release APK.

Для релиз-кандидата используйте суффикс `-rc.N` в версии и теге, а к `gh release create` добавьте `--prerelease --latest=false`, чтобы сохранить стабильный выпуск последним.

Интерфейс опирается на [официальное описание Niagara](https://niagaralauncher.com/) и [описание всплывающих меню](https://help.niagaralauncher.app/article/115-pop-ups). Код лаунчера и пиктограммы созданы для Better Launcher; фон главного экрана берётся из системных обоев Android. Отдельный файл LICENSE для проекта пока не добавлен.
