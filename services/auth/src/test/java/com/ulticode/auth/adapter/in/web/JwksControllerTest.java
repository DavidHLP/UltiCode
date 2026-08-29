package com.ulticode.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ulticode.auth.security.jwt.RsaKeyManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwksControllerTest {

    @Test
    void exposesRfc7517KeysAtTopLevel() {
        RsaKeyManager keyManager = mock(RsaKeyManager.class);
        Map<String, Object> jwkSet = Map.of(
                "keys", List.of(Map.of("kid", "current", "kty", "RSA")));
        when(keyManager.toJwkSet()).thenReturn(jwkSet);

        Map<String, Object> response = new JwksController(keyManager).getJwks();

        assertThat(response).isSameAs(jwkSet);
        assertThat(response).containsKey("keys");
        assertThat(response).doesNotContainKey("data");
    }
}
