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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
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

const STATUSES = [
  "VALIDATED",
  "REJECTED",
  "PROVIDER_FAILURE",
  "TIMEOUT",
  "PARSE_FAILURE",
  "IN_PROGRESS",
];
const PAGE_SIZE = 10;

function StatusChip({ status }) {
  const color =
    status === "VALIDATED"
      ? "success"
      : status === "IN_PROGRESS"
        ? "info"
        : status === "REJECTED"
          ? "warning"
          : "error";
  return (
    <Chip
      size="small"
      label={status ?? "UNKNOWN"}
      color={color}
      variant="outlined"
    />
  );
}

function ReviewEligibility({ attempt }) {
  return attempt?.status === "VALIDATED" && attempt?.reviewEligible === true ? (
    <Alert severity="info">
      Validated proposal. See Human Review for its current decision status.
    </Alert>
  ) : (
    <Alert severity="warning">Not eligible for human review.</Alert>
  );
}

function JsonDetails({ label, data }) {
  if (data == null)
    return (
      <Typography variant="body2" color="text.secondary">
        {label}: Not available.
      </Typography>
    );
  return (
    <Box>
      <Typography variant="subtitle2">{label}</Typography>
      <Box
        component="pre"
        sx={{
          overflowX: "auto",
          p: 2,
          bgcolor: "action.hover",
          borderRadius: 1,
          fontSize: 12,
          whiteSpace: "pre-wrap",
          overflowWrap: "anywhere",
        }}
      >
        {JSON.stringify(data, null, 2)}
      </Box>
    </Box>
  );
}

function AttemptDetails({ attempt }) {
  if (!attempt) return null;
  const errors = attempt.validation?.errors;
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} flexwrap="wrap" alignitems="center">
        <StatusChip status={attempt.status} />
        <Typography variant="caption">{attempt.attemptId}</Typography>
      </Stack>
      <ReviewEligibility attempt={attempt} />
      <Typography variant="body2">
        Traffic event: {attempt.trafficEventId}
      </Typography>
      <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
        Finding: {attempt.findingId}
      </Typography>
      <Typography variant="body2">
        Created: {formatTime(attempt.createdAt)} · Completed:{" "}
        {formatTime(attempt.completedAt)}
      </Typography>
      <Typography variant="body2">
        Provider: {attempt.providerStatus ?? "Not available"} · HTTP:{" "}
        {attempt.providerHttpStatus ?? "Not available"}
      </Typography>
      {attempt.message && <Alert severity="warning">{attempt.message}</Alert>}
      {Array.isArray(errors) && errors.length > 0 && (
        <Alert severity="warning">
          Deterministic validation returned {errors.length} error(s).
        </Alert>
      )}
      <JsonDetails label="Generated proposal" data={attempt.proposal} />
      <JsonDetails label="Deterministic validation" data={attempt.validation} />
    </Stack>
  );
}

function formatTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

