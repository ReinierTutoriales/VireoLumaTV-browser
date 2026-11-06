"""Shared legacy-View / Compose testTag contract for UIAutomator XML.

Compose exports tags as resource-id only when testTagsAsResourceId is enabled
on a semantics ancestor. A missing control is transitional; an exported,
focusable control makes focus assertions enforceable.
"""
from typing import Optional
import xml.etree.ElementTree as ET


def matches_control(node: ET.Element, package: str, name: str) -> bool:
    rid = node.get("resource-id", "")
    # Legacy Android Views use package:id/name. Compose emits the raw testTag.
    return rid in (name, f"{package}:id/{name}") or rid.endswith(f":id/{name}")


def find_control(root: Optional[ET.Element], package: str, name: str):
    if root is None:
        return None
    return next((n for n in root.iter("node") if matches_control(n, package, name)), None)


def has_compose_tags(root: Optional[ET.Element], names) -> bool:
    if root is None:
        return False
    return any(n.get("resource-id") in names for n in root.iter("node"))
