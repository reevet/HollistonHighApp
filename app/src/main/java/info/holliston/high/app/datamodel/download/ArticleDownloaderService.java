package info.holliston.high.app.datamodel.download;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import android.net.Uri;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

import info.holliston.high.app.ImageAsyncCacher;
import info.holliston.high.app.MainActivity;
import info.holliston.high.app.R;
import info.holliston.high.app.datamodel.Article;
import info.holliston.high.app.widget.HHSWidget;

public class ArticleDownloaderService extends Service {

    private ArticleParser.SourceMode refreshSource = ArticleParser.SourceMode.ALLOW_BOTH;
    public static final String NOTIFICATION = "info.holliston.high.service.receiver";
    private ArticleDataSource newsDataSource;
    ImageAsyncCacher.SourceMode getImages = ImageAsyncCacher.SourceMode.ALLOW_BOTH;
    private String alarmSource;
    private Boolean newNewsAvailable = false;

    ArticleDataSource schedulesDataSource;
    ArticleDataSource dailyAnnDataSource;
    //ArticleDataSource newsDataSource;
    ArticleDataSource eventsDataSource;
    ArticleDataSource lunchDataSource;

    String schedulesString = "";
    String newsString = "";
    String eventsString = "";
    String dailyAnnString = "";
    String lunchString = "";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //--
        ArticleParser.SourceMode sentMode = (ArticleParser.SourceMode) intent.getSerializableExtra("refreshSource");
        String gi = "";
        gi = intent.getStringExtra("getImages");
        alarmSource = intent.getStringExtra("alarm");

        String alarmReset = "";
        alarmReset = intent.getStringExtra("alarmReset");
        if (alarmReset!=null) {
            scheduleDownloads();
            return Service.START_NOT_STICKY;
        }

        if (gi.equals("DOWNLOAD_ONLY")) {
            this.getImages = ImageAsyncCacher.SourceMode.DOWNLOAD_ONLY;
        } else {
            this.getImages = ImageAsyncCacher.SourceMode.ALLOW_BOTH;
        }
        Log.d("ArticleDownloaderService", "Service started due to " + alarmSource);
        if (!(sentMode == null)) {
            this.refreshSource = sentMode;
        }

        refreshNews();
        refreshDailyAnn();
        refreshSchedules();
        refreshEvents();
        refreshLunch();
        scheduleDownloads();

