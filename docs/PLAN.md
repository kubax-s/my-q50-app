# Q50 App – analiza i plan pracy

Aplikacja (TypeScript, Android) instalowana na systemie multimedialnym Infiniti Q50 (V37) **3.0t VR30DDTT**, wyświetlająca dane z auta przez port OBD-II.

---

## 1. Skąd w ogóle wziąć dane – ograniczenia, które kształtują cały projekt

| Źródło | Co daje | Uwagi |
|---|---|---|
| **Port OBD-II + adapter ELM327 / STN** | Wszystko, co silnik i inne moduły udostępniają po CAN | Główne źródło. Q50 komunikuje się po ISO 15765-4 (CAN 11-bit, 500 kbit/s). |
| Standardowe PID-y (SAE J1979, mode 01/02/03/04/06/07/09/0A) | Obroty, prędkość, temperatury, ciśnienia, trymy paliwa, lambda, błędy, VIN | Działają na każdym aucie, gwarantowane – ale tylko dane „emisyjne” z ECM. |
| Rozszerzone PID-y producenta (mode 21/22, UDS) | Temp. ATF, aktualny bieg, ciśnienia w oponach, temp. oleju, dane hybrydy/DPF, błędy z ABS/SRS/BCM | Nieudokumentowane publicznie – trzeba je **odkryć i zweryfikować na Twoim aucie** (etap 4). |
| Radio/urządzenie z Androidem (GPS, ew. akcelerometr) | Prędkość GPS, trasa, przeciążenia | Radia z Androidem mają GPS; akcelerometr nie zawsze – przy jego braku G-metr liczony z OBD/GPS. |
| Ręcznie wpisywane dane | Tankowania, ceny paliwa, serwisy, koszty | Do historii i statystyk kosztów. |

### 1.1 Platforma docelowa: system multimedialny w aucie

