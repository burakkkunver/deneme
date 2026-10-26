# Son kilometre (last-mile) dağıtım yazılımları: araştırma ve öneriler

## 1. Sektörde neler yapılıyor?

### Trendyol
- Trendyol Express, Trendyol'un kendi son kilometre dağıtım ağıdır. Grup günde 1
  milyonun üzerinde paket teslim ettiğini açıklıyor
  ([General Atlantic](https://generalatlantic.com/media-article/trendyol-raises-1-5-billion-valuing-the-company-at-16-5-billion/?pdf=7497)).
- İş ortaklarına verilen **Hermes** uygulamasıyla ilgili kamuya açık teknik bilgi
  bulamadım. Bu dokümandaki öneriler, Hermes'in iş ortaklarının şubeden paket alıp
  dağıttığı uygulama olduğu varsayımına dayanıyor. Hermes'te zaten olan özellikler
  ekiple konuşulup listeden çıkarılmalı.

### HepsiJET (Hepsiburada)
- Yapay zeka destekli **"Çok Araçlı Rota Optimizasyonu"** sistemi var ve TÜRKPATENT
  tarafından tescillenmiş
  ([DHA](https://www.dha.com.tr/ekonomi/hepsijet-perakendecilere-sundugu-tasima-hacmini-yuzde-97-artirdigini-duyurdu-2553908)).
- Sistem teslim ve alım noktaları arasında mesafe matrisi kuruyor, gönderileri öncelik
  derecesine ve araç tipine göre grupluyor. Alıcının seçtiği zaman aralığına göre
  teslim ediyor. Koordinatı belirsiz adresleri otomatik rotalamanın dışında
  bırakıyor ([ShiftDelete](https://shiftdelete.net/hepsijet-teslimat-hizi?amp=1)).
- **Çıkarım:** Rakip zaman penceresi ve araç tipi kısıtlarını da kullanıyor. Bizim
  prototip tek kurye ve tek araç için. Doğal bir sonraki adım zaman penceresi
  eklemek.

### Amazon
- Teslimat sürücüleri için **akıllı gözlük** geliştirdi. Gözlük paket tarama, park
  edilen yerden kapıya yürüme navigasyonu ve telefonsuz teslim fotoğrafı sağlıyor.
  Ekran yalnızca araç park edilince açılıyor
  ([Amazon](https://aboutamazon.com/news/transportation/smart-glasses-amazon-delivery-drivers)).
  Sürücülerin çektiği görüntülerin **Wellspring** adlı üretken yapay zeka platformuyla
  bina girişi ve park yeri haritası çıkarmak için kullanıldığı bildiriliyor
  ([Android Headlines](https://www.androidheadlines.com/?p=1316138)). Bu kullanım
  mahremiyet tartışmasına da yol açtı
  ([Northeast Times](https://northeasttimes.com/2026/09/30/amazon-delivery-drivers-smart-glasses-will-photograph-homes-and-passersby/)).
- **Çıkarım:** Asıl zaman kaybı yolda değil, **araçtan kapıya** geçen son 50 metrede:
  bina girişi bulmak, park, zil, kat. Rota kadar "teslim noktası bilgisi" de önemli.

### UPS – ORION
- UPS'in rota optimizasyon sistemi ORION'un rota başına günde 7–8 mil kısalma sağladığı
  ve yılda ~100 milyon mil, ~10 milyon galon yakıt tasarrufu getirdiği belirtiliyor.
  UPS'e göre sürücü başına günde 1 mil azalma yılda 50 milyon dolara kadar tasarruf
  demek ([Governing](https://www.governing.com/archive/gov-ups-government-fleet-waste-management-route-optimization.html),
  [NextBillion.ai](https://nextbillion.ai/feeds/blog/ups-orion-ai-route-optimization-savings)).
  Rakamlar şirket beyanı, bağımsız denetim yok.
- Aynı kaynaklar sürücülerin önerilen rotayı her zaman optimal bulmadığını da
  aktarıyor. **Çıkarım:** Kurye sırayı değiştirebilmeli (prototipte "Sıradaki yap"
  var) ve sistem bu tercihlerden öğrenmeli.

### Hazır araçlar (referans)
- Ticari: Google Maps Platform Route Optimization API, Routific, OptimoRoute, Onfleet,
  Circuit.
- Açık kaynak: OSRM, Valhalla, GraphHopper (yol ağı), VROOM, Google OR-Tools (araç
  rotalama ve zaman pencereleri).

## 2. Kuryelerin işini kolaylaştıracak diğer öneriler

Etki ve zorluk kabaca tahmin edildi. Öncelik pilot verisiyle netleşir.

| # | Öneri | Ne çözer? | Etki | Zorluk |
|---|---|---|---|---|
| 1 | **Rota sırasına göre araç yükleme** | Şubede barkod okutunca "37. durak, arka sol" etiketi basılır. Her durakta paket arama süresi biter. | Yüksek | Düşük |
| 2 | **Teslimattan önce müşteri bildirimi + "evde değilim" seçenekleri** | Bildirimde ETA yer alır. Müşteri komşuya, kapıcıya veya lockera yönlendirebilir ya da başka saat seçebilir. Başarısız teslimat ve tekrar dağıtım azalır. | Çok yüksek | Orta |
| 3 | **Teslim noktası hafızası** | Bina giriş fotoğrafı, kapı kodu, "zil bozuk, arayın" gibi notlar kuryeler arasında paylaşılır. Pin, geçmiş başarılı teslim GPS'leriyle düzeltilir. | Yüksek | Orta |
| 4 | **Yapay zeka ile adres düzeltme** | Serbest metin adres (ör. "market yanı, 3. kat") LLM ile ayrıştırılıp normalize edilir. Belirsiz adresler dağıtımdan **önce** işaretlenir ve müşteriye "konumunu paylaş" linki gider. | Yüksek | Orta |
| 5 | **Zaman pencereli rota** | "İş yerleri 09–18, evler akşam" ve müşteri saat tercihi rota hesabına girer (VRP-TW). | Yüksek | Orta |
| 6 | **Aynı binayı gruplama** | "Bu binada 3 paket var" uyarısıyla tek park, tek giriş. | Orta | Düşük |
| 7 | **Dijital teslim kanıtı** | Fotoğraf, OTP/SMS kodu veya imza. "Paket gelmedi" itirazlarını azaltır. | Orta | Düşük |
| 8 | **Maskeli arama** | Kurye müşteriyi gerçek numarayı görmeden arar. KVKK uyumu sağlar. | Orta | Orta |
| 9 | **Tek elle / sesli kullanım** | Büyük butonlar, "teslim edildi" sesli komutu. Motor üzerinde güvenli kullanım. | Orta | Orta |
| 10 | **Kazanç ve performans paneli** | Günlük paket, km ve tahmini hakediş. Şeffaflık ve motivasyon sağlar. | Orta | Düşük |
| 11 | **Gün sonu otomatik mutabakat** | İade listesi şubeye otomatik gider. Paketler kurye dönmeden hazırlanır. | Orta | Düşük |
| 12 | **Hızlı sorun bildirimi** | Hasarlı paket veya yanlış adres tek tuşla, fotoğrafla şubeye bildirilir. | Orta | Düşük |

**Öneri sırası:** Önce rota + araç yükleme sırası (1) birlikte pilotlanır, çünkü
ikisi aynı veriyi kullanır. Sonra en büyük maliyet kalemi olan başarısız teslimatı
azaltan müşteri bildirimi (2) ve adres düzeltme (4) gelir.

## 3. Müdüre sunarken

1. **Problem ve rakam:** "Bir iş ortağı günde X paket, Y km. Başarısız teslimatlar
   tekrar dağıtım maliyeti yaratıyor." Şubeden gerçek rakam alınabilirse çok güçlü olur.
2. **Canlı demo (5 dk):** README'deki senaryo.
3. **Dürüst sınırlar:** Demo veri, demo sunucular, tasarruf rakamının pilotta ölçüleceği.
4. **Somut istek:** 1 pilot şube, anonimleştirilmiş geçmiş teslimat verisi, Hermes
   ekibinden bir teknik kontak, KVKK onay süreci.
