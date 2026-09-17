"""Notification privacy and subscription-failure tests."""
from app.services.notifications import bark_payload, is_permanent_push_failure


def test_bark_privacy_mode_hides_sensitive_content():
    payload = bark_payload(True, sender="10086", body="验证码 123456", otp="123456")
    rendered = str(payload)
    assert "收到验证码" in rendered
    assert "10086" not in rendered and "123456" not in rendered


def test_bark_full_mode_is_explicit():
    payload = bark_payload(False, sender="10086", body="验证码 123456", otp="123456")
    assert payload["title"] == "远桥"
    assert "123456" in str(payload)


def test_only_permanent_webpush_failures_disable_subscription():
    assert is_permanent_push_failure(404)
    assert is_permanent_push_failure(410)
    assert not is_permanent_push_failure(429)
    assert not is_permanent_push_failure(503)
