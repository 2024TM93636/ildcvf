import { useCallback, useEffect, useState } from "react";
import axios from "axios";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  CssBaseline,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  ThemeProvider,
  Typography,
  createTheme,
} from "@mui/material";
import "./App.css";
import { recordedLlmEvidence } from "./data/recordedLlmEvidence";
import { verifiedDriftScenario } from "./data/verifiedDriftScenario";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#2563eb" },
    background: { default: "#f5f7fb" },
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: "Inter, Segoe UI, Arial, sans-serif",
  },
});

const METHODS = new Set([
  "get",
  "post",
  "put",
  "patch",
  "delete",
  "options",
  "head",
  "trace",
]);

// Extract only OpenAPI paths and HTTP methods for display.
// This is not an OpenAPI validator or general YAML parser.
function extractOperations(yaml) {
  const lines = yaml.split(/\r?\n/);
  const operations = [];
  let inPaths = false;
  let currentPath = null;
  let operationIndent = null;

  for (const line of lines) {
    if (!line.trim() || line.trim().startsWith("#")) continue;

    const indent = line.match(/^\s*/)[0].length;
    const trimmed = line.trim();

    if (indent === 0) {
      inPaths = trimmed === "paths:";
      currentPath = null;
      operationIndent = null;
      continue;
    }

    if (!inPaths) continue;

    const pathMatch = line.match(/^(\s+)(\/[^:]*):\s*(?:#.*)?$/);
    if (pathMatch) {
      currentPath = pathMatch[2];
      operationIndent = null;
      continue;
    }

    const methodMatch = trimmed.match(/^([a-z]+):\s*(?:#.*)?$/);
    if (currentPath && methodMatch && METHODS.has(methodMatch[1])) {
      if (operationIndent === null) operationIndent = indent;
      if (indent === operationIndent) {
        operations.push({
          method: methodMatch[1].toUpperCase(),
          path: currentPath,
        });
      }
    }
  }

  return operations;
}

function Section({ title, subtitle, children }) {
  return (
    <Card variant="outlined" component="section">
      <CardContent sx={{ p: 3 }}>
        <Typography variant="h6" fontWeight={700}>
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {subtitle}
          </Typography>
        )}
        <Divider sx={{ mb: 2 }} />
        {children}
      </CardContent>
    </Card>
  );
}

function Metric({ label, value }) {
  return (
    <Card variant="outlined" sx={{ flex: 1, minWidth: 150 }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h4" fontWeight={700}>
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}

function MethodChip({ method }) {
  const color =
    method === "GET"
      ? "primary"
      : method === "POST"
        ? "success"
        : method === "DELETE"
          ? "error"
          : "default";

  return <Chip size="small" label={method} color={color} variant="outlined" />;
}

function App() {
  const [baseline, setBaseline] = useState(null);
  const [traffic, setTraffic] = useState([]);
  const [drift, setDrift] = useState(null);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadData = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true);
    else setLoading(true);

    const [baselineResult, trafficResult, driftResult] =
      await Promise.allSettled([
        axios.get("/baseline/resident-api-v1.yaml", {
          responseType: "text",
        }),
        axios.get("/api/traffic/recent"),
        axios.get("/api/drift"),
      ]);

    const nextErrors = {};

    if (baselineResult.status === "fulfilled") {
      const yaml = baselineResult.value.data;
      const operations = extractOperations(yaml);
      setBaseline({ operations, yaml });
      if (operations.length === 0) {
        nextErrors.baseline =
          "No operations were extracted. Check the baseline YAML format.";
      }
    } else {
      nextErrors.baseline = "Could not load the OpenAPI baseline.";
    }

    if (trafficResult.status === "fulfilled") {
      const events = trafficResult.value.data;
      if (Array.isArray(events) && events.length <= 20) {
        setTraffic(events);
      } else {
        nextErrors.traffic = "Unexpected traffic response or event count.";
      }
    } else {
      nextErrors.traffic = "Could not load recent traffic.";
    }

    if (driftResult.status === "fulfilled") {
      const result = driftResult.value.data;
      if (Array.isArray(result?.results)) {
        setDrift(result);
      } else {
        nextErrors.drift = "Unexpected drift response.";
      }
    } else {
      nextErrors.drift = "Could not load drift analysis.";
    }

    setErrors(nextErrors);
    setLoading(false);
    setRefreshing(false);
  }, []);

  useEffect(() => {
    let cancelled = false;

    Promise.resolve().then(() => {
      if (!cancelled) {
        loadData();
      }
    });

    return () => {
      cancelled = true;
    };
  }, [loadData]);

  const results = drift?.results ?? [];
  const evaluated = results.filter(
    (result) => result.evaluationStatus === "EVALUATED",
  ).length;

  const findings = results.flatMap((result) => result.findings ?? []);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ minHeight: "100vh", pb: 6 }}>
        <Box sx={{ bgcolor: "#14243c", color: "white", py: 3, mb: 4 }}>
          <Container maxWidth="lg">
            <Stack
              direction={{ xs: "column", sm: "row" }}
              justifyContent="space-between"
              alignItems={{ xs: "start", sm: "center" }}
              spacing={2}
            >
              <Box>
                <Typography variant="h4" fontWeight={800}>
                  ILDCVF
                </Typography>
                <Typography sx={{ opacity: 0.8 }}>
                  Intelligent API Contract Drift Detection & Validation
                  Framework
                </Typography>
                <Typography variant="caption" sx={{ opacity: 0.65 }}>
                  M.Tech Dissertation — Demonstration Dashboard
                </Typography>
              </Box>
              <Button
                variant="contained"
                onClick={() => loadData(true)}
                disabled={loading || refreshing}
              >
                {refreshing ? "Refreshing..." : "Refresh"}
              </Button>
            </Stack>
          </Container>
        </Box>

        <Container maxWidth="lg">
          {loading && (
            <Stack
              direction="row"
              alignItems="center"
              spacing={2}
              sx={{ mb: 3 }}
            >
              <CircularProgress size={22} />
              <Typography>Loading dashboard data...</Typography>
            </Stack>
          )}

          <Stack spacing={3}>
            <Section
              title="1. OpenAPI Baseline"
              subtitle="Documented Resident API operations from the existing OpenAPI specification"
            >
              {errors.baseline && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {errors.baseline}
                </Alert>
              )}

              {baseline && (
                <>
                  <Typography variant="body2" sx={{ mb: 2 }}>
                    Specification: resident-api-v1.yaml
                    {" · "}
                    {baseline.operations.length} documented operations
                  </Typography>

                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>HTTP Method</TableCell>
                          <TableCell>Documented Endpoint</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {baseline.operations.map((operation, index) => (
                          <TableRow
                            key={`${operation.method}-${operation.path}-${index}`}
                          >
                            <TableCell>
                              <MethodChip method={operation.method} />
                            </TableCell>
                            <TableCell sx={{ fontFamily: "monospace" }}>
                              {operation.path}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>

                  <Box component="details" sx={{ mt: 2 }}>
                    <Box component="summary" sx={{ cursor: "pointer" }}>
                      <Typography component="span" variant="body2">
                        View original OpenAPI YAML
                      </Typography>
                    </Box>
                    <Box
                      component="pre"
                      sx={{
                        bgcolor: "#f1f5f9",
                        p: 2,
                        borderRadius: 1,
                        overflowX: "auto",
                        maxHeight: 350,
                        fontSize: 12,
                      }}
                    >
                      {baseline.yaml}
                    </Box>
                  </Box>
                </>
              )}
            </Section>

            <Section
              title="2. Captured API Traffic"
              subtitle="Latest 20 persisted events, ordered by descending sequence"
            >
              {errors.traffic && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {errors.traffic}
                </Alert>
              )}

              <Typography variant="body2" sx={{ mb: 2 }}>
                Events returned: {traffic.length}
              </Typography>

              <TableContainer sx={{ maxHeight: 420 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Sequence</TableCell>
                      <TableCell>Method</TableCell>
                      <TableCell>Path</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Timestamp (UTC)</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {traffic.map((event) => (
                      <TableRow key={event.eventId} hover>
                        <TableCell>{event.sequence}</TableCell>
                        <TableCell>
                          <MethodChip method={event.method} />
                        </TableCell>
                        <TableCell sx={{ fontFamily: "monospace" }}>
                          {event.requestPath}
                        </TableCell>
                        <TableCell>{event.responseStatus}</TableCell>
                        <TableCell>
                          {event.timestamp
                            ? new Date(event.timestamp).toLocaleString(
                                "en-GB",
                                {
                                  timeZone: "UTC",
                                },
                              )
                            : "—"}
                        </TableCell>
                      </TableRow>
                    ))}
                    {!loading && traffic.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} align="center">
                          No recent traffic available.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </Section>

            <Section
              title="3. Contract Drift Detection"
              subtitle="Existing deterministic analysis of the selected recent traffic events"
            >
              {errors.drift && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {errors.drift}
                </Alert>
              )}

              <Stack direction="row" flexWrap="wrap" gap={2} sx={{ mb: 3 }}>
                <Metric
                  label="Analyzed Events"
                  value={drift?.analyzedEvents ?? "—"}
                />
                <Metric
                  label="Fully Evaluated"
                  value={drift ? evaluated : "—"}
                />
                <Metric
                  label="Drift Findings"
                  value={drift?.totalFindings ?? "—"}
                />
              </Stack>

              {drift && findings.length === 0 && (
                <Alert severity="success">
                  No drift detected in the selected events.
                </Alert>
              )}

              {findings.length > 0 && (
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Sequence</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Severity</TableCell>
                        <TableCell>Explanation</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {findings.map((finding) => (
                        <TableRow key={finding.findingId} hover>
                          <TableCell>{finding.trafficSequence}</TableCell>
                          <TableCell>{finding.type}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={finding.severity}
                              color={
                                finding.severity === "HIGH" ||
                                finding.severity === "CRITICAL"
                                  ? "error"
                                  : "warning"
                              }
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>{finding.message}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}

              {drift && (
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ display: "block", mt: 2 }}
                >
                  Selection: {drift.selectionMethod} · Maximum events:{" "}
                  {drift.selectionLimit}
                </Typography>
              )}

              <Divider sx={{ my: 3 }} />

              <Typography variant="h6" fontWeight={700} sx={{ mb: 1 }}>
                Verified Contract Drift Example
              </Typography>

              <Alert severity="info" sx={{ mb: 2 }}>
                <strong>Verified Evaluation Scenario — Not Live Traffic</strong>
                <Typography variant="body2" sx={{ mt: 1 }}>
                  This scenario comes from the existing deterministic evaluation
                  fixtures. It is separate from the latest persisted traffic and
                  is not included in the live drift metrics.
                </Typography>
              </Alert>

              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={2}>
                    <Stack
                      direction="row"
                      spacing={1}
                      flexWrap="wrap"
                      useFlexGap
                    >
                      <Chip
                        label={verifiedDriftScenario.scenarioId}
                        size="small"
                        variant="outlined"
                      />
                      <Chip
                        label={verifiedDriftScenario.findingType}
                        size="small"
                        color="warning"
                        variant="outlined"
                      />
                      <Chip
                        label={verifiedDriftScenario.evaluationStatus}
                        size="small"
                        color="success"
                        variant="outlined"
                      />
                    </Stack>

                    <Typography variant="body2">
                      <strong>Operation:</strong>{" "}
                      {verifiedDriftScenario.requestMethod}{" "}
                      {verifiedDriftScenario.requestPath}
                    </Typography>

                    <Typography variant="body2">
                      <strong>Response status:</strong>{" "}
                      {verifiedDriftScenario.responseStatus}
                    </Typography>

                    <Typography variant="body2">
                      <strong>Field:</strong> {verifiedDriftScenario.fieldPath}
                    </Typography>

                    <Typography variant="body2">
                      <strong>Expected:</strong>{" "}
                      {verifiedDriftScenario.expectedType}
                    </Typography>

                    <Typography variant="body2">
                      <strong>Observed:</strong>{" "}
                      {verifiedDriftScenario.observedType} — value "
                      {verifiedDriftScenario.observedValue}"
                    </Typography>

                    <Alert severity="warning">
                      The OpenAPI contract expects the resident ID to be an
                      integer, but the recorded evaluation fixture contains a
                      string. The existing deterministic analyzer reported
                      RESPONSE_TYPE_MISMATCH for field id.
                    </Alert>

                    <Typography variant="body2">
                      <strong>Evaluation result:</strong> TP=
                      {verifiedDriftScenario.truePositives}, FP=
                      {verifiedDriftScenario.falsePositives}, FN=
                      {verifiedDriftScenario.falseNegatives}
                    </Typography>

                    <Typography variant="caption" color="text.secondary">
                      Source fixture: {verifiedDriftScenario.sourceFixture}
                    </Typography>

                    <Typography variant="caption" color="text.secondary">
                      Evaluation dataset:{" "}
                      {verifiedDriftScenario.sourceEvaluation}
                    </Typography>

                    <Typography variant="caption" color="text.secondary">
                      Verified using: {verifiedDriftScenario.verificationTest}
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Section>

            <Section
              title="4. LLM Proposal & Validation"
              subtitle="Recorded experimental evidence from the E6 reliability evaluation"
            >
              <Alert severity="info" sx={{ mb: 2 }}>
                <strong>
                  Recorded Experimental Evidence — Not a Live LLM Call
                </strong>
              </Alert>

              <Typography variant="body2" sx={{ mb: 3 }}>
                The LLM proposes possible contract changes based on detected
                drift evidence. Deterministic validation independently decides
                whether a proposal is acceptable. A successfully generated
                proposal is not automatically approved.
              </Typography>

              <Stack spacing={2}>
                {recordedLlmEvidence.map((attempt) => (
                  <Card variant="outlined" key={attempt.attemptId}>
                    <CardContent>
                      <Stack
                        direction={{ xs: "column", sm: "row" }}
                        justifyContent="space-between"
                        alignItems={{ xs: "start", sm: "center" }}
                        spacing={1}
                        sx={{ mb: 2 }}
                      >
                        <Typography fontWeight={700}>
                          {attempt.attemptId}
                        </Typography>

                        <Chip
                          size="small"
                          variant="outlined"
                          color={
                            attempt.validatorAccepted === false
                              ? "error"
                              : "warning"
                          }
                          label={
                            attempt.validatorAccepted === false
                              ? "VALIDATION REJECTED"
                              : "TRANSPORT FAILURE — NOT EVALUATED"
                          }
                        />
                      </Stack>

                      <Typography variant="body2" color="text.secondary">
                        Scenario: {attempt.scenarioId}
                      </Typography>

                      <Typography variant="body2" color="text.secondary">
                        Provider: {attempt.provider}
                        {" · "}
                        Model: {attempt.model}
                      </Typography>

                      <Divider sx={{ my: 2 }} />

                      <Stack spacing={1}>
                        <Typography variant="body2">
                          <strong>Provider outcome:</strong>{" "}
                          {attempt.providerCallStatus}
                        </Typography>

                        <Typography variant="body2">
                          <strong>Parsing outcome:</strong>{" "}
                          {attempt.proposalParseStatus ?? "Not reached"}
                        </Typography>

                        {attempt.validatorAccepted === false &&
                          attempt.validatorCodes.length > 0 && (
                            <Typography variant="body2">
                              <strong>Validator rejection code:</strong>{" "}
                              {attempt.validatorCodes.join(", ")}
                            </Typography>
                          )}

                        {attempt.changeKind ? (
                          <>
                            <Typography variant="body2">
                              <strong>Proposed change:</strong>{" "}
                              {attempt.changeKind}
                            </Typography>

                            <Typography variant="body2">
                              <strong>Operation:</strong> {attempt.method}{" "}
                              {attempt.runtimePath}
                            </Typography>

                            <Typography variant="body2">
                              <strong>Proposed value:</strong>{" "}
                              {attempt.proposedValue ?? "null"}
                            </Typography>

                            <Alert severity="error" sx={{ mt: 1 }}>
                              Deterministic validation rejected this proposal.
                              It was not approved or applied.
                            </Alert>
                          </>
                        ) : (
                          <Alert severity="warning">
                            {attempt.providerDiagnostic} No proposal was
                            produced, so deterministic validation was not
                            performed.
                          </Alert>
                        )}

                        <Typography variant="body2">
                          <strong>Grounding assessment:</strong>{" "}
                          {attempt.hallucinationApplicability}
                        </Typography>

                        {attempt.hallucinationReasonCodes.length > 0 && (
                          <Typography variant="body2">
                            <strong>
                              Recorded hallucination reason codes:
                            </strong>{" "}
                            {attempt.hallucinationReasonCodes.join(", ")}
                          </Typography>
                        )}
                      </Stack>

                      <Divider sx={{ my: 2 }} />

                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ display: "block", overflowWrap: "anywhere" }}
                      >
                        Source: {attempt.sourceFile}
                      </Typography>

                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ display: "block", overflowWrap: "anywhere" }}
                      >
                        SHA-256: {attempt.sha256}
                      </Typography>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </Section>
          </Stack>
        </Container>
      </Box>
    </ThemeProvider>
  );
}

export default App;
