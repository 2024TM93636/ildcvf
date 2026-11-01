import { useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import {
  AppBar,
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Toolbar,
  Typography,
  useMediaQuery,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";

const drawerWidth = 252;
const links = [
  { label: "Overview", path: "/" },
  { label: "Traffic Monitor", path: "/traffic" },
  { label: "Drift Analysis", path: "/drift" },
  { label: "LLM Proposals", path: "/proposals" },
  { label: "Human Review", path: "/reviews" },
  { label: "Contract Versions", path: "/versions" },
];

export default function AppShell() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up("md"));
  const location = useLocation();

  const navigation = (
    <Box component="nav" aria-label="Primary navigation" sx={{ pt: 1 }}>
      <List>
        {links.map(({ label, path }) => (
          <ListItemButton
            key={path}
            component={NavLink}
            to={path}
            end={path === "/"}
            selected={location.pathname === path}
            onClick={() => setMobileOpen(false)}
            sx={{
              mx: 1,
              mb: 0.5,
              borderRadius: 2,
              "&.Mui-selected": {
                bgcolor: "primary.main",
                color: "primary.contrastText",
              },
              "&.Mui-selected:hover": { bgcolor: "primary.dark" },
            }}
          >
            <ListItemText primary={label} />
          </ListItemButton>
        ))}
      </List>
      <Divider sx={{ my: 1 }} />
      <List>
        <ListItemButton
          component={NavLink}
          to="/legacy"
          selected={location.pathname === "/legacy"}
          onClick={() => setMobileOpen(false)}
          sx={{ mx: 1, borderRadius: 2 }}
        >
          <ListItemText
            primary="Operational dashboard (legacy)"
            slotProps={{ primary: { variant: "body2" } }}
          />
        </ListItemButton>
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{
          zIndex: (t) => t.zIndex.drawer + 1,
          borderBottom: 1,
          borderColor: "divider",
        }}
      >
        <Toolbar>
          {!desktop && (
            <IconButton
              edge="start"
              aria-label="Open navigation"
              onClick={() => setMobileOpen(true)}
              sx={{ mr: 2 }}
            >
              <Box
                component="span"
                aria-hidden="true"
                sx={{ fontSize: 24, lineHeight: 1 }}
              >
                ☰
              </Box>
            </IconButton>
          )}
          <Typography variant="h6" fontWeight={700}>
            ILDCVF
          </Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ ml: 2, display: { xs: "none", sm: "block" } }}
          >
            API Contract Drift Verification
          </Typography>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        open
        sx={{
          display: { xs: "none", md: "block" },
          width: drawerWidth,
          flexShrink: 0,
          "& .MuiDrawer-paper": {
            width: drawerWidth,
            boxSizing: "border-box",
            pt: 8,
            borderRight: 1,
            borderColor: "divider",
          },
        }}
      >
        {navigation}
      </Drawer>
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: "block", md: "none" },
          "& .MuiDrawer-paper": {
            width: drawerWidth,
            boxSizing: "border-box",
            pt: 8,
          },
        }}
      >
        {navigation}
      </Drawer>
      <Box
        component="main"
        id="main-content"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          pt: { xs: 11, md: 12 },
          px: { xs: 2, sm: 3, lg: 4 },
          pb: 5,
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
}
