package com.anysoftkeyboard.api;


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;


/**
 * @author lado
 */
public abstract class LanguageChangeddBroadcastReceiver extends BroadcastReceiver {

    /* (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
     */
    @Override
    public final void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(".NOTIFY_ON")) {
            sendNotification(context, intent);
        } else if (action.equals(".NOTIFY_OFF")) {
            cancelNotification(context, intent);
        }
        //        if(action.equals("notifylanguage")){
//            Notification notification = new Notification();
//
//            Intent notificationIntent = new Intent();
//            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
//                    notificationIntent, 0);
//
//            notification.setLatestEventInfo(getApplicationContext(),
//                    getText("ka"), "ka",
//                    contentIntent);
//            //this will not work. Need to find a way to show notification as a different package.
//            //notification.icon = current.getKeyboardIconResId();
//            notification.icon = R.drawable.ka;
//
//            notification.defaults = 0;// no sound, vibrate, etc.
//            // notifying
//            NotificationManager    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); 
//            mNotificationManager.notify(KEYBOARD_NOTIFICATION_ID, notification);

        //}
    }

    private void sendNotification(Context ctx, Intent i) {
        int icon = i.getExtras().getInt("current_layout_resid");
        String name = i.getExtras().getString("current_layout_name");
        Intent intent = new Intent(Intent.ACTION_DEFAULT);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, 0);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx);
        builder.setSmallIcon(icon);
        builder.setContentTitle("AnySoftKeyboard");
        builder.setContentText(name);
        builder.setContentIntent(pi);

        nm.notify(1, builder.build());
    }

    private void cancelNotification(Context ctx, Intent i) {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(ns);
        nm.cancel(1);
    }

}
