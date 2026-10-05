"""Owner-authorized cleanup; application code and successful run evidence are retained."""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

repo = os.environ["GITHUB_REPOSITORY"]
run_id = int(os.environ["GITHUB_RUN_ID"])
head = os.environ["GITHUB_SHA"]
token = os.environ["GH_TOKEN"]
manifest = json.loads(Path("docs/audit/REPOSITORY_CLEANUP_20261005.json").read_text())
branch = manifest["default_branch"]
report = {"source_sha": head, "deleted_branches": [], "retained_branches": [],
          "cancelled_runs": [], "deleted_runs": [], "publication": None}

def api(method, path, data=None, allowed=()):
    request = urllib.request.Request(
        "https://api.github.com/repos/" + repo + "/" + path,
        data=None if data is None else json.dumps(data).encode(), method=method,
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            if error.code in allowed:
                return error.code, None
            if error.code >= 500 and attempt < 2:
                time.sleep(2 ** attempt)
                continue
            raise

def get(path):
    return api("GET", path)[1]

def runs():
    values = []
    for page in range(1, 6):
        batch = get("actions/runs?per_page=100&page=" + str(page))["workflow_runs"]
        values.extend(batch)
        if len(batch) < 100:
            break
    return values

def current_head():
    return get("git/ref/heads/" + urllib.parse.quote(branch, safe="/"))["object"]["sha"]

if current_head() != head:
    raise RuntimeError("Default branch moved; refuse cleanup against an outdated source")
open_pr_branches = {pr["head"]["ref"] for pr in get("pulls?state=open&per_page=100")}
active = [run for run in runs() if run["id"] != run_id and run["status"] != "completed"]
for run in active:
    status, _ = api("POST", "actions/runs/" + str(run["id"]) + "/cancel", allowed=(404, 409))
    if status == 409 and get("actions/runs/" + str(run["id"]))["status"] != "completed":
        api("POST", "actions/runs/" + str(run["id"]) + "/force-cancel", allowed=(404, 409))
    report["cancelled_runs"].append(run["id"])
    print("Cancellation requested:", run["id"])
for _ in range(10):
    pending = [run for run in runs() if run["id"] != run_id and run["status"] != "completed"]
    if not pending:
        break
    time.sleep(2)
else:
    raise RuntimeError("Old runs are still active; refuse to create another publisher")

for candidate in manifest["delete_branches"]:
    name, expected = candidate["name"], candidate["sha"]
    if name == branch or name in open_pr_branches:
        report["retained_branches"].append({"name": name, "reason": "default/open PR"})
        continue
    status, reference = api("GET", "git/ref/heads/" + urllib.parse.quote(name, safe="/"), allowed=(404,))
    if status == 404:
        continue
    if reference["object"]["sha"] != expected:
        report["retained_branches"].append({"name": name, "reason": "branch changed"})
        continue
    if get("compare/" + expected + "..." + head)["status"] not in ("ahead", "identical"):
        report["retained_branches"].append({"name": name, "reason": "not integrated"})
        continue
    api("DELETE", "git/refs/heads/" + urllib.parse.quote(name, safe="/"))
    report["deleted_branches"].append({"name": name, "sha": expected})
    print("Deleted integrated branch:", name)

history = runs()
recent_failures = set(sorted((run["id"] for run in history if run["conclusion"] == "failure"), reverse=True)[:5])
for run in history:
    if run["id"] == run_id or run["head_branch"] in open_pr_branches or run["status"] != "completed":
        continue
    if run["conclusion"] == "cancelled" or (run["conclusion"] == "failure" and run["id"] not in recent_failures):
        api("DELETE", "actions/runs/" + str(run["id"]), allowed=(404,))
        report["deleted_runs"].append(run["id"])
        print("Deleted obsolete run:", run["id"])

if current_head() != head:
    raise RuntimeError("Default branch moved during cleanup; do not publish an unknown source")
tag = get("git/ref/tags/v1.0.0")["object"]["sha"]
if tag == "6a94ee6f2da87ff41aa38f82d7b30988556f62f1":
    api("POST", "actions/workflows/update-v1.0.0.yml/dispatches", {"ref": branch})
    report["publication"] = "One fresh workflow_dispatch requested for " + head
else:
    report["publication"] = "Release tag already moved to " + tag + "; retained for verification"
Path("cleanup-result.json").write_text(json.dumps(report, indent=2) + "\n")
with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
    summary.write("## Repository cleanup\n\n")
    summary.write("- Integrated branches deleted: " + str(len(report["deleted_branches"])) + "\n")
    summary.write("- Runs cancelled: " + str(len(report["cancelled_runs"])) + "\n")
    summary.write("- Obsolete run records deleted: " + str(len(report["deleted_runs"])) + "\n")
    summary.write("- " + report["publication"] + "\n")
print(report["publication"])
