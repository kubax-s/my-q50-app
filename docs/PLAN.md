# Q50 App – analiza i plan pracy

Aplikacja mobilna (TypeScript) wyświetlająca dane z Infiniti Q50 (V37) przez port OBD-II.

---

## 1. Skąd w ogóle wziąć dane – ograniczenia, które kształtują cały projekt

| Źródło | Co daje | Uwagi |
|---|---|---|
| **Port OBD-II + adapter ELM327 / STN** | Wszystko, co silnik i inne moduły udostępniają po CAN | Główne źródło. Q50 komunikuje się po ISO 15765-4 (CAN 11-bit, 500 kbit/s). |
| Standardowe PID-y (SAE J1979, mode 01/02/03/04/06/07/09/0A) | Obroty, prędkość, temperatury, ciśnienia, trymy paliwa, lambda, błędy, VIN | Działają na każdym aucie, gwarantowane – ale tylko dane „emisyjne” z ECM. |
| Rozszerzone PID-y producenta (mode 21/22, UDS) | Temp. ATF, aktualny bieg, ciśnienia w oponach, temp. oleju, dane hybrydy/DPF, błędy z ABS/SRS/BCM | Nieudokumentowane publicznie – trzeba je **odkryć i zweryfikować na Twoim aucie** (etap 4). |
| Telefon (GPS, akcelerometr, żyroskop) | Prędkość GPS, trasa, przeciążenia, nachylenie | Darmowe, niezależne od auta. |
| Ręcznie wpisywane dane | Tankowania, ceny paliwa, serwisy, koszty | Do historii i statystyk kosztów. |

**Ekran InTouch** w Q50 nie pozwala instalować aplikacji (a CarPlay/Android Auto pojawiły się dopiero w późnych rocznikach), więc aplikacja działa **na telefonie / tablecie w uchwycie**.

**Silnik ma ogromne znaczenie.** W Europie Q50 występował m.in. jako:
- **2.2d** (Mercedes OM651) + 7G-Tronic – ECU Mercedesa/Bosch, ciekawy DPF/AdBlue-less diesel → inne PID-y producenta,
- **2.0t** (Mercedes M274) + 7G-Tronic,
- **3.5 Hybrid** (VQ35HR + silnik elektryczny, Jatco 7AT) – dochodzą dane baterii HV,
- **3.0t** (VR30DDTT, 300/405 KM, Jatco 7AT) – turbo, doładowanie, spalanie stukowe,
- (USA: 3.7 VQ37VHR).

Standardowe PID-y działają na wszystkich, ale część „smaczków” (DPF, bateria HV, ATF) zależy od wersji.

---

## 2. Co aplikacja może wyświetlać

Oznaczenia dostępności: **[S]** – standard OBD-II (pewne, o ile ECU zgłasza wsparcie PID), **[P]** – PID producenta (do odkrycia/weryfikacji), **[T]** – telefon, **[O]** – obliczane z innych danych, **[R]** – dane wpisywane ręcznie.

