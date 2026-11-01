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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";

const PAGE_SIZE = 20;

const proposalUrl = "/api/llm/demo/proposals";
const reviewUrl = "/api/llm/reviews";
const versionUrl = "/api/contract/versions";
const PROTECTED_ATTEMPT_ID = "9713d95c-2c14-4c68-ad78-cadc1b2ac6ca";

async function lookupVersion(attemptId) {
  try {
    const { data } = await axios.get(
      `${versionUrl}/by-proposal/${encodeURIComponent(attemptId)}`,
    );
    return { state: "exists", record: data };
  } catch (error) {
    if (error.response?.status === 404)
      return { state: "absent", record: null };
    throw error;
  }
}

function display(value) {
  return value === null || value === undefined || value === ""
    ? "Not available"
    : String(value);
}

function formatDate(value) {
  if (!value) return "Not available";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function EvidenceValue({ title, value }) {
  return (
    <Box
      sx={{
        flex: 1,
        minWidth: 0,
        p: 2,
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
      }}
    >
      <Typography variant="body2" color="text.secondary">
        {title}
      </Typography>
      <Typography variant="h6" sx={{ overflowWrap: "anywhere" }}>
        {display(value)}
      </Typography>
    </Box>
  );
}

function ProposalEvidence({ attempt }) {
  const proposal = attempt?.proposal;
  const validation = attempt?.validation;
  const errors = validation?.errors;

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} flexwrap="wrap" useFlexGap>
        <Chip
          label={display(attempt.status)}
          color={attempt.status === "VALIDATED" ? "success" : "default"}
          variant="outlined"
        />
        <Chip label={display(proposal?.changeKind)} variant="outlined" />
      </Stack>

      <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
        <strong>Proposal attempt:</strong> {attempt.attemptId}
      </Typography>
      <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
        <strong>Traffic event:</strong> {display(attempt.trafficEventId)}
      </Typography>
      <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
        <strong>Drift finding:</strong> {display(attempt.findingId)}
      </Typography>
      <Typography variant="body2">
        <strong>Documented operation:</strong> {display(proposal?.method)}{" "}
        {display(proposal?.matchedOpenApiPath)}
      </Typography>
      <Typography variant="body2">
        <strong>Observed runtime path:</strong> {display(proposal?.runtimePath)}
      </Typography>
      <Typography variant="body2">
        <strong>Response field:</strong> {display(proposal?.targetFieldPath)}
      </Typography>

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <EvidenceValue
          title="Original contract expectation"
          value={proposal?.expected}
        />
        <EvidenceValue title="Observed traffic" value={proposal?.observed} />
        <EvidenceValue
          title="Proposed contract value"
          value={proposal?.proposedValue}
        />
      </Stack>

      <Typography variant="body2">
        <strong>Proposal rationale:</strong> {display(proposal?.rationale)}
      </Typography>

      <Alert
        severity={
          attempt.status === "VALIDATED" &&
          Array.isArray(errors) &&
          errors.length === 0
            ? "success"
            : "warning"
        }
      >
        Deterministic validation status: {display(attempt.status)}.
        {Array.isArray(errors)
          ? ` Validation errors: ${errors.length}.`
          : " Validation details unavailable."}
      </Alert>

      <Box component="details">
        <Box component="summary" sx={{ cursor: "pointer" }}>
          <Typography component="span" variant="body2">
            Inspect complete persisted proposal and validation evidence
          </Typography>
        </Box>
        <Box
          component="pre"
          sx={{
            p: 2,
            mt: 1,
            bgcolor: "action.hover",
            borderRadius: 1,
            fontSize: 12,
            whiteSpace: "pre-wrap",
            overflowWrap: "anywhere",
          }}
        >
          {JSON.stringify(
            {
              proposal: attempt.proposal,
              validation: attempt.validation,
            },
            null,
            2,
          )}
        </Box>
      </Box>
    </Stack>
  );
}

async function lookupReview(attemptId, signal) {
  try {
    const { data } = await axios.get(
      `${reviewUrl}/${encodeURIComponent(attemptId)}`,
      { signal },
    );
    return { state: "reviewed", record: data };
  } catch (error) {
    if (error.response?.status === 404) {
      return { state: "pending", record: null };
    }
    throw error;
  }
}

