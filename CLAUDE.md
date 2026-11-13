# CLAUDE.md

## Model ve subagent seçimi

Modeli işin niteliğine göre seç; varsayılanı her işe uygulama. Güncel modeller Opus 5.5, Sonnet 5.5 ve Haiku 5.5. Haiku 5.5 (7 Ekim 2026) Anthropic'e göre yüksek hacimli, dar kapsamlı ve maliyete duyarlı işler için; Sonnet'in yaklaşık yirmide biri fiyatına. Karmaşık ajan kodlamasında Sonnet ve Opus daha iyidir.

- **haiku**: kod tasarlamayan ya da çıktısı tam belirlenmiş işler.
  - Arama: dosya, sembol ve kullanım yeri (Explore ajanı); sürüm ve yapılandırma sorgulama.
  - CI ve test günlüğünden düşen testi ya da hatayı bulup özetleme.
  - Mekanik toplu düzenleme: katalog anahtarı, üretilmiş dosya, yeniden adlandırma; talimatı tam verilmiş tek dosyalık değişiklik.
  - GitHub ve TODO işleri: issue, etiket, milestone, pano; bağlantı ve belge tutarlılığı denetimi.
  - Çıktı, rapor ve uzun dosya özetleme.
- **sonnet**: planı net, kabul ölçütü yazılı görevlerin uygulanması: iskelet, CRUD rotaları, RLS ya da izin içermeyen migration, testler, bileşen önizlemeleri ve ekranlar. Çok dosyalı düzeltme turları; küçük düzeltme diff'inin hedefli yeniden incelemesi. Belge, README ve TODO metni düzenlemesi sonnet'tir; issue, etiket, milestone ve madde işaretleme haiku'dur (yukarıda).
- **opus**: güvenlik ve tasarım ağırlıklı iş.
  - Güvenlik: RLS politikaları (kiracı tablosu ve RLS politikası açan migration dahil), `can()` ve izin tablosu, oturum/OTP, denetim zinciri, kurum olarak görüntüleme, kişisel veri (KVKK, veli ve çocuk verisi, moderasyon).
  - Tasarım: mimari kararlar, spec ve plan.
  - İnceleme: bu işlerin ve bütün dalın bağımsız kod incelemesi.
- Uygulayan ile inceleyen ayrı ajandır. Güvenlik ağırlıklı PR'ın bağımsız incelemesi opus'tadır; küçük düzeltme turunun yeniden incelemesi sonnet'te.
- Haiku güvenlik ağırlıklı koda dokunmaz ve tek başına onay vermez: bulduğunu sonnet ya da opus doğrular, ürün ya da güvenlik kararı vermez.
- Subagent yalnızca bağımsız işleri paralel yürütmek ya da ana bağlamı büyük çıktılardan korumak için açılır; tek dosyalık ya da yeri bilinen işi doğrudan yap. Paralel ajan sayısını iş sayısıyla sınırla.
- Bir subagent'a iş verirken modeli açıkça belirt ve neden o modeli seçtiğini bir cümleyle söyle.
