package com.ulticode.rpc.resilience;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the workload identity carried by a Dubbo mTLS peer certificate.
 *
 * <p>The caller matrix is the transport-level counterpart of the signed
 * delegation audiences: a certificate can invoke only the owner that its
 * service identity is allowed to reach.
 */
final class DubboMtlsCertificatePolicy {

    static final String SPIFFE_PREFIX = "spiffe://ulticode/service/";
    static final String DNS_SUFFIX = ".ulticode.internal";

    private static final Map<String, Set<String>> EXPECTED_CALLERS = Map.of(
            "backend-auth", Set.of("backend-admin", "backend-app", "backend-notification",
                    "backend-submission"),
            "backend-admin", Set.of(),
            "backend-app", Set.of("backend-admin", "backend-submission", "backend-judge"),
            "backend-submission", Set.of("backend-admin", "backend-app", "backend-judge"),
            "backend-notification", Set.of("backend-admin"),
            "backend-judge", Set.of("backend-app"));

    private DubboMtlsCertificatePolicy() {
    }

    static Set<String> expectedCallers(String targetService) {
        Set<String> callers = EXPECTED_CALLERS.get(targetService);
        if (callers == null) {
            throw new IllegalArgumentException("Unknown Dubbo service identity: " + targetService);
        }
        return callers;
    }

    static void validateConfiguration(String targetService, Set<String> allowedCallers) {
        Set<String> configured = Set.copyOf(allowedCallers);
        Set<String> expected = expectedCallers(targetService);
        if (!configured.equals(expected)) {
            throw new IllegalArgumentException(
                    "Dubbo mTLS caller policy does not match " + targetService
                            + "; expected=" + expected + ", configured=" + configured);
        }
    }

    static Set<String> expectedTargets(String callerService) {
        expectedCallers(callerService);
        Set<String> targets = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : EXPECTED_CALLERS.entrySet()) {
            if (entry.getValue().contains(callerService)) {
                targets.add(entry.getKey());
            }
        }
        return Set.copyOf(targets);
    }

    static String authorize(
            String targetService,
            Set<String> allowedCallers,
            X509Certificate certificate,
            Instant now) throws CertificateException {
        validateConfiguration(targetService, allowedCallers);
        String callerService = readValidatedIdentity(certificate, now);
        if (!allowedCallers.contains(callerService)) {
            throw new CertificateException("Dubbo mTLS caller is not authorized");
        }
        return callerService;
    }

    static String authorizeTarget(
            String callerService,
            Set<String> allowedTargets,
            String targetService,
            X509Certificate certificate,
            Instant now) throws CertificateException {
        Set<String> expected = expectedTargets(callerService);
        if (!Set.copyOf(allowedTargets).equals(expected)) {
            throw new IllegalArgumentException(
                    "Dubbo mTLS target policy does not match " + callerService
                            + "; expected=" + expected + ", configured=" + allowedTargets);
        }
        if (!allowedTargets.contains(targetService)) {
            throw new CertificateException("Dubbo mTLS target is not authorized");
        }
        String peerService = readValidatedIdentity(certificate, now);
        if (!targetService.equals(peerService)) {
            throw new CertificateException("Dubbo mTLS server identity does not match target");
        }
        return peerService;
    }

    private static String readValidatedIdentity(
            X509Certificate certificate, Instant now) throws CertificateException {
        if (certificate == null) {
            throw new CertificateException("Dubbo mTLS peer certificate is missing");
        }
        certificate.checkValidity(java.util.Date.from(now));

        Set<String> identities = readIdentities(certificate.getSubjectAlternativeNames());
        if (identities.size() != 1) {
            throw new CertificateException("Dubbo mTLS certificate must contain one workload identity");
        }
        return identities.iterator().next();
    }

    private static Set<String> readIdentities(Collection<List<?>> subjectAlternativeNames)
            throws CertificateException {
        if (subjectAlternativeNames == null || subjectAlternativeNames.isEmpty()) {
            throw new CertificateException("Dubbo mTLS certificate has no SAN identity");
        }

        Set<String> identities = new HashSet<>();
        for (List<?> entry : subjectAlternativeNames) {
            if (entry == null || entry.size() < 2 || !(entry.get(0) instanceof Number)
                    || !(entry.get(1) instanceof String)) {
                throw new CertificateException("Dubbo mTLS certificate has an invalid SAN");
            }
            int type = ((Number) entry.get(0)).intValue();
            String value = (String) entry.get(1);
            if (type == 6) {
                identities.add(parseSpiffeIdentity(value));
            } else if (type == 2) {
                identities.add(parseDnsIdentity(value));
            } else {
                throw new CertificateException("Dubbo mTLS certificate has an unsupported SAN type");
            }
        }
        return identities;
    }

    private static String parseSpiffeIdentity(String value) throws CertificateException {
        if (!value.startsWith(SPIFFE_PREFIX)) {
            throw new CertificateException("Dubbo mTLS certificate has an invalid SPIFFE SAN");
        }
        String identity = value.substring(SPIFFE_PREFIX.length());
        if (identity.isBlank() || identity.contains("/")) {
            throw new CertificateException("Dubbo mTLS certificate has an invalid SPIFFE SAN");
        }
        ensureKnownIdentity(identity);
        return identity;
    }

    private static String parseDnsIdentity(String value) throws CertificateException {
        for (String identity : EXPECTED_CALLERS.keySet()) {
            if ((identity + DNS_SUFFIX).equals(value)) {
                return identity;
            }
        }
        throw new CertificateException("Dubbo mTLS certificate has an invalid DNS SAN");
    }

    private static void ensureKnownIdentity(String identity) throws CertificateException {
        if (!EXPECTED_CALLERS.containsKey(identity)) {
            throw new CertificateException("Dubbo mTLS certificate has an unknown workload identity");
        }
    }
}
