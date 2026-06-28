import json
import urllib.request

base = "http://127.0.0.1:8080"
ids = [23, 24, 25]
with urllib.request.urlopen(base + "/api/v1/materials?page=0&size=20") as response:
    print(json.dumps({"list_status": response.status}, ensure_ascii=False))
for material_id in ids:
    with urllib.request.urlopen(base + f"/api/v1/materials/{material_id}") as response:
        data = json.loads(response.read().decode("utf-8"))
    print(json.dumps({
        "id": material_id,
        "title": data.get("title"),
        "contentType": data.get("contentType"),
        "status": data.get("status"),
        "candidateType": data.get("candidateType"),
        "segmentId": data.get("segmentId"),
        "sourceCount": data.get("sourceCount"),
        "sourceMessages": len(data.get("sourceMessages") or []),
        "traceStages": len(data.get("traceStages") or []),
        "providerCalls": len(data.get("providerCalls") or []),
        "hasContent": bool(data.get("content")),
    }, ensure_ascii=False))
