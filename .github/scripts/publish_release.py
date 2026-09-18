"""Publish the verified package with bounded GitHub API requests."""
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

repo = os.environ["GITHUB_REPOSITORY"]
token = os.environ["GH_TOKEN"]
version = os.environ.get("RELEASE_VERSION", "0.8.0")
tag = "v" + version
api = f"https://api.github.com/repos/{repo}"

def request(method, url, data=None, content_type="application/json"):
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json", "User-Agent": "Shiguang-Release", "X-GitHub-Api-Version": "2022-11-28"}
    if data is not None:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as response:
        body = response.read()
        return json.loads(body) if body else None

releases = request("GET", api + "/releases?per_page=100")
release = next((item for item in releases if item["tag_name"] == tag), None)
if release and not release["draft"]:
    print("Release already public; leaving its assets unchanged.", flush=True)
    raise SystemExit(0)
if release is None:
    print("Creating draft release", flush=True)
    payload = {"tag_name": tag, "target_commitish": os.environ["GITHUB_SHA"], "name": "拾光相册 " + version, "body": Path("dist/release-notes.md").read_text(encoding="utf-8"), "draft": True, "prerelease": False}
    release = request("POST", api + "/releases", json.dumps(payload).encode())
print("Draft ready", release["id"], flush=True)
existing = {a["name"]: a for a in release["assets"]}
for name in ["shiguang-album.apk", f"shiguang-album-{version}-source.zip", "SHA256SUMS.txt"]:
    data = (Path("release-assets") / name).read_bytes()
    digest = "sha256:" + hashlib.sha256(data).hexdigest()
    old = existing.get(name)
    if old and old.get("digest") == digest and old.get("state") == "uploaded":
        print("Verified existing draft asset:", name, flush=True)
        continue
    if old:
        request("DELETE", api + f'/releases/assets/{old["id"]}')
    print("Uploading:", name, len(data), flush=True)
    upload_url = release["upload_url"].split("{")[0] + "?" + urllib.parse.urlencode({"name": name})
    asset = request("POST", upload_url, data, "application/octet-stream")
    assert asset["state"] == "uploaded" and asset["size"] == len(data)
    assert asset.get("digest") in (None, digest)
    print("Uploaded:", name, flush=True)
print("Publishing complete release", flush=True)
request("PATCH", api + f'/releases/{release["id"]}', json.dumps({"draft": False, "make_latest": "true"}).encode())
print(release["html_url"], flush=True)
