"""OpenAPI(/v3/api-docs) → Postman 컬렉션(v2.1).

사용: python make_collection.py [openapi 주소 또는 파일] [출력 파일]
기본: http://localhost:8080/v3/api-docs → ArgOS-gateway.postman_collection.json
로그인 요청을 보내면 accessToken 이 컬렉션 변수에 저장되고, 나머지 요청은 그 토큰을 쓴다.
"""
import json
import re
import sys
import urllib.request
from collections import OrderedDict

src = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/v3/api-docs"
out = sys.argv[2] if len(sys.argv) > 2 else "ArgOS-gateway.postman_collection.json"

if src.startswith("http"):
    spec = json.load(urllib.request.urlopen(src))
else:
    spec = json.load(open(src, encoding="utf-8"))
schemas = spec.get("components", {}).get("schemas", {})

# 경로 변수 예시 값 (시연 데이터: 5공장, PNT 도장 공정 12)
PATH_DEFAULTS = {"factoryId": "5", "processId": "12", "siteId": "1"}

LOGIN_EXAMPLE = {"loginId": "admin", "password": "Argos1234!", "client": "WEB"}


def example(schema, depth=0):
    if not schema or depth > 4:
        return None
    if "$ref" in schema:
        return example(schemas.get(schema["$ref"].split("/")[-1], {}), depth + 1)
    if "example" in schema:
        return schema["example"]
    if "enum" in schema:
        return schema["enum"][0]
    t = schema.get("type")
    if t == "object" or "properties" in schema:
        return {k: example(v, depth + 1) for k, v in schema.get("properties", {}).items()}
    if t == "array":
        item = example(schema.get("items"), depth + 1)
        return [] if item is None else [item]
    if t == "integer":
        return 1
    if t == "number":
        return 1.0
    if t == "boolean":
        return False
    if t == "string":
        return {"date": "2026-11-16", "date-time": "2026-11-16T09:00:00+09:00"}.get(schema.get("format"), "")
    return None


def param_value(p):
    """필수 쿼리에 넣어 둘 예시 값 (factoryId=5 처럼 시연 데이터 기준)"""
    sch = p.get("schema", {})
    if p["name"] == "factoryId":
        return "5"
    if p["name"] in ("processId",):
        return "12"
    v = example(sch)
    return "" if v is None else str(v).lower() if isinstance(v, bool) else str(v)


folders = OrderedDict()
for path, item in sorted(spec["paths"].items()):
    for method, op in item.items():
        tag = (op.get("tags") or ["기타"])[0]
        pm_path = re.sub(r"\{(\w+)\}", r":\1", path)
        params = op.get("parameters", [])
        url = {
            "raw": "{{baseUrl}}" + pm_path,
            "host": ["{{baseUrl}}"],
            "path": [p for p in pm_path.split("/") if p],
            "variable": [{"key": p["name"], "value": PATH_DEFAULTS.get(p["name"], "1")}
                         for p in params if p.get("in") == "path"],
            "query": [{"key": p["name"], "value": param_value(p), "disabled": not p.get("required", False),
                       "description": "필수" if p.get("required") else "선택"}
                      for p in params if p.get("in") == "query"],
        }
        if url["query"]:
            url["raw"] += "?" + "&".join(q["key"] + "=" + q["value"] for q in url["query"] if not q["disabled"])
            url["raw"] = url["raw"].rstrip("?")
        req = {"method": method.upper(), "header": [], "url": url,
               "description": op.get("summary") or op.get("description") or ""}
        body = op.get("requestBody", {}).get("content", {})
        if "application/json" in body:
            ex = LOGIN_EXAMPLE if path == "/api/v1/auth/login" else example(body["application/json"].get("schema"))
            req["header"].append({"key": "Content-Type", "value": "application/json"})
            req["body"] = {"mode": "raw", "raw": json.dumps(ex, ensure_ascii=False, indent=2),
                           "options": {"raw": {"language": "json"}}}
        elif "multipart/form-data" in body:
            props = example(body["multipart/form-data"].get("schema")) or {}
            req["body"] = {"mode": "formdata", "formdata": [
                {"key": k, "type": "file" if k in ("file", "files") else "text", "value": "" if v is None else str(v)}
                for k, v in props.items()]}
        entry = {"name": f"{method.upper()} {path.replace('/api/v1', '')}", "request": req}
        if op.get("security") == []:
            req["auth"] = {"type": "noauth"}
        if path == "/api/v1/auth/login":
            entry["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": [
                "const d = pm.response.json().data;",
                "if (d && d.accessToken) { pm.collectionVariables.set('accessToken', d.accessToken); }"]}}]
        folders.setdefault(tag, []).append(entry)

collection = {
    "info": {
        "name": spec["info"]["title"],
        "description": spec["info"].get("description", "")
                       + "\n\n먼저 '01 로그인 · 내 정보' 의 POST /auth/login 을 보내면 토큰이 저장된다. "
                         "서버 주소는 컬렉션 변수 baseUrl 에서 바꾼다.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}]},
    "variable": [{"key": "baseUrl", "value": "http://localhost:8080"},
                 {"key": "accessToken", "value": ""}],
    "item": [{"name": tag, "item": items} for tag, items in sorted(folders.items())],
}
with open(out, "w", encoding="utf-8", newline="\n") as f:
    json.dump(collection, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(out, sum(len(v) for v in folders.values()), "requests,", len(folders), "folders")