        return Service.START_NOT_STICKY;
    }

    private void refreshSchedules() {
        ArticleDataSource.ArticleDataSourceOptions sdso = new ArticleDataSource.ArticleDataSourceOptions(
                ArticleSQLiteHelper.TABLE_SCHEDULES,
                ArticleDataSource.ArticleDataSourceOptions.SourceType.JSON,
                getString(R.string.schedules_url),
                getResources().getStringArray(R.array.schedules_parser_names),
                ArticleParser.HtmlTags.IGNORE_HTML_TAGS,
                ArticleDataSource.ArticleDataSourceOptions.SortOrder.GET_FUTURE,
                "25");
        schedulesDataSource = new ArticleDataSource(getApplicationContext(), sdso);

        new refreshDataSource(schedulesDataSource, false, null, false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void refreshNews() {
        ArticleDataSource.ArticleDataSourceOptions ndso = new ArticleDataSource.ArticleDataSourceOptions(
                ArticleSQLiteHelper.TABLE_NEWS,
                ArticleDataSource.ArticleDataSourceOptions.SourceType.XML,
                getString(R.string.news_url),
                getResources().getStringArray(R.array.news_parser_names),
                ArticleParser.HtmlTags.KEEP_HTML_TAGS,
                ArticleDataSource.ArticleDataSourceOptions.SortOrder.GET_PAST,
                "25");
        newsDataSource = new ArticleDataSource(getApplicationContext(), ndso);
        new refreshDataSource(newsDataSource, true, this.getImages, true).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void refreshDailyAnn() {
        ArticleDataSource.ArticleDataSourceOptions dadso = new ArticleDataSource.ArticleDataSourceOptions(
                ArticleSQLiteHelper.TABLE_DAILYANN,
                ArticleDataSource.ArticleDataSourceOptions.SourceType.XML,
                getString(R.string.dailyann_url),
                getResources().getStringArray(R.array.dailyann_parser_names),
                ArticleParser.HtmlTags.CONVERT_LINE_BREAKS,
                ArticleDataSource.ArticleDataSourceOptions.SortOrder.GET_PAST,
                "10");
        dailyAnnDataSource = new ArticleDataSource(getApplicationContext(), dadso);
        new refreshDataSource(dailyAnnDataSource, true, null, false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void refreshEvents() {
        ArticleDataSource.ArticleDataSourceOptions edso = new ArticleDataSource.ArticleDataSourceOptions(
                ArticleSQLiteHelper.TABLE_EVENTS,
                ArticleDataSource.ArticleDataSourceOptions.SourceType.JSON,
                getString(R.string.events_url),
                getResources().getStringArray(R.array.events_parser_names),
                ArticleParser.HtmlTags.IGNORE_HTML_TAGS,
                ArticleDataSource.ArticleDataSourceOptions.SortOrder.GET_FUTURE,
                "40");
        eventsDataSource = new ArticleDataSource(getApplicationContext(), edso);
        new refreshDataSource(eventsDataSource, true, null, false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void refreshLunch() {
        ArticleDataSource.ArticleDataSourceOptions ldso = new ArticleDataSource.ArticleDataSourceOptions(
                ArticleSQLiteHelper.TABLE_LUNCH,
                ArticleDataSource.ArticleDataSourceOptions.SourceType.JSON,
                getString(R.string.lunch_url),
                getResources().getStringArray(R.array.lunch_parser_names),
                ArticleParser.HtmlTags.IGNORE_HTML_TAGS,
                ArticleDataSource.ArticleDataSourceOptions.SortOrder.GET_FUTURE,
                "40");
        lunchDataSource = new ArticleDataSource(getApplicationContext(), ldso);
        new refreshDataSource(lunchDataSource, true, null, false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class refreshDataSource extends AsyncTask<Void, Void, String> {

        ArticleDataSource dataSource;
        Boolean triggersNotification;
        Boolean cacheImages;
        ImageAsyncCacher.SourceMode getImages;

        public refreshDataSource (ArticleDataSource dataSource, Boolean cacheImages, ImageAsyncCacher.SourceMode getImages, Boolean triggersNotification) {
            this.dataSource = dataSource;
            this.cacheImages = cacheImages;
            this.getImages = getImages;
            this.triggersNotification = triggersNotification;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Void... arg0) {
            //nukeDatabaseRecords();
            //nukeCache();
            String result = "";
            try {
                String mostRecentNewsKey = "";
                List<Article> newsList = dataSource.getAllArticles();
                if (triggersNotification) {
                    mostRecentNewsKey = "";
                    if ((newsList == null) && (newsList.size() > 0)) {
                        mostRecentNewsKey = newsList.get(0).title;
                    }
                }
                result = dataSource.downloadArticles(refreshSource, getImages);
                Log.i("ArticleDownloaderService", dataSource.getName() +": "+ result);

                if (triggersNotification && (alarmSource != null)) {
                    List<Article> downloadedList = dataSource.getAllArticles();
                    //TODO: undebug this
                    //if ((newsList != null) && (newsList.size() > 0)) {

                        String downloadedNewsKey = newsList.get(0).title;
                        if (!downloadedNewsKey.equals(mostRecentNewsKey)) {
                            sendNotification();
                        }
                    //}
                }
            } catch (Exception e) {
                result = getResources().getString(R.string.xml_error);
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            publishResults(this.dataSource.getName(), result);
            if (this.cacheImages) {
                dataSource.downloadAndCacheImages(getImages);
            }


        }
    }

    private void publishResults(String name, String result) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra("result", result);
        intent.putExtra("datasource", name);
        sendBroadcast(intent);

        Intent intent2 = new Intent(this, HHSWidget.class);
        intent2.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        // Use an array and EXTRA_APPWIDGET_IDS instead of AppWidgetManager.EXTRA_APPWIDGET_ID,
        // since it seems the onUpdate() is only fired on that:
        int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), HHSWidget.class));
        intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent2);

        //Log.d("ArticleDownloaderService", "Results published for "+name );

    }

    private void scheduleDownloads() {
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String syncPrefString = "3";
        syncPrefString = prefs.getString("sync_frequency", "3");
        int syncPref;
        try {
            syncPref = Integer.parseInt(syncPrefString);
        } catch (NumberFormatException nfe) {
            syncPref = 3;
        }
        cancelAlarm(0);//legacy
        cancelAlarm(1);//legacy
        cancelAlarm(2);//legacy
        cancelAlarm(4);
        cancelAlarm(8);
        cancelAlarm(13);
        cancelAlarm(24);
        cancelAlarm(10); //for testing only

        Random r = new Random();
        int val = r.nextInt(15); //between 14 and 0;
        int sign = ~(r.nextInt(2));
        int jitter = sign * val;

        //Alarm 4 - 4 am --- 3 a day plan
        if ((syncPref == 3)) {
            setAlarm(4, 4, jitter, AlarmManager.INTERVAL_DAY);
        }

        //Alarm 8 - 8:30 am --- 3 a day plan or daily plan
        if ((syncPref == 3) || (syncPref == 1)) {
            setAlarm(8, 8, 30 + jitter, AlarmManager.INTERVAL_DAY);
        }

        //Alarm 13 - 1:30 pm --- 3 a day plan
        if (syncPref == 3) {
            setAlarm(13, 13, 30 + jitter, AlarmManager.INTERVAL_DAY);
        }

        //Alarm 24 -- hourly plan
        if (syncPref == 24) {
            setAlarm(24, 0, 0, AlarmManager.INTERVAL_HOUR);
        }
        //Alarm 10 - Testing only
        //This is every 30 seconds! Disable before publishing!
        //setTestAlarm();

    }

    private void setAlarm(int alarm, int hour, int minute, long interval) {

        Date now = new Date();
        Calendar nowCal = Calendar.getInstance();
        nowCal.setTime(now);
        int nowHour = nowCal.get(Calendar.HOUR_OF_DAY);
        int nowMinute = nowCal.get(Calendar.MINUTE);
        if ((nowHour > hour) ||
                ((nowHour == hour) && (nowMinute >= minute))) {
            nowCal.set(Calendar.DATE, nowCal.get(Calendar.DATE) + 1);
        }
        nowCal.set(Calendar.HOUR_OF_DAY, hour);
        nowCal.set(Calendar.MINUTE, minute);

        if ((hour == 0) && (minute == 0)) {
            nowCal.set(Calendar.HOUR_OF_DAY, 0);
            nowCal.set(Calendar.MINUTE, 0);
        }

        AlarmManager alarmMgr;
        alarmMgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(getApplicationContext(), ArticleDownloaderService.class);
        intent.putExtra("refreshSource", ArticleParser.SourceMode.PREFER_DOWNLOAD);
        intent.putExtra("alarm", "alarm-" + alarm);
        intent.putExtra("getImages", "ALLOW_BOTH");

        PendingIntent alarmIntent = PendingIntent.getService(getApplicationContext(), alarm, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmMgr.setRepeating(AlarmManager.RTC, nowCal.getTimeInMillis(),
                interval, alarmIntent);

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, hh:mm a");
        Date setFor = nowCal.getTime();
        String setString = sdf.format(setFor);
        Log.d("ArticleDownloaderService", "Alarm " + alarm + " set for " + setString);
    }

    private void cancelAlarm(int alarm) {
        AlarmManager alarmMgr;
        alarmMgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(getApplicationContext(), ArticleDownloaderService.class);

        PendingIntent alarmIntent = PendingIntent.getService(getApplicationContext(), alarm, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        alarmMgr.cancel(alarmIntent);
    }

    /*private void setTestAlarm() {
        Date now = new Date();
        Calendar nowCal = Calendar.getInstance();
        nowCal.setTime(now);
        nowCal.set(Calendar.SECOND, nowCal.get(Calendar.SECOND) + 30);

        AlarmManager alarmMgr;
        alarmMgr = (AlarmManager)getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(getApplicationContext(), ArticleDownloaderService.class);
        intent.putExtra("refreshSource", ArticleParser.SourceMode.DOWNLOAD_ONLY);
        intent.putExtra("alarm", "alarm-"+10);

        PendingIntent alarmIntent = PendingIntent.getService(getApplicationContext(), 10, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmMgr.setRepeating(AlarmManager.RTC, nowCal.getTimeInMillis(),
                1000*30, alarmIntent);

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, hh:mm a");
        Date setFor = nowCal.getTime();
        String setString = sdf.format(setFor);
        Log.d("ArticleDownloaderService", "TestAlarm(10) set for "+setString);
    }*/

    private void sendNotification() {

        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String notifications_new_message_ringtone = "";
        notifications_new_message_ringtone = prefs.getString("notifications_new_message_ringtone", "content://settings/system/notification_sound");
        Boolean notifications_new_message_vibrate = true;
        notifications_new_message_vibrate = prefs.getBoolean("notifications_new_message_vibrate", true);

        List<Article> articleList = newsDataSource.getAllArticles();
        if ((articleList != null) && articleList.size() > 0) {

            Article article = articleList.get(0);
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_hhs_hollow)
                    .setContentTitle("New post from Holliston High")
                    .setContentText(article.title);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addParentStack(MainActivity.class);

            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.putExtra("fromNotification", true);
            stackBuilder.addNextIntent(intent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(0, mBuilder.build());

            if (!notifications_new_message_ringtone.equals("silent")) {
                try {
                    Uri ringtoneUri = Uri.parse(notifications_new_message_ringtone);
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
                    r.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (notifications_new_message_vibrate) {
                try {
                    Vibrator v = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                    // Vibrate for 500 milliseconds
                    v.vibrate(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


        }
    }
}

