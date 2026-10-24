export const verifiedDriftScenario = {
  scenarioId: 'evaluation-011',
  evidenceType: 'FIXTURE_BASED',
  requestMethod: 'GET',
  requestPath: '/api/residents/123',
  responseStatus: 200,
  fieldPath: 'id',
  expectedType: 'integer',
  observedType: 'string',
  observedValue: 'not-an-integer',
  findingType: 'RESPONSE_TYPE_MISMATCH',
  evaluationStatus: 'EVALUATED',
  truePositives: 1,
  falsePositives: 0,
  falseNegatives: 0,
  sourceFixture:
    'backend/src/test/java/com/ildcvf/evaluation/ResidentTrafficEventFixtures.java',
  sourceEvaluation:
    'backend/src/test/java/com/ildcvf/evaluation/ResidentDriftEvaluationDataset.java',
  verificationTest:
    'ResidentDriftEvaluationDatasetTest',
}
