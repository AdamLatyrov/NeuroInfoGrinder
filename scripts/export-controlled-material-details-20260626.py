import json
import urllib.request

base = "http://127.0.0.1:8080"
out = []
for material_id in [23, 24, 25]:
    with urllib.request.urlopen(base + f"/api/v1/materials/{material_id}") as response:
        out.append(json.loads(response.read().decode("utf-8")))
print(json.dumps(out, ensure_ascii=False, indent=2))
