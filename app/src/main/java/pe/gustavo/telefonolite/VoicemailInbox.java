package pe.gustavo.telefonolite;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Last events delivered by Telecom, not a live query of the carrier's mailbox. */
final class VoicemailInbox {
    private static final String PREFS = "voicemail_events";
    private static final String COUNT = "count_";
    private static final String TIME = "time_";
    private static final int MAX_ACCOUNTS = 8;

    static final class Entry {
        final PhoneAccountHandle account;
        final CharSequence label;
        final int count;
        final boolean observed;

        Entry(PhoneAccountHandle account, CharSequence label, int count, boolean observed) {
            this.account = account;
            this.label = label;
            this.count = count;
            this.observed = observed;
        }
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean canManage(Context context) {
        try {
            TelecomManager telecom = context.getSystemService(TelecomManager.class);
            return telecom != null && context.getPackageName().equals(telecom.getDefaultDialerPackage())
                    && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void syncReceiver(Context context) {
        int state = canManage(context) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        try {
            ComponentName component = new ComponentName(context, VoicemailReceiver.class);
            PackageManager packages = context.getPackageManager();
            if (packages.getComponentEnabledSetting(component) != state) {
                packages.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
            }
        } catch (RuntimeException ignored) {
            // The next reconciliation can retry; the receiver also checks access on delivery.
        }
    }

    static List<Entry> entries(Context context) {
        if (!canManage(context)) return Collections.emptyList();
        List<Entry> result = new ArrayList<>();
        try {
            TelecomManager telecom = context.getSystemService(TelecomManager.class);
            if (telecom == null) return result;
            SharedPreferences prefs = preferences(context);
            for (PhoneAccountHandle handle : telecom.getCallCapablePhoneAccounts()) {
                PhoneAccount account = telecom.getPhoneAccount(handle);
                if (account == null || !account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) continue;
                String key = COUNT + accountKey(handle);
                boolean observed = prefs.contains(key);
                result.add(new Entry(handle, account.getLabel(), prefs.getInt(key, -1), observed));
            }
        } catch (SecurityException revoked) {
            return Collections.emptyList();
        } catch (RuntimeException e) {
            // A partial list could be misleading after a Telecom failure.
            return Collections.emptyList();
        }
        return result;
    }

    static int pendingCount(Context context) {
        List<Entry> entries = entries(context);
        if (entries.isEmpty()) return -1;
        int total = 0;
        for (Entry entry : entries) {
            if (!entry.observed || entry.count < 0) return -1;
            total = (int) Math.min(Integer.MAX_VALUE, (long) total + entry.count);
        }
        return total;
    }

    static String accountKey(PhoneAccountHandle handle) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Include the user as handles can otherwise collide across profiles.
            String value = handle.getComponentName().flattenToString() + "\n"
                    + handle.getId() + "\n" + handle.getUserHandle();
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(hash.length * 2);
            char[] hex = "0123456789abcdef".toCharArray();
            for (byte b : hash) {
                key.append(hex[(b & 0xff) >>> 4]).append(hex[b & 0x0f]);
            }
            return key.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("deprecation")
    static void receive(Context context, Intent intent) {
        if (intent == null || !TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION.equals(intent.getAction())
                || !canManage(context)) return;
        try {
            PhoneAccountHandle handle = intent.getParcelableExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE);
            if (handle == null || handle.getComponentName() == null || handle.getId() == null) return;
            String key = accountKey(handle);
            int count = intent.getIntExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, -1);
            if (count < 0) count = -1;
            SharedPreferences prefs = preferences(context);
            SharedPreferences.Editor edit = prefs.edit();
            Map<String, ?> all = prefs.getAll();
            if (!prefs.contains(COUNT + key)) {
                int existing = 0;
                String oldest = null;
                long oldestTime = Long.MAX_VALUE;
                for (String name : all.keySet()) {
                    if (!name.startsWith(COUNT)) continue;
                    existing++;
                    String suffix = name.substring(COUNT.length());
                    Object time = all.get(TIME + suffix);
                    long at = time instanceof Long ? (Long) time : 0;
                    if (oldest == null || at < oldestTime) {
                        oldest = suffix;
                        oldestTime = at;
                    }
                }
                if (existing >= MAX_ACCOUNTS && oldest != null) {
                    edit.remove(COUNT + oldest).remove(TIME + oldest);
                }
            }
            edit.putInt(COUNT + key, count).putLong(TIME + key, System.currentTimeMillis()).apply();
        } catch (RuntimeException ignored) {
            // Ignore malformed broadcasts and access failures without inventing a clear state.
        }
    }

    private VoicemailInbox() { }
}
