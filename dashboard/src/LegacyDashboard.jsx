import { useCallback, useEffect, useRef, useState } from "react";
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
} from "@mui/material";
import "./App.css";
import theme from "./theme";
import ProposalHistory from "./components/ProposalHistory";
import ContractVersions from "./components/ContractVersions";
import HumanReview from "./components/HumanReview";
import { recordedLlmEvidence } from "./data/recordedLlmEvidence";
import { verifiedDriftScenario } from "./data/verifiedDriftScenario";

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

function PipelineStage({ number, title, status, detail, color = "default" }) {
  return (
    <Box
      sx={{
        flex: "1 1 175px",
        minWidth: 0,
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        p: 2,
        bgcolor: "background.paper",
      }}
    >
      <Typography variant="caption" color="text.secondary">
        Stage {number}
      </Typography>

      <Typography fontWeight={700} sx={{ mt: 0.5, mb: 1 }}>
        {title}
      </Typography>

      <Chip
        size="small"
        color={color}
        variant="outlined"
        label={status}
        sx={{ mb: 1, maxWidth: "100%" }}
      />

      <Typography
        variant="body2"
        color="text.secondary"
        sx={{ overflowWrap: "anywhere" }}
      >
        {detail}
      </Typography>
    </Box>
  );
}

// Read-only semantic comparison. No contract generation or mutation occurs here.
function ContractComparison({ baseline, proposal, validation, source }) {
  const method = (proposal?.method ?? proposal?.httpMethod ?? "").toUpperCase();
  const path =
    proposal?.runtimePath ?? proposal?.path ?? proposal?.apiPath ?? "";
  const changeKind = proposal?.changeKind ?? proposal?.changeType ?? null;
  const proposedValue = proposal?.proposedValue ?? null;
  const operationKnown = Boolean(method && path);
  const documented = operationKnown
    ? baseline?.operations.some(
        (item) => item.method === method && item.path === path,
      )
    : null;
  const accepted = validation?.valid ?? validation?.validatorAccepted ?? null;
  const rejectionCodes =
    validation?.validatorCodes ??
    validation?.reasonCodes ??
    validation?.codes ??
    [];
  const reasons = Array.isArray(rejectionCodes) ? rejectionCodes : [];
  const canReview = accepted === true;

  return (
    <Card variant="outlined" sx={{ mt: 2 }}>
      <CardContent>
        <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1 }}>
          Original OpenAPI Baseline | Proposed Contract Change
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {source} — semantic comparison only; no proposed OpenAPI YAML is
          generated.
        </Typography>
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={2}
          sx={{ mt: 2 }}
        >
          <Box
            sx={{
              flex: 1,
              minWidth: 0,
              p: 2,
              border: "1px solid",
              borderColor: "divider",
              borderRadius: 1,
            }}
          >
            <Typography fontWeight={700}>Original OpenAPI baseline</Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
              {baseline
                ? "Loaded from resident-api-v1.yaml"
                : "Baseline unavailable; comparison cannot be confirmed."}
            </Typography>
            <Typography
              variant="body2"
              sx={{ mt: 1, overflowWrap: "anywhere" }}
            >
              Operation:{" "}
              {operationKnown ? `${method} ${path}` : "Not specified"}
            </Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
              {documented === null || !baseline
                ? "Operation documentation status unavailable."
                : documented
                  ? "This operation is documented in the baseline."
                  : "This operation is not documented in the baseline."}
            </Typography>
          </Box>
          <Box
            sx={{
              flex: 1,
              minWidth: 0,
              p: 2,
              border: "1px solid",
              borderColor: "divider",
              borderRadius: 1,
            }}
          >
            <Typography fontWeight={700}>Proposed contract change</Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
              Change type: {changeKind ?? "Not supplied"}
            </Typography>
            <Typography
              variant="body2"
              sx={{ mt: 1, overflowWrap: "anywhere" }}
            >
              Operation:{" "}
              {operationKnown ? `${method} ${path}` : "Not specified"}
            </Typography>
            <Typography
              variant="body2"
              sx={{ mt: 1, overflowWrap: "anywhere" }}
            >
              Proposed value:{" "}
              {proposedValue === null
                ? "null (not supplied)"
                : typeof proposedValue === "string"
                  ? proposedValue
                  : JSON.stringify(proposedValue)}
            </Typography>
          </Box>
        </Stack>
        <Alert
          severity={
            accepted === true
              ? "success"
              : accepted === false
                ? "warning"
                : "info"
          }
          sx={{ mt: 2 }}
        >
          Deterministic validation:{" "}
          {accepted === true
            ? "VALIDATED"
            : accepted === false
              ? "REJECTED"
              : "NOT PERFORMED"}
          {reasons.length > 0 && ` — ${reasons.join(", ")}`}
          {accepted === false &&
            proposedValue === null &&
            " — The proposed value is not grounded in the supplied evidence."}
        </Alert>
        <Typography variant="subtitle2" fontWeight={700} sx={{ mt: 2, mb: 1 }}>
          Human review (read-only)
        </Typography>
        <Table size="small" aria-label="Read-only human review summary">
          <TableBody>
            <TableRow>
              <TableCell>Eligible for human review</TableCell>
              <TableCell>
                {canReview ? "Yes — pending human review" : "No"}
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Human decision</TableCell>
              <TableCell>Not performed</TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Contract applied</TableCell>
              <TableCell>No</TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Baseline modified</TableCell>
              <TableCell>No</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function LegacyDashboard() {
  const [baseline, setBaseline] = useState(null);
  const [traffic, setTraffic] = useState([]);
  const [drift, setDrift] = useState(null);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedFindingId, setSelectedFindingId] = useState("");
  const [liveProposal, setLiveProposal] = useState(null);
  const [liveProposalError, setLiveProposalError] = useState("");
  const [generatingProposal, setGeneratingProposal] = useState(false);
  const proposalInProgress = useRef(false);
  const [versionRefreshToken, setVersionRefreshToken] = useState(0);

  const requestInProgress = useRef(false);
  const activeController = useRef(null);
  const baselineLoaded = useRef(false);
  const mounted = useRef(false);

  const loadData = useCallback(async (refresh = false) => {
    if (requestInProgress.current) return;

    requestInProgress.current = true;

    const controller = new AbortController();
    activeController.current = controller;

    if (refresh) setRefreshing(true);

    const shouldLoadBaseline = !baselineLoaded.current;

    try {
      const requests = [
        axios.get("/api/traffic/recent", {
          signal: controller.signal,
        }),
        axios.get("/api/drift", {
          signal: controller.signal,
        }),
      ];

      if (shouldLoadBaseline) {
        requests.push(
          axios.get("/baseline/resident-api-v1.yaml", {
            responseType: "text",
            signal: controller.signal,
          }),
        );
      }

      const settled = await Promise.allSettled(requests);

      if (controller.signal.aborted || !mounted.current) return;

      const [trafficResult, driftResult, baselineResult] = settled;

      const nextErrors = {};

      if (trafficResult.status === "fulfilled") {
        const events = trafficResult.value.data;

        if (Array.isArray(events) && events.length <= 20) {
          setTraffic(events);
        } else {
          nextErrors.traffic = "Unexpected traffic response or event count.";
          setTraffic([]);
        }
      } else {
        nextErrors.traffic = "Could not load recent traffic.";
        setTraffic([]);
      }

      if (driftResult.status === "fulfilled") {
        const result = driftResult.value.data;

        if (Array.isArray(result?.results) && result.selectionLimit === 10) {
          setDrift(result);
        } else {
          nextErrors.drift = "Unexpected drift response or selection limit.";
          setDrift(null);
        }
      } else {
        nextErrors.drift = "Could not load drift analysis.";
        setDrift(null);
      }

      if (shouldLoadBaseline) {
        baselineLoaded.current = true;

        if (baselineResult.status === "fulfilled") {
          const yaml = baselineResult.value.data;
          const operations = extractOperations(yaml);

          if (operations.length > 0) {
            setBaseline({ operations, yaml });
          } else {
            setBaseline(null);
            nextErrors.baseline =
              "No operations were extracted from the baseline.";
          }
        } else {
          setBaseline(null);
          nextErrors.baseline = "Could not load the OpenAPI baseline.";
        }
      }

      setErrors((previous) => ({
        ...previous,
        ...nextErrors,
        baseline: shouldLoadBaseline ? nextErrors.baseline : previous.baseline,
        traffic: nextErrors.traffic,
        drift: nextErrors.drift,
      }));
    } finally {
      if (activeController.current === controller) {
        activeController.current = null;
      }

      requestInProgress.current = false;

      if (mounted.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    mounted.current = true;

    const start = Promise.resolve().then(() => {
      if (mounted.current) {
        loadData();
      }
    });

    const interval = window.setInterval(() => {
      if (mounted.current) {
        loadData(true);
      }
    }, 3000);

    return () => {
      mounted.current = false;
      activeController.current?.abort();
      window.clearInterval(interval);
      void start;
    };
  }, [loadData]);

  const generateLiveProposal = async (trafficEventId, findingId) => {
    if (!trafficEventId || !findingId || proposalInProgress.current) {
      return;
    }

    proposalInProgress.current = true;
    setGeneratingProposal(true);
    setLiveProposal(null);
    setLiveProposalError("");

    try {
      const response = await axios.post(
        "/api/llm/demo/proposals",
        {
          trafficEventId,
          findingId,
        },
        {
          timeout: 35000,
        },
      );

      if (mounted.current) {
        setLiveProposal(response.data);
      }
    } catch (error) {
      if (mounted.current) {
        const message =
          error.code === "ECONNABORTED"
            ? "The demonstration request timed out."
            : error.response?.data?.message ||
              "The live proposal request failed.";

        setLiveProposalError(message);
      }
    } finally {
      proposalInProgress.current = false;

      if (mounted.current) {
        setGeneratingProposal(false);
      }
    }
  };

  const results = drift?.results ?? [];
  const evaluated = results.filter(
    (result) => result.evaluationStatus === "EVALUATED",
  ).length;

  const findings = results.flatMap((result) => result.findings ?? []);

  const eligibleFindings = results
    .filter((result) => result.evaluationStatus === "EVALUATED")
    .flatMap((result) =>
      (result.findings ?? []).map((finding) => ({
        ...finding,
        trafficEventId: result.trafficEventId,
      })),
    );

  const selectedFinding =
    eligibleFindings.find(
      (finding) => finding.findingId === selectedFindingId,
    ) ??
    eligibleFindings[0] ??
    null;

  const latestTraffic = traffic[0] ?? null;

  const latestDriftResult = latestTraffic
    ? results.find((result) => result.trafficEventId === latestTraffic.eventId)
    : null;

  const latestFindings = latestDriftResult?.findings ?? [];

  const latestEventAnalyzed =
    latestDriftResult?.evaluationStatus === "EVALUATED";

  const latestDriftStatus = !latestTraffic
    ? "No traffic"
    : !drift || !latestDriftResult || !latestEventAnalyzed
      ? "Result unavailable"
      : latestFindings.length > 0
        ? "Drift detected"
        : "No drift found";

  const latestDriftDetail = !latestTraffic
    ? "Waiting for captured API traffic."
    : !drift || !latestDriftResult || !latestEventAnalyzed
      ? "Latest event not included in the current analysis window, or its result is unavailable."
      : `${latestFindings.length} finding(s) for ${latestTraffic.eventId}.`;

  const hasRecordedLlmEvidence = recordedLlmEvidence.length > 0;

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ minHeight: "100vh", pb: 6 }}>
        <Box sx={{ bgcolor: "#14243c", color: "white", py: 3, mb: 4 }}>
          <Container maxWidth="lg">
            <Stack
              direction={{ xs: "column", sm: "row" }}
              justifycontent="space-between"
              alignitems={{ xs: "start", sm: "center" }}
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
                disabled={loading || refreshing || true}
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
              alignitems="center"
              spacing={2}
              sx={{ mb: 3 }}
            >
              <CircularProgress size={22} />
              <Typography>Loading dashboard data...</Typography>
            </Stack>
          )}

          <Section
            title="Live API Observation Pipeline"
            subtitle="Evidence-based status of each framework stage. LLM and human review are not automatically executed for new traffic."
          >
            <Stack direction="row" flexwrap="wrap" gap={1.5} useFlexGap>
              <PipelineStage
                number={1}
                title="OpenAPI Baseline"
                status={baseline ? "Loaded" : "Unavailable"}
                color={baseline ? "success" : "warning"}
                detail={
                  baseline
                    ? `${baseline.operations.length} documented operations loaded.`
                    : "OpenAPI baseline is not currently available."
                }
              />

              <PipelineStage
                number={2}
                title="Traffic Capture"
                status={latestTraffic ? "Captured" : "No traffic"}
                color={latestTraffic ? "success" : "default"}
                detail={
                  latestTraffic
                    ? `Event ${latestTraffic.eventId}, sequence ${latestTraffic.sequence}.`
                    : "No recent persisted traffic available."
                }
              />

              <PipelineStage
                number={3}
                title="Drift Detection"
                status={latestDriftStatus}
                color={
                  latestDriftStatus === "Drift detected"
                    ? "error"
                    : latestDriftStatus === "No drift found"
                      ? "success"
                      : "warning"
                }
                detail={latestDriftDetail}
              />

              <PipelineStage
                number={4}
                title="LLM Proposal & Validation"
                status={
                  hasRecordedLlmEvidence
                    ? "Recorded evidence"
                    : "No recorded evidence"
                }
                color={hasRecordedLlmEvidence ? "info" : "default"}
                detail={
                  hasRecordedLlmEvidence
                    ? "Historical experimental evidence only. No LLM call or proposal generated for the latest live event."
                    : "No recorded LLM experimental evidence available."
                }
              />

              <PipelineStage
                number={5}
                title="Human Review"
                status="Implemented"
                color="default"
                detail="Persisted human review is available below. Decisions are never automatic."
              />
            </Stack>

            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ display: "block", mt: 2 }}
            >
              Traffic and drift refresh automatically approximately every 3
              seconds. Drift analysis uses the latest 10 selected events.
            </Typography>
          </Section>

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

              <Stack direction="row" flexwrap="wrap" gap={2} sx={{ mb: 3 }}>
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
                                finding.severity === "BREAKING" ||
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
                  Selection: {drift.selectionMethod} Â· Maximum events:{" "}
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
                      flexwrap="wrap"
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

            <ProposalHistory />

            <HumanReview
              onVersionCreated={() =>
                setVersionRefreshToken((value) => value + 1)
              }
            />

            <ContractVersions refreshToken={versionRefreshToken} />

            <Section
              title="4. LLM Proposal & Validation"
              subtitle="Recorded experimental evidence from the E6 reliability evaluation"
            >
              <Alert severity="warning" sx={{ mb: 2 }}>
                <Typography fontWeight={700}>
                  M12 Step 4E — operator-recorded live outcome
                </Typography>
                <Typography variant="body2" sx={{ mt: 1 }}>
                  Event traffic-00000012; finding
                  traffic-00000012:RESPONSE_TYPE_MISMATCH:id. PROVIDER_FAILURE /
                  TRANSPORT_FAILURE. No live proposal was returned,
                  deterministic validation was not performed, and the original
                  OpenAPI baseline was not modified. This is a historical
                  observation, not a new provider request.
                </Typography>
              </Alert>
              <Card variant="outlined" sx={{ mb: 3 }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Typography variant="h6" fontWeight={700}>
                      Live LLM Demonstration
                    </Typography>

                    <Typography variant="body2" color="text.secondary">
                      Select a finding from persisted API traffic and manually
                      request one LLM-generated contract change proposal. The
                      deterministic validator independently evaluates the
                      proposal.
                    </Typography>

                    {eligibleFindings.length === 0 ? (
                      <Alert severity="info">
                        No eligible live drift finding is currently available.
                      </Alert>
                    ) : (
                      <>
                        <Box
                          component="select"
                          value={selectedFinding?.findingId ?? ""}
                          onChange={(event) => {
                            setSelectedFindingId(event.target.value);
                            setLiveProposal(null);
                            setLiveProposalError("");
                          }}
                          disabled={generatingProposal}
                          sx={{
                            width: "100%",
                            p: 1.5,
                            border: "1px solid",
                            borderColor: "divider",
                            borderRadius: 1,
                            bgcolor: "background.paper",
                          }}
                        >
                          {eligibleFindings.map((finding) => (
                            <option
                              key={finding.findingId}
                              value={finding.findingId}
                            >
                              {finding.trafficEventId} — {finding.type} —{" "}
                              {finding.fieldPath ?? "operation"}
                            </option>
                          ))}
                        </Box>

                        <Typography variant="body2">
                          <strong>Event:</strong>{" "}
                          {selectedFinding?.trafficEventId}
                        </Typography>

                        <Typography variant="body2">
                          <strong>Finding:</strong> {selectedFinding?.findingId}
                        </Typography>

                        <Typography variant="body2">
                          <strong>Expected:</strong>{" "}
                          {selectedFinding?.expected ?? "N/A"}
                          {" | "}
                          <strong>Observed:</strong>{" "}
                          {selectedFinding?.observed ?? "N/A"}
                        </Typography>
                      </>
                    )}

                    <Button
                      variant="contained"
                      disabled={!selectedFinding || generatingProposal}
                      onClick={() => {
                        const eventId = selectedFinding?.trafficEventId;
                        const findingId = selectedFinding?.findingId;

                        void generateLiveProposal(eventId, findingId);
                      }}
                    >
                      {generatingProposal
                        ? "Generating proposal..."
                        : "Generate Live LLM Proposal"}
                    </Button>

                    {generatingProposal && (
                      <Stack direction="row" spacing={2} alignitems="center">
                        <CircularProgress size={20} />
                        <Typography variant="body2">
                          Waiting for the provider response...
                        </Typography>
                      </Stack>
                    )}

                    {liveProposalError && (
                      <Alert severity="error">{liveProposalError}</Alert>
                    )}

                    {liveProposal && (
                      <Alert
                        severity={
                          liveProposal.status === "VALIDATED"
                            ? "success"
                            : liveProposal.status === "REJECTED"
                              ? "warning"
                              : "error"
                        }
                      >
                        <Typography fontWeight={700}>
                          Outcome: {liveProposal.status}
                        </Typography>

                        <Typography
                          component="pre"
                          sx={{
                            whiteSpace: "pre-wrap",
                            overflowWrap: "anywhere",
                            fontSize: 12,
                            mt: 1,
                          }}
                        >
                          {JSON.stringify(liveProposal, null, 2)}
                        </Typography>
                      </Alert>
                    )}
                    {liveProposal && !liveProposal.proposal && (
                      <Alert severity="info">
                        No live proposal available. No live deterministic
                        validation performed. Original OpenAPI baseline
                        unchanged. Provider status:{" "}
                        {liveProposal.providerStatus ?? liveProposal.status}.
                      </Alert>
                    )}
                    {liveProposal?.proposal && (
                      <ContractComparison
                        baseline={baseline}
                        proposal={liveProposal.proposal}
                        validation={liveProposal.validation}
                        source="Current manually initiated live response"
                      />
                    )}
                  </Stack>
                </CardContent>
              </Card>
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
                        justifycontent="space-between"
                        alignitems={{ xs: "start", sm: "center" }}
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

                      {attempt.changeKind && (
                        <ContractComparison
                          baseline={baseline}
                          proposal={attempt}
                          validation={{
                            validatorAccepted: attempt.validatorAccepted,
                            validatorCodes: attempt.validatorCodes,
                          }}
                          source={`Recorded E6 attempt ${attempt.attemptId}`}
                        />
                      )}

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

export default LegacyDashboard;
