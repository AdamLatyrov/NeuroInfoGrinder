import { useEffect, useState } from "react";
import { apiBaseUrl } from "./env";
import { queryClient } from "./queryClient";

export type PipelineConnectionState = "connecting" | "connected" | "disconnected";

type PipelineEventPayload = {
  type?: string;
  messageId?: number;
  groupId?: number;
  stage?: string;
  status?: string;
};

function refreshPipelineQueries(payload: PipelineEventPayload) {
  const eventType = payload.type ?? "";
  const isTelegramRefreshEvent =
    eventType === "MESSAGE_INGESTED" ||
    eventType === "MESSAGE_SYNC_COMPLETED" ||
    eventType === "MESSAGE_SYNC_FAILED" ||
    eventType === "TOPICS_REFRESHED";

  if (payload.groupId != null) {
    queryClient.invalidateQueries({
      queryKey: ["group-messages", String(payload.groupId)],
    });
  } else if (isTelegramRefreshEvent) {
    queryClient.invalidateQueries({ queryKey: ["group-messages"] });
  }

  if (isTelegramRefreshEvent) {
    queryClient.invalidateQueries({ queryKey: ["groups"] });
    queryClient.invalidateQueries({ queryKey: ["topics"] });
  } else {
    queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
    queryClient.invalidateQueries({ queryKey: ["traces"] });
    if (payload.stage === "GUIDE_GENERATION" || payload.status === "GUIDE_FOUND") {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
    }
  }

  if (payload.messageId != null) {
    queryClient.invalidateQueries({ queryKey: ["message-trace", String(payload.messageId)] });
  }
}

export function usePipelineEvents() {
  const [state, setState] = useState<PipelineConnectionState>("connecting");

  useEffect(() => {
    const events = new EventSource(`${apiBaseUrl}/pipeline/events/stream`);

    events.addEventListener("connected", () => setState("connected"));
    events.addEventListener("pipeline", (event) => {
      setState("connected");
      const payload = JSON.parse(
        (event as MessageEvent<string>).data
      ) as PipelineEventPayload;
      refreshPipelineQueries(payload);
    });
    events.onerror = () => setState("disconnected");

    return () => events.close();
  }, []);

  return state;
}
