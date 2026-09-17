"""Privacy-aware Bark and standard Web Push helpers."""
from __future__ import annotations

import json

import httpx


def bark_payload(privacy_mode: bool, sender: str, body: str, otp: str | None) -> dict:
    """Build a Bark payload; privacy mode intentionally excludes all content."""
    if privacy_mode:
        return {"title": "远桥", "body": "收到验证码" if otp else "收到新消息", "group": "phone-mirror"}
    rendered_body = f"{sender}: {body}" if sender else body
    return {"title": "远桥", "body": rendered_body, "group": "phone-mirror", "copy": otp or body}


async def send_bark(base_url: str, key: str, payload: dict) -> None:
    """Send a Bark notification without placing endpoint or content in logs."""
    url = f"{base_url.rstrip('/')}/{key}"
    async with httpx.AsyncClient(timeout=10) as client:
        response = await client.post(url, json=payload)
        response.raise_for_status()


def is_permanent_push_failure(status_code: int) -> bool:
    """Only gone or unknown endpoints are permanent subscription failures."""
    return status_code in {404, 410}


def send_webpush(subscription: dict, payload: dict, private_key: str, subject: str) -> None:
    """Deliver standards Web Push using VAPID; caller handles permanent failure."""
    from pywebpush import webpush
    webpush(subscription_info=subscription, data=json.dumps(payload, ensure_ascii=False), vapid_private_key=private_key, vapid_claims={"sub": subject})
