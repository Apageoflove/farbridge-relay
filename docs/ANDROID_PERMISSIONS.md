# Android Permissions

Required runtime capability checks cover `RECEIVE_SMS`, `READ_SMS`, `READ_CALL_LOG`, `READ_PHONE_STATE`, network access and boot recovery. A failed or partial provider query must return `QueryFailure` and must never produce DELETE events. Default SMS role is opt-in and reversible; enable it only when real-device testing proves delayed OTP delivery or the non-default path is insufficient.

Verify grants after OS/security updates and expose their status in heartbeat. Play Store SMS/Call Log policy is not bypassed; private sideloading is the expected distribution model.
