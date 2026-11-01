import { lazy, Suspense } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { CssBaseline, ThemeProvider } from "@mui/material";
import AppShell from "./components/AppShell";
const LegacyDashboard = lazy(() => import("./LegacyDashboard"));
import OperationalScreen from "./OperationalScreen";
import theme from "./theme";
import { Button, Stack, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<OperationalScreen screen="overview" />} />
            <Route
              path="traffic"
              element={<OperationalScreen screen="traffic" />}
            />
            <Route
              path="drift"
              element={<OperationalScreen screen="drift" />}
            />
            <Route
              path="proposals"
              element={<OperationalScreen screen="proposals" />}
            />
            <Route
              path="reviews"
              element={<OperationalScreen screen="reviews" />}
            />
            <Route
              path="versions"
              element={<OperationalScreen screen="versions" />}
            />
            <Route
              path="legacy"
              element={
                <Suspense
                  fallback={<div role="status">Loading legacy dashboard…</div>}
                >
                  <LegacyDashboard />
                </Suspense>
              }
            />
            <Route path="not-found" element={<NotFound />} />
            <Route path="*" element={<Navigate to="/not-found" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}

function NotFound() {
  return (
    <Stack spacing={2} component="section">
      <Typography variant="h4" component="h1">
        Page not found
      </Typography>
      <Typography>The requested route does not exist.</Typography>
      <Button component={RouterLink} to="/" sx={{ alignSelf: "flex-start" }}>
        Return to overview
      </Button>
    </Stack>
  );
}
