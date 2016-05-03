package moe.feng.nhentai.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Jordy on 28/04/2016.
 */
public class ExecutorManager {
    final static int corePoolSize = 60;
    final static int maximumPoolSize = 80;
    final static int keepAliveTime = 10;

    public static Executor getExecutor() {
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
        return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
    }
}