| Wariant radia | Czy da się wgrać własną aplikację? | Wniosek |
|---|---|---|
| **Fabryczny InTouch** (dwa ekrany, 2014–2019) | **Brak oficjalnej możliwości.** Aplikacje w „App Garage” pochodziły wyłącznie z katalogu Infiniti (platforma Airbiquity Choreo) i były dodawane przez aplikację InTouch na telefonie. USB w dokumentacji serwisowej służy do aktualizacji oprogramowania. Na forach pojawia się opcja „install via USB”, ale użytkownicy wgrywający pliki `.apk` dostawali „no apps found”. Jedyny znany przypadek uruchomienia własnego kodu to reverse engineering z dostępem root (Linux, Intel Atom, karta microSD na płycie) bez dostępu do wyświetlaczy (r/CarHacking, 2022). | Nie nadaje się jako platforma bez głębokiego hakowania sprzętu. |
| **Zamienne radio z Androidem** (np. pionowy ekran 10–13" zastępujący oba ekrany InTouch) | **Tak.** Pełny Android, instalacja APK z pendrive'a USB lub ze Sklepu Play. | **Rekomendowana platforma docelowa.** |
| Moduł CarPlay/Android Auto (retrofit na fabryczny ekran) | Nie wprost: moduły zwykle działają na Linuksie, a aplikacja działałaby na telefonie i wyświetlała się przez Android Auto (szablony, ograniczenia kategorii). | Wariant awaryjny. |
| Telefon/tablet w uchwycie | Tak. | Ta sama aplikacja (Android) działa też tu, więc przyda się do developmentu i testów. |

**Decyzja projektowa:** budujemy **aplikację na Androida (APK)** zoptymalizowaną pod ekran radia w aucie: orientacja pozioma i pionowa, duże elementy, start razem z radiem. Ten sam kod uruchomi się na telefonie z Androidem.

Źródła danych na radiu z Androidem:
- **Adapter OBD-II na Bluetooth** (Classic lub BLE, na Androidzie działają oba) – główne źródło.
- **Adapter OBD-II na USB** (np. OBDLink EX / ELM327 USB, układ FTDI/CH340) – stabilniejszy, bez parowania; radio ma i tak port USB.
- **CAN-box radia** (dekoder Raise/Hiworld/Simple Soft dołączony do radia) – często podaje radiu drzwi, klimatyzację, czasem TPMS; do sprawdzenia, czy producent radia udostępnia te dane aplikacjom.

### 1.2 Silnik: VR30DDTT (3.0 V6 twin-turbo, bezpośredni wtrysk) + 7-biegowy automat Jatco

To dobra wiadomość: ECU Nissana (nie Mercedesa), więc standardowe PID-y plus rozszerzone PID-y Nissana (mode 21/22) w zasięgu. Dla tego silnika szczególnie wartościowe są:
- doładowanie (zadane vs rzeczywiste), osobno odchyłka regulacji,
- prędkości obrotowe obu turbosprężarek (silnik ma czujniki prędkości turbin),
- temperatura powietrza za intercoolerem (VR30 ma intercooler wodny z osobną pompą; wzrost IAT pod obciążeniem to typowy objaw problemu z pompą lub obiegiem chłodzenia intercoolera),
- wycofywanie zapłonu (knock) per cylinder / per bank,
- ciśnienie paliwa w układzie wysokiego ciśnienia (DI),
- temperatura oleju silnikowego i oleju skrzyni (ATF).

---

## 2. Co aplikacja może wyświetlać

Oznaczenia dostępności: **[S]** – standard OBD-II (pewne, o ile ECU zgłasza wsparcie PID), **[P]** – PID producenta (do odkrycia/weryfikacji), **[T]** – telefon, **[O]** – obliczane z innych danych, **[R]** – dane wpisywane ręcznie.

### 2.1 Silnik – na żywo
- Obroty, prędkość pojazdu, obciążenie obliczeniowe i absolutne **[S]**
- Pozycja przepustnicy / pedału gazu **[S]**
- Temperatura płynu chłodzącego **[S]**, temperatura oleju **[S – PID 0x5C, jeśli wspierany / P]**
- Temperatura powietrza dolotowego (IAT) **[S]** i za intercoolerem **[P]**, temperatura otoczenia **[S]** → różnica IAT − otoczenie jako wskaźnik sprawności intercoolera **[O]**
- Przepływ powietrza MAF (g/s) **[S]**
- Ciśnienie w kolektorze (MAP) i ciśnienie atmosferyczne **[S]** → **ciśnienie doładowania = MAP − Baro [O]**; PID 0x0B ma limit 255 kPa abs., przy tuningu potrzebny PID rozszerzony (0x87) lub PID Nissana
- Doładowanie zadane vs rzeczywiste (PID 0x70, jeśli wspierany) **[S/P]** → odchyłka regulacji = wycieki doładowania, problemy z wastegate/turbo **[O]**
- Kąt wyprzedzenia zapłonu **[S]**, wycofanie zapłonu przez spalanie stukowe per cylinder **[P]** – kluczowe przy VR30 (jakość paliwa, tuning)
- Trymy paliwa krótko/długoterminowe, osobno dla banku 1 i 2 (V6) **[S]** – świetny wskaźnik nieszczelności dolotu/wtrysków
- Sondy lambda / współczynnik ekwiwalencji (zadany i rzeczywisty) **[S]**
- Ciśnienie paliwa na listwie wysokiego ciśnienia **[S/P]** (bezpośredni wtrysk) – spadek pod obciążeniem = problem z pompą HPFP
- Temperatura katalizatora **[S]**
- Napięcie modułu sterującego (≈ napięcie instalacji) **[S]**
- Czas od uruchomienia silnika **[S]**, poziom paliwa w zbiorniku **[S]**
- Prędkość obu turbosprężarek (VR30 ma czujniki prędkości turbin) **[P]** + porównanie bank 1 vs bank 2 **[O]**
- Liczniki wypadania zapłonu per cylinder **[S mode 06 / P]**

### 2.2 Skrzynia biegów
- Temperatura oleju ATF **[P]** – jedna z najcenniejszych metryk (jazda dynamiczna, upały, holowanie)
- Aktualny / zadany bieg **[P]**, poślizg sprzęgła zmiennika momentu (lock-up) **[P/O]**
- Obroty wału wejściowego/wyjściowego **[P]**
- Wyliczany bieg z ilorazu RPM/prędkość **[O]** – działa nawet bez PID producenta

### 2.3 Spalanie i koszty
- Spalanie chwilowe (l/100 km, l/h na postoju) **[O]**
  - benzyna: `paliwo [g/s] = MAF / (14.7 × λ_zadana)`, przeliczone przez gęstość ~0,745 kg/l,
  - lub bezpośrednio z PID 0x5E (fuel rate), jeśli wspierany **[S]**,
  - pod pełnym doładowaniem VR30 wzbogaca mieszankę – dlatego korekta przez λ zadaną jest konieczna.
- Średnie: bieżący przejazd, dzień, miesiąc, od tankowania **[O]**
- Zasięg na pozostałym paliwie (poziom paliwa × pojemność zbiornika ÷ średnie spalanie) **[O]**
- Koszt przejazdu w PLN (z ceną z ostatniego tankowania) **[O+R]**
- Kalibracja: porównanie spalania wyliczonego z realnym z dystrybutora → współczynnik korekty **[R+O]**

### 2.4 Diagnostyka
- Odczyt i kasowanie błędów silnika (mode 03/04), błędy oczekujące (07) i stałe (0A) **[S]**
- Status lampki MIL i liczba błędów **[S]**
- Monitory gotowości (przydatne przed przeglądem / po skasowaniu błędów) **[S]**
- Freeze frame – stan silnika w chwili wystąpienia błędu (mode 02) **[S]**
- Wyniki testów pokładowych (mode 06: katalizator, sondy, misfire) **[S]**
- VIN, identyfikatory kalibracji ECU (mode 09) **[S]**
- Błędy z innych modułów: ABS/VDC, poduszki, BCM, skrzynia, DAS (układ kierowniczy) **[P – UDS 0x19]**
- Baza opisów kodów (P0xxx generyczne + typowe Nissan/Infiniti P1xxx)

### 2.5 Stan techniczny i trendy (to odróżnia aplikację od zwykłego skanera)
- **Akumulator:** napięcie spoczynkowe, spadek przy rozruchu, napięcie ładowania – trend w czasie → ostrzeżenie o słabnącym akumulatorze **[S+O]**
- **Trymy paliwa w czasie** – rosnący LTFT na jednym banku = nieszczelność / wtryskiwacz **[S+O]**
- **Czas nagrzewania** silnika do 80–90 °C (termostat) **[S+O]**
- **Temperatury maksymalne** płynu, oleju, ATF, IAT na trasie **[S/P+O]**
- **Ciśnienie w oponach** (Q50 ma czujniki TPMS w każdym kole) **[P]** + alarm spadku ciśnienia między pomiarami
- **Turbo VR30:** historia maksymalnych prędkości turbin i odchyłek doładowania – wczesne wykrycie zużycia turbosprężarki lub nieszczelności **[P+O]**
- **Intercooler:** trend IAT pod obciążeniem – wykrycie awarii pompy intercoolera **[S/P+O]**
- **Cewki zapłonowe:** liczniki wypadania zapłonu per cylinder w czasie **[S/P+O]**
- **Przypomnienia serwisowe** wg km/czasu: olej, filtry, świece, ATF, płyn hamulcowy, przegląd, OC **[R+O]**

### 2.6 Osiągi i jazda
- Pomiar 0–100, 60–100, 80–120 km/h, 100–0 (hamowanie), 1/4 mili, 402 m **[S+T]**
- Szacowana moc na kołach z masy, przyspieszenia i oporów **[O]**
- G-metr (przeciążenia boczne/wzdłużne), wykres „koła przyczepności” **[T]**
- Wykres doładowania vs obroty, logi „pull” do analizy tuningu **[S+O]**
- Wskaźnik zmiany biegu (shift light) w trybie manualnym **[S]**
- Porównanie prędkościomierza z GPS (błąd licznika, np. po zmianie rozmiaru kół) **[S+T]**

### 2.7 Historia i przejazdy
- Automatyczny zapis przejazdów: trasa GPS, dystans, czas, średnia prędkość, spalanie, koszt
- Mapa tras kolorowana spalaniem/prędkością
- Statystyki: czas na biegu jałowym, udział jazdy miejskiej/trasy, ocena płynności jazdy
- Eksport CSV / JSON (do analizy w Excelu, Pythonie, VirtualDyno itp.)

### 2.8 Alerty (dźwięk + kolor na ekranie)
- Przegrzanie płynu / oleju / ATF
- Niskie napięcie lub brak ładowania
- Pojawienie się nowego kodu błędu
- Wycofywanie zapłonu (knock) powyżej progu
- Spadek ciśnienia w oponie
- Wysoka temperatura za intercoolerem (heat soak)
- Odchyłka doładowania (zadane vs rzeczywiste) powyżej progu
- Nadmierne doładowanie / zbyt ubogie AFR pod obciążeniem (tuning)

---

## 3. Proponowana architektura (TypeScript)

```
┌────────────────────────── UI (React Native) ──────────────────────────┐
│ Dashboard (konfigurowalne kafelki) · Wykresy · Diagnostyka · Trasy     │
└───────────────▲───────────────────────────────────────▲────────────────┘
                │ store (Zustand)                       │ zapytania
┌───────────────┴──────────────┐          ┌─────────────┴───────────────┐
│ Scheduler odpytywania        │          │ Baza SQLite (trasy, próbki, │
│ (priorytety, multi-PID)      │──zapis──▶│ tankowania, serwisy, DTC)   │
└───────────────▲──────────────┘          └─────────────────────────────┘
┌───────────────┴──────────────┐
│ Rejestr PID-ów (typowane     │  definicje: id, tryb, nagłówek ECU,
│ definicje + dekodery)        │  wzór, jednostka, zakres
└───────────────▲──────────────┘
┌───────────────┴──────────────┐
│ Warstwa protokołu ELM327     │  AT-init, kolejka komend, parsowanie
│ (+ ISO-TP, UDS)              │  ramek wieloczęściowych, timeouty
└───────────────▲──────────────┘
┌───────────────┴──────────────┐
│ Transport (interfejs)        │  BLE · Bluetooth Classic · Wi-Fi TCP ·
│                              │  Mock/emulator (do pracy bez auta)
└──────────────────────────────┘
```

**Stack (rekomendacja):**
- **React Native + Expo (dev client / prebuild) + TypeScript**, budowane do **APK** (`eas build --local` lub Gradle) i instalowane z pendrive'a. Natywne moduły (Bluetooth, USB) wykluczają Expo Go.
- `react-native-bluetooth-classic` (adaptery BT Classic), `react-native-ble-plx` (adaptery BLE), `react-native-usb-serialport-for-android` (adapter OBD na USB).
- **Wymaganie:** sprawdzić wersję Androida i architekturę CPU radia (często Android 10–13, arm64) i ustawić `minSdkVersion` odpowiednio.
- Autostart po uruchomieniu radia (`BOOT_COMPLETED`) i automatyczne łączenie z adapterem.
- `expo-sqlite` (lub `op-sqlite`) – dane; `zustand` – stan; `react-native-skia` / `victory-native` – szybkie wykresy i zegary; `expo-location`, `expo-sensors`, `expo-keep-awake`.
- Logika OBD jako **czysty pakiet TS bez zależności od RN** (`packages/obd-core`) → testy w Vitest, łatwe podpięcie emulatora.

Alternatywa: natywny Kotlin + Jetpack Compose – lepsza wydajność na słabszych radiach, ale tracimy TypeScript. Zaczynamy od RN; jeśli radio okaże się za słabe do płynnych zegarów 30 fps, warstwę UI można zoptymalizować (Skia).

**Kluczowe decyzje techniczne:**
- **Przepustowość:** ELM327 przez BLE daje realnie ~10–30 odpowiedzi/s. Rozwiązanie: scheduler z priorytetami (RPM/doładowanie/przepustnica często, temperatury co kilka sekund, poziom paliwa co minutę) + łączenie do 6 PID-ów w jednym zapytaniu mode 01 na CAN.
- **Inicjalizacja:** `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP6` (CAN 11-bit 500k), `ATAT1`; przełączanie nagłówków (`ATSH`) do odpytywania innych modułów.
- **Wykrywanie wsparcia:** bitmapy PID 0x00/0x20/0x40/… → UI pokazuje tylko to, co auto faktycznie obsługuje.
- **Tylko odczyt:** w pierwszych etapach żadnego zapisu/kodowania modułów. Kasowanie DTC z potwierdzeniem.

---

## 4. Sprzęt

- **Adapter oparty o STN / dobrej jakości klon:** OBDLink MX+ (BT Classic) lub OBDLink EX (USB) / vLinker FS (USB) / vLinker MC+. Unikać najtańszych klonów ELM327 v1.5/v2.1 – gubią ramki, źle obsługują długie odpowiedzi.
- **Radio z Androidem** – przed zakupem sprawdzić: wersję Androida, RAM (min. 4 GB), możliwość instalacji APK, zachowanie fabrycznych funkcji (kamera, kierownica, klimatyzacja na dolnym ekranie!).
- Adapter z **automatycznym usypianiem** (żeby nie rozładował akumulatora na postoju).
- Telefon z Androidem do developmentu (ten sam APK) przed testami na radiu.
- Na etap odkrywania PID-ów pomocne: aplikacja Car Scanner ELM (ma profile Nissan/Infiniti) – jako punkt odniesienia do weryfikacji własnych odczytów.

---

## 5. Plan pracy (etapy)

### Etap 0 – Rozpoznanie (½–1 dzień)
- [ ] Ustalić model radia: fabryczny InTouch czy zamienne z Androidem (wersja Androida, CPU, rozdzielczość ekranu, orientacja).
- [ ] Sprawdzić instalację testowego APK z pendrive'a na radiu.
- [ ] Kupić adapter OBD (USB lub BT).
- [ ] Zrobić „zrzut” auta istniejącą aplikacją: lista wspieranych PID-ów, VIN, lista modułów odpowiadających na CAN.

### Etap 1 – Fundament (1–2 tyg.)
- [ ] Monorepo: `apps/mobile` (Expo) + `packages/obd-core` (czysty TS), ESLint, Prettier, Vitest, CI na GitHub Actions.
- [ ] Interfejs `Transport` + implementacje: `MockTransport` (nagrane odpowiedzi), `BluetoothClassicTransport`, `UsbSerialTransport`, później `BleTransport`.
- [ ] Pipeline budowania APK (GitHub Actions → artefakt do pobrania i wgrania na pendrive).
- [ ] Warstwa ELM327: kolejka komend, init, parser odpowiedzi (w tym wieloramkowe ISO-TP), obsługa `NO DATA`, `STOPPED`, `CAN ERROR`, timeoutów, reconnectu.
- [ ] Rejestr standardowych PID-ów mode 01 z dekoderami + testy jednostkowe na znanych ramkach.
- [ ] Emulator do developmentu (np. `ELM327-emulator` przez TCP) – praca bez auta.

### Etap 2 – MVP: dashboard + diagnostyka (1–2 tyg.)
- [ ] Ekran połączenia (wybór adaptera BT/USB, zapamiętanie, autołączenie, autostart z radiem).
- [ ] Dashboard na żywo: konfigurowalne kafelki (wartość, zegar, mini-wykres), układ dopasowany do rozdzielczości radia, ciemny motyw nocny, duże czcionki.
- [ ] Domyślny ekran „VR30”: doładowanie, IAT, temp. płynu/oleju, AFR, timing, napięcie.
- [ ] Diagnostyka: VIN, MIL, odczyt/kasowanie DTC, oczekujące/stałe DTC, monitory gotowości, freeze frame, baza opisów kodów.
- [ ] Spalanie chwilowe i średnie z bieżącej sesji.
- [ ] **Pierwszy test w aucie.**

### Etap 3 – Zapis i historia (1–2 tyg.)
- [ ] Schemat SQLite: `trips`, `samples` (kompresja/decymacja), `fuel_ups`, `service_items`, `dtc_history`.
- [ ] Automatyczny start/stop przejazdu (połączenie z adapterem + RPM > 0), GPS.
- [ ] Ekran przejazdów: mapa, wykresy w czasie, statystyki, koszt.
- [ ] Tankowania, kalibracja spalania, zasięg.
- [ ] Eksport CSV.

### Etap 4 – PID-y producenta (2–4 tyg., najbardziej badawczy)
- [ ] Narzędzie „skaner DID” w trybie deweloperskim: odpytanie zakresów mode 21/22 na kolejnych adresach modułów, zapis surowych odpowiedzi.
- [ ] Identyfikacja wartości przez korelację (np. ATF rośnie razem z czasem jazdy; ciśnienie opon po dopompowaniu; bieg zmienia się z ilorazem RPM/prędkość).
- [ ] Priorytety dla VR30: temp. ATF → bieg → knock per cylinder → prędkości turbin → doładowanie zadane → temp. oleju → IAT za intercoolerem → TPMS.
- [ ] Punkt odniesienia do weryfikacji: aplikacje z profilami Nissan/Infiniti (np. Car Scanner) oraz społeczność tuningowa VR30 (logi z narzędzi typu Ecutek).
- [ ] Odczyt DTC z innych modułów (UDS 0x19).
- [ ] Definicje PID-ów producenta jako plik danych (JSON) per wariant silnika – łatwe rozszerzanie.

### Etap 5 – Zdrowie, alerty, osiągi (1–2 tyg.)
- [ ] Silnik reguł alertów (progi, histereza, konfigurowalne).
- [ ] Trendy: akumulator, LTFT, czas nagrzewania, temperatury maksymalne.
- [ ] Przypomnienia serwisowe (lokalne powiadomienia).
- [ ] Pomiary osiągów (0–100, 100–200 itd.) z fuzją danych OBD + GPS (+ akcelerometr, jeśli radio ma), G-metr.

### Etap 6 – Dopracowanie (ciągłe)
- [ ] Działanie w tle (foreground service) – logowanie trwa, gdy na radiu włączona jest nawigacja/muzyka.
- [ ] Pływające okno / widżet z kluczowymi wartościami nad innymi aplikacjami.
- [ ] Wersja na telefon (ten sam APK) i ewentualnie Android Auto dla wariantu z modułem retrofit.
- [ ] Opcjonalnie backup/synchronizacja w chmurze, panel webowy z historią.

---

## 6. Ryzyka

| Ryzyko | Mitygacja |
|---|---|
| PID-y producenta nieznane / różne dla wersji | Etap 4 jako osobny, badawczy; aplikacja w pełni użyteczna na samych standardowych PID-ach |
| Słaby adapter → zgubione ramki, niska częstotliwość | Adapter STN/OBDLink, scheduler z priorytetami, multi-PID |
| Fabryczny InTouch nie pozwala na własne aplikacje | Docelowo radio z Androidem; do tego czasu aplikacja działa na telefonie/tablecie |
| Słabe CPU / stary Android w tanich radiach | Ustalić specyfikację radia na etapie 0, testować wydajność UI wcześnie |
| Radio agresywnie ubija aplikacje w tle | Foreground service, wyłączenie optymalizacji baterii, autostart |
| Rozpraszanie kierowcy | Minimalna interakcja w trakcie jazdy, duże elementy, alerty dźwiękowe |
| Rozładowanie akumulatora przez adapter | Adapter z auto-sleep, wykrywanie wyłączenia zapłonu |
| Kasowanie DTC / zapytania do modułów bezpieczeństwa | Tylko odczyt, potwierdzenia, brak zapisu do modułów |

---

## 7. Otwarte pytania

1. **Jakie masz radio?** Fabryczny InTouch (dwa ekrany) czy już wymienione na Androida (model, wersja Androida)?
2. Jeśli fabryczne: skąd informacja o instalacji przez USB? Konkretna instrukcja / nazwa opcji w menu pomoże sprawdzić, czy da się tam wgrać własny kod.
3. Rocznik auta i czy wersja Red Sport 400 (405 KM)? Czy silnik jest tuningowany (inne progi alertów, doładowanie > 255 kPa)?
4. Masz już adapter OBD? Jaki?
5. Priorytet: monitoring silnika/turbo, koszty i trasy, czy osiągi?
