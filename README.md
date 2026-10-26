# Rota Asistanı – Kurye rota prototipi (Android)

Dağıtım yapan iş ortakları (kuryeler) için rota uygulaması prototipi. Bir mahallede
65 paket varsa en kısa dağıtım sırasını hesaplar ve haritada gösterir. Kurye her
durakta paketi **Teslim edildi**, **Sonra dön** (not ekleyerek) veya **Teslim
edilemedi** olarak işaretler. Ana tur bitince ertelenen paketler için bulunduğu
yerden **ayrı bir en kısa rota** çizilir.

> Bu bir **sunum prototipidir**. Gerçek müşteri verisi kullanmaz. İsimler ve
> adresler rastgele üretilir. Hermes'e entegrasyon için aşağıdaki
> "Üretime geçiş" bölümüne bakın.

## Özellikler

| | |
|---|---|
| Rota optimizasyonu | En yakın komşu + 2-opt + Or-opt + Iterated Local Search. 12 durağa kadar Held-Karp ile **kesin** en kısa sıra. 65 durak bilgisayarda ~40 ms'de çözülüyor, telefonda da bir saniyenin altında. |
| Yol ağı mesafeleri | İnternet varsa OSRM ile gerçek sokak mesafeleri kullanılır ve rota sokakları takip eder. Yoksa kuş uçuşu × 1,3 tahmine geçer, uygulama çevrimdışı da çalışır. |
| Teslimat akışı | Teslim edildi (snackbar'dan **Geri al**), Sonra dön (hazır not seçenekleri + serbest not), Teslim edilemedi (sebep + not → şubeye iade). |
| 2. tur | Ana tur bitince "Ertelenenler için rota oluştur" ile kalan paketlere son konumdan en kısa rota çizilir. 2. turda tekrar ertelenen paket rotanın sonuna alınır. |
| Harita | OpenStreetMap (API anahtarı gerekmez). Numaralı ve renkli duraklar, sıradaki durak vurgulu, kurye konumu, lejant. |
| Kıyas | "Liste sırasıyla gitseydi" ve "hep en yakın pakete gitseydi" senaryolarına göre kaç % kısa olduğunu gösterir. |
| Diğer | Navigasyon uygulamasına (Google Maps / Yandex) tek dokunuşla geçiş, sırayı elle değiştirme ("Sıradaki yap"), sekmeli paket listesi, gün sonu özeti. Uygulama kapansa da günün durumu kaybolmaz. |

## APK'yı telefona yükleme (Android Studio gerekmez)

Bu repoya her push'ta GitHub Actions testleri çalıştırır ve bir **debug APK** üretir.

1. GitHub'da repo → **Actions** → **Android APK** → en son yeşil çalışmayı aç.
2. Sayfanın altındaki **Artifacts** bölümünden `rota-asistani-debug-apk` dosyasını indir (zip).
3. Zip'i aç, içindeki `app-debug.apk` dosyasını telefona at (USB, Drive, WhatsApp vb.).
4. Telefonda dosyaya dokun. "Bilinmeyen kaynaklardan yükleme" izni istenirse ver.
5. Uygulama açılınca konum iznini ver (Ataşehir demosu için zorunlu değil).

Android 8.0 (API 26) ve üzeri desteklenir.

Aynı çalışmada **duman testi** de var: uygulama bir Android emülatöründe açılır ve
teslim → ertele → iade → 2. tur → gün sonu akışına otomatik dokunulur. Her adımın
ekran görüntüsü `emulator-ekran-goruntuleri` artifact'ında. Sunum slaytı için de
kullanılabilir.

### Android Studio ile çalıştırma (geliştirme için)

1. [Android Studio](https://developer.android.com/studio)'yu kur ve bu klasörü *Open* ile aç.
2. Gradle senkronizasyonu bitince telefonu USB hata ayıklama ile bağla ve **Run ▶** de.
3. Rota algoritması testleri: `./gradlew :route-core:test`

## 5 dakikalık sunum senaryosu

1. **Açılış:** Ataşehir'de 65 paket yüklü gelir ve rota otomatik hesaplanır. Üst bantta
   kalan km ve süre ile "liste sırasına göre %X, *hep en yakına git*e göre %Y daha
   kısa" bilgisi görünür.
2. **Sıradaki durak:** Alttaki kartta alıcı, adres ve "Ardından" bilgisi var.
   **Navigasyon** ile Google Maps/Yandex açılır.
3. **Teslim edildi:** Harita bir sonraki durağa kayar. Yanlış dokunuşu **Geri al** düzeltir.
4. **Sonra dön:** "Alıcı evde yok" notuyla ertele. Paket turuncu ↻ ile işaretlenir ve rotadan çıkar.
5. **Teslim edilemedi:** Sebep seç ("Adres bulunamadı"). Paket kırmızı ! ile işaretlenir.
6. **Liste:** Sağ üstteki liste ikonu: Rota / Teslim / Ertelenen / İade sekmeleri.
7. **2. tur:** Menü → *Demo: ana turu hızlıca bitir*, ardından **Ertelenenler için rota
   oluştur**. Kalan paketler için son konumdan yeni en kısa rota çizilir.
8. **Gün sonu özeti:** Menü → *Gün sonu özeti*: teslim, iade (sebepleriyle) ve ertelenenler.

İpucu: Menü → *Yeni demo: konumum çevresinde* seçeneği paketleri toplantı odasının
etrafına dağıtır. GPS ile canlı gösterim için iyi bir seçenek.

## Mimari

```
route-core/   Saf Kotlin, Android'e bağımlı değil
  RoutePlanner   açık uçlu TSP çözücü (asimetrik maliyet destekli)
  Geo            haversine, mesafe matrisi
app/
  RouteViewModel durum makinesi: ana tur → ertelenenler turu → gün sonu
  OsrmClient     yol ağı mesafe matrisi + rota çizgisi (hata olursa null döner, tahmine düşer)
  StopStore      günün durumunu cihazda saklar
  ui/            Jetpack Compose ekranları + osmdroid harita
```

`route-core` Android'e bağımlı olmadığı için **aynı algoritma** hem telefonda
(çevrimdışı yeniden hesaplama) hem de Hermes'in sunucu tarafında (JVM) çalışabilir.

## Prototipin sınırları (sunumda açıkça söylenmeli)

- **Demo veri:** İsimler rastgele. Noktalar mahallede rastgele seçilip OSRM ile en yakın
  sokağa bağlanıyor. Otoyola, kavşağa veya isimsiz yola düşenler eleniyor, sokak adı
  gerçek. Gerçekte en zor kısım adresi doğru koordinata ve **doğru sokağa** bağlamak
  (geocoding). Test sırasında bunu yaşadık: kavşağa yapışan 6 nokta 18 km'lik bir
  turu 46 km'ye çıkarıyordu.
- **"Liste sırasına göre %X" rakamı abartılıdır**, çünkü demo listesi coğrafi olarak
  karışık. Deneyimli bir kuryeyle karşılaştırmaya daha yakın olan rakam
  *"hep en yakına git"e göre* olanıdır. Gerçek tasarruf ancak pilotta ölçülebilir.
- **OSRM demo sunucusu ve OSM harita karoları** sadece deneme içindir; kullanım
  kuralları ticari yükü yasaklar. Üretimde şirketin kendi sunucusu veya ticari bir
  sağlayıcı gerekir. OSRM demo sunucusu araba profilini kullanır, motosiklet/yaya
  profili yoktur.
- Zaman penceresi ("17:00'den sonra gel") şimdilik sadece not olarak tutuluyor.
  Rota hesabına girmiyor.

## Üretime geçiş: Hermes'e entegrasyon önerisi

1. **Hermes ekibiyle teknik eşleşme:** Hermes native Kotlin mi, Flutter mı, React Native mi?
   Native ise bu ekranlar modül olarak eklenebilir. Değilse rota mantığı backend
   servisi olarak sunulur ve arayüz Hermes'in kendi teknolojisiyle yazılır.
2. **Rota servisi (backend):** Şube paketleri kuryeye atadığında servis adresleri
   koordinata çevirir, yol ağı matrisini hesaplar (kendi OSRM/Valhalla/GraphHopper
   sunucusu veya Google Route Optimization API) ve sırayı Hermes'e döner. Telefon
   sapma olursa `route-core` ile çevrimdışı yeniden hesaplar.
3. **Durum senkronizasyonu:** Teslim/erteleme/iade olayları anında Hermes backend'ine
   gider. Bu prototipteki `StopStore`'un yerini Room + WorkManager ile çevrimdışı
   kuyruk alır.
4. **KVKK:** Alıcı adı, adresi ve telefonu kişisel veridir. Cihazda şifreli ve gün sonu
   silinen veri, maskeli arama, erişim kayıtları. Pilot öncesi hukuk ve bilgi
   güvenliği onayı alınmalı.
5. **Pilot:** 1 şube, 5–10 iş ortağı, 2–4 hafta. Ölçülecekler: günlük km, saat başına
   teslimat, ilk denemede teslim oranı, mesai bitiş saati, kurye memnuniyeti.

Sektör araştırması ve kuryelerin işini kolaylaştıracak diğer fikirler:
[docs/ARASTIRMA_VE_ONERILER.md](docs/ARASTIRMA_VE_ONERILER.md)
