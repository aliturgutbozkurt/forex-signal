# ADR-0001 — Modular Monolith: Spring Modulith + Hexagonal + jMolecules

- **Durum:** Accepted · 2026-11-04

## Bağlam
Düşük gecikme (in-process çağrı), net bounded context sınırları ve ileride servis ayrıştırma esnekliği gerekiyor.

## Karar
- Tek deploy edilebilir Spring Boot 4.1 uygulaması; her bounded context bir Modulith **application module** (`com.fsp.<context>`).
- Modül içi Hexagonal: `domain` (saf Java, framework'süz), `application` (use case + port), `adapter.in/out`.
- jMolecules annotasyonları (`@AggregateRoot`, `@ValueObject`, `@DomainEvent`, `@Port`, `@Adapter`) + ArchUnit kuralları.
- Modüller arası iletişim: domain event (Event Publication Registry ile outbox) veya modülün `api` paketi.

## Sonuçlar
+ Mikroservis ağ gecikmesi yok; tek tx sınırı; Modulith `verify()` ile sınır ihlali CI'da yakalanır.
− Tek ölçekleme birimi → yoğun modül (`marketdata`) ileride Kafka externalization ile ayrılabilir.