### 2.1 Silnik – na żywo
- Obroty, prędkość pojazdu, obciążenie obliczeniowe i absolutne **[S]**
- Pozycja przepustnicy / pedału gazu **[S]**
- Temperatura płynu chłodzącego **[S]**, temperatura oleju **[S – PID 0x5C, jeśli wspierany / P]**
- Temperatura powietrza dolotowego (IAT) **[S]**, temperatura otoczenia **[S]**
- Przepływ powietrza MAF (g/s) **[S]**
- Ciśnienie w kolektorze (MAP) i ciśnienie atmosferyczne **[S]** → **ciśnienie doładowania = MAP − Baro [O]** (turbo: 2.0t, 3.0t, 2.2d)
- Kąt wyprzedzenia zapłonu **[S]**, wycofanie zapłonu przez spalanie stukowe **[P]** (ważne przy VR30)
- Trymy paliwa krótko/długoterminowe, osobno dla banku 1 i 2 (V6) **[S]** – świetny wskaźnik nieszczelności dolotu/wtrysków
- Sondy lambda / współczynnik ekwiwalencji (zadany i rzeczywisty) **[S]**
- Ciśnienie paliwa na listwie **[S]** (bezpośredni wtrysk – wysokie ciśnienie)
- Temperatura katalizatora **[S]**
- Napięcie modułu sterującego (≈ napięcie instalacji) **[S]**
- Czas od uruchomienia silnika **[S]**, poziom paliwa w zbiorniku **[S]**
- Prędkość turbosprężarek (VR30 ma czujniki) **[P]**
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
  - diesel: MAF się nie nadaje (praca na ubogiej mieszance) → potrzebna dawka wtrysku **[P]**.
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
- **Diesel 2.2d:** zapełnienie DPF (sadza w g), dystans od ostatniej regeneracji, status regeneracji w toku, ciśnienie różnicowe DPF **[P]** – bardzo praktyczne (nie gasić auta w trakcie regeneracji)
- **Hybryda:** stan naładowania baterii HV, temperatury ogniw, prąd/moc, tryb EV **[P]**
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
- Regeneracja DPF w toku (diesel)
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
- **React Native + Expo (dev client) + TypeScript** – jedna baza kodu iOS/Android, potrzebny natywny Bluetooth, więc nie czysty Expo Go.
- `react-native-ble-plx` (BLE – iOS i Android), opcjonalnie `react-native-bluetooth-classic` (tanie adaptery BT Classic – tylko Android).
- `expo-sqlite` (lub `op-sqlite`) – dane; `zustand` – stan; `react-native-skia` / `victory-native` – szybkie wykresy i zegary; `expo-location`, `expo-sensors`, `expo-keep-awake`.
- Logika OBD jako **czysty pakiet TS bez zależności od RN** (`packages/obd-core`) → testy w Vitest, łatwe podpięcie emulatora.

Alternatywa: PWA z Web Bluetooth – szybki start, ale **nie działa na iOS** i ma ograniczone działanie w tle, więc tylko jako prototyp.

**Kluczowe decyzje techniczne:**
- **Przepustowość:** ELM327 przez BLE daje realnie ~10–30 odpowiedzi/s. Rozwiązanie: scheduler z priorytetami (RPM/doładowanie/przepustnica często, temperatury co kilka sekund, poziom paliwa co minutę) + łączenie do 6 PID-ów w jednym zapytaniu mode 01 na CAN.
- **Inicjalizacja:** `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP6` (CAN 11-bit 500k), `ATAT1`; przełączanie nagłówków (`ATSH`) do odpytywania innych modułów.
- **Wykrywanie wsparcia:** bitmapy PID 0x00/0x20/0x40/… → UI pokazuje tylko to, co auto faktycznie obsługuje.
- **Tylko odczyt:** w pierwszych etapach żadnego zapisu/kodowania modułów. Kasowanie DTC z potwierdzeniem.

---

## 4. Sprzęt

- **Adapter BLE oparty o STN / dobrej jakości klon:** OBDLink CX (BLE, iOS+Android) lub MX+ / vLinker MC+ / FS. Unikać najtańszych klonów ELM327 v1.5/v2.1 – gubią ramki, źle obsługują długie odpowiedzi.
- Adapter z **automatycznym usypianiem** (żeby nie rozładował akumulatora na postoju).
- Uchwyt telefonu + zasilanie (sesja z GPS i ekranem włączonym zjada baterię).
- Na etap odkrywania PID-ów pomocne: aplikacja Car Scanner ELM (ma profile Nissan/Infiniti) – jako punkt odniesienia do weryfikacji własnych odczytów.

---

## 5. Plan pracy (etapy)

### Etap 0 – Rozpoznanie (½–1 dzień)
- [ ] Ustalić: rocznik, silnik, rynek (EU/US), platformę telefonu.
- [ ] Kupić adapter BLE.
- [ ] Zrobić „zrzut” auta istniejącą aplikacją: lista wspieranych PID-ów, VIN, lista modułów odpowiadających na CAN.

