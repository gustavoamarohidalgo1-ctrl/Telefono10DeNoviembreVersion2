package pe.gustavo.telefonolite;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.test.InstrumentationTestCase;

/** Synthetic events only. Run on an emulator with this app as the default dialer. */
@SuppressWarnings("deprecation")
public final class VoicemailInboxTest extends InstrumentationTestCase {
    private static final String TEST_PREFS = "voicemail_inbox_instrumentation_test";
    private Context context;
    private SharedPreferences prefs;
    private PhoneAccountHandle first;
    private PhoneAccountHandle second;
    private int originalReceiverState;

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context target = getInstrumentation().getTargetContext();
        context = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(TEST_PREFS, mode);
            }
        };
        prefs = VoicemailInbox.preferences(context);
        assertTrue(prefs.edit().clear().commit());
        ComponentName source = new ComponentName(context.getPackageName(), "SyntheticConnectionService");
        first = new PhoneAccountHandle(source, "test-sim-one");
        second = new PhoneAccountHandle(source, "test-sim-two");
        originalReceiverState = context.getPackageManager().getComponentEnabledSetting(
                new ComponentName(context, VoicemailReceiver.class));
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (prefs != null) prefs.edit().clear().commit();
            if (context != null) context.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(context, VoicemailReceiver.class), originalReceiverState,
                    PackageManager.DONT_KILL_APP);
        } finally {
            super.tearDown();
        }
    }

    private void requireDefaultDialerAndPermission() {
        assertTrue("Configure the emulator with Teléfono Lite as default dialer and grant READ_PHONE_STATE",
                VoicemailInbox.canManage(context));
    }

    private Intent event(PhoneAccountHandle account) {
        return new Intent(TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION)
                .putExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE, account);
    }

    private int count(PhoneAccountHandle account) {
        return prefs.getInt("count_" + VoicemailInbox.accountKey(account), Integer.MIN_VALUE);
    }

    public void testCountChangesFromTwoToClear() {
        requireDefaultDialerAndPermission();
        VoicemailInbox.receive(context, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 2));
        assertEquals(2, count(first));
        VoicemailInbox.receive(context, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 0));
        assertEquals(0, count(first));
    }

    public void testMissingAndUnknownCountAreNotClear() {
        requireDefaultDialerAndPermission();
        VoicemailInbox.receive(context, event(first));
        assertEquals(-1, count(first));
        VoicemailInbox.receive(context, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, -1));
        assertEquals(-1, count(first));
    }

    public void testAccountsRemainSeparateAndKeysDoNotExposeIds() {
        requireDefaultDialerAndPermission();
        String firstKey = VoicemailInbox.accountKey(first);
        String secondKey = VoicemailInbox.accountKey(second);
        assertFalse(firstKey.equals(secondKey));
        assertFalse(firstKey.contains(first.getId()));
        VoicemailInbox.receive(context, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 2));
        VoicemailInbox.receive(context, event(second).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 3));
        VoicemailInbox.receive(context, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 0));
        assertEquals(0, count(first));
        assertEquals(3, count(second));
    }

    public void testWrongActionDoesNotChangeState() {
        requireDefaultDialerAndPermission();
        VoicemailInbox.receive(context, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 2));
        VoicemailInbox.receive(context, event(first).setAction("test.WRONG")
                .putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 0));
        assertEquals(2, count(first));
    }

    public void testMissingAccountAndWrongDefaultDialerDoNotRecord() {
        requireDefaultDialerAndPermission();
        VoicemailInbox.receive(context, new Intent(TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION)
                .putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 2));
        Context notDefault = new ContextWrapper(context) {
            @Override public String getPackageName() { return "test.not.default.dialer"; }
        };
        assertFalse(VoicemailInbox.canManage(notDefault));
        VoicemailInbox.receive(notDefault, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 2));
        assertTrue(prefs.getAll().isEmpty());
    }

    public void testStorageIsBounded() {
        requireDefaultDialerAndPermission();
        ComponentName source = first.getComponentName();
        for (int i = 0; i < 12; i++) {
            PhoneAccountHandle account = new PhoneAccountHandle(source, "synthetic-" + i);
            VoicemailInbox.receive(context, event(account)
                    .putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, i));
        }
        int countKeys = 0;
        for (String key : prefs.getAll().keySet()) if (key.startsWith("count_")) countKeys++;
        assertEquals(8, countKeys);
        assertEquals(16, prefs.getAll().size());
    }

    public void testDeniedPermissionDoesNotRecordAndDisablesReceiver() {
        Context denied = new ContextWrapper(context) {
            @Override public int checkSelfPermission(String permission) {
                return Manifest.permission.READ_PHONE_STATE.equals(permission)
                        ? PackageManager.PERMISSION_DENIED : super.checkSelfPermission(permission);
            }
        };
        assertFalse(VoicemailInbox.canManage(denied));
        VoicemailInbox.receive(denied, event(first).putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 2));
        assertTrue(prefs.getAll().isEmpty());
        VoicemailInbox.syncReceiver(denied);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                context.getPackageManager().getComponentEnabledSetting(
                        new ComponentName(context, VoicemailReceiver.class)));
    }

    public void testReceiverEnabledOnlyWhenEligible() {
        VoicemailInbox.syncReceiver(context);
        int expected = VoicemailInbox.canManage(context) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        assertEquals(expected, context.getPackageManager().getComponentEnabledSetting(
                new ComponentName(context, VoicemailReceiver.class)));
    }
}
