import { useMutation, useQuery } from "@tanstack/react-query";
import { getJsonAuth, patchJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";

export interface AppUser {
  id: number;
  username: string;
  role: string;
  hidden: boolean;
  createdAt: string;
  updatedAt: string;
}

interface CreateUserRequest {
  username: string;
  password: string;
}

export function useUsersQuery() {
  return useQuery({
    queryKey: ["users"],
    queryFn: () => getJsonAuth<AppUser[]>("/users"),
    staleTime: 30_000,
  });
}

export function useCreateUserMutation() {
  return useMutation({
    mutationFn: (request: CreateUserRequest) => postJsonAuth<AppUser>("/users", request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });
}

export function useSetUserHiddenMutation() {
  return useMutation({
    mutationFn: ({ userId, hidden }: { userId: number; hidden: boolean }) =>
      patchJsonAuth<AppUser>(`/users/${userId}/hidden`, { hidden }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });
}

export function useChangeUserPasswordMutation() {
  return useMutation({
    mutationFn: ({ userId, password }: { userId: number; password: string }) =>
      patchJsonAuth<AppUser>(`/users/${userId}/password`, { password }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });
}