### Etap 1 – Fundament (1–2 tyg.)
- [ ] Monorepo: `apps/mobile` (Expo) + `packages/obd-core` (czysty TS), ESLint, Prettier, Vitest, CI na GitHub Actions.
- [ ] Interfejs `Transport` + implementacje: `MockTransport` (nagrane odpowiedzi), `BleTransport`.
- [ ] Warstwa ELM327: kolejka komend, init, parser odpowiedzi (w tym wieloramkowe ISO-TP), obsługa `NO DATA`, `STOPPED`, `CAN ERROR`, timeoutów, reconnectu.
- [ ] Rejestr standardowych PID-ów mode 01 z dekoderami + testy jednostkowe na znanych ramkach.
- [ ] Emulator do developmentu (np. `ELM327-emulator` przez TCP) – praca bez auta.

### Etap 2 – MVP: dashboard + diagnostyka (1–2 tyg.)
- [ ] Ekran połączenia (skan BLE, zapamiętanie adaptera, autołączenie).
- [ ] Dashboard na żywo: konfigurowalne kafelki (wartość, zegar, mini-wykres), tryb poziomy, ciemny motyw nocny, duże czcionki.
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
- [ ] Priorytety: temp. ATF → bieg → TPMS → temp. oleju → (diesel) DPF / (hybryda) bateria HV / (3.0t) knock i prędkość turbin.
- [ ] Odczyt DTC z innych modułów (UDS 0x19).
- [ ] Definicje PID-ów producenta jako plik danych (JSON) per wariant silnika – łatwe rozszerzanie.

### Etap 5 – Zdrowie, alerty, osiągi (1–2 tyg.)
- [ ] Silnik reguł alertów (progi, histereza, konfigurowalne).
- [ ] Trendy: akumulator, LTFT, czas nagrzewania, temperatury maksymalne.
- [ ] Przypomnienia serwisowe (lokalne powiadomienia).
- [ ] Pomiary osiągów (0–100 itd.) z fuzją danych OBD + GPS + akcelerometr, G-metr.

### Etap 6 – Dopracowanie (ciągłe)
- [ ] Działanie w tle / przy zablokowanym ekranie (ograniczenia iOS!).
- [ ] Widżety, ewentualnie Android Auto / CarPlay (ograniczone kategorie aplikacji – do sprawdzenia).
- [ ] Opcjonalnie backup/synchronizacja w chmurze, panel webowy z historią.

---

## 6. Ryzyka

| Ryzyko | Mitygacja |
|---|---|
| PID-y producenta nieznane / różne dla wersji | Etap 4 jako osobny, badawczy; aplikacja w pełni użyteczna na samych standardowych PID-ach |
| Słaby adapter → zgubione ramki, niska częstotliwość | Adapter STN/OBDLink, scheduler z priorytetami, multi-PID |
| iOS ogranicza pracę w tle i BT Classic | Tylko adaptery BLE; logowanie w tle przez BLE background mode |
| Rozpraszanie kierowcy | Minimalna interakcja w trakcie jazdy, duże elementy, alerty dźwiękowe |
| Rozładowanie akumulatora przez adapter | Adapter z auto-sleep, wykrywanie wyłączenia zapłonu |
| Kasowanie DTC / zapytania do modułów bezpieczeństwa | Tylko odczyt, potwierdzenia, brak zapisu do modułów |

---

## 7. Otwarte pytania

1. Jaki rocznik i silnik (2.2d / 2.0t / 3.5 Hybrid / 3.0t)? → decyduje o zakresie etapu 4.
2. iPhone czy Android? → wybór adaptera i priorytet BLE vs Classic.
3. Masz już adapter OBD? Jaki?
4. Priorytet: codzienna jazda i koszty, diagnostyka/zdrowie auta czy osiągi/tuning?
5. Aplikacja tylko dla siebie, czy docelowo do publikacji w sklepach?
