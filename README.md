# Q50 Monitor

Aplikacja na fabryczny system **InTouch** w Infiniti Q50 (V37, MY2014–2019), instalowana przez **App Garage** z pendrive'a. Czyta dane z auta, które InTouch udostępnia aplikacjom jako sensory Androida – bez adaptera OBD i bez telefonu.

Plan i analiza: [`docs/PLAN.md`](docs/PLAN.md).

## Wersja 0.1 – Eksplorator sensorów

| Zakładka | Co pokazuje |
|---|---|
| **PRZEGLĄD** | Obroty, prędkość, bieg, przepustnica, temp. płynu i oleju, ciśnienie oleju, moment, moc, przeciążenia, ciśnienie w 4 oponach, z wartościami max |
| **SENSORY** | Wszystkie sensory radia: typ, nazwa, wartości `v0–v2`, częstotliwość, zakres min…max. Dotknięcie wiersza pokazuje wykres z ostatnich 20 s. Filtr: pojazd / zmienne / wszystkie |
| **LOG** | Zapis CSV (10 wierszy/s, wszystkie sensory), przycisk MARKER, wybór miejsca zapisu (pamięć wewnętrzna / pendrive) |

Zmapowane sygnały (typ sensora → nazwa, skala) są w [`app/assets/sensor-map.json`](app/assets/sensor-map.json). Pochodzą z ustaleń społeczności na tym samym silniku (VR30DDTT) i **nie są jeszcze zweryfikowane na naszym aucie** – do tego służą Eksplorator i logi.

Bez sensorów pojazdu (np. w emulatorze) aplikacja przechodzi w tryb **DEMO** z wyraźnym oznaczeniem.

## Instalacja w aucie

1. Pendrive sformatowany jako **FAT32**, plik `q50-monitor.epk` w **katalogu głównym**.
2. Silnik włączony, odczekać aż InTouch skończy „Loading all apps”.
3. Dolny ekran: **APPS → App Garage → Install Apps via USB** → wybrać *Q50 Monitor* → **Install**.
4. Nie wyjmować pendrive'a w trakcie instalacji.

Aktualizacja: nowszy `.epk` instaluje się na starszy tylko wtedy, gdy jest podpisany **tym samym kluczem**. Jeśli App Garage nie pokazuje nowej wersji – odinstalować starą i zainstalować ponownie.

## Budowanie

Wymagania: JDK 17+, Node 22.18+, oraz `aapt`, `zipalign`, `apksigner`, `dx`/`d8`
(Ubuntu: `sudo apt-get install aapt zipalign apksigner dalvik-exchange`).

```bash
npm ci
npm test                    # testy narzędzia .epk
bash scripts/test-java.sh   # testy logiki aplikacji (JVM)
bash scripts/build-apk.sh   # -> build/q50-monitor.apk + build/q50-monitor.epk
```

Pierwsze budowanie tworzy klucz podpisu `signing/q50.keystore` (poza gitem). **Zachowaj go** – bez niego nie da się zaktualizować zainstalowanej aplikacji.

GitHub Actions buduje `.epk` przy każdym pushu (artefakt *q50-monitor-N*). Aby buildy z CI aktualizowały zainstalowaną aplikację, dodaj sekrety repozytorium:
- `APK_KEYSTORE_B64` – `base64 -w0 signing/q50.keystore`
- `APK_KEYSTORE_PASS` – hasło keystore (domyślnie `android`)

### Ograniczenia platformy
- Android 2.3.7 (API 10), x86, Dalvik: Java 8 bez lambd i bez API spoza Androida 2.3, żadnych bibliotek ani kodu natywnego.
- Podpis APK v1 (JAR).
- `.epk` = APK zaszyfrowany AES-256-CBC, klucz zaszyfrowany publicznym certyfikatem OBU z firmware ([`keys/`](keys/README.md)). Narzędzie: `node tools/epk.ts build|inspect`.

## Podziękowania

Format `.epk`, certyfikat OBU i mapa sensorów pochodzą z pracy społeczności:
[bugjosh/appgarage-dash](https://github.com/bugjosh/appgarage-dash) (MIT),
[oneezeeroo/Q50-Reverse-Engineering](https://github.com/oneezeeroo/Q50-Reverse-Engineering) (MIT).

Projekt prywatny, niezwiązany z Infiniti/Nissan. Używaj tylko we własnym aucie; nie patrz na ekran zamiast na drogę.
