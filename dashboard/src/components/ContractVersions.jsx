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
  Typography,
} from "@mui/material";

const API = "/api/contract/versions";
const PAGE_SIZE = 10;

function show(value) {
  return value === null || value === undefined || value === ""
    ? "—"
    : String(value);
}

function dateTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function Field({ label, value, mono = false }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography
        variant="body2"
        sx={{
          overflowWrap: "anywhere",
          fontFamily: mono ? "monospace" : "inherit",
        }}
      >
        {show(value)}
      </Typography>
    </Box>
  );
}

export default function ContractVersions({ refreshToken = 0 }) {
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState("");
  const requestRef = useRef(0);

  const refresh = useCallback(() => setReload((value) => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();

    axios
      .get(API, {
        params: { page, size: PAGE_SIZE },
        signal: controller.signal,
      })
      .then(({ data }) => {
        if (!controller.signal.aborted) {
          if (!Array.isArray(data?.content))
            throw new Error("Unexpected history response");
          setHistory(data);
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) {
          setHistory(null);
          setError(
            err.response?.data?.message ||
              err.message ||
              "Could not load contract versions.",
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, reload, refreshToken]);

  const openDetails = async (id) => {
    const request = ++requestRef.current;
    setDetailOpen(true);
    setSelected(null);
    setDetailError("");
    setDownloadError("");
    setDetailLoading(true);
    try {
      const { data } = await axios.get(`${API}/${encodeURIComponent(id)}`);
      if (request === requestRef.current) setSelected(data);
    } catch (err) {
      if (request === requestRef.current) {
        setDetailError(
          err.response?.status === 404
            ? "Contract version no longer exists."
            : err.response?.data?.message || "Unable to load version details.",
        );
      }
    } finally {
      if (request === requestRef.current) setDetailLoading(false);
    }
  };

  const closeDetails = () => {
    if (downloading) return;
    ++requestRef.current;
    setDetailOpen(false);
  };

  const download = async () => {
    if (!selected?.id || downloading) return;
    setDownloading(true);
    setDownloadError("");
    try {
      const response = await axios.get(
        `${API}/${encodeURIComponent(selected.id)}/download`,
        {
          responseType: "blob",
        },
      );
      const url = URL.createObjectURL(response.data);
      try {
        const link = document.createElement("a");
        link.href = url;
        link.download = `contract-version-${selected.id}.yaml`;
        document.body.appendChild(link);
        link.click();
        link.remove();
      } finally {
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      }
    } catch (err) {
      setDownloadError(
        `Download failed (HTTP ${err.response?.status ?? "network error"}).`,
      );
    } finally {
      setDownloading(false);
    }
  };

  const rows = Array.isArray(history?.content) ? history.content : [];
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
              Contract Versions
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Immutable, approval-gated OpenAPI candidates. Creating a version
              does not publish or activate it.
            </Typography>
          </Box>
          <Button variant="outlined" onClick={refresh} disabled={loading}>
            Refresh versions
          </Button>
        </Stack>
        <Divider sx={{ my: 2 }} />
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        {loading ? (
          <Stack direction="row" spacing={2} alignitems="center">
            <CircularProgress size={22} />
            <Typography>Loading contract versions...</Typography>
          </Stack>
        ) : !error && rows.length === 0 ? (
          <Alert severity="info">No contract versions have been created.</Alert>
        ) : (
          !error && (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Created</TableCell>
                    <TableCell>Version ID</TableCell>
                    <TableCell>Proposal attempt</TableCell>
                    <TableCell>Validation</TableCell>
                    <TableCell>Details</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((version) => (
                    <TableRow key={version.id} hover>
                      <TableCell sx={{ whiteSpace: "nowrap" }}>
                        {dateTime(version.createdAt)}
                      </TableCell>
                      <TableCell
                        sx={{
                          fontFamily: "monospace",
                          overflowWrap: "anywhere",
                        }}
                      >
                        {show(version.id)}
                      </TableCell>
                      <TableCell
                        sx={{
                          fontFamily: "monospace",
                          overflowWrap: "anywhere",
                        }}
                      >
                        {show(version.proposalAttemptId)}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          variant="outlined"
                          label={show(version.validationStatus)}
                          color={
                            version.validationStatus === "VALID" ||
                            version.validationStatus === "VALIDATED"
                              ? "success"
                              : "default"
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Button
                          size="small"
                          onClick={() => void openDetails(version.id)}
                        >
                          Inspect
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )
        )}
        {!loading && !error && (
          <Stack
            direction="row"
            alignitems="center"
            justifycontent="space-between"
            sx={{ mt: 2 }}
          >
            <Typography variant="caption">
              Page {page + 1} · {history?.totalElements ?? 0} versions
            </Typography>
            <Stack direction="row" spacing={1}>
              <Button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </Button>
              <Button
                disabled={page + 1 >= (history?.totalPages ?? 0)}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </Stack>
          </Stack>
        )}
        <Dialog
          open={detailOpen}
          onClose={closeDetails}
          fullWidth
          maxWidth="md"
        >
          <DialogTitle>Persisted Contract Version</DialogTitle>
          <DialogContent dividers>
            {detailLoading ? (
              <CircularProgress size={24} />
            ) : detailError ? (
              <Alert severity="error">{detailError}</Alert>
            ) : (
              selected && (
                <Stack spacing={2}>
                  <Alert severity="info">
                    This is a stored candidate document, not an active or
                    published contract.
                  </Alert>
                  <Field label="Version ID" value={selected.id} mono />
                  <Field label="Created" value={dateTime(selected.createdAt)} />
                  <Field
                    label="Validation status"
                    value={selected.validationStatus}
                  />
                  <Divider />
                  <Typography variant="subtitle2">Provenance</Typography>
                  <Field
                    label="Proposal attempt ID"
                    value={selected.proposalAttemptId}
                    mono
                  />
                  <Field
                    label="Review decision ID"
                    value={selected.reviewDecisionId}
                    mono
                  />
                  <Field
                    label="Baseline identifier"
                    value={selected.baselineIdentifier}
                  />
                  <Field
                    label="Baseline SHA-256"
                    value={selected.baselineSha256}
                    mono
                  />
                  <Field
                    label="Document SHA-256"
                    value={selected.documentSha256}
                    mono
                  />
                  <Alert severity="info">
                    The YAML download is served by the backend from persisted
                    content; it is not regenerated in the browser.
                  </Alert>
                  {downloadError && (
                    <Alert severity="error">{downloadError}</Alert>
                  )}
                </Stack>
              )
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={closeDetails} disabled={downloading}>
              Close
            </Button>
            <Button
              variant="contained"
              disabled={!selected?.id || detailLoading || downloading}
              onClick={() => void download()}
            >
              {downloading ? "Downloading..." : "Download persisted YAML"}
            </Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
}
