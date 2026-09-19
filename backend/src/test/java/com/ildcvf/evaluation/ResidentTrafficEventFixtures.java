package com.ildcvf.evaluation;

import com.ildcvf.traffic.domain.TrafficEvent;

import java.time.Instant;
import java.util.Map;

public final class ResidentTrafficEventFixtures {

    private static final Instant EVALUATION_TIMESTAMP =
            Instant.parse("2026-09-18T06:00:00Z");

    private static final String VALID_RESIDENT = """
        {
          "id": 1,
          "firstName": "Anu",
          "lastName": "Thomas",
          "email": "anu@example.test",
          "dateOfBirth": "1995-05-10",
          "phoneNumber": null,
          "status": "ACTIVE",
          "roomNumber": null
        }
        """;

    private static final String VALID_RESIDENT_COLLECTION = """
        [
          {
            "id": 1,
            "firstName": "Anu",
            "lastName": "Thomas",
            "email": "anu@example.test",
            "dateOfBirth": "1995-05-10",
            "phoneNumber": null,
            "roomNumber": null,
            "status": "ACTIVE"
          }
        ]
        """;

    private static final String VALID_CREATE_RESIDENT = """
        {
          "firstName": "Anu",
          "lastName": "Thomas",
          "email": "anu@example.test",
          "dateOfBirth": "1995-05-10",
          "status": "ACTIVE"
        }
        """;

    private ResidentTrafficEventFixtures() {
    }

    public static TrafficEvent baselineValidPost() {
        return event(
                "evaluation-001",
                1,
                true,
                "POST",
                "/api/residents",
                "application/json",
                VALID_CREATE_RESIDENT,
                false,
                201,
                VALID_RESIDENT);
    }

    public static TrafficEvent baselineValidGetItem() {
        return event(
                "evaluation-002",
                2,
                true,
                "GET",
                "/api/residents/123",
                null,
                null,
                false,
                200,
                VALID_RESIDENT);
    }

    public static TrafficEvent baselineValidGetCollection() {
        return event(
                "evaluation-003",
                3,
                true,
                "GET",
                "/api/residents",
                null,
                null,
                false,
                200,
                VALID_RESIDENT_COLLECTION);
    }

    public static TrafficEvent undocumentedOperation() {
        return event(
                "evaluation-004",
                4,
                true,
                "PATCH",
                "/api/residents",
                null,
                null,
                false,
                200,
                VALID_RESIDENT_COLLECTION);
    }

    public static TrafficEvent requestUnexpectedField() {
        return event(
                "evaluation-005",
                5,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE",
                  "unexpectedField": "not documented"
                }
                """,
                false,
                201,
                VALID_RESIDENT);
    }

    public static TrafficEvent requestMissingRequiredField() {
        return event(
                "evaluation-006",
                6,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false,
                201,
                VALID_RESIDENT);
    }

    public static TrafficEvent requestTypeMismatch() {
        return event(
                "evaluation-007",
                7,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": 123,
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false,
                201,
                VALID_RESIDENT);
    }

    public static TrafficEvent requestEnumMismatch() {
        return event(
                "evaluation-008",
                8,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "INVALID_STATUS"
                }
                """,
                false,
                201,
                VALID_RESIDENT);
    }

    public static TrafficEvent responseUndocumentedStatus() {
        return event(
                "evaluation-009",
                9,
                true,
                "POST",
                "/api/residents",
                "application/json",
                VALID_CREATE_RESIDENT,
                false,
                422,
                VALID_RESIDENT);
    }

    public static TrafficEvent responseMissingRequiredField() {
        return event(
                "evaluation-010",
                10,
                true,
                "GET",
                "/api/residents/123",
                null,
                null,
                false,
                200,
                """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "phoneNumber": null,
                  "status": "ACTIVE",
                  "roomNumber": null
                }
                """);
    }

    public static TrafficEvent responseTypeMismatch() {
        return event(
                "evaluation-011",
                11,
                true,
                "GET",
                "/api/residents/123",
                null,
                null,
                false,
                200,
                """
                {
                  "id": "not-an-integer",
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "phoneNumber": null,
                  "status": "ACTIVE",
                  "roomNumber": null
                }
                """);
    }

    public static TrafficEvent responseEnumMismatch() {
        return event(
                "evaluation-012",
                12,
                true,
                "GET",
                "/api/residents/123",
                null,
                null,
                false,
                200,
                """
                {
                  "id": 1,
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "phoneNumber": null,
                  "status": "INVALID_STATUS",
                  "roomNumber": null
                }
                """);
    }

    public static TrafficEvent collectionItemTypeMismatch() {
        return event(
                "evaluation-013",
                13,
                true,
                "GET",
                "/api/residents",
                null,
                null,
                false,
                200,
                """
                [
                  {
                    "id": "not-an-integer",
                    "firstName": "Anu",
                    "lastName": "Thomas",
                    "email": "anu@example.test",
                    "dateOfBirth": "1995-05-10",
                    "phoneNumber": null,
                    "status": "ACTIVE",
                    "roomNumber": null
                  }
                ]
                """);
    }

    public static TrafficEvent requestTruncated() {
        return event(
                "evaluation-014",
                14,
                true,
                "POST",
                "/api/residents",
                "application/json",
                "{\"firstName\":\"An",
                true,
                201,
                VALID_RESIDENT);
    }

    public static TrafficEvent responseTruncated() {
        return event(
                "evaluation-015",
                15,
                true,
                "GET",
                "/api/residents/123",
                null,
                null,
                false,
                200,
                """
                {"id":1,"firstName":"Anu"
                """,
                true);
    }

    public static TrafficEvent malformedRequestJson() {
        return event(
                "evaluation-016",
                16,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {"firstName":"Anu","lastName":
                """,
                false,
                201,
                VALID_RESIDENT);
    }

    public static TrafficEvent malformedResponseJson() {
        return event(
                "evaluation-017",
                17,
                true,
                "GET",
                "/api/residents/123",
                null,
                null,
                false,
                200,
                """
                {"id":1,"firstName":"Anu","lastName":
                """);
    }

    public static TrafficEvent upstreamNotReached() {
        return event(
                "evaluation-018",
                18,
                false,
                "GET",
                "/api/residents",
                null,
                null,
                false,
                502,
                null);
    }

    private static TrafficEvent event(
            String eventId,
            long sequence,
            boolean upstreamReached,
            String method,
            String requestPath,
            String requestContentType,
            String requestBody,
            boolean requestBodyTruncated,
            int responseStatus,
            String responseBody) {

        return event(
                eventId,
                sequence,
                upstreamReached,
                method,
                requestPath,
                requestContentType,
                requestBody,
                requestBodyTruncated,
                responseStatus,
                responseBody,
                false);
    }

    private static TrafficEvent event(
            String eventId,
            long sequence,
            boolean upstreamReached,
            String method,
            String requestPath,
            String requestContentType,
            String requestBody,
            boolean requestBodyTruncated,
            int responseStatus,
            String responseBody,
            boolean responseBodyTruncated) {

        return new TrafficEvent(
                eventId,
                sequence,
                EVALUATION_TIMESTAMP,
                "resident-api",
                upstreamReached,
                method,
                requestPath,
                null,
                Map.of(),
                Map.of(),
                requestContentType,
                requestBody,
                requestBody == null
                        ? 0
                        : requestBody.length(),
                requestBodyTruncated,
                responseStatus,
                Map.of(),
                "application/json",
                responseBody,
                responseBody == null
                        ? 0
                        : responseBody.length(),
                responseBodyTruncated,
                10L);
    }
}
