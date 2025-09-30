package com.fatihkarakus.keycloak.mappers;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenResponseMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;

import java.util.*;

/**
 * Keycloak Custom Protocol Mapper
 *
 * Bu mapper kullanıcı login olduğunda çalışır ve kullanıcının client'a ait rollerini alarak
 * her rolün attribute'larını token içinde role_attributes adlı bir claim olarak ekler.
 *
 * Özellikler:
 * - Access Token, ID Token ve UserInfo endpoint'lerinde çalışır
 * - Sadece belirtilen client için roller ve attribute'ları dahil eder
 * - Boş attribute'lara sahip roller dahil edilmez
 * - Structured logging kullanır
 * - Thread-safe implementasyon
 *
 * @author Fatih Karakuş
 * @version 1.0.0
 */
public class ClientRoleAttributesMapper
        extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "oidc-client-role-attributes-mapper";

    private static final Logger logger = Logger.getLogger(ClientRoleAttributesMapper.class);


    // Default claim name - mapper konfigürasyonunda değiştirilebilir
    private static final String DEFAULT_CLAIM_NAME = "role_attributes";

    // Configuration property keys
    private static final String CLAIM_NAME_PROPERTY = "claim.name";
    private static final String INCLUDE_EMPTY_ATTRIBUTES = "include.empty.attributes";

    /**
     * Bu mapper'ın benzersiz ID'sini döndürür
     *
     * @return Provider ID
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * Keycloak admin console'da görünecek display name
     *
     * @return Display name
     */
    @Override
    public String getDisplayType() {
        return "Client Role Attributes";
    }

    /**
     * Admin console'da gösterilecek yardım metni
     *
     * @return Help text
     */
    @Override
    public String getHelpText() {
        return "Kullanıcının mevcut client'a ait rollerinin attribute'larını token içine dahil eder. " +
               "Her rol için attribute'lar ayrı bir map olarak claim içinde yer alır.";
    }

    /**
     * Mapper kategorisini döndürür (Keycloak 22.x için gerekli)
     *
     * @return Display category
     */
    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    /**
     * Mapper'ın priority'sini döndürür
     */
    @Override
    public int getPriority() {
        return 100;
    }

    /**
     * Mapper konfigürasyon özelliklerini tanımlar
     *
     * @return Konfigürasyon özellikleri listesi
     */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        List<ProviderConfigProperty> properties = new ArrayList<>();

        // Claim name konfigürasyonu
        ProviderConfigProperty claimNameProperty = new ProviderConfigProperty();
        claimNameProperty.setName(CLAIM_NAME_PROPERTY);
        claimNameProperty.setLabel("Claim Name");
        claimNameProperty.setType(ProviderConfigProperty.STRING_TYPE);
        claimNameProperty.setDefaultValue(DEFAULT_CLAIM_NAME);
        claimNameProperty.setHelpText("Token içinde role attribute'larının ekleneceği claim adı");
        properties.add(claimNameProperty);

        // Boş attribute'ları dahil etme seçeneği
        ProviderConfigProperty includeEmptyProperty = new ProviderConfigProperty();
        includeEmptyProperty.setName(INCLUDE_EMPTY_ATTRIBUTES);
        includeEmptyProperty.setLabel("Include Empty Attributes");
        includeEmptyProperty.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        includeEmptyProperty.setDefaultValue("false");
        includeEmptyProperty.setHelpText("Boş attribute'lara sahip rolleri de dahil et");
        properties.add(includeEmptyProperty);

        // Standard OIDC properties'leri ekle
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(properties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(properties, ClientRoleAttributesMapper.class);

        return properties;
    }


    /**
     * Ana setClaim metodu - token'a claim ekler
     * 
     * @param token Token instance
     * @param mappingModel Mapper konfigürasyonu  
     * @param userSession User session
     * @param session Keycloak session
     * @param clientSessionCtx Client session context
     */
    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
                           UserSessionModel userSession, KeycloakSession session,
                           ClientSessionContext clientSessionCtx) {
        
        try {
            // Client'ı ClientSessionContext'ten al
            ClientModel client = null;
            if (clientSessionCtx != null && clientSessionCtx.getClientSession() != null) {
                client = clientSessionCtx.getClientSession().getClient();
            }
            
            // Eğer ClientSessionContext'ten alamazsak alternatif yöntem kullan
            if (client == null) {
                client = getClientFromContext(token, session, userSession);
            }
            
            UserModel user = userSession.getUser();
            RealmModel realm = session.getContext().getRealm();

            // Client bulunamazsa mapper'ı çalıştırma
            if (client == null) {
                logger.warnf("Could not determine client for user session: %s", userSession.getId());
                return;
            }

            logger.debugf("Processing role attributes for user: %s, client: %s, realm: %s",
                         user.getUsername(), client.getClientId(), realm.getName());

            // Konfigürasyon değerlerini oku
            String claimName = getClaimName(mappingModel);
            boolean includeEmptyAttributes = shouldIncludeEmptyAttributes(mappingModel);

            // Kullanıcının bu client için rollerini al
            Set<RoleModel> clientRoles = user.getClientRoleMappingsStream(client).collect(java.util.stream.Collectors.toSet());

            if (clientRoles == null || clientRoles.isEmpty()) {
                logger.debugf("No client roles found for user: %s in client: %s",
                             user.getUsername(), client.getClientId());
                return;
            }

            // Role attribute'larını topla
            Map<String, Map<String, List<String>>> roleAttributesMap = collectRoleAttributes(
                clientRoles, includeEmptyAttributes);

            // Eğer hiç attribute bulunamazsa claim ekleme
            if (roleAttributesMap.isEmpty()) {
                logger.debugf("No role attributes found for user: %s in client: %s",
                             user.getUsername(), client.getClientId());
                return;
            }

            // Claim'i token'a ekle
            addClaimToToken(token, claimName, roleAttributesMap);

            logger.debugf("Successfully added role attributes claim '%s' with %d roles for user: %s",
                         claimName, roleAttributesMap.size(), user.getUsername());

        } catch (Exception e) {
            // Hata durumunda loglama yap ama token oluşturmayı engelleme
            logger.errorf(e, "Error processing role attributes for user session: %s",
                         userSession.getId());
        }
    }


    /**
     * Konfigürasyondan claim name'i alır
     *
     * @param mappingModel Mapper konfigürasyonu
     * @return Claim name
     */
    private String getClaimName(ProtocolMapperModel mappingModel) {
        String claimName = mappingModel.getConfig().get(CLAIM_NAME_PROPERTY);
        return (claimName != null && !claimName.trim().isEmpty()) ? claimName.trim() : DEFAULT_CLAIM_NAME;
    }

    /**
     * Boş attribute'ların dahil edilip edilmeyeceğini kontrol eder
     *
     * @param mappingModel Mapper konfigürasyonu
     * @return true if empty attributes should be included
     */
    private boolean shouldIncludeEmptyAttributes(ProtocolMapperModel mappingModel) {
        String includeEmpty = mappingModel.getConfig().get(INCLUDE_EMPTY_ATTRIBUTES);
        return "true".equalsIgnoreCase(includeEmpty);
    }

    /**
     * Role'lerden attribute'ları toplar
     *
     * @param roles Client role'leri
     * @param includeEmptyAttributes Boş attribute'ları dahil et
     * @return Role attribute'ları map'i
     */
    private Map<String, Map<String, List<String>>> collectRoleAttributes(
            Set<RoleModel> roles, boolean includeEmptyAttributes) {

        Map<String, Map<String, List<String>>> roleAttributesMap = new HashMap<>();

        for (RoleModel role : roles) {
            try {
                Map<String, List<String>> attributes = role.getAttributes();

                // Null check
                if (attributes == null) {
                    if (includeEmptyAttributes) {
                        roleAttributesMap.put(role.getName(), new HashMap<>());
                    }
                    continue;
                }

                // Boş attribute'ları filtrele
                Map<String, List<String>> filteredAttributes = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
                    List<String> values = entry.getValue();
                    if (values != null && (!values.isEmpty() || includeEmptyAttributes)) {
                        // Defensive copy
                        filteredAttributes.put(entry.getKey(), new ArrayList<>(values));
                    }
                }

                // Eğer attribute varsa veya boş attribute'lar dahil edilecekse ekle
                if (!filteredAttributes.isEmpty() || includeEmptyAttributes) {
                    roleAttributesMap.put(role.getName(), filteredAttributes);
                }

            } catch (Exception e) {
                logger.warnf(e, "Error processing attributes for role: %s", role.getName());
                // Bu role'ü atla ama diğerlerine devam et
            }
        }

        return roleAttributesMap;
    }

    /**
     * Token'a claim ekler - token tipine göre uygun metodu kullanır
     *
     * @param token Token instance
     * @param claimName Claim adı
     * @param roleAttributes Role attribute'ları
     */
    private void addClaimToToken(IDToken token, String claimName,
                                Map<String, Map<String, List<String>>> roleAttributes) {

        if (token instanceof AccessToken) {
            // Access Token için
            ((AccessToken) token).getOtherClaims().put(claimName, roleAttributes);
        } else {
            // ID Token ve UserInfo için
            token.getOtherClaims().put(claimName, roleAttributes);
        }
    }

    /**
     * Token ve session bilgilerinden client'ı alır
     *
     * @param token Token instance
     * @param session Keycloak session
     * @param userSession User session
     * @return ClientModel
     */
    private ClientModel getClientFromContext(IDToken token, KeycloakSession session, UserSessionModel userSession) {
        // Önce token'dan client bilgisini almaya çalış
        if (token instanceof AccessToken) {
            AccessToken accessToken = (AccessToken) token;
            String clientId = accessToken.getIssuedFor();
            if (clientId != null) {
                ClientModel client = session.getContext().getRealm().getClientByClientId(clientId);
                if (client != null) {
                    return client;
                }
            }
        }

        // Token'dan alamazsak, user session'dan client session'ları kontrol et
        if (userSession != null && userSession.getAuthenticatedClientSessions() != null) {
            // İlk client session'ı al (genellikle tek bir client olur)
            for (AuthenticatedClientSessionModel clientSession : userSession.getAuthenticatedClientSessions().values()) {
                return clientSession.getClient();
            }
        }

        // Son çare olarak realm'in default client'ını döndür (bu durumda mapper çalışmayabilir)
        logger.warnf("Could not determine client from token or session context");
        return null;
    }
}
