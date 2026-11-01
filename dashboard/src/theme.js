import { createTheme } from "@mui/material/styles";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#2563eb", contrastText: "#ffffff" },
    background: { default: "#f5f7fb", paper: "#ffffff" },
    text: { primary: "#172033", secondary: "#526078" },
  },
  shape: { borderRadius: 10 },
  typography: { fontFamily: 'Inter, "Segoe UI", Arial, sans-serif' },
  components: {
    MuiCssBaseline: { styleOverrides: { body: { margin: 0 } } },
    MuiCard: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { borderRadius: 12 } },
    },
    MuiCardContent: { styleOverrides: { root: { overflowWrap: "anywhere" } } },
    MuiTableContainer: { styleOverrides: { root: { borderRadius: 8 } } },
    MuiTableCell: {
      styleOverrides: {
        head: { fontWeight: 700, backgroundColor: "#f8fafc", color: "#334155" },
        root: { verticalAlign: "top" },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { textTransform: "none", fontWeight: 650 } },
    },
    MuiAlert: { styleOverrides: { root: { borderRadius: 10 } } },
    MuiChip: { styleOverrides: { root: { fontWeight: 650 } } },
  },
});

export default theme;
