# EnsPillars 1.0.0

Paper 1.20+ için ücretsiz, modüler **Pillars of Fortune** mini-oyunu. MIT lisanslı.

Oyun içinde rehberli kurulumla arenalar kur, oyuncular oyun/harita moduna oy versin, partiler yönet, kozmetik sat, istatistik tut — ve her maçı çevrimdışı 3D izleyicide tekrar izlenebilen replay olarak kaydet.

- 🌍 Dil: **Türkçe** | [English](README.md)
- 💬 Destek: [Discord](https://discord.gg/xnE8zYkCyz)
- 📊 Metrikler: [bStats](https://bstats.org/plugin/bukkit/EnsPillars/34112)

## Özellikler

- **Arena yaşam döngüsü** — `WAITING → STARTING → PRE_GAME → CAGED → ACTIVE → FINAL → ENDING → RESETTING`; kurma, rehberli GUI kurulumu (spawn, merkez, izleyici, sınır, süre, mod), açma/kapama, ışınlanarak inceleme
- **Oyun ve harita modları** — Normal, Shuffle, Swap; Lava Rise, Fragile, TNT Rain, Shrinking Border, UHC hook
- **Oylama** — sohbet + GUI ile oyun/harita oyu, bekleme süreli
- **Parti sistemi** — kurma, davet, kabul/ret, toplu arena girişi (en fazla 5 kişi)
- **Mağaza ve kozmetikler** — kafesler, öldürme mesajları, ölüm çığlıkları; coin ekonomisi, rütbeye özel eşyalar
- **İstatistik ve izleme** — kalıcı istatistikler, galibiyet serileri, sayfalı liderlik tablosu, izleme menüsü
- **Replay** — keyframe'li ikili `.ens` kaydı, çökmede yarım replay kurtarma, saklama/boyut budaması, çevrimdışı 3D web izleyici (three.js gömülü)
- **Güvenlik** — yıkıcı komutlarda iki aşamalı onay (`arena delete|rollback`, `replay delete`, `setspawn`, `import`), denetim kaydı, her çıkış yolunda envanter anlık görüntüsü/iadesi (çıkış/restart dahil), blok geri-yükleme

## Gereksinimler

- **Sunucu:** Paper 1.20.1+ (1.20.x–1.21.x uyumlu, NMS yok), Java 17+
- **Opsiyonel:** PlaceholderAPI (`%enspillars_*%` değişkenleri)
- **Derleme:** Gradle'i çalıştırmak için JDK 17–21 (derleme Java 17 toolchain kullanır)

## Kurulum

1. `EnsPillars-1.0.0.jar`'ı indir (GitHub Releases) ya da derle: `./gradlew shadowJar` → `build/libs/`
2. Sunucunun `plugins/` klasörüne at, yeniden başlat
3. `plugins/EnsPillars/config.yml`'yi isteğine göre düzenle

## Hızlı başlangıç (arena kurulumu, tamamı oyun içinden)

1. `/pof arena create <id>`
2. `/pof arena setup <id>` — kurulum moduna girer (envanterin yedeklenir, çıkışta iade edilir)
3. Yönetim GUI'sinde: oyuncu sayısı, sınır, süre ve modları +/- butonlarıyla ayarla
4. Center / Spectator'a tıkla, sonra dünyada bir bloğa tıklayarak her noktayı işaretle
5. Spawn yakalama: her spawn için bir bloğa tıkla (blok üstü yükseklik: `setup.spawn-height`); ender-eye aracı spawn'lar arası ışınlanarak inceleme yapar
6. Durum öğesi yeşilken Kaydet & Etkinleştir
7. `/pof setspawn` — opsiyonel global dönüş noktası (arenada lobi/spawn yoksa kullanılır)

Tek arena yeniden yükleme (`reloadSingle`) diğer süren oyunlara dokunmaz. Oyunlar sürerken `/enspillars reload` onay ister.

## Komutlar

| Komut | Açıklama |
|---|---|
| `/pof` | Ana menü |
| `/pof join [arena]` · `autojoin` · `leave` · `menu` | Oyuna katıl |
| `/pof stats` · `shop` · `vote <game\|map> <değer>` | İstatistik, mağaza, oylama |
| `/pof party create\|invite\|accept\|decline\|leave\|join` | Parti |
| `/pof spectate [oyuncu]` | Maç izle |
| `/pof replay list\|latest\|open\|download\|delete` | Replay (`delete` yetkili + onaylı) |
| `/pof arena list\|info\|create\|delete\|enable\|disable\|tp\|setup\|edit\|rollback` | Arena yönetimi |
| `/pof setup <id>` · `/pof setspawn` · `/pof forcestart` | Kurulum kısayolu, global spawn, zorla başlat |
| `/enspillars` · `help` · `reload <…>` · `import` · `debug` | Bilgi ve bakım |

Kökler, aliaslar ve açıklamalar `commands.yml` ile yapılandırılır.

## İzinler

| Node | Varsayılan | Açıklama |
|---|---|---|
| `enspillars.use` | true | Temel komut erişimi |
| `enspillars.vote` · `enspillars.party` · `enspillars.cosmetics` · `enspillars.stats` · `enspillars.replay` | true | Oyuncu özellikleri |
| `enspillars.forcestart` | op | Oyunu zorla başlatma |
| `enspillars.admin` (+ `reload`, `import`, `arena`, `debug`, `setup` alt izinleri) | op | Yönetim |
| `enspillars.cages.gold` · `enspillars.cages.netherite` · `enspillars.deathcries.dragon` | op | Rütbeye özel kozmetikler |

## Depolama

`storage.type`: `YAML` (varsayılan), `SQLITE` ya da `MYSQL` (`config.yml` içinde `storage.mysql`).
Sonradan SQL'e mi geçiyorsun? `/enspillars import` (izni `enspillars.admin.import`, onaylı) `players.yml` + `cosmetics.yml`'yi bir seferde taşır.

## Üretim notları

- Dünya başına tek arena şiddetle önerilir: vanilla dünya sınırı dünya başına ortaktır, aynı dünyadaki iki aktif arena sınırı çekişir (açılışta uyarı loglanır).
- Replay web izleyici varsayılan `127.0.0.1`'e bağlanır. Herkese açık adrese bağlanırsan `replay.web.auth-token` belirle; tokensiz isteklere `401` döner.
- Replay'ler delta anlık görüntüler kaydeder (her `replay.keyframe-interval` tick'te tam keyframe), çökmede yarım replay olarak kurtulur, `replay.retention-days` / `replay.max-total-mb` ile budanır.
- Oyuncu envanterleri (içerik, zırh, XP, efekt, oyun modu, uçuş) girişte anlık görüntülenir ve çıkış/restart dahil her çıkış yolunda iade edilir (`snapshots.yml` disk yedeği).
- Maç ortasında çıkmak mağlubiyet sayılır, galibiyet serisi çıkışla korunamaz.
- İstatistik otomatik kayıt aralığı: `settings.auto-save-seconds` (0 kapatır).
- Güncelleme denetimi varsayılan kapalıdır; Hangar/Modrinth slug'ı belli olunca `config.yml → update-check`'ten açılır.

## Destek

- Discord: https://discord.gg/xnE8zYkCyz
- Hata ve öneriler: GitHub Issues

## Lisans

MIT — bkz. [LICENSE](LICENSE).
