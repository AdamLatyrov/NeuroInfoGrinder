import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";

import { AppRouter } from "./app/router";
import { ThemeProvider } from "./app/theme";
import { queryClient } from "./shared/api/queryClient";
import "./app/styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <ThemeProvider>
            <QueryClientProvider client={queryClient}>
                <AppRouter />
            </QueryClientProvider>
        </ThemeProvider>
    </React.StrictMode>
);
