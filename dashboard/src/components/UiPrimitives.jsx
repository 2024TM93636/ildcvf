import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Stack,
  Typography,
} from "@mui/material";

export function PageHeader({ title, description }) {
  return (
    <Box component="header" sx={{ mb: 3 }}>
      <Typography
        variant="h4"
        component="h1"
        fontWeight={750}
        sx={{ letterSpacing: "-0.025em" }}
      >
        {title}
      </Typography>
      {description && (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ mt: 0.75, maxWidth: 800 }}
        >
          {description}
        </Typography>
      )}
    </Box>
  );
}

export function SectionCard({ title, subtitle, children }) {
  return (
    <Card variant="outlined" component="section" sx={{ mb: 2.5 }}>
      <CardContent
        sx={{ p: { xs: 2, sm: 3 }, "&:last-child": { pb: { xs: 2, sm: 3 } } }}
      >
        <Typography variant="h6" fontWeight={700}>
          {title}
        </Typography>
        {subtitle && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ mt: 0.5, mb: 2 }}
          >
            {subtitle}
          </Typography>
        )}
        <Divider sx={{ my: 2 }} />
        {children}
      </CardContent>
    </Card>
  );
}

export function MetricCard({ label, value }) {
  return (
    <Card variant="outlined" sx={{ flex: "1 1 170px", minWidth: 0 }}>
      <CardContent sx={{ p: 2.5 }}>
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        <Typography
          variant="h5"
          fontWeight={750}
          sx={{ mt: 0.75, overflowWrap: "anywhere" }}
        >
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}

const semantic = {
  VALIDATED: "success",
  APPROVED: "success",
  VALID: "success",
  REJECTED: "error",
  FAILED: "error",
  TIMEOUT: "warning",
  PENDING: "warning",
  BREAKING: "error",
  NON_BREAKING: "info",
  UNKNOWN: "default",
  CANDIDATE: "info",
};
export function StatusChip({ status, label, color, ...props }) {
  const normalized = String(status ?? "UNKNOWN")
    .toUpperCase()
    .replace(/[- ]/g, "_");
  return (
    <Chip
      size="small"
      variant="outlined"
      color={color || semantic[normalized] || "default"}
      label={label ?? status ?? "Unknown"}
      {...props}
    />
  );
}
export function LoadingState({ operation = "Loading records" }) {
  return (
    <Stack
      role="status"
      direction="row"
      spacing={1.5}
      sx={{ py: 2, alignItems: "center" }}
    >
      <CircularProgress size={20} />
      <Typography variant="body2">{operation}…</Typography>
    </Stack>
  );
}
export function EmptyState({
  message = "No records available for this view.",
}) {
  return (
    <Box sx={{ py: 3, textAlign: "center" }}>
      <Typography color="text.secondary" variant="body2">
        {message}
      </Typography>
    </Box>
  );
}
export function ErrorState({ message }) {
  return (
    <Alert
      severity="error"
      role="alert"
      sx={{ my: 2, overflowWrap: "anywhere" }}
    >
      {message || "Unable to load data. Try refreshing this view."}
    </Alert>
  );
}
