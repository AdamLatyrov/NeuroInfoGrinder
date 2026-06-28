import { apiBaseUrl } from "./env";

const TOKEN_KEY = "nig_token";

async function parseResponseBody<T>(response: Response): Promise<T> {
    if (response.status === 204) {
        return undefined as T;
    }

    const text = await response.text();
    if (!text.trim()) {
        return undefined as T;
    }

    return JSON.parse(text) as T;
}

export function getAuthHeaders(): Record<string, string> {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return {};
    return { Authorization: `Bearer ${token}` };
}

function handleUnauthorized(response: Response): void {
    if (response.status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        // Use React Router navigation instead of hard redirect
        // Only redirect if we're not already on /login
        if (!window.location.pathname.startsWith("/login")) {
            window.location.href = "/login";
        }
    }
}

function apiUrl(path: string): string {
    return path.startsWith("/api/") || path.startsWith("/actuator") ? path : `${apiBaseUrl}${path}`;
}

export async function getJson<T>(path: string): Promise<T> {
    const response = await fetch(apiUrl(path), {
        headers: {
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    return parseResponseBody<T>(response);
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
    const response = await fetch(apiUrl(path), {
        method: "POST",
        headers: {
            Accept: "application/json",
            "Content-Type": "application/json; charset=utf-8"
        },
        body: JSON.stringify(body)
    });

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    return parseResponseBody<T>(response);
}

export async function getJsonAuth<T>(path: string): Promise<T> {
    const response = await fetch(apiUrl(path), {
        headers: {
            Accept: "application/json",
            ...getAuthHeaders()
        }
    });

    handleUnauthorized(response);

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    return parseResponseBody<T>(response);
}

export async function postJsonAuth<T>(path: string, body: unknown): Promise<T> {
    const response = await fetch(apiUrl(path), {
        method: "POST",
        headers: {
            Accept: "application/json",
            "Content-Type": "application/json; charset=utf-8",
            ...getAuthHeaders()
        },
        body: JSON.stringify(body)
    });

    handleUnauthorized(response);

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    return parseResponseBody<T>(response);
}

export async function postFormAuth<T>(path: string, body: FormData): Promise<T> {
    const response = await fetch(apiUrl(path), {
        method: "POST",
        headers: {
            Accept: "application/json",
            ...getAuthHeaders()
        },
        body
    });

    handleUnauthorized(response);

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }

    return parseResponseBody<T>(response);
}

export async function patchJsonAuth<T>(path: string, body: unknown): Promise<T> {
    const response = await fetch(apiUrl(path), {
        method: "PATCH",
        headers: {
            Accept: "application/json",
            "Content-Type": "application/json; charset=utf-8",
            ...getAuthHeaders()
        },
        body: JSON.stringify(body)
    });

    handleUnauthorized(response);

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    return parseResponseBody<T>(response);
}

export async function putJsonAuth<T>(path: string, body: unknown): Promise<T> {
    const response = await fetch(apiUrl(path), {
        method: "PUT",
        headers: {
            Accept: "application/json",
            "Content-Type": "application/json; charset=utf-8",
            ...getAuthHeaders()
        },
        body: JSON.stringify(body)
    });

    handleUnauthorized(response);

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    return parseResponseBody<T>(response);
}

export async function deleteJsonAuth(path: string): Promise<void> {
    const response = await fetch(apiUrl(path), {
        method: "DELETE",
        headers: {
            ...getAuthHeaders()
        }
    });

    handleUnauthorized(response);

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
}
