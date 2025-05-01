package com.zoho.catalyst_plugin.service; // Use your package name

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
// Removed CredentialAttributesKt import as the helper is no longer used
import com.intellij.ide.passwordSafe.PasswordSafe;   // Recommended way to access secure store
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service responsible for securely storing and retrieving the user's
 * *application-specific session token* obtained after successful authentication
 * via the backend service. Uses IntelliJ's PasswordSafe.
 */
public class AuthService {
    private static final Logger LOG = Logger.getInstance(AuthService.class);

    // Unique service name (e.g., plugin ID) used as a namespace in the secure store.
    private static final String CREDENTIAL_SERVICE_NAME = "com.zoho.catalyst_plugin"; // Use your plugin ID or similar

    // Key attribute (acts like a username) to store the *backend's session token* under the service name.
    private static final String SESSION_TOKEN_KEY = "BackendApiSessionToken";

    private static final AuthService instance = new AuthService();

    private AuthService() { }

    public static AuthService getInstance() {
        return instance;
    }

    /**
     * Creates the CredentialAttributes object used to uniquely identify
     * the session token within the PasswordSafe using the recommended constructor.
     *
     * @return CredentialAttributes for accessing the token.
     */
    private CredentialAttributes createCredentialAttributes() {
        // Use the primary constructor taking serviceName and userName (key)
        return new CredentialAttributes(
                CREDENTIAL_SERVICE_NAME, // Your unique service namespace
                SESSION_TOKEN_KEY        // The specific key for the token within your namespace
        );
    }

    /**
     * Retrieves the stored backend session token from PasswordSafe.
     *
     * @return The stored token string, or null if not found or error occurs.
     */
    @Nullable
    public String getAuthToken() {
        LOG.debug("Attempting to retrieve session token from PasswordSafe using attributes: Service='{}', Key='{}'", CREDENTIAL_SERVICE_NAME, SESSION_TOKEN_KEY);
        CredentialAttributes attributes = createCredentialAttributes();
        String token = null;
        try {
            Credentials credentials = PasswordSafe.getInstance().get(attributes);
            if (credentials != null) {
                token = credentials.getPasswordAsString();
                if (token != null && !token.isEmpty()) {
                    LOG.debug("Session token retrieved successfully.");
                } else {
                    LOG.warn("Stored credential found but session token (password) is null or empty.");
                    token = null;
                }
            } else {
                LOG.info("No stored session token found for Service='"+ CREDENTIAL_SERVICE_NAME +"', Key='" + SESSION_TOKEN_KEY + "'");
            }
        } catch (Exception e) {
            LOG.error("Error retrieving token from PasswordSafe", e);
        }
        return token;
    }

    /**
     * Securely stores the backend session token in PasswordSafe.
     *
     * @param sessionToken The token to store. Must not be null or empty.
     * @throws IllegalArgumentException if the token is null or empty.
     */
    public void storeAuthToken(@NotNull String sessionToken) {
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            LOG.error("Attempted to store a null or empty session token.");
            throw new IllegalArgumentException("Cannot store a null or empty session token.");
        }

        LOG.debug("Attempting to store session token in PasswordSafe for Service='{}', Key='{}'", CREDENTIAL_SERVICE_NAME, SESSION_TOKEN_KEY);
        CredentialAttributes attributes = createCredentialAttributes();
        // When setting, the 'userName' in the Credentials object should ideally match the key used in attributes.
        Credentials credentials = new Credentials(SESSION_TOKEN_KEY, sessionToken);

        try {
            PasswordSafe.getInstance().set(attributes, credentials);
            LOG.info("Session token stored successfully.");
        } catch (Exception e) {
            LOG.error("Error storing token in PasswordSafe", e);
        }
    }

    /**
     * Removes the stored backend session token from PasswordSafe.
     */
    public void clearAuthToken() {
        LOG.debug("Attempting to clear stored session token from PasswordSafe for Service='{}', Key='{}'", CREDENTIAL_SERVICE_NAME, SESSION_TOKEN_KEY);
        CredentialAttributes attributes = createCredentialAttributes();
        try {
            PasswordSafe.getInstance().set(attributes, null); // Setting to null removes
            LOG.info("Session token cleared successfully.");
        } catch (Exception e) {
            LOG.error("Error clearing token in PasswordSafe", e);
        }
    }


    /**
     * Checks if a session token is currently stored.
     * @return true if a non-null token exists, false otherwise.
     */
    public boolean isSignedIn() {
        return getAuthToken() != null;
    }
}