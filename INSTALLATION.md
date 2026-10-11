# Installing Finances / Finances telepítése

[English](#english) · [Magyar](#magyar)

---

## English

Finances is an Android app (Android 8.0 or newer). It is not on Google Play, so you install the APK file from GitHub.

> **Status:** only a personal-beta, debug-signed APK (a pre-release named like `Finances-X.Y.Z-beta.N-debug.apk`) is on the [Releases page](https://github.com/teppe21/Finances_Android/releases) so far; there is no signed production release yet. Developers can build the app themselves, see [DEVELOPMENT.md](DEVELOPMENT.md).

### 1. Download the APK

1. On your phone, open the [Releases page](https://github.com/teppe21/Finances_Android/releases) and the newest entry.
2. Under **Assets**, tap `Finances-vX.Y.Z.apk`. (The `.sha256` file is only for checking the download.)
3. If the browser warns that this type of file can harm your device, choose **Download anyway**. This warning appears for every APK from outside Google Play.

Only download the app from this repository's Releases page. Do not install an APK that someone sends you.

### 2. Allow "Install unknown apps"

When you open the file, Android asks you to allow installing from this source. Tap **Settings** and switch on **Allow from this source** for the app you opened the file with (your browser or file manager). You can switch it off again after the installation.

### 3. Install

Open the downloaded file (notification, browser downloads, or the Files app), tap **Install**, then **Open**.

### 4. Turn on notification access (optional)

Notification recognition reads payment notifications from a short list of supported banking apps and wallets. It is optional; the app works without it. The app shows an explanation first; after you tap **Continue**, Android opens its notification access screen.

> **Android 13 and newer: restricted setting.** For apps installed outside Google Play, Android blocks this switch (it is greyed out, or shows "Restricted setting"). To unblock it:
> 1. Long-press the Finances icon → **App info** (or Settings → Apps → Finances).
> 2. Tap **⋮** (three dots, top right) → **Allow restricted settings**. Confirm with your PIN or fingerprint.
> 3. Go back to the notification access screen and turn **Finances** on.
>
> If you do not see the ⋮ menu, first try to switch notification access on once (it will be refused), then open App info again. Menu wording differs a little between phone brands.

Other permissions are asked only when needed (for example the camera, when you open the receipt scanner).

### 5. Troubleshooting

| Problem | What to do |
| --- | --- |
| "App not installed" | The same app with a different signature is already installed (for example a test build). Uninstall it first. **Uninstalling deletes its data.** |
| Install button is greyed out or missing | Allow "Install unknown apps" for the app you opened the file from (step 2). |
| "Blocked by Play Protect" / "app is not safe" | Expected for an app that is not on Google Play. Choose **More details → Install anyway** only if you got the file from this repository's Releases page. |
| Notification access is greyed out | See the restricted-setting box in step 4. |
| Download looks incomplete or damaged | Download again; optionally compare the SHA-256 value with the `.sha256` file. |

### Updating

Install the newer APK over the old one (or use Obtainium below). Your data stays, because releases are signed with the same key. Android refuses an update signed with a different key.

### Automatic updates with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates apps straight from GitHub Releases.

1. Install Obtainium (see its own page for the download).
2. Tap **Add App**, paste `https://github.com/teppe21/Finances_Android`, and tap **Add**.
3. Obtainium finds the latest release and offers the APK. Tap **Install**.
4. It checks for new releases in the background and tells you when an update is available.

### Your data

Everything is stored only on your phone. There is no account and no cloud backup, and **there is currently no export function**. Uninstalling the app or clearing its storage deletes your data permanently. To delete the data from inside the app: Settings → Data management. Privacy policy: Settings → Privacy.

### Known limitations

- Notification recognition covers only the listed supported apps; other notifications are ignored. Do not connect real financial accounts to try the app: use Settings → Data management → **Add demo data**.
- Existing transactions cannot be edited (delete and re-add).
- Currency conversion uses the latest exchange rate, not the rate of the transaction date.
- The stock watchlist needs your own free [Finnhub](https://finnhub.io) API key (Settings → Market data); whether quotes are real-time depends on your Finnhub plan.
- The app has not been tested on a wide range of physical devices yet.

### Building from source

See [DEVELOPMENT.md](DEVELOPMENT.md).

---

## Magyar

A Finances egy Android-alkalmazás (Android 8.0 vagy újabb). Nincs a Google Play-en, ezért az APK fájlt a GitHubról kell telepíteni.

> **Állapot:** a [Releases oldalon](https://github.com/teppe21/Finances_Android/releases) egyelőre csak egy személyes béta, debug kulccsal aláírt APK található (előzetes kiadás, neve pl. `Finances-X.Y.Z-beta.N-debug.apk`); aláírt, éles kiadás még nincs. Fejlesztők maguk is megépíthetik az appot, lásd: [DEVELOPMENT.md](DEVELOPMENT.md).

### 1. Az APK letöltése

1. A telefonodon nyisd meg a [Releases oldalt](https://github.com/teppe21/Finances_Android/releases), majd a legújabb bejegyzést.
2. Az **Assets** alatt koppints a `Finances-vX.Y.Z.apk` fájlra. (A `.sha256` fájl csak a letöltés ellenőrzésére való.)
3. Ha a böngésző figyelmeztet, hogy ez a fájltípus károsíthatja az eszközt, válaszd a **Letöltés mindenképp** lehetőséget. Ez a figyelmeztetés minden, Google Play-en kívüli APK-nál megjelenik.

Az appot csak ennek a tárolónak a Releases oldaláról töltsd le. Ne telepíts olyan APK-t, amit valaki elküldött neked.

### 2. „Ismeretlen alkalmazások telepítése" engedélyezése

A fájl megnyitásakor az Android engedélyt kér a telepítéshez erről a forrásról. Koppints a **Beállításokra**, és kapcsold be az **Engedélyezés erről a forrásról** opciót annál az appnál, amellyel a fájlt megnyitottad (böngésző vagy fájlkezelő). A telepítés után ki is kapcsolhatod.

### 3. Telepítés

Nyisd meg a letöltött fájlt (értesítésből, a böngésző letöltéseiből vagy a Fájlok appból), koppints a **Telepítés** gombra, majd a **Megnyitás**ra.

### 4. Értesítéshozzáférés bekapcsolása (opcionális)

Az értesítésfelismerés néhány támogatott banki app és pénztárca fizetési értesítéseit olvassa. Nem kötelező, enélkül is működik az app. Az app előbb elmagyarázza, mit csinál; a **Continue** után az Android megnyitja az értesítéshozzáférés képernyőjét.

> **Android 13 és újabb: korlátozott beállítás.** A Google Play-en kívül telepített appoknál az Android letiltja ezt a kapcsolót (szürke, vagy „Korlátozott beállítás" felirat látszik). A feloldás:
> 1. Tartsd nyomva a Finances ikonját → **Alkalmazásinfó** (vagy Beállítások → Alkalmazások → Finances).
> 2. Koppints a **⋮** (jobb felső három pont) → **Korlátozott beállítások engedélyezése** menüre. Erősítsd meg PIN-nel vagy ujjlenyomattal.
> 3. Menj vissza az értesítéshozzáférés képernyőjére, és kapcsold be a **Finances**-t.
>
> Ha nem látod a ⋮ menüt, próbáld meg egyszer bekapcsolni az értesítéshozzáférést (elutasítja), majd nyisd meg újra az Alkalmazásinfót. A menü szövege telefonmárkánként kicsit eltérhet.

A többi engedélyt az app csak akkor kéri, amikor kell (például a kamerát, amikor megnyitod a nyugtaszkennert).

### 5. Hibaelhárítás

| Probléma | Teendő |
| --- | --- |
| „Az alkalmazás nincs telepítve" | Már telepítve van ugyanez az app más aláírással (például egy tesztverzió). Előbb távolítsd el. **Az eltávolítás törli az adatait.** |
| A Telepítés gomb szürke vagy hiányzik | Engedélyezd az „ismeretlen alkalmazások telepítését" annál az appnál, amiből a fájlt megnyitottad (2. lépés). |
| „Play Protect blokkolta" / „az app nem biztonságos" | Várható egy Google Play-en kívüli appnál. A **További részletek → Telepítés mindenképp** lehetőséget csak akkor válaszd, ha a fájlt ennek a tárolónak a Releases oldaláról töltötted le. |
| Az értesítéshozzáférés szürke | Lásd a 4. lépés korlátozott beállításról szóló részét. |
| A letöltés hiányosnak vagy sérültnek tűnik | Töltsd le újra; ha szeretnéd, hasonlítsd össze az SHA-256 értéket a `.sha256` fájllal. |

### Frissítés

Telepítsd az újabb APK-t a régi fölé (vagy használd lent az Obtainiumot). Az adataid megmaradnak, mert a kiadások ugyanazzal a kulccsal vannak aláírva. Az Android elutasítja a más kulccsal aláírt frissítést.

### Automatikus frissítés Obtainiummal

Az [Obtainium](https://github.com/ImranR98/Obtainium) közvetlenül a GitHub Releases oldalról telepít és frissít appokat.

1. Telepítsd az Obtainiumot (a letöltéshez lásd a saját oldalát).
2. Koppints az **Add App** gombra, illeszd be: `https://github.com/teppe21/Finances_Android`, majd koppints az **Add** gombra.
3. Az Obtainium megtalálja a legújabb kiadást, és felajánlja az APK-t. Koppints a **Telepítés** gombra.
4. A háttérben ellenőrzi az új kiadásokat, és jelzi, ha van frissítés.

### Az adataid

Minden adat kizárólag a telefonodon tárolódik. Nincs fiók és nincs felhős mentés, **és jelenleg nincs exportfunkció**. Az app eltávolítása vagy a tárhelyének törlése véglegesen törli az adataidat. Az adatok törlése az appból: Settings → Data management. Adatvédelmi tájékoztató: Settings → Privacy → Privacy policy.

### Ismert korlátok

- Az értesítésfelismerés csak a felsorolt támogatott appokat fedi le, a többi értesítést figyelmen kívül hagyja. Ne kapcsolj valódi pénzügyi fiókot a kipróbáláshoz: használd a Settings → Data management → **Add demo data** lehetőséget.
- A meglévő tranzakciók nem szerkeszthetők (töröld és vidd fel újra).
- A pénznemváltás a legfrissebb árfolyamot használja, nem a tranzakció napjáét.
- A részvény-figyelőlistához saját, ingyenes [Finnhub](https://finnhub.io) API-kulcs kell (Settings → Market data); hogy az árfolyamok valós idejűek-e, a Finnhub csomagodtól függ.
- Az appot még nem tesztelték sokféle fizikai eszközön.

### Forrásból építés

Lásd: [DEVELOPMENT.md](DEVELOPMENT.md).
