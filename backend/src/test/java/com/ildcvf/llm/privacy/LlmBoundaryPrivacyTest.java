package com.ildcvf.llm.privacy;

import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.evidence.RelevantContractContext;
import com.ildcvf.llm.provider.LLMProvider;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmBoundaryPrivacyTest {

    private static final Set<String> FORBIDDEN_COMPONENT_NAMES =
            Set.of(
                    "trafficevent",
                    "requestheaders",
                    "responseheaders",
                    "requestbody",
                    "responsebody",
                    "cookies",
                    "cookie",
                    "jwt",
                    "authorization",
                    "authtoken",
                    "accesstoken"
            );

    @Test
    void groundedEvidencePublicRecordShapeExcludesRawRuntimeMaterial() {
        assertTrue(GroundedEvidencePackage.class.isRecord());

        Set<String> componentNames =
                Arrays.stream(
                                GroundedEvidencePackage.class
                                        .getRecordComponents()
                        )
                        .map(RecordComponent::getName)
                        .map(name ->
                                name.toLowerCase(Locale.ROOT)
                        )
                        .collect(Collectors.toSet());

        for (String forbidden : FORBIDDEN_COMPONENT_NAMES) {
            assertFalse(
                    componentNames.contains(forbidden),
                    () -> "GroundedEvidencePackage unexpectedly exposes "
                            + forbidden
            );
        }

        Set<Class<?>> componentTypes =
                Arrays.stream(
                                GroundedEvidencePackage.class
                                        .getRecordComponents()
                        )
                        .map(RecordComponent::getType)
                        .collect(Collectors.toSet());

        assertFalse(
                componentTypes.stream()
                        .anyMatch(type ->
                                type.getSimpleName()
                                        .equals("TrafficEvent")
                        )
        );
    }

    @Test
    void relevantContractContextRemainsMinimal() {
        assertTrue(RelevantContractContext.class.isRecord());

        Set<String> names =
                Arrays.stream(
                                RelevantContractContext.class
                                        .getRecordComponents()
                        )
                        .map(RecordComponent::getName)
                        .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "openApiVersion",
                        "method",
                        "pathTemplate",
                        "operationId"
                ),
                names
        );
    }

    @Test
    void providerAcceptsBoundedEvidenceRatherThanTrafficEvent() {
        Method generateProposal =
                Arrays.stream(LLMProvider.class.getDeclaredMethods())
                        .filter(method ->
                                method.getName()
                                        .equals("generateProposal")
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                ContractChangeProposal.class,
                generateProposal.getReturnType()
        );

        assertEquals(
                1,
                generateProposal.getParameterCount()
        );

        assertEquals(
                GroundedEvidencePackage.class,
                generateProposal.getParameterTypes()[0]
        );

        assertFalse(
                Arrays.stream(
                                generateProposal.getParameterTypes()
                        )
                        .anyMatch(type ->
                                type.getSimpleName()
                                        .equals("TrafficEvent")
                        )
        );
    }
}