# Q50 App – analiza i plan pracy

Aplikacja instalowana na **fabrycznym systemie InTouch** (App Garage, pakiet `.epk` z pendrive'a) w Infiniti Q50 (V37) **MY2018** z silnikiem **3.0t VR30DDTT (305 KM, bez modyfikacji)**, wyświetlająca jak najwięcej danych o samochodzie – bez adaptera OBD i bez telefonu.

---

## 1. Platforma – co wiemy o InTouch

### 1.1 System
Źródła: projekty społeczności [oneezeeroo/Q50-Reverse-Engineering](https://github.com/oneezeeroo/Q50-Reverse-Engineering), [bugjosh/appgarage-dash](https://github.com/bugjosh/appgarage-dash) (działająca aplikacja zweryfikowana na Q60 Red Sport 400 2018, VR30DDTT), [doudineugene-dot/Q50-gtr-](https://github.com/doudineugene-dot/Q50-gtr-) oraz analiza dostarczonego pliku `gtr.epk`.

| Cecha | Wartość |
|---|---|
| Roczniki z App Garage | Q50 MY2014–2019, Q60 (V37) MY2017–2019. **MY2020+ nie ma App Garage.** |
| System aplikacji | Zmodyfikowany **Android 2.3.7 (API 10)**, CPU **x86** (Intel Atom), działa obok linuksowej części systemu (nawigacja, „carwings”) |
| Ekrany | dwa, ok. **800×480**; aplikacje domyślnie na dolnym (`ivi.defaultDisplay=LOWER`) |
| Instalacja | pendrive **FAT32**, plik `.epk` w katalogu głównym → *APPS → App Garage → Install Apps via USB* |
| Aktualizacja | nowa wersja musi mieć wyższy `versionCode` i ten sam klucz podpisu APK |

### 1.2 Format `.epk` (zweryfikowany na `gtr.epk`)

```
0x000  ".epk"            magic
0x004  0x0002            wersja EPK (v2)
0x006  0x0002            typ payloadu (2 = APK w znanych pakietach)
0x008  0x0001            liczba bloków
0x00A  0x0080            długość klucza (128 B = RSA-1024)
0x00C  256 B             klucz AES zaszyfrowany RSA (wykorzystane 128 B)
0x10C  128 B             nazwa pliku: "gtr.apk"
0x18C  4 B (BE)          długość danych: 2 156 512
0x190  …                 APK zaszyfrowany AES-CBC (długość podzielna przez 16, entropia ≈ 8 bit/B)
```

**Najważniejsze:** EPK to tylko **szyfrowanie kluczem publicznym** – do zbudowania pakietu wystarcza **publiczny certyfikat OBU** z firmware (`CN=tt18002`, wystawca `IT5.YGOMI.COM CA`, RSA-1024), który jest publicznie dostępny w projektach społeczności. Prywatnego klucza nie potrzebujemy. Sam APK jest podpisany zwykłym, własnym kluczem (podpis v1/JAR).

### 1.3 Skąd aplikacja bierze dane z auta
InTouch udostępnia sygnały z magistrali CAN jako **standardowe sensory Androida** (`SensorManager`, vendor „Ygomi”, nazwy `VS_ID_*`, typy **12–53**). Wymagane uprawnienie `com.ygomi.permission.IVI_CAN_READ` (przyznawane automatycznie). Manifest z `ivi.isDistractive="false"` pozwala używać aplikacji w czasie jazdy.

Według appgarage-dash na żywo jest **~42 sygnały**. Zmapowane dotąd:

| Typ | Sygnał | Skala (VR30) |
|---:|---|---|
| 12 | Moment obrotowy | ≈ Nm |
| 13 | Obroty silnika | bezpośrednio |
| 14 | Temp. płynu chłodzącego | °C |
| 15 | Temp. oleju | °C |
| 16 | **Ciśnienie oleju** | MPa (×10 = bar) |
| 17 | Prędkość | km/h |
| 20 / 21 | Przeciążenie boczne / wzdłużne | ~g |
| 22 | Bieg | P=1, R=2, N=3, D=4, M1–M7 = 16–22 |
| 23 | Przepustnica | % |
| 32 | Moc | raw × 1,047e-4 = kW |
| 36–39 | Ciśnienie w oponach PP, LP, PT, LT | psi |

**Niedostępne** z CAN radia (wg appgarage-dash): doładowanie/MAP, AFR/lambda, knock, kąt zapłonu – to dane ze sterownika silnika, które czytają narzędzia typu EcuTek. Komercyjna aplikacja „Red Sport” (vqapps) reklamuje jednak wskaźnik doładowania z funkcją „AUTO FIND” – do zbadania, z czego go wylicza.

**~27 typów sensorów pozostaje niezmapowanych** – to nasze pole do odkrycia (etap 2).

### 1.4 Konsekwencje techniczne

- **React Native / nowoczesny Android odpadają** – API 10, Dalvik, brak Gradle (AGP nie wspiera tak niskiego `minSdk`).
- Sprawdzona ścieżka budowania: `javac --release 7/8` → `d8 --min-api 10` → `aapt` → `zipalign` → `apksigner` (tylko v1) → pakowanie do `.epk`.
- UI rysowane ręcznie na `Canvas` (bez AndroidX, bez bibliotek) – lekko i płynnie na Atomie.
- **Gdzie TypeScript:** WebView w Androidzie 2.3 to bardzo stary WebKit (bez `requestAnimationFrame`, wolny canvas) – płynne zegary w TS w aucie są ryzykowne. TypeScript ma sens w: narzędziu do budowania `.epk` (Node `crypto`), symulatorze danych, aplikacji webowej do analizy logów zgranych z auta. Ewentualny wariant „Java + WebView z TS (ES5)” do sprawdzenia testem wydajności w etapie 1.

---

## 2. Co aplikacja może wyświetlać

Oznaczenia: **[N]** – natywny sensor InTouch (potwierdzony), **[?]** – możliwy wśród niezmapowanych sensorów (do zbadania), **[O]** – obliczane, **[X]** – niedostępne bez zewnętrznego źródła.

### 2.1 Silnik (na żywo)
- Obroty, moment, moc (kW/KM) **[N]**
- Temperatura płynu i **oleju** **[N]**
- **Ciśnienie oleju** **[N]** – rzadkość, fabrycznie niewidoczne na zegarach
- Przepustnica **[N]**
- Temp. ATF, napięcie instalacji, temp. zewnętrzna, poziom paliwa, chwilowe zużycie paliwa **[?]**
- Doładowanie, AFR, knock, kąt zapłonu, temp. dolotu **[X / ? – patrz etap 5]**

### 2.2 Jazda i podwozie
- Prędkość, bieg (w tym tryb manualny M1–M7) **[N]**
- G-metr: przeciążenia boczne i wzdłużne, „koło przyczepności”, maksima **[N+O]**
- **Ciśnienie w każdej oponie** na grafice auta z góry **[N]**
- Kąt skrętu kierownicy (DAS), prędkości kół, ciśnienie hamulca **[?]**

### 2.3 Zdrowie silnika (przewaga nad zwykłymi zegarami)
- **Krzywa ciśnienie oleju vs obroty i temperatura** – zapis wzorca i ostrzeżenie, gdy ciśnienie spada poniżej normy dla danych warunków **[N+O]**
- **Gotowość do dynamicznej jazdy:** ostrzeżenie przy wysokich obrotach / pełnym gazie, gdy olej jest zimny (np. < 60 °C), pasek „rozgrzewania” **[N+O]**
- Czas nagrzewania płynu i oleju (sprawność termostatu), trend w czasie **[N+O]**
- Maksymalne temperatury w trasie i „cool-down” po ostrej jeździe (turbo) – licznik zalecanego czasu na wolnych obrotach przed zgaszeniem **[N+O]**
- Opony: alarm spadku ciśnienia i asymetrii lewo/prawo, trend (wolny wyciek) **[N+O]**

### 2.4 Osiągi
- Pomiar 0–100, 60–100, 80–120, 100–200 km/h, 1/4 mili, 100–0 (hamowanie) **[N+O]**
- Maksymalna moc i moment z przejazdu, wykres moc/moment vs obroty („dyno”) **[N+O]**
- Shift light w trybie manualnym **[N]**
- Rekordy i historia pomiarów **[O]**

### 2.5 Przejazdy i statystyki
- Komputer podróży: czas, dystans (z całkowania prędkości), średnia prędkość, czas na postoju **[N+O]**
- Czas w zakresach obrotów / biegach, udział jazdy dynamicznej **[N+O]**
- Spalanie i koszty – jeśli znajdzie się sensor zużycia/poziomu paliwa **[?]**
- Zapis logów CSV na pendrive / pamięć urządzenia (do sprawdzenia, czy zapis na USB działa) → analiza w aplikacji webowej **[N+O]**

### 2.6 Alerty
Przegrzanie płynu/oleju, niskie ciśnienie oleju, spadek ciśnienia w oponie, wysokie obroty na zimnym oleju, przypomnienie o schłodzeniu turbin.

---

## 3. Architektura

```
┌──────────────────────── Aplikacja InTouch (APK, Java 7, API 10) ───────────────────────┐
│ UI: własne widoki Canvas, strony przełączane swipem, 800×480, tryb dzień/noc            │
│   Silnik │ Opony/Podwozie │ Osiągi │ Przejazd │ Zdrowie │ Eksplorator sensorów (dev)   │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ DataHub: kanały (wartość, jednostka, stan LIVE/STALE/DEMO/UNAVAILABLE), min/max, bufor  │
│ Silniki: alerty, pomiary osiągów, statystyki przejazdu, logger CSV                      │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ Źródła: SensorSource (SensorManager)  │  DemoSource (syntetyczne)  │  ReplaySource (CSV) │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────── Narzędzia (TypeScript / Node) ────────────┐   ┌──── Aplikacja webowa (TS) ────┐
│ epk-tool: build / inspect pakietów .epk               │   │ Wczytanie logów CSV z auta:   │
│ symulator: generowanie logów testowych                │   │ wykresy, przejazdy, „dyno”,   │
│ CI: GitHub Actions → APK + EPK jako artefakt          │   │ trendy ciśnienia oleju itd.   │
└───────────────────────────────────────────────────────┘   └───────────────────────────────┘
```

Zasady:
- Mapa sensorów (typ → nazwa, skala, jednostka) jako **jeden plik konfiguracyjny** – łatwo poprawiać po testach w aucie.
- Każda wartość ma stan; brak danych = „brak danych”, nigdy fałszywa liczba.
- Zero zależności zewnętrznych w APK, kompilacja przeciw `android.jar` API 10 (żeby nie użyć nieistniejącego API).
- Stały klucz podpisu APK w sekretach GitHub (inaczej aktualizacje się nie zainstalują).

---

## 4. Plan pracy

### Etap 0 – Weryfikacja auta ✅
- [x] App Garage + *Install Apps via USB* dostępne (MY2018).
- [x] Pakiet społeczności (`gtr.epk`) instaluje się i działa.
- [ ] Zanotować wersję oprogramowania InTouch.

### Etap 1 – Toolchain + „Eksplorator sensorów” (≈1 tydz.)
- [x] Struktura repo: `app/` (Java), `tools/` (TS), `scripts/`, `docs/`.
- [x] Skrypt budowania APK (aapt/javac/dx/zipalign/apksigner v1), weryfikacja `minSdk`, braku kodu natywnego, podpisu v1.
- [x] `tools/epk.ts` (TypeScript): `build` i `inspect`, testy; `inspect` poprawnie czyta `gtr.epk`.
- [x] GitHub Actions: każdy push → artefakt `.apk` + `.epk`.
- [x] **Aplikacja v0.1 „Eksplorator”:** przegląd zmapowanych sygnałów, lista wszystkich sensorów z wykresem 20 s, zapis CSV z markerami.
- [x] Decyzja: UI w Javie na Canvas (TS w narzędziach i przyszłej analizie logów).
- [ ] **Pierwsza instalacja w aucie** i sprawdzenie, gdzie da się zapisywać pliki (pendrive? pamięć wewnętrzna?).

### Etap 2 – Mapowanie sygnałów (1–2 tyg., w aucie)
- [ ] Przejazdy testowe z logowaniem; korelacja niezmapowanych sensorów z akcjami: tankowanie (poziom paliwa), skręt kierownicą, hamowanie, włączenie świateł, rozgrzewanie skrzyni.
- [ ] Potwierdzenie skal (porównanie z zegarami i komputerem pokładowym).
- [ ] Wynik: `docs/sensor-map.md` + plik konfiguracyjny mapy.

### Etap 3 – Dashboard MVP (1–2 tyg.)
- [ ] Strony: **Silnik** (obroty, moc, moment, temp./ciśnienie oleju, płyn), **Podwozie** (opony, G-metr, bieg), **Przejazd**.
- [ ] Jednostki metryczne domyślnie, motyw nocny, duże czcionki, obsługa jednym dotknięciem.
- [ ] DemoSource do testów bez auta.

### Etap 4 – Zdrowie, alerty, osiągi, logi (2 tyg.)
- [ ] Alerty z progami i histerezą.
- [ ] Wzorzec ciśnienia oleju, rozgrzewanie, cool-down turbo.
- [ ] Pomiary 0–100 itd., rekordy, „dyno” z sensorów momentu/mocy.
- [ ] Logger CSV przejazdów.

### Etap 5 – Dane spoza CAN radia (badawczy, opcjonalny)
- [ ] Zbadać, czy wśród sensorów da się wyliczyć doładowanie (jak „AUTO FIND” w Red Sport).
- [ ] Sprawdzić, czy aplikacje w Androidzie InTouch mają dostęp do Bluetooth (adapter OBD) – jeśli tak: doładowanie, AFR, trymy, temp. ATF przez OBD.
- [ ] Alternatywa: telefon z adapterem OBD jako uzupełnienie.

### Etap 6 – Aplikacja webowa do analizy logów (TS)
- [ ] Wczytanie CSV z pendrive'a, wykresy przejazdów, porównanie pomiarów, trendy zdrowia silnika.

---

## 5. Ryzyka

| Ryzyko | Mitygacja |
|---|---|
| Inna wersja firmware InTouch → inny certyfikat OBU lub mapa sensorów | Etap 0 (test znanego pakietu), mapa sensorów jako konfiguracja |
| Uszkodzenie/niestabilność radia | Tylko oficjalna ścieżka App Garage, bez modyfikacji firmware; prosta, mała aplikacja |
| Brak doładowania/AFR/knock na CAN radia | Jasno oznaczone jako niedostępne; etap 5 badawczy |
| Android 2.3 – stare API, słaby WebView | Java + Canvas, kompilacja przeciw API 10, test wydajności wcześnie |
| Rozpraszanie kierowcy (aplikacja aktywna w czasie jazdy) | Czytelne, statyczne układy, alerty dźwiękowe zamiast patrzenia na ekran |
| Utrata klucza podpisu → brak aktualizacji | Klucz w sekretach GitHub + kopia zapasowa |

---

## 6. Ustalenia

- Auto: Q50 MY2018, VR30DDTT 305 KM, bez modyfikacji (brak EcuTek – doładowanie/AFR/knock tylko przez etap 5).
- App Garage działa (zainstalowany `gtr.epk`).
- Aplikacja w aucie: **Java**; TypeScript w narzędziach i aplikacji webowej.
