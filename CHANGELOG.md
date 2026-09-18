# Changelog

## [1.0.0] — 2026-09-18

İlk herkese açık sürüm (Paper 1.20+, Java 17+).

### Oyun
- Arena yaşam döngüsü: WAITING → STARTING → PRE_GAME → CAGED → ACTIVE → FINAL → ENDING → RESETTING
- Loot motoru (ağırlıklı tablo, oyuncu başına), harita modları (lava-rise, fragile, TNT-rain, shrinking-border, UHC hook), oyun modları (Normal, Shuffle, Swap)
- Oylama (sohbet + GUI): oyun/harita modu; parti sistemi (5 kişi); mağaza + kozmetikler (kafes, kill-message, death-cry)
- Skor tablosu, bossbar, hotbar menü, arena seçici ve istatistik GUI'leri
- Blok geri-yükleme (rollback), `.ens` replay kaydı + çevrimdışı 3D izleyici
- Envanter anlık görüntüsü: her çıkış yolunda iade (quit/restart dahil, disk yedeği)

### Komut mimarisi
- `/pof` + `/enspillars` kökleri; 6 iki-aşamalı confirm kapısı (arena-delete, arena-rollback, setspawn, replay-delete, import, reload-confirm)
- Yıkıcı işlemlerde `AUDIT actor/action/scope` log satırı
- Yetkiye saygılı tab-completion, messages.yml üzerinden tüm oyuncu metinleri (legacy `&` renkleri), `commands.yml` ile alias/description yapılandırması

### Teknik
- Depolama: YAML (varsayılan), SQLITE, MYSQL; `/enspillars import` ile tek seferlik YAML→SQL taşıma
- PlaceholderAPI genişletmesi (`%enspillars_*%`), Folia-dostu async kayıt, NMS yok (1.20.x–1.21.x dostu)
