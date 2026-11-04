# AGENTS.md — Çalışma Kuralları (Spec-Driven + TLA+)

1. **Tek doğruluk kaynağı:** `docs/specs/SPEC-001-forex-signal-platform.md`. Kod spec ile çelişirse önce spec güncellenir (PR'da gerekçe).
2. **TLA+ önce:** Durum makinesi, eşzamanlılık, teslim garantisi değişiyorsa önce `docs/tla/*.tla` güncellenir, `./scripts/tlc.sh` yeşil olmalı.
3. **Issue'suz kod yok:** Her PR bir issue'yu kapatır; issue bir spec ID'si taşır.
4. **Test önce:** Kabul kriterleri `@Tag("<SPEC-ID>")` ile JUnit/jqwik testlerine çevrilir.
5. **Mimari:** `domain` paketleri Spring'e bağımlı olamaz; modüller arası yalnızca event veya `api` paketi. `ApplicationModules.of(FspApplication.class).verify()` her build'de çalışır.
6. **Dürüstlük:** Kazanç metrikleri yalnızca backtest/walk-forward çıktısından gelir; UI'da "yatırım tavsiyesi değildir" uyarısı kalıcıdır.
7. **Stil:** Java 25, record VO'lar, sealed hiyerarşiler, `Optional` dönüşleri, JSpecify `@NullMarked`; virtual threads; hot path'te allocation yok.