export default function HumanReview({ onVersionCreated }) {
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [attemptPage, setAttemptPage] = useState(null);
  const [reviewPage, setReviewPage] = useState(null);
  const [reviewPageNumber, setReviewPageNumber] = useState(0);
  const [statuses, setStatuses] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [historyError, setHistoryError] = useState("");

  const [selected, setSelected] = useState(null);
  const [selectedReview, setSelectedReview] = useState(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");

  const [reviewerId, setReviewerId] = useState("");
  const [justification, setJustification] = useState("");
  const [confirmation, setConfirmation] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState(null);

  const submittingRef = useRef(false);
  const creatingRef = useRef(false);
  const [versionLookup, setVersionLookup] = useState({
    state: "unavailable",
    record: null,
  });
  const [createConfirmation, setCreateConfirmation] = useState(false);
  const [creating, setCreating] = useState(false);
  const [versionFeedback, setVersionFeedback] = useState(null);

  const refresh = useCallback(() => {
    setRefreshKey((key) => key + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    const load = async () => {
      setLoading(true);
      setError("");

      try {
        const { data } = await axios.get(proposalUrl, {
          params: { page, size: PAGE_SIZE },
          signal: controller.signal,
        });

        const rows = Array.isArray(data?.content) ? data.content : [];
        const results = await Promise.all(
          rows.map(async (attempt) => {
            try {
              const result = await lookupReview(
                attempt.attemptId,
                controller.signal,
              );
              return [attempt.attemptId, result];
            } catch {
              return [
                attempt.attemptId,
                { state: "unavailable", record: null },
              ];
            }
          }),
        );

        if (controller.signal.aborted) return;

        setAttemptPage(data);
        setStatuses(Object.fromEntries(results));
      } catch (err) {
        if (!controller.signal.aborted) {
          setAttemptPage(null);
          setError(
            err.response?.data?.message ||
              "Unable to load persisted proposals.",
          );
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };

    void load();
    return () => controller.abort();
  }, [page, refreshKey]);

  useEffect(() => {
    const controller = new AbortController();

    const loadHistory = async () => {
      setHistoryError("");
      try {
        const { data } = await axios.get(reviewUrl, {
          params: { page: reviewPageNumber, size: PAGE_SIZE },
          signal: controller.signal,
        });
        if (!controller.signal.aborted) setReviewPage(data);
      } catch (err) {
        if (!controller.signal.aborted) {
          setReviewPage(null);
          setHistoryError(
            err.response?.data?.message || "Unable to load review history.",
          );
        }
      }
    };

    void loadHistory();
    return () => controller.abort();
  }, [reviewPageNumber, refreshKey]);

  const openAttempt = async (attemptId) => {
    setDetailOpen(true);
    setSelected(null);
    setSelectedReview(null);
    setVersionLookup({ state: "loading", record: null });
    setVersionFeedback(null);
    setCreateConfirmation(false);
    setDetailError("");
    setFeedback(null);
    setReviewerId("");
    setJustification("");
    setDetailLoading(true);

    try {
      const [attemptResponse, reviewResult, versionResult] = await Promise.all([
        axios.get(`${proposalUrl}/${encodeURIComponent(attemptId)}`),
        lookupReview(attemptId),
        lookupVersion(attemptId),
      ]);

      setSelected(attemptResponse.data);
      setSelectedReview(reviewResult.record);
      setVersionLookup(versionResult);
      setStatuses((previous) => ({
        ...previous,
        [attemptId]: reviewResult,
      }));
    } catch (err) {
      console.error("Human Review detail loading failed:", err);

      const status = err.response?.status;
      const url = err.config?.url;

      setDetailError(
        `Unable to load proposal details or verify review status. ` +
          `HTTP status: ${status ?? "Network error"}. ` +
          `Endpoint: ${url ?? "Unknown"}.`,
      );
    } finally {
      setDetailLoading(false);
    }
  };

  const canReview =
    selected?.status === "VALIDATED" &&
    selected?.reviewEligible === true &&
    statuses[selected?.attemptId]?.state === "pending" &&
    !selectedReview &&
    !submitting;

  const canCreateVersion =
    selected?.status === "VALIDATED" &&
    selectedReview?.decision === "APPROVED" &&
    versionLookup.state === "absent" &&
    selected?.attemptId !== PROTECTED_ATTEMPT_ID &&
    !submitting &&
    !creating;

  const submitVersionCreation = async () => {
    if (!createConfirmation || !canCreateVersion || creatingRef.current) return;
    const attemptId = selected.attemptId;
    creatingRef.current = true;
    setCreating(true);
    setCreateConfirmation(false);
    setVersionFeedback(null);
    try {
      // Recheck persisted review and version immediately before mutation.
      const [review, version] = await Promise.all([
        lookupReview(attemptId),
        lookupVersion(attemptId),
      ]);
      setSelectedReview(review.record);
      setVersionLookup(version);
      if (
        review.record?.decision !== "APPROVED" ||
        version.state !== "absent"
      ) {
        setVersionFeedback({
          severity: "warning",
          message:
            "Eligibility changed. Refresh and inspect the persisted review/version state.",
        });
        return;
      }
      const { data } = await axios.post(versionUrl, {
        proposalAttemptId: attemptId,
      });
      setVersionLookup({ state: "exists", record: data });
      setVersionFeedback({
        severity: "success",
        message: `Contract candidate ${data.id} was created and stored. It is not published or active.`,
      });
      onVersionCreated?.(data);
      refresh();
      // Check current version status after review decision has persisted.
      try {
        setVersionLookup(await lookupVersion(attemptId));
      } catch {
        setVersionLookup({ state: "unavailable", record: null });
      }
    } catch (err) {
      const status = err.response?.status;
      if (status === 409) {
        const results = await Promise.allSettled([
          lookupReview(attemptId),
          lookupVersion(attemptId),
        ]);
        if (results[0].status === "fulfilled")
          setSelectedReview(results[0].value.record);
        if (results[1].status === "fulfilled")
          setVersionLookup(results[1].value);
        else setVersionLookup({ state: "unavailable", record: null });
        const existing =
          results[1].status === "fulfilled" ? results[1].value.record : null;
        setVersionFeedback({
          severity: "warning",
          message: existing
            ? `A candidate already exists: ${existing.id}. No additional version was created.`
            : "Creation conflict. The persisted proposal or review is no longer eligible. Refresh before retrying.",
        });
        refresh();
      } else {
        setVersionFeedback({
          severity: "error",
          message:
            status === 400
              ? "Invalid candidate creation request."
              : status === 404
                ? "The proposal could not be found."
                : status === 422
                  ? "This proposal or generated document is not supported for candidate creation."
                  : "Candidate creation failed. Verify backend availability and refresh before retrying.",
        });
        setVersionLookup({ state: "unavailable", record: null });
      }
    } finally {
      creatingRef.current = false;
      setCreating(false);
    }
  };

  const submitDecision = async () => {
    if (
      !confirmation ||
      !canReview ||
      submittingRef.current ||
      !reviewerId.trim() ||
      !justification.trim()
    )
      return;

    submittingRef.current = true;
    setSubmitting(true);
    setConfirmation(null);
    setFeedback(null);

    const attemptId = selected.attemptId;

    try {
      const { data } = await axios.post(reviewUrl, {
        proposalAttemptId: attemptId,
        decision: confirmation,
        reviewerId: reviewerId.trim(),
        justification: justification.trim(),
      });

      setSelectedReview(data);
      // Review approval does not create a version; operator must confirm separately.
      setVersionLookup({ state: "unavailable", record: null });
      setStatuses((previous) => ({
        ...previous,
        [attemptId]: { state: "reviewed", record: data },
      }));
      setFeedback({
        severity: "success",
        message: `Final decision persisted: ${data.decision}.`,
      });
      refresh();
    } catch (err) {
      if (err.response?.status === 409) {
        try {
          const current = await lookupReview(attemptId);
          setSelectedReview(current.record);
          setStatuses((previous) => ({
            ...previous,
            [attemptId]: current,
          }));
          setFeedback({
            severity: "warning",
            message: current.record
              ? `Another final decision already exists: ${current.record.decision}.`
              : "Review conflict. The proposal is no longer eligible or is being reviewed. Refresh and inspect its status.",
          });
        } catch {
          setStatuses((previous) => ({
            ...previous,
            [attemptId]: { state: "unavailable", record: null },
          }));
          setFeedback({
            severity: "error",
            message:
              "Review conflict occurred, but the saved outcome could not be retrieved.",
          });
        }
        refresh();
      } else {
        setStatuses((previous) => ({
          ...previous,
          [attemptId]: { state: "unavailable", record: null },
        }));
        setFeedback({
          severity: "error",
          message:
            err.response?.data?.message ||
            "Decision submission failed. Refresh before trying again.",
        });
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const rows = Array.isArray(attemptPage?.content) ? attemptPage.content : [];

  const reviewRows = Array.isArray(reviewPage?.content)
    ? reviewPage.content
    : [];

  const pendingCount = rows.filter(
    (attempt) =>
      attempt.status === "VALIDATED" &&
      attempt.reviewEligible === true &&
      statuses[attempt.attemptId]?.state === "pending",
  ).length;

  return (
    <Card variant="outlined" component="section" sx={{ mt: 3 }}>
      <CardContent sx={{ p: 3 }}>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          justifycontent="space-between"
          alignitems={{ sm: "center" }}
          spacing={2}
        >
          <Box>
            <Typography variant="h6" fontWeight={700}>
              Human Review
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Inspect grounded proposals and record final human decisions.
              Approval does not publish or modify an OpenAPI contract.
            </Typography>
          </Box>
          <Button variant="outlined" onClick={refresh} disabled={loading}>
            Refresh
          </Button>
        </Stack>

        <Divider sx={{ my: 2 }} />

        <Alert severity="info" sx={{ mb: 2 }}>
          Reviewer identifiers are operator-supplied and are not authenticated.
          Use disposable proposals for testing. Do not review the existing
          successful Groq demonstration proposal without explicit authorization.
        </Alert>

        <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1 }}>
          Proposal review queue
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {pendingCount} pending on this page. All proposal statuses are shown;
          only validated, unreviewed proposals can receive decisions.
        </Typography>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {loading ? (
          <Stack direction="row" spacing={2} alignitems="center">
            <CircularProgress size={22} />
            <Typography>Loading proposal review status...</Typography>
          </Stack>
        ) : !error && rows.length === 0 ? (
          <Alert severity="info">No persisted proposals found.</Alert>
        ) : (
          !error && (
            <>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Proposal / event</TableCell>
                      <TableCell>Validation</TableCell>
                      <TableCell>Review state</TableCell>
                      <TableCell>Action</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((attempt) => {
                      const state = statuses[attempt.attemptId];
                      const eligible =
                        attempt.status === "VALIDATED" &&
                        attempt.reviewEligible === true;
                      const label =
                        state?.state === "reviewed"
                          ? (state.record?.decision ?? "Reviewed")
                          : state?.state === "unavailable"
                            ? "Status unavailable"
                            : eligible && state?.state === "pending"
                              ? "Pending review"
                              : "Not eligible";

                      return (
                        <TableRow key={attempt.attemptId}>
                          <TableCell sx={{ overflowWrap: "anywhere" }}>
                            <Typography variant="body2">
                              {attempt.trafficEventId}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {attempt.attemptId}
                            </Typography>
                          </TableCell>
                          <TableCell>{display(attempt.status)}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={label}
                              color={
                                label === "Pending review"
                                  ? "warning"
                                  : state?.state === "reviewed"
                                    ? "success"
                                    : "default"
                              }
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>
                            <Button
                              size="small"
                              onClick={() =>
                                void openAttempt(attempt.attemptId)
                              }
                            >
                              Inspect
                            </Button>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>

              <Stack
                direction="row"
                justifycontent="space-between"
                alignitems="center"
                sx={{ mt: 2 }}
              >
                <Typography variant="caption">
                  Page {page + 1} · {attemptPage?.totalElements ?? 0} proposals
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    disabled={page === 0 || loading}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={
                      loading || page + 1 >= (attemptPage?.totalPages ?? 0)
                    }
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </Stack>
              </Stack>
            </>
          )
        )}

        <Divider sx={{ my: 3 }} />

        <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 2 }}>
          Persisted review history
        </Typography>

        {historyError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {historyError}
          </Alert>
        )}

        {!historyError && reviewPage && reviewRows.length === 0 && (
          <Alert severity="info">No review decisions on this page.</Alert>
        )}

        {!historyError && reviewRows.length > 0 && (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Decision</TableCell>
                  <TableCell>Reviewer (operator-supplied)</TableCell>
                  <TableCell>Justification</TableCell>
                  <TableCell>Decision time</TableCell>
                  <TableCell>Proposal</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {reviewRows.map((record) => (
                  <TableRow key={record.reviewId}>
                    <TableCell>{record.decision}</TableCell>
                    <TableCell>{record.reviewerId}</TableCell>
                    <TableCell sx={{ overflowWrap: "anywhere" }}>
                      {record.justification}
                    </TableCell>
                    <TableCell>{formatDate(record.decidedAt)}</TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        onClick={() =>
                          void openAttempt(record.proposalAttemptId)
                        }
                      >
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        <Stack
          direction="row"
          justifycontent="flex-end"
          alignitems="center"
          spacing={1}
          sx={{ mt: 2 }}
        >
          <Button
            disabled={reviewPageNumber === 0}
            onClick={() => setReviewPageNumber((p) => p - 1)}
          >
            Previous
          </Button>
          <Typography variant="caption">
            History page {reviewPageNumber + 1}
          </Typography>
          <Button
            disabled={
              !reviewPage || reviewPageNumber + 1 >= reviewPage.totalPages
            }
            onClick={() => setReviewPageNumber((p) => p + 1)}
          >
            Next
          </Button>
        </Stack>

        <Dialog
          open={detailOpen}
          onClose={() => {
            if (!submitting) setDetailOpen(false);
          }}
          fullWidth
          maxWidth="md"
        >
          <DialogTitle>Proposal Review</DialogTitle>
          <DialogContent dividers>
            {detailLoading ? (
              <CircularProgress size={24} />
            ) : detailError ? (
              <Alert severity="error">{detailError}</Alert>
            ) : (
              selected && (
                <Stack spacing={3}>
                  <ProposalEvidence attempt={selected} />

                  <Divider />

                  {feedback && (
                    <Alert severity={feedback.severity}>
                      {feedback.message}
                    </Alert>
                  )}

                  {versionFeedback && (
                    <Alert severity={versionFeedback.severity}>
                      {versionFeedback.message}
                    </Alert>
                  )}
                  {selectedReview?.decision === "APPROVED" && (
                    <Stack spacing={1}>
                      <Alert severity="info">
                        Human approval is recorded. A contract candidate is a
                        separate, explicit operation and will not publish or
                        activate the API contract.
                      </Alert>
                      {versionLookup.state === "exists" ? (
                        <Alert severity="success">
                          Existing contract candidate:{" "}
                          {versionLookup.record?.id}. View its provenance and
                          download in Contract Versions below.
                        </Alert>
                      ) : versionLookup.state === "absent" ? (
                        <Button
                          variant="contained"
                          color="primary"
                          disabled={!canCreateVersion}
                          onClick={() => setCreateConfirmation(true)}
                        >
                          Create Contract Version
                        </Button>
                      ) : (
                        <Alert severity="warning">
                          Version eligibility has not been verified. Reopen the
                          proposal to refresh persisted state.
                        </Alert>
                      )}
                      {selected?.attemptId === PROTECTED_ATTEMPT_ID && (
                        <Alert severity="warning">
                          Protected demonstration evidence: candidate creation
                          is disabled.
                        </Alert>
                      )}
                    </Stack>
                  )}

                  {selectedReview ? (
                    <Alert severity="success">
                      <Typography fontWeight={700}>
                        Final decision: {selectedReview.decision}
                      </Typography>
                      <Typography variant="body2">
                        Reviewer (operator-supplied):{" "}
                        {selectedReview.reviewerId}
                      </Typography>
                      <Typography variant="body2">
                        Justification: {selectedReview.justification}
                      </Typography>
                      <Typography variant="body2">
                        Recorded: {formatDate(selectedReview.decidedAt)}
                      </Typography>
                      <Typography variant="body2">
                        Contract published: No
                      </Typography>
                    </Alert>
                  ) : canReview ? (
                    <>
                      <Alert severity="info">
                        This proposal is eligible for a final human decision.
                        The decision cannot be changed through this workflow.
                      </Alert>

                      <TextField
                        fullWidth
                        required
                        label="Reviewer identifier (operator-supplied)"
                        value={reviewerId}
                        onChange={(e) => setReviewerId(e.target.value)}
                        inputProps={{ maxLength: 128 }}
                        disabled={submitting}
                      />
                      <TextField
                        fullWidth
                        required
                        multiline
                        minRows={3}
                        label="Review justification"
                        value={justification}
                        onChange={(e) => setJustification(e.target.value)}
                        inputProps={{ maxLength: 4000 }}
                        disabled={submitting}
                      />
                      <Stack
                        direction={{ xs: "column", sm: "row" }}
                        spacing={2}
                      >
                        <Button
                          color="error"
                          variant="outlined"
                          disabled={
                            submitting ||
                            !reviewerId.trim() ||
                            !justification.trim()
                          }
                          onClick={() => setConfirmation("REJECTED")}
                        >
                          Reject
                        </Button>
                        <Button
                          color="success"
                          variant="contained"
                          disabled={
                            submitting ||
                            !reviewerId.trim() ||
                            !justification.trim()
                          }
                          onClick={() => setConfirmation("APPROVED")}
                        >
                          Approve
                        </Button>
                      </Stack>
                    </>
                  ) : (
                    <Alert severity="warning">
                      Review actions are unavailable. The proposal is
                      ineligible, already reviewed, or its current review status
                      could not be verified.
                    </Alert>
                  )}
                </Stack>
              )
            )}
          </DialogContent>
          <DialogActions>
            <Button disabled={submitting} onClick={() => setDetailOpen(false)}>
              Close
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog
          open={createConfirmation}
          onClose={() => {
            if (!creating) setCreateConfirmation(false);
          }}
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle>Confirm contract candidate creation</DialogTitle>
          <DialogContent>
            <Alert severity="warning" sx={{ mb: 2 }}>
              Creating a contract candidate does not publish or activate the API
              contract.
            </Alert>
            <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
              Source proposal: {selected?.attemptId}
            </Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
              A new immutable OpenAPI YAML candidate will be generated and
              persisted only if the backend independently verifies the approved
              proposal.
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button
              disabled={creating}
              onClick={() => setCreateConfirmation(false)}
            >
              Cancel
            </Button>
            <Button
              variant="contained"
              disabled={creating || !canCreateVersion}
              onClick={() => void submitVersionCreation()}
            >
              {creating ? "Creating..." : "Confirm creation"}
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog
          open={Boolean(confirmation)}
          onClose={() => {
            if (!submitting) setConfirmation(null);
          }}
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle>Confirm final review decision</DialogTitle>
          <DialogContent>
            <Alert severity="warning" sx={{ mb: 2 }}>
              This decision is final. It does not publish or modify the OpenAPI
              baseline.
            </Alert>
            <Typography variant="body2" sx={{ mb: 1 }}>
              <strong>Decision:</strong> {confirmation}
            </Typography>
            <Typography variant="body2" sx={{ mb: 1 }}>
              <strong>Reviewer:</strong> {reviewerId.trim()}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
              <strong>Justification:</strong> {justification.trim()}
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button disabled={submitting} onClick={() => setConfirmation(null)}>
              Cancel
            </Button>
            <Button
              variant="contained"
              color={confirmation === "REJECTED" ? "error" : "success"}
              disabled={submitting}
              onClick={() => void submitDecision()}
            >
              {submitting ? "Submitting..." : "Confirm final decision"}
            </Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
}
