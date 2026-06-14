import { useQuery } from "@tanstack/react-query";
import { z } from "zod";

import { getJson } from "./http";

const systemInfoSchema = z.object({
    application: z.string(),
    version: z.string(),
    status: z.string()
});

export type SystemInfo = z.infer<typeof systemInfoSchema>;

export function useSystemInfoQuery() {
    return useQuery({
        queryKey: ["system-info"],
        queryFn: async () => {
            const response = await getJson<SystemInfo>("/system/info");
            return systemInfoSchema.parse(response);
        }
    });
}
