import { useMutation, useQuery } from "@tanstack/react-query";
import { getJsonAuth, postJson } from "./http";
import { queryClient } from "./queryClient";

const TOKEN_KEY = "nig_token";

export interface LoginRequest {
    username: string;
    password: string;
}

export interface LoginResponse {
    token: string;
    userId: number;
    username: string;
    role: string;
}

export interface CurrentUser {
    userId: number;
    username: string;
    role: string;
}

export function setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
    localStorage.removeItem(TOKEN_KEY);
}

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function isAuthenticated(): boolean {
    return !!getToken();
}

export function useLoginMutation() {
    return useMutation({
        mutationFn: async (credentials: LoginRequest) => {
            const response = await postJson<LoginResponse>("/auth/login", credentials);
            setToken(response.token);
            return response;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["auth"] });
        }
    });
}

export function useCurrentUser() {
    return useQuery({
        queryKey: ["auth", "me"],
        queryFn: () => getJsonAuth<CurrentUser>("/auth/me"),
        enabled: isAuthenticated(),
        retry: false,
        staleTime: 300_000
    });
}
