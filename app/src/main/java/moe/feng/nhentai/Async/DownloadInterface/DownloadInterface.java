package moe.feng.nhentai.Async.DownloadInterface;

/**
 * Created by Jordy on 18/04/2016.
 */
public interface DownloadInterface {
    void onProgress(int ID, int Progress);

    void onStop(int ID);

    void onPause(int ID);

    void onOK(int ID);
}
