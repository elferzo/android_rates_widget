# PriceWidget — котировки на рабочем столе Android

Виджет с прозрачным фоном, показывает 6 активов с ценой и % изменением за 24ч.

## Активы
| Тикер | Источник данных |
|-------|----------------|
| BTC | CoinGecko (бесплатно) |
| ETH | CoinGecko (бесплатно) |
| SOL | CoinGecko (бесплатно) |
| XAU | Frankfurter API / metals.live |
| Brent | Yahoo Finance (BZ=F) |
| USD/RUB | Frankfurter API |

Обновление: каждые **5 минут** через `AlarmManager.setExactAndAllowWhileIdle`.

---

## Сборка и установка

1. Загрузить все файлы в корень GitHub репозитория
2. Создать `.github/workflows/build_apk.yml`
3. Actions → **Build APK** → Run workflow
4. APK появится в Releases
5. Установить APK на телефон
6. **Открыть приложение Price Widget** → выдать разрешение на точные будильники
7. Долгое нажатие на рабочем столе → Виджеты → **Price Widget** (4×5)

---

## Архитектура

```
PriceWidget.kt     — AppWidgetProvider + AlarmManager + fetch логика
MainActivity.kt    — запрашивает SCHEDULE_EXACT_ALARM (Samsung fix)
BootReceiver.kt    — восстанавливает расписание после перезагрузки
widget_layout.xml  — плоский LinearLayout (без вложений — RemoteViews ограничение)
widget_bg.xml      — полупрозрачный тёмный фон с округлёнными углами
widget_info.xml    — метаданные виджета
```

## Ключевые решения (по опыту DualClockWidget)

- **Плоский layout** — вложенные LinearLayout в RemoteViews не работают
- **AlarmManager вместо Service** — сервисы заблокированы на Android 8+
- **MainActivity** — Samsung One UI требует явного разрешения на точные будильники
- **BootReceiver** — восстанавливает расписание после перезагрузки

## Настройка интервала обновления

В `PriceWidget.kt`, метод `scheduleNext`:
```kotlin
val nextTime = System.currentTimeMillis() + 5 * 60 * 1000L // 5 минут
```
Поменяй `5` на нужное количество минут.

## Замена цвета фона

В `widget_bg.xml`:
- `#CC1A1A2E` — тёмно-синий полупрозрачный (как на скриншоте)
- `#CC000000` — чёрный
- `#00000000` — полностью прозрачный
