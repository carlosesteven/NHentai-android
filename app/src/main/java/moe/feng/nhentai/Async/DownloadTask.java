package moe.feng.nhentai.Async;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.util.ArrayList;

import moe.feng.nhentai.Async.DownloadInterface.DownloadInterface;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.BookDetailsActivity;
import moe.feng.nhentai.util.Utility;
import moe.feng.nhentai.util.task.BookDownloader;

public class DownloadTask extends AsyncTask<String, String, String> {
    final private Handler handler = new Handler();
    private int startProgress;
    private Book book;
    private Context context;
    private int notID;
    private NotificationCompat.Builder mBuilder;
    private BookDownloader mDownloader;
    private DownloadInterface inteface;
    private FileCacheManager mFileCacheManager;
    private State current;
    Runnable check = new Runnable() {
        @Override
        public void run() {
            int state = context.getSharedPreferences("data", Context.MODE_PRIVATE).getInt(notID + "state", DownloadTask.State.NONE.getValue());
            switch (State.fromValue(state)) {
                case PAUSE:
                    Log.d("State", "PAUSE");
                    if (current == State.DOWNLOADING) {
                        current = State.PAUSE;
                        mDownloader.pause();
                        mBuilder.setContentText("PAUSA");
                        context.getSharedPreferences("data", Context.MODE_PRIVATE).edit().putInt(notID + "state", State.PAUSE.getValue()).apply();
                        NotificationManagerCompat.from(context).notify(notID, mBuilder.build());
                        inteface.onPause(notID);
                    }
                    handler.postDelayed(check, 500);
                    break;
                case CANCELED:
                    Log.d("State", "CANCELED");
                    current = State.CANCELED;
                    mDownloader.stop();
                    NotificationManagerCompat.from(context).cancel(notID);
                    handler.removeCallbacks(check);
                    cancel(true);
                    break;
                case RESUME_DOWNLOAD:
                    Log.d("State", "RESUME");
                    current = State.DOWNLOADING;
                    context.getSharedPreferences("data", Context.MODE_PRIVATE).edit().putInt(notID + "state", State.DOWNLOADING.getValue()).apply();
                    mDownloader.start();
                    handler.postDelayed(check, 500);
                    break;
                default:
                    Log.d("State", "STATE_" + state);
                    handler.postDelayed(check, 500);
                    break;
            }
        }
    };

    public DownloadTask(int startProgress, Book book, Context context) {
        this.startProgress = startProgress;
        this.book = book;
        this.context = context;
        this.notID = Integer.parseInt(book.galleryId);
        inteface = (DownloadInterface) context;
        Intent intent = new Intent(context, BookDetailsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("book_data", book.toJSONString());
        mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(PendingIntent.getActivity(
                        context,
                        (int) (System.currentTimeMillis() & 0xfffffff),
                        intent,
                        PendingIntent.FLAG_CANCEL_CURRENT
                ))
                .setOngoing(true);
        mFileCacheManager = FileCacheManager.getInstance(context);
    }

    @Override
    protected String doInBackground(String... params) {
        handler.postDelayed(check, 500);
        current = State.DOWNLOADING;
        context.getSharedPreferences("data", Context.MODE_PRIVATE).edit().putInt(notID + "state", State.DOWNLOADING.getValue()).apply();
        mBuilder.setContentTitle(book.title);
        mBuilder.setContentText(startProgress + "%");
        mBuilder.setProgress(100, startProgress, false);
        NotificationManagerCompat.from(context).notify(notID, mBuilder.build());
        if (mDownloader == null) {
            mDownloader = new BookDownloader(context, book);
            mDownloader.setCurrentPosition(0);
            mDownloader.setOnDownloadListener(new BookDownloader.OnDownloadListener() {
                @Override
                public void onFinish(int position, final int progress) {
                    if (current == State.DOWNLOADING) {
                        inteface.onProgress(notID, progress);
                        mBuilder.mActions = new ArrayList<>();
                        mBuilder.setContentText(progress + "/" + book.pageCount + "      " + Utility.calcProgress(progress, book.pageCount) + "%");
                        mBuilder.setProgress(100, Utility.calcProgress(progress, book.pageCount), false);
                        NotificationManagerCompat.from(context).notify(notID, mBuilder.build());
                    }
                }

                @Override
                public void onError(int position, int errorCode) {

                }

                @Override
                public void onStateChange(int state, final int progress) {
                    switch (state) {
                        case BookDownloader.STATE_STOP:
                            context.getSharedPreferences("data", Context.MODE_PRIVATE).edit().putInt(notID + "state", State.CANCELED.getValue()).apply();
                            inteface.onStop(notID);
                            NotificationManagerCompat.from(context).cancel(notID);
                            break;
                        case BookDownloader.STATE_PAUSE:
                            mBuilder.setContentText("PAUSA");
                            context.getSharedPreferences("data", Context.MODE_PRIVATE).edit().putInt(notID + "state", State.PAUSE.getValue()).apply();
                            NotificationManagerCompat.from(context).notify(notID, mBuilder.build());
                            inteface.onPause(notID);
                            break;
                        case BookDownloader.STATE_ALL_OK:
                            inteface.onOK(notID);
                            context.getSharedPreferences("data", Context.MODE_PRIVATE).edit().putInt(notID + "state", State.OK.getValue()).apply();
                            break;
                    }
                }
            });
        }
        mFileCacheManager.saveBookDataToExternalPath(book);
        mDownloader.start();
        return null;
    }

    @Override
    protected void onCancelled(String s) {
        super.onCancelled(s);
        handler.removeCallbacks(check);
        mDownloader.stop();
    }

    public enum State {
        DOWNLOADING(0),
        PAUSE(1),
        CANCELED(2),
        OK(3),
        NONE(4),
        RESUME_DOWNLOAD(5);
        int value;

        State(int value) {
            this.value = value;
        }

        public static State fromValue(int value) {
            switch (value) {
                case 0:
                    return State.DOWNLOADING;
                case 1:
                    return State.PAUSE;
                case 2:
                    return State.CANCELED;
                case 3:
                    return State.OK;
                case 4:
                    return State.NONE;
                case 5:
                    return State.RESUME_DOWNLOAD;
                default:
                    return fromValue(4);
            }
        }

        public int getValue() {
            return value;
        }
    }
}
