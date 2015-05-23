package com.tw.go.authentication;

import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.io.IOUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

@Extension
public class SampleAuthenticationPluginImpl implements GoPlugin {
    private static Logger LOGGER = Logger.getLoggerFor(SampleAuthenticationPluginImpl.class);

    public static final String PLUGIN_ID = "sample.authenticator";
    public static final String EXTENSION_NAME = "authentication";
    private static final List<String> goSupportedVersions = asList("1.0");

    public static final String PLUGIN_CONFIGURATION = "go.authentication.plugin-configuration";
    public static final String AUTHENTICATE_USER = "go.authentication.authenticate-user";

    public static final String WEB_REQUEST_INDEX = "index";
    public static final String WEB_REQUEST_AUTHENTICATE = "authenticate";

    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int REDIRECT_RESPONSE_CODE = 302;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;

    private GoApplicationAccessor goApplicationAccessor;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        this.goApplicationAccessor = goApplicationAccessor;
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        String requestName = goPluginApiRequest.requestName();
        if (requestName.equals(PLUGIN_CONFIGURATION)) {
            return handlePluginConfigurationRequest();
        } else if (requestName.equals(AUTHENTICATE_USER)) {
            return handleAuthenticateUserRequest(goPluginApiRequest);
        } else if (requestName.equals(WEB_REQUEST_INDEX)) {
            return handleSetupLoginWebRequest(goPluginApiRequest);
        } else if (requestName.equals(WEB_REQUEST_AUTHENTICATE)) {
            return handleAuthenticateWebRequest(goPluginApiRequest);
        }
        return renderResponse(404, null, null);
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return getGoPluginIdentifier();
    }

    private GoPluginApiResponse handlePluginConfigurationRequest() {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("display-name", "Sample");
        configuration.put("supports-password-based-authentication", true);
        configuration.put("supports-user-search", false);
        return renderResponse(SUCCESS_RESPONSE_CODE, null, JSONUtils.toJSON(configuration));
    }

    private GoPluginApiResponse handleAuthenticateUserRequest(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> requestBodyMap = (Map<String, Object>) JSONUtils.fromJSON(goPluginApiRequest.requestBody());
        String username = (String) requestBodyMap.get("username");
        String password = (String) requestBodyMap.get("password");
        if (username.equals("test") && password.equals("test")) {
            Map<String, Object> userMap = new HashMap<String, Object>();
            userMap.put("user", getUserJSON("test", "display name", ""));
            return renderResponse(SUCCESS_RESPONSE_CODE, null, JSONUtils.toJSON(userMap));
        } else {
            return renderResponse(SUCCESS_RESPONSE_CODE, null, null);
        }
    }

    private GoPluginApiResponse handleSetupLoginWebRequest(GoPluginApiRequest goPluginApiRequest) {
        try {
            String responseBody = getFileContents("/views/login.html");
            return renderResponse(SUCCESS_RESPONSE_CODE, null, responseBody);
        } catch (Exception e) {
            LOGGER.error("Error occurred while Login setup.", e);
            return renderResponse(INTERNAL_ERROR_RESPONSE_CODE, null, null);
        }
    }

    private GoPluginApiResponse handleAuthenticateWebRequest(final GoPluginApiRequest goPluginApiRequest) {
        try {
            String verificationCode = goPluginApiRequest.requestParameters().get("verification_code");
            if (verificationCode == null || verificationCode.trim().isEmpty() || !verificationCode.equals("123456")) {
                return renderRedirectResponse(getServerBaseURL() + "/go/auth/login?login_error=1");
            }

            String displayName = "test";
            String fullName = "display name";
            String emailId = "";
            emailId = emailId == null ? emailId : emailId.toLowerCase().trim();

            authenticateUser(displayName, fullName, emailId);

            return renderRedirectResponse(getServerBaseURL());
        } catch (Exception e) {
            LOGGER.error("Error occurred while Login authenticate.", e);
            return renderResponse(INTERNAL_ERROR_RESPONSE_CODE, null, null);
        } finally {
            delete();
        }
    }

    private GoPluginApiResponse renderRedirectResponse(String redirectURL) {
        Map<String, String> responseHeaders = new HashMap<String, String>();
        responseHeaders.put("Location", redirectURL);
        return renderResponse(REDIRECT_RESPONSE_CODE, responseHeaders, null);
    }

    private void authenticateUser(String displayName, String fullName, String emailId) {
        Map<String, Object> userMap = new HashMap<String, Object>();
        userMap.put("user", getUserJSON(displayName, fullName, emailId));
        GoApiRequest authenticateUserRequest = createGoApiRequest("go.processor.authentication.authenticate-user", JSONUtils.toJSON(userMap));
        GoApiResponse authenticateUserResponse = goApplicationAccessor.submit(authenticateUserRequest);
        // handle error
    }

    private void store(String verificationCode) {
        Map<String, Object> requestMap = new HashMap<String, Object>();
        requestMap.put("plugin-id", PLUGIN_ID);
        Map<String, Object> sessionData = new HashMap<String, Object>();
        sessionData.put("verification-code", verificationCode);
        requestMap.put("session-data", sessionData);
        GoApiRequest goApiRequest = createGoApiRequest("go.processor.session.put", JSONUtils.toJSON(requestMap));
        GoApiResponse response = goApplicationAccessor.submit(goApiRequest);
        // handle error
    }

    private String read() {
        Map<String, Object> requestMap = new HashMap<String, Object>();
        requestMap.put("plugin-id", PLUGIN_ID);
        GoApiRequest goApiRequest = createGoApiRequest("go.processor.session.get", JSONUtils.toJSON(requestMap));
        GoApiResponse response = goApplicationAccessor.submit(goApiRequest);
        // handle error
        String responseBody = response.responseBody();
        Map<String, Object> sessionData = (Map<String, Object>) JSONUtils.fromJSON(responseBody);
        String verificationCode = (String) sessionData.get("verification-code");
        return verificationCode;
    }

    private void delete() {
        Map<String, Object> requestMap = new HashMap<String, Object>();
        requestMap.put("plugin-id", PLUGIN_ID);
        GoApiRequest goApiRequest = createGoApiRequest("go.processor.session.remove", JSONUtils.toJSON(requestMap));
        GoApiResponse response = goApplicationAccessor.submit(goApiRequest);
        // handle error
    }

    // TODO: this needs to be dynamic (system property)
    private String getServerBaseURL() {
        return "http://" + "localhost:8153";
    }

    private String getFileContents(String filePath) {
        try {
            return IOUtils.toString(getClass().getResource(filePath));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getUserJSON(String username, String displayName, String emailId) {
        Map<String, String> userMap = new HashMap<String, String>();
        userMap.put("username", username);
        userMap.put("display-name", displayName);
        userMap.put("email-id", emailId);
        return userMap;
    }

    private GoPluginIdentifier getGoPluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, goSupportedVersions);
    }

    private GoApiRequest createGoApiRequest(final String api, final String responseBody) {
        return new GoApiRequest() {
            @Override
            public String api() {
                return api;
            }

            @Override
            public String apiVersion() {
                return "1.0";
            }

            @Override
            public GoPluginIdentifier pluginIdentifier() {
                return getGoPluginIdentifier();
            }

            @Override
            public Map<String, String> requestParameters() {
                return null;
            }

            @Override
            public Map<String, String> requestHeaders() {
                return null;
            }

            @Override
            public String requestBody() {
                return responseBody;
            }
        };
    }

    private GoPluginApiResponse renderResponse(final int responseCode, final Map<String, String> responseHeaders, final String responseBody) {
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return responseHeaders;
            }

            @Override
            public String responseBody() {
                return responseBody;
            }
        };
    }
}