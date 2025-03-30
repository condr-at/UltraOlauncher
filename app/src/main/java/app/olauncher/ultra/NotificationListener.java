package app.olauncher.ultra;

import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NotificationListener extends NotificationListenerService {

    public static final String ACTION_NOTIFICATION_UPDATE = "app.olauncher.ultra.NOTIFICATION_UPDATE";
    public static final String EXTRA_PACKAGES_WITH_NOTIFICATIONS = "packages_with_notifications";

    // Using a static set for simplicity in this context. Be mindful of potential issues in complex scenarios.
    private static final Set<String> notifiedPackages = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        updateActiveNotifications();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (sbn == null || sbn.isOngoing()) return; // Ignore ongoing notifications like music players
        notifiedPackages.add(sbn.getPackageName());
        sendBroadcast();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (sbn == null) return;
        updateActiveNotifications(); // Re-check all active notifications for this package
    }

    private void updateActiveNotifications() {
        StatusBarNotification[] activeNotifications = getActiveNotifications();
        Set<String> currentActivePackages = new HashSet<>();
        if (activeNotifications != null) {
            for (StatusBarNotification sbn : activeNotifications) {
                if (!sbn.isOngoing()) { // Only count dismissible notifications
                    currentActivePackages.add(sbn.getPackageName());
                }
            }
        }

        boolean changed = !notifiedPackages.equals(currentActivePackages);
        synchronized (notifiedPackages) {
            notifiedPackages.clear();
            notifiedPackages.addAll(currentActivePackages);
        }

        if (changed) {
            sendBroadcast();
        }
    }

    private void sendBroadcast() {
        Intent intent = new Intent(ACTION_NOTIFICATION_UPDATE);
        // Sending the whole set is simple but potentially inefficient if large.
        // Consider sending only added/removed packages if performance becomes an issue.
        intent.putExtra(EXTRA_PACKAGES_WITH_NOTIFICATIONS, new HashSet<>(notifiedPackages));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // Static getter for MainActivity to access the initial state if needed,
    // but primarily rely on broadcasts for updates.
    public static Set<String> getNotifiedPackages() {
        return new HashSet<>(notifiedPackages);
    }
}