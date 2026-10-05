# MRPhone — Case Collection 02

28 Eylül 2026

Beş kılıf yeniden tasarlandı: Bunny Blush (pastel gül ve krem tavşan), Carbon Guard (grafit örgü, mercan detaylar ve darbe köşeleri), Forest Leather (koyu yeşil, pirinç tonunda dikiş ve yaprak amblemi), Midnight Cyber (lacivert, turkuaz devre çizgileri), Bunny Block (buz mavisi, lila voxel tavşan).

Classic ve Model 01–05 için toplam 30 ayrı OBJ ve 30 ayrı GUI kaplaması üretilir. Arka kamera boşlukları gerçek OBJ kamera bileşenlerinden hesaplanır; Model 02/03 kapsül, Model 05 dairesel kamera yuvasına sahiptir. Kasa eğriliği, düğme açıklığı ve donanım vurguları model bazında ayarlanır. Ekran geometrisi ve mevcut elde tutuş/arama/açılış pozları korunur. Kılıf kimlikleri değişmediğinden eski kayıtlar yeni tasarımları kullanır.

`collection-preview.png`, dışa aktarılan gerçek OBJ'lerin yazılımsal renderlarını ve oyunda kullanılan GUI kaplamalarını gösterir. Minecraft ekran görüntüsü değildir. `<model>_<style>_3d.png` dosyaları 30 eşleşmenin ayrı arka perspektif önizlemeleridir.

## Düzenlenebilir kaynak

Ana tasarım kaynağı `tools/PhoneCaseDesigner.java` dosyasıdır. Geometri, malzeme desenleri, ikonlar ve GUI çerçeveleri burada parametrik olarak oluşturulur; Minecraft çalışırken kodla üretilmez. Java 17 veya üstüyle, proje kökünde:

```powershell
java tools/PhoneCaseDesigner.java
```

Alternatif:

```powershell
./tools/generate_phone_cases.ps1 -JavaExecutable 'C:/path/to/jdk/bin/java.exe'
```

`generate_phone_case_models.ps1` dispatcher dosyalarını hazırladıktan sonra aynı tasarım üreticisini çağırır. Telefonun Blender kaynağı değiştirilip tekrar dışa aktarılırsa, son adımda kılıf üreticisini yeniden çalıştırın: yeni kamera ve tuş ölçülerine göre OBJ aksesuarlarını yeniden uyarlar. Blender dosyalarındaki eski optional-case koleksiyonları bu yeni tasarımların kaynağı değildir.

## GUI ve animasyon

Arama alanı 280 ms boyunca merkezden sağa ve sola genişler. Boşken odağı kaybettiğinde veya Escape ile kapatıldığında ters animasyon oynar. Animasyon sürerken tekrar yön değiştirmek konum sıçramasına yol açmaz; animasyonlar kapalıysa anında geçiş uygulanır.

Kılıf, telefonun bütün ana GUI ekranlarında ve gelen arama telefon animasyonunda seçili kasayla eşleşir. Tavşan kulakları için pencere yüksekliğinde yer ayrılır. 708 x 1440 uygulama dikdörtgeninin içindeki yuvarlatılmış ekran açıklığı şeffaftır; dört köşe opak kılıf kenarıyla örtülür. Böylece dikdörtgen duvar kâğıdı ve uygulama arka planı yuvarlak köşelerden taşmaz.

## Doğrulama

- IntelliJ derlemesi başarılı.
- `gradlew.bat check build --offline --console=plain` başarılı.
- `PhoneCaseChecks`: 30 model/kaplama bağlantısı; ekran açıklığı; OBJ indisleri, yüzey yönleri, benzersiz grup adları ve atlas UV sınırları; arama animasyonu ve tersine çevirme.
- Mevcut tüm regresyon testleri başarılı.
- Canlı Minecraft oturumunda görsel/etkileşim testi yapılmadı; önizlemeler doğrudan dışa aktarılan kaynaklardan oluşturuldu.

Paket: `build/libs/MRPhone 2 0.9.3 beta.jar`.
28 Eylül köşe düzeltmesi: Ekran açıklığını yeniden dikdörtgen olarak silen adım kaldırıldı. 30 kasa/kılıf eşleşmesinin dört köşesinde 9 x 9 piksellik opak maske kontrolü ve yuvarlatılmış iç bölgede şeffaflık kontrolü eklendi.
