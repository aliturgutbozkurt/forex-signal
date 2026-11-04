package com.fsp.identity.api;

import java.util.List;
import java.util.Map;

public interface IdentityFacade {

    Map<String, Object> createWebAuthnRegistrationChallenge(String username);

    boolean verifyPasskeyAssertion(String credentialId, String challengeResponse);

    List<String> supportedOAuth2Providers();
}

