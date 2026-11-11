import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.Process;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

public class VoicemailEvent {
  public static void main(String[] args) throws Exception {
    String qemu = (String) Class.forName("android.os.SystemProperties")
        .getMethod("get", String.class).invoke(null, "ro.kernel.qemu");
    if (Process.myUid() != 0 || !"1".equals(qemu))
      throw new SecurityException("Fixture requires a root emulator");
    int count = Integer.parseInt(args[0]);
    if (count != 0 && count != 2) throw new IllegalArgumentException("Only test count 0 or 2");
    Looper.prepareMainLooper();
    Class<?> activityThread = Class.forName("android.app.ActivityThread");
    Object thread = activityThread.getMethod("systemMain").invoke(null);
    Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
    TelecomManager telecom = context.getSystemService(TelecomManager.class);
    String target = "pe.gustavo.telefonolite";
    if (!target.equals(telecom.getDefaultDialerPackage()))
      throw new IllegalStateException("Test app must be default dialer");
    for (PhoneAccountHandle handle : telecom.getCallCapablePhoneAccounts()) {
      PhoneAccount account = telecom.getPhoneAccount(handle);
      if (account != null && account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
        Intent event = new Intent(TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION)
            .setPackage(target).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            .putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, count);
        context.sendBroadcast(event, android.Manifest.permission.READ_PHONE_STATE);
        System.out.println("Dispatched synthetic voicemail count " + count);
        return;
      }
    }
    throw new IllegalStateException("No test SIM available");
  }
}
