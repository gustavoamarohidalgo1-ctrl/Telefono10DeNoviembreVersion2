package pe.gustavo.telefonolite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives the protected system voicemail event while this app is the default dialer. */
public final class VoicemailReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        VoicemailInbox.receive(context, intent);
    }
}
