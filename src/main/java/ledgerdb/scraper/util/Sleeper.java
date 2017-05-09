package ledgerdb.scraper.util;

import java.util.concurrent.TimeUnit;

public class Sleeper {

    private Sleeper() {}
    
    public static void sleepBetween(int minUnit, int maxUnit, TimeUnit unit) {
        long minTime = TimeUnit.MILLISECONDS.convert(minUnit, unit);
        long maxTime = TimeUnit.MILLISECONDS.convert(minUnit, unit);
        long time = minTime + (long)(Math.random() * (maxTime - minTime));
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