export default function ProposalHistory() {
  const [page, setPage] = useState(0);
  const [eventFilter, setEventFilter] = useState("");
  const [findingFilter, setFindingFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [applied, setApplied] = useState({
    trafficEventId: "",
    findingId: "",
    status: "",
  });
  const [refreshKey, setRefreshKey] = useState(0);
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    axios
      .get("/api/llm/demo/proposals", {
        params: {
          page,
          size: PAGE_SIZE,
          ...Object.fromEntries(
            Object.entries(applied).filter(([, value]) => value),
          ),
        },
        signal: controller.signal,
      })
      .then(({ data }) => {
        if (!controller.signal.aborted) {
          setHistory(data);
          setError("");
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) {
          setError(
            err.response?.data?.message ||
              "Unable to retrieve persisted proposal history.",
          );
          setHistory(null);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, applied, refreshKey]);

  const refresh = useCallback(() => {
    setLoading(true);
    setRefreshKey((value) => value + 1);
  }, []);
  const applyFilters = () => {
    setLoading(true);
    setPage(0);
    setApplied({
      trafficEventId: eventFilter.trim(),
      findingId: findingFilter.trim(),
      status: statusFilter,
    });
  };
  const showDetail = async (attemptId) => {
    setOpen(true);
    setSelected(null);
    setDetailError("");
    setDetailLoading(true);
    try {
      const { data } = await axios.get(
        `/api/llm/demo/proposals/${encodeURIComponent(attemptId)}`,
      );
      setSelected(data);
    } catch (err) {
      setDetailError(
        err.response?.status === 404
          ? "This attempt could not be found."
          : "Unable to load attempt details.",
      );
    } finally {
      setDetailLoading(false);
    }
  };
  const rows = Array.isArray(history?.content) ? history.content : [];
  const latest =
    !applied.trafficEventId &&
    !applied.findingId &&
    !applied.status &&
    page === 0
      ? rows[0]
      : null;
  return (
    <Card variant="outlined" component="section" sx={{ mt: 3 }}>
      <CardContent sx={{ p: 3 }}>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={1}
          sx={{ justifyContent: "space-between", alignitems: { sm: "center" } }}
        >
          <Box>
            <Typography variant="h6" fontWeight={700}>
              Persisted LLM Proposal History
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Live generation attempts saved in PostgreSQL. Separate from
              recorded E6 evidence and the current-session response.
            </Typography>
          </Box>
          <Button variant="outlined" onClick={refresh} disabled={loading}>
            Refresh history
          </Button>
        </Stack>
        <Divider sx={{ my: 2 }} />
        {latest && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Latest persisted attempt: <strong>{latest.status}</strong> ·{" "}
            {formatTime(latest.createdAt)} · {latest.trafficEventId}.{" "}
            {latest.reviewEligible
              ? "See Human Review for its current decision status."
              : "Not review-eligible."}
          </Alert>
        )}
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={1.5}
          sx={{ mb: 2 }}
        >
          <TextField
            size="small"
            label="Traffic event ID"
            value={eventFilter}
            onChange={(e) => setEventFilter(e.target.value)}
            sx={{ flex: 1 }}
          />
          <TextField
            size="small"
            label="Finding ID"
            value={findingFilter}
            onChange={(e) => setFindingFilter(e.target.value)}
            sx={{ flex: 1 }}
          />
          <FormControl size="small" sx={{ minWidth: 175 }}>
            <InputLabel>Status</InputLabel>
            <Select
              label="Status"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <MenuItem value="">All statuses</MenuItem>
              {STATUSES.map((status) => (
                <MenuItem key={status} value={status}>
                  {status}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button variant="contained" onClick={applyFilters}>
            Apply filters
          </Button>
        </Stack>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
            <Button size="small" onClick={refresh}>
              Retry
            </Button>
          </Alert>
        )}
        {loading ? (
          <Stack direction="row" spacing={2} alignitems="center">
            <CircularProgress size={22} />
            <Typography>Loading saved attempts…</Typography>
          </Stack>
        ) : !error && rows.length === 0 ? (
          <Alert severity="info">
            No persisted proposal attempts found. Recorded E6 experiment
            evidence is displayed separately below.
          </Alert>
        ) : (
          !error && (
            <>
              <TableContainer sx={{ overflowX: "auto" }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Created</TableCell>
                      <TableCell>Event / finding</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Review eligibility</TableCell>
                      <TableCell>Details</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((attempt) => (
                      <TableRow key={attempt.attemptId}>
                        <TableCell sx={{ whiteSpace: "nowrap" }}>
                          {formatTime(attempt.createdAt)}
                        </TableCell>
                        <TableCell sx={{ overflowWrap: "anywhere" }}>
                          {attempt.trafficEventId}
                          <Typography
                            variant="caption"
                            display="block"
                            color="text.secondary"
                          >
                            {attempt.findingId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <StatusChip status={attempt.status} />
                        </TableCell>
                        <TableCell>
                          {attempt.reviewEligible &&
                          attempt.status === "VALIDATED"
                            ? "Validated — See Human Review"
                            : "Not eligible"}
                        </TableCell>
                        <TableCell>
                          <Button
                            size="small"
                            onClick={() => showDetail(attempt.attemptId)}
                          >
                            View
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Stack
                direction="row"
                sx={{
                  mt: 2,
                  justifyContent: "space-between",
                  alignitems: "center",
                }}
              >
                <Typography variant="caption">
                  Page {page + 1} · {history?.totalElements ?? 0} attempts
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    disabled={page === 0}
                    onClick={() => {
                      setLoading(true);
                      setPage((p) => p - 1);
                    }}
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={page + 1 >= (history?.totalPages ?? 0)}
                    onClick={() => {
                      setLoading(true);
                      setPage((p) => p + 1);
                    }}
                  >
                    Next
                  </Button>
                </Stack>
              </Stack>
            </>
          )
        )}
        <Dialog
          open={open}
          onClose={() => setOpen(false)}
          fullWidth
          maxWidth="md"
        >
          <DialogTitle>Persisted proposal attempt</DialogTitle>
          <DialogContent dividers>
            {detailLoading ? (
              <CircularProgress size={24} />
            ) : detailError ? (
              <Alert severity="error">{detailError}</Alert>
            ) : (
              <AttemptDetails attempt={selected} />
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpen(false)}>Close</Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
}
