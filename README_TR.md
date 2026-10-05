# MikasRevs Phone 2 — MRPhone / MPhone

**Minecraft 1.20.1 · Forge · Java 17 · 0.9.4 beta**

[English README](README.md) · [Değişiklikler](CHANGELOG.md)

Minecraft içinde çalışan telefon: numara ve kişiler, mesajlar ve gruplar, sesli/görüntülü aramalar, bildirimler, kamera ve galeri, sosyal uygulamalar, Marketplace ve eşya takası. PIN, Rahatsız Etmeyin, gerçek saat/Minecraft saati seçimi, telefon modelleri ve kılıflar da bulunur. Türkçe, İngilizce, Almanca, İspanyolca, Fransızca, İtalyanca, Lehçe, Brezilya Portekizcesi, Rusça, Basitleştirilmiş Çince ve Geleneksel Çince desteklenir.

Deneysel **iPhone Duo** bu sürümde Creative envanterinde gizlidir. Kaynak kodu geliştirme için korunur; ayrı telefon hesabı oluşturmaz.

## Kurulum

MRPhone JAR dosyasını Forge istemcisine ve sunucuya kur. İki tarafta aynı MRPhone sürümü kullanılmalı. Kaynak ZIP dosyası oyun içine kurulmaz.

- YouTube için istemcide **MCEF Forge 2.1.6 / 1.20.1** gerekir. İlk açılışta Chromium indirmesinin tamamlanmasını bekle.
- Mikrofonla telefon görüşmesi için **Simple Voice Chat** istemci ve sunucuda kurulup yapılandırılmalı.
- Banka için **Lightman's Currency** veya **Kuro's Economy** kurulabilir. Telefon mevcut hesapları kullanır; transfer ücreti almaz. İkisi birlikteyse Lightman's seçilir.

Bu modlar MRPhone JAR'ına gömülmez. Geliştirmede kullanılan sürümler ve resmi indirme bağlantıları [İngilizce README](README.md#installation) içinde bulunur.

## Derleme

64-bit **JDK 17** kur; proje klasöründe PowerShell aç:

```powershell
./gradlew.bat build --console=plain
```

Sonuç: `build/libs/MRPhone 2 0.9.4 beta.jar`. Gradle wrapper projede bulunur; ayrı Gradle kurulumu gerekmez. İlk derleme internet gerektirir. Normal derleme için Blender, Python veya Node.js gerekmez.

```powershell
./gradlew.bat runClient
./gradlew.bat check
./gradlew.bat runGameTestServer -PphoneEconomy=none --console=plain
./gradlew.bat runGameTestServer -PphoneEconomy=lightmans --console=plain
./gradlew.bat runGameTestServer -PphoneEconomy=kuros --console=plain
./gradlew.bat runGameTestServer -PphoneEconomy=both --console=plain
```

`build`, mevcut regresyon kontrollerini çalıştırır. GitHub Actions derlemeyi ve dört ekonomi yapılandırmasını sınar. Gerçek mikrofon, canlı video ve YouTube oynatımı iki gerçek istemciyle ayrıca denenmelidir.

Telefon verileri oyuncu ve dünya kaydına bağlıdır; cihaz/kılıf değişince yeni hesap açılmaz. Bu sürüm harici MySQL veya REST sunucusu gerektirmez. Yedek alırken oyuncu kayıtları, telefon işlem günlükleri ve ekonomi modunun kayıtlarıyla birlikte **dünyanın tamamını** yedekle.

## Lisans

Proje, özel [MRPhone Source-Available License 1.0](LICENSE.txt) ile **kaynağı görünür** olarak yayımlanır. Kod incelenebilir, derlenebilir ve kişisel kullanım için değiştirilebilir. Orijinal veya değiştirilmiş modu ve proje kodunu yeniden yayımlamak/dağıtmak için hak sahibinden önceden yazılı izin gerekir. Bu şart CurseForge, Modrinth ve diğer yayın hizmetlerini de kapsar.

GitHub'ın herkese açık depolar için tanıdığı kaynak fork'u ve katkı hakları korunur; fork oluşturmak ayrı bir mod sürümü yayımlama izni vermez. Modpack'ler resmi projeye referans verip dosyaları izinli platform özellikleriyle indirebilir; mod dosyasını ayrıca barındırmak veya paketlemek izin gerektirir.

Bu model, yeniden dağıtıma izin veren açık kaynak lisanslarından farklıdır. Fontların ve üçüncü taraf bileşenlerin mevcut lisansları [üçüncü taraf bildirimleri](THIRD_PARTY_NOTICES.md) içinde korunur. Yeniden yayımlamadan önce tam lisans metni okunmalıdır.
