package com.renard.ocr;

/**
 * 权限授予事件，在MonitoredActivity中授权之后EventBus会发送该类型事件
 *
 * @author renard
 */
public class PermissionGrantedEvent {
    private final String mPermission;

    public PermissionGrantedEvent(String permission) {
        mPermission = permission;
    }

    public String getPermission() {
        return mPermission;
    }
}
