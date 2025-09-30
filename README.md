# Keycloak Client Role Attributes Mapper v2.0.0

Bu proje, Keycloak için özel bir protocol mapper'dır. Bu mapper, kullanıcı login olduğunda çalışır ve kullanıcının belirtilen client'a ait rollerinin attribute'larını token içine dahil eder.


## Özellikler

- ✅ Kullanıcının client-specific rollerini alır
- ✅ Her rolün attribute'larını okur ve token'a ekler
- ✅ Access Token, ID Token ve UserInfo endpoint'lerinde çalışır
- ✅ Konfigürasyon seçenekleri sunar (claim adı, boş attribute'ları dahil etme)
- ✅ Structured logging kullanır
- ✅ Thread-safe implementasyon
- ✅ Hata durumlarında graceful handling
- ✅ Public SPI kullanır (internal SPI uyarısı yok)
- ✅ Keycloak 26.x+ uyumlu
- ✅ Java 21 LTS ile geliştirilmiş

## Gereksinimler

- **Java:** 21 LTS (önerilen) veya üzeri
- **Maven:** 3.6 veya üzeri
- **Keycloak:** 26.0.7+ (test edildi)
- **JDK:** Development için JDK 21+ gerekli (JRE yeterli değil)

## Kurulum

### 1. Projeyi Derle

#### Manuel Maven Build:
```bash
mvn clean package
```

### 2. Keycloak'a Deploy Et

1. Oluşturulan JAR dosyasını (`target/keycloak-client-role-attributes-mapper-2.0.0.jar`) Keycloak'ın `providers` klasörüne kopyalayın:
   ```
   KEYCLOAK_HOME/providers/keycloak-client-role-attributes-mapper-2.0.0.jar
   ```

2. Keycloak'ı yeniden başlatın:
   ```bash
   # Standalone mode
   ./kc.sh start-dev
   
   # Production mode
   ./kc.sh build
   ./kc.sh start
   ```

### 3. Mapper'ı Konfigüre Et

1. Keycloak Admin Console'a giriş yapın
2. İlgili Realm'i seçin
3. **Clients** → İlgili Client → **Client Scopes** → **Dedicated scope** → **Mappers** → **Add mapper**
4. **Mapper Type:** "Client Role Attributes" seçin
5. Konfigürasyon seçenekleri:
    - **Name:** Mapper adı (örn: "role-attributes")
    - **Claim Name:** Token'da görünecek claim adı (default: "role_attributes")
    - **Include Empty Attributes:** Boş attribute'lara sahip rolleri dahil et (default: false)
    - **Add to ID token:** ID Token'a ekle
    - **Add to access token:** Access Token'a ekle
    - **Add to userinfo:** UserInfo endpoint'ine ekle

## Token Yapısı

Mapper çalıştığında, token içinde şu formatta bir claim oluşturur:

```json
{
  "role_attributes": {
    "admin": {
      "department": ["IT", "Security"],
      "level": ["5"],
      "permissions": ["read", "write", "delete"]
    },
    "user": {
      "department": ["Sales"],
      "level": ["2"]
    }
  }
}
```

## Konfigürasyon Seçenekleri

| Özellik | Açıklama | Default |
|---------|----------|---------|
| **Claim Name** | Token içinde role attribute'larının ekleneceği claim adı | `role_attributes` |
| **Include Empty Attributes** | Boş attribute'lara sahip rolleri de dahil et | `false` |

## Geliştirme

### Proje Yapısı

```
├── pom.xml                                 # Maven konfigürasyonu
├── build.bat                              # Windows build scripti
├── build.sh                               # Linux/Mac build scripti
├── README.md                              # Bu dosya
└── src/
    └── main/
        ├── java/
        │   └── com/fatihkarakus/keycloak/mappers/
        │       └── ClientRoleAttributesMapper.java    # Ana mapper sınıfı
        └── resources/
            └── META-INF/
                └── services/
                    ├── org.keycloak.protocol.ProtocolMapper                        # Ana mapper interface
                    ├── org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper    # Access Token mapper
                    ├── org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper        # ID Token mapper
                    └── org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper      # UserInfo mapper
```

### Service Provider Dosyaları

Mapper'ın Keycloak tarafından tanınması için 4 adet service provider dosyası gereklidir:

| Dosya | Açıklama |
|-------|----------|
| `org.keycloak.protocol.ProtocolMapper` | Ana mapper interface - Admin Console'da görünmesi için |
| `org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper` | Access Token'da çalışması için |
| `org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper` | ID Token'da çalışması için |
| `org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper` | UserInfo endpoint'inde çalışması için |

Her dosyanın içeriği aynıdır: `com.fatihkarakus.keycloak.mappers.ClientRoleAttributesMapper`

### Logging

Mapper structured logging kullanır ve şu log seviyelerinde çıktı verir:

- **DEBUG:** Detaylı işlem bilgileri
- **WARN:** Rol işleme hatalarında
- **ERROR:** Kritik hatalarda

### Test Etme

1. Mapper'ı deploy edin
2. Bir client oluşturun ve rolle'ler tanımlayın
3. Rollere attribute'lar ekleyin:
   ```
   Role: admin
   Attributes:
   - department: IT,Security
   - level: 5
   - permissions: read,write,delete
   ```
4. Kullanıcıya roller atayın
5. Token alın ve `role_attributes` claim'ini kontrol edin

## Sorun Giderme

### JAR Dosyası Oluşturulmuyor
- **JDK 21+** kurulu olduğundan emin olun (JRE değil, JDK gerekli)
- Maven 3.6+ kurulu olduğundan emin olun
- **JAVA_HOME** environment variable'ının JDK 21+'yı gösterdiğinden emin olun
- `java -version` ile Java sürümünüzü kontrol edin
- `mvn clean package -X` ile detaylı log alın

### Mapper Keycloak'ta Görünmüyor
- JAR dosyası `providers` klasöründe olduğundan emin olun
- Keycloak'ı tamamen yeniden başlattığınızdan emin olun
- Keycloak loglarını kontrol edin

### Token'da Claim Görünmüyor
- Mapper'ın client'a eklendiğinden emin olun
- Token type seçeneklerinin doğru olduğundan emin olun
- Kullanıcının ilgili client için rolleri olduğundan emin olun
- Rollerin attribute'ları olduğundan emin olun

## Lisans

Bu proje MIT lisansı altında dağıtılmaktadır.

## Katkıda Bulunma

1. Bu repository'yi fork edin
2. Feature branch oluşturun (`git checkout -b feature/amazing-feature`)
3. Değişikliklerinizi commit edin (`git commit -m 'Add amazing feature'`)
4. Branch'inizi push edin (`git push origin feature/amazing-feature`)
5. Pull Request oluşturun
