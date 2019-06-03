package org.apache.james.mailets.configuration;

import org.apache.james.jdkim.MockPublicKeyRecordRetriever;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;

import com.google.inject.AbstractModule;

public class MockPublicKeyRecordRetrieverModule extends AbstractModule {
    @Override
    public void configure() {
        super.configure();
        bind(PublicKeyRecordRetriever.class).toInstance(new MockPublicKeyRecordRetriever(
            "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
            "selector", "example.com"));
    }
}
